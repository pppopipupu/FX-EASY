package com.pppopipupu.fxeasy.gametest;

import module java.base;

import com.mojang.datafixers.util.Pair;
import com.pppopipupu.fxeasy.FxEasy;
import com.pppopipupu.fxeasy.content.FxItems;
import com.pppopipupu.fxeasy.datagen.FxStructures;
import com.pppopipupu.fxeasy.datagen.FxVillages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/// The exchange as the running game sees it: the template the jar ships parses into a structure with one chest in
/// it, that chest holds the phone and ten stacks of dirt, every village pool the mod overrides lists the exchange
/// once, and a placed exchange carries the sign that names it, the two lamps that light its entrance, which are
/// still lit twenty ticks later, and the two storeys above its ground floor, each of them a floor with room to
/// stand on that the stairs of the building reach. A template that does not parse, a chest whose items do not
/// decode, a pool that lost the exchange, a lamp that a neighbour update puts out or a storey nobody can climb to
/// all fail here rather than in a generated world nobody checked.
public final class FxVillageGameTests {

    private static final int MAX_TICKS = 100;
    private static final int CHEST_SLOTS = 1 + 10;
    private static final List<String> EXCHANGE_POOLS = List.of("houses", "terminators");
    private static final String ENTRANCE_NAME = "minecraft:building_entrance";

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
            for (String pool : EXCHANGE_POOLS) {
                StructureTemplatePool entries = pools.getValue(ResourceKey.create(Registries.TEMPLATE_POOL,
                        Identifier.withDefaultNamespace("village/" + style + "/" + pool)));
                helper.assertTrue(entries != null, "the " + style + " " + pool + " pool is gone");
                int listings = listings(entries);
                helper.assertTrue(listings == 1,
                        "the " + style + " " + pool + " pool lists the exchange " + listings + " times");
            }
        }
        List<StructureTemplate.StructureBlockInfo> signs =
                template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.OAK_SIGN);
        helper.assertTrue(signs.size() == 1, "the exchange holds " + signs.size() + " signs");
        List<StructureTemplate.StructureBlockInfo> lamps =
                template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.REDSTONE_LAMP);
        helper.assertTrue(lamps.size() == 2, "the exchange holds " + lamps.size() + " lamps");
        for (StructureTemplate.StructureBlockInfo lamp : lamps) {
            helper.assertTrue(lamp.state().getValue(RedstoneLampBlock.LIT),
                    "the lamp at " + lamp.pos() + " is written unlit");
        }
        BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
        template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), Block.UPDATE_ALL);
        BlockPos entrance = null;
        for (StructureTemplate.StructureBlockInfo jigsaw : template.filterBlocks(BlockPos.ZERO,
                new StructurePlaceSettings(), Blocks.JIGSAW)) {
            CompoundTag data = jigsaw.nbt();
            if (data != null && ENTRANCE_NAME.equals(data.getString("name").orElse(""))) {
                helper.assertTrue(Blocks.CUT_COPPER == BuiltInRegistries.BLOCK.getValue(
                        Identifier.parse(data.getString("final_state").orElse(""))),
                        "the entrance jigsaw leaves " + data.getString("final_state").orElse("nothing") + " behind");
                entrance = jigsaw.pos();
            }
        }
        helper.assertTrue(entrance != null, "the exchange carries no entrance jigsaw");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setYRot(270.0F);
        player.setXRot(0.0F);
        BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, ItemStack.EMPTY,
                new BlockHitResult(Vec3.ZERO, Direction.UP, origin.offset(entrance), false));
        int facing = Blocks.OAK_SIGN.getStateForPlacement(context).getValue(StandingSignBlock.ROTATION);
        helper.assertTrue(signs.getFirst().state().getValue(StandingSignBlock.ROTATION) == facing,
                "the sign over the door faces " + signs.getFirst().state().getValue(StandingSignBlock.ROTATION)
                        + " where a sign placed from the street would face " + facing);
        for (int course = 1; course <= 3; course++) {
            helper.assertTrue(level.getBlockState(origin.offset(entrance.above(course))).isAir(),
                    "the doorway of the exchange is not walkable at course " + course);
        }
        for (int storey = 1; storey <= 2; storey++) {
            int floor = 4 * storey;
            helper.assertTrue(!level.getBlockState(origin.offset(6, floor, 6)).isAir(),
                    "the storey of course " + floor + " of the exchange has no floor");
            helper.assertTrue(level.getBlockState(origin.offset(6, floor + 1, 6)).isAir(),
                    "the storey of course " + floor + " of the exchange has no room to stand in");
            for (int step = 0; step < 3; step++) {
                BlockPos stair = origin.offset(2 + step, floor - 3 + step, 1);
                helper.assertTrue(!level.getBlockState(stair).isAir(),
                        "the stairs to the storey of course " + floor + " are missing the step at " + stair);
                helper.assertTrue(level.getBlockState(stair.above()).isAir(),
                        "the step at " + stair + " of the stairs to the storey of course " + floor
                                + " has no headroom");
            }
            helper.assertTrue(!level.getBlockState(origin.offset(5, floor, 1)).isAir(),
                    "the stairs to the storey of course " + floor + " arrive over a hole");
        }
        SignBlockEntity placed = (SignBlockEntity) level.getBlockEntity(origin.offset(signs.getFirst().pos()));
        helper.assertTrue(placed != null, "the sign of the exchange is not a sign");
        Component[] messages = placed.getFrontText().getMessages(false);
        for (int line = 0; line < FxStructures.SIGN_LINES.size(); line++) {
            helper.assertTrue(messages[line].getString().equals(FxStructures.SIGN_LINES.get(line)),
                    "the sign reads " + messages[line].getString() + " on line " + line);
        }
        helper.runAfterDelay(20, () -> {
            for (StructureTemplate.StructureBlockInfo lamp : lamps) {
                helper.assertTrue(level.getBlockState(origin.offset(lamp.pos())).getValue(RedstoneLampBlock.LIT),
                        "the lamp at " + lamp.pos() + " went out after being placed");
            }
            helper.succeed();
        });
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
