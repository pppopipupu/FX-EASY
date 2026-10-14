package com.pppopipupu.fxeasy.client.panel;

import module java.base;

import net.minecraft.util.ARGB;

/// Terminal palette and metrics. Colors are packed ARGB, which is what the vanilla GUI element API takes.
public final class FxTheme {

    public static final int UP = ARGB.color(255, 77, 194, 138);
    public static final int DOWN = ARGB.color(255, 232, 87, 82);
    public static final int ACCENT = ARGB.color(255, 255, 196, 87);
    public static final int MA_FAST = ARGB.color(255, 255, 196, 87);
    public static final int MA_SLOW = ARGB.color(255, 186, 142, 255);
    public static final int TEXT = ARGB.color(255, 230, 234, 240);
    public static final int MUTED = ARGB.color(255, 154, 164, 176);
    public static final int AXIS = ARGB.color(219, 178, 188, 199);
    public static final int GRID = ARGB.color(36, 255, 255, 255);
    public static final int CROSSHAIR = ARGB.color(150, 227, 232, 240);
    public static final int CARD = ARGB.color(232, 14, 18, 22);
    public static final int CARD_BORDER = ARGB.color(48, 255, 255, 255);
    public static final int CHART_BG = ARGB.color(255, 18, 23, 28);
    public static final int TOOLTIP = ARGB.color(244, 10, 13, 17);
    public static final int BUTTON = ARGB.color(40, 255, 255, 255);
    public static final int BUTTON_HOVER = ARGB.color(64, 255, 255, 255);
    public static final int BUTTON_DISABLED = ARGB.color(18, 255, 255, 255);
    public static final int SELECTED = ARGB.color(56, 255, 196, 87);
    public static final int SLIDER_TRACK = ARGB.color(56, 255, 255, 255);
    public static final int SLIDER_FILL = ARGB.color(150, 255, 196, 87);
    public static final int SCROLL = ARGB.color(72, 255, 255, 255);
    public static final int SHADE = ARGB.color(96, 0, 0, 0);

    public static final int PAD = 3;
    public static final int GAP = 2;
    public static final int ROW = 11;
    public static final int BAR = 20;
    public static final int TAB = 13;
    public static final int WICK = 1;

    private FxTheme() {
    }
}
