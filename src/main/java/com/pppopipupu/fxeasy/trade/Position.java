package com.pppopipupu.fxeasy.trade;

import module java.base;

import com.pppopipupu.fxeasy.market.Currency;
import com.pppopipupu.fxeasy.market.CurrencyPair;
import com.pppopipupu.fxeasy.market.FeeDiscount;
import com.pppopipupu.fxeasy.market.FeeModel;
import com.pppopipupu.fxeasy.market.MarketEngine;
import com.pppopipupu.fxeasy.market.Side;

/// One open leveraged position. `units` is the size the order named, counted in items of the base currency of
/// the pair, and `baseUnits` the base amount the position really holds, which is the same number for every
/// position opened by this build: a save written before the size was counted in items keeps the base amount it
/// was opened with, so its notional, its profit and its exposure do not change on load. The margin is locked in
/// the currency the order named and every amount of the position - the locked margin, the unrealised profit and
/// the swap - is kept in that currency.
public final class Position {

    private final long id;
    private final CurrencyPair pair;
    private final Side side;
    private final Currency marginCurrency;
    private final int units;
    private final int leverage;
    private final double openPrice;
    private final double baseUnits;
    private final double signedBaseUnits;
    private final long marginMicro;
    private final long openMarketTick;
    private long swapMicro;

    public Position(long id, CurrencyPair pair, Side side, Currency marginCurrency, int units, int leverage, double openPrice,
            double baseUnits, long marginMicro, long openMarketTick) {
        this.id = id;
        this.pair = pair;
        this.side = side;
        this.marginCurrency = marginCurrency;
        this.units = units;
        this.leverage = leverage;
        this.openPrice = openPrice;
        this.baseUnits = baseUnits;
        this.signedBaseUnits = side == Side.BUY ? baseUnits : -baseUnits;
        this.marginMicro = marginMicro;
        this.openMarketTick = openMarketTick;
    }

    public long id() {
        return this.id;
    }

    public CurrencyPair pair() {
        return this.pair;
    }

    public Side side() {
        return this.side;
    }

    /// Currency the margin is locked from, which is also the currency the position settles in.
    public Currency marginCurrency() {
        return this.marginCurrency;
    }

    /// Size of the position in items of the base currency of its pair, which is what the ticket asked for.
    public int units() {
        return this.units;
    }

    public int leverage() {
        return this.leverage;
    }

    public double openPrice() {
        return this.openPrice;
    }

    /// Units of the base currency the position holds, always positive: this is the size the notional and the
    /// carry are measured from, while the side decides which way the exposure and the profit point.
    public double baseUnits() {
        return this.baseUnits;
    }

    /// Units of the base currency the position is long, negative on a short. A sale is short the base and long
    /// the quote, so exposure and the profit mark both follow the sign of the side.
    public double signedBaseUnits() {
        return this.signedBaseUnits;
    }

    /// Margin locked from the margin currency balance, in micro units of that currency.
    public long marginMicro() {
        return this.marginMicro;
    }

    public long openMarketTick() {
        return this.openMarketTick;
    }

    /// Swap already charged to the account for this position, in micro units of the margin currency.
    public long swapMicro() {
        return this.swapMicro;
    }

    public void addSwap(long micro) {
        this.swapMicro += micro;
    }

    /// Price the position would be closed at right now, i.e. at the unfavourable side of the spread, under a
    /// plan: the spread the account trades on is the spread the closing price is marked at, so a subscription
    /// shows up in the equity of every open position.
    public double closingPrice(MarketEngine market, FeeDiscount discount) {
        double halfSpread = FeeModel.halfSpread(market.regime(this.pair), discount);
        double mid = market.price(this.pair);
        return this.side == Side.BUY
                ? mid * (1.0D - halfSpread)
                : mid * (1.0D + halfSpread);
    }

    /// Unrealised profit in items of the currency the pair settles in, marked at the closing price.
    public double unrealizedQuoteItems(MarketEngine market, FeeDiscount discount) {
        double delta = closingPrice(market, discount) - this.openPrice;
        return this.signedBaseUnits * delta;
    }

    /// Unrealised profit in micro units of the margin currency, converted at the live rate.
    public long unrealizedMarginMicro(MarketEngine market, FeeDiscount discount) {
        return Money.round(FeeModel.toItems(unrealizedQuoteItems(market, discount), this.pair, this.marginCurrency,
                market.rate(this.marginCurrency, this.pair.quote()), discount));
    }

    /// Signed exposure of this position in items of one currency: positive when the position is long that
    /// currency and negative when it is short it. A position is long the base of its pair and short the quote,
    /// and the margin currency is where it settles rather than what it holds.
    public double exposureItems(Currency currency, MarketEngine market) {
        double base = this.signedBaseUnits;
        if (currency == this.pair.base()) {
            return base;
        }
        if (currency == this.pair.quote()) {
            return -base * market.price(this.pair);
        }
        return 0.0D;
    }
}
