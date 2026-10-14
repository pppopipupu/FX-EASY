package com.pppopipupu.fxeasy.market;

import module java.base;

/// Fractions off the costs the platform charges a trader, one per kind of cost: the spread of a fill, the
/// slippage that may widen it, the swap of an overnight position and the spread of a currency conversion. A
/// discount scales what the trader pays and never the carry the platform owes them, so a position that is
/// credited keeps its whole credit. [NONE] is retail pricing, which is what every account starts on, and a
/// discount never reaches the haircut, which is what the platform will lend against rather than what it
/// charges.
public record FeeDiscount(double spread, double slippage, double swap, double conversion) {

    public static final FeeDiscount NONE = new FeeDiscount(0.0D, 0.0D, 0.0D, 0.0D);

    public FeeDiscount {
        spread = Math.clamp(spread, 0.0D, 1.0D);
        slippage = Math.clamp(slippage, 0.0D, 1.0D);
        swap = Math.clamp(swap, 0.0D, 1.0D);
        conversion = Math.clamp(conversion, 0.0D, 1.0D);
    }

    /// Fraction of the spread a plan still charges.
    public double spreadScale() {
        return 1.0D - this.spread;
    }

    /// Fraction of the slippage magnitude a plan still charges. The chance of a slip is market reality and is
    /// never discounted, only how far a slipped fill moves.
    public double slippageScale() {
        return 1.0D - this.slippage;
    }

    /// Fraction of what a position pays for its carry a plan still charges; a credit is never scaled.
    public double swapScale() {
        return 1.0D - this.swap;
    }

    /// Fraction of the conversion spread a plan still charges.
    public double conversionScale() {
        return 1.0D - this.conversion;
    }
}
