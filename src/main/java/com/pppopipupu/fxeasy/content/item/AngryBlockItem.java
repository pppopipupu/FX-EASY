package com.pppopipupu.fxeasy.content.item;

import module java.base;

import com.pppopipupu.fxeasy.content.block.AngryBlock;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class AngryBlockItem extends BlockItem {

    private final int mode;

    public AngryBlockItem(Block block, Item.Properties properties, int mode) {
        super(block, properties);
        this.mode = mode;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
            Consumer<Component> tooltip, TooltipFlag flag) {
        switch (mode) {
            case 0 -> tooltip.accept(Component.translatable("tooltip.fxeasy.angry_block"));
            case 1 -> {
                tooltip.accept(Component.translatable("tooltip.fxeasy.angry_lightning_block"));
                tooltip.accept(Component.nullToEmpty(
                        "§kAYq4OBRzT2tYA70e7SFtzXGem09RBxTHkR6o5Tv5U9sHAFZNyhCu1hev5I5VyVvIubNobPO3KuEQ7GVuIbNW3nYWmlitE2PGRL5kxNRPUH9ldGI69rNHccLVfVLhNMPO63Kqwt0RF3xwUpUbzSKIN960U2CmnhGANgoZ7sjExaeztCN52kw82HWHyP7cM/kmmkRGR7FmX8cZmRzv0xadqa2K1sJRb8XxQ2C3uxMEH8heKl4/Ol2PJI6v7mluK4wEj2+X644DpDu/144nOv5mta1rPJxeT8pzSmfRemN4dP63Re6xFs4ASURRGgLlUwgssK//Hx+BgCwcoXACULvBhfJjR66CtVKLw7wYQMG2x0VzrDVPapmgDecxmRo49crb"));
            }
            case 2 -> tooltip.accept(Component.translatable("tooltip.fxeasy.angry_atomic_block"));
            default -> {
            }
        }
    }

    @Nullable
    @Override
    protected BlockState getPlacementState(BlockPlaceContext context) {
        BlockState state = super.getPlacementState(context);
        if (state == null) {
            return null;
        }
        return switch (mode) {
            case 1 -> state.setValue(AngryBlock.IS_LIGHTNING, true);
            case 2 -> state.setValue(AngryBlock.IS_ATOMIC, true);
            default -> state;
        };
    }
}
