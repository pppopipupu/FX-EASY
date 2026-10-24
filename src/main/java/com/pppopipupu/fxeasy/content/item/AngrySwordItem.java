package com.pppopipupu.fxeasy.content.item;

import module java.base;

import com.pppopipupu.fxeasy.content.block.AngryFemaleBlock;
import com.pppopipupu.fxeasy.content.block.MultiPartBlock;
import com.pppopipupu.fxeasy.content.entity.AngryFireBall;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class AngrySwordItem extends Item {

    public AngrySwordItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
            Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.fxeasy.angry_sword"));
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BOW;
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
    public void onUseTick(Level level, LivingEntity livingEntity, ItemStack stack, int count) {
        if (level.isClientSide() || !(livingEntity instanceof Player player)) {
            return;
        }
        if (level.getGameTime() % 20 != 0) {
            return;
        }
        AABB area = player.getBoundingBox().inflate(11.4);
        AtomicBoolean loved = new AtomicBoolean(false);
        BlockPos.betweenClosedStream(area).forEach(pos -> {
            BlockState blockState = level.getBlockState(pos);
            if (blockState.getBlock() instanceof AngryFemaleBlock
                    && blockState.getValue(MultiPartBlock.IS_CORE)
                    && !blockState.getValue(AngryFemaleBlock.IS_LOVE)) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_DRAGON_DEATH,
                        SoundSource.PLAYERS, 1.0F, 1.0F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
                level.setBlock(pos, blockState.setValue(AngryFemaleBlock.IS_LOVE, true), 3);
                loved.set(true);
            }
        });
        if (loved.get()) {
            return;
        }
        for (LivingEntity target : findTarget(player, 51.4F)) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GHAST_SHOOT,
                    SoundSource.PLAYERS, 1.0F, 1.0F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
            level.addFreshEntity(new AngryFireBall(level, player, target));
            player.awardStat(Stats.ITEM_USED.get(this));
        }
    }

    public static List<LivingEntity> findTarget(Entity source, double range) {
        Level level = source.level();
        AABB searchArea = source.getBoundingBox().inflate(range);
        return level.getEntitiesOfClass(LivingEntity.class, searchArea,
                entity -> entity != source && entity.isAlive());
    }
}
