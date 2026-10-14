package com.pppopipupu.fxeasy.trade;

import module java.base;

/// Every amount the ledger holds is an integer of 1e-6 item of one currency, which keeps it exact: a balance,
/// a margin, a profit or a price all round to this unit and a currency is only ever converted into another at
/// the rate the market quotes.
public final class Money {

    public static final long UNIT = 1_000_000L;

    private Money() {
    }

    public static long round(double units) {
        return Math.round(units * UNIT);
    }

    public static double units(long micro) {
        return micro / (double) UNIT;
    }
}
