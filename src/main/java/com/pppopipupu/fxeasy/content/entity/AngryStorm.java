package com.pppopipupu.fxeasy.content.entity;

import module java.base;

import com.pppopipupu.fxeasy.content.FxBlocks;
import com.pppopipupu.fxeasy.content.FxEntities;
import com.pppopipupu.fxeasy.content.block.AngryBlock;
import com.pppopipupu.fxeasy.content.block.MultiPartBlock;
import com.pppopipupu.fxeasy.content.block.entity.AngryBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/// A 45-second (900-tick) seven-phase Storm of Vengeance anchored at a target location.
public class AngryStorm extends ThrowableProjectile {

    public static final int TOTAL_DURATION = 900;
    public static final int PHASE_1_END = 110;
    public static final int PHASE_2_END = 250;
    public static final int PHASE_3_END = 390;
    public static final int PHASE_4_END = 510;
    public static final int PHASE_5_END = 630;
    public static final int PHASE_6_END = 790;
    public static final float BASE_RADIUS = 36.0F;
    public static final double CANOPY_HEIGHT = 34.0;
    public static final double VOID_CORE_HEIGHT = 12.0;
    public static final float VOID_RADIUS = 6.0F;

    private static final EntityDataAccessor<Float> DATA_AMPLIFIER =
            SynchedEntityData.defineId(AngryStorm.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_OWNER_ID =
            SynchedEntityData.defineId(AngryStorm.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STORM_AGE =
            SynchedEntityData.defineId(AngryStorm.class, EntityDataSerializers.INT);

    private int age;

    public AngryStorm(EntityType<? extends ThrowableProjectile> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
    }

    public AngryStorm(Level level, double x, double y, double z, @Nullable LivingEntity owner, float amplifier) {
        this(FxEntities.ANGRY_STORM.get(), level);
        this.setOwner(owner);
        this.entityData.set(DATA_OWNER_ID, owner != null ? owner.getId() : -1);
        this.entityData.set(DATA_AMPLIFIER, Math.clamp(amplifier, 1.0F, 2.5F));
        this.setPos(x, y, z);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_AMPLIFIER, 1.0F);
        builder.define(DATA_OWNER_ID, -1);
        builder.define(DATA_STORM_AGE, 0);
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0;
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return false;
    }

    @Override
    protected void onHitBlock(BlockHitResult hitResult) {
    }

    public int getStormAge() {
        if (this.level().isClientSide()) {
            return Math.max(this.tickCount, this.entityData.get(DATA_STORM_AGE));
        }
        return this.age;
    }

    public int phase() {
        int stormAge = this.getStormAge();
        if (stormAge < PHASE_1_END) {
            return 1;
        }
        if (stormAge < PHASE_2_END) {
            return 2;
        }
        if (stormAge < PHASE_3_END) {
            return 3;
        }
        if (stormAge < PHASE_4_END) {
            return 4;
        }
        if (stormAge < PHASE_5_END) {
            return 5;
        }
        if (stormAge < PHASE_6_END) {
            return 6;
        }
        return 7;
    }

    public float getAmplifier() {
        return this.entityData.get(DATA_AMPLIFIER);
    }

    public void setAmplifier(float amplifier) {
        this.entityData.set(DATA_AMPLIFIER, Math.clamp(amplifier, 1.0F, 2.5F));
    }

    public int getOwnerEntityId() {
        return this.entityData.get(DATA_OWNER_ID);
    }

    public float radius() {
        return BASE_RADIUS * this.getAmplifier();
    }

    public Vec3 voidCenter() {
        return new Vec3(this.getX(), this.getY() + VOID_CORE_HEIGHT, this.getZ());
    }

    public float voidRadius() {
        return VOID_RADIUS * this.getAmplifier();
    }

    public float pullRadius() {
        return this.radius();
    }

    public boolean isImmune(Entity entity) {
        if (entity == this || entity == this.getOwner()) {
            return true;
        }
        int ownerId = this.getOwnerEntityId();
        if (ownerId != -1 && entity.getId() == ownerId) {
            return true;
        }
        if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return true;
        }
        return false;
    }

    @Override
    public void tick() {
        super.baseTick();
        this.setDeltaMovement(Vec3.ZERO);
        if (this.level().isClientSide()) {
            this.age = Math.max(this.tickCount, this.entityData.get(DATA_STORM_AGE));
            this.tickClientParticles();
            return;
        }
        this.age++;
        this.entityData.set(DATA_STORM_AGE, this.age);
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (this.age >= TOTAL_DURATION) {
            this.triggerFinale(serverLevel);
            this.discard();
            return;
        }
        if (this.age == PHASE_6_END) {
            this.tickPhase6(serverLevel);
            return;
        }
        int currentPhase = this.phase();
        switch (currentPhase) {
            case 1 -> this.tickPhase1(serverLevel);
            case 2 -> this.tickPhase2(serverLevel);
            case 3 -> this.tickPhase3(serverLevel);
            case 4 -> this.tickPhase4(serverLevel);
            case 5 -> this.tickPhase5(serverLevel);
            case 6 -> this.tickPhase6(serverLevel);
            default -> this.tickPhase7(serverLevel);
        }
    }

    private void tickClientParticles() {
        float r = this.radius();
        int currentPhase = this.phase();
        for (int i = 0; i < 4; i++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0;
            double dist = Math.sqrt(this.random.nextDouble()) * r;
            double px = this.getX() + Math.cos(angle) * dist;
            double pz = this.getZ() + Math.sin(angle) * dist;
            double py = this.getY() + CANOPY_HEIGHT + (this.random.nextDouble() - 0.5) * 3.0;
            this.level().addParticle(ParticleTypes.CLOUD, px, py, pz,
                    -Math.sin(angle) * 0.15, -0.02, Math.cos(angle) * 0.15);
            if (i % 2 == 0) {
                this.level().addParticle(FxEntities.ANGRY_THUNDER_PARTICLE.get(), px, py - 1.5, pz, 0.0, -0.1, 0.0);
            } else {
                this.level().addParticle(FxEntities.ANGRY_PARTICLE.get(), px, py - 2.0, pz, 0.0, -0.15, 0.0);
            }
        }
        if (currentPhase == 2) {
            for (int i = 0; i < 3; i++) {
                double angle = this.random.nextDouble() * Math.PI * 2.0;
                double dist = Math.sqrt(this.random.nextDouble()) * r;
                double px = this.getX() + Math.cos(angle) * dist;
                double pz = this.getZ() + Math.sin(angle) * dist;
                double py = this.getY() + 2.0 + this.random.nextDouble() * CANOPY_HEIGHT;
                this.level().addParticle(ParticleTypes.WITCH, px, py, pz, 0.0, -0.35, 0.0);
                this.level().addParticle(FxEntities.SURPRISE_PARTICLE.get(), px, py, pz, 0.0, -0.45, 0.0);
            }
        } else if (currentPhase == 4) {
            for (int i = 0; i < 4; i++) {
                double angle = this.random.nextDouble() * Math.PI * 2.0;
                double dist = Math.sqrt(this.random.nextDouble()) * r;
                double px = this.getX() + Math.cos(angle) * dist;
                double pz = this.getZ() + Math.sin(angle) * dist;
                double py = this.getY() + 1.0 + this.random.nextDouble() * CANOPY_HEIGHT;
                this.level().addParticle(ParticleTypes.SNOWFLAKE, px, py, pz,
                        -Math.sin(angle) * 0.3, -0.25, Math.cos(angle) * 0.3);
            }
        } else if (currentPhase == 5) {
            for (int i = 0; i < 6; i++) {
                double angle = this.random.nextDouble() * Math.PI * 2.0;
                double dist = Math.sqrt(this.random.nextDouble()) * r;
                double px = this.getX() + Math.cos(angle) * dist;
                double pz = this.getZ() + Math.sin(angle) * dist;
                double py = this.getY() + CANOPY_HEIGHT * 0.8 + (this.random.nextDouble() - 0.5) * 4.0;
                if (i % 2 == 0) {
                    this.level().addParticle(ParticleTypes.FLAME, px, py, pz, 0.0, -0.7, 0.0);
                } else {
                    this.level().addParticle(ParticleTypes.LAVA, px, py, pz, 0.0, -0.7, 0.0);
                }
                this.level().addParticle(FxEntities.ANGRY_PARTICLE.get(), px, py, pz, 0.0, -0.55, 0.0);
            }
        } else if (currentPhase == 6) {
            Vec3 centre = this.voidCenter();
            double shell = this.voidRadius();
            for (int i = 0; i < 12; i++) {
                double theta = this.random.nextDouble() * Math.PI * 2.0;
                double phi = Math.acos(2.0 * this.random.nextDouble() - 1.0);
                double nx = Math.sin(phi) * Math.cos(theta);
                double ny = Math.cos(phi);
                double nz = Math.sin(phi) * Math.sin(theta);
                double px = centre.x + nx * shell;
                double py = centre.y + ny * shell;
                double pz = centre.z + nz * shell;
                this.level().addParticle(i % 2 == 0 ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.PORTAL,
                        px, py, pz, -nx * 0.6, -ny * 0.6, -nz * 0.6);
                if (i % 3 == 0) {
                    this.level().addParticle(FxEntities.ANGRY_PARTICLE.get(), px, py, pz,
                            -nx * 0.45, -ny * 0.45, -nz * 0.45);
                }
            }
        }
    }

    private AABB stormCylinder() {
        double r = this.radius();
        return new AABB(
                this.getX() - r, this.getY() - 8.0, this.getZ() - r,
                this.getX() + r, this.getY() + CANOPY_HEIGHT + 6.0, this.getZ() + r
        );
    }

    private List<LivingEntity> findTargets(ServerLevel level) {
        double r = this.radius();
        double rSq = r * r;
        return level.getEntitiesOfClass(LivingEntity.class, this.stormCylinder(), entity -> {
            if (!entity.isAlive() || this.isImmune(entity)) {
                return false;
            }
            double dx = entity.getX() - this.getX();
            double dz = entity.getZ() - this.getZ();
            return dx * dx + dz * dz <= rSq;
        });
    }

    private void tickPhase1(ServerLevel level) {
        if (this.age == 1 || this.age == 40 || this.age == 80) {
            level.playSound(null, this.getX(), this.getY() + CANOPY_HEIGHT, this.getZ(),
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 6.0F, 0.6F);
            level.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 3.0F, 0.7F);
            float damage = 6.0F * this.getAmplifier();
            for (LivingEntity target : this.findTargets(level)) {
                target.invulnerableTime = 0;
                target.hurtServer(level, this.damageSources().lightningBolt(), damage);
                target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0));
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 1));
                target.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0));
            }
            level.sendParticles(FxEntities.ANGRY_THUNDER_PARTICLE.get(),
                    this.getX(), this.getY() + CANOPY_HEIGHT * 0.5, this.getZ(),
                    28, this.radius() * 0.4, 6.0, this.radius() * 0.4, 0.05);
            level.sendParticles(FxEntities.ANGRY_PARTICLE.get(),
                    this.getX(), this.getY() + 2.0, this.getZ(),
                    16, this.radius() * 0.35, 2.0, this.radius() * 0.35, 0.04);
        }
    }

    private void tickPhase2(ServerLevel level) {
        float r = this.radius();
        if (this.age % 4 == 0) {
            for (int i = 0; i < 6; i++) {
                double angle = this.random.nextDouble() * Math.PI * 2.0;
                double dist = Math.sqrt(this.random.nextDouble()) * r;
                double px = this.getX() + Math.cos(angle) * dist;
                double pz = this.getZ() + Math.sin(angle) * dist;
                double py = this.getY() + 2.0 + this.random.nextDouble() * (CANOPY_HEIGHT - 2.0);
                level.sendParticles(FxEntities.ANGRY_PARTICLE.get(), px, py, pz, 2, 0.4, 0.8, 0.4, 0.02);
                level.sendParticles(FxEntities.SURPRISE_PARTICLE.get(), px, py, pz, 2, 0.4, 0.8, 0.4, 0.02);
                level.sendParticles(ParticleTypes.WITCH, px, py, pz, 3, 0.5, 0.8, 0.5, 0.05);
            }
        }
        if (this.age % 10 == 0) {
            float damage = 4.0F * this.getAmplifier();
            for (LivingEntity target : this.findTargets(level)) {
                target.invulnerableTime = 0;
                target.hurtServer(level, this.damageSources().magic(), damage);
                target.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1));
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0));
            }
            level.playSound(null, this.getX(), this.getY() + 2.0, this.getZ(),
                    SoundEvents.LAVA_EXTINGUISH, SoundSource.WEATHER, 2.0F, 0.8F);
        }
    }

    private void tickPhase3(ServerLevel level) {
        if (this.age % 20 != 0) {
            return;
        }
        List<LivingEntity> candidates = new ArrayList<>(this.findTargets(level));
        Collections.shuffle(candidates);
        List<Vec3> strikePoints = new ArrayList<>(6);
        for (int i = 0; i < Math.min(6, candidates.size()); i++) {
            strikePoints.add(candidates.get(i).position());
        }
        float r = this.radius();
        while (strikePoints.size() < 6) {
            double angle = this.random.nextDouble() * Math.PI * 2.0;
            double dist = Math.sqrt(this.random.nextDouble()) * (r * 0.85);
            int sx = Mth.floor(this.getX() + Math.cos(angle) * dist);
            int sz = Mth.floor(this.getZ() + Math.sin(angle) * dist);
            int sy = this.resolveSurfaceY(level, sx, sz);
            strikePoints.add(new Vec3(sx + 0.5, sy, sz + 0.5));
        }
        float damage = 14.0F * this.getAmplifier();
        for (int i = 0; i < strikePoints.size(); i++) {
            Vec3 pt = strikePoints.get(i);
            double tx = pt.x;
            double ty = pt.y;
            double tz = pt.z;
            for (double h = 0.0; h <= CANOPY_HEIGHT; h += 2.5) {
                level.sendParticles(FxEntities.ANGRY_THUNDER_PARTICLE.get(),
                        tx, ty + h, tz, 2, 0.25, 0.4, 0.25, 0.01);
                level.sendParticles(FxEntities.ANGRY_SURGE_PARTICLE.get(),
                        tx, ty + h, tz, 2, 0.2, 0.4, 0.2, 0.02);
            }
            AABB boltBox = new AABB(tx - 3.5, ty - 2.0, tz - 3.5, tx + 3.5, ty + 4.0, tz + 3.5);
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, boltBox,
                    e -> e.isAlive() && !this.isImmune(e))) {
                victim.invulnerableTime = 0;
                victim.hurtServer(level, this.damageSources().lightningBolt(), damage);
            }
            level.explode(this, tx, ty, tz, 2.5F, Level.ExplosionInteraction.TNT);
            level.playSound(null, tx, ty, tz, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER, 3.0F, 0.8F);
            level.playSound(null, tx, ty, tz, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 4.0F, 0.7F);
            if (i == 0) {
                placeUnstableCore(level, BlockPos.containing(tx, ty, tz), this.random, 200, 1000);
            }
        }
    }

    private void tickPhase4(ServerLevel level) {
        if (this.age % 8 != 0) {
            return;
        }
        float r = this.radius();
        float damage = 8.0F * this.getAmplifier();
        for (int i = 0; i < 3; i++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0;
            double dist = Math.sqrt(this.random.nextDouble()) * (r * 0.85);
            int ix = Mth.floor(this.getX() + Math.cos(angle) * dist);
            int iz = Mth.floor(this.getZ() + Math.sin(angle) * dist);
            int iy = this.resolveSurfaceY(level, ix, iz);
            double hx = ix + 0.5;
            double hy = iy;
            double hz = iz + 0.5;
            for (double h = 2.0; h <= CANOPY_HEIGHT; h += 4.0) {
                level.sendParticles(FxEntities.ANGRY_PARTICLE.get(), hx, hy + h, hz, 2, 0.4, 0.6, 0.4, 0.05);
                level.sendParticles(FxEntities.SURPRISE_PARTICLE.get(), hx, hy + h, hz, 2, 0.4, 0.6, 0.4, 0.05);
                level.sendParticles(ParticleTypes.SNOWFLAKE, hx, hy + h, hz, 4, 0.5, 0.6, 0.5, 0.08);
            }
            level.explode(this, hx, hy, hz, 3.0F, Level.ExplosionInteraction.TNT);
            AABB hailBox = new AABB(hx - 4.0, hy - 2.0, hz - 4.0, hx + 4.0, hy + 4.0, hz + 4.0);
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, hailBox,
                    e -> e.isAlive() && !this.isImmune(e))) {
                victim.invulnerableTime = 0;
                victim.hurtServer(level, this.damageSources().explosion(this, this.getOwner()), damage);
            }
            if (this.random.nextBoolean()) {
                placeUnstableCore(level, BlockPos.containing(hx, hy, hz), this.random, 200, 1000);
            }
        }
    }

    private void tickPhase5(ServerLevel level) {
        if (this.age % 12 != 0) {
            return;
        }
        float r = this.radius();
        List<LivingEntity> targets = this.findTargets(level);
        for (int i = 0; i < 2; i++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0;
            double dist = Math.sqrt(this.random.nextDouble()) * r * 0.8;
            double px = this.getX() + Math.cos(angle) * dist;
            double pz = this.getZ() + Math.sin(angle) * dist;
            double py = this.getY() + CANOPY_HEIGHT * 0.8;
            LivingEntity target = this.random.nextFloat() < 0.6F && !targets.isEmpty()
                    ? targets.get(this.random.nextInt(targets.size()))
                    : null;
            AngryFireBall fireBall = new AngryFireBall(FxEntities.ANGRY_FIREBALL.get(), level);
            if (this.getOwner() instanceof LivingEntity shooter) {
                fireBall.setOwner(shooter);
            }
            fireBall.setPos(px, py, pz);
            fireBall.setTarget(target);
            fireBall.setPower(2.5F + 0.5F * this.getAmplifier());
            fireBall.setSpeedScale(1.5F);
            fireBall.setDeltaMovement(0.0, -0.35, 0.0);
            level.addFreshEntity(fireBall);
            level.sendParticles(ParticleTypes.FLAME, px, py, pz, 8, 0.3, 0.3, 0.3, 0.02);
        }
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.BLAZE_SHOOT, SoundSource.WEATHER, 1.2F, 0.6F);
    }

    private void tickPhase6(ServerLevel level) {
        Vec3 center = this.voidCenter();
        float voidRadius = this.voidRadius();
        float pullRadius = this.pullRadius();
        if (this.age == PHASE_5_END + 1) {
            level.playSound(null, center.x, center.y, center.z,
                    SoundEvents.WARDEN_ROAR, SoundSource.WEATHER, 3.0F, 0.5F);
            level.playSound(null, center.x, center.y, center.z,
                    SoundEvents.PORTAL_TRAVEL, SoundSource.WEATHER, 2.0F, 0.4F);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z,
                    120, voidRadius * 0.6, voidRadius * 0.6, voidRadius * 0.6, 0.2);
        }
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, this.stormCylinder(),
                e -> e.isAlive() && !this.isImmune(e))) {
            Vec3 offset = center.subtract(target.position());
            double distance = offset.length();
            if (distance > pullRadius || distance < 1.0E-4) {
                continue;
            }
            if (distance > voidRadius) {
                double falloff = 1.0 - distance / pullRadius;
                target.setDeltaMovement(target.getDeltaMovement().scale(0.80)
                        .add(offset.normalize().scale(0.10 + 0.16 * falloff)));
                target.hurtMarked = true;
                target.resetFallDistance();
            } else {
                target.setDeltaMovement(Vec3.ZERO);
                target.hurtMarked = true;
                target.resetFallDistance();
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 6, false, false));
                target.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 20, 128, false, false));
            }
        }
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, this.stormCylinder())) {
            Vec3 offset = center.subtract(item.position());
            double distance = offset.length();
            if (distance > pullRadius || distance < 1.0E-4) {
                continue;
            }
            if (distance > voidRadius) {
                double falloff = 1.0 - distance / pullRadius;
                item.setDeltaMovement(item.getDeltaMovement().scale(0.80)
                        .add(offset.normalize().scale(0.10 + 0.16 * falloff)));
                item.hurtMarked = true;
                item.resetFallDistance();
            } else {
                level.sendParticles(ParticleTypes.PORTAL, item.getX(), item.getY() + 0.25, item.getZ(),
                        8, 0.2, 0.2, 0.2, 0.1);
                item.discard();
            }
        }
        if (this.age % 20 == 0) {
            float damage = 10.0F * this.getAmplifier();
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, this.stormCylinder(),
                    e -> e.isAlive() && !this.isImmune(e))) {
                if (target.position().distanceToSqr(center) > voidRadius * voidRadius) {
                    continue;
                }
                if (!(target instanceof Player) && target.getHealth() <= damage) {
                    level.sendParticles(FxEntities.ANGRY_PARTICLE.get(),
                            target.getX(), target.getY() + 1.0, target.getZ(), 20, 0.4, 0.6, 0.4, 0.05);
                    level.sendParticles(ParticleTypes.PORTAL,
                            target.getX(), target.getY() + 1.0, target.getZ(), 20, 0.4, 0.6, 0.4, 0.1);
                    target.discard();
                } else {
                    target.invulnerableTime = 0;
                    target.hurtServer(level, this.damageSources().indirectMagic(this, this.getOwner()), damage);
                    target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1));
                }
            }
            level.playSound(null, center.x, center.y, center.z,
                    SoundEvents.PORTAL_AMBIENT, SoundSource.WEATHER, 1.5F, 0.5F);
        }
        if (this.age == PHASE_6_END) {
            level.playSound(null, center.x, center.y, center.z,
                    SoundEvents.WARDEN_SONIC_BOOM, SoundSource.WEATHER, 3.0F, 0.5F);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z,
                    120, voidRadius, voidRadius, voidRadius, 0.0);
        }
    }

    private void tickPhase7(ServerLevel level) {
        List<Projectile> projectiles = level.getEntitiesOfClass(Projectile.class, this.stormCylinder(),
                proj -> !(proj instanceof AngryStorm) && !this.isImmune(proj));
        for (Projectile proj : projectiles) {
            double dx = proj.getX() - this.getX();
            double dz = proj.getZ() - this.getZ();
            double len = Math.hypot(dx, dz);
            double nx = len > 1.0E-4 ? dx / len : 1.0;
            double nz = len > 1.0E-4 ? dz / len : 0.0;
            double tx = -nz;
            double tz = nx;
            Vec3 flung = new Vec3(nx * 1.4 + tx * 0.9, 0.55, nz * 1.4 + tz * 0.9);
            proj.setDeltaMovement(flung);
            proj.hurtMarked = true;
        }
        float freezeDamage = 4.0F * this.getAmplifier();
        for (LivingEntity target : this.findTargets(level)) {
            double dx = this.getX() - target.getX();
            double dz = this.getZ() - target.getZ();
            double dist = Math.hypot(dx, dz);
            if (dist > 0.5) {
                double nx = dx / dist;
                double nz = dz / dist;
                double tx = -nz;
                double tz = nx;
                Vec3 swirl = new Vec3(nx * 0.14 + tx * 0.18, 0.04, nz * 0.14 + tz * 0.18);
                target.setDeltaMovement(target.getDeltaMovement().scale(0.75).add(swirl));
                target.hurtMarked = true;
            }
            target.setTicksFrozen(Math.min(target.getTicksRequiredToFreeze() + 40, target.getTicksFrozen() + 6));
            if (this.age % 10 == 0) {
                target.invulnerableTime = 0;
                target.hurtServer(level, this.damageSources().freeze(), freezeDamage);
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 2));
                target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0));
            }
        }
    }

    private void triggerFinale(ServerLevel level) {
        float amp = this.getAmplifier();
        float r = this.radius();
        Vec3 center = this.voidCenter();
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z,
                160, this.voidRadius(), this.voidRadius(), this.voidRadius(), 0.1);
        level.explode(this, this.getX(), this.getY(), this.getZ(), 7.0F * amp, Level.ExplosionInteraction.TNT);
        for (int i = 0; i < 6; i++) {
            double angle = i * (Math.PI * 2.0 / 6.0);
            double rx = this.getX() + Math.cos(angle) * (r * 0.45);
            double rz = this.getZ() + Math.sin(angle) * (r * 0.45);
            double ry = this.resolveSurfaceY(level, Mth.floor(rx), Mth.floor(rz));
            level.explode(this, rx, ry, rz, 4.0F, Level.ExplosionInteraction.TNT);
        }
        float finaleDamage = 20.0F * amp;
        for (LivingEntity target : this.findTargets(level)) {
            target.invulnerableTime = 0;
            target.hurtServer(level, this.damageSources().lightningBolt(), finaleDamage);
        }
        int count = 6 + this.random.nextInt(5);
        for (int i = 0; i < count; i++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0;
            double dist = 2.0 + this.random.nextDouble() * (r * 0.65);
            int cx = Mth.floor(this.getX() + Math.cos(angle) * dist);
            int cz = Mth.floor(this.getZ() + Math.sin(angle) * dist);
            int cy = this.resolveSurfaceY(level, cx, cz);
            placeUnstableCore(level, new BlockPos(cx, cy, cz), this.random, 200, 1000);
        }
        level.sendParticles(FxEntities.ANGRY_THUNDER_PARTICLE.get(),
                this.getX(), this.getY() + 2.0, this.getZ(), 60, r * 0.35, 4.0, r * 0.35, 0.12);
        level.sendParticles(FxEntities.ANGRY_PARTICLE.get(),
                this.getX(), this.getY() + 2.0, this.getZ(), 40, r * 0.35, 3.0, r * 0.35, 0.1);
        level.sendParticles(FxEntities.SURPRISE_PARTICLE.get(),
                this.getX(), this.getY() + 2.0, this.getZ(), 40, r * 0.35, 3.0, r * 0.35, 0.1);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                this.getX(), this.getY() + 1.0, this.getZ(), 6, 4.0, 1.5, 4.0, 0.0);
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.WARDEN_SONIC_BOOM, SoundSource.WEATHER, 5.0F, 0.6F);
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 8.0F, 0.5F);
    }

    private int resolveSurfaceY(ServerLevel level, int x, int z) {
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int base = Mth.floor(this.getY());
        int y = Math.abs(surface - base) <= 16 ? surface : base;
        return Mth.clamp(y, level.getMinY() + 2, level.getMaxY() - 3);
    }

    public static boolean placeUnstableCore(ServerLevel level, BlockPos center, RandomSource random,
            int minCollapseTicks, int maxCollapseTicks) {
        int minY = level.getMinY() + 2;
        int maxY = level.getMaxY() - 3;
        BlockPos pos = new BlockPos(center.getX(), Mth.clamp(center.getY(), minY, maxY), center.getZ());
        for (int i = 0; i < 4 && !level.getBlockState(pos).canBeReplaced(); i++) {
            pos = pos.above();
        }
        if (pos.getY() > maxY || !level.getBlockState(pos).canBeReplaced()) {
            return false;
        }
        float roll = random.nextFloat();
        boolean isLightning = roll < 0.35F;
        boolean isAtomic = !isLightning && roll < 0.60F;
        BlockState state = FxBlocks.ANGRY_BLOCK.get().defaultBlockState()
                .setValue(MultiPartBlock.IS_CORE, true)
                .setValue(MultiPartBlock.FACING, Direction.from2DDataValue(random.nextInt(4)))
                .setValue(AngryBlock.IS_LIGHTNING, isLightning)
                .setValue(AngryBlock.IS_ATOMIC, isAtomic)
                .setValue(AngryBlock.IS_UNSTABLE, true);
        level.setBlock(pos, state, Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof AngryBlockEntity angryEntity) {
            int span = Math.max(1, maxCollapseTicks - minCollapseTicks + 1);
            angryEntity.setCollapseLimit(minCollapseTicks + random.nextInt(span));
        }
        return true;
    }
}
