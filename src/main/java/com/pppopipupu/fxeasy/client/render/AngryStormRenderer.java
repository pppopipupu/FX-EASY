package com.pppopipupu.fxeasy.client.render;

import module java.base;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.pppopipupu.fxeasy.FxEasy;
import com.pppopipupu.fxeasy.FxTickScheduler;
import com.pppopipupu.fxeasy.client.AngryPostEffects;
import com.pppopipupu.fxeasy.content.FxBlocks;
import com.pppopipupu.fxeasy.content.entity.AngryStorm;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

public class AngryStormRenderer extends EntityRenderer<AngryStorm, AngryStormRenderer.State> {

    public static final RenderPipeline CLOUD_PIPELINE = RenderPipeline
            .builder(RenderPipelines.MATRICES_FOG_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(FxEasy.MODID, "pipeline/angry_storm_cloud"))
            .withVertexShader(Identifier.fromNamespaceAndPath(FxEasy.MODID, "core/angry_storm_cloud"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(FxEasy.MODID, "core/angry_storm_cloud"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withCull(false)
            .build();

    public static final RenderPipeline VORTEX_PIPELINE = RenderPipeline
            .builder(RenderPipelines.MATRICES_FOG_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(FxEasy.MODID, "pipeline/angry_storm_vortex"))
            .withVertexShader(Identifier.fromNamespaceAndPath(FxEasy.MODID, "core/angry_storm_vortex"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(FxEasy.MODID, "core/angry_storm_vortex"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withCull(false)
            .build();

    public static final RenderPipeline VORTEX_GLOW_PIPELINE = RenderPipeline
            .builder(RenderPipelines.MATRICES_FOG_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(FxEasy.MODID, "pipeline/angry_storm_vortex_glow"))
            .withVertexShader(Identifier.fromNamespaceAndPath(FxEasy.MODID, "core/angry_storm_vortex"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(FxEasy.MODID, "core/angry_storm_vortex"))
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withCull(false)
            .build();

    public static final RenderPipeline VOID_PIPELINE = RenderPipeline
            .builder(RenderPipelines.MATRICES_FOG_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(FxEasy.MODID, "pipeline/angry_storm_void"))
            .withVertexShader(Identifier.fromNamespaceAndPath(FxEasy.MODID, "core/angry_storm_void"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(FxEasy.MODID, "core/angry_storm_void"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withCull(false)
            .build();

    public static final RenderType CLOUD_RENDER_TYPE = RenderType.create("angry_storm_cloud",
            RenderSetup.builder(CLOUD_PIPELINE).createRenderSetup());
    public static final RenderType VORTEX_RENDER_TYPE = RenderType.create("angry_storm_vortex",
            RenderSetup.builder(VORTEX_PIPELINE).createRenderSetup());
    public static final RenderType VORTEX_GLOW_RENDER_TYPE = RenderType.create("angry_storm_vortex_glow",
            RenderSetup.builder(VORTEX_GLOW_PIPELINE).createRenderSetup());
    public static final RenderType VOID_RENDER_TYPE = RenderType.create("angry_storm_void",
            RenderSetup.builder(VOID_PIPELINE).createRenderSetup());

    private static final int CANOPY_SIDES = 16;
    private static final int HAILSTONE_COUNT = 12;
    private static final int RAIN_RIBBON_COUNT = 24;
    private static final int PILLAR_COUNT = 6;
    private static final int FIREBALL_STREAK_COUNT = 18;
    private static final int EMBER_RING_COUNT = 6;
    private static final int VOID_LATITUDE_BANDS = 10;
    private static final int VOID_LONGITUDE_BANDS = 24;
    private static final int VOID_ARM_COUNT = 12;
    private static final int VOID_ARM_STEPS = 14;
    private static long lastPostEffectTouchTick = -100L;

    private final BlockModelResolver modelResolver;

    public AngryStormRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.modelResolver = context.getBlockModelResolver();
        this.shadowRadius = 0.0F;
    }

    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(CLOUD_PIPELINE);
        event.registerPipeline(VORTEX_PIPELINE);
        event.registerPipeline(VORTEX_GLOW_PIPELINE);
        event.registerPipeline(VOID_PIPELINE);
    }

    @Override
    public boolean shouldRender(AngryStorm entity, Frustum frustum, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(AngryStorm entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.stormAge = entity.getStormAge() + partialTicks;
        state.phase = entity.phase();
        state.radius = entity.radius();
        state.voidRadius = entity.voidRadius();
        state.amplifier = entity.getAmplifier();
        this.modelResolver.update(state.coreModel, FxBlocks.ANGRY_BLOCK.get().defaultBlockState(),
                BlockDisplayContext.create());
        updateStormPostEffect(entity, state);
    }

    private static void updateStormPostEffect(AngryStorm entity, State state) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.level() != entity.level()) {
            return;
        }
        double reach = state.radius + 10.0F;
        boolean inside = player.distanceToSqr(entity) <= reach * reach
                && state.stormAge < AngryStorm.TOTAL_DURATION - 5;
        long gameTime = entity.level().getGameTime();
        int stormMode = state.phase == 6 ? 3 : 2;
        if (inside) {
            lastPostEffectTouchTick = gameTime;
            if (AngryPostEffects.mode() < 0 || isStormMode(AngryPostEffects.mode())) {
                AngryPostEffects.set(stormMode);
            }
            FxTickScheduler.scheduleClient(6L, () -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level == null || mc.level.getGameTime() - lastPostEffectTouchTick >= 5L) {
                    if (isStormMode(AngryPostEffects.mode())) {
                        AngryPostEffects.set(-1);
                    }
                }
            });
        } else if (isStormMode(AngryPostEffects.mode()) && gameTime - lastPostEffectTouchTick >= 2L) {
            AngryPostEffects.set(-1);
        }
    }

    private static boolean isStormMode(int mode) {
        return mode == 2 || mode == 3;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        submitCloudCanopy(state, poseStack, collector);
        submitCentralStormEye(state, poseStack, collector);
        if (state.phase == 4 || state.phase == 7) {
            submitHailstones(state, poseStack, collector);
        }
        submitGroundRitualArray(state, poseStack, collector);
        if (state.phase == 2) {
            submitAcidRainCurtains(state, poseStack, collector);
        }
        if (state.phase == 3) {
            submitLightningPillars(state, poseStack, collector);
        }
        if (state.phase == 5) {
            submitFireballRain(state, poseStack, collector);
        }
        if (state.phase == 6) {
            submitRavenousVoid(state, poseStack, collector);
        }
        if (state.phase == 7) {
            submitCycloneFunnel(state, poseStack, collector);
        }
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    private static void submitCloudCanopy(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        float age = state.stormAge;
        int innerColor = phaseInnerCloudColor(state.phase);
        int outerColor = phaseOuterCloudColor(state.phase);
        int rimColor = phaseGlowColor(state.phase);

        for (int layer = 0; layer < 3; layer++) {
            float y = (float) AngryStorm.CANOPY_HEIGHT + (layer - 1) * 1.35F;
            float direction = layer % 2 == 0 ? 1.0F : -1.0F;
            float spin = age * (1.8F + layer * 0.65F) * direction + layer * 37.0F;
            float innerRadius = (2.8F + layer * 1.4F) * state.amplifier;
            float midRadius = state.radius * (0.48F + layer * 0.12F);
            float outerRadius = state.radius * (0.88F + layer * 0.11F);

            poseStack.pushPose();
            poseStack.translate(0.0F, y, 0.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees(spin));
            collector.submitCustomGeometry(poseStack, CLOUD_RENDER_TYPE, (pose, buffer) -> {
                for (int i = 0; i < CANOPY_SIDES; i++) {
                    float a0 = i * Mth.TWO_PI / CANOPY_SIDES;
                    float a1 = (i + 1) * Mth.TWO_PI / CANOPY_SIDES;
                    float cos0 = Mth.cos(a0);
                    float sin0 = Mth.sin(a0);
                    float cos1 = Mth.cos(a1);
                    float sin1 = Mth.sin(a1);

                    float wave0 = Mth.sin(a0 * 4.0F + age * 0.12F) * 0.65F;
                    float wave1 = Mth.sin(a1 * 4.0F + age * 0.12F) * 0.65F;

                    doubleSidedQuad(pose, buffer,
                            cos0 * innerRadius, -0.45F, sin0 * innerRadius, rimColor,
                            cos1 * innerRadius, -0.45F, sin1 * innerRadius, rimColor,
                            cos1 * midRadius, 0.55F + wave1, sin1 * midRadius, innerColor,
                            cos0 * midRadius, 0.55F + wave0, sin0 * midRadius, innerColor);

                    doubleSidedQuad(pose, buffer,
                            cos0 * midRadius, 0.55F + wave0, sin0 * midRadius, innerColor,
                            cos1 * midRadius, 0.55F + wave1, sin1 * midRadius, innerColor,
                            cos1 * outerRadius, -0.35F - wave1 * 0.5F, sin1 * outerRadius, outerColor,
                            cos0 * outerRadius, -0.35F - wave0 * 0.5F, sin0 * outerRadius, outerColor);
                }
            });
            poseStack.popPose();
        }
    }

    private static void submitCentralStormEye(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        float age = state.stormAge;
        float baseEyeY = (float) AngryStorm.CANOPY_HEIGHT - 1.5F;
        float eyeY = baseEyeY;
        if (state.phase == 7 && age > AngryStorm.TOTAL_DURATION - 50) {
            float progress = Mth.clamp((age - (AngryStorm.TOTAL_DURATION - 50)) / 50.0F, 0.0F, 1.0F);
            eyeY = Mth.lerp(progress * progress, baseEyeY, 2.0F);
        }
        float coreScale = state.phase == 6 ? 0.55F : 1.0F;
        float coreSpin = state.phase == 6 ? 3.0F : 1.0F;

        poseStack.pushPose();
        poseStack.translate(0.0F, eyeY, 0.0F);

        poseStack.pushPose();
        poseStack.scale(2.5F * coreScale, 2.5F * coreScale, 2.5F * coreScale);
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 6.5F * coreSpin));
        poseStack.mulPose(Axis.XP.rotationDegrees(Mth.sin(age * 0.15F) * 18.0F));
        poseStack.translate(-1.0F, -1.0F, -1.0F);
        state.coreModel.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();

        int ringColorA = phaseGlowColor(state.phase);
        int ringColorB = 0xEEFFFFFF;
        for (int g = 0; g < 2; g++) {
            float ringRadius = 3.6F + g * 1.1F;
            float tilt = g == 0 ? 32.0F : -38.0F;
            float speed = g == 0 ? 5.2F : -6.4F;
            int color = g == 0 ? ringColorA : ringColorB;
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(age * speed));
            poseStack.mulPose(Axis.ZP.rotationDegrees(tilt));
            poseStack.mulPose(Axis.XP.rotationDegrees(age * speed * 0.65F));
            collector.submitCustomGeometry(poseStack, VORTEX_GLOW_RENDER_TYPE,
                    (pose, buffer) -> ringRibbon(pose, buffer, ringRadius, 0.28F, 0.32F, CANOPY_SIDES, color));
            poseStack.popPose();
        }

        poseStack.popPose();
    }

    private static void submitHailstones(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        float age = state.stormAge;
        for (int i = 0; i < HAILSTONE_COUNT; i++) {
            float seed = i * 1.731F;
            float cycle = (age * 0.045F + i / (float) HAILSTONE_COUNT) % 1.0F;
            float y = Mth.lerp(cycle, 25.0F, 0.8F);
            float orbitRadius = (0.18F + 0.72F * ((i * 5 % HAILSTONE_COUNT) / (float) HAILSTONE_COUNT))
                    * state.radius * (1.0F - cycle * 0.25F);
            float orbitAngle = age * (0.08F + (i % 4) * 0.02F) + seed * 3.8F;
            float x = Mth.cos(orbitAngle) * orbitRadius;
            float z = Mth.sin(orbitAngle) * orbitRadius;
            float scale = 0.45F + (i % 5) * 0.05F;

            poseStack.pushPose();
            poseStack.translate(x, y, z);
            poseStack.scale(scale, scale, scale);
            poseStack.mulPose(Axis.YP.rotationDegrees(age * (9.0F + i) + i * 30.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(age * (7.0F + (i % 3) * 2.0F) + i * 45.0F));
            poseStack.translate(-1.0F, -1.0F, -1.0F);
            state.coreModel.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }

    private static void submitGroundRitualArray(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        float age = state.stormAge;
        int primary = phaseGlowColor(state.phase);
        int secondary = phaseInnerCloudColor(state.phase);

        poseStack.pushPose();
        poseStack.translate(0.0F, 0.12F, 0.0F);

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 1.6F));
        collector.submitCustomGeometry(poseStack, VORTEX_GLOW_RENDER_TYPE, (pose, buffer) -> {
            flatRing(pose, buffer, state.radius * 0.96F, state.radius, CANOPY_SIDES, primary);
            flatRing(pose, buffer, state.radius * 0.58F, state.radius * 0.62F, CANOPY_SIDES, secondary);
            for (int i = 0; i < 8; i++) {
                float angle = i * Mth.TWO_PI / 8.0F;
                float cos = Mth.cos(angle);
                float sin = Mth.sin(angle);
                float nx = -sin * 0.22F;
                float nz = cos * 0.22F;
                float r0 = state.radius * 0.24F;
                float r1 = state.radius * 0.96F;
                doubleSidedQuad(pose, buffer,
                        cos * r0 - nx, 0.02F, sin * r0 - nz, primary,
                        cos * r0 + nx, 0.02F, sin * r0 + nz, primary,
                        cos * r1 + nx, 0.02F, sin * r1 + nz, secondary,
                        cos * r1 - nx, 0.02F, sin * r1 - nz, secondary);
            }
        });
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-age * 2.7F));
        collector.submitCustomGeometry(poseStack, VORTEX_GLOW_RENDER_TYPE,
                (pose, buffer) -> flatRing(pose, buffer, state.radius * 0.24F, state.radius * 0.29F, 8, primary));
        poseStack.popPose();

        poseStack.popPose();
    }

    private static void submitAcidRainCurtains(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        float age = state.stormAge;
        int acidGreen = 0xAA38FF6A;
        int crimson = 0xAAFF1E56;

        collector.submitCustomGeometry(poseStack, VORTEX_RENDER_TYPE, (pose, buffer) -> {
            for (int i = 0; i < RAIN_RIBBON_COUNT; i++) {
                float baseAngle = i * Mth.TWO_PI / RAIN_RIBBON_COUNT + age * 0.035F;
                float radialBand = state.radius * (0.22F + 0.72F * ((i * 7 % RAIN_RIBBON_COUNT) / (float) RAIN_RIBBON_COUNT));
                float topX = Mth.cos(baseAngle) * radialBand;
                float topZ = Mth.sin(baseAngle) * radialBand;
                float bottomAngle = baseAngle + 0.28F;
                float bottomX = Mth.cos(bottomAngle) * (radialBand * 0.92F);
                float bottomZ = Mth.sin(bottomAngle) * (radialBand * 0.92F);

                float width = 0.42F;
                float nx = -Mth.sin(baseAngle) * width;
                float nz = Mth.cos(baseAngle) * width;
                int color = i % 2 == 0 ? acidGreen : crimson;

                doubleSidedQuad(pose, buffer,
                        topX - nx, (float) AngryStorm.CANOPY_HEIGHT, topZ - nz, color,
                        topX + nx, (float) AngryStorm.CANOPY_HEIGHT, topZ + nz, color,
                        bottomX + nx, 0.15F, bottomZ + nz, color,
                        bottomX - nx, 0.15F, bottomZ - nz, color);
            }
        });
    }

    private static void submitLightningPillars(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        float age = state.stormAge;
        int coreWhite = 0xEEECFAFF;
        int outerCyan = 0xCC3AE8FF;
        int lockViolet = 0xDDAA42FF;

        collector.submitCustomGeometry(poseStack, VORTEX_GLOW_RENDER_TYPE, (pose, buffer) -> {
            for (int i = 0; i < PILLAR_COUNT; i++) {
                float angle = i * Mth.TWO_PI / PILLAR_COUNT + age * 0.048F;
                float radial = state.radius * (0.45F + 0.35F * Mth.sin(age * 0.09F + i * 1.3F));
                float targetX = Mth.cos(angle) * radial;
                float targetZ = Mth.sin(angle) * radial;

                int segments = 8;
                float prevX = targetX * 0.75F;
                float prevZ = targetZ * 0.75F;
                float prevY = (float) AngryStorm.CANOPY_HEIGHT;

                for (int s = 1; s <= segments; s++) {
                    float t = s / (float) segments;
                    float curY = Mth.lerp(t, (float) AngryStorm.CANOPY_HEIGHT, 0.15F);
                    float jag = s < segments ? Mth.sin(age * 0.85F + i * 2.7F + s * 4.1F) * 0.95F : 0.0F;
                    float jagZ = s < segments ? Mth.cos(age * 0.75F + i * 3.3F + s * 3.7F) * 0.95F : 0.0F;
                    float curX = Mth.lerp(t, targetX * 0.75F, targetX) + jag;
                    float curZ = Mth.lerp(t, targetZ * 0.75F, targetZ) + jagZ;

                    pillarSegment(pose, buffer, prevX, prevY, prevZ, curX, curY, curZ, 0.58F, outerCyan);
                    pillarSegment(pose, buffer, prevX, prevY, prevZ, curX, curY, curZ, 0.24F, coreWhite);

                    prevX = curX;
                    prevY = curY;
                    prevZ = curZ;
                }

                offsetFlatRing(pose, buffer, targetX, 0.16F, targetZ, 1.3F, 1.85F, 8, lockViolet);
            }
        });
    }

    private static void submitFireballRain(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        float age = state.stormAge;
        float radius = state.radius;
        float topY = (float) AngryStorm.CANOPY_HEIGHT;

        collector.submitCustomGeometry(poseStack, VORTEX_GLOW_RENDER_TYPE, (pose, buffer) -> {
            for (int i = 0; i < FIREBALL_STREAK_COUNT; i++) {
                float t = (age * 0.055F + i / (float) FIREBALL_STREAK_COUNT) % 1.0F;
                float angle = i * Mth.TWO_PI / FIREBALL_STREAK_COUNT + age * 0.05F;
                float radialBand = radius * (0.20F + 0.70F
                        * ((i * 7) % FIREBALL_STREAK_COUNT) / (float) FIREBALL_STREAK_COUNT);
                float headY = Mth.lerp(t, topY, 0.4F);
                float tailY = Math.min(headY + 4.2F, topY);
                float x = Mth.cos(angle) * radialBand;
                float z = Mth.sin(angle) * radialBand;
                float nx = -Mth.sin(angle) * 0.22F;
                float nz = Mth.cos(angle) * 0.22F;
                int bodyColor = i % 2 == 0 ? 0xFFFF7A22 : 0xFFFFC24A;

                doubleSidedQuad(pose, buffer,
                        x - nx, headY, z - nz, bodyColor,
                        x + nx, headY, z + nz, bodyColor,
                        x + nx, tailY, z + nz, bodyColor,
                        x - nx, tailY, z - nz, bodyColor);

                float headTop = Math.min(headY + 1.1F, tailY);
                if (headTop > headY) {
                    int headColor = 0xFFFFF0B8;
                    doubleSidedQuad(pose, buffer,
                            x - nx * 1.5F, headY, z - nz * 1.5F, headColor,
                            x + nx * 1.5F, headY, z + nz * 1.5F, headColor,
                            x + nx * 1.5F, headTop, z + nz * 1.5F, headColor,
                            x - nx * 1.5F, headTop, z - nz * 1.5F, headColor);
                }
            }
        });

        poseStack.pushPose();
        poseStack.translate(0.0F, 0.18F, 0.0F);
        collector.submitCustomGeometry(poseStack, VORTEX_GLOW_RENDER_TYPE, (pose, buffer) -> {
            for (int i = 0; i < EMBER_RING_COUNT; i++) {
                float ringRadius = radius * (0.15F + 0.12F * i);
                int color = withAlpha(0xAAFF8A2A, 0.55F + 0.45F * Mth.sin(age * 0.11F + i));
                flatRing(pose, buffer, ringRadius * 0.94F, ringRadius * 1.06F, CANOPY_SIDES, color);
            }
        });
        poseStack.popPose();
    }

    private static void submitRavenousVoid(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        float age = state.stormAge;
        float radius = state.radius;
        float vr = state.voidRadius;
        float coreY = (float) AngryStorm.VOID_CORE_HEIGHT;
        float swellStart = AngryStorm.PHASE_6_END - 30.0F;
        float swell = age > swellStart
                ? 1.0F + 0.25F * Mth.clamp((age - swellStart) / 30.0F, 0.0F, 1.0F)
                : 1.0F;
        float sphereScale = (1.0F + 0.03F * Mth.sin(age * 0.12F)) * swell;
        int horizonColor = 0xCC14001E;

        poseStack.pushPose();
        poseStack.translate(0.0F, coreY, 0.0F);
        poseStack.scale(sphereScale, sphereScale, sphereScale);
        collector.submitCustomGeometry(poseStack, VOID_RENDER_TYPE, (pose, buffer) -> {
            for (int lat = 0; lat < VOID_LATITUDE_BANDS; lat++) {
                float phi0 = lat * Mth.PI / VOID_LATITUDE_BANDS;
                float phi1 = (lat + 1) * Mth.PI / VOID_LATITUDE_BANDS;
                float ring0 = Mth.sin(phi0) * vr;
                float ring1 = Mth.sin(phi1) * vr;
                float y0 = Mth.cos(phi0) * vr;
                float y1 = Mth.cos(phi1) * vr;

                for (int lon = 0; lon < VOID_LONGITUDE_BANDS; lon++) {
                    float theta0 = lon * Mth.TWO_PI / VOID_LONGITUDE_BANDS;
                    float theta1 = (lon + 1) * Mth.TWO_PI / VOID_LONGITUDE_BANDS;
                    float cos0 = Mth.cos(theta0);
                    float sin0 = Mth.sin(theta0);
                    float cos1 = Mth.cos(theta1);
                    float sin1 = Mth.sin(theta1);

                    doubleSidedQuad(pose, buffer,
                            cos0 * ring0, y0, sin0 * ring0, horizonColor,
                            cos1 * ring0, y0, sin1 * ring0, horizonColor,
                            cos1 * ring1, y1, sin1 * ring1, horizonColor,
                            cos0 * ring1, y1, sin0 * ring1, horizonColor);
                }
            }
        });
        poseStack.popPose();

        for (int i = 0; i < 3; i++) {
            float ringRadius = switch (i) {
                case 0 -> vr * 1.5F;
                case 1 -> vr * 2.0F;
                default -> vr * 2.6F;
            };
            float thickness = switch (i) {
                case 0 -> 0.35F;
                case 1 -> 0.5F;
                default -> 0.7F;
            };
            float spin = switch (i) {
                case 0 -> age * 4.5F;
                case 1 -> age * -3.2F;
                default -> age * 2.4F;
            };
            int color = switch (i) {
                case 0 -> 0xCC9A3CFF;
                case 1 -> 0xCCE8D8FF;
                default -> 0xCCFF2A66;
            };
            poseStack.pushPose();
            poseStack.translate(0.0F, coreY, 0.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees(spin));
            if (i == 1) {
                poseStack.mulPose(Axis.ZP.rotationDegrees(-22.0F));
            } else {
                poseStack.mulPose(Axis.XP.rotationDegrees(i == 0 ? 28.0F : 64.0F));
            }
            collector.submitCustomGeometry(poseStack, VORTEX_GLOW_RENDER_TYPE,
                    (pose, buffer) -> ringRibbon(pose, buffer, ringRadius, thickness, thickness,
                            CANOPY_SIDES, color));
            poseStack.popPose();
        }

        collector.submitCustomGeometry(poseStack, VORTEX_GLOW_RENDER_TYPE, (pose, buffer) -> {
            for (int arm = 0; arm < VOID_ARM_COUNT; arm++) {
                float armOffset = arm * Mth.TWO_PI / VOID_ARM_COUNT;
                for (int s = 0; s < VOID_ARM_STEPS; s++) {
                    float t0 = s / (float) VOID_ARM_STEPS;
                    float t1 = (s + 1) / (float) VOID_ARM_STEPS;
                    float r0 = Mth.lerp(t0, radius * 0.92F, vr * 1.35F);
                    float r1 = Mth.lerp(t1, radius * 0.92F, vr * 1.35F);
                    float y0 = Mth.lerp(t0, 0.4F, coreY);
                    float y1 = Mth.lerp(t1, 0.4F, coreY);
                    float a0 = armOffset + t0 * Mth.TWO_PI * 1.5F - age * 0.10F;
                    float a1 = armOffset + t1 * Mth.TWO_PI * 1.5F - age * 0.10F;
                    float w0 = Mth.lerp(t0, 0.9F, 0.25F);
                    float w1 = Mth.lerp(t1, 0.9F, 0.25F);
                    float cos0 = Mth.cos(a0);
                    float sin0 = Mth.sin(a0);
                    float cos1 = Mth.cos(a1);
                    float sin1 = Mth.sin(a1);
                    int c0 = lerpColor(0xBB7A1EFF, 0xEEFFFFFF, t0);
                    int c1 = lerpColor(0xBB7A1EFF, 0xEEFFFFFF, t1);

                    doubleSidedQuad(pose, buffer,
                            cos0 * r0 - sin0 * w0, y0, sin0 * r0 + cos0 * w0, c0,
                            cos0 * r0 + sin0 * w0, y0, sin0 * r0 - cos0 * w0, c0,
                            cos1 * r1 + sin1 * w1, y1, sin1 * r1 - cos1 * w1, c1,
                            cos1 * r1 - sin1 * w1, y1, sin1 * r1 + cos1 * w1, c1);
                }
            }
        });

        poseStack.pushPose();
        poseStack.translate(0.0F, coreY, 0.0F);
        collector.submitCustomGeometry(poseStack, VORTEX_GLOW_RENDER_TYPE, (pose, buffer) -> {
            float pulse = 0.70F + 0.30F * Mth.sin(age * 0.09F);
            flatRing(pose, buffer, vr * 1.05F, vr * 1.25F, CANOPY_SIDES, withAlpha(0xCCB26AFF, pulse));
            flatRing(pose, buffer, vr * 2.9F, vr * 3.05F, CANOPY_SIDES, withAlpha(0x88E8D8FF, pulse));
        });
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.translate(0.0F, 0.15F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(age * 2.2F));
        collector.submitCustomGeometry(poseStack, VORTEX_GLOW_RENDER_TYPE,
                (pose, buffer) -> flatRing(pose, buffer, radius * 0.30F, radius * 0.34F, CANOPY_SIDES, 0xAA7A1EFF));
        poseStack.popPose();
    }

    private static void submitCycloneFunnel(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        float age = state.stormAge;
        int ringCount = 9;
        int voidColor = 0xAA260438;
        int crimsonGlow = 0xCCFF184C;
        int iceCyan = 0xCC48F0FF;

        collector.submitCustomGeometry(poseStack, VORTEX_RENDER_TYPE, (pose, buffer) -> {
            for (int r = 0; r < ringCount; r++) {
                float t0 = r / (float) ringCount;
                float t1 = (r + 1) / (float) ringCount;
                float y0 = t0 * (float) AngryStorm.CANOPY_HEIGHT;
                float y1 = t1 * (float) AngryStorm.CANOPY_HEIGHT;
                float rad0 = Mth.lerp(t0 * t0, state.radius * 0.18F, state.radius * 0.86F);
                float rad1 = Mth.lerp(t1 * t1, state.radius * 0.18F, state.radius * 0.86F);
                float twist0 = age * 0.14F + r * 0.55F;
                float twist1 = age * 0.14F + (r + 1) * 0.55F;
                int c0 = r % 2 == 0 ? voidColor : crimsonGlow;
                int c1 = (r + 1) % 2 == 0 ? voidColor : iceCyan;

                for (int i = 0; i < CANOPY_SIDES; i++) {
                    float a0 = i * Mth.TWO_PI / CANOPY_SIDES;
                    float a1 = (i + 1) * Mth.TWO_PI / CANOPY_SIDES;

                    float x00 = Mth.cos(a0 + twist0) * rad0;
                    float z00 = Mth.sin(a0 + twist0) * rad0;
                    float x10 = Mth.cos(a1 + twist0) * rad0;
                    float z10 = Mth.sin(a1 + twist0) * rad0;

                    float x01 = Mth.cos(a0 + twist1) * rad1;
                    float z01 = Mth.sin(a0 + twist1) * rad1;
                    float x11 = Mth.cos(a1 + twist1) * rad1;
                    float z11 = Mth.sin(a1 + twist1) * rad1;

                    doubleSidedQuad(pose, buffer,
                            x00, y0, z00, c0,
                            x10, y0, z10, c0,
                            x11, y1, z11, c1,
                            x01, y1, z01, c1);
                }
            }
        });

        collector.submitCustomGeometry(poseStack, VORTEX_GLOW_RENDER_TYPE, (pose, buffer) -> {
            for (int arm = 0; arm < 4; arm++) {
                float armOffset = arm * Mth.HALF_PI;
                int steps = 14;
                for (int s = 0; s < steps; s++) {
                    float t0 = s / (float) steps;
                    float t1 = (s + 1) / (float) steps;
                    float r0 = Mth.lerp(t0, state.radius * 0.95F, 1.4F);
                    float r1 = Mth.lerp(t1, state.radius * 0.95F, 1.4F);
                    float a0 = armOffset + t0 * Mth.TWO_PI * 2.0F - age * 0.18F;
                    float a1 = armOffset + t1 * Mth.TWO_PI * 2.0F - age * 0.18F;
                    float y0 = 0.25F + t0 * 6.5F;
                    float y1 = 0.25F + t1 * 6.5F;
                    int color = arm % 2 == 0 ? crimsonGlow : iceCyan;

                    doubleSidedQuad(pose, buffer,
                            Mth.cos(a0) * r0, y0, Mth.sin(a0) * r0, color,
                            Mth.cos(a0) * (r0 + 0.45F), y0 + 0.35F, Mth.sin(a0) * (r0 + 0.45F), color,
                            Mth.cos(a1) * (r1 + 0.45F), y1 + 0.35F, Mth.sin(a1) * (r1 + 0.45F), color,
                            Mth.cos(a1) * r1, y1, Mth.sin(a1) * r1, color);
                }
            }
        });
    }

    private static int phaseInnerCloudColor(int phase) {
        return switch (phase) {
            case 1 -> 0xD85A189A;
            case 2 -> 0xD81E8A4C;
            case 3 -> 0xD82874FC;
            case 4 -> 0xD8E84A18;
            case 5 -> 0xD8FF7A1E;
            case 6 -> 0xE0120418;
            default -> 0xE67A0828;
        };
    }

    private static int phaseOuterCloudColor(int phase) {
        return switch (phase) {
            case 1 -> 0xB8240046;
            case 2 -> 0xB83C096C;
            case 3 -> 0xB81D1148;
            case 4 -> 0xB812385B;
            case 5 -> 0xB8701800;
            case 6 -> 0xCC040008;
            default -> 0xCC10001C;
        };
    }

    private static int phaseGlowColor(int phase) {
        return switch (phase) {
            case 1 -> 0xEEFF2A55;
            case 2 -> 0xEE38FF6A;
            case 3 -> 0xEE5CE6FF;
            case 4 -> 0xEEFF781F;
            case 5 -> 0xEEFFB43A;
            case 6 -> 0xEE8A2AFF;
            default -> 0xEEFF1048;
        };
    }

    private static int lerpColor(int from, int to, float delta) {
        int a = (int) Mth.lerp(delta, (float) ((from >>> 24) & 0xFF), (float) ((to >>> 24) & 0xFF));
        int r = (int) Mth.lerp(delta, (float) ((from >>> 16) & 0xFF), (float) ((to >>> 16) & 0xFF));
        int g = (int) Mth.lerp(delta, (float) ((from >>> 8) & 0xFF), (float) ((to >>> 8) & 0xFF));
        int b = (int) Mth.lerp(delta, (float) (from & 0xFF), (float) (to & 0xFF));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int withAlpha(int color, float factor) {
        int alpha = (int) Math.clamp(((color >>> 24) & 0xFF) * factor, 0.0F, 255.0F);
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private static void pillarSegment(PoseStack.Pose pose, VertexConsumer buffer,
            float x0, float y0, float z0, float x1, float y1, float z1, float halfWidth, int color) {
        doubleSidedQuad(pose, buffer,
                x0 - halfWidth, y0, z0, color,
                x0 + halfWidth, y0, z0, color,
                x1 + halfWidth, y1, z1, color,
                x1 - halfWidth, y1, z1, color);
        doubleSidedQuad(pose, buffer,
                x0, y0, z0 - halfWidth, color,
                x0, y0, z0 + halfWidth, color,
                x1, y1, z1 + halfWidth, color,
                x1, y1, z1 - halfWidth, color);
    }

    private static void ringRibbon(PoseStack.Pose pose, VertexConsumer buffer, float radius, float thickness,
            float height, int sides, int color) {
        float inner = radius - thickness * 0.5F;
        float outer = radius + thickness * 0.5F;
        float halfH = height * 0.5F;
        for (int i = 0; i < sides; i++) {
            float a0 = i * Mth.TWO_PI / sides;
            float a1 = (i + 1) * Mth.TWO_PI / sides;
            float c0 = Mth.cos(a0);
            float s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1);
            float s1 = Mth.sin(a1);
            doubleSidedQuad(pose, buffer,
                    c0 * outer, -halfH, s0 * outer, color,
                    c1 * outer, -halfH, s1 * outer, color,
                    c1 * inner, halfH, s1 * inner, color,
                    c0 * inner, halfH, s0 * inner, color);
        }
    }

    private static void flatRing(PoseStack.Pose pose, VertexConsumer buffer, float innerRadius, float outerRadius,
            int sides, int color) {
        offsetFlatRing(pose, buffer, 0.0F, 0.0F, 0.0F, innerRadius, outerRadius, sides, color);
    }

    private static void offsetFlatRing(PoseStack.Pose pose, VertexConsumer buffer, float cx, float cy, float cz,
            float innerRadius, float outerRadius, int sides, int color) {
        for (int i = 0; i < sides; i++) {
            float a0 = i * Mth.TWO_PI / sides;
            float a1 = (i + 1) * Mth.TWO_PI / sides;
            float c0 = Mth.cos(a0);
            float s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1);
            float s1 = Mth.sin(a1);
            doubleSidedQuad(pose, buffer,
                    cx + c0 * innerRadius, cy, cz + s0 * innerRadius, color,
                    cx + c1 * innerRadius, cy, cz + s1 * innerRadius, color,
                    cx + c1 * outerRadius, cy, cz + s1 * outerRadius, color,
                    cx + c0 * outerRadius, cy, cz + s0 * outerRadius, color);
        }
    }

    private static void doubleSidedQuad(PoseStack.Pose pose, VertexConsumer buffer,
            float x0, float y0, float z0, int c0,
            float x1, float y1, float z1, int c1,
            float x2, float y2, float z2, int c2,
            float x3, float y3, float z3, int c3) {
        buffer.addVertex(pose, x0, y0, z0).setColor(c0);
        buffer.addVertex(pose, x1, y1, z1).setColor(c1);
        buffer.addVertex(pose, x2, y2, z2).setColor(c2);
        buffer.addVertex(pose, x3, y3, z3).setColor(c3);
    }

    public static class State extends EntityRenderState {
        public final BlockModelRenderState coreModel = new BlockModelRenderState();
        public float stormAge;
        public int phase = 1;
        public float radius = AngryStorm.BASE_RADIUS;
        public float voidRadius = AngryStorm.VOID_RADIUS;
        public float amplifier = 1.0F;
    }
}
