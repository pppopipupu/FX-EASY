package com.pppopipupu.fxeasy.client.panel;

import module java.base;

import com.pppopipupu.fxeasy.client.FxActions;
import com.pppopipupu.fxeasy.client.FxClientState;
import com.pppopipupu.fxeasy.client.FxText;
import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;
import com.pppopipupu.fxeasy.market.CurrencyPair;
import com.pppopipupu.fxeasy.market.Side;
import com.pppopipupu.fxeasy.shop.Membership;
import com.pppopipupu.fxeasy.trade.TradeEngine;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

/// Order ticket: side, lots, leverage, the currency the order locks its margin in, the margin the order would
/// need and the button that sends it. One slider sets the size, in whole lots, one item of the base currency of
/// the pair being one lot, from nothing up to what the free margin of the margin currency backs at the
/// leverage of the order and the risk room the account reported on either currency of the pair allows, whichever
/// is smaller, so the ticket never builds a size the platform would refuse for want of margin or for want of the
/// account's own quota. What the quota of the whole server still leaves is the server's alone, so a size that
/// runs into it is refused and reported rather than never offered.
///
/// The column is drawn top down from the controls of the order and bottom up from the button, because its height
/// follows the window and the panel lays the side column out in design units. A column that cannot hold the
/// whole ticket gets the compact layout - the notional readout and the margin currency cells are conveniences,
/// while the slider and the margin amount above the button are what the order is made of. The margin currency
/// cells wrap into as many rows as the width needs.
public final class FxTicket {

    public static final int DRAG_LOTS = 10;
    public static final int DRAG_LEVERAGE = 11;

    /// Room one margin currency cell needs before the cells wrap into another row.
    private static final int MARGIN_CELL_MIN = 20;

    /// Highest leverage a plan without a subscription buys, which is the last of the retail preset steps and the
    /// ceiling every account starts on.
    private static final int RETAIL_LEVERAGE = Membership.NONE.maxLeverage();
    private static final int[] LEVERAGE_PRESETS = {1, 5, 10, 25, 50, RETAIL_LEVERAGE};
    /// Height of one text row of the ticket, which every label and every amount is drawn in.
    public static final int TEXT_ROW = 10;
    /// Height of the two sliders of the ticket: the track, its handle and the row above it the value is read in.
    private static final int SLIDER_HEIGHT = FxTheme.ROW + 2;
    /// Height of the column that holds the whole ticket, summed the way [draw] lays it out: the side buttons, the
    /// lots label and its slider, the margin currency cells, the leverage label, the leverage slider and its two
    /// preset rows, the notional and the two margin amounts, and the open button. A shorter column gets the
    /// compact layout, which does not draw the conveniences this sum counts, and the margin currency cells take
    /// more than one row here when the width makes them wrap.
    public static final int FULL_HEIGHT = FxTheme.ROW + 3 + FxTheme.GAP
            + TEXT_ROW + SLIDER_HEIGHT + FxTheme.GAP
            + TEXT_ROW + FxTheme.ROW + FxTheme.GAP
            + TEXT_ROW + SLIDER_HEIGHT + FxTheme.GAP
            + FxTheme.ROW + 1 + FxTheme.ROW + FxTheme.GAP * 2
            + TEXT_ROW * 3
            + FxTheme.ROW + 3;

    private FxTicket() {
    }

    public static void draw(GuiGraphicsExtractor graphics, Font font, FxClientState state, FxInput input,
            ScreenRectangle area) {
        CurrencyPair pair = state.selectedPair();
        Currency quote = pair.quote();
        Currency margin = state.orderMargin(pair);
        int x = area.left();
        int width = area.width();
        int y = area.top();
        int half = (width - FxTheme.GAP) / 2;
        ScreenRectangle buy = FxUi.rect(x, y, half, FxTheme.ROW + 3);
        ScreenRectangle sell = FxUi.rect(x + half + FxTheme.GAP, y, width - half - FxTheme.GAP, FxTheme.ROW + 3);
        boolean buying = state.orderSide() == Side.BUY;
        FxControls.choice(graphics, font, buy, FxText.get("fxeasy.panel.side.buy"), buying, input.hovered(buy), FxTheme.UP);
        FxControls.choice(graphics, font, sell, FxText.get("fxeasy.panel.side.sell"), !buying, input.hovered(sell), FxTheme.DOWN);
        if (input.clicked(buy)) {
            state.setOrderSide(Side.BUY);
        }
        if (input.clicked(sell)) {
            state.setOrderSide(Side.SELL);
        }
        y += buy.height() + FxTheme.GAP;
        int maximum = state.maxOrderUnits();
        state.setOrderUnits(Math.min(state.orderUnits(), maximum));
        FxUi.text(graphics, font, x, y, FxText.get("fxeasy.panel.lots"), FxTheme.MUTED);
        FxUi.right(graphics, font, area.right(), y, FxText.literal(FxUi.lots(state.orderUnits())), FxTheme.TEXT);
        ScreenRectangle lots = lotsArea(area);
        FxControls.slider(graphics, font, lots, lotsFraction(state.orderUnits(), maximum), input.hovered(lots),
                input.dragging(DRAG_LOTS));
        if (input.clicked(lots)) {
            input.beginDrag(DRAG_LOTS);
        }
        if (input.dragging(DRAG_LOTS)) {
            state.setOrderUnits(lotsAt((input.mouseX - lots.left()) / (double) Math.max(1, lots.width() - 1), maximum));
        }
        y = lots.bottom() + FxTheme.GAP;
        boolean full = fullLayout(area);
        x = area.left();
        FxUi.text(graphics, font, x, y, FxText.get("fxeasy.panel.margin.currency"), FxTheme.MUTED);
        FxUi.right(graphics, font, area.right(), y, FxText.literal(margin.code()), FxTheme.ACCENT);
        y += TEXT_ROW;
        List<ScreenRectangle> marginCells = marginAreas(area);
        for (int index = 0; index < marginCells.size(); index++) {
            Currency currency = Currencies.byIndex(index);
            ScreenRectangle cell = marginCells.get(index);
            FxControls.choice(graphics, font, cell, FxText.literal(currency.code()), currency == margin, input.hovered(cell), FxTheme.ACCENT);
            if (input.clicked(cell)) {
                state.setOrderMargin(currency);
            }
        }
        x = area.left();
        y = marginRowTop(area) + marginRows(area) * (FxTheme.ROW + FxTheme.GAP);
        FxUi.text(graphics, font, x, y, FxText.get("fxeasy.panel.leverage"), FxTheme.MUTED);
        FxUi.right(graphics, font, area.right(), y,
                FxText.literal(state.orderLeverage() + "x / " + state.maxOrderLeverage() + "x"), FxTheme.ACCENT);
        y += TEXT_ROW;
        ScreenRectangle slider = leverageArea(area);
        FxControls.slider(graphics, font, slider, leverageFraction(state.orderLeverage(), state.maxOrderLeverage()),
                input.hovered(slider), input.dragging(DRAG_LEVERAGE));
        if (input.clicked(slider)) {
            input.beginDrag(DRAG_LEVERAGE);
        }
        if (input.dragging(DRAG_LEVERAGE)) {
            state.setOrderLeverage(leverageAt((input.mouseX - slider.left()) / (double) Math.max(1, slider.width() - 1),
                    state.maxOrderLeverage()));
        }
        y = slider.bottom() + FxTheme.GAP;
        if (full) {
            int[] presets = leveragePresets(state.maxOrderLeverage());
            int presetWidth = (width - 2 * FxTheme.GAP) / 3;
            for (int index = 0; index < presets.length; index++) {
                int column = index % 3;
                if (column == 0 && index > 0) {
                    y += FxTheme.ROW + 1;
                }
                ScreenRectangle cell = FxUi.rect(x + column * (presetWidth + FxTheme.GAP), y, presetWidth, FxTheme.ROW);
                boolean selected = state.orderLeverage() == presets[index];
                FxControls.choice(graphics, font, cell, FxText.literal(presets[index] + "x"), selected, input.hovered(cell), FxTheme.ACCENT);
                if (input.clicked(cell)) {
                    state.setOrderLeverage(presets[index]);
                }
            }
            y += FxTheme.ROW + FxTheme.GAP * 2;
        }
        long required = state.requiredMargin(margin, state.orderUnits(), state.orderLeverage());
        long free = state.freeMargin(margin);
        if (full) {
            y = labeled(graphics, font, area, y, FxText.get("fxeasy.panel.notional"),
                    FxUi.money(state.notional(pair, state.orderUnits()), quote.code()), FxTheme.TEXT);
        }
        y = labeled(graphics, font, area, y, FxText.get("fxeasy.panel.margin.required"), FxUi.money(required, margin.code()),
                FxTheme.TEXT);
        y = labeled(graphics, font, area, y, FxText.get("fxeasy.panel.margin.free"), FxUi.money(free, margin.code()),
                free >= required ? FxTheme.TEXT : FxTheme.DOWN);
        boolean sized = state.orderUnits() > 0;
        boolean affordable = sized && free >= required && !state.blacklisted();
        ScreenRectangle open = openArea(area);
        int accent = buying ? FxTheme.UP : FxTheme.DOWN;
        Component label = state.blacklisted() ? FxText.get(TradeEngine.REJECT_CREDIT)
                : FxText.get(affordable ? "fxeasy.panel.open"
                        : sized ? TradeEngine.REJECT_MARGIN : TradeEngine.REJECT_SIZE);
        FxControls.button(graphics, font, open, label, ARGB.multiplyAlpha(accent, 0.82F), ARGB.color(255, 12, 16, 20),
                input.hovered(open), false, affordable);
        if (affordable && input.clicked(open)) {
            FxActions.openOrder(pair, state.orderSide(), margin, state.orderUnits(), state.orderLeverage());
        }
    }

    /// The size slider of the ticket, under the label of the lots, which the draw and a scripted click both
    /// read: its left end is an order of nothing and its right end everything the free margin of the ticket backs
    /// at its leverage or the risk room of the account leaves, whichever is smaller.
    public static ScreenRectangle lotsArea(ScreenRectangle area) {
        return FxUi.rect(area.left(), area.top() + FxTheme.ROW + 3 + FxTheme.GAP + TEXT_ROW, area.width(),
                SLIDER_HEIGHT);
    }

    /// The margin currency cells of the ticket, one per currency of the table, packed left to right into as
    /// many rows as the width needs, which the draw and a scripted click both read. A column too short for the
    /// full layout carries no cells.
    public static List<ScreenRectangle> marginAreas(ScreenRectangle area) {
        if (!fullLayout(area)) {
            return List.of();
        }
        int count = Currencies.count();
        int columns = marginColumns(area);
        int cell = (area.width() - (columns - 1) * FxTheme.GAP) / columns;
        int y = marginRowTop(area);
        List<ScreenRectangle> cells = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            cells.add(FxUi.rect(area.left() + index % columns * (cell + FxTheme.GAP),
                    y + index / columns * (FxTheme.ROW + FxTheme.GAP), cell, FxTheme.ROW));
        }
        return cells;
    }

    /// The leverage slider of the ticket, under the margin currency cells.
    public static ScreenRectangle leverageArea(ScreenRectangle area) {
        return FxUi.rect(area.left(),
                marginRowTop(area) + marginRows(area) * (FxTheme.ROW + FxTheme.GAP) + TEXT_ROW, area.width(),
                SLIDER_HEIGHT);
    }

    /// Cells of the margin currency row that fit side by side, at least one.
    private static int marginColumns(ScreenRectangle area) {
        int columns = Math.max(1, (area.width() + FxTheme.GAP) / (MARGIN_CELL_MIN + FxTheme.GAP));
        return Math.min(Currencies.count(), columns);
    }

    /// Rows the margin currency cells occupy, zero while the compact layout drops them.
    private static int marginRows(ScreenRectangle area) {
        if (!fullLayout(area)) {
            return 0;
        }
        int columns = marginColumns(area);
        return (Currencies.count() + columns - 1) / columns;
    }

    /// Whether the column is tall enough for the whole ticket: the notional readout and the margin currency
    /// cells are conveniences the compact layout does without, and every wrapped row of margin cells takes one
    /// more row of the sum.
    private static boolean fullLayout(ScreenRectangle area) {
        int columns = marginColumns(area);
        int packed = (Currencies.count() + columns - 1) / columns;
        return area.height() >= FULL_HEIGHT + (packed - 1) * (FxTheme.ROW + FxTheme.GAP);
    }

    /// The row the margin currency cells start at: the label of the margin currency stands under the size
    /// slider.
    private static int marginRowTop(ScreenRectangle area) {
        return lotsArea(area).bottom() + FxTheme.GAP + TEXT_ROW;
    }

    /// The button that sends the order, which is the bottom row of the column.
    public static ScreenRectangle openArea(ScreenRectangle area) {
        return FxUi.rect(area.left(), area.bottom() - FxTheme.ROW - 3, area.width(), FxTheme.ROW + 3);
    }

    private static int labeled(GuiGraphicsExtractor graphics, Font font, ScreenRectangle area, int y, Component label,
            Component value, int colour) {
        FxUi.clipped(graphics, font, area, area.left(), y, label, FxTheme.MUTED);
        FxUi.right(graphics, font, area.right(), y, value, colour);
        return y + TEXT_ROW;
    }

    /// Where the handle of the size slider stands for an order of that many lots: nothing at the left end of
    /// the track, the whole range the margin and the risk room back at the right one.
    public static double lotsFraction(int units, int maximum) {
        return maximum <= 0 ? 0.0D : Math.clamp(units, 0, maximum) / (double) maximum;
    }

    /// The size a handle dragged to that position sets, in whole lots of the base currency of the pair, one lot
    /// being one item, which is the smallest size the platform accepts.
    public static int lotsAt(double fraction, int maximum) {
        return (int) Math.round(Math.clamp(fraction, 0.0D, 1.0D) * Math.max(0, maximum));
    }

    /// Where the handle of the leverage slider stands for one leverage, on a geometric track from the smallest
    /// leverage the platform takes to the ceiling the plan of the account buys.
    public static double leverageFraction(int leverage, int maxLeverage) {
        return Math.log(leverage / (double) TradeEngine.MIN_LEVERAGE) / Math.log(maxLeverage / (double) TradeEngine.MIN_LEVERAGE);
    }

    /// The leverage a handle dragged to that position sets, on the same track, rounded to a whole multiple and
    /// held inside what the plan of the account allows.
    public static int leverageAt(double fraction, int maxLeverage) {
        double clamped = Math.clamp(fraction, 0.0D, 1.0D);
        double leverage = TradeEngine.MIN_LEVERAGE * Math.pow(maxLeverage / (double) TradeEngine.MIN_LEVERAGE, clamped);
        return (int) Math.clamp(Math.round(leverage), TradeEngine.MIN_LEVERAGE, maxLeverage);
    }

    /// Leverage the preset cells offer. The retail steps serve a plan that stops where the platform itself stops;
    /// a plan that raises the ceiling trades the 5x step for its own ceiling, so the leverage the plan is paid for
    /// is one click away and the cells still take the two rows the column reserves for them.
    private static int[] leveragePresets(int maxLeverage) {
        if (maxLeverage <= RETAIL_LEVERAGE) {
            return LEVERAGE_PRESETS;
        }
        return new int[] {1, 10, 25, 50, RETAIL_LEVERAGE, maxLeverage};
    }
}