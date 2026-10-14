package com.pppopipupu.fxeasy.client.panel;

import module java.base;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

/// Stateless drawing for the terminal controls. Hover, press and drag state lives in the screen, so every
/// control here is a pure function of its rectangle, its state and the palette.
public final class FxControls {

    private static final int LABEL_DARK = ARGB.color(255, 16, 20, 24);

    private FxControls() {
    }

    public static void button(GuiGraphicsExtractor graphics, Font font, ScreenRectangle area, Component label,
            int background, int text, boolean hovered, boolean pressed, boolean enabled) {
        int fill = enabled ? hovered ? ARGB.addRgb(background, ARGB.color(64, 24, 26, 30)) : background : FxTheme.BUTTON_DISABLED;
        if (pressed) {
            fill = ARGB.addRgb(fill, ARGB.color(48, 30, 32, 36));
        }
        FxUi.fill(graphics, area.left(), area.top(), area.right(), area.bottom(), fill);
        FxUi.border(graphics, area, ARGB.multiplyAlpha(FxTheme.CARD_BORDER, enabled ? 1.0F : 0.4F));
        FxUi.clipped(graphics, font, area, area.left() + (area.width() - font.width(label)) / 2,
                area.top() + (area.height() - 8) / 2, label, enabled ? text : FxTheme.MUTED);
    }

    /// A segmented choice: the selected cell carries the accent, the others stay flat.
    public static void choice(GuiGraphicsExtractor graphics, Font font, ScreenRectangle area, Component label,
            boolean selected, boolean hovered, int accent) {
        int background = selected ? accent : hovered ? FxTheme.BUTTON_HOVER : FxTheme.BUTTON;
        FxUi.fill(graphics, area.left(), area.top(), area.right(), area.bottom(), background);
        FxUi.border(graphics, area, selected ? ARGB.multiplyAlpha(accent, 0.9F) : FxTheme.CARD_BORDER);
        FxUi.clipped(graphics, font, area, area.left() + (area.width() - font.width(label)) / 2,
                area.top() + (area.height() - 8) / 2, label, selected ? LABEL_DARK : FxTheme.TEXT);
    }

    /// Slider track with the value centered above it. [fraction] is already clamped by the caller.
    public static void slider(GuiGraphicsExtractor graphics, Font font, ScreenRectangle area, double fraction,
            boolean hovered, boolean active) {
        int trackY = area.top() + area.height() - 3;
        FxUi.fill(graphics, area.left(), trackY, area.right(), trackY + 3, FxTheme.SLIDER_TRACK);
        int filled = area.left() + (int) Math.round((area.width() - 1) * fraction);
        FxUi.fill(graphics, area.left(), trackY, Math.max(area.left() + 1, filled), trackY + 3, FxTheme.SLIDER_FILL);
        int handle = area.height() >= 10 ? 3 : 2;
        FxUi.fill(graphics, Math.max(area.left(), filled - handle / 2), trackY - handle,
                Math.min(area.right(), Math.max(area.left(), filled - handle / 2) + handle), trackY + 3 + handle,
                hovered || active ? FxTheme.ACCENT : FxTheme.AXIS);
    }

    public static void scrollbar(GuiGraphicsExtractor graphics, ScreenRectangle area, double offset, double visible, boolean hovered) {
        FxUi.fill(graphics, area.left(), area.top(), area.right(), area.bottom(), ARGB.multiplyAlpha(FxTheme.BUTTON, 0.6F));
        int height = Math.max(8, (int) Math.round(area.height() * Math.min(1.0D, visible)));
        int travel = area.height() - height;
        int top = area.top() + (int) Math.round(travel * Math.clamp(offset, 0.0D, 1.0D));
        FxUi.fill(graphics, area.left(), top, area.right(), top + height, hovered ? FxTheme.ACCENT : FxTheme.SCROLL);
    }

    /// Hand drawn tooltip: a card behind the lines, kept inside the screen.
    public static void tooltip(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, int screenWidth,
            int screenHeight, List<Component> lines, int accent) {
        int width = 0;
        for (Component line : lines) {
            width = Math.max(width, font.width(line));
        }
        int height = lines.size() * (font.lineHeight + 1);
        int x = Math.min(mouseX + 8, screenWidth - width - FxTheme.PAD * 2 - 2);
        int y = Math.min(mouseY + 8, screenHeight - height - FxTheme.PAD * 2 - 2);
        x = Math.max(0, x);
        y = Math.max(0, y);
        ScreenRectangle area = FxUi.rect(x, y, width + FxTheme.PAD * 2, height + FxTheme.PAD * 2);
        FxUi.fill(graphics, area.left(), area.top(), area.right(), area.bottom(), FxTheme.TOOLTIP);
        FxUi.border(graphics, area, ARGB.multiplyAlpha(accent, 0.7F));
        int lineY = area.top() + FxTheme.PAD;
        for (Component line : lines) {
            FxUi.text(graphics, font, area.left() + FxTheme.PAD, lineY, line, FxTheme.TEXT);
            lineY += font.lineHeight + 1;
        }
    }

    /// Thin legend chip: a colored bar followed by its label.
    public static void legend(GuiGraphicsExtractor graphics, Font font, int x, int y, int colour, Component label) {
        FxUi.fill(graphics, x, y + 3, x + 6, y + 5, colour);
        FxUi.text(graphics, font, x + 8, y, label, FxTheme.MUTED);
    }

    /// A filled tag, used for the last price on the axis.
    public static void tag(GuiGraphicsExtractor graphics, Font font, ScreenRectangle area, Component label, int background) {
        FxUi.fill(graphics, area.left(), area.top(), area.right(), area.bottom(), background);
        FxUi.clipped(graphics, font, area, area.left() + 2, area.top() + (area.height() - 8) / 2, label, LABEL_DARK);
    }
}
