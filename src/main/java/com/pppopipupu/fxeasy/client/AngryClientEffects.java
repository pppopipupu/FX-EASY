package com.pppopipupu.fxeasy.client;

import module java.base;

import com.pppopipupu.fxeasy.FxTickScheduler;
import com.pppopipupu.fxeasy.content.AngryEnergy;
import com.pppopipupu.fxeasy.content.AngryHealth;
import com.pppopipupu.fxeasy.content.FxEntities;
import com.pppopipupu.fxeasy.content.block.AngryBlock;
import com.pppopipupu.fxeasy.content.block.AngryFemaleBlock;
import com.pppopipupu.fxeasy.content.block.MultiPartBlock;
import com.pppopipupu.fxeasy.content.block.entity.AngryFemaleEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class AngryClientEffects {

    private static final float ROTATION_SPEED = 12.0F;
    private static final float LERP_FACTOR = 0.86F;
    private static final int SPEAR_STEPS = 15;
    private static final long SPEAR_STEP_TICKS = 8L;

    public static void tickFemale(AngryFemaleEntity entity, Level level, BlockPos pos, BlockState state) {
        entity.prevRotationAngle = entity.rotationAngle;
        HitResult hitResult = Minecraft.getInstance().hitResult;
        if (level.getGameTime() % 20 == 0 && !entity.is_jiaopei) {
            BlockPos.betweenClosed(pos.offset(-2, -2, -2), pos.offset(2, 2, 2)).forEach(blockPos -> {
                BlockState neighbour = level.getBlockState(blockPos);
                if (neighbour.getBlock() instanceof AngryBlock
                        && !neighbour.getValue(AngryBlock.IS_ATOMIC)
                        && neighbour.getValue(AngryBlock.IS_CORE)) {
                    entity.is_jiaopei = true;
                    entity.jiaopeiTime = 100;
                }
            });
        }
        boolean lookingAt = false;
        if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHitResult = (BlockHitResult) hitResult;
            BlockPos lookedAtPos = blockHitResult.getBlockPos();
            BlockState lookedAtState = level.getBlockState(lookedAtPos);
            Block lookedAtBlock = lookedAtState.getBlock();
            if (lookedAtBlock instanceof MultiPartBlock multiPartBlock
                    && multiPartBlock.getCorePos(lookedAtState, lookedAtPos).equals(pos)) {
                lookingAt = true;
            }
        }

        if (entity.is_jiaopei) {
            entity.rotationAngle += ROTATION_SPEED * 5;
        } else if (lookingAt) {
            entity.rotationAngle += ROTATION_SPEED;
        } else {
            entity.rotationAngle *= LERP_FACTOR;
            if (Math.abs(entity.rotationAngle) < 0.1F) {
                entity.rotationAngle = 0.0F;
            }
        }

        if (state.getValue(AngryFemaleBlock.IS_LOVE)) {
            int mode = AngryPostEffects.mode();
            if (mode != 0 && lookingAt) {
                AngryPostEffects.set(0);
            } else if (mode == 0 && !lookingAt && entity.prevTickFlag) {
                AngryPostEffects.set(-1);
            }
            entity.prevTickFlag = AngryPostEffects.mode() == 0;
            double centerX = pos.getX() + 0.5;
            double centerY = pos.getY() + 0.5;
            double centerZ = pos.getZ() + 0.5;
            double randomX = centerX + (level.getRandom().nextDouble() - 0.5) * 3.0;
            double randomY = centerY + (level.getRandom().nextDouble() - 0.5) * 3.0;
            double randomZ = centerZ + (level.getRandom().nextDouble() - 0.5) * 3.0;
            level.addParticle(ParticleTypes.HEART, randomX, randomY, randomZ, 0.0, 1.0, 0.0);
            entity.prevTickFlag = lookingAt;
        }
    }

    public static void spear(Level level, LivingEntity user) {
        AngryPostEffects.set(1);
        for (int step = 0; step < SPEAR_STEPS; step++) {
            FxTickScheduler.scheduleClient(step * SPEAR_STEP_TICKS, () -> {
                if (Minecraft.getInstance().level != level || user.isRemoved()) {
                    return;
                }
                user.playSound(SoundEvents.ENDERMAN_DEATH, 10.0F, 1.0F);
                for (int i = 0; i < 3; i++) {
                    level.addParticle(FxEntities.SURPRISE_PARTICLE.get(),
                            user.getX(), user.getY(), user.getZ(), i, 0, 0);
                }
            });
        }
        FxTickScheduler.scheduleClient(SPEAR_STEPS * SPEAR_STEP_TICKS, () -> AngryPostEffects.set(-1));
    }

    public static void wand(Level level, LivingEntity user) {
        BlockPos source = AngryEnergy.nearestBlock(level, user, 6);
        if (source == null) {
            return;
        }
        double centerX = source.getX() + 0.5;
        double centerY = source.getY() + 0.5;
        double centerZ = source.getZ() + 0.5;
        for (int i = 0; i < 3; i++) {
            double x = centerX + (level.getRandom().nextDouble() - 0.5) * 0.9;
            double y = centerY + (level.getRandom().nextDouble() - 0.5) * 0.9;
            double z = centerZ + (level.getRandom().nextDouble() - 0.5) * 0.9;
            level.addParticle(FxEntities.ANGRY_PARTICLE_WAND.get(), x, y, z, user.getId(), 0, 0);
        }
    }

    public static void wandRelease(Level level, LivingEntity user) {
        if (!(user instanceof Player player)) {
            return;
        }
        float pool = AngryHealth.get(player);
        if (pool <= 0.0F) {
            return;
        }
        int count = 12 + Math.min(18, Math.round(pool));
        for (int wave = 0; wave < 3; wave++) {
            long delay = wave * 2L;
            FxTickScheduler.scheduleClient(delay, () -> {
                if (Minecraft.getInstance().level != level || player.isRemoved()) {
                    return;
                }
                for (int i = 0; i < count; i++) {
                    double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
                    double pitch = (level.getRandom().nextDouble() - 0.5) * 0.8;
                    double distance = 4.0 + level.getRandom().nextDouble() * 4.0;
                    double x = player.getX();
                    double y = player.getY() + 1.0;
                    double z = player.getZ();
                    double dx = Math.cos(angle) * Math.cos(pitch) * distance;
                    double dy = Math.sin(pitch) * distance;
                    double dz = Math.sin(angle) * Math.cos(pitch) * distance;
                    level.addParticle(FxEntities.ANGRY_SURGE_PARTICLE.get(), x, y, z,
                            x + dx, y + dy, z + dz);
                }
            });
        }
    }

    private AngryClientEffects() {
    }
}
