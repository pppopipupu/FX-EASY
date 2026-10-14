package com.pppopipupu.fxeasy.mixin;

import module java.base;

import com.pppopipupu.fxeasy.server.FxWorldEconomyTracker;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// Opens and closes the per death loot operation and, inside it, the window in which the entity's drop capture
/// list holds exactly the loot table's output. The list is read at the end of the roll, before the equipment and
/// the custom death loot are added to it, which is what separates loot the world created from what the entity
/// carried.
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "dropAllDeathLoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("HEAD"))
    private void fxeasy$beginDeathLoot(ServerLevel level, DamageSource source, CallbackInfo callback) {
        FxWorldEconomyTracker.beginDeathLoot((LivingEntity) (Object) this);
    }

    @Inject(method = "dropAllDeathLoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("RETURN"))
    private void fxeasy$endDeathLoot(ServerLevel level, DamageSource source, CallbackInfo callback) {
        FxWorldEconomyTracker.endDeathLoot((LivingEntity) (Object) this);
    }

    @Inject(method = "dropFromLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;ZLnet/minecraft/resources/ResourceKey;Ljava/util/function/Consumer;)V", at = @At("HEAD"))
    private void fxeasy$beginLootTable(ServerLevel level, DamageSource source, boolean playerKilled, ResourceKey<LootTable> lootTable,
            Consumer<ItemStack> consumer, CallbackInfo callback) {
        FxWorldEconomyTracker.beginLootTable((LivingEntity) (Object) this);
    }

    @Inject(method = "dropFromLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;ZLnet/minecraft/resources/ResourceKey;Ljava/util/function/Consumer;)V", at = @At("RETURN"))
    private void fxeasy$endLootTable(ServerLevel level, DamageSource source, boolean playerKilled, ResourceKey<LootTable> lootTable,
            Consumer<ItemStack> consumer, CallbackInfo callback) {
        FxWorldEconomyTracker.endLootTable((LivingEntity) (Object) this);
    }
}
