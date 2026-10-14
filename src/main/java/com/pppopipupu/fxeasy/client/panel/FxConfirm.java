package com.pppopipupu.fxeasy.client.panel;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import com.pppopipupu.fxeasy.client.FxText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

/// The modal question the panel puts in front of an operation that cannot be taken back: a card with the
/// question, what the operation would do and a confirm next to a cancel. It owns its own [FxInput], which the
/// screen fills while the card is up, so a click can neither reach a control behind it nor confirm the wrong
/// thing. The action runs on the confirm click, so a request that is never confirmed never leaves the client,
/// and every step is logged under the id of the request for the end to end runs to read.
public final class FxConfirm {

    /// What the operation would do and the request to run when it is confirmed. The action is built by the panel
    /// that saw the click and captures the amounts of that click, not the state of the frame it runs in.
    public record Request(String id, Component title, List<Component> body, Component confirm, Runnable action) {
    }

    /// The two buttons of the card, which the screen aims a scripted click at.
    public record Buttons(ScreenRectangle confirm, ScreenRectangle cancel) {
    }

    private static final int CARD_WIDTH = 240;
    private static final int CARD_PAD = 8;
    private static final int LINE = 10;
    private static final int BUTTON_WIDTH = 64;
    private static final int BUTTON_HEIGHT = 14;
    private static final int COVER = ARGB.color(178, 4, 6, 9);

    private final FxInput input = new FxInput();
    private Request request;

    public boolean active() {
        return this.request != null;
    }

    /// The pointer state of the card. The screen fills it from the mouse callbacks while the card is up.
    public FxInput input() {
        return this.input;
    }

    /// Opens the card. The line it logs carries the wording of the request with its amounts already formatted, so
    /// a scripted run can assert what the player is asked to agree to and not only that a card came up.
    public void open(Request request) {
        this.request = request;
        this.input.clear();
        FxEasy.LOGGER.info("FX terminal confirm: {} open, {}", request.id(),
                request.body().isEmpty() ? request.title().getString() : request.body().getFirst().getString());
    }

    public void close() {
        this.request = null;
        this.input.clear();
    }

    /// The two buttons of the card that is up, which the screen aims a scripted click at, or null while no
    /// request is open.
    public Buttons buttons(int designWidth, int designHeight) {
        return this.request == null ? null : buttons(this.request, designWidth, designHeight);
    }

    public void draw(GuiGraphicsExtractor graphics, Font font, int designWidth, int designHeight) {
        Request current = this.request;
        if (current == null) {
            return;
        }
        FxUi.fill(graphics, 0, 0, designWidth, designHeight, COVER);
        ScreenRectangle card = card(current, designWidth, designHeight);
        FxUi.fill(graphics, card.left(), card.top(), card.right(), card.bottom(), FxTheme.TOOLTIP);
        FxUi.border(graphics, card, ARGB.multiplyAlpha(FxTheme.ACCENT, 0.8F));
        FxUi.clipped(graphics, font, card, card.left() + CARD_PAD, card.top() + CARD_PAD, current.title(),
                FxTheme.ACCENT);
        int y = card.top() + CARD_PAD + LINE + FxTheme.GAP;
        for (Component line : current.body()) {
            FxUi.clipped(graphics, font, card, card.left() + CARD_PAD, y, line, FxTheme.TEXT);
            y += LINE;
        }
        Buttons buttons = buttons(current, designWidth, designHeight);
        FxControls.button(graphics, font, buttons.cancel(), FxText.get("fxeasy.confirm.cancel"), FxTheme.BUTTON,
                FxTheme.TEXT, this.input.hovered(buttons.cancel()),
                this.input.pressed && this.input.hovered(buttons.cancel()), true);
        FxControls.button(graphics, font, buttons.confirm(), current.confirm(),
                ARGB.multiplyAlpha(FxTheme.ACCENT, 0.82F), ARGB.color(255, 12, 16, 20),
                this.input.hovered(buttons.confirm()), this.input.pressed && this.input.hovered(buttons.confirm()), true);
        if (this.input.clicked(buttons.confirm())) {
            Runnable action = current.action();
            close();
            FxEasy.LOGGER.info("FX terminal confirm: {} confirmed", current.id());
            action.run();
            return;
        }
        if (this.input.clicked(buttons.cancel())
                || (this.input.pressed && !FxUi.hit(card, this.input.mouseX, this.input.mouseY))) {
            close();
            FxEasy.LOGGER.info("FX terminal confirm: {} cancelled", current.id());
        }
    }

    /// The card of a request: as wide as it wants up to the design width, and as tall as the question and the
    /// buttons need, which keeps the registration sized text inside the smallest panel the terminal draws.
    private static ScreenRectangle card(Request request, int designWidth, int designHeight) {
        int width = Math.min(CARD_WIDTH, designWidth - FxTheme.PAD * 2);
        int height = CARD_PAD * 2 + LINE * (request.body().size() + 1) + FxTheme.GAP * 2 + BUTTON_HEIGHT;
        return FxUi.rect((designWidth - width) / 2, (designHeight - height) / 2, width, height);
    }

    private static Buttons buttons(Request request, int designWidth, int designHeight) {
        ScreenRectangle card = card(request, designWidth, designHeight);
        ScreenRectangle confirm = FxUi.rect(card.right() - CARD_PAD - BUTTON_WIDTH,
                card.bottom() - CARD_PAD - BUTTON_HEIGHT, BUTTON_WIDTH, BUTTON_HEIGHT);
        ScreenRectangle cancel = FxUi.rect(confirm.left() - FxTheme.GAP - BUTTON_WIDTH, confirm.top(), BUTTON_WIDTH,
                BUTTON_HEIGHT);
        return new Buttons(confirm, cancel);
    }
}
