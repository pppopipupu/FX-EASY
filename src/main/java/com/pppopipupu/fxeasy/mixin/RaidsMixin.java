package com.pppopipupu.fxeasy.mixin;

import module java.base;

import com.pppopipupu.fxeasy.server.FxWorldEconomyTracker;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raids;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// The raid map of a level is private and is not enumerable through any public API, and it is only ever pruned
/// when a raid stopped, so reading it right before the tick is the cheapest reliable observation of a raid's
/// lifecycle: the transition of a raid into a started state and its disappearance.
@Mixin(Raids.class)
public abstract class RaidsMixin {

    @Shadow
    @Final
    private Int2ObjectMap<Raid> raidMap;

    @Inject(method = "tick(Lnet/minecraft/server/level/ServerLevel;)V", at = @At("HEAD"))
    private void fxeasy$observe(ServerLevel level, CallbackInfo callback) {
        FxWorldEconomyTracker.observeRaids(level, this.raidMap);
    }
}
