package com.pppopipupu.fxeasy.client.panel;

import module java.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.net.FxEconomyPayload;
import com.pppopipupu.fxeasy.net.FxEventsPayload;

/// The intel overlay derives everything it shows from the two payloads and the ticks they carry: the phase of a
/// message, the card its root event owns, the direction of two flow estimates and what a pressure level says
/// about how much of the revaluation is already priced. All of it is pure, so it is pinned here instead of
/// through a client.
class FxWorldIntelTest {

    @Test
    void thePhaseOfAMessageFollowsTheTicksThePayloadCarries() {
        assertEquals(FxWorldIntel.Phase.ANNOUNCED, FxWorldIntel.phase(100L, 200L, 99L, false));
        assertEquals(FxWorldIntel.Phase.ACTIVE, FxWorldIntel.phase(100L, 200L, 100L, false));
        assertEquals(FxWorldIntel.Phase.ACTIVE, FxWorldIntel.phase(100L, 200L, 174L, false));
        assertEquals(FxWorldIntel.Phase.RESOLVING, FxWorldIntel.phase(100L, 200L, 175L, false));
        assertEquals(FxWorldIntel.Phase.ARCHIVED, FxWorldIntel.phase(100L, 200L, 200L, false));
        assertEquals(FxWorldIntel.Phase.ARCHIVED, FxWorldIntel.phase(100L, 200L, 150L, true),
                "the archived flag of the payload wins over the clock");
    }

    @Test
    void aRepeatUpdateReplacesTheCardOfItsRoot() {
        List<FxEventsPayload.Event> cards = FxWorldIntel.cards(List.of(
                event("raid", 4L, 300L), event("raid", 7L, 300L), event("gold", 5L, 200L)));
        assertEquals(2, cards.size(), "an update of a known root replaces its card instead of adding a second one");
        assertEquals(7L, cards.getFirst().revision());
        assertEquals("raid", cards.getFirst().rootId());
        assertEquals("gold", cards.getLast().rootId(), "the newest announcement comes first");
    }

    @Test
    void aFlowOnlyHasADirectionOnceItClearsTheCapacityFloor() {
        assertEquals(1, FxWorldIntel.flowDirection(5.0D, 1.0D, 10.0D));
        assertEquals(-1, FxWorldIntel.flowDirection(1.0D, 5.0D, 10.0D));
        assertEquals(0, FxWorldIntel.flowDirection(1.0D, 1.4D, 10.0D),
                "four tenths of a unit against a capacity of ten is not a direction");
        assertEquals(1, FxWorldIntel.flowDirection(0.5D, 0.0D, 0.0D),
                "without a capacity the floor of the estimate decides");
    }

    @Test
    void aStrongerPressureLeavesLessOfTheRevaluationPriced() {
        assertEquals("fxeasy.intel.priced.low", FxWorldIntel.pricedKey(FxEconomyPayload.PRESSURE_LOW));
        assertEquals("fxeasy.intel.priced.normal", FxWorldIntel.pricedKey(FxEconomyPayload.PRESSURE_NORMAL));
        assertEquals("fxeasy.intel.priced.high", FxWorldIntel.pricedKey(FxEconomyPayload.PRESSURE_HIGH));
        assertEquals("fxeasy.intel.priced.severe", FxWorldIntel.pricedKey(FxEconomyPayload.PRESSURE_SURPLUS));
        assertEquals("fxeasy.intel.priced.severe", FxWorldIntel.pricedKey(FxEconomyPayload.PRESSURE_SEVERE));
    }

    /// The per-currency rows of the economy view are the currencies of the session, in the order its table names
    /// them, and the view reads a report at [Currencies#index], so a report that does not carry one entry per
    /// traded currency would be read past its own end and is refused at the door.
    @Test
    void anEconomyReportCarriesOneEntryPerCurrencyOfTheSession() {
        FxEconomyPayload report = economy(Currencies.count());
        assertEquals(Currencies.count(), report.supplyFlow().length);
        assertEquals(Currencies.count(), report.demandFlow().length);
        assertEquals(Currencies.count(), report.capacity().length);
        assertEquals(Currencies.count(), report.pressure().length);
        assertThrows(IllegalArgumentException.class, () -> economy(Currencies.count() + 1));
        assertThrows(IllegalArgumentException.class, () -> economy(Currencies.count() - 1));
    }

    /// A report whose per-currency arrays carry the given number of entries, so a case can hand the view one
    /// that is not sized by the table of the session.
    private static FxEconomyPayload economy(int entries) {
        double[] flows = new double[entries];
        int[] pressure = new int[entries];
        return new FxEconomyPayload(FxEconomyPayload.STATUS_FRESH, 1L, 100L, 100L, 1L, 10,
                flows.clone(), flows.clone(), pressure, flows.clone(), flows.clone(), flows.clone(),
                new int[FxEconomyPayload.COVERAGE_ENTRIES], new int[0], new long[0]);
    }

    private static FxEventsPayload.Event event(String rootId, long revision, long announcedTick) {
        double[] values = new double[Currencies.count()];
        return new FxEventsPayload.Event("event-" + revision, rootId, 0, 0, 0, revision, announcedTick,
                announcedTick + 100L, values, values.clone(), "fxeasy.event.test.title", "fxeasy.event.test.body", false);
    }
}
