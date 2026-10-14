package com.pppopipupu.fxeasy.mixin;

import module java.base;

import com.pppopipupu.fxeasy.server.FxWorldEconomyTracker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// The commit point of a barter: the off hand is cleared, the gold is gone and the loot roll is about to run.
/// The window is opened at the entry and closed at the roll, which is the branch that only runs for a real
/// barter, so the two admiring branches that call this method can never record a use.
@Mixin(PiglinAi.class)
public abstract class PiglinAiMixin {

    @Inject(method = "stopHoldingOffHandItem(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/monster/piglin/Piglin;Z)V", at = @At("HEAD"))
    private static void fxeasy$barterStarted(ServerLevel level, Piglin piglin, boolean barteringEnabled, CallbackInfo callback) {
        FxWorldEconomyTracker.barterStarted(piglin);
    }

    @Inject(method = "stopHoldingOffHandItem(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/monster/piglin/Piglin;Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/piglin/PiglinAi;getBarterResponseItems(Lnet/minecraft/world/entity/monster/piglin/Piglin;)Ljava/util/List;"))
    private static void fxeasy$barterCommitted(ServerLevel level, Piglin piglin, boolean barteringEnabled, CallbackInfo callback) {
        FxWorldEconomyTracker.barterCommitted();
    }
}
