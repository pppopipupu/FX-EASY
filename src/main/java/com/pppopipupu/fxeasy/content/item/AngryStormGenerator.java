package com.pppopipupu.fxeasy.content.item;

import module java.base;

import com.pppopipupu.fxeasy.content.AngryHealth;
import com.pppopipupu.fxeasy.content.entity.AngryStorm;
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

/// Summons a 45-second seven-phase Storm of Vengeance at the targeted ground location, consuming any stored Angry
/// Health to amplify its radius and damage.
public class AngryStormGenerator extends Item {

    private static final double RANGE = 96.0;
    private static final double FALLBACK_DISTANCE = 40.0;
    private static final int COOLDOWN_TICKS = 900;

    public AngryStormGenerator(Item.Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
            Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.fxeasy.angry_storm_generator"));
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
        float pool = AngryHealth.get(player);
        float amplifier = 1.0F + (pool / AngryHealth.MAX) * 0.75F;
        if (pool > 0.0F) {
            AngryHealth.set(player, 0.0F);
        }
        AngryStorm storm = new AngryStorm(serverLevel,
                target.getX() + 0.5, target.getY(), target.getZ() + 0.5, player, amplifier);
        serverLevel.addFreshEntity(storm);
        serverLevel.playSound(null, target, SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.PLAYERS, 3.0F, 0.6F);
        serverLevel.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, 1.5F, 0.8F);
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
