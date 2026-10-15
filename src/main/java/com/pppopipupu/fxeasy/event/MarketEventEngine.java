package com.pppopipupu.fxeasy.event;

import module java.base;

import com.pppopipupu.fxeasy.FxConfig;
import com.pppopipupu.fxeasy.economy.EconomySourceKind;
import com.pppopipupu.fxeasy.economy.WorldEconomyState;
import com.pppopipupu.fxeasy.event.MarketEventDefinition.ImpactChannel;
import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;
import com.pppopipupu.fxeasy.market.MarketDriverFrame;
import com.pppopipupu.fxeasy.market.MarketEngine;
import com.pppopipupu.fxeasy.market.Rng;
import com.pppopipupu.fxeasy.net.FxEventsPayload;
import com.pppopipupu.fxeasy.storage.EventRow;

/// Server side owner of the running market events and their family cooldowns. It is restored from an [EventRow]
/// and snapshotted back into one; every decision it makes is a pure function of that persisted state, the world
/// facts the collector recorded through [#observe], the economy readings and the one event generator the service
/// hands [#tick]. Nothing here is re-rolled on load: the curve of a running event is a function of its start tick
/// and its definition, so a save in the middle of an event resumes on the same tick of the same curve.
///
/// The engine only ever prices a channel it is allowed to: a report of a fact the world already committed, and
/// every news only event, contribute exactly zero to the rate request and to the risk floor. The rate request is
/// per market tick, so the whole target of an event is applied exactly once across its lifetime and the economy's
/// shared bucket budget is never widened by an event.
public final class MarketEventEngine {

    /// Risk floor per log unit of announced impact. A two percent event raises the floor to three times the calm
    /// regime, and the floor never exceeds the market's own ceiling.
    public static final double RISK_PER_LOG = 100.0D;

    private static final int MIN_TRADE_DEATHS = 3;
    private static final double DEFAULT_CONFIRM_CHANCE = 0.6D;
    private static final String NETHER = "minecraft:the_nether";
    private static final String END = "minecraft:the_end";

    private static final Map<String, List<EconomySourceKind>> TRIGGER_KINDS = triggerKinds();

    private final List<MarketEventDefinition> catalog;
    private final Map<String, MarketEventDefinition> definitions;
    private final List<MarketEventState> running = new ArrayList<>();
    private final List<MarketEventState> archive = new ArrayList<>();
    private final Map<String, EventRow.CooldownRow> cooldowns = new HashMap<>();
    private final Set<String> milestones = new HashSet<>();
    private final MarketActivityWindow activity = new MarketActivityWindow();
    private final double[] request = new double[Currencies.count()];
    private final List<MarketEventState> scratch = new ArrayList<>();

    private long revision;
    private long lastCandidateTick = Long.MIN_VALUE;
    private double confirmChance = DEFAULT_CONFIRM_CHANCE;
    private Integer candidateIntervalOverride;
    private Double candidateChanceOverride;
    private Integer maxPricedOverride;
    private Boolean flavorOverride;

    MarketEventEngine(List<MarketEventDefinition> catalog) {
        this.catalog = List.copyOf(catalog);
        Map<String, MarketEventDefinition> map = new LinkedHashMap<>();
        for (MarketEventDefinition definition : this.catalog) {
            map.put(definition.id(), definition);
        }
        this.definitions = Map.copyOf(map);
    }

    public static MarketEventEngine restore(EventRow row) {
        MarketEventEngine engine = new MarketEventEngine(MarketEventCatalog.ALL);
        engine.load(row);
        return engine;
    }

    private void load(EventRow row) {
        this.running.clear();
        this.archive.clear();
        this.cooldowns.clear();
        this.milestones.clear();
        for (MarketEventState state : row.events()) {
            if (!this.definitions.containsKey(state.definitionId())) {
                continue;
            }
            if (state.archived()) {
                this.archive.add(state);
            } else {
                this.running.add(state);
            }
        }
        for (EventRow.CooldownRow cooldown : row.cooldowns()) {
            this.cooldowns.put(cooldown.family(), cooldown);
        }
        this.milestones.addAll(row.milestones());
        this.revision = row.revision();
    }

    /// Records one committed world fact. Any recorded fact is accepted; the engine only reacts to the kinds it
    /// knows, so the tracker may forward every fact it sees.
    public void observe(WorldActivity fact) {
        this.activity.observe(fact);
    }

    /// One market tick. Advances every running event, starts the world events and reports the recorded facts
    /// justify, rolls the flavour candidates on their interval, and resolves the rumours whose resolution tick
    /// has arrived. Only `eventRng` is consumed, and only for scheduling and for outcomes that are published.
    public void tick(long marketTick, WorldEconomyState economy, Rng eventRng) {
        prune(marketTick);
        advance(marketTick);
        startWorldEvents(marketTick, economy);
        rollFlavour(marketTick, economy, eventRng);
        resolveRumours(marketTick, eventRng);
    }

    /// The per tick log rate request this tick prepared, one entry per currency in [Currencies] order. The
    /// caller hands it straight to [WorldEconomyState#tick], which is what keeps the event contribution inside
    /// the shared bucket budget. Fills `out` when it is non null and returns the array that holds the values.
    public double[] rateRequest(double[] out) {
        double[] target = out == null ? this.request : out;
        if (target.length != Currencies.count()) {
            throw new IllegalArgumentException("rate request needs one entry per currency");
        }
        System.arraycopy(this.request, 0, target, 0, Currencies.count());
        return target;
    }

    /// The bounded public view of the layer: running events and the newest archived ones, newest first, with the
    /// applied-so-far and the target of each event per currency. No unpublished outcome is ever exposed.
    public List<FxEventsPayload.Event> publish(long marketTick) {
        List<MarketEventState> states = new ArrayList<>(this.running.size() + this.archive.size());
        states.addAll(this.running);
        states.addAll(this.archive);
        states.sort(Comparator.comparingLong(MarketEventState::announcementTick).reversed());
        if (states.size() > FxEventsPayload.MAX_EVENTS) {
            states = states.subList(0, FxEventsPayload.MAX_EVENTS);
        }
        List<FxEventsPayload.Event> messages = new ArrayList<>(states.size());
        for (MarketEventState state : states) {
            messages.add(message(state, marketTick));
        }
        return List.copyOf(messages);
    }

    /// The risk floor the announced events justify, never below the calm regime and never above the market
    /// ceiling. A report or a news only event contributes exactly nothing.
    public double riskFloor() {
        double largest = 0.0D;
        for (MarketEventState state : this.running) {
            MarketEventDefinition definition = this.definitions.get(state.definitionId());
            if (definition == null || !MarketEventRules.prices(definition)) {
                continue;
            }
            largest = Math.max(largest, state.peakMagnitude(definition));
        }
        double floor = MarketDriverFrame.NO_RISK_FLOOR + largest * RISK_PER_LOG;
        return Math.clamp(floor, MarketDriverFrame.NO_RISK_FLOOR, MarketEngine.REGIME_CEILING);
    }

    public long revision() {
        return this.revision;
    }

    public EventRow snapshot() {
        List<MarketEventState> events = new ArrayList<>(this.running.size() + this.archive.size());
        int runningFrom = Math.max(0, this.running.size() - EventRow.HISTORY_CAPACITY);
        events.addAll(this.running.subList(runningFrom, this.running.size()));
        int room = Math.max(0, EventRow.HISTORY_CAPACITY - events.size());
        int from = Math.max(0, this.archive.size() - room);
        events.addAll(this.archive.subList(from, this.archive.size()));
        return new EventRow(events, List.copyOf(this.cooldowns.values()), List.copyOf(this.milestones),
                this.revision);
    }

    private void advance(long marketTick) {
        Arrays.fill(this.request, 0.0D);
        this.scratch.clear();
        for (MarketEventState state : this.running) {
            MarketEventDefinition definition = this.definitions.get(state.definitionId());
            if (definition == null) {
                continue;
            }
            double nowFactor = MarketEventRules.factor(definition, state, marketTick);
            double beforeFactor = MarketEventRules.factor(definition, state, marketTick - 1L);
            double applied = 0.0D;
            for (Currency currency : Currencies.all()) {
                int index = Currencies.index(currency);
                double impact = definition.peakImpact(currency);
                this.request[index] += impact * (nowFactor - beforeFactor);
                applied += Math.abs(impact * nowFactor);
            }
            MarketEventState updated = new MarketEventState(state.definitionId(), state.announcementTick(),
                    state.startTick(), state.appliedPeakTick(), state.endTick(), applied, state.targetLog(),
                    state.archived(), state.retired());
            if (marketTick >= state.endTick()) {
                archive(updated);
                startCooldown(definition, state.endTick());
                this.revision++;
            } else {
                this.scratch.add(updated);
            }
        }
        this.running.clear();
        this.running.addAll(this.scratch);
    }

    private void startWorldEvents(long marketTick, WorldEconomyState economy) {
        for (MarketEventDefinition definition : this.catalog) {
            if (definition.kind() != MarketEventKind.WORLD_EVENT
                    && definition.kind() != MarketEventKind.OBSERVED_REPORT) {
                continue;
            }
            if (isRunning(definition.id()) || !cooldownReady(definition.id(), marketTick)) {
                continue;
            }
            String milestone = milestoneKey(definition, marketTick);
            if (MarketEventRules.gated(definition) && milestone == null) {
                continue;
            }
            if (!justified(definition, marketTick, economy)) {
                continue;
            }
            if (MarketEventRules.prices(definition) && pricedCount() >= maxPriced()) {
                continue;
            }
            start(definition, marketTick, milestone);
            consume(definition);
        }
    }

    private void rollFlavour(long marketTick, WorldEconomyState economy, Rng eventRng) {
        if (!flavorEnabled()) {
            return;
        }
        if (this.lastCandidateTick != Long.MIN_VALUE && marketTick - this.lastCandidateTick < candidateInterval()) {
            return;
        }
        this.lastCandidateTick = marketTick;
        if (!eventRng.nextChance(candidateChance())) {
            return;
        }
        List<MarketEventDefinition> eligible = new ArrayList<>();
        int priced = pricedCount();
        int limit = maxPriced();
        for (MarketEventDefinition definition : this.catalog) {
            if (definition.kind() != MarketEventKind.FLAVOR_EVENT) {
                continue;
            }
            if (isRunning(definition.id()) || !cooldownReady(definition.id(), marketTick)) {
                continue;
            }
            if (!flavourEligible(definition, marketTick, economy)) {
                continue;
            }
            if (MarketEventRules.prices(definition) && priced >= limit) {
                continue;
            }
            eligible.add(definition);
        }
        if (eligible.isEmpty()) {
            return;
        }
        int index = (int) (eventRng.nextDouble() * eligible.size());
        start(eligible.get(index), marketTick, null);
    }

    private void resolveRumours(long marketTick, Rng eventRng) {
        this.scratch.clear();
        boolean changed = false;
        for (MarketEventState state : this.running) {
            MarketEventDefinition definition = this.definitions.get(state.definitionId());
            if (definition == null || definition.quality() != MarketEventQuality.RUMOR
                    || MarketEventRules.resolved(state)
                    || marketTick < MarketEventRules.resolveTick(definition, state)) {
                this.scratch.add(state);
                continue;
            }
            boolean confirmed = eventRng.nextChance(this.confirmChance);
            long resolve = MarketEventRules.resolveTick(definition, state);
            double target = confirmed ? state.targetLog() : -Math.abs(state.targetLog());
            this.scratch.add(new MarketEventState(state.definitionId(), state.announcementTick(), state.startTick(),
                    resolve, state.endTick(), state.appliedLog(), target, false, false));
            changed = true;
        }
        if (changed) {
            this.running.clear();
            this.running.addAll(this.scratch);
            this.revision++;
        }
    }

    private boolean justified(MarketEventDefinition definition, long marketTick, WorldEconomyState economy) {
        String id = definition.id();
        long since = this.activity.windowStart(marketTick);
        if (id.equals("trade_network_damage")) {
            return this.activity.countRecent(EconomySourceKind.TRADE_VILLAGER_DIED, since) >= MIN_TRADE_DEATHS;
        }
        if (id.equals("dimension_expansion")) {
            return this.activity.nextUnmilestonedDimension(since, this.milestones) != null;
        }
        if (id.equals("diamond_mining_boom")) {
            Currency diamond = Currencies.byId("diamond");
            return diamond != null && this.activity.hasRecent(EconomySourceKind.NATURAL_ORE, since, diamond)
                    && economy.supplyEwma(diamond) >= 1.0D;
        }
        if (id.equals("netherite_resource_wave")) {
            Currency netherite = Currencies.byId("netherite");
            return netherite != null && this.activity.hasRecent(EconomySourceKind.NATURAL_ORE, since, netherite);
        }
        for (EconomySourceKind kind : TRIGGER_KINDS.getOrDefault(id, List.of())) {
            if (this.activity.hasRecent(kind, since, null)) {
                return true;
            }
        }
        return false;
    }

    private boolean flavourEligible(MarketEventDefinition definition, long marketTick, WorldEconomyState economy) {
        long since = this.activity.windowStart(marketTick);
        return switch (definition.id()) {
            case "piglin_festival" -> this.activity.containsDimension(NETHER)
                    && this.activity.hasRecent(EconomySourceKind.BARTER_TERMINAL_USE, since, null);
            case "villager_trade_fair" -> this.activity.hasRecent(EconomySourceKind.VILLAGER_PAYS_EMERALD, since, null)
                    || this.activity.hasRecent(EconomySourceKind.VILLAGER_REABSORB, since, null);
            case "end_shipping_delay" -> this.activity.containsDimension(END);
            case "smithing_order_rumor" -> this.activity.hasRecent(EconomySourceKind.SMITHING_TERMINAL_USE, since, null)
                    || this.activity.hasRecent(EconomySourceKind.CRAFT_TERMINAL_USE, since, null);
            case "resource_discovery_rumor" -> this.activity.hasAnyDimension()
                    && this.activity.hasRecent(EconomySourceKind.NATURAL_ORE, since, null);
            case "gold_quality_dispute" -> this.activity.hasRecent(EconomySourceKind.PIGLIN_GOLD, since, null);
            case "kurumi_margin_review" -> hasPressure(economy);
            case "cleric_forecast" -> this.activity.hasRecent(EconomySourceKind.VILLAGER_PAYS_EMERALD, since, null)
                    || this.activity.hasRecent(EconomySourceKind.VILLAGER_PAYS_CURRENCY, since, null)
                    || this.activity.hasRecent(EconomySourceKind.VILLAGER_REABSORB, since, null);
            case "creeper_press_release" -> true;
            case "allay_receipt_mixup" -> true;
            default -> false;
        };
    }

    private void start(MarketEventDefinition definition, long marketTick, String milestone) {
        this.running.add(MarketEventState.announced(definition, marketTick));
        if (milestone != null) {
            this.milestones.add(milestone);
        }
        this.revision++;
    }

    private void archive(MarketEventState state) {
        this.archive.add(state.archive());
        while (this.archive.size() > EventRow.HISTORY_CAPACITY) {
            this.archive.removeFirst();
        }
    }

    private void startCooldown(MarketEventDefinition definition, long endTick) {
        EventRow.CooldownRow existing = this.cooldowns.get(definition.id());
        int runs = existing == null ? 0 : existing.runs();
        this.cooldowns.put(definition.id(),
                new EventRow.CooldownRow(definition.id(), endTick + definition.cooldownTicks(), runs + 1));
    }

    private void prune(long marketTick) {
        this.activity.prune(marketTick);
        long cutoff = marketTick - EventRow.COOLDOWN_RETENTION_TICKS;
        this.cooldowns.entrySet().removeIf(entry -> entry.getValue().readyTick() < cutoff);
    }

    private void consume(MarketEventDefinition definition) {
        String id = definition.id();
        if (MarketEventRules.gated(definition)) {
            return;
        }
        List<EconomySourceKind> kinds = TRIGGER_KINDS.getOrDefault(id, List.of());
        if (kinds.isEmpty()) {
            return;
        }
        if (id.equals("diamond_mining_boom")) {
            Currency diamond = Currencies.byId("diamond");
            if (diamond != null) {
                this.activity.removeIf(fact -> fact.kind() == EconomySourceKind.NATURAL_ORE
                        && diamond.equals(fact.currency()));
            }
            return;
        }
        if (id.equals("netherite_resource_wave")) {
            Currency netherite = Currencies.byId("netherite");
            if (netherite != null) {
                this.activity.removeIf(fact -> fact.kind() == EconomySourceKind.NATURAL_ORE
                        && netherite.equals(fact.currency()));
            }
            return;
        }
        this.activity.removeIf(fact -> kinds.contains(fact.kind()));
    }

    private boolean hasPressure(WorldEconomyState economy) {
        for (Currency currency : Currencies.all()) {
            if (Math.abs(economy.pressure(currency)) > 1.0E-9D) {
                return true;
            }
        }
        return false;
    }

    private int pricedCount() {
        int count = 0;
        for (MarketEventState state : this.running) {
            MarketEventDefinition definition = this.definitions.get(state.definitionId());
            if (definition != null && MarketEventRules.prices(definition)) {
                count++;
            }
        }
        return count;
    }

    private boolean cooldownReady(String family, long marketTick) {
        EventRow.CooldownRow cooldown = this.cooldowns.get(family);
        return cooldown == null || marketTick >= cooldown.readyTick();
    }

    boolean isRunning(String id) {
        for (MarketEventState state : this.running) {
            if (state.definitionId().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private String milestoneKey(MarketEventDefinition definition, long marketTick) {
        return switch (definition.id()) {
            case "dimension_expansion" -> {
                String dimension = this.activity.nextUnmilestonedDimension(
                        this.activity.windowStart(marketTick), this.milestones);
                yield dimension == null ? null : "dimension:" + dimension;
            }
            case "dragon_unlock" -> this.milestones.contains("dragon_defeated") ? null : "dragon_defeated";
            case "boss_confidence" -> this.milestones.contains("boss_confidence") ? null : "boss_confidence";
            default -> null;
        };
    }

    private FxEventsPayload.Event message(MarketEventState state, long marketTick) {
        MarketEventDefinition definition = this.definitions.get(state.definitionId());
        double[] applied = new double[Currencies.count()];
        double[] peak = new double[Currencies.count()];
        if (definition != null) {
            long tick = state.archived() ? state.endTick() : marketTick;
            for (Currency currency : Currencies.all()) {
                int index = Currencies.index(currency);
                applied[index] = definition.peakImpact(currency) * MarketEventRules.factor(definition, state, tick);
                peak[index] = definition.peakImpact(currency)
                        * (MarketEventRules.denied(state)
                                ? MarketEventState.phaseCurve(definition,
                                        MarketEventRules.resolveTick(definition, state) - state.startTick())
                                : 1.0D);
            }
        }
        String id = definition == null ? state.definitionId() : definition.id();
        int kind = definition == null ? MarketEventKind.OBSERVED_REPORT.ordinal() : definition.kind().ordinal();
        int quality = definition == null ? MarketEventQuality.CONFIRMED.ordinal() : definition.quality().ordinal();
        int channel = definition == null ? ImpactChannel.NONE.ordinal() : definition.channel().ordinal();
        String titleKey = definition == null ? MarketEventCatalog.KEY_PREFIX + id + ".title" : definition.titleKey();
        String bodyKey = definition == null ? MarketEventCatalog.KEY_PREFIX + id + ".body" : definition.bodyKey();
        return new FxEventsPayload.Event(id + "@" + state.announcementTick(), id, kind, quality, channel,
                this.revision, state.announcementTick(), state.endTick(), applied, peak, titleKey, bodyKey,
                state.archived());
    }

    private boolean flavorEnabled() {
        return this.flavorOverride != null ? this.flavorOverride : FxConfig.get().flavorEventsEnabled();
    }

    private int candidateInterval() {
        return this.candidateIntervalOverride != null ? this.candidateIntervalOverride
                : FxConfig.get().eventCandidateInterval();
    }

    private double candidateChance() {
        return this.candidateChanceOverride != null ? this.candidateChanceOverride
                : FxConfig.get().eventBaseChance();
    }

    private int maxPriced() {
        return this.maxPricedOverride != null ? this.maxPricedOverride : FxConfig.get().maxPricedEvents();
    }

    void tuneForTest(int candidateInterval, double candidateChance, int maxPriced) {
        this.candidateIntervalOverride = candidateInterval;
        this.candidateChanceOverride = candidateChance;
        this.maxPricedOverride = maxPriced;
        this.flavorOverride = Boolean.TRUE;
    }

    void rumorConfirmChance(double chance) {
        this.confirmChance = chance;
    }

    void begin(MarketEventDefinition definition, long marketTick) {
        if (this.definitions.containsKey(definition.id())) {
            start(definition, marketTick, null);
        }
    }

    private static Map<String, List<EconomySourceKind>> triggerKinds() {
        Map<String, List<EconomySourceKind>> map = new HashMap<>();
        map.put("gold_industrial_output", List.of(EconomySourceKind.PIGLIN_GOLD));
        map.put("emerald_trade_issuance", List.of(EconomySourceKind.VILLAGER_PAYS_EMERALD));
        map.put("trade_reabsorption", List.of(EconomySourceKind.VILLAGER_REABSORB));
        map.put("diamond_mining_boom", List.of(EconomySourceKind.NATURAL_ORE));
        map.put("netherite_resource_wave", List.of(EconomySourceKind.NATURAL_ORE));
        map.put("barter_boom", List.of(EconomySourceKind.BARTER_TERMINAL_USE));
        map.put("raid_disruption", List.of(EconomySourceKind.RAID_STARTED));
        map.put("raid_recovery", List.of(EconomySourceKind.RAID_ENDED));
        map.put("trade_network_damage", List.of(EconomySourceKind.TRADE_VILLAGER_DIED));
        map.put("reconstruction_demand", List.of(EconomySourceKind.TRADE_REGION_BUILD));
        map.put("dimension_expansion", List.of(EconomySourceKind.DIMENSION_ENTERED));
        map.put("dragon_unlock", List.of(EconomySourceKind.DRAGON_DEFEATED));
        map.put("boss_confidence", List.of(EconomySourceKind.DRAGON_DEFEATED, EconomySourceKind.WITHER_DEFEATED));
        map.put("weather_logistics", List.of(EconomySourceKind.WEATHER_STARTED));
        return Map.copyOf(map);
    }
}
