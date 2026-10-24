package com.pppopipupu.fxeasy.client.particle;

import module java.base;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class ThunderParticle extends Particle {

    private static final int BOLTS = 6;
    private static final int SEGMENTS = 5;
    private static final double STEP = 0.16;

    private final List<Bolt> bolts;
    private final float scale;

    public ThunderParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed,
            double zSpeed) {
        super(level, x, y, z, 0.0, 0.0, 0.0);
        this.lifetime = 10 + this.random.nextInt(7);
        this.gravity = 0.0F;
        this.friction = 1.0F;
        this.hasPhysics = false;
        this.scale = 0.7F + this.random.nextFloat() * 0.8F;
        this.bolts = buildBolts();
    }

    private List<Bolt> buildBolts() {
        List<Bolt> result = new ArrayList<>();
        for (int bolt = 0; bolt < BOLTS; bolt++) {
            double yaw = this.random.nextDouble() * Math.PI * 2.0;
            double pitch = (this.random.nextDouble() - 0.5) * Math.PI;
            double directionX = Math.cos(pitch) * Math.cos(yaw);
            double directionY = Math.sin(pitch);
            double directionZ = Math.cos(pitch) * Math.sin(yaw);
            double x = (this.random.nextDouble() - 0.5) * 0.08;
            double y = (this.random.nextDouble() - 0.5) * 0.08;
            double z = (this.random.nextDouble() - 0.5) * 0.08;
            for (int segment = 0; segment < SEGMENTS; segment++) {
                double length = STEP * (0.65 + this.random.nextDouble() * 0.7);
                double nextX = x + (directionX + (this.random.nextDouble() - 0.5) * 1.1) * length;
                double nextY = y + (directionY + (this.random.nextDouble() - 0.5) * 1.1) * length;
                double nextZ = z + (directionZ + (this.random.nextDouble() - 0.5) * 1.1) * length;
                int color = segment == 0 ? 0xFFFFFFFF : segment < 3 ? 0xFF8AEEFF : 0xFFB15CFF;
                result.add(new Bolt(x, y, z, nextX, nextY, nextZ, color));
                if (segment == 1 && this.random.nextFloat() < 0.8F) {
                    double branchX = nextX + (this.random.nextDouble() - 0.5) * 0.35;
                    double branchY = nextY + (this.random.nextDouble() - 0.5) * 0.35;
                    double branchZ = nextZ + (this.random.nextDouble() - 0.5) * 0.35;
                    result.add(new Bolt(nextX, nextY, nextZ, branchX, branchY, branchZ, 0xFFB15CFF));
                }
                x = nextX;
                y = nextY;
                z = nextZ;
            }
        }
        return List.copyOf(result);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
        }
    }

    public ThunderSnapshot snapshot(Vec3 cameraPos, float partialTick) {
        float progress = (this.age + partialTick) / this.lifetime;
        float alpha = Math.clamp(1.0F - progress, 0.0F, 1.0F);
        float width = (0.018F + 0.03F * alpha) * this.scale;
        float centerX = (float) (this.x - cameraPos.x());
        float centerY = (float) (this.y - cameraPos.y());
        float centerZ = (float) (this.z - cameraPos.z());
        List<ThunderSegment> segments = new ArrayList<>(this.bolts.size());
        for (Bolt bolt : this.bolts) {
            segments.add(new ThunderSegment(
                    (float) (this.x + bolt.x1() - cameraPos.x()),
                    (float) (this.y + bolt.y1() - cameraPos.y()),
                    (float) (this.z + bolt.z1() - cameraPos.z()),
                    (float) (this.x + bolt.x2() - cameraPos.x()),
                    (float) (this.y + bolt.y2() - cameraPos.y()),
                    (float) (this.z + bolt.z2() - cameraPos.z()),
                    bolt.color()));
        }
        return new ThunderSnapshot(segments, centerX, centerY, centerZ, alpha, width, this.scale);
    }

    @Override
    public ParticleRenderType getGroup() {
        return AngryParticleGroup.TYPE;
    }

    public record ThunderSegment(float x1, float y1, float z1, float x2, float y2, float z2, int color) {
    }

    public record ThunderSnapshot(List<ThunderSegment> segments, float centerX, float centerY, float centerZ,
            float alpha, float width, float scale) {
    }

    private record Bolt(double x1, double y1, double z1, double x2, double y2, double z2, int color) {
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {

        @Override
        public @Nullable Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y,
                double z, double xSpeed, double ySpeed, double zSpeed, RandomSource random) {
            return new ThunderParticle(level, x, y, z, xSpeed, ySpeed, zSpeed);
        }
    }
}
