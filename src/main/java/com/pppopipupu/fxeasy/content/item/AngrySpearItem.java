package com.pppopipupu.fxeasy.content.item;

import module java.base;

import com.pppopipupu.fxeasy.FxTickScheduler;
import com.pppopipupu.fxeasy.content.AngryClientHooks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

public class AngrySpearItem extends Item {

    private static final int LAYERS = 2;
    private static final int ROWS = 5;
    private static final int COLUMNS = 2;
    private static final long ROW_INTERVAL_TICKS = 8L;
    private static final long COLUMN_INTERVAL_TICKS = 2L;

    public AngrySpearItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BRUSH;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 60;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onUseTick(Level level, LivingEntity livingEntity, ItemStack stack, int count) {
        if (!(livingEntity instanceof Player player) || level.getGameTime() % 60 != 0) {
            return;
        }
        player.stopUsingItem();
        if (!level.isClientSide()) {
            for (int layer = 0; layer < LAYERS; layer++) {
                for (int row = 0; row < ROWS; row++) {
                    for (int column = 0; column < COLUMNS; column++) {
                        int finalRow = row;
                        int finalColumn = column;
                        long delay = (layer * ROWS + row) * ROW_INTERVAL_TICKS
                                + column * COLUMN_INTERVAL_TICKS;
                        FxTickScheduler.scheduleServer(delay,
                                () -> level.explode(player, player.getX(),
                                        player.getY() + 4 + finalRow * 3, player.getZ() - finalColumn,
                                        4.0F, true, Level.ExplosionInteraction.TNT));
                    }
                }
            }
            return;
        }
        AngryClientHooks.spearEffect.run(level, livingEntity);
    }
}
