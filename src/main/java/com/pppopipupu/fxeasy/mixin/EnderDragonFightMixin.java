package com.pppopipupu.fxeasy.mixin;

import module java.base;

import com.pppopipupu.fxeasy.server.FxWorldEconomyTracker;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// The only public accessor of the fight is the already killed flag, which the commit point flips while it
/// commits, so the flag is read before the call to tell a first kill from a repeat one.
@Mixin(EnderDragonFight.class)
public abstract class EnderDragonFightMixin {

    @Shadow
    private boolean hasPreviouslyKilledDragon;

    @Inject(method = "setDragonKilled(Lnet/minecraft/world/entity/boss/enderdragon/EnderDragon;)V", at = @At("HEAD"))
    private void fxeasy$dragonKilled(EnderDragon dragon, CallbackInfo callback) {
        FxWorldEconomyTracker.dragonKilled(dragon, this.hasPreviouslyKilledDragon);
    }
}
