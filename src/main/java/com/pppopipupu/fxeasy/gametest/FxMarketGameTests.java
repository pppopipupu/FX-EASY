package com.pppopipupu.fxeasy.gametest;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import com.pppopipupu.fxeasy.economy.EconomyConstants;
import com.pppopipupu.fxeasy.economy.EconomyRow;
import com.pppopipupu.fxeasy.economy.EconomySourceKind;
import com.pppopipupu.fxeasy.economy.WorldEconomyState;
import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;
import com.pppopipupu.fxeasy.market.Timeframe;
import com.pppopipupu.fxeasy.server.FxWorldEconomyTracker;
import com.pppopipupu.fxeasy.storage.FxSavedData;
import com.pppopipupu.fxeasy.storage.FxSnapshot;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/// Covers what only a running server can show: the tick loop advances the market on its monotonic clock and
/// hands the market and the world economy to the saved data every `FxTradingService.SNAPSHOT_INTERVAL` ticks,
/// even though nobody ever traded.
public final class FxMarketGameTests {

    private static final int MAX_TICKS = 900;

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(BuiltInRegistries.TEST_FUNCTION, FxEasy.MODID);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> MARKET_IS_SAVED =
            FUNCTIONS.register("market_is_saved", () -> FxMarketGameTests::marketIsSaved);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> ECONOMY_IS_SAVED =
            FUNCTIONS.register("economy_is_saved", () -> FxMarketGameTests::economyIsSaved);

    private static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> PRODUCTION_MOVES_THE_FUNDAMENTAL =
            FUNCTIONS.register("production_moves_the_fundamental", () -> FxMarketGameTests::productionMovesTheFundamental);

    private FxMarketGameTests() {
    }

    public static void register(IEventBus modBus) {
        FUNCTIONS.register(modBus);
        modBus.addListener(FxMarketGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
                Identifier.fromNamespaceAndPath(FxEasy.MODID, "server"),
                new TestEnvironmentDefinition.AllOf(List.of()));
        event.registerTest(
                Identifier.fromNamespaceAndPath(FxEasy.MODID, "market_is_saved"),
                new FunctionGameTestInstance(
                        MARKET_IS_SAVED.getKey(),
                        new TestData<>(environment, Identifier.withDefaultNamespace("empty"), MAX_TICKS, 1, true)));
        event.registerTest(
                Identifier.fromNamespaceAndPath(FxEasy.MODID, "economy_is_saved"),
                new FunctionGameTestInstance(
                        ECONOMY_IS_SAVED.getKey(),
                        new TestData<>(environment, Identifier.withDefaultNamespace("empty"), MAX_TICKS, 1, true)));
        event.registerTest(
                Identifier.fromNamespaceAndPath(FxEasy.MODID, "production_moves_the_fundamental"),
                new FunctionGameTestInstance(
                        PRODUCTION_MOVES_THE_FUNDAMENTAL.getKey(),
                        new TestData<>(environment, Identifier.withDefaultNamespace("empty"), MAX_TICKS, 1, true)));
    }

    private static void marketIsSaved(GameTestHelper helper) {
        FxSavedData data = helper.getLevel().getServer().getDataStorage().computeIfAbsent(FxSavedData.TYPE);
        helper.succeedWhen(() -> {
            FxSnapshot snapshot = data.snapshot();
            helper.assertTrue(snapshot.currencies().size() == Currencies.count(), "saved market misses currencies");
            helper.assertTrue(snapshot.series().size() == Currencies.pairs().size() * Timeframe.ALL.size(), "saved market misses candle windows");
            Optional<FxSnapshot.SeriesRow> forming = snapshot.series().stream().filter(row -> row.forming().isPresent()).findFirst();
            helper.assertTrue(forming.isPresent(), "saved market has no candle forming");
            helper.assertTrue(forming.orElseThrow().forming().orElseThrow().close() > 0.0D, "saved market has no price");
        });
    }

    /// The world economy runs on the market clock rather than on world time, so a save written a few hundred
    /// ticks into a world already carries the row the terminal reads back, with the clock the market will
    /// continue from and a capacity the market can measure production against. The world is shared with the
    /// other tests in the batch, so this pins the shape and the coherence of the row rather than assuming the
    /// world observed nothing.
    private static void economyIsSaved(GameTestHelper helper) {
        FxSavedData data = helper.getLevel().getServer().getDataStorage().computeIfAbsent(FxSavedData.TYPE);
        FxWorldEconomyTracker live = FxWorldEconomyTracker.current();
        helper.succeedWhen(() -> {
            FxSnapshot snapshot = data.snapshot();
            Optional<EconomyRow> saved = snapshot.economy();
            helper.assertTrue(saved.isPresent(), "saved market has no economy row");
            EconomyRow economy = saved.orElseThrow();
            helper.assertTrue(snapshot.marketTick() > 0L, "saved market has no clock");
            helper.assertTrue(economy.clock().marketTick() <= snapshot.marketTick(),
                    "the economy ran ahead of the market it prices");
            helper.assertTrue(economy.clock().nextDayTick() > economy.clock().marketTick(),
                    "the saved economy is already past its day boundary");
            helper.assertTrue(live != null, "the server started without a fact collector");
            helper.assertTrue(live.snapshotEconomy().marketTick() >= economy.clock().marketTick(),
                    "the save is ahead of the economy it was written from");
            for (Currency currency : Currencies.all()) {
                helper.assertTrue(economy.capacityOf(currency) == currency.capacity(),
                        "a world with no player online has no capacity to measure production against: " + currency);
            }
            for (FxSnapshot.CurrencyRow row : snapshot.currencies()) {
                helper.assertTrue(Double.isFinite(row.logPrice()) && Double.isFinite(row.anchor()),
                        "saved currency has no valuation: " + row.currency());
            }
        });
    }

    /// The whole chain the plan is about: a committed world fact enters the bucket, the bucket boundary folds it
    /// into the flow estimates, and the next bucket revalues the currency it belongs to and leaves the others
    /// alone. The economy under test is a private one, so the live market of the running server is untouched and
    /// the capacity is the one player figure no player online gives.
    private static void productionMovesTheFundamental(GameTestHelper helper) {
        Currencies.set(Currencies.defaults());
        Currency gold = currency("gold");
        WorldEconomyState economy = WorldEconomyState.genesis();
        FxWorldEconomyTracker tracker = new FxWorldEconomyTracker(economy);
        long capacity = (long) economy.capacity(gold);
        tracker.recordCommitted(EconomySourceKind.NATURAL_ORE, gold, 16L * capacity);
        double[] noEvent = new double[Currencies.count()];
        advanceBucket(economy, noEvent);
        helper.assertTrue(economy.supplyEwma(gold) > 0.0D, "the recorded production never reached the flow");
        advanceBucket(economy, noEvent);
        helper.assertTrue(economy.fundamental(gold) < 0.0D, "supply pressure did not revalue the currency down");
        helper.assertTrue(economy.fundamental(gold) > EconomyConstants.MIN_FUNDAMENTAL,
                "one bucket of production priced the whole fundamental band");
        for (Currency currency : Currencies.all()) {
            if (currency == gold) {
                continue;
            }
            helper.assertTrue(economy.fundamental(currency) == 0.0D, "production moved a currency it did not belong to");
            helper.assertTrue(economy.supplyEwma(currency) == 0.0D, "production was attributed to the wrong currency");
        }
        helper.succeed();
    }

    /// One bucket the way the service runs it: the clock, the planned revaluation and the bucket boundary all
    /// move together, which is what makes the plan of a closed bucket reach the applied fundamental.
    private static void advanceBucket(WorldEconomyState economy, double[] noEvent) {
        for (int tick = 0; tick < EconomyConstants.TICKS_PER_BUCKET; tick++) {
            economy.advanceClock(0);
            economy.tick(noEvent);
        }
        economy.closeBucket();
    }

    /// A currency of the default set the tests that name an id pin, or the running world's own currency for the
    /// ids the default set defines.
    private static Currency currency(String id) {
        Currency currency = Currencies.byId(id);
        if (currency == null) {
            throw new IllegalStateException("the traded currencies do not define " + id);
        }
        return currency;
    }
}
