package com.pppopipupu.fxeasy.content.block.entity;

import module java.base;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

public final class AngryLightingBolt {

    public final List<Vector3f> segments = new ArrayList<>();
    public final List<AngryLightingBolt> children = new ArrayList<>();
    public final float red;
    public final float green;
    public final float blue;
    public final float thickness;
    public int life;

    public AngryLightingBolt(Vector3f start, Vector3f end, int segmentCount, float jitter, float thickness, int life,
            float red, float green, float blue, int maxRecursion, RandomSource random) {
        this(start, end, segmentCount, jitter, thickness, life, red, green, blue, 0, maxRecursion, random);
    }

    private AngryLightingBolt(Vector3f start, Vector3f end, int segmentCount, float jitter, float thickness, int life,
            float red, float green, float blue, int recursionDepth, int maxRecursion, RandomSource random) {
        this.thickness = thickness;
        this.life = life;
        this.red = red;
        this.green = green;
        this.blue = blue;

        generateSegments(start, end, segmentCount, jitter, random);

        if (recursionDepth < maxRecursion) {
            int forks = random.nextInt(3);
            for (int i = 0; i < forks; i++) {
                if (this.segments.size() > 2) {
                    Vector3f forkStart = this.segments.get(random.nextInt(this.segments.size() - 2) + 1);

                    Vector3f direction = end.sub(start, new Vector3f()).normalize();
                    direction.add(
                            (random.nextFloat() - 0.5F) * 1.5F,
                            (random.nextFloat() - 0.5F) * 1.5F,
                            (random.nextFloat() - 0.5F) * 1.5F
                    ).normalize();

                    float forkLength = end.distance(start) * (0.5F + random.nextFloat() * 0.3F);
                    Vector3f forkEnd = new Vector3f(direction).mul(forkLength).add(forkStart);

                    AngryLightingBolt child = new AngryLightingBolt(
                            forkStart,
                            forkEnd,
                            segmentCount / 2 + 1,
                            jitter,
                            thickness * 0.6F,
                            life / 2,
                            red, green, blue,
                            recursionDepth + 1,
                            maxRecursion,
                            random
                    );
                    this.children.add(child);
                }
            }
        }
    }

    private void generateSegments(Vector3f start, Vector3f end, int segmentCount, float jitter,
            RandomSource random) {
        this.segments.add(start);
        Vector3f diff = end.sub(start, new Vector3f());
        float totalLength = diff.length();
        if (totalLength <= 0) {
            this.segments.add(end);
            return;
        }

        for (int i = 1; i < segmentCount; i++) {
            float progress = (float) i / segmentCount;
            Vector3f pointOnLine = new Vector3f(diff).mul(progress).add(start);
            float currentJitter = jitter * (totalLength / segmentCount);
            pointOnLine.add(
                    (random.nextFloat() - 0.5F) * 2.0F * currentJitter,
                    (random.nextFloat() - 0.5F) * 2.0F * currentJitter,
                    (random.nextFloat() - 0.5F) * 2.0F * currentJitter
            );
            this.segments.add(pointOnLine);
        }
        this.segments.add(end);
    }

    public void update() {
        if (this.life > 0) {
            this.life--;
        }
        for (AngryLightingBolt child : children) {
            child.update();
        }
    }
}
