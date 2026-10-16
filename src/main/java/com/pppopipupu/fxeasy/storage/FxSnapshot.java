package com.pppopipupu.fxeasy.storage;

import module java.base;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.pppopipupu.fxeasy.economy.EconomyRow;
import com.pppopipupu.fxeasy.market.Candle;
import com.pppopipupu.fxeasy.shop.Membership;

/// Everything the FX economy has to remember: the price process, the candle window of every pair and timeframe,
/// every trading account, the monotonic market clock, the world economy, the published event layer and the
/// separate random generators. `marketAnchor` is the offset from world time to the market clock, which is
/// what keeps a candle's tick and a save's day boundary in the same frame across a reload. The shape is
/// versioned, so a save written before the world economy existed is recognised and migrated rather than
/// overwritten, and a save written before the volume generator existed reads as one whose volume stream is
/// derived from its price stream.
public record FxSnapshot(int schemaVersion, long marketTick, long chartEpoch, long priceRng, long eventRng,
        long executionRng, long volumeRng, long rolloverDay, long marketAnchor, List<CurrencyRow> currencies,
        List<SeriesRow> series, Optional<EconomyRow> economy, EventRow events, List<AccountRow> accounts) {

    /// Version written by this build. A save without the field is version 1.
    public static final int SCHEMA_VERSION = 2;
    /// Version of a save written before the world economy, the market clock and the separated generators.
    public static final int LEGACY_SCHEMA_VERSION = 1;

    public static final Codec<FxSnapshot> CODEC = RecordCodecBuilder.create((RecordCodecBuilder.Instance<FxSnapshot> instance) -> instance.group(
            Codec.INT.optionalFieldOf("schema", LEGACY_SCHEMA_VERSION).forGetter(FxSnapshot::schemaVersion),
            Codec.LONG.optionalFieldOf("market_tick", 0L).forGetter(FxSnapshot::marketTick),
            Codec.LONG.optionalFieldOf("chart_epoch", 0L).forGetter(FxSnapshot::chartEpoch),
            Codec.LONG.fieldOf("rng").forGetter(FxSnapshot::priceRng),
            Codec.LONG.optionalFieldOf("event_rng", Long.MIN_VALUE).forGetter(FxSnapshot::eventRng),
            Codec.LONG.optionalFieldOf("execution_rng", Long.MIN_VALUE).forGetter(FxSnapshot::executionRng),
            Codec.LONG.optionalFieldOf("volume_rng", Long.MIN_VALUE).forGetter(FxSnapshot::volumeRng),
            Codec.LONG.fieldOf("rolloverDay").forGetter(FxSnapshot::rolloverDay),
            Codec.LONG.optionalFieldOf("market_anchor", Long.MIN_VALUE).forGetter(FxSnapshot::marketAnchor),
            CurrencyRow.CODEC.listOf().fieldOf("currencies").forGetter(FxSnapshot::currencies),
            SeriesRow.CODEC.listOf().fieldOf("series").forGetter(FxSnapshot::series),
            EconomyRow.CODEC.optionalFieldOf("world_economy").forGetter(FxSnapshot::economy),
            EventRow.CODEC.optionalFieldOf("events", EventRow.empty()).forGetter(FxSnapshot::events),
            AccountRow.CODEC.listOf().fieldOf("accounts").forGetter(FxSnapshot::accounts)).apply(instance, FxSnapshot::new));

    public static final FxSnapshot EMPTY = new FxSnapshot(SCHEMA_VERSION, 0L, 0L, 0L, Long.MIN_VALUE, Long.MIN_VALUE,
            Long.MIN_VALUE, -1L, Long.MIN_VALUE, List.of(), List.of(), Optional.empty(), EventRow.empty(), List.of());

    public FxSnapshot {
        currencies = List.copyOf(currencies);
        series = List.copyOf(series);
        accounts = List.copyOf(accounts);
    }

    /// Whether this save predates the world economy, which is what decides the one time observation period and
    /// the honest coverage note the terminal shows.
    public boolean legacy() {
        return this.schemaVersion < SCHEMA_VERSION || this.economy.isEmpty();
    }

    /// Log price of one currency, plus the components the world economy and the event layer have applied on top
    /// of the anchor and the realised volatility estimate. A log price is only meaningful against the log price
    /// of another currency, so the whole column may be shifted without changing a single rate; what a load
    /// restores is the ratio between the entries.
    public record CurrencyRow(String currency, double logPrice, double absReturn, double anchor, double fundamental,
            double event) {

        private static final Codec<CurrencyRow> BASE = RecordCodecBuilder.create((RecordCodecBuilder.Instance<CurrencyRow> instance) -> instance.group(
                Codec.STRING.fieldOf("currency").forGetter(CurrencyRow::currency),
                Codec.DOUBLE.fieldOf("log_price").forGetter(CurrencyRow::logPrice),
                Codec.DOUBLE.fieldOf("abs_return").forGetter(CurrencyRow::absReturn),
                Codec.DOUBLE.optionalFieldOf("anchor", Double.NaN).forGetter(CurrencyRow::anchor),
                Codec.DOUBLE.optionalFieldOf("fundamental", 0.0D).forGetter(CurrencyRow::fundamental),
                Codec.DOUBLE.optionalFieldOf("event", 0.0D).forGetter(CurrencyRow::event))
                .apply(instance, CurrencyRow::new));

        public static final Codec<CurrencyRow> CODEC = BASE.withAlternative(LegacyRow.CODEC, LegacyRow::toRow);

        /// Saves written before the log price carried that name, read so a world keeps its prices across the
        /// change; the row is the same otherwise.
        private record LegacyRow(String currency, double logValue, double absReturn, double anchor, double fundamental,
                double event) {

            static final Codec<LegacyRow> CODEC = RecordCodecBuilder.create((RecordCodecBuilder.Instance<LegacyRow> instance) -> instance.group(
                    Codec.STRING.fieldOf("currency").forGetter(LegacyRow::currency),
                    Codec.DOUBLE.fieldOf("log_value").forGetter(LegacyRow::logValue),
                    Codec.DOUBLE.fieldOf("abs_return").forGetter(LegacyRow::absReturn),
                    Codec.DOUBLE.optionalFieldOf("anchor", Double.NaN).forGetter(LegacyRow::anchor),
                    Codec.DOUBLE.optionalFieldOf("fundamental", 0.0D).forGetter(LegacyRow::fundamental),
                    Codec.DOUBLE.optionalFieldOf("event", 0.0D).forGetter(LegacyRow::event))
                    .apply(instance, LegacyRow::new));

            CurrencyRow toRow() {
                return new CurrencyRow(this.currency, this.logValue, this.absReturn, this.anchor, this.fundamental,
                        this.event);
            }
        }
    }

    /// Candle window of one pair and timeframe, oldest first, plus the candle that is still forming and the
    /// market tick it will close at.
    public record SeriesRow(String pair, String timeframe, List<Candle> closed, Optional<Candle> forming,
            long nextRollTick) {

        public static final Codec<SeriesRow> CODEC = RecordCodecBuilder.create((RecordCodecBuilder.Instance<SeriesRow> instance) -> instance.group(
                Codec.STRING.fieldOf("pair").forGetter(SeriesRow::pair),
                Codec.STRING.fieldOf("timeframe").forGetter(SeriesRow::timeframe),
                Candle.CODEC.listOf().fieldOf("closed").forGetter(SeriesRow::closed),
                Candle.CODEC.optionalFieldOf("forming").forGetter(SeriesRow::forming),
                Codec.LONG.optionalFieldOf("next_roll_tick", Long.MIN_VALUE).forGetter(SeriesRow::nextRollTick)).apply(instance, SeriesRow::new));
    }

    /// Balances are micro units of one item of the currency, and `debts` is the credit record of the account: one
    /// entry per currency whose balance is in the red, carrying the market tick the debt started at and the tick
    /// its next interest period ends at. A save written before the platform charged interest has no entry there,
    /// so its debts start being counted from the load. `subscription` is what the shop sold the account, absent
    /// in every save written before the shop existed.
    public record AccountRow(String owner, List<AmountRow> balances, List<AmountRow> realized, List<PositionRow> positions, long nextPositionId, long closedTrades, boolean opened, boolean tutorialSeen, boolean bonusPaid, String holder, int age, List<DebtRow> debts, Optional<SubscriptionRow> subscription) {

        public static final Codec<AccountRow> CODEC = RecordCodecBuilder.create((RecordCodecBuilder.Instance<AccountRow> instance) -> instance.group(
                Codec.STRING.fieldOf("owner").forGetter(AccountRow::owner),
                AmountRow.CODEC.listOf().fieldOf("balances").forGetter(AccountRow::balances),
                AmountRow.CODEC.listOf().fieldOf("realized").forGetter(AccountRow::realized),
                PositionRow.CODEC.listOf().fieldOf("positions").forGetter(AccountRow::positions),
                Codec.LONG.fieldOf("next_position_id").forGetter(AccountRow::nextPositionId),
                Codec.LONG.fieldOf("closed_trades").forGetter(AccountRow::closedTrades),
                Codec.BOOL.optionalFieldOf("opened", false).forGetter(AccountRow::opened),
                Codec.BOOL.optionalFieldOf("tutorial_seen", false).forGetter(AccountRow::tutorialSeen),
                Codec.BOOL.optionalFieldOf("welcome_paid", false).forGetter(AccountRow::bonusPaid),
                Codec.STRING.optionalFieldOf("holder", "").forGetter(AccountRow::holder),
                Codec.INT.optionalFieldOf("age", 0).forGetter(AccountRow::age),
                DebtRow.CODEC.listOf().optionalFieldOf("debts", List.of()).forGetter(AccountRow::debts),
                SubscriptionRow.CODEC.optionalFieldOf("subscription").forGetter(AccountRow::subscription)).apply(instance, AccountRow::new));
    }

    /// The subscription of one account: the plan it is on written by name rather than by ordinal, so a build that
    /// inserts a tier into the catalogue cannot turn one plan into another in the saves it reads - an id no plan
    /// carries comes back as no plan at all - the tick its period runs to, whether the platform charges another
    /// period when that one ends, the tick its lounge hands its next gift out at and what that lounge is holding,
    /// oldest first.
    public record SubscriptionRow(String plan, long until, boolean autoRenew, long loungeNext,
            List<GiftRow> lounge) {

        /// What an account that never bought a plan comes back to, which is also what a row without a
        /// subscription is read as.
        public static final SubscriptionRow NONE = new SubscriptionRow(Membership.NONE.id(), 0L, false, 0L, List.of());

        public static final Codec<SubscriptionRow> CODEC = RecordCodecBuilder.create((RecordCodecBuilder.Instance<SubscriptionRow> instance) -> instance.group(
                Codec.STRING.optionalFieldOf("plan", Membership.NONE.id()).forGetter(SubscriptionRow::plan),
                Codec.LONG.optionalFieldOf("until", 0L).forGetter(SubscriptionRow::until),
                Codec.BOOL.optionalFieldOf("auto_renew", false).forGetter(SubscriptionRow::autoRenew),
                Codec.LONG.optionalFieldOf("lounge_next", 0L).forGetter(SubscriptionRow::loungeNext),
                GiftRow.CODEC.listOf().optionalFieldOf("lounge", List.of()).forGetter(SubscriptionRow::lounge)).apply(instance, SubscriptionRow::new));
    }

    /// One gift the lounge of an account is holding, as the id of its item rather than as a built stack, so it
    /// survives a save and an id the registry no longer holds is dropped on load instead of restored as a broken
    /// stack. `potion` is the potion a potion stack carries, empty for every other item.
    public record GiftRow(String item, int count, Optional<String> potion) {

        public static final Codec<GiftRow> CODEC = RecordCodecBuilder.create((RecordCodecBuilder.Instance<GiftRow> instance) -> instance.group(
                Codec.STRING.fieldOf("item").forGetter(GiftRow::item),
                Codec.INT.optionalFieldOf("count", 1).forGetter(GiftRow::count),
                Codec.STRING.optionalFieldOf("potion").forGetter(GiftRow::potion)).apply(instance, GiftRow::new));
    }

    /// One currency the account owes, in micro units of that currency: the tick the debt started at, which is
    /// what the credit limit is measured from, and the tick the next interest period ends at.
    public record DebtRow(String currency, long since, long due) {

        public static final Codec<DebtRow> CODEC = RecordCodecBuilder.create((RecordCodecBuilder.Instance<DebtRow> instance) -> instance.group(
                Codec.STRING.fieldOf("currency").forGetter(DebtRow::currency),
                Codec.LONG.fieldOf("since").forGetter(DebtRow::since),
                Codec.LONG.fieldOf("due").forGetter(DebtRow::due)).apply(instance, DebtRow::new));
    }

    public record AmountRow(String currency, long micro) {

        public static final Codec<AmountRow> CODEC = RecordCodecBuilder.create((RecordCodecBuilder.Instance<AmountRow> instance) -> instance.group(
                Codec.STRING.fieldOf("currency").forGetter(AmountRow::currency),
                Codec.LONG.fieldOf("micro").forGetter(AmountRow::micro)).apply(instance, AmountRow::new));
    }

    /// One position of an account. `units` is its size in items of the base currency of its pair, which rows
    /// written before sizes were counted in items do not carry: those fall back to the base amount they hold,
    /// rounded to whole items. The margin, the swap and the profit of a position are micro units of the currency
    /// it named as its margin, which an empty `marginCurrency` leaves at the quote of its pair: that is what rows
    /// written before positions picked their own currency mean. `openMarketTick` is the monotonic market tick the
    /// position was opened at, whatever the field is called in older saves.
    public record PositionRow(long id, String pair, String side, int units, int leverage, double openPrice, double baseUnits, long marginMicro, long openMarketTick, long swapMicro, String marginCurrency) {

        public static final Codec<PositionRow> CODEC = RecordCodecBuilder.create((RecordCodecBuilder.Instance<PositionRow> instance) -> instance.group(
                Codec.LONG.fieldOf("id").forGetter(PositionRow::id),
                Codec.STRING.fieldOf("pair").forGetter(PositionRow::pair),
                Codec.STRING.fieldOf("side").forGetter(PositionRow::side),
                Codec.INT.optionalFieldOf("units", 0).forGetter(PositionRow::units),
                Codec.INT.fieldOf("leverage").forGetter(PositionRow::leverage),
                Codec.DOUBLE.fieldOf("open_price").forGetter(PositionRow::openPrice),
                Codec.DOUBLE.fieldOf("base_units").forGetter(PositionRow::baseUnits),
                Codec.LONG.fieldOf("margin").forGetter(PositionRow::marginMicro),
                Codec.LONG.fieldOf("open_market_tick").forGetter(PositionRow::openMarketTick),
                Codec.LONG.fieldOf("swap").forGetter(PositionRow::swapMicro),
                Codec.STRING.optionalFieldOf("margin_currency", "").forGetter(PositionRow::marginCurrency)).apply(instance, PositionRow::new));
    }
}
