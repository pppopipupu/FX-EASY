package com.pppopipupu.fxeasy.client;

import module java.base;

import com.pppopipupu.fxeasy.client.panel.FxControls;
import com.pppopipupu.fxeasy.client.panel.FxField;
import com.pppopipupu.fxeasy.client.panel.FxInput;
import com.pppopipupu.fxeasy.client.panel.FxTheme;
import com.pppopipupu.fxeasy.client.panel.FxUi;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

/// The account opening: the player reads the risk disclosure, accepts the terms the platform trades on and
/// registers the account with a name and an age, which is the only step that ends the flow. It comes before the
/// first run walkthrough, dims the whole panel and carries the wording of the current step with the buttons that
/// move between them; [steps] holds the wording of the three steps, this class owns the counter, the
/// registration, the drawing and what a click on a button means.
public final class FxAccountOpening {

    /// What the player asked for by clicking, which the screen turns into a request.
    public enum Action {
        NONE,
        BACK,
        NEXT,
        OPEN
    }

    /// One step: the keys of the title, of the lines of text and of the line under them.
    public record Step(String title, List<String> lines, String hint) {
    }

    /// Where the pieces of one step sit in the design area. A scripted run clicks these rectangles by name
    /// instead of by pixel, so it can walk the opening without knowing the window it runs in.
    public record Cells(ScreenRectangle card, ScreenRectangle primary, ScreenRectangle back, ScreenRectangle name,
            ScreenRectangle age) {
    }

    /// Age the registration asks for. The platform is a joke about a manga and so is this rule: the terminal
    /// refuses to file the form below it, the server keeps whatever it is told.
    public static final int MIN_AGE = 20;

    private static final int CARD_WIDTH = 268;
    private static final int CARD_PAD = 6;
    private static final int LINE = 11;
    private static final int BUTTON_WIDTH = 62;
    private static final int BUTTON_HEIGHT = 14;
    private static final int FIELD_HEIGHT = 12;
    private static final int FIELD_LABEL = 34;
    private static final int AGE_WIDTH = 54;
    private static final int NAME_LENGTH = 12;
    private static final int AGE_DIGITS = 3;
    /// The dim behind the card: the panel stays visible, but it is clearly out of reach until the account is
    /// open, which is why this is thicker than the walkthrough's.
    private static final int COVER = ARGB.color(208, 4, 6, 9);

    private final FxField name = FxField.ascii(NAME_LENGTH);
    private final FxField age = FxField.digits(AGE_DIGITS);
    private int step;
    private boolean active;
    private String message = "";

    /// The three steps of the flow, in the order they are read: what the platform does with the items a player
    /// hands it, the terms it trades on, which are the same numbers the terminal enforces afterwards, and the
    /// registration the account is filed under.
    public static List<Step> steps() {
        return List.of(
                new Step("fxeasy.opening.disclosure.title",
                        List.of("fxeasy.opening.disclosure.line1", "fxeasy.opening.disclosure.line2",
                                "fxeasy.opening.disclosure.line3"),
                        "fxeasy.opening.disclosure.hint"),
                new Step("fxeasy.opening.terms.title",
                        List.of("fxeasy.opening.terms.line1", "fxeasy.opening.terms.line2", "fxeasy.opening.terms.line3",
                                "fxeasy.opening.terms.line4", "fxeasy.opening.terms.line5"),
                        "fxeasy.opening.terms.hint"),
                new Step("fxeasy.opening.register.title",
                        List.of("fxeasy.opening.register.line1", "fxeasy.opening.register.line2"),
                        "fxeasy.opening.register.hint"));
    }

    public boolean active() {
        return this.active;
    }

    public int step() {
        return this.step;
    }

    /// Name typed into the registration, which is what the ledger is written with.
    public String holder() {
        return this.name.text().strip();
    }

    /// Age typed into the registration, zero while the field is still empty.
    public int age() {
        String typed = this.age.text();
        return typed.isEmpty() ? 0 : Integer.parseInt(typed);
    }

    /// Why the registration cannot be filed yet, as the key of the line the card shows instead of its hint.
    public String message() {
        return this.message;
    }

    public void start() {
        this.step = 0;
        this.active = true;
        this.message = "";
    }

    /// Ends the flow without a registration: the platform answered that the account is already open, which is
    /// what happens when it was filed from another client or by an automated run.
    public void dismiss() {
        this.active = false;
        this.message = "";
    }

    /// Applies what the player clicked, with what the registration fields hold. The last step only ends the flow
    /// when the registration is filled in and old enough, and every refusal says why in the line under the
    /// fields. Returns true when the opening is over, which is when the screen has to tell the platform about it.
    public boolean apply(Action action, int steps, String holder, int age) {
        switch (action) {
            case BACK -> {
                this.step = Math.max(0, this.step - 1);
                this.message = "";
            }
            case NEXT -> {
                this.step = Math.min(steps - 1, this.step + 1);
                this.message = "";
            }
            case OPEN -> {
                this.message = complaint(holder, age);
                if (!this.message.isEmpty()) {
                    return false;
                }
                this.active = false;
                return true;
            }
            case NONE -> {
            }
        }
        return false;
    }

    /// The first thing wrong with the registration, empty when there is nothing wrong with it. The age of
    /// majority is the terminal's own joke and not a check: the server keeps whatever it is told.
    public static String complaint(String holder, int age) {
        if (holder.isBlank()) {
            return "fxeasy.opening.register.nameless";
        }
        if (age <= 0) {
            return "fxeasy.opening.register.ageless";
        }
        return age < MIN_AGE ? "fxeasy.opening.register.underage" : "";
    }

    /// The rectangles of the [index]th of [steps] in a design area of that size, which is what [draw] lays the
    /// step out with and what a scripted run clicks: the last step is the one that carries the two boxes.
    public static Cells cells(Step step, int index, int steps, int designWidth, int designHeight) {
        boolean last = index + 1 >= steps;
        int form = last ? FIELD_HEIGHT * 2 + FxTheme.GAP + LINE + FxTheme.GAP : 0;
        int width = Math.min(CARD_WIDTH, designWidth - FxTheme.PAD * 2);
        int height = CARD_PAD * 2 + LINE * (step.lines().size() + 2) + form + FxTheme.GAP * 2 + BUTTON_HEIGHT;
        ScreenRectangle card = FxUi.rect((designWidth - width) / 2, (designHeight - height) / 2, width, height);
        int buttonY = card.bottom() - CARD_PAD - BUTTON_HEIGHT;
        ScreenRectangle primary = FxUi.rect(card.right() - CARD_PAD - BUTTON_WIDTH, buttonY, BUTTON_WIDTH,
                BUTTON_HEIGHT);
        ScreenRectangle back = FxUi.rect(primary.left() - FxTheme.GAP - BUTTON_WIDTH, buttonY, BUTTON_WIDTH,
                BUTTON_HEIGHT);
        int fields = card.top() + CARD_PAD + LINE * (step.lines().size() + 1) + FxTheme.GAP;
        ScreenRectangle name = FxUi.rect(card.left() + CARD_PAD + FIELD_LABEL, fields,
                card.width() - CARD_PAD * 2 - FIELD_LABEL, FIELD_HEIGHT);
        ScreenRectangle age = FxUi.rect(name.left(), name.bottom() + FxTheme.GAP, AGE_WIDTH, FIELD_HEIGHT);
        return new Cells(card, primary, back, name, age);
    }

    /// Draws the dimmed frame and the card of the current step, whose last one carries the registration. [input]
    /// is the opening's own pointer state, which the screen fills instead of the panel's while the opening is up.
    public Action draw(GuiGraphicsExtractor graphics, Font font, FxInput input, Step step, int steps, int designWidth, int designHeight) {
        boolean last = this.step + 1 >= steps;
        Cells cells = cells(step, this.step, steps, designWidth, designHeight);
        ScreenRectangle card = cells.card();
        FxUi.fill(graphics, 0, 0, designWidth, designHeight, COVER);
        FxUi.fill(graphics, card.left(), card.top(), card.right(), card.bottom(), FxTheme.TOOLTIP);
        FxUi.border(graphics, FxUi.rect(card.left() - 1, card.top() - 1, card.width() + 2, card.height() + 2),
                ARGB.multiplyAlpha(FxTheme.ACCENT, 0.4F));
        FxUi.border(graphics, card, FxTheme.ACCENT);
        FxUi.text(graphics, font, card.left() + CARD_PAD, card.top() + CARD_PAD, FxText.get(step.title()), FxTheme.ACCENT);
        FxUi.right(graphics, font, card.right() - CARD_PAD, card.top() + CARD_PAD,
                FxText.literal("%d/%d", this.step + 1, steps), FxTheme.MUTED);
        int y = card.top() + CARD_PAD + LINE;
        for (String line : step.lines()) {
            FxUi.clipped(graphics, font, card, card.left() + CARD_PAD, y, FxText.get(line), FxTheme.TEXT);
            y += LINE;
        }
        if (last) {
            y = this.registration(graphics, font, input, cells);
        }
        Component under = this.message.isEmpty() ? FxText.get(step.hint()) : FxText.get(this.message);
        FxUi.clipped(graphics, font, card, card.left() + CARD_PAD, y + FxTheme.GAP, under,
                this.message.isEmpty() ? FxTheme.MUTED : FxTheme.DOWN);

        ScreenRectangle primary = cells.primary();
        ScreenRectangle back = cells.back();
        if (this.step > 0) {
            FxControls.button(graphics, font, back, FxText.get("fxeasy.opening.back"), FxTheme.BUTTON, FxTheme.TEXT,
                    input.hovered(back), input.pressed && input.hovered(back), true);
        }
        FxControls.button(graphics, font, primary, FxText.get(last ? "fxeasy.opening.register" : "fxeasy.opening.next"),
                FxTheme.BUTTON, FxTheme.ACCENT, input.hovered(primary), input.pressed && input.hovered(primary), true);
        if (input.clicked(primary)) {
            return last ? Action.OPEN : Action.NEXT;
        }
        if (this.step > 0 && input.clicked(back)) {
            return Action.BACK;
        }
        return Action.NONE;
    }

    /// The two fields of the registration step with their labels, drawn from [y] down. Returns the bottom of the
    /// block, which is where the line under it goes. The fields are their own value, so a click continues what
    /// they hold and they keep showing it while unfocused: seeding them with anything else would wipe the name
    /// the second time it is clicked, and the box would fall back to empty after Enter.
    private int registration(GuiGraphicsExtractor graphics, Font font, FxInput input, Cells cells) {
        ScreenRectangle card = cells.card();
        ScreenRectangle name = cells.name();
        ScreenRectangle age = cells.age();
        FxUi.text(graphics, font, card.left() + CARD_PAD, name.top() + 2, FxText.get("fxeasy.opening.name"), FxTheme.MUTED);
        FxUi.text(graphics, font, card.left() + CARD_PAD, age.top() + 2, FxText.get("fxeasy.opening.age"), FxTheme.MUTED);
        this.name.click(input, name, this.name.text());
        this.age.click(input, age, this.age.text());
        field(graphics, font, input, name, this.name);
        field(graphics, font, input, age, this.age);
        return age.bottom();
    }

    private static void field(GuiGraphicsExtractor graphics, Font font, FxInput input, ScreenRectangle area, FxField field) {
        FxUi.fill(graphics, area.left(), area.top(), area.right(), area.bottom(), FxTheme.CHART_BG);
        FxUi.border(graphics, area, field.focused(input) ? FxTheme.ACCENT : FxTheme.CARD_BORDER);
        field.draw(graphics, font, area, input, field.text());
    }
}
