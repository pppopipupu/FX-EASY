package com.pppopipupu.fxeasy.client.panel;

import module java.base;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.pppopipupu.fxeasy.net.FxEconomyPayload;

/// The market row marker has to say which side of a pair is under pressure without leaning on colour, so the
/// pressure level is turned into a direction the row draws as a triangle: down for an unabsorbed overhang, up
/// for a currency the world absorbs faster than it produces, and nothing for a normal level.
class FxMarketViewTest {

    @Test
    void aPressureLevelPointsTheWayTheMarkerDrawsIt() {
        assertEquals(1, FxMarketView.pressureDirection(FxEconomyPayload.PRESSURE_LOW));
        assertEquals(0, FxMarketView.pressureDirection(FxEconomyPayload.PRESSURE_NORMAL));
        assertEquals(-1, FxMarketView.pressureDirection(FxEconomyPayload.PRESSURE_HIGH));
        assertEquals(-1, FxMarketView.pressureDirection(FxEconomyPayload.PRESSURE_SURPLUS));
        assertEquals(-1, FxMarketView.pressureDirection(FxEconomyPayload.PRESSURE_SEVERE));
    }
}
