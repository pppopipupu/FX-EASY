package com.pppopipupu.fxeasy.gametest;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import com.pppopipupu.fxeasy.economy.EconomySourceKind;
import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;
import com.pppopipupu.fxeasy.server.FxWorldEconomyTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/// The committed fact collector against a live level, one test per commit chain it claims to observe:
/// `committed_piglin_death_records_gold`, `cancelled_piglin_death_records_nothing`,
/// `villager_trade_records_both_directions`, `unpaid_villager_trade_records_nothing`,
/// `natural_ore_records_supply`, `placed_ore_records_nothing`, `mined_position_records_supply_once` and
/// `barter_consumes_gold_once`. Every assertion is made against the tracker's own counters, so a test can only
/// pass when the chain really reached the collector.
public final class FxEconomyGameTests {

    private static final int MAX_TICKS = 600;
    private static final int SETTLE_TICKS = 40;

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(BuiltInRegistries.TEST_FUNCTION, FxEasy.MODID);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> COMMITTED_PIGLIN_DEATH =
            FUNCTIONS.register("committed_piglin_death_records_gold", () -> FxEconomyGameTests::committedPiglinDeathRecordsGold);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> CANCELLED_PIGLIN_DEATH =
            FUNCTIONS.register("cancelled_piglin_death_records_nothing", () -> FxEconomyGameTests::cancelledPiglinDeathRecordsNothing);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> VILLAGER_TRADE =
            FUNCTIONS.register("villager_trade_records_both_directions", () -> FxEconomyGameTests::villagerTradeRecordsBothDirections);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> UNPAID_TRADE =
            FUNCTIONS.register("unpaid_villager_trade_records_nothing", () -> FxEconomyGameTests::unpaidVillagerTradeRecordsNothing);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> NATURAL_ORE =
            FUNCTIONS.register("natural_ore_records_supply", () -> FxEconomyGameTests::naturalOreRecordsSupply);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> PLACED_ORE =
            FUNCTIONS.register("placed_ore_records_nothing", () -> FxEconomyGameTests::placedOreRecordsNothing);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> MINED_ONCE =
            FUNCTIONS.register("mined_position_records_supply_once", () -> FxEconomyGameTests::minedPositionRecordsSupplyOnce);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> BARTER =
            FUNCTIONS.register("barter_consumes_gold_once", () -> FxEconomyGameTests::barterConsumesGoldOnce);

    private FxEconomyGameTests() {
    }

    public static void register(IEventBus modBus) {
        FUNCTIONS.register(modBus);
        modBus.addListener(FxEconomyGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
                Identifier.fromNamespaceAndPath(FxEasy.MODID, "economy"),
                new TestEnvironmentDefinition.AllOf(List.of()));
        register(event, environment, "committed_piglin_death_records_gold", COMMITTED_PIGLIN_DEATH);
        register(event, environment, "cancelled_piglin_death_records_nothing", CANCELLED_PIGLIN_DEATH);
        register(event, environment, "villager_trade_records_both_directions", VILLAGER_TRADE);
        register(event, environment, "unpaid_villager_trade_records_nothing", UNPAID_TRADE);
        register(event, environment, "natural_ore_records_supply", NATURAL_ORE);
        register(event, environment, "placed_ore_records_nothing", PLACED_ORE);
        register(event, environment, "mined_position_records_supply_once", MINED_ONCE);
        register(event, environment, "barter_consumes_gold_once", BARTER);
    }

    private static void register(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment, String name,
            DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> function) {
        event.registerTest(
                Identifier.fromNamespaceAndPath(FxEasy.MODID, name),
                new FunctionGameTestInstance(
                        function.getKey(),
                        new TestData<>(environment, Identifier.withDefaultNamespace("empty"), MAX_TICKS, 1, true)));
    }

    /// The loot table of a death the level keeps: the gold the world created is counted, and it is counted only
    /// for a kill that really happened. The kill is repeated until the roll produced gold, because a zombified
    /// piglin's nugget is a random drop and a single roll proves nothing about a missing one.
    private static void committedPiglinDeathRecordsGold(GameTestHelper helper) {
        Currencies.set(Currencies.defaults());
        ServerLevel level = helper.getLevel();
        Player killer = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos pos = new BlockPos(1, 2, 1);
        long before = atoms(EconomySourceKind.PIGLIN_GOLD, currency("gold"));
        long start = level.getGameTime();
        helper.succeedWhen(() -> {
            if (atoms(EconomySourceKind.PIGLIN_GOLD, currency("gold")) == before) {
                var piglin = helper.spawn(EntityType.ZOMBIFIED_PIGLIN, pos);
                piglin.hurtServer(level, level.damageSources().playerAttack(killer), 1000.0F);
            }
            helper.assertTrue(atoms(EconomySourceKind.PIGLIN_GOLD, currency("gold")) > before,
                    "a committed death did not record the gold its loot table produced");
            helper.assertTrue(level.getGameTime() - start > SETTLE_TICKS, "waiting for the death to settle");
        });
    }

    /// A death whose drops the event cancelled is not a fact: the level never sees the entities, so the collector
    /// may not see them either.
    /// A death the level refuses records nothing, and the removal of the body is what the wait is for: the death
    /// animation only advances in a chunk the level ticks for entities, so the chunk of the test area is forced
    /// and the piglin is only killed once the level really ticks entities there. Without that it stays dying and
    /// the test fails on a body that never leaves.
    private static void cancelledPiglinDeathRecordsNothing(GameTestHelper helper) {
        Currencies.set(Currencies.defaults());
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setChunkForced(at.getX() >> 4, at.getZ() >> 4, true);
        Player killer = helper.makeMockPlayer(GameType.SURVIVAL);
        long before = atoms(EconomySourceKind.PIGLIN_GOLD, currency("gold"));
        ZombifiedPiglin[] spawned = new ZombifiedPiglin[1];
        helper.succeedWhen(() -> {
            if (spawned[0] == null) {
                helper.assertTrue(level.getChunkSource().chunkMap.getDistanceManager().inEntityTickingRange(ChunkPos.pack(at)),
                        "waiting for the forced chunk to tick entities");
                ZombifiedPiglin piglin = helper.spawn(EntityType.ZOMBIFIED_PIGLIN, new BlockPos(1, 2, 1));
                spawned[0] = piglin;
                Consumer<LivingDropsEvent> cancel = event -> {
                    if (event.getEntity() == piglin) {
                        event.setCanceled(true);
                    }
                };
                NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, cancel);
                boolean hurt;
                try {
                    hurt = piglin.hurtServer(level, level.damageSources().playerAttack(killer), 1000.0F);
                } finally {
                    NeoForge.EVENT_BUS.unregister(cancel);
                }
                helper.assertTrue(hurt, "the piglin was not hurt at all");
                helper.assertTrue(atoms(EconomySourceKind.PIGLIN_GOLD, currency("gold")) == before,
                        "a cancelled death recorded the drops the level refused");
                return;
            }
            ZombifiedPiglin piglin = spawned[0];
            helper.assertTrue(piglin.isRemoved(), "the killed piglin was not removed: health=" + piglin.getHealth()
                    + " alive=" + piglin.isAlive() + " dying=" + piglin.isDeadOrDying());
            helper.assertTrue(atoms(EconomySourceKind.PIGLIN_GOLD, currency("gold")) == before,
                    "a cancelled death recorded the drops the level refused, later in the death animation");
        });
    }

    /// The two directions of one committed trade, and the fact that an offer sitting on the menu is not a trade.
    private static void villagerTradeRecordsBothDirections(GameTestHelper helper) {
        Currencies.set(Currencies.defaults());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        var villager = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
        villager.setTradingPlayer(player);
        MerchantOffer offer = new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(Items.GOLD_INGOT), 12, 1, 0.05F);
        villager.getOffers().add(offer);
        long paidBefore = atoms(EconomySourceKind.VILLAGER_PAYS_CURRENCY, currency("gold"));
        long takenBefore = atoms(EconomySourceKind.VILLAGER_REABSORB, currency("emerald"));
        helper.assertTrue(atoms(EconomySourceKind.VILLAGER_PAYS_CURRENCY, currency("gold")) == paidBefore,
                "an offer that was only displayed was recorded as the villager paying");
        helper.assertTrue(atoms(EconomySourceKind.VILLAGER_REABSORB, currency("emerald")) == takenBefore,
                "an offer that was only displayed was recorded as the player paying");
        villager.notifyTrade(offer);
        helper.assertTrue(atoms(EconomySourceKind.VILLAGER_PAYS_CURRENCY, currency("gold")) - paidBefore == 9L,
                "the gold ingot the villager paid was not recorded");
        helper.assertTrue(atoms(EconomySourceKind.VILLAGER_REABSORB, currency("emerald")) - takenBefore == 2L,
                "the emeralds the player handed over were not recorded");
        helper.assertTrue(atoms(EconomySourceKind.VILLAGER_PAYS_EMERALD, currency("emerald")) == 0L,
                "an emerald the player paid was recorded as an emerald the villager paid");
        helper.succeed();
    }

    /// The guard the trade menu itself uses: a take that the payment cannot pay for never reaches the commit
    /// point, so it can never reach the collector.
    private static void unpaidVillagerTradeRecordsNothing(GameTestHelper helper) {
        Currencies.set(Currencies.defaults());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        var villager = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
        villager.setTradingPlayer(player);
        villager.getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(Items.GOLD_INGOT), 12, 1, 0.05F));
        long paidBefore = atoms(EconomySourceKind.VILLAGER_PAYS_CURRENCY, currency("gold"));
        long takenBefore = atoms(EconomySourceKind.VILLAGER_REABSORB, currency("emerald"));
        MerchantMenu menu = new MerchantMenu(0, player.getInventory(), villager);
        menu.getSlot(2).onTake(player, menu.getSlot(2).getItem());
        helper.assertTrue(atoms(EconomySourceKind.VILLAGER_PAYS_CURRENCY, currency("gold")) == paidBefore,
                "an unpaid trade was recorded as the villager paying");
        helper.assertTrue(atoms(EconomySourceKind.VILLAGER_REABSORB, currency("emerald")) == takenBefore,
                "an unpaid trade was recorded as the player paying");
        helper.succeed();
    }

    /// A block the world generated: breaking it is a find, and its unenchanted drop is the resource.
    private static void naturalOreRecordsSupply(GameTestHelper helper) {
        Currencies.set(Currencies.defaults());
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlockAndUpdate(pos, Blocks.GOLD_ORE.defaultBlockState());
        long before = atoms(EconomySourceKind.NATURAL_ORE, currency("gold"));
        level.destroyBlock(pos, true);
        helper.assertTrue(atoms(EconomySourceKind.NATURAL_ORE, currency("gold")) - before == 9L,
                "a natural gold ore was not recorded as the raw gold it dropped");
        helper.assertTrue(atoms(EconomySourceKind.DIRT_DIG, currency("dirt")) == 0L,
                "an ore was recorded as dirt");
        helper.succeed();
    }

    /// The same block placed by a player: breaking it recovers what the player put down, which is not supply.
    private static void placedOreRecordsNothing(GameTestHelper helper) {
        Currencies.set(Currencies.defaults());
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos base = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlockAndUpdate(base, Blocks.STONE.defaultBlockState());
        BlockPos pos = base.above();
        place(helper, player, base, pos, Items.GOLD_ORE);
        helper.assertTrue(level.getBlockState(pos).is(Blocks.GOLD_ORE), "the player placement did not commit");
        long before = atoms(EconomySourceKind.NATURAL_ORE, currency("gold"));
        level.destroyBlock(pos, true);
        helper.assertTrue(atoms(EconomySourceKind.NATURAL_ORE, currency("gold")) == before,
                "a placed ore was recorded as a natural find");
        helper.succeed();
    }

    /// One position, one find: breaking it again while it is air adds nothing, and putting the same block back
    /// as a player adds nothing either.
    private static void minedPositionRecordsSupplyOnce(GameTestHelper helper) {
        Currencies.set(Currencies.defaults());
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlockAndUpdate(pos, Blocks.GOLD_ORE.defaultBlockState());
        long before = atoms(EconomySourceKind.NATURAL_ORE, currency("gold"));
        level.destroyBlock(pos, true);
        long after = atoms(EconomySourceKind.NATURAL_ORE, currency("gold"));
        helper.assertTrue(after - before == 9L, "the first break did not record the find");
        level.destroyBlock(pos, true);
        helper.assertTrue(atoms(EconomySourceKind.NATURAL_ORE, currency("gold")) == after,
                "breaking an empty position recorded a find");
        place(helper, player, pos.below(), pos, Items.GOLD_ORE);
        level.destroyBlock(pos, true);
        helper.assertTrue(atoms(EconomySourceKind.NATURAL_ORE, currency("gold")) == after,
                "the same position was recorded as a second find");
        helper.succeed();
    }

    /// A barter consumes the gold in the piglin's off hand once, and the loot it rolls back is not supply. A test
    /// area is loaded for blocks but not for entities, so the chunk is forced and the piglin is only spawned once
    /// the level really ticks entities there: without that the brain behaviour that hands the gold over never runs.
    private static void barterConsumesGoldOnce(GameTestHelper helper) {
        Currencies.set(Currencies.defaults());
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(3, 2, 3));
        level.setChunkForced(at.getX() >> 4, at.getZ() >> 4, true);
        long before = atoms(EconomySourceKind.BARTER_TERMINAL_USE, currency("gold"));
        long start = level.getGameTime();
        Piglin[] spawned = new Piglin[1];
        helper.succeedWhen(() -> {
            if (spawned[0] == null) {
                helper.assertTrue(level.getChunkSource().chunkMap.getDistanceManager()
                        .inEntityTickingRange(ChunkPos.pack(at)), "waiting for the forced chunk to tick entities");
                Piglin piglin = helper.spawn(EntityType.PIGLIN, new BlockPos(3, 2, 3));
                piglin.setBaby(false);
                piglin.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.GOLD_INGOT));
                spawned[0] = piglin;
                return;
            }
            Piglin piglin = spawned[0];
            helper.assertTrue(atoms(EconomySourceKind.BARTER_TERMINAL_USE, currency("gold")) - before == 9L,
                    "a barter did not consume exactly the gold that left the off hand: recorded="
                            + atoms(EconomySourceKind.BARTER_TERMINAL_USE, currency("gold"))
                            + " offhand=" + piglin.getOffhandItem() + " alive=" + piglin.isAlive()
                            + " ticks=" + piglin.tickCount);
            helper.assertTrue(level.getGameTime() - start > SETTLE_TICKS, "waiting for the barter to settle");
            helper.assertTrue(atoms(EconomySourceKind.BARTER_TERMINAL_USE, currency("gold")) - before == 9L,
                    "the same barter was counted more than once");
        });
    }

    /// Places through [ItemStack#useOn] rather than through `BlockItem#place`: the place event, and with it the
    /// mark that keeps a block a player put back from counting as a find, is only fired on the use path.
    private static void place(GameTestHelper helper, Player player, BlockPos against, BlockPos pos, ItemLike item) {
        ItemStack stack = new ItemStack(item);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(against), Direction.UP, against, false);
        stack.useOn(new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, stack, hit));
        helper.assertTrue(!helper.getLevel().getBlockState(pos).isAir(), "the player placement did not commit at " + pos);
    }

    private static long atoms(EconomySourceKind kind, Currency currency) {
        FxWorldEconomyTracker tracker = FxWorldEconomyTracker.current();
        if (tracker == null) {
            throw new IllegalStateException("the server started without a fact collector");
        }
        return tracker.recordedAtoms(kind, currency);
    }

    private static Currency currency(String id) {
        Currency currency = Currencies.byId(id);
        if (currency == null) {
            throw new IllegalStateException("the default currencies do not define " + id);
        }
        return currency;
    }
}
