package com.pppopipupu.fxeasy.economy;

import module java.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;

class WorldEconomyStateTest {

    private static final Currency GOLD = Currencies.byId("gold");
    private static final Currency EMERALD = Currencies.byId("emerald");
    private static final long GOLD_ATOMS_PER_BUCKET_AT_SIXTEEN_CAPACITY = 360L;

    @Test
    void sustainedProductionSettlesAtABoundedDiscountInsteadOfFallingForever() {
        WorldEconomyState state = WorldEconomyState.restore(rowWithCapacity(100.0D));
        double atDay15 = 0.0D;
        for (int day = 1; day <= 25; day++) {
            runProductionDay(state);
            if (day == 15) {
                atDay15 = state.target(GOLD);
            }
        }
        double atDay25 = state.target(GOLD);

        assertEquals(atDay15, atDay25, 1e-6);
        assertEquals(-GOLD.supplyWeight() * Math.tanh(Math.log1p(16.0D)), atDay25, 1e-9);
        assertTrue(atDay25 < 0.0D);
        assertTrue(atDay25 > EconomyConstants.MIN_FUNDAMENTAL);
    }

    @Test
    void stoppingProductionClearsTheSupplyEwmaFasterThanThePressure() {
        WorldEconomyState state = WorldEconomyState.restore(rowWithCapacity(100.0D));
        for (int day = 0; day < 5; day++) {
            runProductionDay(state);
        }
        double supplyPeak = state.supplyEwma(GOLD);
        double pressurePeak = state.pressure(GOLD);

        for (int bucket = 0; bucket < EconomyConstants.BUCKETS_PER_DAY; bucket++) {
            advanceBucket(state);
        }
        double supplyAfter = state.supplyEwma(GOLD);
        double pressureAfter = state.pressure(GOLD);

        assertTrue(supplyAfter < supplyPeak);
        assertTrue(pressureAfter < pressurePeak);
        assertTrue(supplyAfter / supplyPeak < pressureAfter / pressurePeak);
        assertTrue(supplyAfter < supplyPeak * 0.1D);
        assertTrue(pressureAfter > pressurePeak * 0.5D);
    }

    @Test
    void aFarFundamentalTargetIsWalkedInBoundedStepsRatherThanJumpedTo() {
        WorldEconomyState state = WorldEconomyState.restore(rowWithCapacity(1.0D));
        state.openBucket().addSupply(EconomySourceKind.NATURAL_ORE, GOLD, 1000L);
        advanceBucket(state);
        advanceBucket(state);

        double target = state.target(GOLD);
        assertTrue(target < -0.15D);
        assertEquals(-EconomyConstants.MAX_FUNDAMENTAL_STEP_PER_BUCKET, state.fundamental(GOLD), 1e-12);
        assertTrue(Math.abs(target - state.fundamental(GOLD)) > 0.1D);
        assertTrue(Math.abs(target) / EconomyConstants.MAX_FUNDAMENTAL_STEP_PER_BUCKET > 10.0D);
    }

    @Test
    void anEventRequestIsClampedToTheBucketBudgetAndTheRemainderIsReported() {
        WorldEconomyState state = WorldEconomyState.genesis();
        double[] request = new double[Currencies.count()];
        request[Currencies.index(GOLD)] = 0.01D;

        state.tick(request);

        assertEquals(EconomyConstants.MAX_DRIVER_STEP_PER_BUCKET, state.eventApplied(GOLD), 1e-12);
        assertEquals(0.01D - EconomyConstants.MAX_DRIVER_STEP_PER_BUCKET, state.eventClamped(GOLD), 1e-12);
    }

    @Test
    void oppositeSignRequestsShareOneAbsoluteBudget() {
        WorldEconomyState state = WorldEconomyState.genesis();
        double[] up = new double[Currencies.count()];
        up[Currencies.index(GOLD)] = 0.0015D;
        double[] down = new double[Currencies.count()];
        down[Currencies.index(GOLD)] = -0.0015D;

        state.tick(up);
        double first = state.eventApplied(GOLD);
        state.tick(down);
        double second = state.eventApplied(GOLD) - first;

        assertEquals(0.0015D, first, 1e-12);
        assertEquals(-0.0005D, second, 1e-12);
        assertNotEquals(-0.0015D, second);
        assertTrue(Math.abs(first) + Math.abs(second) <= EconomyConstants.MAX_DRIVER_STEP_PER_BUCKET + 1e-12);
    }

    @Test
    void aFullDayOfTwentyPlayersMovesCapacityByTheTenPercentDailyLimit() {
        WorldEconomyState state = WorldEconomyState.genesis();
        for (int tick = 0; tick < EconomyConstants.TICKS_PER_DAY; tick++) {
            state.advanceClock(20);
        }

        assertTrue(state.dayRolledOver());
        assertEquals(20.0D, state.playerDayEwma(), 1e-9);
        for (Currency currency : Currencies.all()) {
            double base = currency.capacity();
            assertEquals(base * (1.0D + EconomyConstants.MAX_CAPACITY_STEP_PER_DAY),
                    state.capacity(currency), 1e-6, currency.name());
        }
    }

    @Test
    void aSingleTickOfTwentyPlayersLeavesCapacityAtBase() {
        WorldEconomyState state = WorldEconomyState.genesis();
        for (int tick = 0; tick < EconomyConstants.TICKS_PER_DAY - 1; tick++) {
            state.advanceClock(0);
        }
        state.advanceClock(20);

        assertTrue(state.dayRolledOver());
        assertTrue(state.playerDayEwma() < 0.01D);
        for (Currency currency : Currencies.all()) {
            assertEquals(currency.capacity(), state.capacity(currency), 1e-9, currency.name());
        }
    }

    @Test
    void sustainedTwentyPlayersConvergesCapacityToTwentyTimesBase() {
        WorldEconomyState state = WorldEconomyState.genesis();
        for (int day = 0; day < 60; day++) {
            for (int tick = 0; tick < EconomyConstants.TICKS_PER_DAY; tick++) {
                state.advanceClock(20);
            }
            state.dayRolledOver();
        }

        for (Currency currency : Currencies.all()) {
            double base = currency.capacity();
            assertEquals(base * 20.0D, state.capacity(currency), 1e-6 * base, currency.name());
        }
    }

    /// The reversibility of a use, not the currency it was paid in, decides what demand it pays: the same atoms
    /// of gold are worth a full crafting input when a recipe consumed them and a fraction of it when a piglin
    /// only held them for a moment.
    @Test
    void aBarterCountsThirtyFivePercentOfACraftingInputOfTheSameAtomCount() {
        double capacity = GOLD.capacity();
        EconomyBucket.Mutable barter = new EconomyBucket.Mutable();
        barter.addTerminalUse(EconomySourceKind.BARTER_TERMINAL_USE, GOLD, 900L);
        EconomyBucket.Mutable craft = new EconomyBucket.Mutable();
        craft.addTerminalUse(EconomySourceKind.CRAFT_TERMINAL_USE, GOLD, 900L);

        double barterDemand = EconomyModel.normalizedDemand(barter.seal(), GOLD, capacity);
        double craftDemand = EconomyModel.normalizedDemand(craft.seal(), GOLD, capacity);

        assertEquals(EconomyModel.terminalUseWeight(EconomySourceKind.BARTER_TERMINAL_USE), barterDemand / craftDemand, 1e-12);
        assertEquals(EconomyModel.terminalUseWeight(EconomySourceKind.CRAFT_TERMINAL_USE),
                EconomyModel.terminalUseWeight(EconomySourceKind.SMITHING_TERMINAL_USE), 0.0D);
    }

    /// A round trip through a villager pays less demand than the supply it generated, so handing back the
    /// emeralds a villager just paid can soften the pressure but never turn it into a revaluation.
    @Test
    void aVillagerPayAndReabsorbRoundTripCannotRaiseTheFundamentalTarget() {
        double capacity = EMERALD.capacity();
        WorldEconomyState paid = WorldEconomyState.restore(rowWithCapacity(capacity));
        paid.openBucket().addSupply(EconomySourceKind.VILLAGER_PAYS_EMERALD, EMERALD, 4096L);
        advanceBucket(paid);

        WorldEconomyState roundTrip = WorldEconomyState.restore(rowWithCapacity(capacity));
        roundTrip.openBucket().addSupply(EconomySourceKind.VILLAGER_PAYS_EMERALD, EMERALD, 4096L);
        roundTrip.openBucket().addTerminalUse(EconomySourceKind.VILLAGER_REABSORB, EMERALD, 4096L);
        advanceBucket(roundTrip);

        assertTrue(roundTrip.target(EMERALD) > paid.target(EMERALD),
                "reabsorbed emeralds did not soften the supply pressure");
        assertTrue(roundTrip.target(EMERALD) < 0.0D,
                "a round trip through a villager revalued the currency up");
    }

    private static EconomyRow rowWithCapacity(double capacity) {
        double[] amounts = new double[Currencies.count()];
        Arrays.fill(amounts, capacity);
        List<CurrencyAmount> values = CurrencyAmount.of(amounts);
        return new EconomyRow(EconomyRow.genesis().clock(), EconomyRow.Flow.zero(), EconomyRow.Drivers.zero(),
                new EconomyRow.CapacityValues(values, values), 1.0D, 0, 0, List.of());
    }

    private static void runProductionDay(WorldEconomyState state) {
        for (int bucket = 0; bucket < EconomyConstants.BUCKETS_PER_DAY; bucket++) {
            state.openBucket().addSupply(EconomySourceKind.NATURAL_ORE, GOLD, GOLD_ATOMS_PER_BUCKET_AT_SIXTEEN_CAPACITY);
            advanceBucket(state);
        }
    }

    private static void advanceBucket(WorldEconomyState state) {
        double[] noEvent = new double[Currencies.count()];
        for (int tick = 0; tick < EconomyConstants.TICKS_PER_BUCKET; tick++) {
            state.advanceClock(0);
            state.tick(noEvent);
        }
        state.closeBucket();
    }
}
