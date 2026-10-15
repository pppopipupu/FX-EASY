package com.pppopipupu.fxeasy.server;

import module java.base;

import com.pppopipupu.fxeasy.FxConfig;
import com.pppopipupu.fxeasy.economy.EconomyConstants;
import com.pppopipupu.fxeasy.economy.EconomySourceKind;
import com.pppopipupu.fxeasy.economy.WorldEconomyState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/// The trade regions of one world: the grid cells that saw a real trade inside the recency window, the newest
/// trade per dimension, the traders still inside that window and the dimensions the collector has entered or
/// refuses. A region is a fixed cell of 128 blocks, so a village that keeps trading keeps its cell and a trader
/// that wanders out of it counts as a different one.
///
/// Only the server thread touches this state, which is why the tables are plain maps.
final class FxTradeRegions {

    private static final int REGION_SHIFT = 7;
    private static final int MAX_RECENT_TRADERS = 512;
    private static final int MAX_TRACKED_DIMENSIONS = 64;
    private static final long TRADE_WINDOW_TICKS = EconomyConstants.TICKS_PER_DAY;

    private final FxWorldEconomyTracker tracker;
    private final WorldEconomyState economy;
    private final Map<Region, Long> tradeRegions = new LinkedHashMap<>();
    private final Map<ResourceKey<Level>, Long> newestTradeByDimension = new HashMap<>();
    private final Map<UUID, Long> recentTraders = new HashMap<>();
    private final Set<ResourceKey<Level>> enteredDimensions = new HashSet<>();
    private final Set<ResourceKey<Level>> unsupportedDimensions = new HashSet<>();

    FxTradeRegions(FxWorldEconomyTracker tracker, WorldEconomyState economy) {
        this.tracker = tracker;
        this.economy = economy;
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, FxTradeRegions::onPlaced);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, FxTradeRegions::onDimensionChanged);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, FxTradeRegions::onLoggedIn);
    }

    private static void onPlaced(BlockEvent.EntityPlaceEvent event) {
        FxWorldEconomyTracker tracker = FxWorldEconomyTracker.current();
        if (tracker == null || event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        FxWorldSourceMarkers.markPlaced(level, pos, event.getPlacedBlock());
        if (event.getEntity() instanceof Player) {
            tracker.regions.tradeRegionActivity(EconomySourceKind.TRADE_REGION_BUILD, level, pos);
        }
    }

    private static void onDimensionChanged(PlayerEvent.PlayerChangedDimensionEvent event) {
        FxWorldEconomyTracker tracker = FxWorldEconomyTracker.current();
        if (tracker != null) {
            tracker.regions.dimensionEntered(event.getTo());
        }
    }

    private static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        FxWorldEconomyTracker tracker = FxWorldEconomyTracker.current();
        if (tracker != null && event.getEntity() instanceof ServerPlayer player) {
            tracker.regions.dimensionEntered(player.level().dimension());
        }
    }

    boolean levelHasRecentTrades(ResourceKey<Level> dimension) {
        Long newest = this.newestTradeByDimension.get(dimension);
        return newest != null && this.economy.marketTick() - newest <= TRADE_WINDOW_TICKS;
    }

    /// Whether a villager traded inside the recency window, which is what makes its death a trade fact.
    boolean villagerTradedRecently(UUID villager) {
        Long traded = this.recentTraders.get(villager);
        return traded != null && this.economy.marketTick() - traded <= TRADE_WINDOW_TICKS;
    }

    void observeTradeRegion(ResourceKey<Level> dimension, BlockPos pos, AbstractVillager villager) {
        long tick = this.economy.marketTick();
        Region region = Region.of(pos);
        Long previous = this.tradeRegions.put(region, tick);
        if (previous == null) {
            evictOldestRegion();
        }
        this.newestTradeByDimension.put(dimension, tick);
        if (this.recentTraders.size() >= MAX_RECENT_TRADERS && !this.recentTraders.containsKey(villager.getUUID())) {
            this.recentTraders.remove(this.recentTraders.keySet().iterator().next());
        }
        this.recentTraders.put(villager.getUUID(), tick);
    }

    void tradeRegionActivity(EconomySourceKind kind, ServerLevel level, BlockPos pos) {
        ResourceKey<Level> dimension = level.dimension();
        if (!levelHasRecentTrades(dimension)) {
            return;
        }
        Long traded = this.tradeRegions.get(Region.of(pos));
        if (traded == null || this.economy.marketTick() - traded > TRADE_WINDOW_TICKS) {
            return;
        }
        this.tracker.activity(kind, dimension);
    }

    void dimensionEntered(ResourceKey<Level> dimension) {
        if (!this.tracker.supported(dimension) || this.enteredDimensions.size() >= MAX_TRACKED_DIMENSIONS) {
            return;
        }
        if (this.enteredDimensions.add(dimension)) {
            this.tracker.activity(EconomySourceKind.DIMENSION_ENTERED, dimension);
        }
    }

    void noteUnsupported(ResourceKey<Level> dimension) {
        if (this.unsupportedDimensions.size() < MAX_TRACKED_DIMENSIONS) {
            this.unsupportedDimensions.add(dimension);
        }
    }

    int unsupportedCount() {
        return this.unsupportedDimensions.size();
    }

    void clearUnsupported() {
        this.unsupportedDimensions.clear();
    }

    void prune() {
        long tick = this.economy.marketTick();
        this.tradeRegions.entrySet().removeIf(entry -> tick - entry.getValue() > TRADE_WINDOW_TICKS);
        this.newestTradeByDimension.entrySet().removeIf(entry -> tick - entry.getValue() > TRADE_WINDOW_TICKS);
        this.recentTraders.entrySet().removeIf(entry -> tick - entry.getValue() > TRADE_WINDOW_TICKS);
    }

    private void evictOldestRegion() {
        int limit = FxConfig.get().tradeRegionLimit();
        while (this.tradeRegions.size() > limit) {
            Region oldest = null;
            long oldestTick = Long.MAX_VALUE;
            for (Map.Entry<Region, Long> entry : this.tradeRegions.entrySet()) {
                if (entry.getValue() < oldestTick) {
                    oldestTick = entry.getValue();
                    oldest = entry.getKey();
                }
            }
            if (oldest == null) {
                return;
            }
            this.tradeRegions.remove(oldest);
        }
    }

    private record Region(long cellX, long cellZ) {

        static Region of(BlockPos pos) {
            return new Region(pos.getX() >> REGION_SHIFT, pos.getZ() >> REGION_SHIFT);
        }
    }
}
