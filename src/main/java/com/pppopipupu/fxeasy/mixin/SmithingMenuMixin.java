package com.pppopipupu.fxeasy.mixin;

import module java.base;

import com.pppopipupu.fxeasy.server.FxWorldEconomyTracker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// The commit point of a smithing take: the three input slots still hold the stacks the take is about to shrink,
/// so the menu's own view of the items the recipe consumed is read there and handed to the tracker.
@Mixin(SmithingMenu.class)
public abstract class SmithingMenuMixin {

    @Shadow
    protected abstract List<ItemStack> getRelevantItems();

    @Inject(method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"))
    private void fxeasy$smithingTaken(Player taker, ItemStack carried, CallbackInfo callback) {
        if (!(taker.level() instanceof ServerLevel level)) {
            return;
        }
        List<ItemStack> inputs = this.getRelevantItems();
        FxWorldEconomyTracker.smithingTaken(level, inputs.get(0), inputs.get(1), inputs.get(2), carried);
    }
}
