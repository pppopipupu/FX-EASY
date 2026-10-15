package com.pppopipupu.fxeasy.client;

import module java.base;

import com.pppopipupu.fxeasy.client.panel.FxControls;
import com.pppopipupu.fxeasy.client.panel.FxInput;
import com.pppopipupu.fxeasy.client.panel.FxTab;
import com.pppopipupu.fxeasy.client.panel.FxTheme;
import com.pppopipupu.fxeasy.client.panel.FxUi;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/// The first run walkthrough: it lights one area of the panel at a time, dims everything around it and carries
/// the text of the step with the buttons that move between them. The screen owns the steps themselves, because
/// the rectangles and the page a step needs open come out of its layout; this class owns the step counter and
/// the drawing.
public final class FxTutorial {

    public enum Action {
        NONE,
        BACK,
        NEXT,
        FINISH
    }

    /// The three cells of the card of a step, which a scripted click aims at. [back] is null on the first step.
    public record Cells(ScreenRectangle primary, ScreenRectangle back, ScreenRectangle skip) {
    }

    /// One step: the page the panel should show while the step is up, the area it points at and the keys of the
    /// text it carries.
    public record Step(FxTab page, ScreenRectangle area, String title, String body, String hint) {
    }

    private static final int CARD_WIDTH = 182;
    private static final int CARD_PAD = 5;
    private static final int LINE = 10;
    private static final int BUTTON_HEIGHT = 13;
    private static final int BUTTON_WIDTH = 48;
    /// The dim over everything the current step does not point at. Opaque enough to read the card against, thin
    /// enough to keep the panel's shape visible underneath.
    private static final int COVER = ARGB.color(184, 4, 6, 9);

    private int step;
    private boolean active;

    public boolean active() {
        return this.active;
    }

    public int step() {
        return this.step;
    }

    public void start() {
        this.step = 0;
        this.active = true;
    }

    public void dismiss() {
        this.active = false;
    }

    /// Applies what the player clicked. Returns true when the walkthrough just left its last step, so the
    /// screen knows to tell the server that the player is done with it.
    public boolean apply(Action action, int steps) {
        switch (action) {
            case BACK -> this.step = Math.max(0, this.step - 1);
            case NEXT -> {
                if (this.step + 1 >= steps) {
                    this.active = false;
                    return true;
                }
                this.step++;
            }
            case FINISH -> {
                this.active = false;
                return true;
            }
            case NONE -> {
            }
        }
        return false;
    }

    /// Draws the dimmed frame, the lit area and the card of the current step. [input] is the walkthrough's own
    /// pointer state, which the screen fills instead of the panel's while the walkthrough is up.
    public Action draw(GuiGraphicsExtractor graphics, Font font, FxInput input, Step step, int steps, int designWidth, int designHeight) {
        ScreenRectangle target = step.area();
        this.cover(graphics, target, designWidth, designHeight);
        float pulse = 0.62F + 0.38F * Mth.sin(Util.getMillis() / 340.0F);
        FxUi.border(graphics, FxUi.grow(target, 1),
                ARGB.multiplyAlpha(FxTheme.ACCENT, pulse * 0.35F));
        FxUi.border(graphics, target, ARGB.multiplyAlpha(FxTheme.ACCENT, pulse));
        return this.card(graphics, font, input, step, steps, designWidth, designHeight);
    }

    private void cover(GuiGraphicsExtractor graphics, ScreenRectangle target, int designWidth, int designHeight) {
        FxUi.fill(graphics, 0, 0, designWidth, target.top(), COVER);
        FxUi.fill(graphics, 0, target.bottom(), designWidth, designHeight, COVER);
        FxUi.fill(graphics, 0, target.top(), target.left(), target.bottom(), COVER);
        FxUi.fill(graphics, target.right(), target.top(), designWidth, target.bottom(), COVER);
    }

    /// The card of one step: under the area the step points at, or above it when there is no room below, and
    /// always inside the frame.
    public static ScreenRectangle cardArea(Step step, int designWidth, int designHeight) {
        int width = Math.min(CARD_WIDTH, designWidth - FxTheme.PAD * 2);
        int height = CARD_PAD * 2 + LINE * 2 + FxTheme.GAP + BUTTON_HEIGHT;
        ScreenRectangle target = step.area();
        int x = Math.clamp(target.left(), FxTheme.PAD, Math.max(FxTheme.PAD, designWidth - width - FxTheme.PAD));
        int y = target.bottom() + FxTheme.PAD + 2;
        if (y + height > designHeight - FxTheme.PAD) {
            y = target.top() - height - FxTheme.PAD - 2;
        }
        y = Math.clamp(y, FxTheme.PAD, Math.max(FxTheme.PAD, designHeight - height - FxTheme.PAD));
        return FxUi.rect(x, y, width, height);
    }

    /// The three cells of the card of a step: the button that advances or finishes the walkthrough, the one that
    /// goes back and the one that skips the rest, which a scripted click aims at. The back cell is null on the
    /// first step, because there is nothing to go back to.
    public static Cells cells(Step step, int index, int designWidth, int designHeight) {
        ScreenRectangle card = cardArea(step, designWidth, designHeight);
        int buttonY = card.bottom() - CARD_PAD - BUTTON_HEIGHT;
        ScreenRectangle skip = FxUi.rect(card.left() + CARD_PAD, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT);
        ScreenRectangle primary = FxUi.rect(card.right() - CARD_PAD - BUTTON_WIDTH, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT);
        ScreenRectangle back = index <= 0 ? null
                : FxUi.rect(primary.left() - FxTheme.GAP - BUTTON_WIDTH, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT);
        return new Cells(primary, back, skip);
    }

    private Action card(GuiGraphicsExtractor graphics, Font font, FxInput input, Step step, int steps, int designWidth, int designHeight) {
        ScreenRectangle card = cardArea(step, designWidth, designHeight);
        Cells cells = cells(step, this.step, designWidth, designHeight);
        FxUi.fill(graphics, card.left(), card.top(), card.right(), card.bottom(), FxTheme.TOOLTIP);
        FxUi.border(graphics, card, ARGB.multiplyAlpha(FxTheme.ACCENT, 0.8F));

        Component title = FxText.get(step.title());
        FxUi.text(graphics, font, card.left() + CARD_PAD, card.top() + CARD_PAD, title, FxTheme.ACCENT);
        FxUi.right(graphics, font, card.right() - CARD_PAD, card.top() + CARD_PAD,
                FxText.literal("%d/%d", this.step + 1, steps), FxTheme.MUTED);
        FxUi.clipped(graphics, font, card, card.left() + CARD_PAD, card.top() + CARD_PAD + LINE, FxText.get(step.body()), FxTheme.TEXT);
        FxUi.clipped(graphics, font, card, card.left() + CARD_PAD, card.top() + CARD_PAD + LINE * 2, FxText.get(step.hint()),
                FxTheme.MUTED);

        ScreenRectangle skip = cells.skip();
        ScreenRectangle primary = cells.primary();
        ScreenRectangle back = cells.back();
        boolean last = this.step + 1 >= steps;
        FxControls.button(graphics, font, skip, FxText.get("fxeasy.tutorial.skip"), FxTheme.BUTTON, FxTheme.MUTED,
                input.hovered(skip), input.pressed && input.hovered(skip), true);
        if (this.step > 0) {
            FxControls.button(graphics, font, back, FxText.get("fxeasy.tutorial.back"), FxTheme.BUTTON, FxTheme.TEXT,
                    input.hovered(back), input.pressed && input.hovered(back), true);
        }
        FxControls.button(graphics, font, primary, FxText.get(last ? "fxeasy.tutorial.finish" : "fxeasy.tutorial.next"),
                FxTheme.BUTTON, FxTheme.ACCENT, input.hovered(primary), input.pressed && input.hovered(primary), true);
        if (input.clicked(primary)) {
            return Action.NEXT;
        }
        if (this.step > 0 && input.clicked(back)) {
            return Action.BACK;
        }
        return input.clicked(skip) ? Action.FINISH : Action.NONE;
    }
}
