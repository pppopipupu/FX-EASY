package com.pppopipupu.fxeasy;

import module java.base;

import com.pppopipupu.fxeasy.economy.EconomyConstants;
import com.pppopipupu.fxeasy.economy.EconomySourceKind;
import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;
import com.pppopipupu.fxeasy.market.MarketEngine;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/// Server configuration of the world economy and the market event layer. Every value is read through a cached
/// snapshot, so the per tick path never touches the config system, and a change only ever takes effect at the
/// next economy bucket: a live market cannot be retuned by editing a file mid bucket.
///
/// The config file lives in the world's `serverconfig` directory, which is what makes it per world rather than
/// per installation.
public final class FxConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue FLAVOR_EVENTS_ENABLED = BUILDER
            .comment("Whether the small independent flavour events may fire. Off keeps the world events and the",
                    "production reports, and turns the market news feed into a quiet one.")
            .define("events.flavor_enabled", true);
    private static final ModConfigSpec.IntValue EVENT_CANDIDATE_INTERVAL = BUILDER
            .comment("Market ticks between two flavour event candidate rolls. One economic day is 24000 ticks.")
            .defineInRange("events.candidate_interval", EconomyConstants.TICKS_PER_DAY, 600, 20 * EconomyConstants.TICKS_PER_DAY);
    private static final ModConfigSpec.DoubleValue EVENT_BASE_CHANCE = BUILDER
            .comment("Chance that an eligible flavour event family fires on a candidate roll.")
            .defineInRange("events.base_chance", 0.35D, 0.0D, 1.0D);
    private static final ModConfigSpec.IntValue MAX_PRICED_EVENTS = BUILDER
            .comment("Most events that may move a price at the same time.")
            .defineInRange("events.max_priced", 3, 0, 8);

    private static final ModConfigSpec.BooleanValue ACCOUNT_QUOTA_ENABLED = BUILDER
            .comment("Whether one account is limited in how much risk it may add on one currency.")
            .define("quota.account_enabled", true);
    private static final ModConfigSpec.BooleanValue SERVER_QUOTA_ENABLED = BUILDER
            .comment("Whether every account together is limited on one currency. A single player world may turn",
                    "this off: the global cap can otherwise block the only trader in the world.")
            .define("quota.server_enabled", true);
    private static final ModConfigSpec.DoubleValue ACCOUNT_QUOTA_DAYS = BUILDER
            .comment("An account may hold this many economic days of one currency as absolute risk, measured at",
                    "the reference value of that currency, not at whatever it is worth after a crash.")
            .defineInRange("quota.account_days", 0.5D, 0.05D, 8.0D);
    private static final ModConfigSpec.DoubleValue SERVER_QUOTA_DAYS = BUILDER
            .comment("Every account together may hold this many economic days of one currency as absolute risk.")
            .defineInRange("quota.server_days", 4.0D, 0.5D, 64.0D);

    private static final ModConfigSpec.BooleanValue COLLECT_ENABLED = BUILDER
            .comment("Master switch for the committed fact collector. Off means the market prices nothing the world",
                    "does, which is the same as an economy with no production at all.")
            .define("collect.enabled", true);
    private static final ModConfigSpec.BooleanValue ORE_MARKERS_ENABLED = BUILDER
            .comment("Whether placed ore and resource blocks are remembered per chunk, so a block that was put",
                    "back by a player is never counted as a natural find.")
            .define("collect.ore_markers", true);
    private static final ModConfigSpec.IntValue ORE_MARKER_BUDGET = BUILDER
            .comment("Most marked positions one chunk may hold. A chunk over budget stops counting as a natural",
                    "source instead of silently forgetting its marks.")
            .defineInRange("collect.ore_marker_budget", 512, 16, 65536);
    private static final ModConfigSpec.IntValue TRADE_REGION_LIMIT = BUILDER
            .comment("How many recently traded regions the market remembers, oldest trade evicted first.")
            .defineInRange("collect.trade_regions", 1024, 16, 65536);
    private static final ModConfigSpec.ConfigValue<List<? extends String>> ENABLED_SOURCES = BUILDER
            .comment("Which committed fact sources are collected, by id. Removing a source stops the market from",
                    "seeing it at all; the terminal reports the coverage it actually has.")
            .defineList("collect.sources", defaultSourceIds(), FxConfig::isSourceId);

    private static final ModConfigSpec.ConfigValue<String> WELCOME_CURRENCY = BUILDER
            .comment("Currency the welcome capital is paid in, by id. A world that does not trade that currency",
                    "pays the first one it can hand over instead.")
            .define("market.welcome_currency", "diamond");
    private static final ModConfigSpec.LongValue WELCOME_ITEMS = BUILDER
            .comment("Items of that currency a new account is handed once.")
            .defineInRange("market.welcome_items", 10L, 0L, 1_000_000L);
    private static final ModConfigSpec.DoubleValue VOLATILITY_SCALE = BUILDER
            .comment("How far the rates of this world swing, as a multiple of the calibration the market ships",
                    "with. It multiplies the daily volatility of every currency before anything reads it, so the",
                    "noise and the reference the volatility regime is measured against move together: a world",
                    "tuned higher trades a wilder market without the platform reading it as a nervous one.",
                    "Read when a world loads, never on the tick path.")
            .defineInRange("market.volatility_scale", MarketEngine.DEFAULT_VOLATILITY_SCALE, 0.1D, 8.0D);

    private static final ModConfigSpec SPEC = BUILDER.build();

    private static volatile Snapshot cache = Snapshot.defaults();

    private FxConfig() {
    }

    public static ModConfigSpec spec() {
        return SPEC;
    }

    /// Registers the config and the reload hook. Called once from the mod constructor. The cached values start
    /// at the defaults, because the file is only read later: a value cannot be asked for before the config is
    /// loaded, and the mod constructor is allowed to touch a config backed code path.
    public static void register(ModContainer container) {
        cache = Snapshot.defaults();
        container.registerConfig(ModConfig.Type.SERVER, SPEC);
    }

    /// Refreshes the cached values. Called when the config loads and whenever it is reloaded, so a running
    /// server never reads a half applied file.
    public static void reload() {
        cache = new Snapshot(
                FLAVOR_EVENTS_ENABLED.get(),
                EVENT_CANDIDATE_INTERVAL.get(),
                EVENT_BASE_CHANCE.get(),
                MAX_PRICED_EVENTS.get(),
                ACCOUNT_QUOTA_ENABLED.get(),
                SERVER_QUOTA_ENABLED.get(),
                ACCOUNT_QUOTA_DAYS.get(),
                SERVER_QUOTA_DAYS.get(),
                COLLECT_ENABLED.get(),
                ORE_MARKERS_ENABLED.get(),
                ORE_MARKER_BUDGET.get(),
                TRADE_REGION_LIMIT.get(),
                WELCOME_CURRENCY.get(),
                WELCOME_ITEMS.get(),
                VOLATILITY_SCALE.get(),
                sourceMask(ENABLED_SOURCES.get()));
    }

    /// Drops the cached values back to the defaults. Called when the config is unloaded: once the file is gone
    /// its values cannot be read, and the next world must not start on the tuning of the last one.
    public static void reset() {
        cache = Snapshot.defaults();
    }

    public static Snapshot get() {
        return cache;
    }

    private static List<String> defaultSourceIds() {
        List<String> ids = new ArrayList<>(EconomySourceKind.COUNT);
        for (EconomySourceKind kind : EconomySourceKind.ALL) {
            ids.add(kind.id());
        }
        return List.copyOf(ids);
    }

    private static boolean isSourceId(Object value) {
        if (!(value instanceof String id)) {
            return false;
        }
        for (EconomySourceKind kind : EconomySourceKind.ALL) {
            if (kind.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static boolean[] sourceMask(List<? extends String> ids) {
        boolean[] mask = new boolean[EconomySourceKind.COUNT];
        for (String id : ids) {
            for (EconomySourceKind kind : EconomySourceKind.ALL) {
                if (kind.id().equals(id)) {
                    mask[kind.ordinal()] = true;
                }
            }
        }
        return mask;
    }

    /// Cached values, one instance per config state. The arrays are never handed out for mutation.
    public record Snapshot(boolean flavorEventsEnabled, int eventCandidateInterval,
            double eventBaseChance, int maxPricedEvents, boolean accountQuotaEnabled,
            boolean serverQuotaEnabled, double accountQuotaDays, double serverQuotaDays, boolean collectEnabled,
            boolean oreMarkersEnabled, int oreMarkerBudget, int tradeRegionLimit, String welcomeCurrencyId,
            long welcomeItems, double volatilityScale, boolean[] enabledSources) {

        public Snapshot {
            enabledSources = enabledSources.clone();
            if (enabledSources.length != EconomySourceKind.COUNT) {
                throw new IllegalArgumentException("enabled source mask needs one entry per source kind");
            }
        }

        public static Snapshot defaults() {
            boolean[] mask = new boolean[EconomySourceKind.COUNT];
            Arrays.fill(mask, true);
            return new Snapshot(true, EconomyConstants.TICKS_PER_DAY, 0.35D, 3, true,
                    true, 0.5D, 4.0D, true, true, 512, 1024, "diamond", 10L,
                    MarketEngine.DEFAULT_VOLATILITY_SCALE, mask);
        }

        public boolean collects(EconomySourceKind kind) {
            return this.collectEnabled && this.enabledSources[kind.ordinal()];
        }

        /// Currency the welcome capital is paid in: the one the configuration names, or the first currency of
        /// the world that an item backs when it names a currency this world does not trade.
        public Currency welcomeCurrency() {
            Currency named = Currencies.byId(this.welcomeCurrencyId);
            if (named != null) {
                return named;
            }
            for (Currency currency : Currencies.all()) {
                if (currency.backed()) {
                    return currency;
                }
            }
            return Currencies.byIndex(0);
        }

        /// Absolute risk one account may add on one currency, in items of that same currency: risk is a size in
        /// the currency it is carried in, so no price ever enters this limit.
        public double accountRiskLimit(Currency currency) {
            return this.accountQuotaEnabled ? this.accountQuotaDays * currency.capacity() : Double.POSITIVE_INFINITY;
        }

        /// Absolute risk every account together may add on one currency, in items of that same currency.
        public double serverRiskLimit(Currency currency) {
            return this.serverQuotaEnabled ? this.serverQuotaDays * currency.capacity() : Double.POSITIVE_INFINITY;
        }
    }
}
