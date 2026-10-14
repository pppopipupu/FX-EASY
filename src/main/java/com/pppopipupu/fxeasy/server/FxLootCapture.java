package com.pppopipupu.fxeasy.server;

import module java.base;

import com.pppopipupu.fxeasy.economy.EconomySourceKind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/// The death and drop capture of one world: the entity whose death is being rolled, the drops the loot table
/// added to it, and the block and explosion drops that are committed. A death only counts once the level keeps
/// its drops, so a cancelled death, a cancelled drop and a reverted break contribute nothing, and a break by a
/// player inside a recent trade region is activity rather than a find.
///
/// Only the server thread touches this state, which is why the per operation fields are plain fields.
final class FxLootCapture {

    private final FxWorldEconomyTracker tracker;
    private final FxTradeRegions regions;
    private final List<ItemEntity> lootDrops = new ArrayList<>();

    private LivingEntity lootEntity;
    private Collection<ItemEntity> lootCapture;
    private int lootFrom;
    private boolean witherDefeated;

    FxLootCapture(FxWorldEconomyTracker tracker, FxTradeRegions regions) {
        this.tracker = tracker;
        this.regions = regions;
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, FxLootCapture::onLivingDeath);
    }

    private static void onLivingDeath(LivingDeathEvent event) {
        FxWorldEconomyTracker tracker = FxWorldEconomyTracker.current();
        if (tracker != null) {
            tracker.loot.observeDeath(event);
        }
    }

    private void observeDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof WitherBoss) {
            if (!this.witherDefeated) {
                this.witherDefeated = true;
                if (entity.level() instanceof ServerLevel level) {
                    this.tracker.activity(EconomySourceKind.WITHER_DEFEATED, level.dimension());
                }
            }
            return;
        }
        if (entity instanceof AbstractVillager villager && entity.level() instanceof ServerLevel level) {
            if (this.regions.villagerTradedRecently(villager.getUUID())) {
                this.tracker.activity(EconomySourceKind.TRADE_VILLAGER_DIED, level.dimension());
            }
        }
    }

    void beginDeathLoot(LivingEntity entity) {
        this.lootEntity = entity;
        this.lootCapture = null;
        this.lootFrom = 0;
        this.lootDrops.clear();
    }

    void endDeathLoot(LivingEntity entity) {
        if (this.lootEntity == entity) {
            this.lootEntity = null;
            this.lootCapture = null;
            this.lootDrops.clear();
        }
    }

    /// Called before the loot table of a death is rolled: the entity's drop capture list is live at that moment,
    /// and the entities it grows by are exactly the loot table's output.
    void beginLootTable(LivingEntity entity) {
        if (this.lootEntity != entity) {
            return;
        }
        Collection<ItemEntity> capture = entity.captureDrops();
        this.lootCapture = capture;
        this.lootFrom = capture == null ? -1 : capture.size();
        this.lootDrops.clear();
    }

    void endLootTable(LivingEntity entity) {
        if (this.lootCapture == null || this.lootEntity != entity) {
            return;
        }
        Collection<ItemEntity> capture = this.lootCapture;
        this.lootCapture = null;
        if (this.lootFrom < 0 || capture == null || capture.size() <= this.lootFrom) {
            return;
        }
        int index = 0;
        for (ItemEntity drop : capture) {
            if (index++ >= this.lootFrom) {
                this.lootDrops.add(drop);
            }
        }
    }

    /// The commit point of a death: the drops the level is about to keep, or the fact that it keeps none.
    void committedDeathDrops(LivingEntity entity, Collection<ItemEntity> drops, boolean cancelled) {
        if (this.lootEntity != entity) {
            return;
        }
        this.lootEntity = null;
        List<ItemEntity> loot = this.lootDrops;
        if (cancelled) {
            loot.clear();
            return;
        }
        if (loot.isEmpty()) {
            return;
        }
        ServerLevel level = entity.level() instanceof ServerLevel serverLevel ? serverLevel : null;
        if (level == null) {
            loot.clear();
            return;
        }
        for (ItemEntity drop : loot) {
            if (!drops.contains(drop)) {
                continue;
            }
            FxCurrencyAtoms.AtomValue value = FxCurrencyAtoms.value(drop.getItem());
            if (value != null) {
                this.tracker.record(EconomySourceKind.PIGLIN_GOLD, value.currency(), value.atoms(), level.dimension());
            }
        }
        loot.clear();
    }

    /// The commit point of a normal block break: the drops the level keeps. A resource block that something
    /// placed is a recovery, not a find, and a break by a player inside a recent trade region is activity.
    void committedBlockDrops(ServerLevel level, BlockPos pos, BlockState state, List<ItemEntity> drops, Entity breaker) {
        if (breaker instanceof Player) {
            this.regions.tradeRegionActivity(EconomySourceKind.TRADE_REGION_ACTIVITY, level, pos);
        }
        EconomySourceKind channel = FxCurrencyAtoms.naturalChannel(state);
        if (channel == null || !FxWorldSourceMarkers.claimNatural(level, pos)) {
            return;
        }
        for (ItemEntity drop : drops) {
            FxCurrencyAtoms.AtomValue value = FxCurrencyAtoms.value(drop.getItem());
            if (value != null) {
                this.tracker.record(channel, value.currency(), value.atoms(), level.dimension());
            }
        }
    }

    /// The commit point of an explosion drop, which never passes through the block drop event. The state is read
    /// while the block is still there, because the explosion removes it before the drops are pushed out.
    void explosionDrop(ServerLevel level, BlockPos pos, BlockState state, ItemStack stack) {
        EconomySourceKind channel = FxCurrencyAtoms.naturalChannel(state);
        if (channel == null || !FxWorldSourceMarkers.claimNatural(level, pos)) {
            return;
        }
        FxCurrencyAtoms.AtomValue value = FxCurrencyAtoms.value(stack);
        if (value != null) {
            this.tracker.record(channel, value.currency(), value.atoms(), level.dimension());
        }
    }

    void reset() {
        this.witherDefeated = false;
    }
}
