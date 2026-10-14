package com.pppopipupu.fxeasy.event;

import module java.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pppopipupu.fxeasy.economy.EconomyConstants;
import com.pppopipupu.fxeasy.economy.EconomySourceKind;
import com.pppopipupu.fxeasy.economy.WorldEconomyState;
import com.pppopipupu.fxeasy.event.MarketEventDefinition.ImpactChannel;
import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;
import com.pppopipupu.fxeasy.market.MarketDriverFrame;
import com.pppopipupu.fxeasy.market.Rng;
import com.pppopipupu.fxeasy.storage.EventRow;
import org.junit.jupiter.api.Test;

class MarketEventEngineTest {

    private static final Currency GOLD = Currencies.byId("gold");
    private static final Currency EMERALD = Currencies.byId("emerald");
    private static final Currency DIAMOND = Currencies.byId("diamond");

    private static MarketEventEngine engine() {
        MarketEventEngine engine = new MarketEventEngine(MarketEventCatalog.ALL);
        engine.tuneForTest(EconomyConstants.TICKS_PER_DAY, 0.0D, 3);
        return engine;
    }

    @Test
    void appliesWholeTargetExactlyOnceAndMonotone() {
        MarketEventEngine engine = engine();
        MarketEventDefinition definition = MarketEventCatalog.RAID_DISRUPTION;
        engine.begin(definition, 0L);
        WorldEconomyState economy = WorldEconomyState.genesis();
        Rng rng = new Rng(1L);
        double[] buffer = new double[Currencies.count()];
        double total = 0.0D;
        double previous = 0.0D;
        for (long tick = 1L; tick <= definition.lifetimeTicks(); tick++) {
            engine.tick(tick, economy, rng);
            engine.rateRequest(buffer);
            total += buffer[Currencies.index(EMERALD)];
            assertTrue(total <= previous + 1.0E-12D, "cumulative applied must be monotone");
            previous = total;
        }
        assertEquals(definition.peakImpact(EMERALD), total, 1.0E-9D);
        assertFalse(engine.isRunning(definition.id()));
    }

    @Test
    void saveAndRestoreMidEventDoesNotPayTwice() {
        MarketEventDefinition definition = MarketEventCatalog.RAID_DISRUPTION;
        MarketEventEngine first = engine();
        first.begin(definition, 0L);
        WorldEconomyState economyA = WorldEconomyState.genesis();
        Rng rngA = new Rng(9L);
        double[] buffer = new double[Currencies.count()];
        double applied = 0.0D;
        for (long tick = 1L; tick <= 5000L; tick++) {
            first.tick(tick, economyA, rngA);
            first.rateRequest(buffer);
            applied += buffer[Currencies.index(EMERALD)];
        }
        EventRow row = first.snapshot();
        MarketEventEngine second = MarketEventEngine.restore(row);
        second.tuneForTest(EconomyConstants.TICKS_PER_DAY, 0.0D, 3);
        WorldEconomyState economyB = WorldEconomyState.genesis();
        Rng rngB = new Rng(9L);
        for (long tick = 5001L; tick <= definition.lifetimeTicks(); tick++) {
            second.tick(tick, economyB, rngB);
            second.rateRequest(buffer);
            applied += buffer[Currencies.index(EMERALD)];
        }
        assertEquals(definition.peakImpact(EMERALD), applied, 1.0E-9D);
    }

    @Test
    void overBudgetRequestIsClampedAndReported() {
        MarketEventDefinition spike = new MarketEventDefinition("test_spike", MarketEventKind.WORLD_EVENT,
                MarketEventQuality.ESTIMATED, ImpactChannel.LIQUIDITY, 0L, 1L, 0L, 0L, weights(),
                peak(GOLD, 0.02D), "fxeasy.event.test_spike.title", "fxeasy.event.test_spike.body");
        MarketEventEngine engine = new MarketEventEngine(List.of(spike));
        engine.begin(spike, 0L);
        WorldEconomyState economy = WorldEconomyState.genesis();
        engine.tick(1L, economy, new Rng(1L));
        double[] buffer = new double[Currencies.count()];
        engine.rateRequest(buffer);
        assertEquals(0.02D, buffer[Currencies.index(GOLD)], 1.0E-12D);
        economy.tick(buffer);
        assertEquals(EconomyConstants.MAX_DRIVER_STEP_PER_BUCKET, economy.eventApplied(GOLD), 1.0E-12D);
        assertEquals(0.02D - EconomyConstants.MAX_DRIVER_STEP_PER_BUCKET, economy.eventClamped(GOLD),
                1.0E-12D);
    }

    @Test
    void familyCooldownBlocksRestartAcrossOrigins() {
        MarketEventEngine engine = engine();
        WorldEconomyState economy = WorldEconomyState.genesis();
        Rng rng = new Rng(7L);
        engine.observe(new WorldActivity(EconomySourceKind.RAID_STARTED, "minecraft:overworld", 0L, 0L, null));
        engine.tick(0L, economy, rng);
        assertTrue(engine.isRunning("raid_disruption"));
        long lifetime = MarketEventCatalog.RAID_DISRUPTION.lifetimeTicks();
        for (long tick = 1L; tick <= lifetime; tick++) {
            engine.tick(tick, economy, rng);
        }
        assertFalse(engine.isRunning("raid_disruption"));
        long ready = cooldownReadyTick(engine, "raid_disruption");
        assertEquals(lifetime + MarketEventCatalog.RAID_DISRUPTION.cooldownTicks(), ready);
        engine.observe(new WorldActivity(EconomySourceKind.RAID_STARTED, "minecraft:the_nether", lifetime + 1L, 0L,
                null));
        engine.tick(lifetime + 1L, economy, rng);
        assertFalse(engine.isRunning("raid_disruption"), "a second origin cannot shorten the cooldown");
        assertEquals(ready, cooldownReadyTick(engine, "raid_disruption"));
    }

    @Test
    void newsOnlyAndReportEventsContributeNothing() {
        MarketEventEngine engine = engine();
        engine.begin(MarketEventCatalog.KURUMI_MARGIN_REVIEW, 0L);
        engine.begin(MarketEventCatalog.BOSS_CONFIDENCE, 0L);
        engine.begin(MarketEventCatalog.GOLD_INDUSTRIAL_OUTPUT, 0L);
        engine.begin(MarketEventCatalog.BARTER_BOOM, 0L);
        WorldEconomyState economy = WorldEconomyState.genesis();
        engine.tick(1L, economy, new Rng(1L));
        double[] buffer = new double[Currencies.count()];
        engine.rateRequest(buffer);
        for (Currency currency : Currencies.all()) {
            assertEquals(0.0D, buffer[Currencies.index(currency)], 0.0D);
        }
        assertEquals(MarketDriverFrame.NO_RISK_FLOOR, engine.riskFloor(), 0.0D);
    }

    @Test
    void pricedEventRaisesTheRiskFloor() {
        MarketEventEngine engine = engine();
        engine.begin(MarketEventCatalog.RAID_DISRUPTION, 0L);
        engine.tick(1L, WorldEconomyState.genesis(), new Rng(1L));
        assertEquals(MarketDriverFrame.NO_RISK_FLOOR + 0.005D * MarketEventEngine.RISK_PER_LOG, engine.riskFloor(),
                1.0E-9D);
    }

    @Test
    void rumourDenialReturnsPremiumToZeroWithoutOvershoot() {
        MarketEventEngine engine = engine();
        engine.rumorConfirmChance(0.0D);
        MarketEventDefinition definition = MarketEventCatalog.PIGLIN_FESTIVAL;
        engine.begin(definition, 0L);
        WorldEconomyState economy = WorldEconomyState.genesis();
        Rng rng = new Rng(3L);
        double[] buffer = new double[Currencies.count()];
        double cumulative = 0.0D;
        double lowest = 0.0D;
        boolean sawPremium = false;
        for (long tick = 1L; tick <= definition.lifetimeTicks(); tick++) {
            engine.tick(tick, economy, rng);
            engine.rateRequest(buffer);
            cumulative += buffer[Currencies.index(GOLD)];
            lowest = Math.min(lowest, cumulative);
            sawPremium |= cumulative > 1.0E-6D;
        }
        assertTrue(sawPremium, "the rumour must have applied a premium before it was denied");
        assertEquals(0.0D, cumulative, 1.0E-9D);
        assertTrue(lowest >= -1.0E-9D, "the give back never overshoots negative");
        assertFalse(engine.isRunning(definition.id()));
    }

    @Test
    void milestonesSurviveSnapshotAndRestore() {
        MarketEventEngine engine = engine();
        engine.observe(WorldActivity.inDimension(EconomySourceKind.DIMENSION_ENTERED, "minecraft:the_nether", 0L));
        engine.tick(0L, WorldEconomyState.genesis(), new Rng(1L));
        assertTrue(engine.isRunning("dimension_expansion"));
        EventRow row = engine.snapshot();
        assertTrue(row.milestones().contains("dimension:minecraft:the_nether"));
        MarketEventEngine restored = MarketEventEngine.restore(row);
        restored.tuneForTest(EconomyConstants.TICKS_PER_DAY, 0.0D, 3);
        assertTrue(restored.snapshot().milestones().contains("dimension:minecraft:the_nether"));
        restored.observe(WorldActivity.inDimension(EconomySourceKind.DIMENSION_ENTERED, "minecraft:the_nether", 1L));
        restored.tick(1L, WorldEconomyState.genesis(), new Rng(1L));
        assertEquals(1, countRunning(restored, "dimension_expansion"));
    }

    @Test
    void loggingIntoTheStartingDimensionIsNotAnExpansion() {
        MarketEventEngine engine = engine();
        engine.observe(WorldActivity.inDimension(EconomySourceKind.DIMENSION_ENTERED, "minecraft:overworld", 0L));
        engine.tick(0L, WorldEconomyState.genesis(), new Rng(1L));
        assertFalse(engine.isRunning("dimension_expansion"), "the dimension a world starts in is not an expansion");
        engine.observe(WorldActivity.inDimension(EconomySourceKind.DIMENSION_ENTERED, "minecraft:the_nether", 1L));
        engine.tick(1L, WorldEconomyState.genesis(), new Rng(1L));
        assertTrue(engine.isRunning("dimension_expansion"), "reaching a second dimension is an expansion");
    }

    @Test
    void selectionIsStableForSameSeedAndEligibility() {
        List<String> first = runSelection(21L);
        List<String> second = runSelection(21L);
        assertFalse(first.isEmpty());
        assertEquals(first, second);
    }

    private static List<String> runSelection(long seed) {
        MarketEventEngine engine = new MarketEventEngine(MarketEventCatalog.ALL);
        engine.tuneForTest(EconomyConstants.TICKS_PER_BUCKET, 1.0D, 8);
        WorldEconomyState economy = WorldEconomyState.genesis();
        Rng rng = new Rng(seed);
        List<String> picked = new ArrayList<>();
        for (long tick = 0L; tick < 20000L; tick++) {
            engine.observe(WorldActivity.of(EconomySourceKind.BARTER_TERMINAL_USE, tick));
            engine.observe(WorldActivity.of(EconomySourceKind.PIGLIN_GOLD, GOLD, tick, 1L));
            engine.observe(WorldActivity.of(EconomySourceKind.NATURAL_ORE, DIAMOND, tick, 1L));
            engine.observe(WorldActivity.inDimension(EconomySourceKind.DIMENSION_ENTERED, "minecraft:the_nether",
                    tick));
            engine.tick(tick, economy, rng);
        }
        for (MarketEventState state : engine.snapshot().events()) {
            if (MarketEventCatalog.byId(state.definitionId()).kind() == MarketEventKind.FLAVOR_EVENT) {
                picked.add(String.format("%012d:%s", state.announcementTick(), state.definitionId()));
            }
        }
        Collections.sort(picked);
        return picked;
    }

    private static int countRunning(MarketEventEngine engine, String id) {
        int count = 0;
        for (MarketEventState state : engine.snapshot().events()) {
            if (state.definitionId().equals(id) && !state.archived()) {
                count++;
            }
        }
        return count;
    }

    private static long cooldownReadyTick(MarketEventEngine engine, String family) {
        for (EventRow.CooldownRow row : engine.snapshot().cooldowns()) {
            if (row.family().equals(family)) {
                return row.readyTick();
            }
        }
        return Long.MIN_VALUE;
    }

    private static double[] weights() {
        double[] weight = new double[Currencies.count()];
        Arrays.fill(weight, 1.0D);
        return weight;
    }

    private static double[] peak(Currency currency, double value) {
        double[] impact = new double[Currencies.count()];
        impact[Currencies.index(currency)] = value;
        return impact;
    }
}
