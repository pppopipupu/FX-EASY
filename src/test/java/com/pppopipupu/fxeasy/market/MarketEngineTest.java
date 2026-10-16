package com.pppopipupu.fxeasy.market;

import module java.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MarketEngineTest {

    private static final Currency DIRT = Currencies.byId("dirt");
    private static final Currency GOLD = Currencies.byId("gold");
    private static final Currency DIAMOND = Currencies.byId("diamond");
    private static final Currency NETHERITE = Currencies.byId("netherite");
    private static final CurrencyPair DIRT_GOLD = CurrencyPair.of(DIRT, GOLD);

    private static double[] noChange() {
        return new double[Currencies.count()];
    }

    /// The definitions open the world on their own ratios, which is the only thing an absolute price scale means
    /// here: a rate is one initial price over the other.
    @Test
    void startsAtTheGenesisRatios() {
        MarketEngine market = new MarketEngine(1L);
        assertEquals(GOLD.initialPrice() / DIRT.initialPrice(), market.rate(GOLD, DIRT), 1.0E-9D);
        assertEquals(DIRT.initialPrice() / GOLD.initialPrice(), market.price(DIRT_GOLD), 1.0E-12D);
        assertEquals(DIRT, DIRT_GOLD.base());
        assertEquals(GOLD, DIRT_GOLD.quote());
        assertEquals(Currencies.count() * (Currencies.count() - 1) / 2, Currencies.pairs().size());
    }

    /// A rate is the ratio of two levels, so chaining two of them is the direct one and a currency is worth one
    /// of itself at any time.
    @Test
    void crossRatesStayConsistent() {
        MarketEngine market = new MarketEngine(7L);
        for (int tick = 0; tick < 5_000; tick++) {
            market.advance(tick, noChange(), MarketDriverFrame.calm());
        }
        double viaDiamond = market.rate(DIRT, DIAMOND) * market.rate(DIAMOND, NETHERITE);
        assertNotEquals(DIRT, DIAMOND);
        assertNotEquals(DIAMOND, NETHERITE);
        assertEquals(market.rate(DIRT, NETHERITE), viaDiamond, Math.abs(viaDiamond) * 1.0E-12D);
        assertEquals(1.0D, market.rate(DIRT, DIRT), 0.0D);
    }

    @Test
    void levelsStayInsideTheDeviationClamp() {
        MarketEngine market = new MarketEngine(11L);
        for (int tick = 0; tick < 200_000; tick++) {
            market.advance(tick, noChange(), MarketDriverFrame.calm());
        }
        for (Currency currency : Currencies.all()) {
            double deviation = market.logPrice(currency) - Math.log(currency.initialPrice());
            assertTrue(Math.abs(deviation) <= MarketEngine.MAX_LOG_DEVIATION + 1.0E-9D, currency.name());
        }
    }

    @Test
    void regimeStaysWithinItsBoundsAndReactsToVolatility() {
        MarketEngine market = new MarketEngine(13L);
        CurrencyPair pair = DIRT_GOLD;
        assertEquals(1.0D, market.regime(pair), 1.0E-9D);
        market.restore(DIRT, market.logPrice(DIRT), 1.0E9D);
        assertTrue(market.regime(pair) > 1.0D);
        for (int tick = 0; tick < 1_000; tick++) {
            market.advance(tick, noChange(), MarketDriverFrame.calm());
            assertTrue(market.regime(pair) >= MarketEngine.REGIME_FLOOR);
            assertTrue(market.regime(pair) <= MarketEngine.REGIME_CEILING);
        }
    }

    /// An announced event may widen the quotes before the market has moved at all, but only through the floor it
    /// publishes: the market never quotes a risk nobody was told about.
    @Test
    void announcedRiskWidensTheRegimeBeforeTheMarketMoves() {
        MarketEngine market = new MarketEngine(17L);
        CurrencyPair pair = DIRT_GOLD;
        assertEquals(MarketEngine.REGIME_FLOOR, market.regime(pair), 1.0E-9D);
        market.advance(1L, noChange(), new MarketDriverFrame(noChange(), 3.0D));
        assertEquals(3.0D, market.regime(pair), 1.0E-9D);
        assertTrue(market.effectiveRegime(DIRT) >= 3.0D);
    }

    @Test
    void candleRollsOverOncePerPeriod() {
        MarketEngine market = new MarketEngine(3L);
        CurrencyPair pair = Currencies.pair(3);
        CandleSeries series = market.series(pair, Timeframe.M1);
        for (long tick = 1; tick <= Timeframe.M1.ticks(); tick++) {
            market.advance(tick, noChange(), MarketDriverFrame.calm());
        }
        assertEquals(1, series.snapshot(false).size());
        assertEquals(1, series.snapshot(true).size());
        market.advance(Timeframe.M1.ticks() + 1L, noChange(), MarketDriverFrame.calm());
        assertEquals(1, series.snapshot(false).size());
        assertEquals(2, series.snapshot(true).size());
        market.advance(2L * Timeframe.M1.ticks(), noChange(), MarketDriverFrame.calm());
        assertEquals(2, series.snapshot(false).size());
    }

    /// Every timeframe keeps a window of its own: the ticks that close an hourly candle leave the minute and
    /// quarter hour candles they contain closing on their own boundaries, and one window never reads another.
    @Test
    void everyTimeframeKeepsItsOwnWindow() {
        MarketEngine market = new MarketEngine(11L);
        CurrencyPair pair = Currencies.pair(1);
        long hour = Timeframe.H1.ticks();
        for (long tick = 1L; tick <= hour; tick++) {
            market.advance(tick, noChange(), MarketDriverFrame.calm());
        }
        assertEquals(1, market.series(pair, Timeframe.H1).snapshot(false).size(), "the hour closed once");
        assertEquals(hour / Timeframe.M15.ticks(), market.series(pair, Timeframe.M15).snapshot(false).size());
        assertEquals(hour / Timeframe.M1.ticks(), market.series(pair, Timeframe.M1).snapshot(false).size());
    }

    @Test
    void rolloverFiresOncePerDay() {
        MarketEngine market = new MarketEngine(5L);
        long day = MarketEngine.TICKS_PER_DAY;
        assertFalse(market.advance(1L, noChange(), MarketDriverFrame.calm()));
        assertTrue(market.advance(day, noChange(), MarketDriverFrame.calm()));
        assertFalse(market.advance(day + 1L, noChange(), MarketDriverFrame.calm()));
        assertFalse(market.advance(2L * day - 1L, noChange(), MarketDriverFrame.calm()));
        assertTrue(market.advance(2L * day, noChange(), MarketDriverFrame.calm()));
    }

    /// A world clock command must not move the market: the market only ever counts the ticks it was advanced
    /// for, so a jump in world time is not a jump in price.
    @Test
    void theMarketClockOnlyMovesWhenTheMarketAdvances() {
        MarketEngine market = new MarketEngine(23L);
        market.advance(10L, noChange(), MarketDriverFrame.calm());
        assertEquals(10L, market.marketTick());
        market.restoreClock(50_000L);
        assertEquals(50_000L, market.marketTick());
        assertEquals(1, market.series(Currencies.pair(0), Timeframe.M1).snapshot(true).size());
    }

    /// The generators are separate state: consuming the event generator, the execution generator or the volume
    /// generator cannot change the price series a world produces, which is what stops trading activity from
    /// picking the news and keeps the volume of a candle off the price path.
    @Test
    void theGeneratorsAreSeparateStreams() {
        MarketEngine first = new MarketEngine(29L);
        MarketEngine second = new MarketEngine(29L);
        for (int tick = 0; tick < 500; tick++) {
            first.advance(tick, noChange(), MarketDriverFrame.calm());
        }
        for (int tick = 0; tick < 500; tick++) {
            second.advance(tick, noChange(), MarketDriverFrame.calm());
            second.eventRng().nextGaussian();
            second.executionRng().nextDouble();
            second.volumeRng().nextDouble();
        }
        for (Currency currency : Currencies.all()) {
            assertEquals(first.logPrice(currency), second.logPrice(currency), 0.0D, currency.name());
        }
        assertEquals(first.priceRng().state(), second.priceRng().state());
        assertNotEquals(first.eventRng().state(), second.eventRng().state());
        assertNotEquals(first.volumeRng().state(), second.volumeRng().state());
    }

    /// A trade the players executed is booked into the candle of the pair it traded, in items of that pair's base
    /// currency, and into the candle of no other pair.
    @Test
    void aPlayerTradeLandsInTheCandleOfItsOwnPair() {
        MarketEngine market = new MarketEngine(37L);
        CurrencyPair pair = Currencies.pair(2);
        CurrencyPair other = Currencies.pair(5);
        market.advance(1L, noChange(), MarketDriverFrame.calm());
        double before = market.series(pair, Timeframe.M1).current().volume();
        double otherBefore = market.series(other, Timeframe.M1).current().volume();
        market.addTradeVolume(pair, 2_500.0D);
        assertEquals(before + 2_500.0D, market.series(pair, Timeframe.M1).current().volume(), 1.0E-9D,
                "the world's own volume of the candle is still in it");
        assertEquals(otherBefore, market.series(other, Timeframe.M1).current().volume(), 1.0E-9D,
                "another pair traded nothing of it");
    }

    /// The fundamental component and the event component both move a level, and the attribution of a bucket adds
    /// up to the log move that bucket actually produced.
    @Test
    void bucketAttributionAddsUpToThePriceChange() {
        MarketEngine market = new MarketEngine(31L);
        int count = Currencies.count();
        double[] fundamental = new double[count];
        double[] eventChange = new double[count];
        double[] drift = new double[count];
        double[] appliedFundamental = new double[count];
        double[] appliedEvent = new double[count];
        double[] noise = new double[count];
        double[] clamped = new double[count];
        double[] before = new double[count];
        int goldIndex = Currencies.index(GOLD);
        for (Currency currency : Currencies.all()) {
            before[Currencies.index(currency)] = market.logPrice(currency);
        }
        for (int tick = 0; tick < 600; tick++) {
            fundamental[goldIndex] = -1.0E-5D;
            eventChange[goldIndex] = 5.0E-6D;
            market.advance(tick, fundamental, new MarketDriverFrame(eventChange, MarketDriverFrame.NO_RISK_FLOOR));
        }
        market.bucketAttribution(drift, appliedFundamental, appliedEvent, noise, clamped);
        for (Currency currency : Currencies.all()) {
            int index = Currencies.index(currency);
            double attributed = drift[index] + appliedFundamental[index] + appliedEvent[index] + noise[index] - clamped[index];
            assertEquals(market.logPrice(currency) - before[index], attributed, 1.0E-9D, currency.name());
        }
        assertEquals(600 * -1.0E-5D, appliedFundamental[goldIndex], 1.0E-12D);
        assertEquals(600 * 5.0E-6D, appliedEvent[goldIndex], 1.0E-12D);
    }

    /// The scale is the world's own tuning: it multiplies what a currency definition names before the noise and
    /// the regime reference read it, so a world tuned twice as high walks the same seed twice as far while the
    /// market it trades still reads as calm, which is what keeps the platform's costs off the tuning.
    @Test
    void theVolatilityScaleWidensTheMarketWithoutReadingAsNervous() {
        double scale = 2.0D;
        MarketEngine calm = new MarketEngine(23L);
        MarketEngine wider = new MarketEngine(23L, 23L,
                MarketEngine.derive(23L, MarketEngine.EVENT_RNG_DOMAIN),
                MarketEngine.derive(23L, MarketEngine.EXECUTION_RNG_DOMAIN),
                MarketEngine.derive(23L, MarketEngine.VOLUME_RNG_DOMAIN), scale);
        for (int tick = 0; tick < 20_000; tick++) {
            calm.advance(tick, noChange(), MarketDriverFrame.calm());
            wider.advance(tick, noChange(), MarketDriverFrame.calm());
        }
        assertEquals(scale, wider.absReturn(DIRT) / calm.absReturn(DIRT), 1.0E-9D);
        assertTrue(calm.regime(DIRT_GOLD) <= 1.2D);
        assertTrue(wider.regime(DIRT_GOLD) <= 1.2D);
    }
}
