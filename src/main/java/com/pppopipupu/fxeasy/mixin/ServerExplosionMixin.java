package com.pppopipupu.fxeasy.mixin;

import module java.base;

import com.pppopipupu.fxeasy.server.FxWorldEconomyTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/// Explosions bypass the block drop hook: they hand each stack the block produced to a consumer while the block
/// is still in place, collect the stacks, and push them into the level unconditionally afterwards. Wrapping the
/// consumer is the commit point of an explosion drop, and it is the only place that still knows which block the
/// stack came from.
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {

    @Redirect(method = "interactWithBlocks(Ljava/util/List;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;onExplosionHit(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Explosion;Ljava/util/function/BiConsumer;)V"))
    private void fxeasy$onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion,
            BiConsumer<ItemStack, BlockPos> consumer) {
        state.onExplosionHit(level, pos, explosion, (stack, dropPos) -> {
            FxWorldEconomyTracker.explosionDrop(level, dropPos, state, stack);
            consumer.accept(stack, dropPos);
        });
    }
}
