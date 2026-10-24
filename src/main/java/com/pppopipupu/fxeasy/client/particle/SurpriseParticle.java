package com.pppopipupu.fxeasy.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class SurpriseParticle extends Particle {

    public SurpriseParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed,
            double zSpeed) {
        super(level, x, y, z, 0.0, 0.0, 0.0);

        this.lifetime = 200;
        this.gravity = 0.0F;
        this.hasPhysics = false;

        float angleDegrees = switch ((int) xSpeed) {
            case 1 -> 30.0F;
            case 2 -> -30.0F;
            default -> 0.0F;
        };
        float angleRadians = (float) Math.toRadians(angleDegrees);
        float radius = 30.0F;
        float upwardMovement = 40.0F;

        double targetX = x + radius * Mth.sin(angleRadians);
        double targetY = y + upwardMovement;
        double targetZ = z + radius * Mth.cos(angleRadians);

        this.xd = (targetX - x) / this.lifetime;
        this.yd = (targetY - y) / this.lifetime;
        this.zd = (targetZ - z) / this.lifetime;
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
        this.move(this.xd, this.yd, this.zd);
    }

    public SurpriseSnapshot snapshot(Vec3 cameraPos, float partialTick) {
        float x = (float) (Mth.lerp(partialTick, this.xo, this.x) - cameraPos.x());
        float y = (float) (Mth.lerp(partialTick, this.yo, this.y) - cameraPos.y());
        float z = (float) (Mth.lerp(partialTick, this.zo, this.z) - cameraPos.z());
        float progress = (this.age + partialTick) / (float) this.lifetime;
        float scale = Mth.lerp(progress, 1.0F, 10.0F);
        int light = LevelRenderer.getLightCoords(this.level,
                BlockPos.containing(this.x, this.y, this.z));
        return new SurpriseSnapshot(x, y, z, scale, light);
    }

    @Override
    public ParticleRenderType getGroup() {
        return AngryParticleGroup.TYPE;
    }

    public record SurpriseSnapshot(float x, float y, float z, float scale, int light) {
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {

        @Override
        public @Nullable Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y,
                double z, double xSpeed, double ySpeed, double zSpeed, RandomSource random) {
            return new SurpriseParticle(level, x, y, z, xSpeed, ySpeed, zSpeed);
        }
    }
}
