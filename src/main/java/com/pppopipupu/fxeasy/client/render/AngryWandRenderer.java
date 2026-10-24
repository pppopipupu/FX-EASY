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
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.pppopipupu.fxeasy.FxEasy;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.SpecialModelWrapper;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterItemModelsEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/// Draws the staff, its floating angry core, its crown and its runes as Blaze3D quads in the item model's unit
/// cube. The emissive parts are a second submission with an additive blend, so the body keeps its depth while
/// the runes and the halo bloom over it.
public final class AngryWandRenderer implements NoDataSpecialModelRenderer {

    private static final int BODY = 0xFF2A1740;
    private static final int BODY_DARK = 0xFF120A1C;
    private static final int METAL = 0xFF5A3A8A;
    private static final int VIOLET = 0xFF9A6BFF;
    private static final int CYAN = 0xFF6FF2FF;
    private static final int EMBER = 0xFFFF3A18;
    private static final int WHITE_HOT = 0xFFFFFFFF;
    private static final float MODEL_SCALE = 1.3F;

    public static final RenderPipeline PIPELINE = RenderPipeline
            .builder(RenderPipelines.MATRICES_FOG_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(FxEasy.MODID, "pipeline/angry_wand"))
            .withVertexShader(Identifier.fromNamespaceAndPath(FxEasy.MODID, "core/angry_wand"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(FxEasy.MODID, "core/angry_wand"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withCull(false)
            .build();

    public static final RenderPipeline GLOW_PIPELINE = RenderPipeline
            .builder(RenderPipelines.MATRICES_FOG_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(FxEasy.MODID, "pipeline/angry_wand_glow"))
            .withVertexShader(Identifier.fromNamespaceAndPath(FxEasy.MODID, "core/angry_wand"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(FxEasy.MODID, "core/angry_wand"))
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withCull(false)
            .build();

    private static final RenderType RENDER_TYPE = RenderType.create("angry_wand",
            RenderSetup.builder(PIPELINE).createRenderSetup());
    private static final RenderType GLOW_RENDER_TYPE = RenderType.create("angry_wand_glow",
            RenderSetup.builder(GLOW_PIPELINE).createRenderSetup());

    private static final List<Vector3fc> EXTENTS = List.of(
            new Vector3f(-0.31F, -0.31F, -0.31F),
            new Vector3f(1.31F, -0.31F, -0.31F),
            new Vector3f(1.31F, 1.31F, -0.31F),
            new Vector3f(-0.31F, 1.31F, -0.31F),
            new Vector3f(-0.31F, -0.31F, 1.31F),
            new Vector3f(1.31F, -0.31F, 1.31F),
            new Vector3f(1.31F, 1.31F, 1.31F),
            new Vector3f(-0.31F, 1.31F, 1.31F));

    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(PIPELINE);
        event.registerPipeline(GLOW_PIPELINE);
    }

    public static void registerSpecialModelRenderers(RegisterSpecialModelRendererEvent event) {
        event.register(Identifier.fromNamespaceAndPath(FxEasy.MODID, "angry_wand"), Unbaked.MAP_CODEC);
    }

    public static void registerItemModels(RegisterItemModelsEvent event) {
        event.register(Identifier.fromNamespaceAndPath(FxEasy.MODID, "animated_wand"),
                AnimatedWandModel.Unbaked.MAP_CODEC);
    }

    public static ItemModel.Unbaked itemModel(Identifier base) {
        return new AnimatedWandModel.Unbaked(base, new Unbaked());
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, int overlayCoords,
            boolean hasFoil, int outlineColor) {
        float time = Util.getMillis() / 1000.0F;
        float bob = Mth.sin(time * 2.2F) * 0.012F;
        poseStack.pushPose();
        poseStack.translate(0.0F, bob, 0.0F);
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        submitRod(poseStack, collector);
        submitTwist(poseStack, collector, time);
        submitHead(poseStack, collector, time);
        submitProngs(poseStack, collector, time);
        submitHalo(poseStack, collector, time);
        submitRunes(poseStack, collector, time);
        poseStack.popPose();
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        EXTENTS.forEach(output);
    }

    private static void submitRod(PoseStack poseStack, SubmitNodeCollector collector) {
        collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> {
            box(pose, buffer, 0.43F, 0.04F, 0.43F, 0.57F, 0.07F, 0.57F, BODY_DARK);
            box(pose, buffer, 0.44F, 0.07F, 0.44F, 0.56F, 0.18F, 0.56F, BODY);
            box(pose, buffer, 0.47F, 0.16F, 0.47F, 0.53F, 0.68F, 0.53F, BODY_DARK);
            box(pose, buffer, 0.455F, 0.145F, 0.455F, 0.545F, 0.175F, 0.545F, METAL);
            box(pose, buffer, 0.455F, 0.655F, 0.455F, 0.545F, 0.685F, 0.545F, METAL);
            box(pose, buffer, 0.465F, 0.685F, 0.465F, 0.535F, 0.72F, 0.535F, BODY);
        });
        collector.submitCustomGeometry(poseStack, GLOW_RENDER_TYPE, (pose, buffer) -> {
            box(pose, buffer, 0.438F, 0.105F, 0.438F, 0.562F, 0.118F, 0.562F, VIOLET);
            box(pose, buffer, 0.438F, 0.128F, 0.438F, 0.562F, 0.136F, 0.562F, CYAN);
            box(pose, buffer, 0.462F, 0.705F, 0.462F, 0.538F, 0.718F, 0.538F, EMBER);
        });
    }

    private static void submitTwist(PoseStack poseStack, SubmitNodeCollector collector, float time) {
        for (int i = 0; i < 10; i++) {
            float y = 0.18F + i * 0.048F;
            float angle = i * 24.0F + time * 70.0F;
            float radians = angle * Mth.DEG_TO_RAD;
            float offset = 0.018F;
            poseStack.pushPose();
            poseStack.translate(0.5F + Mth.cos(radians) * offset, y,
                    0.5F + Mth.sin(radians) * offset);
            poseStack.mulPose(Axis.YP.rotationDegrees(angle));
            int color = i % 2 == 0 ? BODY : METAL;
            collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> box(pose, buffer,
                    -0.038F, -0.026F, -0.038F, 0.038F, 0.026F, 0.038F, color));
            poseStack.popPose();
        }
        for (int i = 0; i < 5; i++) {
            float y = 0.205F + i * 0.096F;
            float angle = i * 48.0F - time * 70.0F;
            float radians = angle * Mth.DEG_TO_RAD;
            poseStack.pushPose();
            poseStack.translate(0.5F + Mth.cos(radians) * 0.022F, y,
                    0.5F + Mth.sin(radians) * 0.022F);
            poseStack.mulPose(Axis.YP.rotationDegrees(angle));
            int color = i % 2 == 0 ? CYAN : VIOLET;
            collector.submitCustomGeometry(poseStack, GLOW_RENDER_TYPE, (pose, buffer) -> box(pose, buffer,
                    -0.042F, -0.006F, -0.042F, 0.042F, 0.006F, 0.042F, color));
            poseStack.popPose();
        }
    }

    private static void submitHead(PoseStack poseStack, SubmitNodeCollector collector, float time) {
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.80F + Mth.sin(time * 2.4F) * 0.01F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(time * 24.0F));
        collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> {
            box(pose, buffer, -0.09F, -0.09F, -0.09F, 0.09F, 0.09F, 0.09F, BODY);
            box(pose, buffer, -0.07F, 0.055F, 0.088F, 0.07F, 0.075F, 0.102F, METAL);
            box(pose, buffer, -0.065F, -0.065F, 0.088F, 0.065F, -0.045F, 0.102F, BODY_DARK);
        });
        collector.submitCustomGeometry(poseStack, GLOW_RENDER_TYPE, (pose, buffer) -> {
            box(pose, buffer, -0.065F, -0.005F, 0.09F, -0.02F, 0.04F, 0.106F, EMBER);
            box(pose, buffer, 0.02F, -0.005F, 0.09F, 0.065F, 0.04F, 0.106F, EMBER);
            box(pose, buffer, -0.045F, -0.06F, 0.09F, 0.045F, -0.048F, 0.106F, VIOLET);
            box(pose, buffer, -0.04F, 0.09F, -0.04F, 0.04F, 0.11F, 0.04F, WHITE_HOT);
        });
        poseStack.popPose();
    }

    private static void submitProngs(PoseStack poseStack, SubmitNodeCollector collector, float time) {
        for (int i = 0; i < 3; i++) {
            poseStack.pushPose();
            poseStack.translate(0.5F, 0.80F, 0.5F);
            poseStack.mulPose(Axis.YP.rotationDegrees(i * 120.0F + time * 18.0F));
            poseStack.translate(0.10F, 0.02F, 0.0F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(-32.0F));
            collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> {
                box(pose, buffer, 0.0F, -0.018F, -0.018F, 0.14F, 0.018F, 0.018F, BODY);
                box(pose, buffer, 0.12F, 0.0F, -0.014F, 0.23F, 0.03F, 0.014F, VIOLET);
            });
            poseStack.popPose();
            poseStack.pushPose();
            poseStack.translate(0.5F, 0.80F, 0.5F);
            poseStack.mulPose(Axis.YP.rotationDegrees(i * 120.0F + time * 18.0F));
            poseStack.translate(0.10F, 0.02F, 0.0F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(-32.0F));
            collector.submitCustomGeometry(poseStack, GLOW_RENDER_TYPE, (pose, buffer) -> {
                box(pose, buffer, 0.21F, 0.02F, -0.022F, 0.27F, 0.06F, 0.022F, CYAN);
                box(pose, buffer, 0.0F, 0.018F, -0.02F, 0.02F, 0.03F, 0.02F, EMBER);
            });
            poseStack.popPose();
        }
    }

    private static void submitHalo(PoseStack poseStack, SubmitNodeCollector collector, float time) {
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.82F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(time * 42.0F));
        collector.submitCustomGeometry(poseStack, GLOW_RENDER_TYPE, (pose, buffer) -> {
            for (int i = 0; i < 16; i++) {
                float angle = i * Mth.TWO_PI / 16.0F;
                float x = Mth.cos(angle) * 0.19F;
                float z = Mth.sin(angle) * 0.19F;
                box(pose, buffer, x - 0.012F, -0.006F, z - 0.012F, x + 0.012F, 0.012F, z + 0.012F,
                        i % 2 == 0 ? CYAN : VIOLET);
            }
        });
        poseStack.popPose();
    }

    private static void submitRunes(PoseStack poseStack, SubmitNodeCollector collector, float time) {
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-time * 55.0F));
        collector.submitCustomGeometry(poseStack, GLOW_RENDER_TYPE, (pose, buffer) -> {
            for (int i = 0; i < 4; i++) {
                float angle = i * Mth.HALF_PI;
                float x = Mth.cos(angle) * 0.105F;
                float z = Mth.sin(angle) * 0.105F;
                float y = 0.25F + i * 0.10F;
                box(pose, buffer, x - 0.01F, y - 0.025F, z - 0.01F, x + 0.01F, y + 0.025F, z + 0.01F,
                        i % 2 == 0 ? EMBER : CYAN);
            }
        });
        poseStack.popPose();
    }

    private static void box(PoseStack.Pose pose, VertexConsumer buffer, float x0, float y0, float z0,
            float x1, float y1, float z1, int color) {
        quad(pose, buffer, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, color);
        quad(pose, buffer, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, color);
        quad(pose, buffer, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, color);
        quad(pose, buffer, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, color);
        quad(pose, buffer, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, color);
        quad(pose, buffer, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, color);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, float x0, float y0, float z0,
            float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3,
            int color) {
        buffer.addVertex(pose, x0, y0, z0).setColor(color);
        buffer.addVertex(pose, x1, y1, z1).setColor(color);
        buffer.addVertex(pose, x2, y2, z2).setColor(color);
        buffer.addVertex(pose, x3, y3, z3).setColor(color);
    }

    public record Unbaked() implements NoDataSpecialModelRenderer.Unbaked {

        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(Unbaked::new);

        @Override
        public NoDataSpecialModelRenderer bake(SpecialModelRenderer.BakingContext context) {
            return new AngryWandRenderer();
        }

        @Override
        public MapCodec<? extends NoDataSpecialModelRenderer.Unbaked> type() {
            return MAP_CODEC;
        }
    }

    private record AnimatedWandModel(ItemModel delegate) implements ItemModel {

        @Override
        public void update(ItemStackRenderState output, ItemStack item, ItemModelResolver resolver,
                ItemDisplayContext displayContext, @Nullable ClientLevel level, @Nullable ItemOwner owner,
                int seed) {
            this.delegate.update(output, item, resolver, displayContext, level, owner, seed);
            output.setAnimated();
        }

        private record Unbaked(Identifier base, SpecialModelRenderer.Unbaked<?> model) implements ItemModel.Unbaked {

            public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(
                    instance -> instance.group(
                            Identifier.CODEC.fieldOf("base").forGetter(Unbaked::base),
                            SpecialModelRenderers.CODEC.fieldOf("model").forGetter(Unbaked::model))
                            .apply(instance, Unbaked::new));

            @Override
            public void resolveDependencies(ResolvableModel.Resolver resolver) {
                resolver.markDependency(this.base);
            }

            @Override
            public ItemModel bake(ItemModel.BakingContext context, Matrix4fc transformation) {
                return new AnimatedWandModel(new SpecialModelWrapper.Unbaked(this.base, Optional.empty(),
                        this.model).bake(context, transformation));
            }

            @Override
            public MapCodec<Unbaked> type() {
                return MAP_CODEC;
            }
        }
    }
}
