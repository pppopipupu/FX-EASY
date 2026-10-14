package com.pppopipupu.fxeasy.mixin;

import module java.base;

import com.pppopipupu.fxeasy.server.FxWorldEconomyTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// The crafter consumes its inputs on the branch that assembled a result, so the assembly is captured as it
/// happens and the take is closed at the end of the dispense, where every input it kept has been shrunk.
@Mixin(CrafterBlock.class)
public abstract class CrafterBlockMixin {

    @Redirect(method = "dispenseFrom(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/CraftingRecipe;assemble(Lnet/minecraft/world/item/crafting/RecipeInput;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack fxeasy$assemble(CraftingRecipe recipe, RecipeInput input, BlockState state, ServerLevel level, BlockPos pos) {
        ItemStack result = recipe.assemble((CraftingInput) input);
        FxWorldEconomyTracker.crafterAssembled(input, result, level);
        return result;
    }

    @Inject(method = "dispenseFrom(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)V", at = @At("RETURN"))
    private void fxeasy$dispensed(BlockState state, ServerLevel level, BlockPos pos, CallbackInfo callback) {
        FxWorldEconomyTracker.crafterCommitted();
    }
}
