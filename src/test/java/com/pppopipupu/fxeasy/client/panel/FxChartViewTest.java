package com.pppopipupu.fxeasy.client.panel;

import module java.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.pppopipupu.fxeasy.market.Candle;
import com.pppopipupu.fxeasy.market.TimedCandle;

/// The line style strokes one column per design unit, interpolating between the samples it falls between. A
/// stroke that stepped sample by sample is what made the line chart look like candles again, so the columns
/// have to move by the slope of the segment and stay inside the series. The event markers hang off the same
/// window: a tick belongs to the candle whose half open interval contains it, and a tick from before the chart
/// epoch belongs to no candle at all.
class FxChartViewTest {

    @Test
    void columnsInterpolateBetweenSamples() {
        double[] values = { 0.0D, 100.0D, 100.0D, 300.0D };
        assertEquals(0.0D, FxChartView.columnValue(values, 4, 10.0D, 0));
        assertEquals(50.0D, FxChartView.columnValue(values, 4, 10.0D, 5));
        assertEquals(90.0D, FxChartView.columnValue(values, 4, 10.0D, 9));
        assertEquals(100.0D, FxChartView.columnValue(values, 4, 10.0D, 10));
        assertEquals(200.0D, FxChartView.columnValue(values, 4, 10.0D, 25));
    }

    @Test
    void columnsPastTheSeriesRepeatTheLastSample() {
        double[] values = { 0.0D, 100.0D };
        assertEquals(100.0D, FxChartView.columnValue(values, 2, 10.0D, 10));
        assertEquals(100.0D, FxChartView.columnValue(values, 2, 10.0D, 900));
    }

    @Test
    void neighbouringColumnsMoveByTheSlopeOfTheSegment() {
        double[] values = { 0.0D, 1000.0D, 0.0D };
        double slope = 1000.0D / 40.0D;
        double previous = FxChartView.columnValue(values, 3, 40.0D, 0);
        for (int column = 1; column < 80; column++) {
            double value = FxChartView.columnValue(values, 3, 40.0D, column);
            assertEquals(slope, Math.abs(value - previous), 1.0E-9D);
            previous = value;
        }
    }

    @Test
    void unfilledAverageBreaksTheStroke() {
        double[] values = { Double.NaN, Double.NaN, 5.0D, 7.0D };
        assertTrue(Double.isNaN(FxChartView.columnValue(values, 4, 10.0D, 3)));
        assertTrue(Double.isNaN(FxChartView.columnValue(values, 4, 10.0D, 15)));
        assertEquals(6.0D, FxChartView.columnValue(values, 4, 10.0D, 25));
    }

    @Test
    void aMarkerSitsOnTheCandleWhoseIntervalContainsItsTick() {
        List<TimedCandle> candles = List.of(candle(0L, 10L), candle(10L, 20L), candle(20L, 30L));
        assertEquals(0, FxChartView.markerIndex(candles, 0L, 0L));
        assertEquals(1, FxChartView.markerIndex(candles, 19L, 0L));
        assertEquals(2, FxChartView.markerIndex(candles, 20L, 0L), "the interval is half open, so its start is inside");
        assertEquals(-1, FxChartView.markerIndex(candles, 30L, 0L), "a tick past the window has no candle to sit on");
    }

    @Test
    void aMarkerBeforeTheChartEpochIsNotDrawn() {
        List<TimedCandle> candles = List.of(candle(0L, 10L), candle(10L, 20L));
        assertEquals(-1, FxChartView.markerIndex(candles, 5L, 10L),
                "an old segment has no trustworthy time anchor, so its tick gets no marker");
        assertEquals(1, FxChartView.markerIndex(candles, 15L, 10L));
    }

    private static TimedCandle candle(long startTick, long endTick) {
        return new TimedCandle(startTick, endTick, Candle.of(1.0D));
    }
}
