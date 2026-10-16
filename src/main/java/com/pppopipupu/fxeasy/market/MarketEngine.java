package com.pppopipupu.fxeasy.market;

import module java.base;

/// Server authoritative price process. Every currency walks on a log price of its own: an anchor that drifts by
/// the currency's interest rate and takes a small residual shock every tick, a fundamental revaluation the world
/// economy has actually applied, and an event component the published event layer has applied. The price of a
/// pair is the ratio of the two, so cross rates are consistent by construction and a pair drifts by the interest
/// difference of its two currencies, which is what holding it pays or costs in swap.
///
/// A level is only ever meaningful relative to the level of another currency: the whole vector may be shifted by
/// any constant without changing a single price, so nothing here is measured in a unit of its own and the
/// public surface only ever hands out rates. The band each level is held inside is measured in the frame the
/// definitions opened the world on, which is what keeps a world that runs for years from running away.
public final class MarketEngine {

    public static final int TICKS_PER_DAY = 24000;
    public static final int DAYS_PER_YEAR = 365;
    public static final int TICKS_PER_YEAR = TICKS_PER_DAY * DAYS_PER_YEAR;
    /// Hard band on the log deviation from the level a currency opened on: prices stay within a factor of 16
    /// either way. It is a stop for a world that runs for years, not a force in the market - nothing pulls a
    /// currency back, the band only catches it at the end.
    public static final double MAX_LOG_DEVIATION = Math.log(16.0D);
    public static final double REGIME_FLOOR = 1.0D;
    public static final double REGIME_CEILING = 8.0D;
    /// Share of the daily volatility the residual noise keeps. The world and the event layer take over the rest,
    /// so a market with no production stays quieter than a pure random walk without ever being pinned. The value
    /// is the calibration the market is tuned to: 1.5 times the daily volatility a currency definition names,
    /// of which this share stays with the noise.
    public static final double RESIDUAL_NOISE_SHARE = 0.525D;
    /// Volatility multiple a world starts a market on. One is the calibration the constants here carry; a server
    /// may retune the whole market through the configuration, and the market is built with the value a world
    /// loaded under, so a running market is never retuned in the middle of a bucket.
    public static final double DEFAULT_VOLATILITY_SCALE = 1.0D;
    private static final double EWMA_ALPHA = 0.01D;
    private static final double EXPECTED_ABS_NORMAL = Math.sqrt(2.0D / Math.PI);
    /// Expected absolute log return of one market tick, in units of the daily volatility the market walks with:
    /// the half normal expectation, 1.5 times what a currency definition names. The realised volatility of a
    /// currency is measured against this, so the regime reads one in a calm market whatever the world is tuned
    /// to, and the costs that follow the regime do not move with the tuning.
    private static final double EXPECTED_TICK_RETURN = EXPECTED_ABS_NORMAL * 1.5D;
    /// Domains the event and execution generators are derived under, so a save that only stored the price
    /// generator still reproduces the other two.
    public static final long EVENT_RNG_DOMAIN = 0x51A7E9C4D2B1F803L;
    public static final long EXECUTION_RNG_DOMAIN = 0x2C6D19B7A3E54F81L;
    public static final long VOLUME_RNG_DOMAIN = 0x7B1D43A9C6F80E52L;
    /// Share of what the scarcer side of a pair produces in one economic day that the world trades of that pair
    /// in the same day, before the regime and the randomness of the tick are applied. It is what a volume bar of
    /// the chart is measured against: a currency the world makes little of is also one little of it changes
    /// hands, and a player order is measured on the same scale.
    public static final double WORLD_VOLUME_SHARE = 0.25D;
    /// Share of the market ticks the world trades on at all. Volume clusters rather than spreading evenly, so a
    /// tick either trades nothing or trades the many ticks it stands for: a candle of twelve hundred ticks then
    /// carries a dozen bursts of different sizes instead of the same average a dozen times over, which is what
    /// makes one bar of a chart taller than the bar beside it.
    public static final double WORLD_TRADE_CHANCE = 0.01D;

    private final List<Currency> currencies;
    private final List<CurrencyPair> pairs;
    private final Map<Currency, Integer> indices;
    private final double[] origins;
    private final double[] logPrices;
    private final double[] tickSigmas;
    private final double[] tickDrifts;
    private final double[] references;
    private final double[] absReturns;
    private final double[] effectiveRegimes;
    private final double[] bucketDrift;
    private final double[] bucketFundamental;
    private final double[] bucketEvent;
    private final double[] bucketNoise;
    private final double[] bucketClamped;
    private final CandleSeries[][] series;

    private final Rng priceRng;
    private final Rng eventRng;
    private final Rng executionRng;
    private final Rng volumeRng;

    private long marketTick;
    private long lastRolloverDay = -1L;
    private double riskFloor = MarketDriverFrame.NO_RISK_FLOOR;

    public MarketEngine(long seed) {
        this(seed, seed);
    }

    /// Builds the price process of a fresh world. All four generators are derived from the world seed through
    /// separate domains, so a world reproduces exactly from its seed and no channel can consume another's
    /// randomness.
    public MarketEngine(long worldSeed, long priceSeed) {
        this(worldSeed, priceSeed, derive(priceSeed, EVENT_RNG_DOMAIN), derive(priceSeed, EXECUTION_RNG_DOMAIN));
    }

    /// Builds the price process from the generator states a save carries. The world seed is kept for the
    /// components that are not random at all, and the volume generator is derived from the price generator when
    /// the save predates it.
    public MarketEngine(long worldSeed, long priceSeed, long eventSeed, long executionSeed) {
        this(worldSeed, priceSeed, eventSeed, executionSeed, derive(priceSeed, VOLUME_RNG_DOMAIN));
    }

    public MarketEngine(long worldSeed, long priceSeed, long eventSeed, long executionSeed, long volumeSeed) {
        this(worldSeed, priceSeed, eventSeed, executionSeed, volumeSeed, DEFAULT_VOLATILITY_SCALE);
    }

    /// Builds the price process of one world at the volatility the world is tuned to. The scale multiplies what
    /// the currency definitions name before anything reads it, so a world retunes how far its rates swing in one
    /// place: the noise keeps its share of the tuned daily volatility and the regime keeps measuring the market
    /// against the same tuned process.
    public MarketEngine(long worldSeed, long priceSeed, long eventSeed, long executionSeed, long volumeSeed,
            double volatilityScale) {
        if (!(volatilityScale > 0.0D) || !Double.isFinite(volatilityScale)) {
            throw new IllegalArgumentException("volatility scale must be a positive number");
        }
        this.currencies = Currencies.all();
        this.pairs = Currencies.pairs();
        int count = this.currencies.size();
        this.indices = new HashMap<>(count * 2);
        for (int index = 0; index < count; index++) {
            this.indices.put(this.currencies.get(index), index);
        }
        this.origins = new double[count];
        this.logPrices = new double[count];
        this.tickSigmas = new double[count];
        this.tickDrifts = new double[count];
        this.references = new double[count];
        this.absReturns = new double[count];
        this.effectiveRegimes = new double[count];
        this.bucketDrift = new double[count];
        this.bucketFundamental = new double[count];
        this.bucketEvent = new double[count];
        this.bucketNoise = new double[count];
        this.bucketClamped = new double[count];
        for (Currency currency : this.currencies) {
            int index = index(currency);
            this.origins[index] = Math.log(currency.initialPrice());
            this.logPrices[index] = this.origins[index];
            double dailySigma = currency.dailySigma() * volatilityScale;
            this.tickSigmas[index] = dailySigma * RESIDUAL_NOISE_SHARE / Math.sqrt(TICKS_PER_DAY);
            this.tickDrifts[index] = -currency.annualRate() / (double) TICKS_PER_YEAR;
            this.references[index] = EXPECTED_TICK_RETURN * dailySigma / Math.sqrt(TICKS_PER_DAY);
            this.absReturns[index] = this.references[index];
            this.effectiveRegimes[index] = REGIME_FLOOR;
        }
        this.series = new CandleSeries[this.pairs.size()][Timeframe.ALL.size()];
        for (CurrencyPair pair : this.pairs) {
            for (Timeframe timeframe : Timeframe.ALL) {
                this.series[pair.index()][timeframe.ordinal()] = new CandleSeries(timeframe);
            }
        }
        this.priceRng = new Rng(priceSeed);
        this.eventRng = new Rng(eventSeed);
        this.executionRng = new Rng(executionSeed);
        this.volumeRng = new Rng(volumeSeed);
    }

    /// A stable child seed: the same base always yields the same child, so a save that only stored one generator
    /// keeps the other two reproducible.
    public static long derive(long base, long domain) {
        long state = base + domain;
        long z = state;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /// Monotonic market clock: one per advanced server tick. It is the only time the market runs on, so sleeping
    /// or a time command cannot skip a market day.
    public long marketTick() {
        return this.marketTick;
    }

    /// Randomness of the residual price shock. Only this generator may be consumed by the price process.
    public Rng priceRng() {
        return this.priceRng;
    }

    /// Randomness of event scheduling and of results that have not been published yet. Trading never consumes
    /// it, so a player cannot pick the next event by opening and closing positions.
    public Rng eventRng() {
        return this.eventRng;
    }

    /// Randomness of order execution. Only slippage draws on it.
    public Rng executionRng() {
        return this.executionRng;
    }

    /// Randomness of the world's own trading volume. It is the only channel that draws on it, so a chart can
    /// never pick a price and a price can never pick a volume bar.
    public Rng volumeRng() {
        return this.volumeRng;
    }

    /// Items of `to` one item of `from` is worth right now, which is the only kind of price this market has.
    public double rate(Currency from, Currency to) {
        return Math.exp(this.logPrices[index(from)] - this.logPrices[index(to)]);
    }

    /// Price of one currency on the scale the whole vector shares. Only the ratio of two of these is a price, so
    /// this is what a feed carries and nothing sums it; [#rate(Currency, Currency)] is the price of a pair.
    public double price(Currency currency) {
        return Math.exp(this.logPrices[index(currency)]);
    }

    public double price(CurrencyPair pair) {
        return rate(pair.base(), pair.quote());
    }

    /// Log price of one currency, which is meaningful only against the log price of another currency. Saves
    /// carry it so a load resumes on the rates it stopped on.
    public double logPrice(Currency currency) {
        return this.logPrices[index(currency)];
    }

    /// Volatility regime multiplier of the currencies of a pair, 1.0 in a calm market and up to
    /// [#REGIME_CEILING] while one of them is crashing or spiking. It is the larger of what the currencies have
    /// actually realised and the risk the platform has publicly warned about, so an announced event widens quotes
    /// and an unannounced one cannot.
    public double regime(CurrencyPair pair) {
        double base = this.effectiveRegimes[index(pair.base())];
        double quote = this.effectiveRegimes[index(pair.quote())];
        return Math.clamp(0.5D * (base + quote), REGIME_FLOOR, REGIME_CEILING);
    }

    public CandleSeries series(CurrencyPair pair, Timeframe timeframe) {
        return this.series[pair.index()][timeframe.ordinal()];
    }

    public List<Candle> candles(CurrencyPair pair, Timeframe timeframe, boolean includeCurrent) {
        return series(pair, timeframe).snapshot(includeCurrent);
    }

    /// Advances the market by one market tick and returns true when a new economic day started, which is the
    /// moment swap fees are charged. `fundamentalChange` is the log change the world economy has already
    /// budgeted for this tick and `frame` carries the event layer's change and the published risk floor, both of
    /// them deltas of this tick rather than levels: they are folded into the anchor, so the revaluation a
    /// currency earned on earlier ticks stays in its price and the attribution of a bucket adds up to the move
    /// the bucket actually produced. Called exactly once per market tick.
    public boolean advance(long marketTick, double[] fundamentalChange, MarketDriverFrame frame) {
        this.marketTick = marketTick;
        this.riskFloor = frame.riskFloor();
        for (Currency currency : this.currencies) {
            int index = index(currency);
            double noise = this.tickSigmas[index] * this.priceRng.nextGaussian();
            double fundamental = fundamentalChange[index];
            double event = frame.eventChange(currency);
            double wanted = this.logPrices[index] + this.tickDrifts[index] + noise + fundamental + event;
            double clampLow = this.origins[index] - MAX_LOG_DEVIATION;
            double clampHigh = this.origins[index] + MAX_LOG_DEVIATION;
            double next = Math.clamp(wanted, clampLow, clampHigh);
            double total = next - this.logPrices[index];
            this.logPrices[index] = next;
            this.absReturns[index] += EWMA_ALPHA * (Math.abs(total) - this.absReturns[index]);
            double realised = Math.clamp(this.absReturns[index] / this.references[index], REGIME_FLOOR, REGIME_CEILING);
            this.effectiveRegimes[index] = Math.max(realised, this.riskFloor);
            this.bucketDrift[index] += this.tickDrifts[index];
            this.bucketNoise[index] += noise;
            this.bucketFundamental[index] += fundamental;
            this.bucketEvent[index] += event;
            this.bucketClamped[index] += wanted - next;
        }
        double[] prices = new double[this.pairs.size()];
        for (CurrencyPair pair : this.pairs) {
            prices[pair.index()] = price(pair);
        }
        for (CurrencyPair pair : this.pairs) {
            double volume = worldVolume(pair);
            for (Timeframe timeframe : Timeframe.ALL) {
                this.series[pair.index()][timeframe.ordinal()].accept(marketTick, prices[pair.index()], volume);
            }
        }
        long day = Math.floorDiv(marketTick, TICKS_PER_DAY);
        boolean rollover = this.lastRolloverDay >= 0L && day != this.lastRolloverDay;
        this.lastRolloverDay = day;
        return rollover;
    }

    /// Items of the base currency the world trades of this pair in one economic day, before the regime and the
    /// randomness of the tick: a share of what the scarcer side of the pair produces in the same day, so a pair
    /// whose quote the world makes little of is also a pair little of it changes hands of. Nothing here is a
    /// level of the price process - it is a size, in items, which is the only thing the chart scales it against.
    private double dailyVolume(CurrencyPair pair) {
        double base = pair.base().capacity();
        double quote = pair.quote().capacity() / price(pair);
        return Math.min(base, quote) * WORLD_VOLUME_SHARE;
    }

    /// The world's own trading of this market tick, in items of the base currency of the pair: nothing on most
    /// ticks, and otherwise one day of the pair spread over the ticks of a day, widened by the regime it is in,
    /// scaled up for the ticks that trade nothing, and drawn as an exponential, so the bursts themselves are of
    /// different sizes. What the players trade is added on top of it by [#addTradeVolume], and both are what a
    /// volume bar of the chart shows.
    private double worldVolume(CurrencyPair pair) {
        if (!this.volumeRng.nextChance(WORLD_TRADE_CHANCE)) {
            return 0.0D;
        }
        double burst = dailyVolume(pair) / TICKS_PER_DAY * regime(pair) / WORLD_TRADE_CHANCE;
        return burst * -Math.log(1.0D - this.volumeRng.nextDouble());
    }

    /// Books a trade that executed on this pair into the candle every timeframe is forming, in items of the base
    /// currency of the pair. The world's own trading and what the players trade are the same quantity to a chart,
    /// which is what a volume bar shows, so both land in the same candle.
    public void addTradeVolume(CurrencyPair pair, double baseItems) {
        for (Timeframe timeframe : Timeframe.ALL) {
            this.series[pair.index()][timeframe.ordinal()].addVolume(baseItems);
        }
    }

    /// Log return a pair is expected to make over one market day, `(rate(quote) - rate(base)) / 365`. The higher
    /// yielding currency of the two drifts down against the other by the interest difference, so the price move
    /// of a carried position cancels the swap it is charged and only the platform's markup is left.
    public double pairDrift(CurrencyPair pair) {
        return (pair.quote().annualRate() - pair.base().annualRate()) / DAYS_PER_YEAR;
    }

    public double absReturn(Currency currency) {
        return this.absReturns[index(currency)];
    }

    public double effectiveRegime(Currency currency) {
        return this.effectiveRegimes[index(currency)];
    }

    public double riskFloor() {
        return this.riskFloor;
    }

    public long lastRolloverDay() {
        return this.lastRolloverDay;
    }

    /// Log returns of the bucket that is currently open, by category, per currency. The terminal reports these
    /// as the attribution of the window, and they always add up to the actual pair return of the window.
    public void bucketAttribution(double[] drift, double[] fundamental, double[] event, double[] noise, double[] clamped) {
        System.arraycopy(this.bucketDrift, 0, drift, 0, this.currencies.size());
        System.arraycopy(this.bucketFundamental, 0, fundamental, 0, this.currencies.size());
        System.arraycopy(this.bucketEvent, 0, event, 0, this.currencies.size());
        System.arraycopy(this.bucketNoise, 0, noise, 0, this.currencies.size());
        System.arraycopy(this.bucketClamped, 0, clamped, 0, this.currencies.size());
    }

    /// Clears the attribution of the bucket that just closed. The next bucket starts from zero on every
    /// category, so a report never carries an older window.
    public void resetBucketAttribution() {
        Arrays.fill(this.bucketDrift, 0.0D);
        Arrays.fill(this.bucketFundamental, 0.0D);
        Arrays.fill(this.bucketEvent, 0.0D);
        Arrays.fill(this.bucketNoise, 0.0D);
        Arrays.fill(this.bucketClamped, 0.0D);
    }

    /// Restores a currency from the level and the realised volatility a save carries.
    public void restore(Currency currency, double logPrice, double absReturn) {
        int index = index(currency);
        double clamped = Math.clamp(logPrice, this.origins[index] - MAX_LOG_DEVIATION,
                this.origins[index] + MAX_LOG_DEVIATION);
        this.logPrices[index] = clamped;
        this.absReturns[index] = Math.clamp(absReturn, this.references[index] * 0.1D, this.references[index] * REGIME_CEILING);
        this.effectiveRegimes[index] = Math.clamp(this.absReturns[index] / this.references[index], REGIME_FLOOR, REGIME_CEILING);
    }

    /// Rebuilds a currency from the level its own process reached and the revaluation the world economy and the
    /// event layer applied on top of it. The stored anchor is the price without those components, so the price
    /// that comes back is exactly the one that was saved and the next tick continues from it rather than
    /// re-applying the whole revaluation a second time.
    public void restoreComponents(Currency currency, double anchor, double fundamental, double event) {
        int index = index(currency);
        double low = this.origins[index] - MAX_LOG_DEVIATION;
        double high = this.origins[index] + MAX_LOG_DEVIATION;
        this.logPrices[index] = Math.clamp(anchor + fundamental + event, low, high);
    }

    public void restoreRollover(long day) {
        this.lastRolloverDay = day;
    }

    /// Continues the monotonic clock of a loaded save, so the first tick after a load is the tick after the last
    /// one that was saved and never a jump backwards.
    public void restoreClock(long marketTick) {
        this.marketTick = marketTick;
    }

    private int index(Currency currency) {
        Integer index = this.indices.get(currency);
        if (index == null) {
            throw new IllegalArgumentException("currency " + currency.id() + " is not part of this market");
        }
        return index;
    }
}
