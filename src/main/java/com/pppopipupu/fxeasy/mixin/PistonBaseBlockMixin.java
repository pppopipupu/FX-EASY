package com.pppopipupu.fxeasy.mixin;

import module java.base;

import com.pppopipupu.fxeasy.server.FxWorldEconomyTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/// A piston carries no chunk attachment, so the placement marks of the blocks it pushes have to be carried by
/// hand: the resolver is read as soon as it resolved, before the blocks are written, and the move is closed when
/// the method returns. A piston that resolved but never moved leaves the marks as they were.
@Mixin(PistonBaseBlock.class)
public abstract class PistonBaseBlockMixin {

    @Redirect(method = "moveBlocks(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Z)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/piston/PistonStructureResolver;resolve()Z"))
    private boolean fxeasy$resolve(PistonStructureResolver resolver, Level level, BlockPos pistonPos, Direction direction, boolean extending) {
        boolean resolved = resolver.resolve();
        if (resolved) {
            FxWorldEconomyTracker.pistonResolved(level, direction, extending, resolver.getToPush());
        }
        return resolved;
    }

    @Inject(method = "moveBlocks(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Z)Z", at = @At("RETURN"))
    private void fxeasy$moved(Level level, BlockPos pistonPos, Direction direction, boolean extending, CallbackInfoReturnable<Boolean> callback) {
        FxWorldEconomyTracker.pistonMoved();
    }
}
