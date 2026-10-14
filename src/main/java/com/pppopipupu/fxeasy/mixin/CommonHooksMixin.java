package com.pppopipupu.fxeasy.mixin;

import module java.base;

import com.pppopipupu.fxeasy.server.FxWorldEconomyTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/// The two NeoForge seams where a drop list becomes a decision. Both are read at their commit point: the death
/// drop list after the event has had its say and before the level is handed the entities, the block drop list
/// after the cancel check and before the drops are added. The collector never trusts the event alone, because
/// neither event proves that the drops were committed.
@Mixin(CommonHooks.class)
public abstract class CommonHooksMixin {

    @Inject(method = "onLivingDrops(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;Ljava/util/Collection;Z)Z", at = @At("RETURN"))
    private static void fxeasy$onLivingDrops(LivingEntity entity, DamageSource source, Collection<ItemEntity> drops, boolean recentlyHit,
            CallbackInfoReturnable<Boolean> callback) {
        FxWorldEconomyTracker.committedDeathDrops(entity, drops, callback.getReturnValueZ());
    }

    @Redirect(method = "handleBlockDrops(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;Ljava/util/List;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/event/level/BlockDropsEvent;isCanceled()Z"))
    private static boolean fxeasy$handleBlockDrops(BlockDropsEvent event, ServerLevel level, BlockPos pos, BlockState state, BlockEntity blockEntity,
            List<ItemEntity> drops, Entity breaker, ItemStack tool) {
        boolean cancelled = event.isCanceled();
        if (!cancelled) {
            FxWorldEconomyTracker.committedBlockDrops(level, pos, state, event.getDrops(), breaker);
        }
        return cancelled;
    }
}
