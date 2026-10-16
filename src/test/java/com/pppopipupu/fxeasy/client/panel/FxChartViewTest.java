package com.pppopipupu.fxeasy.client.panel;

import module java.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.pppopipupu.fxeasy.market.Candle;
import com.pppopipupu.fxeasy.market.TimedCandle;

/// The line style strokes one column per design unit, reading a smooth curve through the samples it falls
/// between. A stroke that stepped sample by sample is what made the line chart look like candles again, and one
/// that ran straight from close to close left a corner on every candle, so the columns have to move by the slope
/// of the curve and stay inside the series: past the newest sample there is no price yet, so the stroke stops
/// there instead of running flat into the future, and the circle that marks the point being read stops with it.
/// The event markers hang off the same window: a tick belongs to the candle whose half open interval contains
/// it, and a tick from before the chart epoch belongs to no candle at all.
class FxChartViewTest {

    @Test
    void columnsPassThroughEverySample() {
        double[] values = { 0.0D, 100.0D, 100.0D, 300.0D };
        assertEquals(0.0D, FxChartView.columnValue(values, 4, 10.0D, 0));
        assertEquals(100.0D, FxChartView.columnValue(values, 4, 10.0D, 10));
        assertEquals(100.0D, FxChartView.columnValue(values, 4, 10.0D, 20));
        assertEquals(300.0D, FxChartView.columnValue(values, 4, 10.0D, 30));
    }

    /// The curve runs between two closes without ever swinging past one of them: a column of a segment stays
    /// inside the closes it joins, which is what a shape preserving curve is for, and a segment of two equal
    /// closes is flat rather than bulging between them.
    @Test
    void columnsStayBetweenTheSamplesTheyJoin() {
        double[] values = { 0.0D, 1000.0D, 0.0D, 500.0D };
        for (int segment = 0; segment < 3; segment++) {
            double from = values[segment];
            double to = values[segment + 1];
            for (int column = segment * 10; column <= (segment + 1) * 10; column++) {
                double value = FxChartView.columnValue(values, 4, 10.0D, column);
                assertTrue(value >= Math.min(from, to) - 1.0E-9D && value <= Math.max(from, to) + 1.0E-9D,
                        "column " + column + " reads " + value + " between " + from + " and " + to);
            }
        }
    }

    /// The curve leaves a sample with one slope rather than a corner, so a line that turns every candle reads as
    /// a line: the two columns around a sample move by the same amount, which is what a stroke that ran straight
    /// from close to close could not do.
    @Test
    void theCurveHasNoCornerAtASample() {
        double[] values = { 0.0D, 1000.0D, 1500.0D };
        double into = FxChartView.columnValue(values, 3, 40.0D, 40) - FxChartView.columnValue(values, 3, 40.0D, 39);
        double out = FxChartView.columnValue(values, 3, 40.0D, 41) - FxChartView.columnValue(values, 3, 40.0D, 40);
        assertEquals(into, out, 2.0D, "the curve turns at the sample rather than breaking there");
    }

    @Test
    void columnsPastTheSeriesDrawNothing() {
        double[] values = { 0.0D, 100.0D };
        assertEquals(100.0D, FxChartView.columnValue(values, 2, 10.0D, 10), "the newest sample keeps its own column");
        assertTrue(Double.isNaN(FxChartView.columnValue(values, 2, 10.0D, 11)),
                "a column past the newest sample is a time the chart holds no price for");
        assertTrue(Double.isNaN(FxChartView.columnValue(values, 2, 10.0D, 900)),
                "the line does not run flat into the future");
    }

    @Test
    void aWindowOfOneSampleCarriesItsValueOnItsOwnColumn() {
        double[] values = { 42.0D };
        assertEquals(42.0D, FxChartView.columnValue(values, 1, 10.0D, 0));
        assertTrue(Double.isNaN(FxChartView.columnValue(values, 1, 10.0D, 1)));
    }

    @Test
    void theMarkerColumnIsWhereTheStrokeStops() {
        double[] values = { 0.0D, 50.0D, 100.0D };
        int last = FxChartView.markerColumn(3, 7.5D);
        assertEquals(15, last);
        assertEquals(100.0D, FxChartView.columnValue(values, 3, 7.5D, last), "the circle sits on the line");
        assertTrue(Double.isNaN(FxChartView.columnValue(values, 3, 7.5D, last + 1)),
                "one column further there is nothing to read");
    }

    @Test
    void theCircleFollowsThePointerAndStopsAtTheSeries() {
        assertEquals(0, FxChartView.markerColumnAt(2, 2, 3, 7.5D), "the pointer on the oldest sample");
        assertEquals(7, FxChartView.markerColumnAt(9, 2, 3, 7.5D), "the pointer between two samples");
        assertEquals(15, FxChartView.markerColumnAt(17, 2, 3, 7.5D), "the pointer on the newest sample");
        assertEquals(15, FxChartView.markerColumnAt(300, 2, 3, 7.5D), "the pointer past the series stops on it");
        assertEquals(0, FxChartView.markerColumnAt(-40, 2, 3, 7.5D), "the pointer left of the plot stops on it");
    }

    /// A series that only ever rises draws a curve that only ever rises: the stroke never steps back on itself
    /// between two closes, so a column of the window reads at least what the column before it read.
    @Test
    void aRisingSeriesDrawsARisingCurve() {
        double[] values = { 0.0D, 100.0D, 300.0D, 700.0D };
        double previous = FxChartView.columnValue(values, 4, 40.0D, 0);
        for (int column = 1; column <= 120; column++) {
            double value = FxChartView.columnValue(values, 4, 40.0D, column);
            assertTrue(value >= previous - 1.0E-9D, "column " + column + " reads " + value + " after " + previous);
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
