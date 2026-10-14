package com.pppopipupu.fxeasy.market;

import module java.base;

/// Direction of a trade in the base currency of a pair.
public enum Side {
    BUY,
    SELL;

    public Side opposite() {
        return this == BUY ? SELL : BUY;
    }
}
