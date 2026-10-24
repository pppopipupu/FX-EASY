package com.pppopipupu.fxeasy.content.entity;

import module java.base;

import com.pppopipupu.fxeasy.content.FxBlocks;
import com.pppopipupu.fxeasy.content.FxEntities;
import com.pppopipupu.fxeasy.content.block.AngryBlock;
import com.pppopipupu.fxeasy.content.block.MultiPartBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/// A large Angry block that falls straight down, explodes on the first block it touches and seeds the crater with
/// a field of Angry cores. The field is placed after the explosion so the explosion cannot remove it again.
public class AngryMeteor extends ThrowableProjectile {

    private static final float EXPLOSION_POWER = 7.0F;
    private static final int FIELD_RADIUS = 6;
    private static final int FIELD_STEP = 2;
    private static final float FIELD_SKIP = 0.10F;
    private static final float LIGHTNING_CHANCE = 0.12F;
    private static final float ATOMIC_CHANCE = 0.06F;
    private static final int MAX_LIFE = 240;

    public float spinY;
    public float spinX;
    public float spinYO;
    public float spinXO;

    private int life;
    private boolean impacted;

    public AngryMeteor(EntityType<? extends ThrowableProjectile> entityType, Level level) {
        super(entityType, level);
    }

    public AngryMeteor(Level level, double x, double y, double z, @Nullable LivingEntity owner) {
        this(FxEntities.ANGRY_METEOR.get(), level);
        this.setOwner(owner);
        this.setPos(x, y, z);
        this.setDeltaMovement(0.0, -1.2, 0.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
    }

    @Override
    protected double getDefaultGravity() {
        return 0.12;
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isRemoved()) {
            return;
        }
        this.spinYO = this.spinY;
        this.spinXO = this.spinX;
        this.spinY += 10.0F;
        this.spinX += 6.0F;
        if (this.level().isClientSide()) {
            this.level().addParticle(FxEntities.ANGRY_THUNDER_PARTICLE.get(),
                    this.getX(), this.getY() + 1.0, this.getZ(), 0.0, 0.0, 0.0);
            if (this.tickCount % 2 == 0) {
                this.level().addParticle(ParticleTypes.FLAME,
                        this.getX(), this.getY() + 1.0, this.getZ(), 0.0, -0.1, 0.0);
            }
            return;
        }
        if (this.life++ > MAX_LIFE) {
            if (this.level() instanceof ServerLevel serverLevel) {
                this.impact(serverLevel, this.position(), this.blockPosition());
            }
            this.discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult hitResult) {
        if (this.level() instanceof ServerLevel serverLevel) {
            this.impact(serverLevel, hitResult.getLocation(), hitResult.getBlockPos());
        }
        this.discard();
    }

    private void impact(ServerLevel level, Vec3 position, BlockPos center) {
        if (this.impacted) {
            return;
        }
        this.impacted = true;
        level.explode(this, position.x, position.y, position.z, EXPLOSION_POWER, Level.ExplosionInteraction.TNT);
        this.placeField(level, center);
        level.sendParticles(FxEntities.ANGRY_THUNDER_PARTICLE.get(),
                position.x, position.y + 1.0, position.z, 24, 3.0, 2.0, 3.0, 0.0);
        level.sendParticles(FxEntities.ANGRY_PARTICLE.get(),
                position.x, position.y + 2.0, position.z, 8, 2.0, 0.5, 2.0, 0.0);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                position.x, position.y + 1.0, position.z, 2, 2.0, 0.5, 2.0, 0.0);
        level.playSound(null, position.x, position.y, position.z, SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.WEATHER, 8.0F, 0.6F);
    }

    private void placeField(ServerLevel level, BlockPos center) {
        int minY = level.getMinY();
        int maxY = level.getMaxY();
        for (int dx = -FIELD_RADIUS; dx <= FIELD_RADIUS; dx += FIELD_STEP) {
            for (int dz = -FIELD_RADIUS; dz <= FIELD_RADIUS; dz += FIELD_STEP) {
                if (dx * dx + dz * dz > FIELD_RADIUS * FIELD_RADIUS) {
                    continue;
                }
                if (this.random.nextFloat() < FIELD_SKIP) {
                    continue;
                }
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                int y = Math.abs(surface - center.getY()) <= 12 ? surface : center.getY();
                y = Mth.clamp(y, minY + 2, maxY - 2);
                BlockPos pos = new BlockPos(x, y, z);
                if (!level.getBlockState(pos).canBeReplaced()) {
                    pos = pos.above();
                }
                if (pos.getY() >= maxY - 2 || !level.getBlockState(pos).canBeReplaced()) {
                    continue;
                }
                this.placeCore(level, pos);
            }
        }
        BlockPos centerCore = center.above();
        for (int i = 0; i < 4 && !level.getBlockState(centerCore).canBeReplaced(); i++) {
            centerCore = centerCore.above();
        }
        if (centerCore.getY() < maxY - 2 && level.getBlockState(centerCore).canBeReplaced()) {
            this.placeCore(level, centerCore);
        }
    }

    private void placeCore(ServerLevel level, BlockPos pos) {
        float roll = this.random.nextFloat();
        BlockState state = FxBlocks.ANGRY_BLOCK.get().defaultBlockState()
                .setValue(MultiPartBlock.IS_CORE, true)
                .setValue(MultiPartBlock.FACING, Direction.from2DDataValue(this.random.nextInt(4)))
                .setValue(AngryBlock.IS_LIGHTNING, roll < LIGHTNING_CHANCE)
                .setValue(AngryBlock.IS_ATOMIC, roll >= LIGHTNING_CHANCE
                        && roll < LIGHTNING_CHANCE + ATOMIC_CHANCE);
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }
}
