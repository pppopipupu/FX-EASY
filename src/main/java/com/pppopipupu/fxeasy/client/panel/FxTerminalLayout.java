package com.pppopipupu.fxeasy.client.panel;

import module java.base;

import net.minecraft.client.gui.navigation.ScreenRectangle;

/// Where the parts of the terminal stand in design units: the header, the control row under it, and either the
/// full width market list or the chart column next to the side column. The chart keeps at least 45% of the width
/// and gives the price axis the room the widest price it prints needs, the side column gets whatever is left
/// inside its own bounds, and the page in the side column draws above the metrics strip every page keeps in
/// view. The frame and a scripted click read the same rectangles, so a run aims at the cells the frame draws.
public record FxTerminalLayout(ScreenRectangle header, ScreenRectangle controls, ScreenRectangle list,
        ScreenRectangle side, ScreenRectangle plot, ScreenRectangle priceAxis, ScreenRectangle timeAxis) {

    private static final int SIDE_MIN = 132;
    private static final int SIDE_MAX = 300;
    private static final int INFO_LINES = 7;
    private static final int INFO_LINE = 10;

    /// Layout of a frame of [designWidth] by [designHeight] design units, whose price axis is [axisWidth] wide.
    public static FxTerminalLayout of(int designWidth, int designHeight, int axisWidth) {
        ScreenRectangle header = FxUi.rect(0, 0, designWidth, FxTheme.BAR);
        ScreenRectangle controls = FxUi.rect(0, FxTheme.BAR, designWidth, FxTheme.TAB + 1);
        int top = FxTheme.BAR + FxTheme.TAB + 2;
        int height = designHeight - top;
        ScreenRectangle list = FxUi.rect(0, top, designWidth, height);
        int sideWidth = Math.clamp(Math.round(designWidth * 0.36F), SIDE_MIN, SIDE_MAX);
        int sideLeft = designWidth - sideWidth;
        ScreenRectangle side = FxUi.rect(sideLeft, top, sideWidth, height);
        ScreenRectangle card = FxUi.rect(0, top, sideLeft, height);
        ScreenRectangle plot = FxUi.rect(card.left() + 2, card.top() + 12, card.width() - axisWidth - 5,
                card.height() - 25);
        ScreenRectangle priceAxis = FxUi.rect(plot.right() + 2, plot.top(), axisWidth, plot.height());
        ScreenRectangle timeAxis = FxUi.rect(plot.left(), plot.bottom() + 1, plot.width(), 10);
        return new FxTerminalLayout(header, controls, list, side, plot, priceAxis, timeAxis);
    }

    /// The area the page in the side column draws in: everything above the metrics strip. Both the draw and a
    /// scripted click read it, so a run aims at the cells the page really draws.
    public ScreenRectangle sideInner() {
        ScreenRectangle strip = sideInfoArea();
        return FxUi.rect(strip.left(), this.side.top() + FxTheme.PAD, strip.width(),
                strip.top() - FxTheme.GAP - this.side.top() - FxTheme.PAD);
    }

    /// The metrics strip at the bottom of the side column, which every page keeps in view.
    public ScreenRectangle sideInfoArea() {
        int strip = INFO_LINES * INFO_LINE;
        int bottom = this.side.bottom() - FxTheme.PAD - strip;
        return FxUi.rect(this.side.left() + FxTheme.PAD, bottom, this.side.width() - FxTheme.PAD * 2, strip);
    }
}
