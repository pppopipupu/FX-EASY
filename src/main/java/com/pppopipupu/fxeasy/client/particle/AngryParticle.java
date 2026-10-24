package com.pppopipupu.fxeasy.client.particle;

import module java.base;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class AngryParticle extends Particle {

    private static final int ORBIT_LIFETIME = 250;
    private static final int SEEK_LIFETIME = 26;
    private static final int SURGE_LIFETIME = 24;
    private static final float ROTATION_SPEED = 1.0F;

    private final Mode mode;
    private double centerX;
    private double centerY;
    private double centerZ;
    private float orbitRadius;
    private float orbitSpeed;
    private float currentAngle;
    private float sinTilt;
    private float cosTilt;
    @Nullable
    private LivingEntity target;
    private double startX;
    private double startY;
    private double startZ;
    private double endX;
    private double endY;
    private double endZ;
    private float swirlAngle;

    public AngryParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed,
            double zSpeed) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.mode = Mode.ORBIT;
        this.lifetime = ORBIT_LIFETIME;
        this.gravity = 0.0F;
        this.friction = 1.0F;
        this.hasPhysics = false;
        this.centerX = x;
        this.centerY = y;
        this.centerZ = z;
        this.orbitRadius = 4.0F;
        this.orbitSpeed = 0.2F;
        this.currentAngle = this.random.nextFloat() * (float) (2 * Math.PI);
        int orbitType = this.random.nextInt(4);
        float tiltAngle = switch (orbitType) {
            case 0 -> 90.0F;
            case 1 -> 45.0F;
            case 2 -> 135.0F;
            default -> 0.0F;
        };
        this.sinTilt = Mth.sin((float) Math.toRadians(tiltAngle));
        this.cosTilt = Mth.cos((float) Math.toRadians(tiltAngle));
        double localZComponent = this.orbitRadius * Mth.sin(this.currentAngle);
        double localXComponent = this.orbitRadius * Mth.cos(this.currentAngle);
        this.x = this.centerX + localXComponent;
        this.y = this.centerY - this.sinTilt * localZComponent;
        this.z = this.centerZ + this.cosTilt * localZComponent;
    }

    private AngryParticle(ClientLevel level, double x, double y, double z, LivingEntity target) {
        super(level, x, y, z, 0.0, 0.0, 0.0);
        this.mode = Mode.SEEK;
        this.lifetime = SEEK_LIFETIME;
        this.gravity = 0.0F;
        this.friction = 1.0F;
        this.hasPhysics = false;
        this.target = target;
        this.startX = x;
        this.startY = y;
        this.startZ = z;
        this.swirlAngle = this.random.nextFloat() * (float) (2 * Math.PI);
    }

    private AngryParticle(ClientLevel level, double x, double y, double z, Vec3 end) {
        super(level, x, y, z, 0.0, 0.0, 0.0);
        this.mode = Mode.SURGE;
        this.lifetime = SURGE_LIFETIME;
        this.gravity = 0.0F;
        this.friction = 1.0F;
        this.hasPhysics = false;
        this.startX = x;
        this.startY = y;
        this.startZ = z;
        this.endX = end.x;
        this.endY = end.y;
        this.endZ = end.z;
        this.swirlAngle = this.random.nextFloat() * (float) (2 * Math.PI);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        if (this.mode == Mode.ORBIT) {
            this.orbit();
            return;
        }
        if (this.mode == Mode.SEEK) {
            this.seek();
            return;
        }
        this.surge();
    }

    private void orbit() {
        this.currentAngle += this.orbitSpeed;
        double localZComponent = this.orbitRadius * Mth.sin(this.currentAngle);
        double localXComponent = this.orbitRadius * Mth.cos(this.currentAngle);
        this.x = this.centerX + localXComponent;
        this.y = this.centerY - this.sinTilt * localZComponent;
        this.z = this.centerZ + this.cosTilt * localZComponent;
    }

    private void seek() {
        if (this.target == null || this.target.isRemoved()) {
            this.remove();
            return;
        }
        float progress = (float) this.age / this.lifetime;
        double ease = progress * progress;
        double radius = (1.0 - progress) * 0.8;
        double angle = this.swirlAngle + progress * 9.0;
        double targetY = this.target.getY() + 1.0;
        this.x = this.startX + (this.target.getX() - this.startX) * ease + Math.cos(angle) * radius;
        this.y = this.startY + (targetY - this.startY) * ease + Math.sin(angle) * radius * 0.45;
        this.z = this.startZ + (this.target.getZ() - this.startZ) * ease + Math.sin(angle) * radius;
    }

    private void surge() {
        float progress = (float) this.age / this.lifetime;
        double ease = 1.0 - (1.0 - progress) * (1.0 - progress);
        double radius = progress * 0.55;
        double angle = this.swirlAngle + progress * 7.0;
        this.x = this.startX + (this.endX - this.startX) * ease + Math.cos(angle) * radius;
        this.y = this.startY + (this.endY - this.startY) * ease + Math.sin(angle) * radius * 0.4;
        this.z = this.startZ + (this.endZ - this.startZ) * ease + Math.sin(angle) * radius;
    }

    public AngrySnapshot snapshot(Vec3 cameraPos, float partialTick) {
        float x = (float) (Mth.lerp(partialTick, this.xo, this.x) - cameraPos.x());
        float y = (float) (Mth.lerp(partialTick, this.yo, this.y) - cameraPos.y());
        float z = (float) (Mth.lerp(partialTick, this.zo, this.z) - cameraPos.z());
        float rotation = (this.age + partialTick) * ROTATION_SPEED * 20.0F;
        float progress = Math.clamp((this.age + partialTick) / this.lifetime, 0.0F, 1.0F);
        float scale = switch (this.mode) {
            case ORBIT -> 0.3F;
            case SEEK -> 0.24F + 0.28F * (1.0F - progress);
            case SURGE -> 0.24F + 0.22F * (1.0F - progress);
        };
        int light = LevelRenderer.getLightCoords(this.level,
                BlockPos.containing(this.x, this.y, this.z));
        return new AngrySnapshot(x, y, z, rotation, scale, light);
    }

    @Override
    public ParticleRenderType getGroup() {
        return AngryParticleGroup.TYPE;
    }

    public record AngrySnapshot(float x, float y, float z, float rotation, float scale, int light) {
    }

    private enum Mode {
        ORBIT,
        SEEK,
        SURGE
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {

        @Override
        public @Nullable Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y,
                double z, double xSpeed, double ySpeed, double zSpeed, RandomSource random) {
            return new AngryParticle(level, x, y, z, xSpeed, ySpeed, zSpeed);
        }
    }

    public static final class WandProvider implements ParticleProvider<SimpleParticleType> {

        @Override
        public @Nullable Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y,
                double z, double xSpeed, double ySpeed, double zSpeed, RandomSource random) {
            LivingEntity target = level.getEntity((int) xSpeed) instanceof LivingEntity living
                    ? living : Minecraft.getInstance().player;
            return target == null ? null : new AngryParticle(level, x, y, z, target);
        }
    }

    public static final class SurgeProvider implements ParticleProvider<SimpleParticleType> {

        @Override
        public @Nullable Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y,
                double z, double xSpeed, double ySpeed, double zSpeed, RandomSource random) {
            return new AngryParticle(level, x, y, z, new Vec3(xSpeed, ySpeed, zSpeed));
        }
    }
}
