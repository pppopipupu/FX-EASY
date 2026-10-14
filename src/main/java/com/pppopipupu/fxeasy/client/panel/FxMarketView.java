package com.pppopipupu.fxeasy.client.panel;

import module java.base;

import com.pppopipupu.fxeasy.client.FxClientState;
import com.pppopipupu.fxeasy.client.FxText;
import com.pppopipupu.fxeasy.content.FxCurrencies;
import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;
import com.pppopipupu.fxeasy.market.CurrencyPair;
import com.pppopipupu.fxeasy.market.Timeframe;
import com.pppopipupu.fxeasy.net.FxEconomyPayload;
import com.pppopipupu.fxeasy.net.FxEventsPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;

/// Market list page: one row per tradable pair with both currency items, the last price, the change over the
/// ticks of the selected timeframe, a sparkline of the same window and the fundamentals marker of the pair. The
/// screen owns the click, so a row only reports that it was hit.
public final class FxMarketView {

    private static final int ROW_HEIGHT = 38;
    private static final int ICON_SCALE = 2;
    private static final int ICON = 16 * ICON_SCALE;
    private static final int SPARK_GAP = 8;
    private static final int SPARK_MIN_WIDTH = 24;
    private static final int SPARK_LEFT = 84;
    private static final int SCROLL_STEP = 3;
    /// Room for the fundamentals marker of a row: a triangle per currency of the pair and a diamond for a
    /// message that concerns either of them. The strip sits between the sparkline and the price column, so the
    /// price and change columns keep the width their own text needs.
    private static final int MARKER_STRIP = 18;
    private static final int MARKER_STEP = 5;

    private final boolean[] concernedCurrency = new boolean[Currencies.MAX];
    private final boolean[] concerned = new boolean[Currencies.MAX * (Currencies.MAX - 1) / 2];
    private int scroll;

    /// Returns the pair whose row was clicked this frame, or null.
    public CurrencyPair draw(GuiGraphicsExtractor graphics, Font font, FxClientState state, FxInput input, ScreenRectangle area) {
        FxUi.card(graphics, area);
        ScreenRectangle inner = FxUi.rect(area.left() + FxTheme.PAD, area.top() + FxTheme.PAD,
                area.width() - FxTheme.PAD * 2, area.height() - FxTheme.PAD * 2);
        int priceWidth = 0;
        int changeWidth = 0;
        for (CurrencyPair pair : Currencies.pairs()) {
            priceWidth = Math.max(priceWidth, font.width(FxText.literal(FxUi.price(state.price(pair)))));
            changeWidth = Math.max(changeWidth, font.width(FxText.literal(FxUi.percent(state.listingChange(pair, state.selectedTimeframe())))));
        }
        int changeRight = inner.right() - priceWidth - SPARK_GAP;
        int priceLeft = changeRight - changeWidth - SPARK_GAP;
        int markerLeft = priceLeft - SPARK_GAP - MARKER_STRIP;
        concernedFeed(state);
        FxUi.text(graphics, font, inner.left(), inner.top(), FxText.get("fxeasy.panel.list.pair"), FxTheme.MUTED);
        FxUi.right(graphics, font, inner.right(), inner.top(), FxText.get("fxeasy.panel.list.price"), FxTheme.MUTED);
        FxUi.right(graphics, font, changeRight, inner.top(), FxText.literal(state.selectedTimeframe().label()), FxTheme.MUTED);
        int top = inner.top() + FxTheme.ROW;
        int bottom = inner.bottom() - FxTheme.ROW;
        int visible = Math.max(1, (bottom - top) / ROW_HEIGHT);
        int maximum = Math.max(0, Currencies.pairs().size() - visible);
        if (input.scroll != 0.0D && input.hovered(area)) {
            this.scroll -= (int) Math.signum(input.scroll) * SCROLL_STEP;
            input.scroll = 0.0D;
        }
        this.scroll = Math.clamp(this.scroll, 0, maximum);
        CurrencyPair clicked = null;
        FxUi.pushClip(graphics, FxUi.rect(inner.left(), top, inner.width(), bottom - top));
        for (int index = this.scroll; index < Math.min(Currencies.pairs().size(), this.scroll + visible); index++) {
            CurrencyPair pair = Currencies.pairs().get(index);
            ScreenRectangle row = rowArea(area, index);
            boolean selected = pair.equals(state.selectedPair());
            if (selected) {
                FxUi.fill(graphics, row.left(), row.top(), row.right(), row.bottom(), FxTheme.SELECTED);
                FxUi.fill(graphics, row.left(), row.top(), row.left() + 2, row.bottom(), FxTheme.ACCENT);
            } else if (input.hovered(row)) {
                FxUi.fill(graphics, row.left(), row.top(), row.right(), row.bottom(), FxTheme.BUTTON_HOVER);
            }
            FxUi.separator(graphics, row.left(), row.right(), row.bottom() - 1);
            FxUi.item(graphics, FxCurrencies.stack(pair.base(), 1), row.left() + 1, row.top() + 3, ICON_SCALE);
            FxUi.item(graphics, FxCurrencies.stack(pair.quote(), 1), row.left() + 1 + ICON + 2, row.top() + 3, ICON_SCALE);
            int textX = row.left() + 2 * ICON + 8;
            FxUi.text(graphics, font, textX, row.top() + 7, FxText.literal(pair.code()),
                    selected ? FxTheme.ACCENT : FxTheme.TEXT);
            FxUi.clipped(graphics, font, row, textX, row.top() + 20, names(pair), FxTheme.MUTED);
            double change = state.listingChange(pair, state.selectedTimeframe());
            int colour = change >= 0.0D ? FxTheme.UP : FxTheme.DOWN;
            sparkline(graphics, state, pair, state.selectedTimeframe(), FxUi.rect(textX + SPARK_LEFT, row.top() + 6,
                    Math.max(0, markerLeft - SPARK_GAP - textX - SPARK_LEFT), ROW_HEIGHT - 12), colour);
            markers(graphics, state, pair, row, markerLeft);
            FxUi.right(graphics, font, inner.right(), row.top() + 7, FxText.literal(FxUi.price(state.price(pair))), FxTheme.TEXT);
            FxUi.right(graphics, font, changeRight, row.top() + 20, FxText.literal(FxUi.percent(change)), colour);
            if (input.clicked(row)) {
                clicked = pair;
            }
        }
        FxUi.popClip(graphics);
        if (maximum > 0) {
            FxControls.scrollbar(graphics, FxUi.rect(inner.right() - 3, top, 3, visible * ROW_HEIGHT),
                    this.scroll / (double) maximum, visible / (double) Currencies.pairs().size(), input.hovered(area));
        }
        FxUi.center(graphics, font, inner.left() + inner.width() / 2, bottom + 2, FxText.get("fxeasy.panel.list.hint"), FxTheme.MUTED);
        return clicked;
    }

    /// Rectangle of one row of the list, or null while that row is scrolled out of the window. The visible
    /// count is derived the way the draw derives it, so a row is hit exactly where it is drawn.
    public ScreenRectangle rowArea(ScreenRectangle area, int index) {
        ScreenRectangle inner = FxUi.rect(area.left() + FxTheme.PAD, area.top() + FxTheme.PAD,
                area.width() - FxTheme.PAD * 2, area.height() - FxTheme.PAD * 2);
        int top = inner.top() + FxTheme.ROW;
        int visible = Math.max(1, (inner.bottom() - FxTheme.ROW - top) / ROW_HEIGHT);
        return index >= this.scroll && index < this.scroll + visible
                ? FxUi.rect(inner.left(), top + (index - this.scroll) * ROW_HEIGHT, inner.width(), ROW_HEIGHT)
                : null;
    }

    private static Component names(CurrencyPair pair) {
        return FxText.join(FxText.name(pair.base()), FxText.literal("/"), FxText.name(pair.quote()));
    }

    /// Which way a pressure level leans: a currency at or under [FxEconomyPayload#PRESSURE_LOW] is absorbed faster
    /// than the world produces it, so its marker points up; from [FxEconomyPayload#PRESSURE_HIGH] the world
    /// carries an unabsorbed supply overhang and the marker points down; a normal level carries no marker.
    public static int pressureDirection(int level) {
        if (level >= FxEconomyPayload.PRESSURE_HIGH) {
            return -1;
        }
        return level <= FxEconomyPayload.PRESSURE_LOW ? 1 : 0;
    }

    /// The fundamentals marker of one row: the base currency in the first slot, the quote in the second and a
    /// diamond in the third when a message concerns either of them. The direction is the shape and not the
    /// colour, so an unabsorbed overhang and an absorbed currency stay apart for a colour blind player, and an
    /// empty slot keeps the position of the two currencies fixed.
    private void markers(GuiGraphicsExtractor graphics, FxClientState state, CurrencyPair pair, ScreenRectangle row,
            int left) {
        FxEconomyPayload report = state.economy();
        int x = left;
        for (Currency currency : List.of(pair.base(), pair.quote())) {
            int direction = report == null ? 0 : pressureDirection(report.pressure()[Currencies.index(currency)]);
            if (direction != 0) {
                triangle(graphics, x, row.top() + 8, direction, direction < 0 ? FxTheme.DOWN : FxTheme.UP);
            }
            x += MARKER_STEP;
        }
        if (report != null && this.concerned[pair.index()]) {
            diamond(graphics, x, row.top() + 8, FxTheme.ACCENT);
        }
    }

    private static void triangle(GuiGraphicsExtractor graphics, int x, int y, int direction, int colour) {
        if (direction < 0) {
            FxUi.fill(graphics, x - 1, y, x + 2, y + 1, colour);
            FxUi.fill(graphics, x, y + 1, x + 1, y + 2, colour);
            return;
        }
        FxUi.fill(graphics, x, y, x + 1, y + 1, colour);
        FxUi.fill(graphics, x - 1, y + 1, x + 2, y + 2, colour);
    }

    private static void diamond(GuiGraphicsExtractor graphics, int x, int y, int colour) {
        FxUi.fill(graphics, x - 1, y + 1, x + 2, y + 2, colour);
        FxUi.fill(graphics, x, y, x + 1, y + 1, colour);
        FxUi.fill(graphics, x, y + 2, x + 1, y + 3, colour);
    }

    /// Which pairs the newest message feed concerns, resolved once per frame so a row only reads two flags.
    private void concernedFeed(FxClientState state) {
        Arrays.fill(this.concernedCurrency, false);
        FxEventsPayload feed = state.events();
        if (feed != null) {
            for (FxEventsPayload.Event event : feed.events()) {
                for (Currency currency : Currencies.all()) {
                    int index = Currencies.index(currency);
                    if (concerns(event, index)) {
                        this.concernedCurrency[index] = true;
                    }
                }
            }
        }
        for (CurrencyPair pair : Currencies.pairs()) {
            this.concerned[pair.index()] = this.concernedCurrency[Currencies.index(pair.base())]
                    || this.concernedCurrency[Currencies.index(pair.quote())];
        }
    }

    /// The event feed bounds neither per currency array, so every read of one goes through this instead of the
    /// accessor that indexes it blindly.
    private static boolean concerns(FxEventsPayload.Event event, int ordinal) {
        return event.applied().length > ordinal && event.peak().length > ordinal && event.concerns(ordinal);
    }

    /// Polyline of the timeframe window, one column per design unit, scaled to this row so a quiet pair still
    /// reads as a shape. A flat window stays a flat line in the middle of the area.
    private static void sparkline(GuiGraphicsExtractor graphics, FxClientState state, CurrencyPair pair, Timeframe timeframe,
            ScreenRectangle area, int colour) {
        int samples = state.listingWindow(timeframe);
        if (samples < 2 || area.width() < SPARK_MIN_WIDTH) {
            return;
        }
        double low = Double.MAX_VALUE;
        double high = -Double.MAX_VALUE;
        for (int index = 0; index < samples; index++) {
            double sample = state.listingWindowSample(pair, timeframe, index);
            low = Math.min(low, sample);
            high = Math.max(high, sample);
        }
        double range = Math.max(high - low, Math.abs(high) * 1.0E-4D);
        int previousY = Integer.MIN_VALUE;
        int runStart = area.left();
        for (int column = 0; column < area.width(); column++) {
            int index = (int) Math.round((double) column / (area.width() - 1) * (samples - 1));
            int x = area.left() + column;
            int y = area.bottom() - 1 - (int) Math.round((state.listingWindowSample(pair, timeframe, index) - low) / range * (area.height() - 2));
            if (previousY == Integer.MIN_VALUE) {
                previousY = y;
                runStart = x;
                continue;
            }
            if (y == previousY) {
                continue;
            }
            if (x - runStart > 1) {
                FxUi.fill(graphics, runStart, previousY, x, previousY + 1, colour);
            }
            FxUi.fill(graphics, x, Math.min(previousY, y), x + 1, Math.max(previousY, y) + 1, colour);
            previousY = y;
            runStart = x;
        }
        if (previousY != Integer.MIN_VALUE && area.right() - runStart > 1) {
            FxUi.fill(graphics, runStart, previousY, area.right(), previousY + 1, colour);
        }
    }
}