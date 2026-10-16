package com.pppopipupu.fxeasy.market;

import module java.base;

/// Trading costs of the platform (久留美集团). The spread, the slippage and the overnight swap all scale with the
/// volatility regime of the pair, so a crashing currency trades wider and is dearer to hold. The swap itself is
/// the interest difference of the two currencies of a pair - the platform pays it to a position that holds the
/// higher yielding currency and takes it from one that holds the lower - and everything the platform keeps on
/// top of a mid rate is a markup the trader pays whichever way the money moves. The one cost that is not the
/// same on both sides of the book is the spread of the order that opens a position: the side that sells a pair
/// pays [SHORT_OPEN_SPREAD_MULTIPLE] times the spread of the side that buys it, so a short is dearer to open
/// than a long.
///
/// A [FeeDiscount] scales every cost the trader pays: a subscription of the terminal buys part of the spread,
/// of the slippage, of the swap and of the conversion spread down. What the platform pays a position is never
/// scaled - a discount that ate into a credit would be no discount - and neither is the haircut, which is what
/// the platform will lend against rather than what it charges.
public final class FeeModel {

    /// Total bid/ask spread of a calm market as a fraction of the mid price.
    public static final double BASE_SPREAD = 0.0002D;
    /// Multiple of the half spread a position pays to open on the side that sells. The platform charges the
    /// short side of a pair more than the long side for the same liquidity; the order that closes a position is
    /// the plain [fill] of the opposite side, so the surcharge is paid once per position and never on the way
    /// out.
    public static final double SHORT_OPEN_SPREAD_MULTIPLE = 1.5D;
    /// Share of the interest difference the platform keeps on each side of an overnight swap.
    public static final double SWAP_MARKUP = 0.25D;
    /// Total spread of the platform's currency conversion around the mid rate.
    public static final double CONVERSION_SPREAD = 0.001D;
    /// Haircut of a collateral currency as a multiple of its daily volatility, and the most the platform ever
    /// takes off it. A currency that swings is worth less as collateral than one that does not.
    public static final double HAIRCUT_SIGMA_MULTIPLE = 4.0D;
    public static final double MAX_HAIRCUT = 0.20D;
    public static final double BASE_SLIPPAGE_CHANCE = 0.10D;
    /// Adverse fill offset when a market order slips, as a fraction of the mid price.
    public static final double BASE_SLIPPAGE = 0.0005D;
    private static final double MAX_SLIPPAGE_CHANCE = 0.90D;

    private FeeModel() {
    }

    public static double halfSpread(double regime, FeeDiscount discount) {
        return BASE_SPREAD * 0.5D * regime * discount.spreadScale();
    }

    /// Half the spread an order that opens a position on `side` pays: the spread of the pair, times
    /// [SHORT_OPEN_SPREAD_MULTIPLE] on the side that sells. The platform fills an order with this and the panel
    /// quotes one with it, so what a ticket shows for the side it is on is what the fill charges.
    public static double openHalfSpread(Side side, double regime, FeeDiscount discount) {
        return halfSpread(regime, discount) * (side == Side.SELL ? SHORT_OPEN_SPREAD_MULTIPLE : 1.0D);
    }

    /// Total conversion spread the platform charges around a mid rate under one plan, which is what the panel
    /// prints next to the haircut.
    public static double conversionSpread(FeeDiscount discount) {
        return CONVERSION_SPREAD * discount.conversionScale();
    }

    /// Fraction of the notional one market day of swap costs, positive when the position pays the platform and
    /// negative when the platform pays the position. A position holds the base currency of its pair and owes the
    /// quote one, so it pays the interest difference while the base yields less than the quote; the platform
    /// keeps [SWAP_MARKUP] of the difference on both sides, which is a spread around the difference.
    ///
    /// The interest difference is what the platform actually owes or is owed, so it is never scaled by the
    /// volatility regime: widening it would break the identity between the drift of a pair and the carry a
    /// position pays. Event risk shows up in the spread and in the fill, which is where a trader can see it. A
    /// [FeeDiscount] takes part of the markup out of what the position pays and leaves a credit whole.
    public static double swapPerDay(CurrencyPair pair, Side side, FeeDiscount discount) {
        double difference = (pair.base().annualRate() - pair.quote().annualRate()) / MarketEngine.DAYS_PER_YEAR;
        double cost = side == Side.BUY ? -difference : difference;
        if (cost > 0.0D) {
            return cost * (1.0D + SWAP_MARKUP) * discount.swapScale();
        }
        return cost * (1.0D - SWAP_MARKUP);
    }

    /// Haircut the platform applies to a currency used as collateral: it takes a multiple of the currency's
    /// daily volatility off its value, so a volatile currency backs less notional than dirt does.
    public static double haircut(Currency currency) {
        return Math.clamp(HAIRCUT_SIGMA_MULTIPLE * currency.dailySigma(), 0.0D, MAX_HAIRCUT);
    }

    public static double halfConversionSpread(FeeDiscount discount) {
        return conversionSpread(discount) * 0.5D;
    }

    /// Quote items one item of `currency` backs a trade on `pair` with, at the live rate the platform converts
    /// it at: `rateToQuote` is the price of the collateral in the currency the pair settles in, and anything but
    /// that settlement currency is discounted by its haircut and by the spread the platform charges for turning
    /// it into the settlement currency. The settlement currency itself converts at par.
    public static double collateralRate(CurrencyPair pair, Currency currency, double rateToQuote, FeeDiscount discount) {
        if (currency == pair.quote()) {
            return rateToQuote;
        }
        return rateToQuote * (1.0D - haircut(currency)) / (1.0D + halfConversionSpread(discount));
    }

    /// Converts a signed amount of the currency a pair settles in into items of `marginCurrency`, at the live
    /// rate the platform sells that currency for: the rate is widened by the conversion spread on the side that
    /// costs the trader - a profit converts down, a loss converts up - and converting into the currency the
    /// trade itself settles in costs nothing.
    public static double toItems(double quoteItems, CurrencyPair pair, Currency marginCurrency, double rateToQuote,
            FeeDiscount discount) {
        if (marginCurrency == pair.quote()) {
            return quoteItems;
        }
        double halfSpread = halfConversionSpread(discount);
        double items = quoteItems / rateToQuote;
        return items * (quoteItems >= 0.0D ? 1.0D - halfSpread : 1.0D + halfSpread);
    }

    public static double slippageChance(double regime) {
        return Math.min(MAX_SLIPPAGE_CHANCE, BASE_SLIPPAGE_CHANCE * regime);
    }

    public static double slippageMagnitude(double regime, FeeDiscount discount) {
        return BASE_SLIPPAGE * regime * discount.slippageScale();
    }

    /// Executes an order that opens a position against the mid price, at the spread of the side that trades it,
    /// which is the wider one when that side is the short side.
    public static Fill openFill(Side side, double mid, double regime, Rng rng, FeeDiscount discount) {
        return fill(side, mid, regime, rng, discount, openHalfSpread(side, regime, discount));
    }

    /// Executes a market order against the mid price: the spread is paid first, then the order may slip
    /// further into the unfavourable direction. This is the fill of an order that closes a position, which pays
    /// the spread of the side it trades and not the surcharge of opening one.
    public static Fill fill(Side side, double mid, double regime, Rng rng, FeeDiscount discount) {
        return fill(side, mid, regime, rng, discount, halfSpread(regime, discount));
    }

    private static Fill fill(Side side, double mid, double regime, Rng rng, FeeDiscount discount,
            double halfSpread) {
        double slipped = 0.0D;
        boolean slippedFlag = rng.nextChance(slippageChance(regime));
        if (slippedFlag) {
            slipped = slippageMagnitude(regime, discount);
        }
        double price = switch (side) {
            case BUY -> mid * (1.0D + halfSpread) * (1.0D + slipped);
            case SELL -> mid * (1.0D - halfSpread) * (1.0D - slipped);
        };
        return new Fill(price, halfSpread, slippedFlag, slipped);
    }

    public record Fill(double price, double halfSpread, boolean slipped, double slippage) {
    }
}
