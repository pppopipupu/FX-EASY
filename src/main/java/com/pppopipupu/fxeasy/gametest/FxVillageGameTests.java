package com.pppopipupu.fxeasy.gametest;

import module java.base;

import com.mojang.datafixers.util.Pair;
import com.pppopipupu.fxeasy.FxEasy;
import com.pppopipupu.fxeasy.content.FxItems;
import com.pppopipupu.fxeasy.datagen.FxStructures;
import com.pppopipupu.fxeasy.datagen.FxVillages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/// The exchange as the running game sees it: the template the jar ships parses into a structure with one chest in
/// it, that chest holds the phone and ten stacks of dirt, and every village house pool the mod overrides lists the
/// exchange once. A template that does not parse, a chest whose items do not decode or a pool that lost the
/// exchange all fail here rather than in a generated world nobody checked.
public final class FxVillageGameTests {

    private static final int MAX_TICKS = 100;
    private static final int CHEST_SLOTS = 1 + 10;

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(BuiltInRegistries.TEST_FUNCTION, FxEasy.MODID);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> EXCHANGE_IS_BUILT =
            FUNCTIONS.register("exchange_is_built", () -> FxVillageGameTests::exchangeIsBuilt);

    private FxVillageGameTests() {
    }

    public static void register(IEventBus modBus) {
        FUNCTIONS.register(modBus);
        modBus.addListener(FxVillageGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
                Identifier.fromNamespaceAndPath(FxEasy.MODID, "village"),
                new TestEnvironmentDefinition.AllOf(List.of()));
        event.registerTest(
                Identifier.fromNamespaceAndPath(FxEasy.MODID, "exchange_is_built"),
                new FunctionGameTestInstance(
                        EXCHANGE_IS_BUILT.getKey(),
                        new TestData<>(environment, Identifier.withDefaultNamespace("empty"), MAX_TICKS, 1, true)));
    }

    private static void exchangeIsBuilt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StructureTemplate template = level.getStructureManager().get(FxStructures.EXCHANGE).orElseThrow();
        List<StructureTemplate.StructureBlockInfo> chests =
                template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.CHEST);
        helper.assertTrue(chests.size() == 1, "the exchange holds " + chests.size() + " chests");
        helper.assertTrue(chests.getFirst().nbt() != null, "the chest of the exchange carries no data");
        CompoundTag chest = chests.getFirst().nbt();
        ListTag items = chest.getList("Items").orElse(new ListTag());
        List<ItemStackWithSlot> stacks = ItemStackWithSlot.CODEC.listOf().parse(NbtOps.INSTANCE, items).getOrThrow();
        helper.assertTrue(stacks.size() == CHEST_SLOTS, "the chest holds " + stacks.size() + " slots");
        helper.assertTrue(stacks.getFirst().stack().is(FxItems.FX_PHONE.get()), "the chest has no phone in its first slot");
        int dirt = 0;
        for (ItemStackWithSlot stack : stacks) {
            if (stack.stack().is(Items.DIRT)) {
                dirt += stack.stack().getCount();
            }
        }
        helper.assertTrue(dirt == 10 * Item.DEFAULT_MAX_STACK_SIZE, "the chest holds " + dirt + " dirt");
        Registry<StructureTemplatePool> pools = level.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
        for (String style : FxVillages.STYLES) {
            StructureTemplatePool pool = pools.getValue(ResourceKey.create(Registries.TEMPLATE_POOL,
                    Identifier.withDefaultNamespace("village/" + style + "/houses")));
            helper.assertTrue(pool != null, "the " + style + " house pool is gone");
            int listings = listings(pool);
            helper.assertTrue(listings == 1, "the " + style + " house pool lists the exchange " + listings + " times");
        }
        helper.succeed();
    }

    /// How often a pool offers the exchange. The element is looked up by the template it places, so a pool that
    /// kept a stale path counts as a pool without an exchange.
    private static int listings(StructureTemplatePool pool) {
        int listings = 0;
        for (Pair<StructurePoolElement, Integer> entry : pool.getTemplates()) {
            if (entry.getFirst() instanceof SinglePoolElement single
                    && single.getTemplateLocation().equals(FxStructures.EXCHANGE)) {
                listings++;
            }
        }
        return listings;
    }
}
