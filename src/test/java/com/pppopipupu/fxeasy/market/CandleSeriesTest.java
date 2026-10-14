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
            series.accept((long) i * period + 1L, i);
        }
        List<Candle> closed = series.snapshot(false);
        assertEquals(Timeframe.MAX_CANDLES, closed.size());
        for (int i = 1; i < closed.size(); i++) {
            assertTrue(closed.get(i).close() > closed.get(i - 1).close());
        }
        assertEquals(accepts - 1.0D, closed.getLast().close(), 1.0E-9D);
    }

    @Test
    void formingCandleTracksHighAndLow() {
        CandleSeries series = new CandleSeries(Timeframe.M15);
        series.accept(1L, 10.0D);
        series.accept(2L, 12.5D);
        series.accept(3L, 9.0D);
        Candle forming = series.current();
        assertEquals(10.0D, forming.open(), 1.0E-9D);
        assertEquals(12.5D, forming.high(), 1.0E-9D);
        assertEquals(9.0D, forming.low(), 1.0E-9D);
        assertEquals(9.0D, forming.close(), 1.0E-9D);
        assertTrue(series.snapshot(true).size() == 1);
    }
}
