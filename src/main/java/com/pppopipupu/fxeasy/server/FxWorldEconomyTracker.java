package com.pppopipupu.fxeasy.server;

import module java.base;

import com.pppopipupu.fxeasy.FxConfig;
import com.pppopipupu.fxeasy.economy.EconomyRow;
import com.pppopipupu.fxeasy.economy.EconomySourceKind;
import com.pppopipupu.fxeasy.economy.WorldEconomyState;
import com.pppopipupu.fxeasy.event.WorldActivity;
import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;
import com.pppopipupu.fxeasy.net.FxEconomyPayload;
import com.pppopipupu.fxeasy.storage.FxState;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;

/// The single entry point for every committed world fact the market prices. An adapter only ever calls here once
/// the operation's own commit point has been reached, so a cancelled death, a cancelled drop, a reverted
/// placement, a previewed trade and every transfer between holders contribute exactly zero. Facts go into the
/// open bucket, are counted for the report and are handed to the event layer as a [WorldActivity].
///
/// The listeners that produce the facts are one family per kind - [FxLootCapture], [FxCommittedProduction],
/// [FxMachineFacts] and [FxTradeRegions]; the static entry points below are the thin adapters the mixins call.
public final class FxWorldEconomyTracker {

    private static final int MAX_EXCLUDED_CHUNKS = 4096;
    private static final int PRUNE_INTERVAL = 20;
    private static final Set<ResourceKey<Level>> SUPPORTED_DIMENSIONS = Set.of(Level.OVERWORLD, Level.NETHER, Level.END);

    private static FxWorldEconomyTracker instance;

    private final WorldEconomyState economy;
    private final List<Consumer<WorldActivity>> listeners = new ArrayList<>();
    private final Set<ChunkKey> excludedChunks = new LinkedHashSet<>();
    private final long[] openAtoms = new long[EconomySourceKind.COUNT];
    private final long[] newestAtoms = new long[EconomySourceKind.COUNT];
    private final long[] lifetimeAtoms = new long[EconomySourceKind.COUNT * Currencies.count()];
    final FxTradeRegions regions;
    final FxLootCapture loot;
    final FxCommittedProduction production;
    final FxMachineFacts machines;

    private boolean bucketSealed;
    private int ticks;

    public FxWorldEconomyTracker(FxState state) {
        this(state.economy());
    }

    public FxWorldEconomyTracker(WorldEconomyState economy) {
        this.economy = economy;
        this.regions = new FxTradeRegions(this, economy);
        this.machines = new FxMachineFacts(this, this.regions);
        this.loot = new FxLootCapture(this, this.regions);
        this.production = new FxCommittedProduction(this, this.regions);
    }

    /// Makes this tracker the one the event listeners and the mixins record into. The service calls it when it
    /// starts, so a tracker a game test builds stays private and can never take the running server's collector
    /// away from it.
    public void attach() {
        instance = this;
    }

    /// Releases the static entry point if this tracker still holds it, so a stopped server records nothing.
    public void detach() {
        if (instance == this) {
            instance = null;
        }
    }

    /// Wires the attachment registry, the event listeners and the static entry points the mixins call. Called
    /// once from the mod constructor.
    public static void register(IEventBus modBus) {
        FxWorldSourceMarkers.register(modBus);
        FxCommittedProduction.register();
        FxLootCapture.register();
        FxTradeRegions.register();
    }

    /// The tracker of the running server, or null before the service started one.
    public static FxWorldEconomyTracker current() {
        return instance;
    }

    public void onFact(Consumer<WorldActivity> listener) {
        this.listeners.add(listener);
    }

    public EconomyRow snapshot() {
        return this.economy.snapshot();
    }

    /// Records one committed primary supply or terminal use. A kind that the configuration does not collect, and
    /// a dimension the collector refuses, contribute nothing at all.
    public void recordCommitted(EconomySourceKind kind, Currency currency, long atoms) {
        record(kind, currency, atoms, null);
    }

    /// The live economy state this tracker's facts are folded into, for the service and the game tests. Never
    /// hand it to a client.
    public WorldEconomyState snapshotEconomy() {
        return this.economy;
    }

    /// Atoms this tracker has recorded for one kind and currency since it was created, for reporting and for the
    /// game tests. Unlike the open bucket this counter is never reset by a bucket boundary.
    public long recordedAtoms(EconomySourceKind kind, Currency currency) {
        return this.lifetimeAtoms[kind.ordinal() * Currencies.count() + Currencies.index(currency)];
    }

    public void onServerTick(MinecraftServer server) {
        this.ticks++;
        this.machines.sampleWeather(server);
        if (this.ticks % PRUNE_INTERVAL == 0) {
            prune();
        }
    }

    public void onBucketBoundary() {
        System.arraycopy(this.openAtoms, 0, this.newestAtoms, 0, this.openAtoms.length);
        Arrays.fill(this.openAtoms, 0L);
        this.bucketSealed = true;
        this.regions.clearUnsupported();
        prune();
    }

    public boolean levelHasRecentTrades(ResourceKey<Level> dimension) {
        return this.regions.levelHasRecentTrades(dimension);
    }

    /// Coverage of the collector, indexed as in [FxEconomyPayload].
    public int[] coverage() {
        int[] coverage = new int[FxEconomyPayload.COVERAGE_ENTRIES];
        coverage[FxEconomyPayload.COVERAGE_OVER_BUDGET_CHUNKS] = this.excludedChunks.size();
        coverage[FxEconomyPayload.COVERAGE_UNSUPPORTED_DIMENSIONS] = this.regions.unsupportedCount();
        coverage[FxEconomyPayload.COVERAGE_DISABLED_SOURCES] = disabledSources();
        return coverage;
    }

    /// Ordinals of the source kinds that produced atoms in the newest closed bucket, ascending.
    public int[] supportedSourceOrdinals() {
        long[] source = this.bucketSealed ? this.newestAtoms : this.openAtoms;
        int[] ordinals = new int[source.length];
        int count = 0;
        for (int index = 0; index < source.length && count < FxEconomyPayload.MAX_COVERAGE; index++) {
            if (source[index] > 0L) {
                ordinals[count++] = index;
            }
        }
        return Arrays.copyOf(ordinals, count);
    }

    /// Atoms of [#supportedSourceOrdinals], in the same order.
    public long[] supportedSourceAtoms() {
        long[] source = this.bucketSealed ? this.newestAtoms : this.openAtoms;
        long[] atoms = new long[source.length];
        int count = 0;
        for (int index = 0; index < source.length && count < FxEconomyPayload.MAX_COVERAGE; index++) {
            if (source[index] > 0L) {
                atoms[count++] = source[index];
            }
        }
        return Arrays.copyOf(atoms, count);
    }

    static void noteExcludedChunk(ServerLevel level, ChunkPos pos) {
        FxWorldEconomyTracker tracker = instance;
        if (tracker == null) {
            return;
        }
        if (tracker.excludedChunks.size() >= MAX_EXCLUDED_CHUNKS) {
            tracker.excludedChunks.remove(tracker.excludedChunks.iterator().next());
        }
        tracker.excludedChunks.add(new ChunkKey(level.dimension(), pos.x(), pos.z()));
    }

    public static void beginDeathLoot(LivingEntity entity) {
        if (instance != null) {
            instance.loot.beginDeathLoot(entity);
        }
    }

    public static void endDeathLoot(LivingEntity entity) {
        if (instance != null) {
            instance.loot.endDeathLoot(entity);
        }
    }

    public static void beginLootTable(LivingEntity entity) {
        if (instance != null) {
            instance.loot.beginLootTable(entity);
        }
    }

    public static void endLootTable(LivingEntity entity) {
        if (instance != null) {
            instance.loot.endLootTable(entity);
        }
    }

    public static void committedDeathDrops(LivingEntity entity, Collection<ItemEntity> drops, boolean cancelled) {
        if (instance != null) {
            instance.loot.committedDeathDrops(entity, drops, cancelled);
        }
    }

    public static void committedBlockDrops(ServerLevel level, BlockPos pos, BlockState state, List<ItemEntity> drops, Entity breaker) {
        if (instance != null) {
            instance.loot.committedBlockDrops(level, pos, state, drops, breaker);
        }
    }

    public static void explosionDrop(ServerLevel level, BlockPos pos, BlockState state, ItemStack stack) {
        if (instance != null) {
            instance.loot.explosionDrop(level, pos, state, stack);
        }
    }

    public static void craftingTaken(ServerLevel level, RecipeInput input, ItemStack result) {
        if (instance != null) {
            instance.production.craftingTaken(level, input, result);
        }
    }

    public static void smithingTaken(ServerLevel level, ItemStack template, ItemStack base, ItemStack addition, ItemStack result) {
        if (instance != null) {
            instance.production.smithingTaken(level, template, base, addition, result);
        }
    }

    public static void crafterAssembled(RecipeInput input, ItemStack result, ServerLevel level) {
        if (instance != null) {
            instance.production.crafterAssembled(input, result, level);
        }
    }

    public static void crafterCommitted() {
        if (instance != null) {
            instance.production.crafterCommitted();
        }
    }

    public static void pistonResolved(Level level, Direction direction, boolean extending, List<BlockPos> toPush) {
        if (instance != null) {
            instance.machines.pistonResolved(level, direction, extending, toPush);
        }
    }

    public static void pistonMoved() {
        if (instance != null) {
            instance.machines.pistonMoved();
        }
    }

    public static void barterStarted(Piglin piglin) {
        if (instance != null) {
            instance.machines.barterStarted(piglin);
        }
    }

    public static void barterCommitted() {
        if (instance != null) {
            instance.machines.barterCommitted();
        }
    }

    public static void observeRaids(ServerLevel level, Int2ObjectMap<Raid> raidMap) {
        if (instance != null) {
            instance.machines.observeRaids(level, raidMap);
        }
    }

    public static void dragonKilled(LivingEntity dragon, boolean previouslyKilled) {
        if (instance != null) {
            instance.machines.dragonKilled(dragon, previouslyKilled);
        }
    }

    void record(EconomySourceKind kind, Currency currency, long atoms, ResourceKey<Level> dimension) {
        if (atoms <= 0L || !FxConfig.get().collects(kind)) {
            return;
        }
        if (dimension != null && !supported(dimension)) {
            this.regions.noteUnsupported(dimension);
            return;
        }
        if (kind.primarySupply()) {
            this.economy.openBucket().addSupply(kind, currency, atoms);
        } else {
            this.economy.openBucket().addTerminalUse(kind, currency, atoms);
        }
        this.openAtoms[kind.ordinal()] += atoms;
        this.lifetimeAtoms[kind.ordinal() * Currencies.count() + Currencies.index(currency)] += atoms;
        emit(kind, currency, atoms, dimension);
    }

    void activity(EconomySourceKind kind, ResourceKey<Level> dimension) {
        if (!kind.activity() || !FxConfig.get().collects(kind)) {
            return;
        }
        if (dimension != null && !supported(dimension)) {
            this.regions.noteUnsupported(dimension);
            return;
        }
        this.economy.openBucket().addActivity(kind);
        emit(kind, null, 0L, dimension);
    }

    private void emit(EconomySourceKind kind, Currency currency, long atoms, ResourceKey<Level> dimension) {
        if (this.listeners.isEmpty()) {
            return;
        }
        WorldActivity fact = new WorldActivity(kind, dimension == null ? null : dimension.identifier().toString(),
                this.economy.marketTick(), atoms, currency);
        for (int index = 0; index < this.listeners.size(); index++) {
            this.listeners.get(index).accept(fact);
        }
    }

    private void prune() {
        this.regions.prune();
    }

    boolean supported(ResourceKey<Level> dimension) {
        return SUPPORTED_DIMENSIONS.contains(dimension);
    }

    private static int disabledSources() {
        FxConfig.Snapshot config = FxConfig.get();
        int disabled = 0;
        for (EconomySourceKind kind : EconomySourceKind.ALL) {
            if (!config.collects(kind)) {
                disabled++;
            }
        }
        return disabled;
    }

    private record ChunkKey(ResourceKey<Level> dimension, int x, int z) {
    }
}
