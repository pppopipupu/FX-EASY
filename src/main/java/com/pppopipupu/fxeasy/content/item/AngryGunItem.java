package com.pppopipupu.fxeasy.content.item;

import module java.base;

import com.pppopipupu.fxeasy.FxTickScheduler;
import com.pppopipupu.fxeasy.content.FxEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class AngryGunItem extends Item {

    private static final int BURST_COUNT = 10;
    private static final long BURST_INTERVAL_TICKS = 6L;
    private static final int BURST_START_TICKS = 60;
    private static final int ANGLE_STEP = 20;
    private static final int ANGLE_LIMIT = 1080;
    private static final long ANGLE_INTERVAL_TICKS = 2L;
    private static final int RING_LENGTH = 8;
    private static final double RING_STEP = 6.0;

    public AngryGunItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        player.playSound(SoundEvents.ANVIL_PLACE, 1.0F, 1.0F);
        if (level instanceof ServerLevel serverLevel) {
            HitResult hitResult = player.pick(144.0, 0.0F, false);
            if (hitResult instanceof BlockHitResult blockHitResult
                    && blockHitResult.getType() == HitResult.Type.BLOCK) {
                Vec3 hitPos = blockHitResult.getLocation();
                for (int burst = 0; burst < BURST_COUNT; burst++) {
                    FxTickScheduler.scheduleServer(burst * BURST_INTERVAL_TICKS,
                            () -> serverLevel.sendParticles(FxEntities.SURPRISE_PARTICLE.get(),
                                    hitPos.x(), hitPos.y() + 0.5, hitPos.z(), 50, 0.2, 0.2, 0.2, 0.1));
                }
                for (int angle = 0; angle < ANGLE_LIMIT; angle += ANGLE_STEP) {
                    int finalAngle = angle;
                    FxTickScheduler.scheduleServer(
                            BURST_START_TICKS + angle / ANGLE_STEP * ANGLE_INTERVAL_TICKS,
                            () -> {
                                double radians = Math.toRadians(finalAngle);
                                double dx = Math.cos(radians);
                                double dz = Math.sin(radians);
                                for (int i = 1; i <= RING_LENGTH; i++) {
                                    double explosionX = hitPos.x() + dx * i * RING_STEP;
                                    double explosionY = hitPos.y();
                                    double explosionZ = hitPos.z() + dz * i * RING_STEP;
                                    serverLevel.explode(null, explosionX, explosionY, explosionZ, 4.0F,
                                            Level.ExplosionInteraction.MOB);
                                    serverLevel.sendParticles(FxEntities.ANGRY_THUNDER_PARTICLE.get(),
                                            explosionX, explosionY, explosionZ, 1, 0.0, 0.0, 0.0, 0.0);
                                }
                            });
                }
            }
        }
        player.getCooldowns().addCooldown(player.getItemInHand(hand), 60);
        return InteractionResult.SUCCESS;
    }
}
