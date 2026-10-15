package com.pppopipupu.fxeasy.client.panel;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import com.pppopipupu.fxeasy.client.FxAccountOpening;
import com.pppopipupu.fxeasy.client.FxActions;
import com.pppopipupu.fxeasy.client.FxCheckout;
import com.pppopipupu.fxeasy.client.FxClientState;
import com.pppopipupu.fxeasy.client.FxLounge;
import com.pppopipupu.fxeasy.client.FxTutorial;
import com.pppopipupu.fxeasy.client.FxWelcomeBonus;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/// Everything that stands in front of the panel, in the order it wins over the rest: the account opening, which
/// every account goes through once, the first run walkthrough behind it, the card the platform hands the welcome
/// capital over with, the confirmation of an operation that cannot be taken back and the world intel overlay the
/// panel opens by itself. Each of them is driven by its own [FxInput], so a click can never reach a control
/// behind the one that owns the pointer, and the keyboard follows the same order.
public final class FxOverlays {

    /// Which overlay owns the pointer and the keyboard this frame, in the order the overlays stand in front of
    /// the panel.
    public enum Owner {
        OPENING,
        TUTORIAL,
        BONUS,
        CONFIRM,
        LOUNGE,
        CHECKOUT,
        INTEL,
        PANEL
    }

    private final FxInput input = new FxInput();
    private final FxTutorial tutorial = new FxTutorial();
    private final FxAccountOpening opening = new FxAccountOpening();
    private final FxWorldIntel intel = new FxWorldIntel();
    private final FxConfirm confirm = new FxConfirm();
    private final FxWelcomeBonus bonus = new FxWelcomeBonus();
    private final FxLounge lounge = new FxLounge();
    private final FxCheckout checkout = new FxCheckout();
    private List<FxTutorial.Step> steps = List.of();

    /// The steps of the walkthrough, which are rectangles of the layout the panel was last measured with and are
    /// therefore rebuilt with it. The steps of the account opening need no layout, so [FxAccountOpening] keeps
    /// its own.
    public void measure(List<FxTutorial.Step> steps) {
        this.steps = steps;
    }

    public List<FxTutorial.Step> steps() {
        return this.steps;
    }

    /// The input the opening and the walkthrough share, which are the two overlays that take text.
    public FxInput input() {
        return this.input;
    }

    public FxTutorial tutorial() {
        return this.tutorial;
    }

    public FxAccountOpening opening() {
        return this.opening;
    }

    public FxWorldIntel intel() {
        return this.intel;
    }

    public FxConfirm confirm() {
        return this.confirm;
    }

    public FxWelcomeBonus bonus() {
        return this.bonus;
    }

    /// The lounge of the subscription, which the shop page opens and the player closes.
    public FxLounge lounge() {
        return this.lounge;
    }

    /// The checkout of a tier, which the shop page opens when the player picks a card and the player pays at or
    /// closes.
    public FxCheckout checkout() {
        return this.checkout;
    }

    public Owner owner(FxClientState state) {
        if (this.opening.active()) {
            return Owner.OPENING;
        }
        if (this.tutorial.active()) {
            return Owner.TUTORIAL;
        }
        if (this.bonus.active(state)) {
            return Owner.BONUS;
        }
        if (this.confirm.active()) {
            return Owner.CONFIRM;
        }
        if (this.lounge.active()) {
            return Owner.LOUNGE;
        }
        if (this.checkout.active()) {
            return Owner.CHECKOUT;
        }
        return this.intel.active() ? Owner.INTEL : Owner.PANEL;
    }

    public FxInput pointer(FxClientState state, FxInput panel) {
        return switch (owner(state)) {
            case OPENING, TUTORIAL -> this.input;
            case BONUS -> this.bonus.input();
            case CONFIRM -> this.confirm.input();
            case LOUNGE -> this.lounge.input();
            case CHECKOUT -> this.checkout.input();
            case INTEL -> this.intel.input();
            case PANEL -> panel;
        };
    }

    public boolean active(FxClientState state) {
        return owner(state) != Owner.PANEL;
    }

    /// Points every overlay input at the point the panel input is at, unless that input carries the position of
    /// a press or a release of its own this frame, which is the only position a scripted click is aimed with.
    public void samplePointer(int mouseX, int mouseY) {
        for (FxInput input : this.inputs()) {
            if (!input.pressed && !input.released) {
                input.mouseX = mouseX;
                input.mouseY = mouseY;
            }
        }
    }

    public void dropUnclaimedFocus() {
        if (this.input.pressed && !this.input.focusClaimed) {
            this.input.focus = null;
        }
    }

    public void clearInputs() {
        for (FxInput input : this.inputs()) {
            input.clear();
        }
    }

    /// Releases every overlay control a scripted click left held: a scripted press is released at the end of the
    /// frame that handles it, and a control that were left held would keep dragging.
    public void release() {
        for (FxInput input : this.inputs()) {
            input.down = false;
            input.endDrag();
        }
    }

    /// Draws whatever stands in front of the panel, in the order it wins over the rest, and applies what the
    /// player clicked. Every overlay waits for the panel to have revealed itself and for the platform to have
    /// answered, because none of them can know what to show before that. Returns the page the walkthrough forced
    /// the panel to, or null when no step asked for one.
    public FxTab draw(GuiGraphicsExtractor graphics, Font font, FxClientState state, boolean revealed,
            int designWidth, int designHeight) {
        FxTab forced = null;
        if (revealed && state.connected()) {
            if (this.opening.active() && state.opened()) {
                this.opening.dismiss();
            }
            if (!this.opening.active() && !state.opened()) {
                this.opening.start();
                FxEasy.LOGGER.info("FX terminal account opening, {} steps ending in the registration",
                        FxAccountOpening.steps().size());
            }
            if (this.opening.active()) {
                this.accountOpening(graphics, font, designWidth, designHeight);
                return null;
            }
            forced = this.walkthrough(graphics, font, state, designWidth, designHeight);
            if (this.tutorial.active()) {
                return forced;
            }
            if (this.bonus.active(state)) {
                this.bonus.draw(graphics, font, state, designWidth, designHeight);
                return forced;
            }
        }
        if (this.intel.active()) {
            this.intel.draw(graphics, font, state, designWidth, designHeight);
        }
        if (this.lounge.active()) {
            this.lounge.draw(graphics, font, state, designWidth, designHeight);
        }
        if (this.checkout.active()) {
            this.checkout.draw(graphics, font, state, designWidth, designHeight);
        }
        this.confirm.draw(graphics, font, designWidth, designHeight);
        return forced;
    }

    private void accountOpening(GuiGraphicsExtractor graphics, Font font, int designWidth, int designHeight) {
        List<FxAccountOpening.Step> openingSteps = FxAccountOpening.steps();
        FxAccountOpening.Step step = openingSteps.get(this.opening.step());
        FxAccountOpening.Action action = this.opening.draw(graphics, font, this.input, step, openingSteps.size(),
                designWidth, designHeight);
        if (this.opening.apply(action, openingSteps.size(), this.opening.holder(), this.opening.age())) {
            FxActions.openAccount(this.opening.holder(), this.opening.age());
            FxEasy.LOGGER.info("FX terminal account registered as {} (age {})", this.opening.holder(),
                    this.opening.age());
        }
    }

    /// The first run walkthrough, which only runs once the account is open. It forces the page of the step that
    /// is up, so the area it points at is the one on screen.
    private FxTab walkthrough(GuiGraphicsExtractor graphics, Font font, FxClientState state, int designWidth,
            int designHeight) {
        if (this.tutorial.active() && state.tutorialSeen()) {
            this.tutorial.dismiss();
        }
        if (!this.tutorial.active()) {
            if (state.tutorialSeen()) {
                return null;
            }
            this.tutorial.start();
        }
        FxTutorial.Step step = this.steps.get(this.tutorial.step());
        FxTutorial.Action action = this.tutorial.draw(graphics, font, this.input, step, this.steps.size(),
                designWidth, designHeight);
        if (this.tutorial.apply(action, this.steps.size())) {
            FxActions.markTutorialSeen();
            FxEasy.LOGGER.info("FX terminal walkthrough finished");
        }
        return step.page();
    }

    private List<FxInput> inputs() {
        return List.of(this.input, this.bonus.input(), this.confirm.input(), this.lounge.input(),
                this.checkout.input(), this.intel.input());
    }
}
