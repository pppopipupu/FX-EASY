package com.pppopipupu.fxeasy.event;

import module java.base;

import com.pppopipupu.fxeasy.economy.EconomyConstants;
import com.pppopipupu.fxeasy.event.MarketEventDefinition.ImpactChannel;
import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;

/// The immutable table of every market event family. One family is announced with a fixed quality and prices
/// through exactly one [ImpactChannel]; a family that only reports or only jokes carries a zero impact.
///
/// Translation keys are derived from the definition id by the fixed rule `fxeasy.event.<id>.title` and
/// `fxeasy.event.<id>.body`, so a definition with id `raid_disruption` is rendered from
/// `fxeasy.event.raid_disruption.title` and `fxeasy.event.raid_disruption.body`. The language table is generated
/// from this catalog, never edited by hand per definition.
///
/// The table is ordered: [ALL] lists reports, then world events, then flavour events, and every selection the
/// engine makes walks that order, so a given seed and eligibility set always pick the same family.
public final class MarketEventCatalog {

    public static final String KEY_PREFIX = "fxeasy.event.";

    private static final long BUCKET = EconomyConstants.TICKS_PER_BUCKET;
    private static final long DAY = EconomyConstants.TICKS_PER_DAY;
    private static final long FIVE_DAYS = 5L * DAY;

    private static final long NEWS_ATTACK = 600L;
    private static final long NEWS_HOLD = 1200L;
    private static final long NEWS_RELEASE = 600L;

    private static final long SHORT_ATTACK = 1200L;
    private static final long SHORT_HOLD = 2400L;
    private static final long SHORT_RELEASE = 1200L;

    private static final long HALF_DAY_ATTACK = 3000L;
    private static final long HALF_DAY_HOLD = 6000L;
    private static final long HALF_DAY_RELEASE = 3000L;

    private static final long DAY_ATTACK = 6000L;
    private static final long DAY_HOLD = 12000L;
    private static final long DAY_RELEASE = 6000L;

    private static final long LONG_ATTACK = 9000L;
    private static final long LONG_HOLD = 18000L;
    private static final long LONG_RELEASE = 9000L;

    public static final MarketEventDefinition GOLD_INDUSTRIAL_OUTPUT = report("gold_industrial_output",
            ImpactChannel.SUPPLY);
    public static final MarketEventDefinition EMERALD_TRADE_ISSUANCE = report("emerald_trade_issuance",
            ImpactChannel.SUPPLY);
    public static final MarketEventDefinition TRADE_REABSORPTION = report("trade_reabsorption", ImpactChannel.USE);
    public static final MarketEventDefinition DIAMOND_MINING_BOOM = report("diamond_mining_boom",
            ImpactChannel.SUPPLY);
    public static final MarketEventDefinition NETHERITE_RESOURCE_WAVE = report("netherite_resource_wave",
            ImpactChannel.SUPPLY);
    public static final MarketEventDefinition BARTER_BOOM = report("barter_boom", ImpactChannel.USE);

    public static final MarketEventDefinition RAID_DISRUPTION = world("raid_disruption", ImpactChannel.LIQUIDITY,
            MarketEventQuality.ESTIMATED, HALF_DAY_ATTACK, HALF_DAY_HOLD, HALF_DAY_RELEASE, FIVE_DAYS,
            peaks("emerald", -0.005D));
    public static final MarketEventDefinition RAID_RECOVERY = world("raid_recovery", ImpactChannel.LIQUIDITY,
            MarketEventQuality.ESTIMATED, HALF_DAY_ATTACK, HALF_DAY_HOLD, HALF_DAY_RELEASE, FIVE_DAYS,
            peaks("emerald", 0.005D));
    public static final MarketEventDefinition TRADE_NETWORK_DAMAGE = world("trade_network_damage",
            ImpactChannel.LIQUIDITY, MarketEventQuality.CONFIRMED, DAY_ATTACK, DAY_HOLD, DAY_RELEASE, FIVE_DAYS,
            peaks("emerald", -0.005D));
    public static final MarketEventDefinition RECONSTRUCTION_DEMAND = world("reconstruction_demand",
            ImpactChannel.EXPECTATION, MarketEventQuality.ESTIMATED, LONG_ATTACK, LONG_HOLD, LONG_RELEASE, FIVE_DAYS,
            peaks("dirt", 0.0035D));
    public static final MarketEventDefinition DIMENSION_EXPANSION = world("dimension_expansion",
            ImpactChannel.EXPECTATION, MarketEventQuality.ESTIMATED, LONG_ATTACK, LONG_HOLD, LONG_RELEASE, 0L,
            peaks("netherite", -0.004D, "diamond", -0.004D));
    public static final MarketEventDefinition DRAGON_UNLOCK = world("dragon_unlock", ImpactChannel.EXPECTATION,
            MarketEventQuality.ESTIMATED, LONG_ATTACK, LONG_HOLD, LONG_RELEASE, 0L,
            peaks("diamond", 0.005D, "netherite", 0.005D));
    public static final MarketEventDefinition BOSS_CONFIDENCE = world("boss_confidence", ImpactChannel.NONE,
            MarketEventQuality.CONFIRMED, NEWS_ATTACK, NEWS_HOLD, NEWS_RELEASE, 0L, peaks());
    public static final MarketEventDefinition WEATHER_LOGISTICS = world("weather_logistics", ImpactChannel.LIQUIDITY,
            MarketEventQuality.CONFIRMED, DAY_ATTACK, DAY_HOLD, DAY_RELEASE, DAY, peaks());

    public static final MarketEventDefinition PIGLIN_FESTIVAL = flavour("piglin_festival", ImpactChannel.EXPECTATION,
            MarketEventQuality.RUMOR, peaks("gold", 0.004D));
    public static final MarketEventDefinition VILLAGER_TRADE_FAIR = flavour("villager_trade_fair",
            ImpactChannel.EXPECTATION, MarketEventQuality.RUMOR, peaks("emerald", 0.004D));
    public static final MarketEventDefinition END_SHIPPING_DELAY = flavour("end_shipping_delay", ImpactChannel.LIQUIDITY,
            MarketEventQuality.RUMOR, peaks("netherite", 0.003D, "diamond", 0.003D));
    public static final MarketEventDefinition SMITHING_ORDER_RUMOR = flavour("smithing_order_rumor",
            ImpactChannel.EXPECTATION, MarketEventQuality.RUMOR,
            peaks("gold", 0.004D, "diamond", 0.004D, "netherite", 0.004D));
    public static final MarketEventDefinition RESOURCE_DISCOVERY_RUMOR = flavour("resource_discovery_rumor",
            ImpactChannel.EXPECTATION, MarketEventQuality.RUMOR, peaks("diamond", -0.003D));
    public static final MarketEventDefinition GOLD_QUALITY_DISPUTE = flavour("gold_quality_dispute",
            ImpactChannel.EXPECTATION, MarketEventQuality.RUMOR, peaks("gold", -0.0035D));
    public static final MarketEventDefinition KURUMI_MARGIN_REVIEW = flavour("kurumi_margin_review",
            ImpactChannel.NONE, MarketEventQuality.ENTERTAINMENT, peaks());
    public static final MarketEventDefinition CLERIC_FORECAST = flavour("cleric_forecast", ImpactChannel.NONE,
            MarketEventQuality.ENTERTAINMENT, peaks());
    public static final MarketEventDefinition CREEPER_PRESS_RELEASE = flavour("creeper_press_release",
            ImpactChannel.NONE, MarketEventQuality.ENTERTAINMENT, peaks());
    public static final MarketEventDefinition ALLAY_RECEIPT_MIXUP = flavour("allay_receipt_mixup",
            ImpactChannel.NONE, MarketEventQuality.ENTERTAINMENT, peaks());

    /// Every family, in the order the engine walks when it selects one.
    public static final List<MarketEventDefinition> ALL = List.of(
            GOLD_INDUSTRIAL_OUTPUT, EMERALD_TRADE_ISSUANCE, TRADE_REABSORPTION, DIAMOND_MINING_BOOM,
            NETHERITE_RESOURCE_WAVE, BARTER_BOOM,
            RAID_DISRUPTION, RAID_RECOVERY, TRADE_NETWORK_DAMAGE, RECONSTRUCTION_DEMAND, DIMENSION_EXPANSION,
            DRAGON_UNLOCK, BOSS_CONFIDENCE, WEATHER_LOGISTICS,
            PIGLIN_FESTIVAL, VILLAGER_TRADE_FAIR, END_SHIPPING_DELAY, SMITHING_ORDER_RUMOR, RESOURCE_DISCOVERY_RUMOR,
            GOLD_QUALITY_DISPUTE, KURUMI_MARGIN_REVIEW, CLERIC_FORECAST, CREEPER_PRESS_RELEASE, ALLAY_RECEIPT_MIXUP);

    private static final Map<String, MarketEventDefinition> BY_ID = index();

    private MarketEventCatalog() {
    }

    /// The definition registered under `id`. Throws for an unknown id, so a persisted event that names a family
    /// the table no longer has fails loudly instead of being silently dropped.
    public static MarketEventDefinition byId(String id) {
        MarketEventDefinition definition = BY_ID.get(id);
        if (definition == null) {
            throw new IllegalArgumentException("unknown market event definition " + id);
        }
        return definition;
    }

    public static Optional<MarketEventDefinition> find(String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    /// Every family of one kind, in table order.
    public static List<MarketEventDefinition> byKind(MarketEventKind kind) {
        List<MarketEventDefinition> matches = new ArrayList<>();
        for (MarketEventDefinition definition : ALL) {
            if (definition.kind() == kind) {
                matches.add(definition);
            }
        }
        return List.copyOf(matches);
    }

    private static MarketEventDefinition report(String id, ImpactChannel channel) {
        return define(id, MarketEventKind.OBSERVED_REPORT, MarketEventQuality.CONFIRMED, channel, NEWS_ATTACK,
                NEWS_HOLD, NEWS_RELEASE, BUCKET, peaks());
    }

    private static MarketEventDefinition world(String id, ImpactChannel channel, MarketEventQuality quality,
            long attack, long hold, long release, long cooldown, double[] peakImpact) {
        return define(id, MarketEventKind.WORLD_EVENT, quality, channel, attack, hold, release, cooldown, peakImpact);
    }

    private static MarketEventDefinition flavour(String id, ImpactChannel channel, MarketEventQuality quality,
            double[] peakImpact) {
        return define(id, MarketEventKind.FLAVOR_EVENT, quality, channel, SHORT_ATTACK, SHORT_HOLD, SHORT_RELEASE,
                DAY, peakImpact);
    }

    private static MarketEventDefinition define(String id, MarketEventKind kind, MarketEventQuality quality,
            ImpactChannel channel, long attack, long hold, long release, long cooldown, double[] peakImpact) {
        double[] weight = new double[Currencies.count()];
        Arrays.fill(weight, 1.0D);
        return new MarketEventDefinition(id, kind, quality, channel, attack, hold, release, cooldown, weight,
                peakImpact, KEY_PREFIX + id + ".title", KEY_PREFIX + id + ".body");
    }

    private static double[] peaks() {
        return new double[Currencies.count()];
    }

    /// A definition prices the id it names; a world that does not trade it simply never pays that impact.
    private static double[] peaks(String id, double value) {
        double[] impact = peaks();
        Currency currency = Currencies.byId(id);
        if (currency != null) {
            impact[Currencies.index(currency)] = value;
        }
        return impact;
    }

    private static double[] peaks(String first, double firstValue, String second, double secondValue) {
        double[] impact = peaks(first, firstValue);
        Currency currency = Currencies.byId(second);
        if (currency != null) {
            impact[Currencies.index(currency)] = secondValue;
        }
        return impact;
    }

    private static double[] peaks(String first, double firstValue, String second, double secondValue,
            String third, double thirdValue) {
        double[] impact = peaks(first, firstValue, second, secondValue);
        Currency currency = Currencies.byId(third);
        if (currency != null) {
            impact[Currencies.index(currency)] = thirdValue;
        }
        return impact;
    }

    private static Map<String, MarketEventDefinition> index() {
        Map<String, MarketEventDefinition> map = new LinkedHashMap<>();
        for (MarketEventDefinition definition : ALL) {
            if (map.put(definition.id(), definition) != null) {
                throw new IllegalStateException("duplicate market event definition " + definition.id());
            }
        }
        return Map.copyOf(map);
    }
}
