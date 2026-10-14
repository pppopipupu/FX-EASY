package com.pppopipupu.fxeasy.economy;

import module java.base;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;

/// Persisted world economy state: what the world actually produced, how that flow and the resulting inventory
/// pressure are estimated, how much of the fundamental revaluation has been applied so far, and the capacity the
/// market measures production against. Everything here is derived from committed world facts; nothing is guessed
/// from a scan of the world.
public record EconomyRow(Clock clock, Flow flow, Drivers drivers, CapacityValues capacity, double playerDayEwma,
        int playerTicks, int watchedTicks, List<AttributionRow> attribution) {

    public static final int ATTRIBUTION_CAPACITY = 160;

    public static final Codec<EconomyRow> CODEC = RecordCodecBuilder.create((RecordCodecBuilder.Instance<EconomyRow> instance) -> instance.group(
            Clock.CODEC.fieldOf("clock").forGetter(EconomyRow::clock),
            Flow.CODEC.fieldOf("flow").forGetter(EconomyRow::flow),
            Drivers.CODEC.fieldOf("drivers").forGetter(EconomyRow::drivers),
            CapacityValues.CODEC.fieldOf("capacity").forGetter(EconomyRow::capacity),
            Codec.DOUBLE.fieldOf("player_day_ewma").forGetter(EconomyRow::playerDayEwma),
            Codec.INT.fieldOf("player_ticks").forGetter(EconomyRow::playerTicks),
            Codec.INT.fieldOf("watched_ticks").forGetter(EconomyRow::watchedTicks),
            AttributionRow.CODEC.listOf().fieldOf("attribution").forGetter(EconomyRow::attribution)).apply(instance, EconomyRow::new));

    public EconomyRow {
        if (!Double.isFinite(playerDayEwma) || playerDayEwma < 0.0D) {
            throw new IllegalArgumentException("player day average must be finite and non negative");
        }
        if (attribution.size() > ATTRIBUTION_CAPACITY) {
            throw new IllegalArgumentException("attribution history is bounded to " + ATTRIBUTION_CAPACITY + " buckets");
        }
        attribution = List.copyOf(attribution);
    }

    /// A fresh world: no production observed yet, the full one player capacity, and the fundamental and event
    /// components at zero so the price keeps exactly the drift and noise it had before this extension existed.
    public static EconomyRow genesis() {
        return new EconomyRow(new Clock(0L, 0L, EconomyConstants.TICKS_PER_DAY, -1L), Flow.zero(), Drivers.zero(),
                CapacityValues.ofCapacity(), 1.0D, 0, 0, List.of());
    }

    /// The row a world that predates this extension starts from: the clock is anchored where the market already
    /// is, so the day boundary it used to roll over on is the next one it rolls over on, and every driver starts
    /// at zero. Nothing is back filled from before the load and no price jumps to the genesis valuation.
    public static EconomyRow migration(long marketTick, long bucketStart, long nextDayTick, long rolloverDay) {
        return new EconomyRow(new Clock(marketTick, bucketStart, nextDayTick, rolloverDay), Flow.zero(), Drivers.zero(),
                CapacityValues.ofCapacity(), 1.0D, 0, 0, List.of());
    }

    /// The market clock the economy runs on, and the boundaries it counts towards.
    public record Clock(long marketTick, long bucketStart, long nextDayTick, long rolloverDay) {

        public static final Codec<Clock> CODEC = RecordCodecBuilder.create((RecordCodecBuilder.Instance<Clock> instance) -> instance.group(
                Codec.LONG.fieldOf("market_tick").forGetter(Clock::marketTick),
                Codec.LONG.fieldOf("bucket_start").forGetter(Clock::bucketStart),
                Codec.LONG.fieldOf("next_day_tick").forGetter(Clock::nextDayTick),
                Codec.LONG.fieldOf("rollover_day").forGetter(Clock::rolloverDay)).apply(instance, Clock::new));
    }

    /// The flow estimates: normalised supply and demand in capacity units per economic day, and the inventory
    /// pressure they add up to.
    public record Flow(List<CurrencyAmount> supplyEwma, List<CurrencyAmount> demandEwma, List<CurrencyAmount> pressure) {

        public static final Codec<Flow> CODEC = RecordCodecBuilder.create((RecordCodecBuilder.Instance<Flow> instance) -> instance.group(
                CurrencyAmount.LIST_CODEC.fieldOf("supply").forGetter(Flow::supplyEwma),
                CurrencyAmount.LIST_CODEC.fieldOf("demand").forGetter(Flow::demandEwma),
                CurrencyAmount.LIST_CODEC.fieldOf("pressure").forGetter(Flow::pressure)).apply(instance, Flow::new));

        public static Flow zero() {
            return new Flow(CurrencyAmount.zero(), CurrencyAmount.zero(), CurrencyAmount.zero());
        }
    }

    /// What the market has been asked to price and how much of it it has actually applied: the world's target,
    /// the event layer's component, the per tick rate and the running totals of the open bucket.
    public record Drivers(List<CurrencyAmount> fundamental, List<CurrencyAmount> event, List<CurrencyAmount> target,
            List<CurrencyAmount> rate, List<CurrencyAmount> applied, List<CurrencyAmount> appliedLastBucket) {

        public static final Codec<Drivers> CODEC = RecordCodecBuilder.create((RecordCodecBuilder.Instance<Drivers> instance) -> instance.group(
                CurrencyAmount.LIST_CODEC.fieldOf("fundamental").forGetter(Drivers::fundamental),
                CurrencyAmount.LIST_CODEC.fieldOf("event").forGetter(Drivers::event),
                CurrencyAmount.LIST_CODEC.fieldOf("target").forGetter(Drivers::target),
                CurrencyAmount.LIST_CODEC.fieldOf("rate").forGetter(Drivers::rate),
                CurrencyAmount.LIST_CODEC.fieldOf("applied").forGetter(Drivers::applied),
                CurrencyAmount.LIST_CODEC.fieldOf("applied_last").forGetter(Drivers::appliedLastBucket)).apply(instance, Drivers::new));

        public static Drivers zero() {
            return new Drivers(CurrencyAmount.zero(), CurrencyAmount.zero(), CurrencyAmount.zero(),
                    CurrencyAmount.zero(), CurrencyAmount.zero(), CurrencyAmount.zero());
        }
    }

    /// Production the market treats as normal, in items per economic day, and the figure that takes effect at
    /// the next economic day.
    public record CapacityValues(List<CurrencyAmount> current, List<CurrencyAmount> next) {

        public static final Codec<CapacityValues> CODEC = RecordCodecBuilder.create((RecordCodecBuilder.Instance<CapacityValues> instance) -> instance.group(
                CurrencyAmount.LIST_CODEC.fieldOf("current").forGetter(CapacityValues::current),
                CurrencyAmount.LIST_CODEC.fieldOf("next").forGetter(CapacityValues::next)).apply(instance, CapacityValues::new));

        public static CapacityValues ofCapacity() {
            double[] capacities = new double[Currencies.count()];
            for (int index = 0; index < capacities.length; index++) {
                capacities[index] = Currencies.byIndex(index).capacity();
            }
            List<CurrencyAmount> base = CurrencyAmount.of(capacities);
            return new CapacityValues(base, base);
        }
    }

    /// Attribution of one economy bucket, in log units of the currency's own valuation: what the world produced,
    /// what the event layer expected, and how much of the combined driver budget had to be clamped away.
    public record AttributionRow(long bucketStart, List<CurrencyAmount> fundamental, List<CurrencyAmount> event,
            List<CurrencyAmount> clamped) {

        public static final Codec<AttributionRow> CODEC = RecordCodecBuilder.create((RecordCodecBuilder.Instance<AttributionRow> instance) -> instance.group(
                Codec.LONG.fieldOf("bucket_start").forGetter(AttributionRow::bucketStart),
                CurrencyAmount.LIST_CODEC.fieldOf("fundamental").forGetter(AttributionRow::fundamental),
                CurrencyAmount.LIST_CODEC.fieldOf("event").forGetter(AttributionRow::event),
                CurrencyAmount.LIST_CODEC.fieldOf("clamped").forGetter(AttributionRow::clamped)).apply(instance, AttributionRow::new));
    }

    /// Current capacity of one currency, in items per economic day.
    public double capacityOf(Currency currency) {
        return CurrencyAmount.get(this.capacity.current(), currency);
    }
}
