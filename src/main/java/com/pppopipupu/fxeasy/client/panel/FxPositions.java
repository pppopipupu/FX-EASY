package com.pppopipupu.fxeasy.client.panel;

import module java.base;

import com.pppopipupu.fxeasy.client.FxActions;
import com.pppopipupu.fxeasy.client.FxClientState;
import com.pppopipupu.fxeasy.client.FxText;
import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;
import com.pppopipupu.fxeasy.market.CurrencyPair;
import com.pppopipupu.fxeasy.market.Side;
import com.pppopipupu.fxeasy.net.FxPositionPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.util.ARGB;

/// Open positions: one two line row each with the close button, plus the close all button. The second line
/// carries the mark price and the profit of the position, which is denominated in the currency the position
/// locked its margin in. The list scrolls with the wheel while the mouse is over it. Closing every position at
/// once cannot be taken back, so the button reports a [FxConfirm.Request] instead of sending it.
public final class FxPositions {

    private static final int ROW_HEIGHT = 22;
    private static final int SCROLL_STEP = 3;

    private int scroll;

    public FxConfirm.Request draw(GuiGraphicsExtractor graphics, Font font, FxClientState state, FxInput input,
            ScreenRectangle area) {
        List<FxPositionPayload> positions = state.positions();
        int x = area.left();
        int width = area.width();
        int y = area.top();
        ScreenRectangle closeAll = closeAllArea(area);
        FxControls.button(graphics, font, closeAll, FxText.get("fxeasy.panel.close.all"),
                ARGB.multiplyAlpha(FxTheme.DOWN, 0.78F), ARGB.color(255, 12, 16, 20), input.hovered(closeAll), false,
                !positions.isEmpty());
        if (!positions.isEmpty() && input.clicked(closeAll)) {
            return new FxConfirm.Request("close_all", FxText.get("fxeasy.confirm.close_all.title"),
                    List.of(FxText.get("fxeasy.confirm.close_all.body", Integer.toString(positions.size()))),
                    FxText.get("fxeasy.confirm.close_all.confirm"), FxActions::closeAll);
        }
        y += closeAll.height() + FxTheme.GAP;
        FxUi.text(graphics, font, x, y, FxText.get("fxeasy.panel.tab.positions"), FxTheme.MUTED);
        FxUi.right(graphics, font, area.right(), y, FxText.literal(Integer.toString(positions.size())), FxTheme.TEXT);
        y += 10;
        if (positions.isEmpty()) {
            FxUi.text(graphics, font, x, y, FxText.get("fxeasy.panel.no.positions"), FxTheme.MUTED);
            return null;
        }
        ScreenRectangle list = listArea(area);
        int visible = Math.max(1, list.height() / ROW_HEIGHT);
        int maximum = Math.max(0, positions.size() - visible);
        if (input.scroll != 0.0D && input.hovered(list)) {
            this.scroll -= (int) Math.signum(input.scroll) * SCROLL_STEP;
            input.scroll = 0.0D;
        }
        this.scroll = Math.clamp(this.scroll, 0, maximum);
        FxUi.pushClip(graphics, list);
        for (int index = this.scroll; index < Math.min(positions.size(), this.scroll + visible); index++) {
            row(graphics, font, input, list, positions.get(index), rowArea(area, index).top());
        }
        FxUi.popClip(graphics);
        if (maximum > 0) {
            FxControls.scrollbar(graphics, FxUi.rect(area.right() - 3, list.top(), 3, list.height()),
                    this.scroll / (double) maximum, visible / (double) positions.size(), input.hovered(list));
        }
        return null;
    }

    /// The close all button, which is the first row of the page. Both the draw and a scripted click read it.
    public static ScreenRectangle closeAllArea(ScreenRectangle area) {
        return FxUi.rect(area.left(), area.top(), area.width(), FxTheme.ROW + 2);
    }

    /// The list of position rows, under the close all button and the header of the page.
    public static ScreenRectangle listArea(ScreenRectangle area) {
        int y = area.top() + closeAllArea(area).height() + FxTheme.GAP + 10;
        return FxUi.rect(area.left(), y, area.width() - 4, area.bottom() - y);
    }

    /// The row of one position, or null when it is scrolled out of the list. Both the draw and a scripted click
    /// read it, so a run closes a position by name and not by pixel.
    public ScreenRectangle rowArea(ScreenRectangle area, int index) {
        ScreenRectangle list = listArea(area);
        int visible = Math.max(1, list.height() / ROW_HEIGHT);
        if (index < this.scroll || index >= this.scroll + visible) {
            return null;
        }
        return FxUi.rect(list.left(), list.top() + (index - this.scroll) * ROW_HEIGHT, list.width(), ROW_HEIGHT);
    }

    /// The close cell of one position row, which the row draws at its right edge.
    public ScreenRectangle closeArea(ScreenRectangle area, int index) {
        ScreenRectangle row = rowArea(area, index);
        return row == null ? null : FxUi.rect(row.right() - 26, row.top() - 1, 26, FxTheme.ROW + 1);
    }

    private void row(GuiGraphicsExtractor graphics, Font font, FxInput input, ScreenRectangle list, FxPositionPayload position,
            int y) {
        CurrencyPair pair = Currencies.pair(position.pairIndex());
        Currency margin = Currencies.byIndex(position.marginCurrencyOrdinal());
        Side side = Side.values()[position.sideOrdinal()];
        boolean rising = position.pnlMicro() >= 0L;
        int accent = side == Side.BUY ? FxTheme.UP : FxTheme.DOWN;
        FxUi.clipped(graphics, font, list, list.left(), y, FxText.literal(pair.code()), FxTheme.TEXT);
        FxUi.clipped(graphics, font, list, list.left() + 42, y,
                FxText.get(side == Side.BUY ? "fxeasy.panel.side.buy" : "fxeasy.panel.side.sell"), accent);
        FxUi.clipped(graphics, font, list, list.left() + 86, y,
                FxText.literal(position.units() + " " + pair.base().code()), FxTheme.MUTED);
        FxUi.clipped(graphics, font, list, list.left() + 128, y, FxText.literal(position.leverage() + "x"), FxTheme.MUTED);
        ScreenRectangle close = FxUi.rect(list.right() - 26, y - 1, 26, FxTheme.ROW + 1);
        FxControls.button(graphics, font, close, FxText.get("fxeasy.panel.close"), FxTheme.BUTTON, FxTheme.TEXT, input.hovered(close),
                false, true);
        if (input.clicked(close)) {
            FxActions.closePosition(position.id());
        }
        FxUi.clipped(graphics, font, list, list.left(), y + 11,
                FxText.literal(FxUi.price(position.openPrice()) + " -> " + FxUi.price(position.markPrice())), FxTheme.MUTED);
        FxUi.right(graphics, font, list.right(), y + 11, FxUi.money(position.pnlMicro(), margin.code()),
                rising ? FxTheme.UP : FxTheme.DOWN);
        FxUi.separator(graphics, list.left(), list.right(), y + ROW_HEIGHT - 2);
    }
}
