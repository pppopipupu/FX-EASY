package com.pppopipupu.fxeasy.client.panel;

import module java.base;

import com.pppopipupu.fxeasy.client.FxClientState;
import com.pppopipupu.fxeasy.client.FxText;
import com.pppopipupu.fxeasy.market.Currency;
import com.pppopipupu.fxeasy.market.CurrencyPair;
import com.pppopipupu.fxeasy.market.FeeModel;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;

/// The metrics strip at the bottom of the side column, which every page that is not the market list keeps in
/// view: what the account is worth and how much margin it uses, quoted in items of the currency the player
/// picked as the display one, the state of its credit, and what the market currently charges for a trade on
/// the selected pair and side - the spread, the slippage, the overnight swap and, for the currency the next
/// order would lock its margin in, the haircut it backs the trade with and the spread the platform converts at.
/// The display currency itself is a cell of the control row, which every page carries.
public final class FxMetrics {

    private static final int LINE = 10;

    private FxMetrics() {
    }

    public static void draw(GuiGraphicsExtractor graphics, Font font, FxClientState state, ScreenRectangle area) {
        int y = area.top();
        int right = area.right();
        int half = area.left() + area.width() / 2;
        double unrealized = state.unrealizedItems(state.basis());
        FxUi.text(graphics, font, area.left(), y, FxText.get("fxeasy.panel.unrealized"), FxTheme.MUTED);
        Component unit = FxText.literal(state.basis().code());
        FxUi.text(graphics, font, right - font.width(unit), y, unit, FxTheme.MUTED);
        FxUi.right(graphics, font, right - font.width(unit) - 3, y, FxText.literal(FxUi.signedAmount(unrealized)),
                unrealized >= 0.0D ? FxTheme.UP : FxTheme.DOWN);
        y += LINE;
        FxUi.text(graphics, font, area.left(), y, FxText.get("fxeasy.panel.margin.used"), FxTheme.MUTED);
        FxUi.right(graphics, font, right, y, FxText.literal(FxUi.amount(state.usedMarginItems(state.basis()))),
                FxTheme.TEXT);
        y += LINE;
        FxUi.text(graphics, font, area.left(), y, FxText.get("fxeasy.panel.margin.level"), FxTheme.MUTED);
        FxUi.right(graphics, font, right, y, FxText.literal(FxUi.level(state.marginLevel())),
                state.marginCall() ? FxTheme.DOWN : FxTheme.AXIS);
        y += LINE;
        FxUi.text(graphics, font, area.left(), y, FxText.get("fxeasy.panel.spread"), FxTheme.MUTED);
        FxUi.right(graphics, font, half - 2, y, FxText.literal(FxUi.rate(state.openSpreadHalf(state.orderSide()))),
                FxTheme.TEXT);
        FxUi.text(graphics, font, half + 2, y, FxText.get("fxeasy.panel.slippage"), FxTheme.MUTED);
        FxUi.right(graphics, font, right, y, FxText.literal(FxUi.percent(state.slippageChance() * 100.0D)),
                FxTheme.TEXT);
        y += LINE;
        Component perDay = FxText.get("fxeasy.panel.perDay");
        int dayWidth = font.width(perDay);
        CurrencyPair pair = state.selectedPair();
        double swap = state.swapPerDay(pair, state.orderSide());
        double credit = swap == 0.0D ? 0.0D : -swap;
        FxUi.text(graphics, font, area.left(), y, FxText.get("fxeasy.panel.swap"), FxTheme.MUTED);
        FxUi.text(graphics, font, right - dayWidth, y, perDay, FxTheme.MUTED);
        FxUi.right(graphics, font, right - dayWidth - 3, y, FxText.literal(FxUi.signedRate(credit)),
                credit >= 0.0D ? FxTheme.UP : FxTheme.DOWN);
        y += LINE;
        FxUi.text(graphics, font, area.left(), y, FxText.get("fxeasy.panel.trades"), FxTheme.MUTED);
        FxUi.right(graphics, font, right, y, FxText.literal(Long.toString(state.closedTrades())), FxTheme.TEXT);
        y += LINE;
        Currency margin = state.orderMargin(pair);
        FxUi.text(graphics, font, area.left(), y, FxText.get("fxeasy.panel.haircut"), FxTheme.MUTED);
        FxUi.right(graphics, font, half - 2, y, FxText.literal(FxUi.rate(FeeModel.haircut(margin))), FxTheme.TEXT);
        FxUi.text(graphics, font, half + 2, y, FxText.get("fxeasy.panel.conversion"), FxTheme.MUTED);
        FxUi.right(graphics, font, right, y, FxText.literal(FxUi.rate(state.conversionSpread())), FxTheme.TEXT);
    }
}
