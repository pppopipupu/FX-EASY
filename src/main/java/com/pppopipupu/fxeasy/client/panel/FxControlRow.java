package com.pppopipupu.fxeasy.client.panel;

import module java.base;

import com.pppopipupu.fxeasy.client.FxActions;
import com.pppopipupu.fxeasy.client.FxClientState;
import com.pppopipupu.fxeasy.client.FxText;
import com.pppopipupu.fxeasy.market.Timeframe;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;

/// The row under the header: the page tabs, the entry that opens the world intel overlay, the cell that picks
/// the currency every amount in the panel is quoted in and the cells that pick the timeframe and the chart
/// style, which only a page that carries a chart draws. A narrow panel drops the chart cells and shrinks the
/// cells that are left, so the row never runs into the settings it cannot show; the display currency cell is
/// never dropped, because it changes what every figure on every page says. The page, the intel entry and the
/// display currency are what a click can change, and the draw reports the first two back to the screen.
public final class FxControlRow {

    private static final int INTEL_CELL = 40;
    private static final int MIN_INTEL = 24;
    /// Width a page tab keeps before the row starts taking room off it instead of off the intel entry, which is
    /// the cell a player reads less than a page. The label of a tab that is narrower than this is clipped rather
    /// than drawn outside its cell, because the cells of the row may never overlap: every one of them is
    /// clickable, and a click that lands on the neighbour of the cell it aimed at changes something the player
    /// did not ask for.
    private static final int MIN_TAB = 26;
    /// Width the display currency cell keeps, which has to hold its label and a currency code.
    private static final int BASIS_CELL = 56;
    /// Row width below which the timeframe and chart style cells are dropped and only the page tabs, the intel
    /// entry and the display currency stay.
    private static final int NARROW = 320;
    /// Row width the chart style cells need before they are drawn, because everything else outranks them.
    private static final int STYLES = 470;

    public record Geometry(ScreenRectangle area, int tabCell, int intelCell, int cell, int basisCell,
            int chartWidth, boolean chartCells, boolean styleCells) {
    }

    /// What a click on the row asked for: the page it picked, which is the page that was already on screen when
    /// it picked none, and whether it opened the world intel overlay.
    public record Result(FxTab tab, boolean intelOpened) {
    }

    private FxControlRow() {
    }

    /// Geometry of a row of [area] on a page that carries chart settings and on one that does not. The chart
    /// style cells are dropped before the timeframe cells and both before the display currency cell, and the
    /// cells of the left block shrink until they fit what is left of the row, so no two of them ever overlap
    /// whatever the window is.
    public static Geometry geometry(ScreenRectangle area, boolean chartCells) {
        int timeframes = Timeframe.ALL.size();
        int styles = FxClientState.ChartStyle.values().length;
        boolean styleCells = chartCells && area.width() >= STYLES;
        int cells = area.width() < NARROW ? 1 : 1 + timeframes + (styleCells ? styles : 0);
        int cell = Math.clamp(area.width() / (cells + 4), 30, 46);
        int basisCell = Math.max(cell, BASIS_CELL);
        int chartWidth = cells == 1 ? basisCell : basisCell + (cells - 1) * (cell + 1);
        int entries = FxTab.values().length;
        int room = area.width() - chartWidth - FxTheme.PAD * 2;
        int intelCell = Math.clamp(room - entries * (MIN_TAB + 1), MIN_INTEL, INTEL_CELL);
        int tabCell = Math.clamp((room - intelCell - entries) / entries, 1, 62);
        return new Geometry(area, tabCell, intelCell, cell, basisCell, chartWidth, chartCells, styleCells);
    }

    /// The cell of one page tab, or of the intel entry when [index] is the number of pages. Both the row and a
    /// scripted click read it.
    public static ScreenRectangle cell(Geometry geometry, int index) {
        int width = index < FxTab.values().length ? geometry.tabCell() : geometry.intelCell();
        return FxUi.rect(geometry.area().left() + FxTheme.PAD + index * (geometry.tabCell() + 1),
                geometry.area().top() + 1, width, FxTheme.TAB);
    }

    /// The cell of the display currency, which is what cycles the currency every amount is quoted in. It is the
    /// first cell of the block on the right and the one cell of that block that is never dropped. Both the row
    /// and a scripted click read it.
    public static ScreenRectangle basisCell(Geometry geometry) {
        return FxUi.rect(geometry.area().right() - FxTheme.PAD - geometry.chartWidth(), geometry.area().top() + 1,
                geometry.basisCell(), FxTheme.TAB);
    }

    /// The cell of one timeframe, or null while the row is too narrow to carry the timeframe cells.
    public static ScreenRectangle timeframeCell(Geometry geometry, int index) {
        int cell = geometry.cell();
        if (cell <= 0 || geometry.chartWidth() <= geometry.basisCell()) {
            return null;
        }
        ScreenRectangle area = geometry.area();
        int start = area.right() - FxTheme.PAD - geometry.chartWidth() + geometry.basisCell() + 1;
        return FxUi.rect(start + index * (cell + 1), area.top() + 1, cell, FxTheme.TAB);
    }

    /// The cell of one chart style, which starts after the timeframe cells and is only drawn on a page that
    /// carries a chart, and only while the row kept room for it. Both the row and a scripted click read it.
    public static ScreenRectangle chartStyleCell(Geometry geometry, int index) {
        ScreenRectangle last = timeframeCell(geometry, Timeframe.ALL.size() - 1);
        if (last == null || !geometry.styleCells()) {
            return null;
        }
        return FxUi.rect(last.right() + 4 + index * (geometry.cell() + 1), last.top(), geometry.cell(), FxTheme.TAB);
    }

    public static Result draw(GuiGraphicsExtractor graphics, Font font, FxClientState state, FxInput input, FxTab tab,
            Geometry geometry) {
        FxTab[] tabs = FxTab.values();
        FxTab picked = tab;
        for (int index = 0; index < tabs.length; index++) {
            FxTab candidate = tabs[index];
            ScreenRectangle box = cell(geometry, index);
            FxControls.choice(graphics, font, box, FxText.get(candidate.key()), candidate == tab, input.hovered(box),
                    FxTheme.ACCENT);
            if (input.clicked(box)) {
                picked = candidate;
            }
        }
        ScreenRectangle entry = cell(geometry, tabs.length);
        int unread = state.unreadEvents();
        Component label = unread > 0 ? FxText.get("fxeasy.panel.intel.unread", Integer.toString(unread))
                : FxText.get("fxeasy.panel.intel");
        FxControls.button(graphics, font, entry, label, FxTheme.BUTTON, FxTheme.ACCENT, input.hovered(entry),
                input.pressed && input.hovered(entry), true);
        boolean intelOpened = input.clicked(entry);
        ScreenRectangle currency = basisCell(geometry);
        Component currencyLabel = FxText.get("fxeasy.panel.basis.cell", state.basis().code());
        FxControls.button(graphics, font, currency, currencyLabel, FxTheme.BUTTON, FxTheme.ACCENT,
                input.hovered(currency), input.pressed && input.hovered(currency), true);
        if (input.clicked(currency)) {
            state.cycleBasis();
        }
        if (geometry.chartWidth() > geometry.basisCell()) {
            for (int index = 0; index < Timeframe.ALL.size(); index++) {
                Timeframe timeframe = Timeframe.ALL.get(index);
                ScreenRectangle box = timeframeCell(geometry, index);
                boolean selected = timeframe == state.selectedTimeframe();
                FxControls.choice(graphics, font, box, FxText.literal(timeframe.label()), selected, input.hovered(box),
                        FxTheme.ACCENT);
                if (input.clicked(box) && !selected) {
                    state.selectTimeframe(timeframe);
                    FxActions.subscribe(state.selectedPair(), timeframe);
                }
            }
            if (geometry.styleCells()) {
                FxClientState.ChartStyle[] styles = FxClientState.ChartStyle.values();
                for (int index = 0; index < styles.length; index++) {
                    FxClientState.ChartStyle style = styles[index];
                    ScreenRectangle box = chartStyleCell(geometry, index);
                    boolean selected = style == state.chartStyle();
                    Component styleLabel = FxText.get(style == FxClientState.ChartStyle.CANDLES
                            ? "fxeasy.panel.chart.candles" : "fxeasy.panel.chart.line");
                    FxControls.choice(graphics, font, box, styleLabel, selected, input.hovered(box), FxTheme.ACCENT);
                    if (input.clicked(box) && !selected) {
                        state.setChartStyle(style);
                    }
                }
            }
        }
        ScreenRectangle area = geometry.area();
        FxUi.fill(graphics, area.left(), area.bottom() - 1, area.right(), area.bottom(), FxTheme.CARD_BORDER);
        return new Result(picked, intelOpened);
    }
}
