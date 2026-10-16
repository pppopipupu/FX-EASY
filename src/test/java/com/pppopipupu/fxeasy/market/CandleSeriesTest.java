package com.pppopipupu.fxeasy.market;

import module java.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CandleSeriesTest {

    @Test
    void keepsOnlyTheNewestCandles() {
        CandleSeries series = new CandleSeries(Timeframe.M1);
        int period = Timeframe.M1.ticks();
        int accepts = 2 * Timeframe.MAX_CANDLES + 4;
        for (int i = 0; i <= accepts; i++) {
            series.accept((long) i * period + 1L, i, 1.0D);
        }
        List<Candle> closed = series.snapshot(false);
        assertEquals(Timeframe.MAX_CANDLES, closed.size());
        for (int i = 1; i < closed.size(); i++) {
            assertTrue(closed.get(i).close() > closed.get(i - 1).close());
        }
        assertEquals(accepts - 1.0D, closed.getLast().close(), 1.0E-9D);
        assertEquals(2.0D, closed.getLast().volume(), 1.0E-9D,
                "a candle carries the volume of every tick that formed it");
    }

    @Test
    void formingCandleTracksHighAndLow() {
        CandleSeries series = new CandleSeries(Timeframe.M15);
        series.accept(1L, 10.0D, 2.0D);
        series.accept(2L, 12.5D, 3.0D);
        series.accept(3L, 9.0D, 0.0D);
        Candle forming = series.current();
        assertEquals(10.0D, forming.open(), 1.0E-9D);
        assertEquals(12.5D, forming.high(), 1.0E-9D);
        assertEquals(9.0D, forming.low(), 1.0E-9D);
        assertEquals(9.0D, forming.close(), 1.0E-9D);
        assertEquals(5.0D, forming.volume(), 1.0E-9D, "the volume of the ticks that formed it");
        assertTrue(series.snapshot(true).size() == 1);
    }

    /// A trade the players executed is booked into the candle being formed, in items of the base currency, and
    /// it is the same quantity as the volume the world traded on its own. A trade that arrives before the market
    /// has formed a candle, and one that names no size, are both dropped.
    @Test
    void playerVolumeLandsInTheFormingCandle() {
        CandleSeries series = new CandleSeries(Timeframe.M1);
        series.addVolume(500.0D);
        assertTrue(series.current() == null, "there is no candle before the first market tick");
        series.accept(1L, 10.0D, 2.0D);
        series.addVolume(500.0D);
        series.addVolume(0.0D);
        series.addVolume(-5.0D);
        assertEquals(502.0D, series.current().volume(), 1.0E-9D, "the world traded two items and a player five hundred");
    }
}
