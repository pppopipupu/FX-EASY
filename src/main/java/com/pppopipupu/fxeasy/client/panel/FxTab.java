package com.pppopipupu.fxeasy.client.panel;

import module java.base;

/// One page of the terminal, in the order the control row draws its cells.
public enum FxTab {

    MARKET("fxeasy.panel.tab.market"),
    TRADE("fxeasy.panel.tab.trade"),
    POSITIONS("fxeasy.panel.tab.positions"),
    ACCOUNT("fxeasy.panel.tab.account"),
    SHOP("fxeasy.panel.tab.shop");

    private final String key;

    FxTab(String key) {
        this.key = key;
    }

    /// Translation key of the label of this page.
    public String key() {
        return this.key;
    }

    /// Whether this page draws the chart column and the side column under it, which is every page but the two
    /// that take the whole panel themselves: the market list and the shop.
    public boolean chart() {
        return this != MARKET && this != SHOP;
    }
}
