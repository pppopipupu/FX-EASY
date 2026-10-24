package com.pppopipupu.fxeasy.content.item;

import module java.base;

import com.pppopipupu.fxeasy.content.entity.AngryMeteor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/// Calls a meteor down on the block the player looks at, or on the ground ahead when the look ray reaches no block.
public class AngryMeteorSummoner extends Item {

    private static final double RANGE = 96.0;
    private static final double FALLBACK_DISTANCE = 48.0;
    private static final int SPAWN_HEIGHT = 72;
    private static final int COOLDOWN_TICKS = 60;

    public AngryMeteorSummoner(Item.Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
            Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.fxeasy.angry_meteor_summoner"));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }
        ItemStack stack = player.getItemInHand(hand);
        BlockPos target = target(serverLevel, player);
        double spawnY = Math.min(serverLevel.getMaxY() - 4.0, target.getY() + (double) SPAWN_HEIGHT);
        AngryMeteor meteor = new AngryMeteor(serverLevel,
                target.getX() + 0.5, spawnY, target.getZ() + 0.5, player);
        serverLevel.addFreshEntity(meteor);
        serverLevel.playSound(null, target, SoundEvents.ENDER_DRAGON_GROWL,
                SoundSource.PLAYERS, 2.0F, 0.6F);
        serverLevel.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE,
                SoundSource.PLAYERS, 1.0F, 0.7F);
        player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResult.SUCCESS;
    }

    private static BlockPos target(ServerLevel level, Player player) {
        HitResult hitResult = player.pick(RANGE, 0.0F, false);
        if (hitResult instanceof BlockHitResult blockHit && hitResult.getType() == HitResult.Type.BLOCK) {
            return BlockPos.containing(blockHit.getLocation());
        }
        Vec3 look = player.getLookAngle();
        int x = Mth.floor(player.getX() + look.x * FALLBACK_DISTANCE);
        int z = Mth.floor(player.getZ() + look.z * FALLBACK_DISTANCE);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new BlockPos(x, y, z);
    }
}
