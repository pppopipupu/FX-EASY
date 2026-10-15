package com.pppopipupu.fxeasy.client.panel;

import module java.base;

import com.pppopipupu.fxeasy.client.FxText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;

/// An input box drawn with the terminal font. Focus lives in [FxInput], so a click on another field or outside
/// every field moves it without the panels knowing about each other. While a field holds focus its text is the
/// value on screen; the owner reads it back with [text] every frame. A name field takes printable ASCII, which
/// is what an account holder is read and drawn as.
public final class FxField {

    private static final long CARET_PERIOD = 1_000L;
    private static final char FIRST_PRINTABLE = 0x20;
    private static final char LAST_PRINTABLE = 0x7F;

    private final int maxLength;
    private final boolean digitsOnly;
    private String text = "";

    private FxField(int maxLength, boolean digitsOnly) {
        this.maxLength = maxLength;
        this.digitsOnly = digitsOnly;
    }

    public static FxField digits(int maxLength) {
        return new FxField(maxLength, true);
    }

    public static FxField ascii(int maxLength) {
        return new FxField(maxLength, false);
    }

    public boolean focused(FxInput input) {
        return input.focus == this;
    }

    public void click(FxInput input, ScreenRectangle area, String seed) {
        if (input.pressed && FxUi.hit(area, input.mouseX, input.mouseY)) {
            if (!focused(input)) {
                this.text = seed;
            }
            input.focus = this;
            input.focusClaimed = true;
        }
    }

    public void type(String codepoints) {
        for (int index = 0; index < codepoints.length() && this.text.length() < this.maxLength; index++) {
            char codepoint = codepoints.charAt(index);
            if (accepted(codepoint)) {
                this.text += codepoint;
            }
        }
    }

    private boolean accepted(char codepoint) {
        return this.digitsOnly ? Character.isDigit(codepoint)
                : codepoint >= FIRST_PRINTABLE && codepoint < LAST_PRINTABLE;
    }

    /// Drops the last character. Returns whether there was one, so the screen can let the key fall through when
    /// the field is already empty.
    public boolean backspace() {
        if (this.text.isEmpty()) {
            return false;
        }
        this.text = this.text.substring(0, this.text.length() - 1);
        return true;
    }

    public String text() {
        return this.text;
    }

    public String shown(FxInput input, String fallback) {
        return focused(input) && !this.text.isEmpty() ? this.text : fallback;
    }

    public void draw(GuiGraphicsExtractor graphics, Font font, ScreenRectangle area, FxInput input, String fallback) {
        Component shown = FxText.literal(shown(input, fallback));
        int x = area.left() + 2;
        int y = area.top() + 2;
        FxUi.clipped(graphics, font, area, x, y, shown, FxTheme.TEXT);
        if (focused(input) && System.currentTimeMillis() % CARET_PERIOD < CARET_PERIOD / 2) {
            int caret = x + font.width(shown);
            FxUi.fill(graphics, caret, area.top() + 1, caret + 1, area.bottom() - 1, FxTheme.ACCENT);
        }
    }
}
