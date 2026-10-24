package com.pppopipupu.fxeasy.content.item;

import com.pppopipupu.fxeasy.content.AngryClientHooks;
import com.pppopipupu.fxeasy.content.AngryEnergy;
import com.pppopipupu.fxeasy.content.AngryHealth;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

public class AngryWand extends Item {

    private static final int ENERGY_RADIUS = 6;
    private static final int ENERGY_INTERVAL = 5;
    private static final float ENERGY_PER_PULSE = 1.0F;

    public AngryWand(Item.Properties properties) {
        super(properties);
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BRUSH;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 114514;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onUseTick(Level level, LivingEntity livingEntity, ItemStack stack, int remainingUseDuration) {
        if (!(livingEntity instanceof Player player)) {
            return;
        }
        if (level.isClientSide()) {
            if (level.getGameTime() % 2 == 0) {
                AngryClientHooks.wandEffect.run(level, player);
            }
            return;
        }
        if (level.getGameTime() % ENERGY_INTERVAL != 0) {
            return;
        }
        BlockPos source = AngryEnergy.nearestBlock(level, player, ENERGY_RADIUS);
        if (source != null) {
            AngryHealth.grant(player, ENERGY_PER_PULSE);
        }
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTime) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        if (level.isClientSide()) {
            AngryClientHooks.wandRelease.run(level, player);
        } else {
            AngryHealth.releaseHalf(player);
        }
        return true;
    }
}
