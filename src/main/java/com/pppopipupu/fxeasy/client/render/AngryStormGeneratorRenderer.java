package com.pppopipupu.fxeasy.client.render;

import module java.base;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.pppopipupu.fxeasy.FxEasy;
import com.pppopipupu.fxeasy.content.FxBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
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
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

public final class AngryStormGeneratorRenderer implements NoDataSpecialModelRenderer {

    private static final int OBSIDIAN = 0xFF0E0816;
    private static final int OBSIDIAN_LIGHT = 0xFF28193D;
    private static final int VIOLET_METAL = 0xFF5D348C;
    private static final int GOLD_TRIM = 0xFFE8B931;
    private static final int STORM_VIOLET = 0xFFB55CFF;
    private static final int STORM_CYAN = 0xFF4CF4FF;
    private static final int STORM_CRIMSON = 0xFFFF2458;
    private static final int WHITE_ARC = 0xFFFFFFFF;

    private static final RenderType RENDER_TYPE = RenderType.create("angry_storm_generator",
            RenderSetup.builder(AngryStormRenderer.VORTEX_PIPELINE).createRenderSetup());
    private static final RenderType GLOW_RENDER_TYPE = RenderType.create("angry_storm_generator_glow",
            RenderSetup.builder(AngryStormRenderer.VORTEX_GLOW_PIPELINE).createRenderSetup());

    private static final List<Vector3fc> EXTENTS = List.of(
            new Vector3f(-0.20F, -0.68F, -0.20F),
            new Vector3f(1.20F, 1.65F, 1.20F));

    private static final List<Vector3fc> SATELLITE_CENTERS = List.of(
            new Vector3f(0.02F, 0.96F, 0.50F),
            new Vector3f(0.98F, 0.96F, 0.50F),
            new Vector3f(0.50F, 0.96F, 0.02F),
            new Vector3f(0.50F, 0.96F, 0.98F));

    private final BlockModelRenderState coreModel = new BlockModelRenderState();

    public static void registerSpecialModelRenderers(RegisterSpecialModelRendererEvent event) {
        event.register(Identifier.fromNamespaceAndPath(FxEasy.MODID, "angry_storm_generator"),
                Unbaked.MAP_CODEC);
    }

    public static void registerItemModels(RegisterItemModelsEvent event) {
        event.register(Identifier.fromNamespaceAndPath(FxEasy.MODID, "animated_storm_generator"),
                AnimatedModel.Unbaked.MAP_CODEC);
    }

    public static ItemModel.Unbaked itemModel(Identifier base) {
        return new AnimatedModel.Unbaked(base, new Unbaked());
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, int overlayCoords,
            boolean hasFoil, int outlineColor) {
        float time = Util.getMillis() / 1000.0F;
        poseStack.pushPose();
        collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> {
            submitGripAndShaft(pose, buffer);
            submitContainmentArms(pose, buffer);
            submitSatelliteMounts(pose, buffer);
        });
        collector.submitCustomGeometry(poseStack, GLOW_RENDER_TYPE, (pose, buffer) -> {
            submitShaftRunes(pose, buffer, time);
            submitPlasmaTethers(pose, buffer, time);
        });
        submitGyroRings(poseStack, collector, time);
        submitStormCrown(poseStack, collector, time);
        submitCoreModels(poseStack, collector, lightCoords, overlayCoords, outlineColor, time);
        poseStack.popPose();
    }

    private void submitCoreModels(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
            int overlayCoords, int outlineColor, float time) {
        Minecraft.getInstance().getBlockModelResolver().update(this.coreModel,
                FxBlocks.ANGRY_BLOCK.get().defaultBlockState(), BlockDisplayContext.create());

        poseStack.pushPose();
        poseStack.translate(0.50F, 1.00F, 0.50F);
        poseStack.mulPose(Axis.YP.rotationDegrees(time * 28.0F));
        poseStack.scale(0.28F, 0.28F, 0.28F);
        poseStack.translate(-1.0F, -1.0F, -1.0F);
        this.coreModel.submit(poseStack, collector, lightCoords, overlayCoords, outlineColor);
        poseStack.popPose();

        for (int i = 0; i < SATELLITE_CENTERS.size(); i++) {
            Vector3fc center = SATELLITE_CENTERS.get(i);
            poseStack.pushPose();
            poseStack.translate(center.x(), center.y(), center.z());
            poseStack.mulPose(Axis.YP.rotationDegrees(time * 36.0F + i * 90.0F));
            poseStack.scale(0.12F, 0.12F, 0.12F);
            poseStack.translate(-1.0F, -1.0F, -1.0F);
            this.coreModel.submit(poseStack, collector, lightCoords, overlayCoords, outlineColor);
            poseStack.popPose();
        }
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        EXTENTS.forEach(output);
    }

    private static void submitGripAndShaft(PoseStack.Pose pose, VertexConsumer buffer) {
        box(pose, buffer, 0.42F, -0.65F, 0.42F, 0.58F, -0.55F, 0.58F, GOLD_TRIM);
        box(pose, buffer, 0.45F, -0.55F, 0.45F, 0.55F, -0.16F, 0.55F, OBSIDIAN);
        box(pose, buffer, 0.435F, -0.46F, 0.435F, 0.565F, -0.42F, 0.565F, VIOLET_METAL);
        box(pose, buffer, 0.435F, -0.30F, 0.435F, 0.565F, -0.26F, 0.565F, GOLD_TRIM);
        box(pose, buffer, 0.34F, -0.16F, 0.45F, 0.66F, -0.10F, 0.55F, OBSIDIAN_LIGHT);
        box(pose, buffer, 0.45F, -0.16F, 0.34F, 0.55F, -0.10F, 0.66F, OBSIDIAN_LIGHT);

        box(pose, buffer, 0.46F, -0.10F, 0.46F, 0.54F, 0.68F, 0.54F, OBSIDIAN);
        box(pose, buffer, 0.435F, -0.04F, 0.475F, 0.46F, 0.64F, 0.525F, OBSIDIAN_LIGHT);
        box(pose, buffer, 0.54F, -0.04F, 0.475F, 0.565F, 0.64F, 0.525F, OBSIDIAN_LIGHT);
        box(pose, buffer, 0.475F, -0.04F, 0.435F, 0.525F, 0.64F, 0.46F, OBSIDIAN_LIGHT);
        box(pose, buffer, 0.475F, -0.04F, 0.54F, 0.525F, 0.64F, 0.565F, OBSIDIAN_LIGHT);

        box(pose, buffer, 0.43F, 0.08F, 0.43F, 0.57F, 0.13F, 0.57F, GOLD_TRIM);
        box(pose, buffer, 0.43F, 0.28F, 0.43F, 0.57F, 0.33F, 0.57F, VIOLET_METAL);
        box(pose, buffer, 0.43F, 0.48F, 0.43F, 0.57F, 0.53F, 0.57F, GOLD_TRIM);
        box(pose, buffer, 0.36F, 0.62F, 0.36F, 0.64F, 0.70F, 0.64F, OBSIDIAN_LIGHT);
    }

    private static void submitContainmentArms(PoseStack.Pose pose, VertexConsumer buffer) {
        box(pose, buffer, 0.14F, 0.66F, 0.44F, 0.38F, 0.74F, 0.56F, OBSIDIAN_LIGHT);
        box(pose, buffer, 0.62F, 0.66F, 0.44F, 0.86F, 0.74F, 0.56F, OBSIDIAN_LIGHT);
        box(pose, buffer, 0.44F, 0.66F, 0.14F, 0.56F, 0.74F, 0.38F, OBSIDIAN_LIGHT);
        box(pose, buffer, 0.44F, 0.66F, 0.62F, 0.56F, 0.74F, 0.86F, OBSIDIAN_LIGHT);

        box(pose, buffer, 0.14F, 0.70F, 0.44F, 0.20F, 1.24F, 0.56F, OBSIDIAN);
        box(pose, buffer, 0.80F, 0.70F, 0.44F, 0.86F, 1.24F, 0.56F, OBSIDIAN);
        box(pose, buffer, 0.44F, 0.70F, 0.14F, 0.56F, 1.24F, 0.20F, OBSIDIAN);
        box(pose, buffer, 0.44F, 0.70F, 0.80F, 0.56F, 1.24F, 0.86F, OBSIDIAN);

        box(pose, buffer, 0.16F, 1.22F, 0.45F, 0.30F, 1.30F, 0.55F, GOLD_TRIM);
        box(pose, buffer, 0.70F, 1.22F, 0.45F, 0.84F, 1.30F, 0.55F, GOLD_TRIM);
        box(pose, buffer, 0.45F, 1.22F, 0.16F, 0.55F, 1.30F, 0.30F, GOLD_TRIM);
        box(pose, buffer, 0.45F, 1.22F, 0.70F, 0.55F, 1.30F, 0.84F, GOLD_TRIM);
    }

    private static void submitSatelliteMounts(PoseStack.Pose pose, VertexConsumer buffer) {
        box(pose, buffer, -0.02F, 0.88F, 0.44F, 0.12F, 0.92F, 0.56F, VIOLET_METAL);
        box(pose, buffer, 0.88F, 0.88F, 0.44F, 1.02F, 0.92F, 0.56F, VIOLET_METAL);
        box(pose, buffer, 0.44F, 0.88F, -0.02F, 0.56F, 0.92F, 0.12F, VIOLET_METAL);
        box(pose, buffer, 0.44F, 0.88F, 0.88F, 0.56F, 0.92F, 1.02F, VIOLET_METAL);
    }

    private static void submitShaftRunes(PoseStack.Pose pose, VertexConsumer buffer, float time) {
        for (int i = 0; i < 6; i++) {
            float flow = (i / 6.0F + time * 0.85F) % 1.0F;
            float y = -0.06F + flow * 0.68F;
            int color = i % 2 == 0 ? STORM_CYAN : STORM_VIOLET;
            box(pose, buffer, 0.432F, y, 0.482F, 0.568F, y + 0.045F, 0.518F, color);
            box(pose, buffer, 0.482F, y, 0.432F, 0.518F, y + 0.045F, 0.568F, color);
        }
    }

    private static void submitPlasmaTethers(PoseStack.Pose pose, VertexConsumer buffer, float time) {
        float pulse = 0.014F + 0.006F * Mth.sin(time * 8.0F);
        box(pose, buffer, 0.05F, 0.96F - pulse, 0.50F - pulse, 0.50F, 0.96F + pulse, 0.50F + pulse, STORM_CYAN);
        box(pose, buffer, 0.50F, 0.96F - pulse, 0.50F - pulse, 0.95F, 0.96F + pulse, 0.50F + pulse, STORM_CYAN);
        box(pose, buffer, 0.50F - pulse, 0.96F - pulse, 0.05F, 0.50F + pulse, 0.96F + pulse, 0.50F, STORM_CRIMSON);
        box(pose, buffer, 0.50F - pulse, 0.96F - pulse, 0.50F, 0.50F + pulse, 0.96F + pulse, 0.95F, STORM_CRIMSON);

        box(pose, buffer, 0.02F, 0.93F, 0.47F, 0.08F, 0.99F, 0.53F, WHITE_ARC);
        box(pose, buffer, 0.92F, 0.93F, 0.47F, 0.98F, 0.99F, 0.53F, WHITE_ARC);
        box(pose, buffer, 0.47F, 0.93F, 0.02F, 0.53F, 0.99F, 0.08F, WHITE_ARC);
        box(pose, buffer, 0.47F, 0.93F, 0.92F, 0.53F, 0.99F, 0.98F, WHITE_ARC);
    }

    private static void submitGyroRings(PoseStack poseStack, SubmitNodeCollector collector, float time) {
        float[] radii = { 0.36F, 0.43F, 0.50F };
        float[] tilts = { 26.0F, -38.0F, 52.0F };
        float[] speeds = { 68.0F, -92.0F, 54.0F };
        int[] colors = { STORM_CYAN, STORM_VIOLET, STORM_CRIMSON };

        for (int r = 0; r < 3; r++) {
            float radius = radii[r];
            int color = colors[r];
            poseStack.pushPose();
            poseStack.translate(0.5F, 0.96F, 0.5F);
            poseStack.mulPose(Axis.YP.rotationDegrees(time * speeds[r]));
            poseStack.mulPose(Axis.ZP.rotationDegrees(tilts[r]));
            poseStack.mulPose(Axis.XP.rotationDegrees(time * speeds[r] * 0.7F));
            collector.submitCustomGeometry(poseStack, GLOW_RENDER_TYPE, (pose, buffer) -> {
                int segments = 16;
                for (int i = 0; i < segments; i++) {
                    float a0 = i * Mth.TWO_PI / segments;
                    float a1 = (i + 1) * Mth.TWO_PI / segments;
                    float x0 = Mth.cos(a0) * radius;
                    float z0 = Mth.sin(a0) * radius;
                    float x1 = Mth.cos(a1) * radius;
                    float z1 = Mth.sin(a1) * radius;
                    quad(pose, buffer,
                            x0, -0.014F, z0,
                            x1, -0.014F, z1,
                            x1, 0.014F, z1,
                            x0, 0.014F, z0,
                            i % 2 == 0 ? color : WHITE_ARC);
                }
            });
            poseStack.popPose();
        }
    }

    private static void submitStormCrown(PoseStack poseStack, SubmitNodeCollector collector, float time) {
        float bob = Mth.sin(time * 3.6F) * 0.025F;
        poseStack.pushPose();
        poseStack.translate(0.5F, 1.42F + bob, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-time * 75.0F));
        collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> {
            box(pose, buffer, -0.04F, 0.0F, -0.04F, 0.04F, 0.16F, 0.04F, OBSIDIAN);
            for (int i = 0; i < 4; i++) {
                float angle = i * Mth.HALF_PI;
                float cx = Mth.cos(angle) * 0.14F;
                float cz = Mth.sin(angle) * 0.14F;
                box(pose, buffer, cx - 0.025F, -0.02F, cz - 0.025F, cx + 0.025F, 0.10F, cz + 0.025F, GOLD_TRIM);
            }
        });
        collector.submitCustomGeometry(poseStack, GLOW_RENDER_TYPE, (pose, buffer) -> {
            box(pose, buffer, -0.02F, 0.06F, -0.02F, 0.02F, 0.20F, 0.02F, WHITE_ARC);
            for (int i = 0; i < 4; i++) {
                float angle = i * Mth.HALF_PI + Mth.PI * 0.25F;
                float cx = Mth.cos(angle) * 0.16F;
                float cz = Mth.sin(angle) * 0.16F;
                box(pose, buffer, cx - 0.018F, 0.01F, cz - 0.018F, cx + 0.018F, 0.13F, cz + 0.018F,
                        i % 2 == 0 ? STORM_CYAN : STORM_CRIMSON);
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
            return new AngryStormGeneratorRenderer();
        }

        @Override
        public MapCodec<? extends NoDataSpecialModelRenderer.Unbaked> type() {
            return MAP_CODEC;
        }
    }

    private record AnimatedModel(ItemModel delegate) implements ItemModel {

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
                return new AnimatedModel(new SpecialModelWrapper.Unbaked(this.base, Optional.empty(),
                        this.model).bake(context, transformation));
            }

            @Override
            public MapCodec<Unbaked> type() {
                return MAP_CODEC;
            }
        }
    }
}
