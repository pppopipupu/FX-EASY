package com.pppopipupu.fxeasy.client.panel;

import module java.base;

import com.pppopipupu.fxeasy.client.FxClientState;
import com.pppopipupu.fxeasy.client.FxText;
import com.pppopipupu.fxeasy.market.Candle;
import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.CurrencyPair;
import com.pppopipupu.fxeasy.market.TimedCandle;
import com.pppopipupu.fxeasy.net.FxEventsPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

/// Chart of one series: viewport (bar width plus left index), price scale, the candles or an interpolated
/// line over the closes, the volume bars (candles only), two moving averages, both axes, the crosshair readout
/// and the last price tag. The window maths are pure, so the viewport and the scale can be unit tested without
/// a client.
public final class FxChartView {

    public static final int MIN_BAR = 3;
    public static final int MAX_BAR = 24;
    private static final int MA_FAST = 7;
    private static final int MA_SLOW = 25;
    private static final double VOLUME_SHARE = 0.24D;
    private static final int GRID_LINES = 4;
    private static final int TIME_LABELS = 4;
    private static final int MARKER_RADIUS = 3;
    private static final int READOUT_GAP = 6;
    private static final int TIME_TAG = 28;

    private int barWidth = 5;
    private int first;
    private boolean follow = true;
    private boolean dragging;
    private int dragFirst;
    private double dragX;
    private final List<Candle> series = new ArrayList<>();
    private final Map<Integer, List<Component>> markers = new HashMap<>();

    public int barWidth() {
        return this.barWidth;
    }

    public boolean dragging() {
        return this.dragging;
    }

    public int visibleCount(int plotWidth) {
        return Math.max(1, plotWidth / this.barWidth);
    }

    public int firstIndex(int count, int plotWidth) {
        int visible = visibleCount(plotWidth);
        return clampFirst(this.follow ? count - visible : this.first, count, visible);
    }

    public static int clampFirst(int first, int count, int visible) {
        return Math.clamp(first, 0, Math.max(0, count - visible));
    }

    /// Wheel zoom anchors the right edge of the window, so the newest candles stay where they are.
    public void zoom(int direction, int count, int plotWidth) {
        int visible = visibleCount(plotWidth);
        int start = firstIndex(count, plotWidth);
        this.barWidth = Math.clamp(this.barWidth + direction, MIN_BAR, MAX_BAR);
        int next = visibleCount(plotWidth);
        this.first = clampFirst(start + visible - next, count, next);
        this.follow = this.first + next >= count;
    }

    public void beginDrag(double x, int count, int plotWidth) {
        this.dragging = true;
        this.dragX = x;
        this.dragFirst = firstIndex(count, plotWidth);
    }

    public void dragTo(double x, int count, int plotWidth) {
        if (!this.dragging) {
            return;
        }
        int visible = visibleCount(plotWidth);
        int bars = (int) Math.round((this.dragX - x) / this.barWidth);
        this.first = clampFirst(this.dragFirst + bars, count, visible);
        this.follow = this.first + visible >= count;
    }

    public void endDrag() {
        this.dragging = false;
    }

    public void followLatest() {
        this.follow = true;
    }

    /// Average of the closes ending at [index], or NaN while the period is not filled yet.
    public static double movingAverage(List<Candle> candles, int period, int index) {
        if (index < period - 1 || index >= candles.size()) {
            return Double.NaN;
        }
        double sum = 0.0D;
        for (int offset = 0; offset < period; offset++) {
            sum += candles.get(index - offset).close();
        }
        return sum / period;
    }

    /// What traded during a candle, in items of the base currency of the pair: a size, so it is written as one,
    /// and a candle whose volume is a fraction of an item still reads as what it is rather than as nothing.
    public static String turnover(double value) {
        if (value <= 0.0D) {
            return "0";
        }
        if (value >= 1.0E9D) {
            return String.format(Locale.ROOT, "%.1fB", value / 1.0E9D);
        }
        if (value >= 1.0E6D) {
            return String.format(Locale.ROOT, "%.1fM", value / 1.0E6D);
        }
        if (value >= 1.0E3D) {
            return String.format(Locale.ROOT, "%.1fk", value / 1.0E3D);
        }
        if (value >= 100.0D) {
            return String.format(Locale.ROOT, "%.0f", value);
        }
        if (value >= 1.0D) {
            return String.format(Locale.ROOT, "%.1f", value);
        }
        return String.format(Locale.ROOT, "%.3f", value);
    }

    /// Index of the candle whose `[startTick, endTick)` interval contains [announcedTick], or -1 when no candle
    /// of the window covers it. A tick before [chartEpoch] sits in a segment whose anchors were not sent with the
    /// candle format, so it gets no marker at all instead of being moved onto the newest candle.
    public static int markerIndex(List<TimedCandle> candles, long announcedTick, long chartEpoch) {
        if (announcedTick < chartEpoch) {
            return -1;
        }
        for (int index = 0; index < candles.size(); index++) {
            if (candles.get(index).covers(announcedTick)) {
                return index;
            }
        }
        return -1;
    }

    /// The plain candles of the frame, in a list the view reuses: drawing a series costs no allocation per frame
    /// while the timed list stays available for the markers.
    private List<Candle> bars(List<TimedCandle> timed) {
        this.series.clear();
        for (TimedCandle candle : timed) {
            this.series.add(candle.candle());
        }
        return this.series;
    }

    /// Groups the messages of the selected pair by the candle whose interval contains their announced tick. The
    /// grouping is rebuilt from the current window every frame, so another pair or timeframe, a pan, a zoom and a
    /// new history snapshot can never leave a marker on a candle it does not belong to.
    private void collectMarkers(FxClientState state, List<TimedCandle> candles) {
        this.markers.clear();
        FxEventsPayload feed = state.events();
        if (feed == null) {
            return;
        }
        CurrencyPair pair = state.selectedPair();
        long epoch = state.chartEpoch();
        for (FxEventsPayload.Event event : feed.events()) {
            if (!concerns(event, Currencies.index(pair.base())) && !concerns(event, Currencies.index(pair.quote()))) {
                continue;
            }
            int index = markerIndex(candles, event.announcedTick(), epoch);
            if (index >= 0) {
                this.markers.computeIfAbsent(index, key -> new ArrayList<>()).add(FxText.get(event.titleKey()));
            }
        }
    }

    /// One diamond per interval that carries at least one message, on the time axis. Several messages in one
    /// interval aggregate into the single marker the axis has room for, and the hover readout lists them.
    private void drawMarkers(GuiGraphicsExtractor graphics, int start, int end, ScreenRectangle plot,
            ScreenRectangle timeAxis, double slot) {
        for (int index : this.markers.keySet()) {
            if (index < start || index >= end) {
                continue;
            }
            int centre = barX(plot.left(), (index - start) * slot) + Math.max(1, (int) (slot * 0.66D)) / 2;
            diamond(graphics, centre, timeAxis.top() + 1, FxTheme.ACCENT);
        }
    }

    /// A diamond of three rows, which reads as a message marker whatever the colour vision of the player and is
    /// distinct from the pressure triangles of the market list.
    private static void diamond(GuiGraphicsExtractor graphics, int x, int y, int colour) {
        FxUi.fill(graphics, x - 1, y + 1, x + 2, y + 2, colour);
        FxUi.fill(graphics, x, y, x + 1, y + 1, colour);
        FxUi.fill(graphics, x, y + 2, x + 1, y + 3, colour);
    }

    /// The event feed bounds neither per currency array, so every read of one goes through this instead of the
    /// accessor that indexes it blindly.
    private static boolean concerns(FxEventsPayload.Event event, int index) {
        return event.applied().length > index && event.peak().length > index && event.concerns(index);
    }

    public void draw(GuiGraphicsExtractor graphics, Font font, FxClientState state, ScreenRectangle plot,
            ScreenRectangle priceAxis, ScreenRectangle timeAxis, int mouseX, int mouseY) {
        FxUi.fill(graphics, plot.left(), plot.top(), plot.right(), plot.bottom(), FxTheme.CHART_BG);
        List<TimedCandle> timed = state.chartCandles();
        List<Candle> candles = bars(timed);
        if (candles.isEmpty()) {
            FxUi.text(graphics, font, plot.left() + 4, plot.top() + 4, FxText.get("fxeasy.panel.empty"), FxTheme.MUTED);
            return;
        }
        int count = candles.size();
        int visible = visibleCount(plot.width());
        int start = firstIndex(count, plot.width());
        int end = Math.min(count, start + visible);
        collectMarkers(state, timed);
        double low = Double.MAX_VALUE;
        double high = -Double.MAX_VALUE;
        double volumeMax = 0.0D;
        for (int index = start; index < end; index++) {
            Candle candle = candles.get(index);
            low = Math.min(low, candle.low());
            high = Math.max(high, candle.high());
            volumeMax = Math.max(volumeMax, candle.volume());
        }
        double padding = Math.max((high - low) * 0.06D, Math.abs(high) * 1.0E-4D);
        low -= padding;
        high += padding;
        double range = high - low;
        boolean bars = state.chartStyle() == FxClientState.ChartStyle.CANDLES;
        int priceTop = plot.top();
        int priceBottom = bars ? plot.top() + Math.max(6, (int) Math.round((plot.height() - 1) * (1.0D - VOLUME_SHARE))) : plot.bottom() - 1;
        int volumeBottom = plot.bottom() - 1;
        double slot = (double) plot.width() / visible;
        FxUi.pushClip(graphics, plot);
        for (int line = 0; line <= GRID_LINES; line++) {
            int y = priceTop + (int) Math.round((priceBottom - priceTop) * line / (double) GRID_LINES);
            FxUi.fill(graphics, plot.left(), y, plot.right(), y + 1, FxTheme.GRID);
        }
        if (bars && volumeMax > 0.0D) {
            for (int index = start; index < end; index++) {
                Candle candle = candles.get(index);
                if (candle.volume() <= 0.0D) {
                    continue;
                }
                int x = barX(plot.left(), (index - start) * slot);
                int width = Math.max(1, (int) (slot * 0.66D));
                int reach = volumeBottom - priceBottom - 1;
                int barTop = volumeBottom - Math.max(1, (int) Math.round(reach * (candle.volume() / volumeMax)));
                FxUi.fill(graphics, x, Math.min(barTop, volumeBottom), x + width, volumeBottom,
                        ARGB.multiplyAlpha(candle.isRising() ? FxTheme.UP : FxTheme.DOWN, 0.55F));
            }
        }
        int lineColour = seriesColour(candles, start);
        if (!bars) {
            stroke(graphics, closes(candles, start, end), end - start, plot, low, range, priceTop, priceBottom, slot,
                    lineColour, true);
        } else {
            for (int index = start; index < end; index++) {
                Candle candle = candles.get(index);
                int x = barX(plot.left(), (index - start) * slot);
                int width = Math.max(1, (int) (slot * 0.66D));
                int centre = x + width / 2;
                int colour = candle.isRising() ? FxTheme.UP : FxTheme.DOWN;
                int highY = priceY(candle.high(), low, range, priceTop, priceBottom);
                int lowY = priceY(candle.low(), low, range, priceTop, priceBottom);
                FxUi.fill(graphics, centre, highY, centre + FxTheme.WICK, Math.max(lowY, highY + 1), colour);
                int openY = priceY(candle.open(), low, range, priceTop, priceBottom);
                int closeY = priceY(candle.close(), low, range, priceTop, priceBottom);
                FxUi.fill(graphics, x, Math.min(openY, closeY), x + width, Math.max(openY, closeY) + 1, colour);
            }
        }
        average(graphics, candles, start, end, MA_FAST, plot, low, range, priceTop, priceBottom, slot, FxTheme.MA_FAST);
        average(graphics, candles, start, end, MA_SLOW, plot, low, range, priceTop, priceBottom, slot, FxTheme.MA_SLOW);
        FxUi.popClip(graphics);
        for (int line = 0; line <= GRID_LINES; line++) {
            int y = priceTop + (int) Math.round((priceBottom - priceTop) * line / (double) GRID_LINES);
            FxUi.right(graphics, font, priceAxis.right(), y - 4, FxText.literal(FxUi.price(high - range * line / GRID_LINES)), FxTheme.AXIS);
        }
        for (int label = 1; label <= TIME_LABELS; label++) {
            int index = start + (end - start) * label / (TIME_LABELS + 1);
            if (index >= end) {
                continue;
            }
            int age = (count - 1 - index) * state.selectedTimeframe().ticks() / (int) FxUi.TICKS_PER_MINUTE;
            Component text = FxText.literal(ageText(age));
            int centre = Math.max(font.width(text) / 2, barX(plot.left(), (index - start) * slot));
            FxUi.center(graphics, font, centre, timeAxis.top() + 1, text, FxTheme.AXIS);
        }
        Candle last = candles.getLast();
        int lastY = priceY(last.close(), low, range, priceTop, priceBottom);
        FxUi.dashed(graphics, plot.left(), plot.right(), lastY, last.isRising() ? FxTheme.UP : FxTheme.DOWN, 2);
        FxControls.tag(graphics, font, FxUi.rect(priceAxis.left(), lastY - 4, priceAxis.width(), 8),
                FxText.literal(FxUi.price(last.close())), last.isRising() ? FxTheme.UP : FxTheme.DOWN);
        legend(graphics, font, candles, plot);
        drawMarkers(graphics, start, end, plot, timeAxis, slot);
        if (FxUi.hit(plot, mouseX, mouseY)) {
            crosshair(graphics, font, candles, state, plot, priceAxis, timeAxis, start, end, slot, low, range, priceTop,
                    priceBottom, mouseX, mouseY, bars);
        } else if (!bars) {
            circle(graphics, plot, plot.left() + markerColumn(end - start, slot),
                    priceY(candles.get(end - 1).close(), low, range, priceTop, priceBottom), lineColour);
        }
    }

    private double[] scratch = new double[0];

    /// Sample buffer shared by the line and the averages: it grows to the requested length and is returned as is,
    /// so the caller must write every entry it later reads.
    private double[] values(int count) {
        if (this.scratch.length < count) {
            this.scratch = new double[count];
        }
        return this.scratch;
    }

    private double[] closes(List<Candle> candles, int start, int end) {
        double[] values = values(end - start);
        for (int index = start; index < end; index++) {
            values[index - start] = candles.get(index).close();
        }
        return values;
    }

    private void average(GuiGraphicsExtractor graphics, List<Candle> candles, int start, int end, int period,
            ScreenRectangle plot, double low, double range, int priceTop, int priceBottom, double slot, int colour) {
        int count = end - start;
        double[] values = values(count);
        for (int index = 0; index < count; index++) {
            values[index] = movingAverage(candles, period, start + index);
        }
        stroke(graphics, values, count, plot, low, range, priceTop, priceBottom, slot, colour, false);
    }

    /// The series sample under one pixel column of the stroke. A column between two samples reads a curve
    /// through the samples rather than the straight line between them: the curve passes through every close and
    /// never overshoots one, but it leaves each sample with a single slope instead of a corner, which is what
    /// keeps a line that changes direction every candle from reading as a saw. A column past the last sample is a
    /// time the chart holds no price for and draws nothing instead of repeating the last one as a flat line into
    /// the future, and a window of a single sample carries its value on its own column alone.
    static double columnValue(double[] values, int count, double slot, int column) {
        double position = column / slot;
        if (count < 1 || position > count - 1.0D) {
            return Double.NaN;
        }
        if (count < 2) {
            return values[0];
        }
        int index = Math.min((int) position, count - 2);
        double left = values[index];
        double right = values[index + 1];
        if (Double.isNaN(left) || Double.isNaN(right)) {
            return Double.NaN;
        }
        double t = position - index;
        double start = tangent(values, count, index);
        double end = tangent(values, count, index + 1);
        double squared = t * t;
        return (2.0D * squared * t - 3.0D * squared + 1.0D) * left
                + (squared * t - 2.0D * squared + t) * start
                + (-2.0D * squared * t + 3.0D * squared) * right
                + (squared * t - squared) * end;
    }

    /// Slope the curve leaves one sample with: zero where the sample is a high or a low of the window, and
    /// otherwise the harmonic mean of the slopes of the two segments that meet there, which is the shape
    /// preserving choice - the curve takes the slope of the flatter side, so a segment can never swing past a
    /// close it runs between. A sample at the edge of the window, and one whose neighbour is missing because an
    /// average is not filled yet, takes the slope of the one segment it has.
    private static double tangent(double[] values, int count, int index) {
        double before = index > 0 ? values[index] - values[index - 1] : Double.NaN;
        double after = index + 1 < count ? values[index + 1] - values[index] : Double.NaN;
        boolean hasBefore = !Double.isNaN(before);
        boolean hasAfter = !Double.isNaN(after);
        if (!hasBefore && !hasAfter) {
            return 0.0D;
        }
        if (!hasBefore) {
            return after;
        }
        if (!hasAfter) {
            return before;
        }
        if (before * after <= 0.0D) {
            return 0.0D;
        }
        return 2.0D * before * after / (before + after);
    }

    /// The column the newest sample of the window stands on. The stroke stops there, and so does the marker that
    /// follows the pointer, so a circle never lands on a time the chart has no price for.
    static int markerColumn(int count, double slot) {
        return (int) ((count - 1) * slot);
    }

    /// The column the circle takes for a pointer at [mouseX] on a plot starting at [left]: the pointer's own
    /// column while it is over the series, the first sample's once it runs off the left, and the newest sample's
    /// once it runs past the end into the future the chart draws nothing in.
    static int markerColumnAt(int mouseX, int left, int count, double slot) {
        return Math.clamp(mouseX - left, 0, markerColumn(count, slot));
    }

    /// Strokes the series one pixel column at a time, joining each column to the previous one, so a NaN sample
    /// (an average whose period is not filled) breaks the line instead of drawing through it.
    private static void stroke(GuiGraphicsExtractor graphics, double[] values, int count, ScreenRectangle plot,
            double low, double range, int priceTop, int priceBottom, double slot, int colour, boolean area) {
        if (count < 2) {
            return;
        }
        double previousY = Double.NaN;
        for (int column = 0; column < plot.width(); column++) {
            double value = columnValue(values, count, slot, column);
            if (Double.isNaN(value)) {
                previousY = Double.NaN;
                continue;
            }
            int x = plot.left() + column;
            double y = priceY(value, low, range, priceTop, priceBottom);
            if (area) {
                FxUi.gradient(graphics, x, (int) Math.round(y), x + 1, priceBottom + 1,
                        ARGB.multiplyAlpha(colour, 0.3F), ARGB.multiplyAlpha(colour, 0.0F));
            }
            span(graphics, x, Double.isNaN(previousY) ? y : previousY, y, colour);
            previousY = y;
        }
    }

    /// Fills the span one column of the stroke covers: the rows it runs through, with the two rows it covers
    /// only in part drawn at the fraction they are covered by. A line that moves less than a pixel per column
    /// would otherwise snap between two rows and read as a staircase rather than as a line. The rows between the
    /// two ends are one rectangle, so a steep column costs the same three draws a flat one does.
    private static void span(GuiGraphicsExtractor graphics, int x, double from, double to, int colour) {
        double top = Math.min(from, to);
        double bottom = Math.max(from, to) + 1.0D;
        int first = (int) Math.floor(top);
        int last = (int) Math.floor(bottom - 1.0E-9D);
        if (last < first) {
            return;
        }
        if (first == last) {
            FxUi.fill(graphics, x, first, x + 1, first + 1, covered(colour, bottom - top));
            return;
        }
        FxUi.fill(graphics, x, first, x + 1, first + 1, covered(colour, first + 1.0D - top));
        if (last - first > 1) {
            FxUi.fill(graphics, x, first + 1, x + 1, last, colour);
        }
        FxUi.fill(graphics, x, last, x + 1, last + 1, covered(colour, bottom - last));
    }

    /// The colour of a stroke at the fraction of a pixel it covers there, which is what softens its two ends.
    private static int covered(int colour, double fraction) {
        int alpha = (int) Math.round(Math.clamp(fraction, 0.0D, 1.0D) * 255.0D);
        return ARGB.multiplyAlpha(colour, alpha / 255.0F);
    }

    private void legend(GuiGraphicsExtractor graphics, Font font, List<Candle> candles, ScreenRectangle plot) {
        double fast = movingAverage(candles, MA_FAST, candles.size() - 1);
        double slow = movingAverage(candles, MA_SLOW, candles.size() - 1);
        if (Double.isNaN(fast) && Double.isNaN(slow)) {
            return;
        }
        int x = plot.left() + 4;
        int y = plot.top() + 3;
        if (!Double.isNaN(fast)) {
            FxControls.legend(graphics, font, x, y, FxTheme.MA_FAST,
                    FxText.literal("MA" + MA_FAST + " " + FxUi.price(fast)));
            x += 8 + font.width(FxText.literal("MA" + MA_FAST + " " + FxUi.price(fast))) + 6;
        }
        if (!Double.isNaN(slow)) {
            FxControls.legend(graphics, font, x, y, FxTheme.MA_SLOW,
                    FxText.literal("MA" + MA_SLOW + " " + FxUi.price(slow)));
        }
    }

    private void crosshair(GuiGraphicsExtractor graphics, Font font, List<Candle> candles, FxClientState state,
            ScreenRectangle plot, ScreenRectangle priceAxis, ScreenRectangle timeAxis, int start, int end, double slot,
            double low, double range, int priceTop, int priceBottom, int mouseX, int mouseY, boolean bars) {
        int count = end - start;
        int index = Math.clamp(start + (int) ((mouseX - plot.left()) / slot), start, end - 1);
        int slotX = barX(plot.left(), (index - start) * slot);
        FxUi.fill(graphics, slotX, plot.top(), slotX + Math.max(1, (int) slot), plot.bottom(), FxTheme.SELECTED);
        int centre;
        int y;
        double price;
        if (bars) {
            centre = slotX + Math.max(1, (int) (slot * 0.66D)) / 2;
            y = Math.clamp(mouseY, priceTop, priceBottom);
            price = low + range * (priceBottom - y) / (priceBottom - priceTop);
        } else {
            int column = markerColumnAt(mouseX, plot.left(), count, slot);
            price = columnValue(closes(candles, start, end), count, slot, column);
            centre = plot.left() + column;
            y = priceY(price, low, range, priceTop, priceBottom);
        }
        FxUi.dashed(graphics, plot.left(), plot.right(), y, FxTheme.CROSSHAIR, 2);
        for (int line = priceTop; line < plot.bottom(); line += 4) {
            FxUi.fill(graphics, centre, line, centre + 1, Math.min(line + 2, plot.bottom()), FxTheme.CROSSHAIR);
        }
        if (!bars) {
            circle(graphics, plot, centre, y, seriesColour(candles, start));
        }
        FxControls.tag(graphics, font, FxUi.rect(priceAxis.left(), y - 4, priceAxis.width(), 8),
                FxText.literal(FxUi.price(price)), FxTheme.AXIS);
        int age = (candles.size() - 1 - index) * state.selectedTimeframe().ticks() / (int) FxUi.TICKS_PER_MINUTE;
        int tagX = Math.clamp(centre - TIME_TAG / 2, plot.left(), Math.max(plot.left(), plot.right() - TIME_TAG));
        FxControls.tag(graphics, font, FxUi.rect(tagX, timeAxis.top(), TIME_TAG, timeAxis.height()),
                FxText.literal(ageText(age)), FxTheme.AXIS);
        readout(graphics, font, candles, state, plot, priceTop, index, price, age, mouseX);
    }

    /// The box that reads the candle under the marker out: the localized name of every field in one column and
    /// its value in the next, on the half of the plot the pointer is not on, so the point it describes stays
    /// visible. [price] is what the marker reads, which under the line style is the interpolated rate at the
    /// pointer rather than the close of the candle it falls in.
    private void readout(GuiGraphicsExtractor graphics, Font font, List<Candle> candles, FxClientState state,
            ScreenRectangle plot, int priceTop, int index, double price, int age, int mouseX) {
        Candle candle = candles.get(index);
        Candle last = candles.getLast();
        double versusLast = last.close() == 0.0D ? 0.0D : (price / last.close() - 1.0D) * 100.0D;
        double change = candle.open() == 0.0D ? 0.0D : (candle.close() / candle.open() - 1.0D) * 100.0D;
        double amplitude = candle.open() == 0.0D ? 0.0D : (candle.high() - candle.low()) / candle.open() * 100.0D;
        List<Row> rows = new ArrayList<>(11);
        rows.add(new Row(null, FxText.literal(state.selectedPair().code() + " " + state.selectedTimeframe().label()
                + " " + ageText(age))));
        rows.add(new Row(FxText.get("fxeasy.panel.chart.rate"),
                FxText.literal(FxUi.price(price) + "  " + FxUi.percent(versusLast))));
        rows.add(new Row(FxText.get("fxeasy.panel.chart.open"), FxText.literal(FxUi.price(candle.open()))));
        rows.add(new Row(FxText.get("fxeasy.panel.chart.high"), FxText.literal(FxUi.price(candle.high()))));
        rows.add(new Row(FxText.get("fxeasy.panel.chart.low"), FxText.literal(FxUi.price(candle.low()))));
        rows.add(new Row(FxText.get("fxeasy.panel.chart.close"),
                FxText.literal(FxUi.price(candle.close()) + "  " + FxUi.percent(change))));
        rows.add(new Row(FxText.get("fxeasy.panel.chart.amplitude"), FxText.literal(FxUi.percent(amplitude))));
        rows.add(new Row(FxText.get("fxeasy.panel.chart.volume"),
                FxText.literal(turnover(candle.volume()) + " " + state.selectedPair().base().code())));
        addAverage(rows, candles, MA_FAST, index);
        addAverage(rows, candles, MA_SLOW, index);
        List<Component> messages = this.markers.get(index);
        if (messages != null) {
            for (Component message : messages) {
                rows.add(new Row(FxText.get("fxeasy.intel.marker"), message));
            }
        }
        int nameWidth = 0;
        for (Row row : rows) {
            if (row.name() != null) {
                nameWidth = Math.max(nameWidth, font.width(row.name()));
            }
        }
        int width = 0;
        for (Row row : rows) {
            width = Math.max(width, font.width(row.value()) + (row.name() == null ? 0 : nameWidth + READOUT_GAP));
        }
        int boxX = mouseX <= plot.left() + plot.width() / 2
                ? plot.right() - width - FxTheme.PAD * 2 - 3 : plot.left() + 3;
        int boxY = Math.min(priceTop + 14, plot.bottom() - rows.size() * (font.lineHeight + 1) - FxTheme.PAD * 2);
        ScreenRectangle box = FxUi.rect(boxX, boxY, width + FxTheme.PAD * 2,
                rows.size() * (font.lineHeight + 1) + FxTheme.PAD * 2);
        FxUi.fill(graphics, box.left(), box.top(), box.right(), box.bottom(), FxTheme.TOOLTIP);
        FxUi.border(graphics, box, ARGB.multiplyAlpha(candle.isRising() ? FxTheme.UP : FxTheme.DOWN, 0.7F));
        int lineY = box.top() + FxTheme.PAD;
        for (Row row : rows) {
            if (row.name() == null) {
                FxUi.text(graphics, font, box.left() + FxTheme.PAD, lineY, row.value(), FxTheme.TEXT);
            } else {
                FxUi.text(graphics, font, box.left() + FxTheme.PAD, lineY, row.name(), FxTheme.MUTED);
                FxUi.text(graphics, font, box.left() + FxTheme.PAD + nameWidth + READOUT_GAP, lineY, row.value(),
                        FxTheme.TEXT);
            }
            lineY += font.lineHeight + 1;
        }
    }

    /// One line of the readout: the localized name of a field and its value. A row without a name spans the box,
    /// which is what the header of the pair and the timeframe is.
    private record Row(Component name, Component value) {
    }

    /// A small circle on one point of the series, drawn as a one unit outline so the line stays readable through
    /// it whatever colour the series carries. It is kept inside [plot]: the newest candle of a window that has
    /// not filled up yet stands on the first column, and a marker there would spill off the chart and off the
    /// screen with it.
    private static void circle(GuiGraphicsExtractor graphics, ScreenRectangle plot, int x, int y, int colour) {
        int centre = Math.clamp(x, plot.left() + MARKER_RADIUS, plot.right() - MARKER_RADIUS);
        for (int offset = -MARKER_RADIUS; offset <= MARKER_RADIUS; offset++) {
            int half = (int) Math.round(Math.sqrt((double) (MARKER_RADIUS * MARKER_RADIUS - offset * offset)));
            FxUi.fill(graphics, centre - half, y + offset, centre - half + 1, y + offset + 1, colour);
            FxUi.fill(graphics, centre + half, y + offset, centre + half + 1, y + offset + 1, colour);
        }
    }

    /// Colour of the line over the window: the newest close read against the oldest open of the window, so a
    /// window that fell stays red even while its newest candle recovers.
    private static int seriesColour(List<Candle> candles, int start) {
        return candles.getLast().close() >= candles.get(start).open() ? FxTheme.UP : FxTheme.DOWN;
    }

    private static void addAverage(List<Row> rows, List<Candle> candles, int period, int index) {
        double average = movingAverage(candles, period, index);
        if (!Double.isNaN(average)) {
            rows.add(new Row(FxText.get("fxeasy.panel.chart.ma", Integer.toString(period)),
                    FxText.literal(FxUi.price(average))));
        }
    }

    /// Age of the candle a readout points at, in hours once it is two of them old, else in minutes.
    private static String ageText(int minutes) {
        return minutes >= 120 ? "-" + minutes / 60 + "h" : "-" + minutes + "m";
    }

    private static int barX(int left, double offset) {
        return left + (int) Math.round(offset);
    }

    private static int priceY(double price, double low, double range, int top, int bottom) {
        return (int) Math.round(pricePosition(price, low, range, top, bottom));
    }

    /// Row of a price as the stroke reads it, before it is rounded to a row of the plot: the curve is drawn
    /// between the rows, so a line that moves a fraction of a pixel still reads as a line.
    private static double pricePosition(double price, double low, double range, int top, int bottom) {
        return range <= 0.0D ? (top + bottom) / 2.0D : bottom - (price - low) / range * (bottom - top);
    }
}
