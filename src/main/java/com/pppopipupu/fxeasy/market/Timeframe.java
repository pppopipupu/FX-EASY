package com.pppopipupu.fxeasy.market;

import module java.base;

/// Candle periods offered by the terminal. The server keeps [#MAX_CANDLES] closed candles per pair and
/// timeframe.
public enum Timeframe {
    M1("1m", 1200),
    M5("5m", 6000),
    M15("15m", 18000);

    public static final List<Timeframe> ALL = List.of(values());
    public static final int MAX_CANDLES = 512;

    private final String label;
    private final int ticks;

    Timeframe(String label, int ticks) {
        this.label = label;
        this.ticks = ticks;
    }

    public String label() {
        return this.label;
    }

    public int ticks() {
        return this.ticks;
    }

    public static Timeframe byIndex(int index) {
        return ALL.get(index);
    }
}
