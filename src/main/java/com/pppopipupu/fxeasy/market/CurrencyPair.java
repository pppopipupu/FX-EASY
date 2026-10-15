package com.pppopipupu.fxeasy.market;

import module java.base;

/// A tradable pair of two currencies of the world's set. Its price is the exchange rate between the two, items of
/// the quote per item of the base, so buying a pair is long the base currency and short the quote. The pairs of
/// a set are every unordered combination of its currencies, in the order the set names them.
public record CurrencyPair(Currency base, Currency quote, int index) {

    private static final String SEPARATOR = "/";

    /// Pair of two currencies, or null when the world trades no such pair. A set of n currencies has n(n-1)/2
    /// pairs, so this is the only way a pair is looked up by what it holds rather than by its index.
    public static CurrencyPair of(Currency base, Currency quote) {
        for (CurrencyPair pair : Currencies.pairs()) {
            if (pair.base == base && pair.quote == quote) {
                return pair;
            }
        }
        return null;
    }

    /// Pair with that id, e.g. `dirt_gold`, which is how a save names it, or null when the world trades no such
    /// pair.
    public static CurrencyPair byId(String id) {
        for (CurrencyPair pair : Currencies.pairs()) {
            if (pair.id().equals(id)) {
                return pair;
            }
        }
        return null;
    }

    public String code() {
        return this.base.code() + SEPARATOR + this.quote.code();
    }

    public String id() {
        return this.base.id() + "_" + this.quote.id();
    }

    public double mid(MarketEngine market) {
        return market.rate(this.base, this.quote);
    }
}
