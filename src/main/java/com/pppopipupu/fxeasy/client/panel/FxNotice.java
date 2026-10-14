package com.pppopipupu.fxeasy.client.panel;

import module java.base;

import com.pppopipupu.fxeasy.client.FxClientState;
import com.pppopipupu.fxeasy.client.FxText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

/// The bar along the bottom of the panel: the platform is offline until the first market tick arrives, and a
/// notice the server sent - a rejection or the welcome capital - is drawn as a card over it for the ticks the
/// mirror keeps it.
public final class FxNotice {

    private static final int BAR = 12;

    private FxNotice() {
    }

    public static void draw(GuiGraphicsExtractor graphics, Font font, FxClientState state, int designWidth,
            int designHeight) {
        if (!state.connected()) {
            FxUi.fill(graphics, 0, designHeight - BAR, designWidth, designHeight, FxTheme.SHADE);
            FxUi.text(graphics, font, FxTheme.PAD, designHeight - BAR + 2, FxText.get("fxeasy.panel.offline"),
                    FxTheme.DOWN);
            return;
        }
        if (!state.noticeVisible()) {
            return;
        }
        Component text = state.noticeComponent();
        int width = Math.min(designWidth - 8, font.width(text) + 8);
        int colour = state.noticeError() ? FxTheme.DOWN : FxTheme.ACCENT;
        ScreenRectangle bar = FxUi.rect(4, designHeight - BAR - 2, width, BAR);
        FxUi.fill(graphics, bar.left(), bar.top(), bar.right(), bar.bottom(), FxTheme.TOOLTIP);
        FxUi.border(graphics, bar, ARGB.multiplyAlpha(colour, 0.8F));
        FxUi.text(graphics, font, bar.left() + 4, bar.top() + 2, text, colour);
    }
}
