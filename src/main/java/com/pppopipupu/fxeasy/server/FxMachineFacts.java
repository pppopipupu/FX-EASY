package com.pppopipupu.fxeasy.server;

import module java.base;

import com.pppopipupu.fxeasy.economy.EconomySourceKind;
import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.WeatherData;

/// The machine and boss facts of one world: the piston move in progress, the piglin barter in progress, the
/// phase of every raid, the ender dragon and the weather. The marks of the blocks a piston pushed follow it
/// once the move is done, a barter counts the gold that left the piglin's hand, and a raid only counts as a
/// fact while the level still traded.
///
/// Only the server thread touches this state, which is why the in progress fields are plain fields.
final class FxMachineFacts {

    private static final int MAX_RAID_RECORDS = 256;

    private final FxWorldEconomyTracker tracker;
    private final FxTradeRegions regions;
    private final Map<RaidKey, RaidPhase> raids = new HashMap<>();
    private final long[] barterAtoms = new long[Currencies.count()];

    private Piglin barterPiglin;
    private ServerLevel pistonLevel;
    private Direction pistonDirection;
    private List<BlockPos> pistonPush;
    private boolean dragonDefeated;
    private boolean weatherActive;

    FxMachineFacts(FxWorldEconomyTracker tracker, FxTradeRegions regions) {
        this.tracker = tracker;
        this.regions = regions;
    }

    /// Called at the commit point of the resolver of a piston move, before the blocks are written: the marks
    /// are applied later, once the move is done, so a destroyed position is not marked before it drops.
    void pistonResolved(Level level, Direction direction, boolean extending, List<BlockPos> toPush) {
        if (!(level instanceof ServerLevel serverLevel) || toPush.isEmpty()) {
            return;
        }
        this.pistonLevel = serverLevel;
        this.pistonDirection = extending ? direction : direction.getOpposite();
        this.pistonPush = toPush;
    }

    /// The end of a piston move: the marks of the positions it pushed follow the block.
    void pistonMoved() {
        if (this.pistonPush == null) {
            return;
        }
        ServerLevel level = this.pistonLevel;
        Direction direction = this.pistonDirection;
        List<BlockPos> pushed = this.pistonPush;
        this.pistonLevel = null;
        this.pistonDirection = null;
        this.pistonPush = null;
        if (level != null && direction != null) {
            FxWorldSourceMarkers.moveMarks(level, pushed, direction);
        }
    }

    void barterStarted(Piglin piglin) {
        this.barterPiglin = piglin;
        Arrays.fill(this.barterAtoms, 0L);
        FxCurrencyAtoms.AtomValue value = FxCurrencyAtoms.value(piglin.getOffhandItem());
        if (value != null) {
            this.barterAtoms[Currencies.index(value.currency())] = value.atoms();
        }
    }

    /// The commit point of a barter: the gold left the piglin's hand for a rolled barter response.
    void barterCommitted() {
        if (this.barterPiglin == null) {
            return;
        }
        Piglin piglin = this.barterPiglin;
        this.barterPiglin = null;
        if (!(piglin.level() instanceof ServerLevel level)) {
            return;
        }
        for (Currency currency : Currencies.all()) {
            long atoms = this.barterAtoms[Currencies.index(currency)];
            if (atoms > 0L) {
                this.tracker.record(EconomySourceKind.BARTER_TERMINAL_USE, currency, atoms, level.dimension());
            }
        }
    }

    /// The commit point of a raid: the raids of one level with the phase each of them is in. A raid that has
    /// left the map was removed because it stopped, which is the end of it.
    void observeRaids(ServerLevel level, Int2ObjectMap<Raid> raidMap) {
        ResourceKey<Level> dimension = level.dimension();
        boolean traded = this.regions.levelHasRecentTrades(dimension);
        for (var entry : raidMap.int2ObjectEntrySet()) {
            RaidKey key = new RaidKey(dimension, entry.getIntKey());
            RaidPhase phase = raidPhase(entry.getValue());
            RaidPhase seen = this.raids.put(key, phase);
            if (phase == RaidPhase.RUNNING && seen == null && traded) {
                this.tracker.activity(EconomySourceKind.RAID_STARTED, dimension);
            } else if (phase == RaidPhase.OVER && seen != null && seen != RaidPhase.OVER) {
                this.tracker.activity(EconomySourceKind.RAID_ENDED, dimension);
            }
        }
        List<RaidKey> gone = null;
        for (Map.Entry<RaidKey, RaidPhase> entry : this.raids.entrySet()) {
            RaidKey key = entry.getKey();
            if (!key.dimension().equals(dimension) || raidMap.containsKey(key.id())) {
                continue;
            }
            if (entry.getValue() != RaidPhase.OVER) {
                this.tracker.activity(EconomySourceKind.RAID_ENDED, dimension);
            }
            (gone == null ? gone = new ArrayList<>() : gone).add(key);
        }
        if (gone != null) {
            for (RaidKey key : gone) {
                this.raids.remove(key);
            }
        }
        if (this.raids.size() > MAX_RAID_RECORDS) {
            this.raids.entrySet().removeIf(record -> !record.getKey().dimension().equals(dimension));
        }
    }

    /// The commit point of the ender dragon fight, called before the world flag is set.
    void dragonKilled(LivingEntity dragon, boolean previouslyKilled) {
        if (previouslyKilled || this.dragonDefeated) {
            return;
        }
        this.dragonDefeated = true;
        if (dragon.level() instanceof ServerLevel level) {
            this.tracker.activity(EconomySourceKind.DRAGON_DEFEATED, level.dimension());
        }
    }

    void sampleWeather(MinecraftServer server) {
        WeatherData weather = server.getWeatherData();
        boolean active = weather.isRaining() || weather.isThundering();
        if (active == this.weatherActive) {
            return;
        }
        this.weatherActive = active;
        EconomySourceKind kind = active ? EconomySourceKind.WEATHER_STARTED : EconomySourceKind.WEATHER_ENDED;
        for (ServerLevel level : server.getAllLevels()) {
            if (level.canHaveWeather()) {
                this.tracker.activity(kind, level.dimension());
            }
        }
    }

    void reset() {
        this.raids.clear();
        this.dragonDefeated = false;
        this.weatherActive = false;
    }

    private static RaidPhase raidPhase(Raid raid) {
        if (raid.isVictory() || raid.isLoss() || raid.isStopped()) {
            return RaidPhase.OVER;
        }
        return raid.isStarted() ? RaidPhase.RUNNING : RaidPhase.FORMING;
    }

    private enum RaidPhase {
        FORMING,
        RUNNING,
        OVER
    }

    private record RaidKey(ResourceKey<Level> dimension, int id) {
    }
}
