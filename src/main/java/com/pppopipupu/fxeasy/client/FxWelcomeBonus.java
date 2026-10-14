package com.pppopipupu.fxeasy.client;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import com.pppopipupu.fxeasy.client.panel.FxControls;
import com.pppopipupu.fxeasy.client.panel.FxInput;
import com.pppopipupu.fxeasy.client.panel.FxTheme;
import com.pppopipupu.fxeasy.client.panel.FxUi;
import com.pppopipupu.fxeasy.content.FxCurrencies;
import com.pppopipupu.fxeasy.market.Currency;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

/// The card the platform offers a client its welcome capital on, drawn the way a broker advertises an account
/// bonus: the brand, the amount in the large face next to the currency it is paid in, what that money can be
/// traded on, the button that claims it and the small print that says where it goes and that it is paid once per
/// account. The platform never pays on its own: the money moves when the player clicks the button, which is a
/// request the server answers with the account and the notice, and the button is gone from then on. Any other
/// click on the card, or Escape, puts it away without claiming anything, and the card comes back the next time
/// the terminal opens, because only the payment is remembered in the save. While it is up it owns the mouse, so
/// nothing behind it can be clicked, and it waits for the account opening and the walkthrough to be done with.
public final class FxWelcomeBonus {

    /// The two cells of the card a scripted click aims at. [action] is null once the capital has been claimed,
    /// because the button is gone from then on.
    public record Cells(ScreenRectangle action, ScreenRectangle close) {
    }

    private static final int CARD_WIDTH = 268;
    private static final int CARD_PAD = 9;
    private static final int LINE = 10;
    private static final int AMOUNT_HEIGHT = 18;
    private static final int ICON = 16;
    private static final int BODY_LINES = 2;
    private static final int NOTE_LINES = 2;
    private static final int BUTTON_WIDTH = 132;
    private static final int BUTTON_HEIGHT = 16;
    private static final int CLOSE_WIDTH = 10;
    private static final int COVER = ARGB.color(200, 4, 6, 9);

    private final FxInput input = new FxInput();
    private boolean closed;
    private boolean claimed;
    private boolean logged;

    /// Whether the card is up: the account is open, the player has not put the card away, and the capital is
    /// either still unclaimed or was claimed in this session, which is when the card carries the confirmation
    /// instead of the button. The screen asks this before it draws the card and before it hands a click to
    /// anything behind it.
    public boolean active(FxClientState state) {
        return state.opened() && !this.closed && (this.claimed || !state.bonusPaid());
    }

    /// The pointer state of the card. The screen fills it from the mouse callbacks while the card is up.
    public FxInput input() {
        return this.input;
    }

    public void draw(GuiGraphicsExtractor graphics, Font font, FxClientState state, int designWidth, int designHeight) {
        ScreenRectangle card = card(designWidth, designHeight);
        Cells cells = this.cells(designWidth, designHeight);
        if (!this.logged) {
            this.logged = true;
            FxEasy.LOGGER.info("FX terminal bonus shown, capital {}", this.claimed ? "claimed" : "unclaimed");
            FxEasy.LOGGER.info("FX bonus layout: card {}x{} at {},{}, close {}x{} at {}, action {}", card.width(),
                    card.height(), card.left(), card.top(), cells.close().width(), cells.close().height(),
                    cells.close().left(), cells.action() == null ? "claimed" : cells.action().left() + ","
                            + cells.action().top());
        }
        int centre = card.left() + card.width() / 2;
        FxUi.fill(graphics, 0, 0, designWidth, designHeight, COVER);
        FxUi.fill(graphics, card.left(), card.top(), card.right(), card.bottom(), FxTheme.TOOLTIP);
        FxUi.border(graphics, card, ARGB.multiplyAlpha(FxTheme.ACCENT, 0.9F));
        FxUi.text(graphics, font, card.left() + CARD_PAD, card.top() + CARD_PAD, FxText.get("fxeasy.bonus.brand"),
                FxTheme.MUTED);
        FxControls.button(graphics, font, cells.close(), FxText.get("fxeasy.bonus.close"), FxTheme.BUTTON, FxTheme.TEXT,
                this.input.hovered(cells.close()), this.input.pressed && this.input.hovered(cells.close()), true);
        int y = card.top() + CARD_PAD + LINE + 5;
        Currency welcome = state.welcomeCurrency();
        Component amount = FxText.big(state.welcomeItems() + " × " + welcome.code());
        int amountWidth = ICON + FxTheme.GAP + font.width(amount);
        FxUi.item(graphics, FxCurrencies.stack(welcome, 1), centre - amountWidth / 2, y - 1, 1);
        FxUi.text(graphics, font, centre - amountWidth / 2 + ICON + FxTheme.GAP, y + (AMOUNT_HEIGHT - LINE) / 2, amount,
                FxTheme.ACCENT);
        y += AMOUNT_HEIGHT + 4;
        FxUi.center(graphics, font, centre, y, FxText.get("fxeasy.bonus.title"), FxTheme.ACCENT);
        y += LINE + 3;
        for (String line : lines(font, FxText.get("fxeasy.bonus.body").getString(), card.width() - CARD_PAD * 2,
                BODY_LINES)) {
            FxUi.center(graphics, font, centre, y, FxText.literal(line), FxTheme.TEXT);
            y += LINE;
        }
        y += 5;
        if (this.claimed) {
            FxUi.center(graphics, font, centre, y + (BUTTON_HEIGHT - LINE) / 2, FxText.get("fxeasy.bonus.claimed"),
                    FxTheme.UP);
        } else {
            FxControls.button(graphics, font, cells.action(), FxText.get("fxeasy.bonus.claim"),
                    ARGB.multiplyAlpha(FxTheme.ACCENT, 0.82F), ARGB.color(255, 12, 16, 20),
                    this.input.hovered(cells.action()), this.input.pressed && this.input.hovered(cells.action()),
                    true);
        }
        y += BUTTON_HEIGHT + 5;
        for (String line : lines(font, FxText.get("fxeasy.bonus.note").getString(), card.width() - CARD_PAD * 2,
                NOTE_LINES)) {
            FxUi.center(graphics, font, centre, y, FxText.literal(line), FxTheme.MUTED);
            y += LINE;
        }
        if (this.input.clicked(cells.close())) {
            dismiss();
            return;
        }
        if (!this.claimed && this.input.clicked(cells.action())) {
            this.claimed = true;
            FxActions.claimBonus();
            FxEasy.LOGGER.info("FX terminal bonus claimed");
            return;
        }
        if (this.input.clicked(card)) {
            dismiss();
        }
    }

    /// Puts the card away, which is what a click on it, the close cell and Escape do. Only the payment is
    /// remembered in the save, so a card that was put away without claiming the capital comes back the next time
    /// the terminal opens.
    public void dismiss() {
        this.closed = true;
        this.input.clear();
        this.logged = false;
        FxEasy.LOGGER.info("FX terminal bonus dismissed");
    }

    /// The card itself: one size whatever the wording is, so the cells a scripted click aims at do not move, and
    /// short enough for the smallest panel the terminal draws.
    public static ScreenRectangle card(int designWidth, int designHeight) {
        int width = Math.min(CARD_WIDTH, designWidth - FxTheme.PAD * 2);
        int height = CARD_PAD * 2 + LINE + 5 + AMOUNT_HEIGHT + 4 + LINE + 3 + LINE * BODY_LINES + 5 + BUTTON_HEIGHT + 5
                + LINE * NOTE_LINES;
        return FxUi.rect((designWidth - width) / 2, (designHeight - height) / 2, width, height);
    }

    /// The call to action and the close cell, which the screen aims a scripted click at. The action is null
    /// while the card carries the confirmation, because the button that claims the capital is gone by then.
    public Cells cells(int designWidth, int designHeight) {
        ScreenRectangle card = card(designWidth, designHeight);
        ScreenRectangle action = this.claimed ? null
                : FxUi.rect(card.left() + (card.width() - BUTTON_WIDTH) / 2,
                        card.top() + CARD_PAD + LINE + 5 + AMOUNT_HEIGHT + 4 + LINE + 3 + LINE * BODY_LINES + 5,
                        BUTTON_WIDTH, BUTTON_HEIGHT);
        ScreenRectangle close = FxUi.rect(card.right() - CARD_PAD - CLOSE_WIDTH, card.top() + CARD_PAD - 1,
                CLOSE_WIDTH, LINE + 1);
        return new Cells(action, close);
    }

    /// The wrapped wording, cut to the lines the card reserved, so a translation that runs long cannot push the
    /// call to action off the card.
    private static List<String> lines(Font font, String text, int width, int maximum) {
        List<String> wrapped = FxUi.wrap(text, width, value -> font.width(FxText.literal(value)));
        return wrapped.size() <= maximum ? wrapped : wrapped.subList(0, maximum);
    }
}
