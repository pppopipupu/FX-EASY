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

    public static String turnover(double value) {
        if (value >= 1.0E9D) {
            return String.format(Locale.ROOT, "%.1fB", value / 1.0E9D);
        }
        if (value >= 1.0E6D) {
            return String.format(Locale.ROOT, "%.1fM", value / 1.0E6D);
        }
        if (value >= 1.0E3D) {
            return String.format(Locale.ROOT, "%.1fk", value / 1.0E3D);
        }
        return String.format(Locale.ROOT, "%.0f", value);
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
                int barTop = volumeBottom - (int) Math.round((volumeBottom - priceBottom - 1) * (candle.volume() / volumeMax));
                FxUi.fill(graphics, x, Math.min(barTop, volumeBottom), x + width, volumeBottom,
                        ARGB.multiplyAlpha(candle.isRising() ? FxTheme.UP : FxTheme.DOWN, 0.55F));
            }
        }
        if (!bars) {
            stroke(graphics, closes(candles, start, end), end - start, plot, low, range, priceTop, priceBottom, slot,
                    candles.getLast().close() >= candles.get(start).open() ? FxTheme.UP : FxTheme.DOWN, true);
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
                    priceBottom, mouseX, mouseY);
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

    /// The series sample under one pixel column of the stroke: a column between two samples moves by the slope of
    /// that segment, and a column past the last sample repeats it.
    static double columnValue(double[] values, int count, double slot, int column) {
        double position = Math.min(column / slot, count - 1.0D);
        int index = Math.min((int) position, count - 2);
        return values[index] + (values[index + 1] - values[index]) * (position - index);
    }

    /// Strokes the series one pixel column at a time, joining each column to the previous one, so a NaN sample
    /// (an average whose period is not filled) breaks the line instead of drawing through it.
    private static void stroke(GuiGraphicsExtractor graphics, double[] values, int count, ScreenRectangle plot,
            double low, double range, int priceTop, int priceBottom, double slot, int colour, boolean area) {
        if (count < 2) {
            return;
        }
        int previousY = Integer.MIN_VALUE;
        for (int column = 0; column < plot.width(); column++) {
            double value = columnValue(values, count, slot, column);
            if (Double.isNaN(value)) {
                previousY = Integer.MIN_VALUE;
                continue;
            }
            int x = plot.left() + column;
            int y = priceY(value, low, range, priceTop, priceBottom);
            if (area) {
                FxUi.gradient(graphics, x, y, x + 1, priceBottom + 1, ARGB.multiplyAlpha(colour, 0.3F),
                        ARGB.multiplyAlpha(colour, 0.0F));
            }
            int top = previousY == Integer.MIN_VALUE ? y : Math.min(previousY, y);
            int bottom = previousY == Integer.MIN_VALUE ? y : Math.max(previousY, y);
            FxUi.fill(graphics, x, top, x + 1, bottom + 1, colour);
            previousY = y;
        }
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
            double low, double range, int priceTop, int priceBottom, int mouseX, int mouseY) {
        int index = Math.clamp(start + (int) ((mouseX - plot.left()) / slot), start, end - 1);
        int slotX = barX(plot.left(), (index - start) * slot);
        int centre = slotX + Math.max(1, (int) (slot * 0.66D)) / 2;
        int y = Math.clamp(mouseY, priceTop, priceBottom);
        FxUi.fill(graphics, slotX, plot.top(), slotX + Math.max(1, (int) slot), plot.bottom(), FxTheme.SELECTED);
        FxUi.dashed(graphics, plot.left(), plot.right(), y, FxTheme.CROSSHAIR, 2);
        for (int line = priceTop; line < plot.bottom(); line += 4) {
            FxUi.fill(graphics, centre, line, centre + 1, Math.min(line + 2, plot.bottom()), FxTheme.CROSSHAIR);
        }
        double price = low + range * (priceBottom - y) / (priceBottom - priceTop);
        FxControls.tag(graphics, font, FxUi.rect(priceAxis.left(), y - 4, priceAxis.width(), 8),
                FxText.literal(FxUi.price(price)), FxTheme.AXIS);
        Candle candle = candles.get(index);
        Candle last = candles.getLast();
        int age = (candles.size() - 1 - index) * state.selectedTimeframe().ticks() / (int) FxUi.TICKS_PER_MINUTE;
        FxControls.tag(graphics, font, FxUi.rect(centre - 14, timeAxis.top(), 28, timeAxis.height()),
                FxText.literal(ageText(age)), FxTheme.AXIS);
        double versusLast = last.close() == 0.0D ? 0.0D : (price / last.close() - 1.0D) * 100.0D;
        double change = candle.open() == 0.0D ? 0.0D : (candle.close() / candle.open() - 1.0D) * 100.0D;
        int accent = candle.isRising() ? FxTheme.UP : FxTheme.DOWN;
        List<Component> lines = new ArrayList<>(9);
        lines.add(FxText.literal(state.selectedPair().code() + " " + state.selectedTimeframe().label()
                + " " + ageText(age)));
        lines.add(FxText.literal("P " + FxUi.price(price) + "  " + FxUi.percent(versusLast)));
        lines.add(FxText.literal("O " + FxUi.price(candle.open())));
        lines.add(FxText.literal("H " + FxUi.price(candle.high())));
        lines.add(FxText.literal("L " + FxUi.price(candle.low())));
        lines.add(FxText.literal("C " + FxUi.price(candle.close()) + "  " + FxUi.percent(change)));
        lines.add(FxText.literal("V " + turnover(candle.volume())));
        addAverage(lines, candles, MA_FAST, index);
        addAverage(lines, candles, MA_SLOW, index);
        List<Component> messages = this.markers.get(index);
        if (messages != null) {
            for (Component message : messages) {
                lines.add(FxText.join(FxText.get("fxeasy.intel.marker"), message));
            }
        }
        int width = 0;
        for (Component line : lines) {
            width = Math.max(width, font.width(line));
        }
        int boxX = mouseX <= plot.left() + plot.width() / 2 ? plot.right() - width - FxTheme.PAD * 2 - 3 : plot.left() + 3;
        int boxY = Math.min(priceTop + 14, plot.bottom() - lines.size() * (font.lineHeight + 1) - FxTheme.PAD * 2);
        ScreenRectangle box = FxUi.rect(boxX, boxY, width + FxTheme.PAD * 2, lines.size() * (font.lineHeight + 1) + FxTheme.PAD * 2);
        FxUi.fill(graphics, box.left(), box.top(), box.right(), box.bottom(), FxTheme.TOOLTIP);
        FxUi.border(graphics, box, ARGB.multiplyAlpha(accent, 0.7F));
        int lineY = box.top() + FxTheme.PAD;
        for (Component line : lines) {
            FxUi.text(graphics, font, box.left() + FxTheme.PAD, lineY, line, FxTheme.TEXT);
            lineY += font.lineHeight + 1;
        }
    }

    private static void addAverage(List<Component> lines, List<Candle> candles, int period, int index) {
        double average = movingAverage(candles, period, index);
        if (!Double.isNaN(average)) {
            lines.add(FxText.literal("MA" + period + " " + FxUi.price(average)));
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
        return range <= 0.0D ? (top + bottom) / 2 : (int) Math.round(bottom - (price - low) / range * (bottom - top));
    }
}
