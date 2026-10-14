package com.pppopipupu.fxeasy.client.panel;

import module java.base;

import com.pppopipupu.fxeasy.client.FxClientState;
import com.pppopipupu.fxeasy.client.FxText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

/// Session equity curve in items of the display currency, one pixel line per sample with a soft gradient area
/// underneath, anchored to the right edge so the newest sample never moves. The series is sampled client side,
/// so it only covers this session.
public final class FxEquityChart {

    private FxEquityChart() {
    }

    public static void draw(GuiGraphicsExtractor graphics, Font font, FxClientState state, ScreenRectangle area, int mouseX, int mouseY) {
        FxUi.fill(graphics, area.left(), area.top(), area.right(), area.bottom(), FxTheme.CHART_BG);
        FxUi.border(graphics, area, FxTheme.CARD_BORDER);
        int samples = state.equitySamples();
        if (samples < 2) {
            FxUi.text(graphics, font, area.left() + FxTheme.PAD, area.top() + FxTheme.PAD, FxText.get("fxeasy.panel.equity.curve"),
                    FxTheme.MUTED);
            return;
        }
        double low = Double.MAX_VALUE;
        double high = -Double.MAX_VALUE;
        for (int index = 0; index < samples; index++) {
            double sample = state.equitySample(index);
            low = Math.min(low, sample);
            high = Math.max(high, sample);
        }
        double padding = Math.max((high - low) * 0.1D, Math.abs(high) * 1.0E-4D);
        low -= padding;
        high += padding;
        double range = Math.max(high - low, 1.0E-9D);
        int top = area.top() + 1;
        int bottom = area.bottom() - 9;
        int step = Math.max(1, area.width() / Math.max(1, samples));
        int visible = Math.min(samples, Math.max(1, (area.width() - 1) / step));
        int start = samples - visible;
        int previousX = Integer.MIN_VALUE;
        int previousY = Integer.MIN_VALUE;
        boolean rising = state.equitySample(samples - 1) >= state.equitySample(0);
        int colour = rising ? FxTheme.UP : FxTheme.DOWN;
        FxUi.pushClip(graphics, area);
        for (int index = start; index < samples; index++) {
            double sample = state.equitySample(index);
            int x = area.right() - (samples - index) * step;
            int y = (int) Math.round(bottom - (sample - low) / range * (bottom - top));
            FxUi.gradient(graphics, x, y, x + step, bottom, ARGB.multiplyAlpha(colour, 0.34F), ARGB.multiplyAlpha(colour, 0.0F));
            FxUi.fill(graphics, x, y, x + step, y + 1, colour);
            if (previousX != Integer.MIN_VALUE) {
                FxUi.fill(graphics, previousX, Math.min(previousY, y), x, Math.max(previousY, y) + 1, colour);
            }
            previousX = x + step;
            previousY = y;
        }
        FxUi.popClip(graphics);
        double first = state.equitySample(0);
        double last = state.equitySample(samples - 1);
        FxUi.right(graphics, font, area.right() - FxTheme.PAD, area.bottom() - 8,
                FxText.literal(FxUi.amount(last) + " " + state.basis().code()), colour);
        FxUi.text(graphics, font, area.left() + FxTheme.PAD, area.bottom() - 8,
                FxText.literal(FxUi.percent(first == 0.0D ? 0.0D : (last / first - 1.0D) * 100.0D)), FxTheme.MUTED);
        if (FxUi.hit(area, mouseX, mouseY)) {
            int hovered = Math.clamp(samples - (area.right() - mouseX) / step, start, samples - 1);
            int x = area.right() - (samples - hovered) * step;
            for (int line = top; line < area.bottom(); line += 4) {
                FxUi.fill(graphics, x, line, x + 1, Math.min(line + 2, area.bottom()), FxTheme.CROSSHAIR);
            }
            double sample = state.equitySample(hovered);
            Component text = FxText.literal(FxUi.amount(sample) + " " + state.basis().code() + "  "
                    + FxUi.percent(last == 0.0D ? 0.0D : (sample / last - 1.0D) * 100.0D));
            int tagX = Math.clamp(x + 3, area.left() + 1, Math.max(area.left() + 1, area.right() - font.width(text) - 5));
            FxControls.tag(graphics, font, FxUi.rect(tagX, area.top() + 1, font.width(text) + 4, 9), text, FxTheme.AXIS);
        }
    }
}
