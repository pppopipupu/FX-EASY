package com.pppopipupu.fxeasy.client.panel;

import module java.base;

import com.pppopipupu.fxeasy.client.FxClientState;
import com.pppopipupu.fxeasy.client.FxText;
import com.pppopipupu.fxeasy.content.FxCurrencies;
import com.pppopipupu.fxeasy.market.CurrencyPair;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;

/// The header line: the selected pair with both of its currencies, its price and its change over the selected
/// timeframe, the bid and the ask and the volatility once the panel is wide enough, and the state of the
/// account against the right edge. A status word - a blacklisted credit or a margin call - is drawn left of
/// whatever the account figures already take, so the two can never land on each other.
public final class FxHeader {

    private static final int WIDE = 520;
    private static final int VERY_WIDE = 660;
    private static final int ICON_STEP = 18;

    private FxHeader() {
    }

    public static void draw(GuiGraphicsExtractor graphics, Font font, FxClientState state, ScreenRectangle area) {
        CurrencyPair pair = state.selectedPair();
        double change = state.listingChange(pair, state.selectedTimeframe());
        int changeColour = change >= 0.0D ? FxTheme.UP : FxTheme.DOWN;
        int y = (FxTheme.BAR - 9) / 2;
        FxUi.item(graphics, FxCurrencies.stack(pair.base(), 1), FxTheme.PAD, y - 3, 1);
        FxUi.item(graphics, FxCurrencies.stack(pair.quote(), 1), FxTheme.PAD + ICON_STEP, y - 3, 1);
        int x = FxTheme.PAD + ICON_STEP * 2 + 2;
        x = items(graphics, font, x, y, 3, new Item(FxText.literal(pair.code()), FxTheme.TEXT),
                new Item(FxText.literal(FxUi.price(state.price(pair))), changeColour),
                new Item(FxText.literal(FxUi.percent(change)), changeColour));
        if (area.width() >= WIDE) {
            x = items(graphics, font, x, y, 3, new Item(FxText.get("fxeasy.panel.bid"), FxTheme.MUTED),
                    new Item(FxText.literal(FxUi.price(state.bid(pair))), FxTheme.TEXT),
                    new Item(FxText.get("fxeasy.panel.ask"), FxTheme.MUTED),
                    new Item(FxText.literal(FxUi.price(state.ask(pair))), FxTheme.TEXT));
        }
        if (area.width() >= VERY_WIDE) {
            items(graphics, font, x, y, 3, new Item(FxText.get("fxeasy.panel.volatility"), FxTheme.MUTED),
                    new Item(FxText.literal(FxUi.factor(state.regime())), FxTheme.ACCENT));
        }
        double unrealized = state.unrealizedItems(state.basis());
        int right = area.right() - FxTheme.PAD;
        if (state.blacklisted()) {
            right = chip(graphics, font, right, y, FxText.get("fxeasy.panel.credit.black"), FxTheme.DOWN);
        }
        if (state.marginCall()) {
            right = chip(graphics, font, right, y, FxText.get("fxeasy.panel.margin.call"), FxTheme.DOWN);
        }
        if (area.width() >= VERY_WIDE) {
            right = FxUi.labeledRight(graphics, font, right, y, FxText.get("fxeasy.panel.margin.level"),
                    FxText.literal(FxUi.level(state.marginLevel())), FxTheme.AXIS);
            right = FxUi.labeledRight(graphics, font, right, y, FxText.get("fxeasy.panel.margin.used"),
                    FxText.literal(FxUi.amount(state.usedMarginItems(state.basis()))), FxTheme.TEXT);
        }
        right = FxUi.labeledRight(graphics, font, right, y, FxText.get("fxeasy.panel.equity"),
                FxText.literal(FxUi.amount(state.equityItems(state.basis())) + " " + state.basis().code()),
                state.marginCall() ? FxTheme.DOWN : FxTheme.TEXT);
        if (area.width() >= WIDE) {
            FxUi.labeledRight(graphics, font, right, y, FxText.get("fxeasy.panel.unrealized"),
                    FxText.literal(FxUi.signedAmount(unrealized)), unrealized >= 0.0D ? FxTheme.UP : FxTheme.DOWN);
        }
        FxUi.fill(graphics, 0, area.bottom() - 1, area.right(), area.bottom(), FxTheme.CARD_BORDER);
    }

    /// A status word drawn against the right edge of the header, left of whatever is already there. Returns the
    /// right edge the next one may use.
    private static int chip(GuiGraphicsExtractor graphics, Font font, int right, int y, Component text, int colour) {
        int x = right - font.width(text);
        FxUi.text(graphics, font, x, y, text, colour);
        return x - 6;
    }

    private static int items(GuiGraphicsExtractor graphics, Font font, int x, int y, int gap, Item... entries) {
        int cursor = x;
        for (Item entry : entries) {
            FxUi.text(graphics, font, cursor, y, entry.text(), entry.colour());
            cursor += font.width(entry.text()) + gap;
        }
        return cursor;
    }

    private record Item(Component text, int colour) {
    }
}
