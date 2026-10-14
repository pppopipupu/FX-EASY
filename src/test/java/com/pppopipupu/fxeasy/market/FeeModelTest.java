package com.pppopipupu.fxeasy.market;

import module java.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FeeModelTest {

    private static final Currency DIRT = Currencies.byId("dirt");
    private static final Currency GOLD = Currencies.byId("gold");
    private static final Currency NETHERITE = Currencies.byId("netherite");
    private static final CurrencyPair DIRT_GOLD = CurrencyPair.of(DIRT, GOLD);

    @Test
    void spreadAndSlippageAlwaysHurtTheTaker() {
        Rng rng = new Rng(99L);
        double mid = 0.5D;
        double regime = 3.0D;
        double halfSpread = FeeModel.halfSpread(regime, FeeDiscount.NONE);
        int slipped = 0;
        for (int i = 0; i < 20_000; i++) {
            FeeModel.Fill buy = FeeModel.fill(Side.BUY, mid, regime, rng, FeeDiscount.NONE);
            FeeModel.Fill sell = FeeModel.fill(Side.SELL, mid, regime, rng, FeeDiscount.NONE);
            assertTrue(buy.price() >= mid * (1.0D + halfSpread));
            assertTrue(sell.price() <= mid * (1.0D - halfSpread));
            assertTrue(buy.price() <= mid * (1.0D + halfSpread)
                    * (1.0D + FeeModel.slippageMagnitude(regime, FeeDiscount.NONE)));
            if (buy.slipped()) {
                slipped++;
            }
        }
        double chance = slipped / 20_000.0D;
        assertTrue(chance > 0.27D && chance < 0.33D, "slippage chance at regime 3 was " + chance);
    }

    @Test
    void calmMarketUsesTheRetailBaseline() {
        assertEquals(0.0001D, FeeModel.halfSpread(1.0D, FeeDiscount.NONE), 1.0E-12D);
        assertEquals(0.10D, FeeModel.slippageChance(1.0D), 1.0E-12D);
        assertEquals(0.0005D, FeeModel.slippageMagnitude(1.0D, FeeDiscount.NONE), 1.0E-12D);
        assertEquals(0.0002D, FeeModel.halfSpread(2.0D, FeeDiscount.NONE), 1.0E-12D);
        assertEquals(0.90D, FeeModel.slippageChance(100.0D), 1.0E-12D);
        assertEquals(0.001D, FeeModel.CONVERSION_SPREAD, 1.0E-12D);
    }

    @Test
    void swapIsTheInterestDifferenceWidenedByTheMarkup() {
        double difference = (DIRT.annualRate() - GOLD.annualRate()) / MarketEngine.DAYS_PER_YEAR;
        assertEquals(-difference * (1.0D + FeeModel.SWAP_MARKUP),
                FeeModel.swapPerDay(DIRT_GOLD, Side.BUY, FeeDiscount.NONE), 1.0E-12D);
        assertEquals(difference * (1.0D - FeeModel.SWAP_MARKUP),
                FeeModel.swapPerDay(DIRT_GOLD, Side.SELL, FeeDiscount.NONE), 1.0E-12D);
    }

    /// The carry the platform pays or takes is the interest difference, never the volatility regime: widening it
    /// would break the identity between the drift of a pair and the swap, and would make a nervous market pay a
    /// bigger overnight rate than the currencies themselves do.
    @Test
    void swapIsNotScaledByVolatility() {
        for (CurrencyPair pair : Currencies.pairs()) {
            for (Side side : Side.values()) {
                double difference = (pair.quote().annualRate() - pair.base().annualRate()) / MarketEngine.DAYS_PER_YEAR;
                double cost = side == Side.BUY ? difference : -difference;
                double expected = cost * (cost > 0.0D ? 1.0D + FeeModel.SWAP_MARKUP : 1.0D - FeeModel.SWAP_MARKUP);
                assertEquals(expected, FeeModel.swapPerDay(pair, side, FeeDiscount.NONE), 1.0E-15D,
                        pair.code() + " " + side);
            }
        }
    }

    /// A position is long the base of its pair and short the quote, so it is paid for holding the higher yielding
    /// currency of the two and pays for holding the lower one, and the platform keeps its markup on both sides.
    @Test
    void theHigherYieldingSideOfAPairCollectsTheSwap() {
        CurrencyPair pair = CurrencyPair.of(DIRT, NETHERITE);
        assertTrue(FeeModel.swapPerDay(pair, Side.SELL, FeeDiscount.NONE) < 0.0D,
                "short dirt is long the netherite rate");
        assertTrue(FeeModel.swapPerDay(pair, Side.BUY, FeeDiscount.NONE) > 0.0D, "long dirt pays the netherite rate");
        assertTrue(-FeeModel.swapPerDay(pair, Side.SELL, FeeDiscount.NONE)
                < FeeModel.swapPerDay(pair, Side.BUY, FeeDiscount.NONE),
                "the platform pays less than it takes: that difference is the swap spread");
    }

    /// The price process drifts by the interest difference, so a carried position takes back in price what the
    /// swap pays it and only the markup is a certain cost.
    @Test
    void swapCancelsTheDriftOfThePair() {
        MarketEngine market = new MarketEngine(1L);
        for (CurrencyPair pair : Currencies.pairs()) {
            double difference = (pair.base().annualRate() - pair.quote().annualRate()) / MarketEngine.DAYS_PER_YEAR;
            assertEquals(-difference, market.pairDrift(pair), 1.0E-12D, "the higher yielding currency drifts down");
            double cost = -difference;
            double expected = cost * (cost > 0.0D ? 1.0D + FeeModel.SWAP_MARKUP : 1.0D - FeeModel.SWAP_MARKUP);
            assertEquals(expected, FeeModel.swapPerDay(pair, Side.BUY, FeeDiscount.NONE), 1.0E-12D);
        }
    }

    @Test
    void haircutFollowsTheVolatilityOfTheCollateral() {
        assertEquals(FeeModel.HAIRCUT_SIGMA_MULTIPLE * DIRT.dailySigma(), FeeModel.haircut(DIRT), 1.0E-12D);
        assertEquals(FeeModel.HAIRCUT_SIGMA_MULTIPLE * NETHERITE.dailySigma(), FeeModel.haircut(NETHERITE), 1.0E-12D);
        assertTrue(FeeModel.haircut(NETHERITE) > FeeModel.haircut(DIRT));
        for (Currency currency : Currencies.all()) {
            assertTrue(FeeModel.haircut(currency) < FeeModel.MAX_HAIRCUT);
        }
    }

    /// The settlement currency of a pair backs it at par, whatever it is worth; any other currency is discounted
    /// by its own haircut and by the spread the platform charges for turning it into the settlement currency.
    @Test
    void collateralRateOnlyDiscountsWhatThePairDoesNotSettleIn() {
        double rateToQuote = 512.0D;
        assertEquals(rateToQuote, FeeModel.collateralRate(DIRT_GOLD, GOLD, rateToQuote, FeeDiscount.NONE), 1.0E-12D);
        double dirt = FeeModel.collateralRate(DIRT_GOLD, DIRT, rateToQuote, FeeDiscount.NONE);
        assertEquals(rateToQuote * (1.0D - FeeModel.haircut(DIRT))
                / (1.0D + FeeModel.halfConversionSpread(FeeDiscount.NONE)), dirt, 1.0E-12D);
        assertTrue(dirt < rateToQuote);
    }

    /// Converting a profit or a loss out of the currency the pair settles in costs the conversion spread on the
    /// side that hurts the trader; converting into the settlement currency itself costs nothing.
    @Test
    void conversionChargesTheSpreadOnTheSideThatCostsTheTrader() {
        double half = FeeModel.halfConversionSpread(FeeDiscount.NONE);
        double rateToQuote = 512.0D;
        double exact = 5.0D / rateToQuote;
        assertEquals(exact * (1.0D - half),
                FeeModel.toItems(5.0D, DIRT_GOLD, DIRT, rateToQuote, FeeDiscount.NONE), 1.0E-12D);
        assertEquals(-exact * (1.0D + half),
                FeeModel.toItems(-5.0D, DIRT_GOLD, DIRT, rateToQuote, FeeDiscount.NONE), 1.0E-12D);
        assertTrue(FeeModel.toItems(5.0D, DIRT_GOLD, DIRT, rateToQuote, FeeDiscount.NONE) < exact,
                "a profit converts down");
        assertTrue(FeeModel.toItems(-5.0D, DIRT_GOLD, DIRT, rateToQuote, FeeDiscount.NONE) < -exact,
                "a loss converts up in size");
        assertEquals(5.0D, FeeModel.toItems(5.0D, DIRT_GOLD, GOLD, 1.0D, FeeDiscount.NONE), 0.0D);
        assertEquals(-5.0D, FeeModel.toItems(-5.0D, DIRT_GOLD, GOLD, 1.0D, FeeDiscount.NONE), 0.0D);
    }

    /// A subscription scales what the trader pays and never what they are paid: the spread, the slippage and the
    /// conversion come down, while a carry that is a credit to the position stays whole, because taking a share
    /// of a credit would be charging a trader for being paid. The conversion is still charged on the side that
    /// costs the trader, only less of it, and the haircut is untouched, because it is what the platform will
    /// lend against rather than what it charges.
    @Test
    void aDiscountScalesWhatTheTraderPaysAndNotWhatTheyArePaid() {
        FeeDiscount half = new FeeDiscount(0.5D, 0.5D, 0.5D, 0.5D);
        assertEquals(FeeModel.halfSpread(1.0D, FeeDiscount.NONE) * 0.5D, FeeModel.halfSpread(1.0D, half), 1.0E-12D);
        assertEquals(FeeModel.slippageMagnitude(1.0D, FeeDiscount.NONE) * 0.5D,
                FeeModel.slippageMagnitude(1.0D, half), 1.0E-12D);
        assertEquals(FeeModel.CONVERSION_SPREAD * 0.5D, FeeModel.conversionSpread(half), 1.0E-12D);
        assertEquals(FeeModel.haircut(DIRT), FeeModel.haircut(DIRT), 1.0E-12D, "a haircut is not a fee");
        for (CurrencyPair pair : Currencies.pairs()) {
            for (Side side : Side.values()) {
                double retail = FeeModel.swapPerDay(pair, side, FeeDiscount.NONE);
                double expected = retail < 0.0D ? retail : retail * 0.5D;
                assertEquals(expected, FeeModel.swapPerDay(pair, side, half), 1.0E-15D, pair.code() + " " + side);
            }
        }
        double rateToQuote = 512.0D;
        double halfConversion = FeeModel.halfConversionSpread(half);
        double exact = 5.0D / rateToQuote;
        assertEquals(exact * (1.0D - halfConversion),
                FeeModel.toItems(5.0D, DIRT_GOLD, DIRT, rateToQuote, half), 1.0E-12D);
        assertEquals(-exact * (1.0D + halfConversion),
                FeeModel.toItems(-5.0D, DIRT_GOLD, DIRT, rateToQuote, half), 1.0E-12D);
    }
}
