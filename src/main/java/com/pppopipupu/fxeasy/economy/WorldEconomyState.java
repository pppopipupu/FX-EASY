package com.pppopipupu.fxeasy.economy;

import module java.base;

import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;
import com.pppopipupu.fxeasy.market.MarketDriverFrame;

/// Runtime world economy of one server: the mutable form of [EconomyRow]. It accumulates the committed facts of
/// the open bucket, closes a bucket every [EconomyConstants#TICKS_PER_BUCKET] market ticks, and hands the market
/// one bounded log change per currency per tick. The event layer contributes through [#tick] as a requested
/// rate, so the fundamental and event channels draw on one shared budget and neither can be used to widen the
/// other.
public final class WorldEconomyState {

    private final EconomyBucket.Mutable open = new EconomyBucket.Mutable();
    private final double[] fundamental = new double[Currencies.count()];
    private final double[] event = new double[Currencies.count()];
    private final double[] demandEwma = new double[Currencies.count()];
    private final double[] supplyEwma = new double[Currencies.count()];
    private final double[] pressure = new double[Currencies.count()];
    private final double[] target = new double[Currencies.count()];
    private final double[] rate = new double[Currencies.count()];
    private final double[] applied = new double[Currencies.count()];
    private final double[] appliedLastBucket = new double[Currencies.count()];
    private final double[] spent = new double[Currencies.count()];
    private final double[] capacity = new double[Currencies.count()];
    private final double[] nextCapacity = new double[Currencies.count()];
    private final double[] clamped = new double[Currencies.count()];
    private final double[] eventApplied = new double[Currencies.count()];
    private final double[] eventClamped = new double[Currencies.count()];
    private final double[] eventThisTick = new double[Currencies.count()];
    private final double[] fundamentalThisTick = new double[Currencies.count()];
    private final List<EconomyRow.AttributionRow> attribution = new ArrayList<>();

    private long marketTick;
    private long bucketStart;
    private long nextDayTick;
    private long rolloverDay = -1L;
    private double playerDayEwma = 1.0D;
    private int playerTicks;
    private int watchedTicks;
    private double riskFloor = MarketDriverFrame.NO_RISK_FLOOR;

    private WorldEconomyState() {
    }

    public static WorldEconomyState genesis() {
        WorldEconomyState state = new WorldEconomyState();
        for (Currency currency : Currencies.all()) {
            int index = Currencies.index(currency);
            state.capacity[index] = currency.capacity();
            state.nextCapacity[index] = currency.capacity();
        }
        state.nextDayTick = EconomyConstants.TICKS_PER_DAY;
        return state;
    }

    /// Restores the economy of a world that predates this extension: the clock is anchored where the market
    /// already is, so the next day boundary is the one the market used to roll over on, and every driver starts
    /// at zero so no price jumps to the genesis valuation. The caller passes the market clock it loaded.
    public static WorldEconomyState migration(long marketTick, long bucketStart, long nextDayTick, long rolloverDay) {
        return restore(EconomyRow.migration(marketTick, bucketStart, nextDayTick, rolloverDay));
    }

    /// Restores a saved world. A world that has never run this extension has no economy row, and the caller
    /// passes one built from the current clock so prices continue from where they were instead of jumping to the
    /// genesis valuation.
    public static WorldEconomyState restore(EconomyRow row) {
        WorldEconomyState state = new WorldEconomyState();
        state.marketTick = row.clock().marketTick();
        state.bucketStart = row.clock().bucketStart();
        state.nextDayTick = row.clock().nextDayTick();
        state.rolloverDay = row.clock().rolloverDay();
        state.playerDayEwma = row.playerDayEwma();
        state.playerTicks = row.playerTicks();
        state.watchedTicks = row.watchedTicks();
        read(row.drivers().fundamental(), state.fundamental);
        read(row.drivers().event(), state.event);
        read(row.flow().demandEwma(), state.demandEwma);
        read(row.flow().supplyEwma(), state.supplyEwma);
        read(row.flow().pressure(), state.pressure);
        read(row.drivers().target(), state.target);
        read(row.drivers().rate(), state.rate);
        read(row.drivers().applied(), state.applied);
        read(row.drivers().appliedLastBucket(), state.appliedLastBucket);
        for (Currency currency : Currencies.all()) {
            state.spent[Currencies.index(currency)] = Math.abs(state.applied[Currencies.index(currency)]);
        }
        read(row.capacity().current(), state.capacity);
        read(row.capacity().next(), state.nextCapacity);
        state.attribution.addAll(row.attribution());
        return state;
    }

    /// The accumulator the committed fact adapters write into. Only the server thread touches it.
    public EconomyBucket.Mutable openBucket() {
        return this.open;
    }

    public long marketTick() {
        return this.marketTick;
    }

    public long bucketStart() {
        return this.bucketStart;
    }

    public long rolloverDay() {
        return this.rolloverDay;
    }

    public double fundamental(Currency currency) {
        return this.fundamental[Currencies.index(currency)];
    }

    public double event(Currency currency) {
        return this.event[Currencies.index(currency)];
    }

    public double capacity(Currency currency) {
        return this.capacity[Currencies.index(currency)];
    }

    public double pressure(Currency currency) {
        return this.pressure[Currencies.index(currency)];
    }

    public double supplyEwma(Currency currency) {
        return this.supplyEwma[Currencies.index(currency)];
    }

    public double demandEwma(Currency currency) {
        return this.demandEwma[Currencies.index(currency)];
    }

    public double target(Currency currency) {
        return this.target[Currencies.index(currency)];
    }

    public double playerDayEwma() {
        return this.playerDayEwma;
    }

    /// Volatility the platform has publicly warned about, never below one. The market takes the larger of this
    /// and what it has actually realised, so a published event widens quotes without leaking an unannounced one.
    public double riskFloor() {
        return this.riskFloor;
    }

    /// Publishes the risk floor the event layer currently justifies, once per tick before the market advances.
    public void setRiskFloor(double riskFloor) {
        this.riskFloor = Double.isFinite(riskFloor) ? Math.max(MarketDriverFrame.NO_RISK_FLOOR, riskFloor) : MarketDriverFrame.NO_RISK_FLOOR;
    }

    public List<EconomyRow.AttributionRow> attribution() {
        return List.copyOf(this.attribution);
    }

    /// Records one tick of the world clock with the number of players online and returns true when the bucket
    /// boundary was crossed, which is the moment the caller seals the open bucket through [#closeBucket].
    public boolean advanceClock(int onlinePlayers) {
        this.marketTick++;
        observePlayers(onlinePlayers);
        if (this.marketTick < this.bucketStart + EconomyConstants.TICKS_PER_BUCKET) {
            return false;
        }
        this.bucketStart += EconomyConstants.TICKS_PER_BUCKET;
        return true;
    }

    /// Convenience for callers that only know whether anyone is online. Capacity scales with online player
    /// ticks, so a caller that can report a count should use [#advanceClock(int)] instead.
    public boolean advanceClock(boolean anyPlayerOnline) {
        return advanceClock(anyPlayerOnline ? 1 : 0);
    }

    /// Whether the economic day just rolled over, which is when swap settles. Unlike world time this cannot be
    /// skipped by sleeping, because it counts market ticks.
    public boolean dayRolledOver() {
        if (this.marketTick < this.nextDayTick) {
            return false;
        }
        this.nextDayTick += EconomyConstants.TICKS_PER_DAY;
        this.rolloverDay++;
        updateCapacity();
        System.arraycopy(this.nextCapacity, 0, this.capacity, 0, Currencies.count());
        return true;
    }

    /// Closes the open bucket: folds its committed atoms into the flow estimates, appends the attribution of the
    /// bucket that just ended, and plans the next bucket's revaluation rate. Returns the bucket that was closed
    /// so the caller can report it.
    public EconomyBucket closeBucket() {
        EconomyBucket bucket = this.open.seal();
        updateFlows(bucket);
        this.attribution.add(new EconomyRow.AttributionRow(this.bucketStart - EconomyConstants.TICKS_PER_BUCKET,
                CurrencyAmount.of(this.fundamental), CurrencyAmount.of(this.event), CurrencyAmount.of(this.clamped)));
        while (this.attribution.size() > EconomyRow.ATTRIBUTION_CAPACITY) {
            this.attribution.removeFirst();
        }
        System.arraycopy(this.applied, 0, this.appliedLastBucket, 0, Currencies.count());
        Arrays.fill(this.applied, 0.0D);
        Arrays.fill(this.spent, 0.0D);
        Arrays.fill(this.clamped, 0.0D);
        Arrays.fill(this.eventApplied, 0.0D);
        Arrays.fill(this.eventClamped, 0.0D);
        return bucket;
    }

    /// One tick of the economy: spreads the planned revaluation, folds in what the event layer asks for, keeps
    /// the combined change inside the bucket budget, and returns the frame the market advances with. The budget
    /// is spent on absolute log change, so a positive and a negative request draw on the same allowance and
    /// cannot be used to offset each other into extra movement.
    public MarketDriverFrame tick(double[] eventRateRequest) {
        Arrays.fill(this.eventThisTick, 0.0D);
        Arrays.fill(this.fundamentalThisTick, 0.0D);
        for (Currency currency : Currencies.all()) {
            int index = Currencies.index(currency);
            double remaining = EconomyConstants.MAX_DRIVER_STEP_PER_BUCKET - this.spent[index];
            double fundamentalApplied = Math.clamp(this.rate[index], -remaining, remaining);
            remaining -= Math.abs(fundamentalApplied);
            double eventApplied = Math.clamp(eventRateRequest[index], -remaining, remaining);
            remaining -= Math.abs(eventApplied);
            this.spent[index] = EconomyConstants.MAX_DRIVER_STEP_PER_BUCKET - remaining;
            this.clamped[index] += this.rate[index] - fundamentalApplied;
            this.eventClamped[index] += eventRateRequest[index] - eventApplied;
            this.fundamental[index] += fundamentalApplied;
            this.event[index] += eventApplied;
            this.applied[index] += fundamentalApplied + eventApplied;
            this.eventApplied[index] += eventApplied;
            this.eventThisTick[index] = eventApplied;
            this.fundamentalThisTick[index] = fundamentalApplied;
        }
        return new MarketDriverFrame(this.eventThisTick, this.riskFloor);
    }

    /// Fundamental log change of the tick that just ran, one entry per currency, ready to hand to the price
    /// process together with the frame [#tick] returned.
    public double[] fundamentalThisTick() {
        return this.fundamentalThisTick;
    }

    /// Frozen view of everything the terminal reports about the economy.
    public EconomyRow snapshot() {
        return new EconomyRow(
                new EconomyRow.Clock(this.marketTick, this.bucketStart, this.nextDayTick, this.rolloverDay),
                new EconomyRow.Flow(CurrencyAmount.of(this.supplyEwma), CurrencyAmount.of(this.demandEwma),
                        CurrencyAmount.of(this.pressure)),
                new EconomyRow.Drivers(CurrencyAmount.of(this.fundamental), CurrencyAmount.of(this.event),
                        CurrencyAmount.of(this.target), CurrencyAmount.of(this.rate), CurrencyAmount.of(this.applied),
                        CurrencyAmount.of(this.appliedLastBucket)),
                new EconomyRow.CapacityValues(CurrencyAmount.of(this.capacity), CurrencyAmount.of(this.nextCapacity)),
                this.playerDayEwma, this.playerTicks, this.watchedTicks, List.copyOf(this.attribution));
    }

    /// Log units of fundamental revaluation the bucket budget had to clamp away, per currency, since the last
    /// bucket closed. Reported so a stuck price never hides an unbounded backlog.
    public double clamped(Currency currency) {
        return this.clamped[Currencies.index(currency)];
    }

    /// Log units of event contribution that were clamped away in the current bucket.
    public double eventClamped(Currency currency) {
        return this.eventClamped[Currencies.index(currency)];
    }

    /// Log units of event contribution actually applied in the current bucket.
    public double eventApplied(Currency currency) {
        return this.eventApplied[Currencies.index(currency)];
    }

    /// Folds one closed bucket into the flow estimates, moves the inventory pressure and plans the per tick
    /// revaluation rate of the bucket that just started.
    private void updateFlows(EconomyBucket bucket) {
        for (Currency currency : Currencies.all()) {
            int index = Currencies.index(currency);
            double scale = Math.max(1.0D, this.capacity[index]);
            double supply = EconomyModel.normalizedSupply(bucket, currency, scale);
            double demand = EconomyModel.normalizedDemand(bucket, currency, scale);
            this.supplyEwma[index] += EconomyModel.FLOW_ALPHA * (supply - this.supplyEwma[index]);
            this.demandEwma[index] += EconomyModel.FLOW_ALPHA * (demand - this.demandEwma[index]);
            double wanted = EconomyModel.pressureTarget(this.supplyEwma[index], this.demandEwma[index]);
            double alpha = wanted > this.pressure[index] ? EconomyModel.PRESSURE_RISE_ALPHA : EconomyModel.PRESSURE_FALL_ALPHA;
            this.pressure[index] += alpha * (wanted - this.pressure[index]);
            this.target[index] = EconomyModel.fundamentalTarget(this.pressure[index],
                    EconomyModel.residualDemand(this.supplyEwma[index], this.demandEwma[index]), currency);
            this.rate[index] = EconomyModel.revaluationRate(this.target[index], this.fundamental[index]);
        }
    }

    /// Player activity of one tick. The average number of players online over an economic day is the player day
    /// figure capacity scales with, so a ten second login cannot move a market.
    private void observePlayers(int onlinePlayers) {
        this.watchedTicks++;
        this.playerTicks = (int) Math.min(Integer.MAX_VALUE, (long) this.playerTicks + Math.max(0, onlinePlayers));
    }

    /// Capacity of the next economic day. It follows the player day average but moves at most ten percent a day,
    /// and it never drops below the one player figure.
    private void updateCapacity() {
        double playerDays = this.watchedTicks == 0 ? this.playerDayEwma : (double) this.playerTicks / this.watchedTicks;
        this.playerDayEwma = playerDays;
        double scale = Math.max(1.0D, playerDays);
        for (Currency currency : Currencies.all()) {
            int index = Currencies.index(currency);
            double wanted = currency.capacity() * scale;
            double current = this.capacity[index];
            double limit = current * EconomyConstants.MAX_CAPACITY_STEP_PER_DAY;
            this.nextCapacity[index] = Math.max(1.0D, current + Math.clamp(wanted - current, -limit, limit));
        }
        this.playerTicks = 0;
        this.watchedTicks = 0;
    }

    private static void read(List<CurrencyAmount> source, double[] target) {
        System.arraycopy(CurrencyAmount.toArray(source), 0, target, 0, target.length);
    }
}
