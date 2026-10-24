package com.pppopipupu.fxeasy.content.block.entity;

import module java.base;

import com.pppopipupu.fxeasy.content.FxBlocks;
import com.pppopipupu.fxeasy.content.FxEntities;
import com.pppopipupu.fxeasy.content.block.AngryBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class AngryBlockEntity extends BlockEntity {

    private final List<AngryLightingBolt> activeBolts = new ArrayList<>();
    private final RandomSource random = RandomSource.create();
    private int unstableTicks;
    private int collapseLimit;

    public AngryBlockEntity(BlockPos pos, BlockState state) {
        super(FxBlocks.ANGRY_BLOCK_ENTITY.get(), pos, state);
    }

    public static void tickClient(Level level, BlockPos pos, BlockState state, AngryBlockEntity blockEntity) {
        if (state.getValue(AngryBlock.IS_UNSTABLE)) {
            blockEntity.unstableTicks++;
            blockEntity.tickLightingBolts();
            if (blockEntity.unstableTicks % 2 == 0) {
                level.addParticle(FxEntities.ANGRY_PARTICLE.get(),
                        pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0,
                        (blockEntity.random.nextDouble() - 0.5) * 0.25,
                        0.08 + blockEntity.random.nextDouble() * 0.15,
                        (blockEntity.random.nextDouble() - 0.5) * 0.25);
                level.addParticle(FxEntities.SURPRISE_PARTICLE.get(),
                        pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0,
                        (blockEntity.random.nextDouble() - 0.5) * 0.25,
                        0.08 + blockEntity.random.nextDouble() * 0.15,
                        (blockEntity.random.nextDouble() - 0.5) * 0.25);
            }
        }
        if (state.getValue(AngryBlock.IS_LIGHTNING)) {
            blockEntity.tickLightingBolts();
        }
        if (state.getValue(AngryBlock.IS_ATOMIC)) {
            level.addParticle(FxEntities.ANGRY_PARTICLE.get(), pos.getX(), pos.getY(), pos.getZ(), 0.0, 0.0, 0.0);
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, AngryBlockEntity blockEntity) {
        if (state.getValue(AngryBlock.IS_UNSTABLE)) {
            if (blockEntity.collapseLimit <= 0) {
                blockEntity.collapseLimit = 200 + blockEntity.random.nextInt(801);
            }
            blockEntity.unstableTicks++;
            Vec3 center = new Vec3(pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
            AABB pullBox = new AABB(pos).inflate(6.0);
            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, pullBox, entity ->
                    !(entity instanceof Player player && (player.isCreative() || player.isSpectator()))
            );
            for (LivingEntity entity : targets) {
                Vec3 pull = center.subtract(entity.position());
                double len = pull.length();
                if (len > 1.0E-4) {
                    Vec3 dir = pull.scale(0.06 / len);
                    entity.setDeltaMovement(entity.getDeltaMovement().add(dir));
                    entity.hurtMarked = true;
                }
                if (blockEntity.unstableTicks % 10 == 0) {
                    entity.setHealth(entity.getHealth() - 1.0F);
                }
            }
            if (blockEntity.unstableTicks >= blockEntity.collapseLimit) {
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.removeBlock(pos, false);
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                BlockPos check = pos.offset(dx, dy, dz);
                                if (serverLevel.getBlockState(check).getBlock() instanceof AngryBlock) {
                                    serverLevel.removeBlock(check, false);
                                }
                            }
                        }
                    }
                    serverLevel.explode(null, center.x, center.y, center.z, 4.5F, Level.ExplosionInteraction.TNT);
                    serverLevel.sendParticles(FxEntities.ANGRY_THUNDER_PARTICLE.get(),
                            center.x, center.y, center.z, 20, 1.8, 1.8, 1.8, 0.1);
                    serverLevel.sendParticles(FxEntities.ANGRY_PARTICLE.get(),
                            center.x, center.y, center.z, 12, 1.5, 1.5, 1.5, 0.1);
                    serverLevel.sendParticles(FxEntities.SURPRISE_PARTICLE.get(),
                            center.x, center.y, center.z, 10, 1.5, 1.5, 1.5, 0.1);
                    serverLevel.sendParticles(ParticleTypes.SONIC_BOOM,
                            center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
                    serverLevel.playSound(null, center.x, center.y, center.z,
                            SoundEvents.WARDEN_SONIC_BOOM, SoundSource.BLOCKS, 3.0F, 0.7F);
                }
                return;
            }
        }
        AABB aabb = new AABB(
                pos.getX() - 3, level.getMinY(), pos.getZ() - 3,
                pos.getX() + 3, level.getMaxY(), pos.getZ() + 3
        );
        List<LightningBolt> lightningBolts = level.getEntitiesOfClass(LightningBolt.class, aabb);
        if (!lightningBolts.isEmpty() && !state.getValue(AngryBlock.IS_LIGHTNING)) {
            level.setBlockAndUpdate(pos, state.setValue(AngryBlock.IS_LIGHTNING, true));
        }
        if (state.getValue(AngryBlock.IS_LIGHTNING)) {
            AABB forceFieldAABB = new AABB(pos).inflate(6.0);
            List<LivingEntity> nearbyEntities = level.getEntitiesOfClass(LivingEntity.class, forceFieldAABB, entity ->
                    !(entity instanceof Player player && (player.isCreative() || player.isSpectator()))
            );
            for (LivingEntity entity : nearbyEntities) {
                entity.setHealth(entity.getHealth() - 1.5F);
            }
        }
        if (state.getValue(AngryBlock.IS_ATOMIC)) {
            AABB forceFieldAABB = new AABB(pos).inflate(10.0);
            List<LivingEntity> nearbyEntities = level.getEntitiesOfClass(LivingEntity.class, forceFieldAABB);
            for (LivingEntity entity : nearbyEntities) {
                entity.setHealth(entity.getHealth() - 1.0F);
            }
        }
    }

    private void tickLightingBolts() {
        for (AngryLightingBolt bolt : activeBolts) {
            bolt.update();
        }
        activeBolts.removeIf(bolt -> bolt.life <= 0);

        int boltsToSpawn = 2 + this.random.nextInt(3);
        for (int i = 0; i < boltsToSpawn; i++) {
            if (activeBolts.size() < 40) {
                spawnNewBolt();
            }
        }
    }

    private void spawnNewBolt() {
        float distance = 2.8F + random.nextFloat() * 3.0F;
        Vector3f endPoint = new Vector3f(
                random.nextFloat() - 0.5F,
                random.nextFloat() - 0.5F,
                random.nextFloat() - 0.5F
        ).normalize().mul(distance);

        int rgb = Mth.hsvToRgb(random.nextFloat(), 0.9F, 1.0F);
        float red = ((rgb >> 16) & 0xFF) / 255.0F;
        float green = ((rgb >> 8) & 0xFF) / 255.0F;
        float blue = (rgb & 0xFF) / 255.0F;

        int life = 15 + random.nextInt(20);
        float thickness = 0.06F + random.nextFloat() * 0.04F;

        activeBolts.add(new AngryLightingBolt(
                new Vector3f(0.0F, 0.0F, 0.0F), endPoint, 15, 0.4F, thickness, life, red, green, blue, 3, this.random
        ));
    }

    public List<AngryLightingBolt> getActiveBolts() {
        return activeBolts;
    }

    public int getUnstableTicks() {
        return unstableTicks;
    }

    public int getCollapseLimit() {
        return collapseLimit;
    }

    public void setCollapseLimit(int collapseLimit) {
        this.collapseLimit = collapseLimit;
    }
}
