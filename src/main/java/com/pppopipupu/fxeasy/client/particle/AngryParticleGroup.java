package com.pppopipupu.fxeasy.client.particle;

import module java.base;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.pppopipupu.fxeasy.FxEasy;
import com.pppopipupu.fxeasy.client.particle.AngryParticle.AngrySnapshot;
import com.pppopipupu.fxeasy.client.particle.SurpriseParticle.SurpriseSnapshot;
import com.pppopipupu.fxeasy.client.particle.ThunderParticle.ThunderSegment;
import com.pppopipupu.fxeasy.client.particle.ThunderParticle.ThunderSnapshot;
import com.pppopipupu.fxeasy.client.render.AngryThunderRenderer;
import com.pppopipupu.fxeasy.content.FxBlocks;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ParticleGroupRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class AngryParticleGroup extends ParticleGroup<Particle> {

    public static final ParticleRenderType TYPE = new ParticleRenderType("fxeasy_angry");
    private static final RenderType BILLBOARD_RENDER_TYPE = RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS);
    private static final float MODEL_EXTENT = 2.0F;
    private static final float NEAR_DISTANCE_SQ = 24.0F * 24.0F;
    private static final float FAR_DISTANCE_SQ = 60.0F * 60.0F;

    private static TextureAtlas atlas;
    private static TextureAtlasSprite angrySprite;
    private static TextureAtlasSprite surpriseSprite;
    private static BlockStateModel angryModel;
    private static BlockStateModel surpriseModel;
    private static List<BakedQuad> angryQuads = List.of();
    private static List<BakedQuad> surpriseQuads = List.of();

    public AngryParticleGroup(ParticleEngine engine) {
        super(engine);
    }

    @Override
    public ParticleGroupRenderState extractRenderState(Frustum frustum, Camera camera, float partialTick) {
        Vec3 cameraPos = camera.position();
        List<AngrySnapshot> angry = new ArrayList<>();
        List<SurpriseSnapshot> surprise = new ArrayList<>();
        List<ThunderSnapshot> thunder = new ArrayList<>();
        for (Particle particle : this.particles) {
            Vec3 position = particle.getPos();
            if (!frustum.pointInFrustum(position.x, position.y, position.z)) {
                continue;
            }
            if (particle instanceof AngryParticle angryParticle) {
                angry.add(angryParticle.snapshot(cameraPos, partialTick));
            } else if (particle instanceof SurpriseParticle surpriseParticle) {
                surprise.add(surpriseParticle.snapshot(cameraPos, partialTick));
            } else if (particle instanceof ThunderParticle thunderParticle) {
                thunder.add(thunderParticle.snapshot(cameraPos, partialTick));
            }
        }
        return new RenderState(angry, surprise, thunder);
    }

    public record RenderState(List<AngrySnapshot> angry, List<SurpriseSnapshot> surprise,
            List<ThunderSnapshot> thunder) implements ParticleGroupRenderState {

        @Override
        public void submit(SubmitNodeCollector collector, CameraRenderState camera) {
            ensureModels();
            List<AngrySnapshot> nearAngry = new ArrayList<>();
            List<AngrySnapshot> midAngry = new ArrayList<>();
            List<AngrySnapshot> farAngry = new ArrayList<>();
            List<SurpriseSnapshot> nearSurprise = new ArrayList<>();
            List<SurpriseSnapshot> midSurprise = new ArrayList<>();
            List<SurpriseSnapshot> farSurprise = new ArrayList<>();
            for (AngrySnapshot snapshot : this.angry) {
                float distance = distanceSq(snapshot.x(), snapshot.y(), snapshot.z());
                if (distance <= NEAR_DISTANCE_SQ) {
                    nearAngry.add(snapshot);
                } else if (distance <= FAR_DISTANCE_SQ) {
                    midAngry.add(snapshot);
                } else {
                    farAngry.add(snapshot);
                }
            }
            for (SurpriseSnapshot snapshot : this.surprise) {
                float distance = distanceSq(snapshot.x(), snapshot.y(), snapshot.z());
                if (distance <= NEAR_DISTANCE_SQ) {
                    nearSurprise.add(snapshot);
                } else if (distance <= FAR_DISTANCE_SQ) {
                    midSurprise.add(snapshot);
                } else {
                    farSurprise.add(snapshot);
                }
            }
            if (!farAngry.isEmpty() || !farSurprise.isEmpty()) {
                ensureSprites();
                Vector3f right = new Vector3f(1.0F, 0.0F, 0.0F).rotate(camera.orientation);
                Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F).rotate(camera.orientation);
                Vector3f normal = new Vector3f(0.0F, 0.0F, 1.0F).rotate(camera.orientation);
                collector.submitCustomGeometry(new PoseStack(), BILLBOARD_RENDER_TYPE, (pose, buffer) -> {
                    for (AngrySnapshot snapshot : farAngry) {
                        submitSprite(pose, buffer, snapshot.x(), snapshot.y(), snapshot.z(), snapshot.scale(),
                                snapshot.rotation(), snapshot.light(), angrySprite, right, up, normal);
                    }
                    for (SurpriseSnapshot snapshot : farSurprise) {
                        submitSprite(pose, buffer, snapshot.x(), snapshot.y(), snapshot.z(), snapshot.scale(),
                                0.0F, snapshot.light(), surpriseSprite, right, up, normal);
                    }
                });
            }
            if (!nearAngry.isEmpty() || !nearSurprise.isEmpty()) {
                collector.submitCustomGeometry(new PoseStack(), Sheets.cutoutBlockSheet(), (pose, buffer) -> {
                    for (AngrySnapshot snapshot : nearAngry) {
                        submitAngry(buffer, snapshot, false);
                    }
                    for (SurpriseSnapshot snapshot : nearSurprise) {
                        submitSurprise(buffer, camera, snapshot, false);
                    }
                });
            }
            if (!midAngry.isEmpty() || !midSurprise.isEmpty()) {
                collector.submitCustomGeometry(new PoseStack(), Sheets.cutoutBlockSheet(), (pose, buffer) -> {
                    for (AngrySnapshot snapshot : midAngry) {
                        submitAngry(buffer, snapshot, true);
                    }
                    for (SurpriseSnapshot snapshot : midSurprise) {
                        submitSurprise(buffer, camera, snapshot, true);
                    }
                });
            }
            if (!this.thunder.isEmpty()) {
                collector.submitCustomGeometry(new PoseStack(), AngryThunderRenderer.RENDER_TYPE,
                        (pose, buffer) -> {
                            for (ThunderSnapshot snapshot : this.thunder) {
                                submitThunder(pose, buffer, camera, snapshot);
                            }
                        });
            }
        }

        private static float distanceSq(float x, float y, float z) {
            return x * x + y * y + z * z;
        }

        private static void submitAngry(VertexConsumer buffer, AngrySnapshot snapshot, boolean cull) {
            PoseStack pose = new PoseStack();
            pose.translate(snapshot.x(), snapshot.y(), snapshot.z());
            pose.mulPose(Axis.YP.rotationDegrees(snapshot.rotation()));
            pose.scale(snapshot.scale(), snapshot.scale(), snapshot.scale());
            pose.translate(-0.5F, -0.5F, -0.5F);
            QuadInstance instance = new QuadInstance();
            instance.setLightCoords(snapshot.light());
            Vector3f center = cull ? new Vector3f() : null;
            Vector3f normal = cull ? new Vector3f() : null;
            for (BakedQuad quad : angryQuads) {
                if (cull && !isFrontFacing(pose.last(), quad, center, normal)) {
                    continue;
                }
                buffer.putBakedQuad(pose.last(), quad, instance);
            }
        }

        private static void submitSurprise(VertexConsumer buffer, CameraRenderState camera,
                SurpriseSnapshot snapshot, boolean cull) {
            PoseStack pose = new PoseStack();
            pose.translate(snapshot.x(), snapshot.y(), snapshot.z());
            pose.mulPose(camera.orientation);
            pose.mulPose(Axis.YP.rotationDegrees(-90.0F));
            pose.scale(snapshot.scale(), snapshot.scale(), snapshot.scale());
            pose.translate(-0.5F, -0.5F, -0.5F);
            QuadInstance instance = new QuadInstance();
            instance.setLightCoords(snapshot.light());
            Vector3f center = cull ? new Vector3f() : null;
            Vector3f normal = cull ? new Vector3f() : null;
            for (BakedQuad quad : surpriseQuads) {
                if (cull && !isFrontFacing(pose.last(), quad, center, normal)) {
                    continue;
                }
                buffer.putBakedQuad(pose.last(), quad, instance);
            }
        }

        private static boolean isFrontFacing(PoseStack.Pose pose, BakedQuad quad, Vector3f center,
                Vector3f normal) {
            center.set(
                    (quad.position0().x() + quad.position1().x() + quad.position2().x()
                            + quad.position3().x()) * 0.25F,
                    (quad.position0().y() + quad.position1().y() + quad.position2().y()
                            + quad.position3().y()) * 0.25F,
                    (quad.position0().z() + quad.position1().z() + quad.position2().z()
                            + quad.position3().z()) * 0.25F);
            pose.pose().transformPosition(center);
            pose.transformNormal(quad.direction().getUnitVec3f(), normal);
            return normal.dot(center) < 0.0F;
        }

        private static void submitSprite(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z,
                float scale, float rotation, int light, TextureAtlasSprite sprite, Vector3f right, Vector3f up,
                Vector3f normal) {
            float half = scale * MODEL_EXTENT * 0.5F;
            float radians = rotation * Mth.DEG_TO_RAD;
            float cosine = Mth.cos(radians);
            float sine = Mth.sin(radians);
            float rx = right.x() * cosine + up.x() * sine;
            float ry = right.y() * cosine + up.y() * sine;
            float rz = right.z() * cosine + up.z() * sine;
            float ux = up.x() * cosine - right.x() * sine;
            float uy = up.y() * cosine - right.y() * sine;
            float uz = up.z() * cosine - right.z() * sine;
            float x0 = x - rx * half - ux * half;
            float y0 = y - ry * half - uy * half;
            float z0 = z - rz * half - uz * half;
            float x1 = x + rx * half - ux * half;
            float y1 = y + ry * half - uy * half;
            float z1 = z + rz * half - uz * half;
            float x2 = x + rx * half + ux * half;
            float y2 = y + ry * half + uy * half;
            float z2 = z + rz * half + uz * half;
            float x3 = x - rx * half + ux * half;
            float y3 = y - ry * half + uy * half;
            float z3 = z - rz * half + uz * half;
            vertex(pose, buffer, x0, y0, z0, sprite.getU0(), sprite.getV1(), light, normal);
            vertex(pose, buffer, x1, y1, z1, sprite.getU1(), sprite.getV1(), light, normal);
            vertex(pose, buffer, x2, y2, z2, sprite.getU1(), sprite.getV0(), light, normal);
            vertex(pose, buffer, x3, y3, z3, sprite.getU0(), sprite.getV0(), light, normal);
        }

        private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u,
                float v, int light, Vector3f normal) {
            buffer.addVertex(pose, x, y, z)
                    .setColor(1.0F, 1.0F, 1.0F, 1.0F)
                    .setUv(u, v)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(normal.x(), normal.y(), normal.z());
        }

        private static void submitThunder(PoseStack.Pose pose, VertexConsumer buffer, CameraRenderState camera,
                ThunderSnapshot snapshot) {
            for (ThunderSegment segment : snapshot.segments()) {
                Vec3 start = new Vec3(segment.x1(), segment.y1(), segment.z1());
                Vec3 end = new Vec3(segment.x2(), segment.y2(), segment.z2());
                Vec3 direction = end.subtract(start);
                if (direction.lengthSqr() < 1e-6) {
                    continue;
                }
                if (start.lengthSqr() < 1e-6) {
                    continue;
                }
                Vec3 toCamera = start.scale(-1.0).normalize();
                Vec3 right = direction.normalize().cross(toCamera).normalize().scale(snapshot.width());
                if (right.lengthSqr() < 1e-6) {
                    continue;
                }
                Vec3 up = direction.normalize().cross(right).normalize().scale(snapshot.width() * 0.65);
                addQuad(buffer, pose, start.add(right), start.subtract(right), end.subtract(right),
                        end.add(right), segment.color(), snapshot.alpha());
                addQuad(buffer, pose, start.add(up), start.subtract(up), end.subtract(up),
                        end.add(up), segment.color(), snapshot.alpha() * 0.75F);
            }
            PoseStack flash = new PoseStack();
            flash.translate(snapshot.centerX(), snapshot.centerY(), snapshot.centerZ());
            flash.mulPose(camera.orientation);
            flash.mulPose(Axis.YP.rotationDegrees(-90.0F));
            float size = snapshot.scale() * (0.20F + 0.32F * snapshot.alpha());
            addQuad(buffer, flash.last(), new Vec3(-size, -size, 0.0), new Vec3(size, -size, 0.0),
                    new Vec3(size, size, 0.0), new Vec3(-size, size, 0.0), 0xFFFFFFFF,
                    snapshot.alpha() * 0.8F);
            float ring = snapshot.scale() * (0.28F + 0.55F * (1.0F - snapshot.alpha()));
            addQuad(buffer, flash.last(), new Vec3(-ring, -ring * 0.08F, 0.0),
                    new Vec3(ring, -ring * 0.08F, 0.0), new Vec3(ring, ring * 0.08F, 0.0),
                    new Vec3(-ring, ring * 0.08F, 0.0), 0xFF62D8FF, snapshot.alpha() * 0.35F);
        }

        private static void addQuad(VertexConsumer buffer, PoseStack.Pose pose, Vec3 first, Vec3 second,
                Vec3 third, Vec3 fourth, int color, float alpha) {
            float red = ((color >> 16) & 0xFF) / 255.0F;
            float green = ((color >> 8) & 0xFF) / 255.0F;
            float blue = (color & 0xFF) / 255.0F;
            float combinedAlpha = ((color >> 24) & 0xFF) / 255.0F * alpha;
            buffer.addVertex(pose, (float) first.x(), (float) first.y(), (float) first.z())
                    .setColor(red, green, blue, combinedAlpha);
            buffer.addVertex(pose, (float) second.x(), (float) second.y(), (float) second.z())
                    .setColor(red, green, blue, combinedAlpha);
            buffer.addVertex(pose, (float) third.x(), (float) third.y(), (float) third.z())
                    .setColor(red, green, blue, combinedAlpha);
            buffer.addVertex(pose, (float) fourth.x(), (float) fourth.y(), (float) fourth.z())
                    .setColor(red, green, blue, combinedAlpha);
        }

        private static void ensureSprites() {
            TextureAtlas current = (TextureAtlas) Minecraft.getInstance().getTextureManager()
                    .getTexture(TextureAtlas.LOCATION_BLOCKS);
            if (current == atlas && angrySprite != null) {
                return;
            }
            atlas = current;
            angrySprite = current.getSprite(Identifier.fromNamespaceAndPath(FxEasy.MODID, "block/face_angry"));
            surpriseSprite = current.getSprite(Identifier.fromNamespaceAndPath(FxEasy.MODID, "block/face_hagry"));
        }

        private static void ensureModels() {
            BlockState angryState = FxBlocks.ANGRY_BLOCK.get().defaultBlockState();
            BlockState surpriseState = FxBlocks.SURPRISE_BLOCK.get().defaultBlockState();
            BlockStateModel angry = Minecraft.getInstance().getModelManager().getBlockStateModelSet()
                    .get(angryState);
            BlockStateModel surprise = Minecraft.getInstance().getModelManager().getBlockStateModelSet()
                    .get(surpriseState);
            if (angry == angryModel && surprise == surpriseModel && !angryQuads.isEmpty()
                    && !surpriseQuads.isEmpty()) {
                return;
            }
            angryModel = angry;
            surpriseModel = surprise;
            angryQuads = collectQuads(angry, angryState);
            surpriseQuads = collectQuads(surprise, surpriseState);
        }

        private static List<BakedQuad> collectQuads(BlockStateModel model, BlockState state) {
            List<BlockStateModelPart> parts = new ArrayList<>();
            model.collectParts(BlockAndTintGetter.EMPTY, BlockPos.ZERO, state, RandomSource.create(42L), parts);
            List<BakedQuad> quads = new ArrayList<>();
            for (BlockStateModelPart part : parts) {
                quads.addAll(part.getQuads(null));
                for (Direction direction : Direction.values()) {
                    quads.addAll(part.getQuads(direction));
                }
            }
            return List.copyOf(quads);
        }
    }
}
