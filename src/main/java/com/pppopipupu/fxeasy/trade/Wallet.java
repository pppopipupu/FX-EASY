package com.pppopipupu.fxeasy.trade;

import module java.base;

import com.pppopipupu.fxeasy.market.Currency;

/// The item side of a deposit or a withdrawal. Implemented over a player inventory on the server and over a
/// map in tests.
public interface Wallet {

    long count(Currency currency);

    boolean remove(Currency currency, long units);

    /// Adds up to `units` items and returns how many actually fitted into the wallet.
    long add(Currency currency, long units);
}
