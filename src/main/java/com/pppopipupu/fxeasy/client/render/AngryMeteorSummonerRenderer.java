package com.pppopipupu.fxeasy.client.render;

import module java.base;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.MapCodec;
import com.pppopipupu.fxeasy.FxEasy;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.SpecialModelWrapper;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/// A long staff: a dark grip and shaft run from below the hand to a claw socket at the top, where the OBJ Angry
/// block sits as the tip. A crown rises above the block and shards float beside it, leaving the face open.
public final class AngryMeteorSummonerRenderer implements NoDataSpecialModelRenderer {

    private static final int DARK = 0xFF09090B;
    private static final int STONE = 0xFF29292E;
    private static final int STONE_LIGHT = 0xFF4A4A52;
    private static final int RED = 0xFFC1121F;
    private static final int EMBER = 0xFFFF5A1F;
    private static final int ORANGE = 0xFFFF9F1C;
    private static final int WHITE = 0xFFFFF1D0;

    private static final RenderType RENDER_TYPE = RenderType.create("angry_meteor_summoner",
            RenderSetup.builder(AngryWandRenderer.PIPELINE).createRenderSetup());
    private static final RenderType GLOW_RENDER_TYPE = RenderType.create("angry_meteor_summoner_glow",
            RenderSetup.builder(AngryWandRenderer.GLOW_PIPELINE).createRenderSetup());

    private static final List<Vector3fc> EXTENTS = List.of(
            new Vector3f(-0.12F, -0.68F, -0.12F),
            new Vector3f(1.12F, 1.55F, 1.12F));

    public static void registerSpecialModelRenderers(RegisterSpecialModelRendererEvent event) {
        event.register(Identifier.fromNamespaceAndPath(FxEasy.MODID, "angry_meteor_summoner"),
                Unbaked.MAP_CODEC);
    }

    public static ItemModel.Unbaked itemModel(Identifier base) {
        return new SpecialModelWrapper.Unbaked(base, Optional.empty(), new Unbaked());
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, int overlayCoords,
            boolean hasFoil, int outlineColor) {
        poseStack.pushPose();
        collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> {
            submitGrip(pose, buffer);
            submitShaft(pose, buffer);
            submitSocket(pose, buffer);
            submitCrown(pose, buffer);
            submitShards(pose, buffer);
        });
        collector.submitCustomGeometry(poseStack, GLOW_RENDER_TYPE, (pose, buffer) -> {
            submitRunes(pose, buffer);
            submitSocketGlow(pose, buffer);
            submitCrownGlow(pose, buffer);
        });
        poseStack.popPose();
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        EXTENTS.forEach(output);
    }

    private static void submitGrip(PoseStack.Pose pose, VertexConsumer buffer) {
        box(pose, buffer, 0.43F, -0.68F, 0.43F, 0.57F, -0.58F, 0.57F, STONE_LIGHT);
        box(pose, buffer, 0.455F, -0.58F, 0.455F, 0.545F, -0.18F, 0.545F, DARK);
        box(pose, buffer, 0.445F, -0.50F, 0.445F, 0.555F, -0.47F, 0.555F, STONE_LIGHT);
        box(pose, buffer, 0.445F, -0.36F, 0.445F, 0.555F, -0.33F, 0.555F, EMBER);
        box(pose, buffer, 0.445F, -0.22F, 0.445F, 0.555F, -0.19F, 0.555F, STONE_LIGHT);
        box(pose, buffer, 0.34F, -0.18F, 0.46F, 0.66F, -0.12F, 0.54F, STONE);
        box(pose, buffer, 0.46F, -0.18F, 0.34F, 0.54F, -0.12F, 0.66F, STONE);
        box(pose, buffer, 0.40F, -0.175F, 0.47F, 0.60F, -0.145F, 0.53F, DARK);
    }

    private static void submitShaft(PoseStack.Pose pose, VertexConsumer buffer) {
        box(pose, buffer, 0.46F, -0.12F, 0.46F, 0.54F, 0.66F, 0.54F, DARK);
        box(pose, buffer, 0.44F, -0.08F, 0.48F, 0.46F, 0.64F, 0.52F, STONE);
        box(pose, buffer, 0.54F, -0.08F, 0.48F, 0.56F, 0.64F, 0.52F, STONE);
        box(pose, buffer, 0.48F, -0.08F, 0.44F, 0.52F, 0.64F, 0.46F, STONE);
        box(pose, buffer, 0.48F, -0.08F, 0.54F, 0.52F, 0.64F, 0.56F, STONE);
        box(pose, buffer, 0.44F, 0.02F, 0.44F, 0.56F, 0.06F, 0.56F, STONE_LIGHT);
        box(pose, buffer, 0.44F, 0.20F, 0.44F, 0.56F, 0.24F, 0.56F, EMBER);
        box(pose, buffer, 0.44F, 0.38F, 0.44F, 0.56F, 0.42F, 0.56F, STONE_LIGHT);
        box(pose, buffer, 0.44F, 0.54F, 0.44F, 0.56F, 0.58F, 0.56F, EMBER);
    }

    private static void submitSocket(PoseStack.Pose pose, VertexConsumer buffer) {
        box(pose, buffer, 0.44F, 0.60F, 0.44F, 0.56F, 0.68F, 0.56F, DARK);
        box(pose, buffer, 0.36F, 0.66F, 0.36F, 0.64F, 0.74F, 0.64F, STONE);
        box(pose, buffer, 0.30F, 0.70F, 0.36F, 0.38F, 0.76F, 0.64F, STONE);
        box(pose, buffer, 0.62F, 0.70F, 0.36F, 0.70F, 0.76F, 0.64F, STONE);
        box(pose, buffer, 0.36F, 0.70F, 0.30F, 0.64F, 0.76F, 0.38F, STONE);
        box(pose, buffer, 0.10F, 0.72F, 0.40F, 0.18F, 0.98F, 0.60F, DARK);
        box(pose, buffer, 0.82F, 0.72F, 0.40F, 0.90F, 0.98F, 0.60F, DARK);
        box(pose, buffer, 0.30F, 0.72F, 0.10F, 0.36F, 0.88F, 0.18F, DARK);
        box(pose, buffer, 0.64F, 0.72F, 0.10F, 0.70F, 0.88F, 0.18F, DARK);
        box(pose, buffer, 0.22F, 0.72F, 0.82F, 0.30F, 0.84F, 0.90F, DARK);
        box(pose, buffer, 0.70F, 0.72F, 0.82F, 0.78F, 0.84F, 0.90F, DARK);
    }

    private static void submitCrown(PoseStack.Pose pose, VertexConsumer buffer) {
        box(pose, buffer, 0.47F, 1.36F, 0.47F, 0.53F, 1.48F, 0.53F, DARK);
        box(pose, buffer, 0.30F, 1.30F, 0.46F, 0.36F, 1.42F, 0.54F, DARK);
        box(pose, buffer, 0.64F, 1.30F, 0.46F, 0.70F, 1.42F, 0.54F, DARK);
    }

    private static void submitShards(PoseStack.Pose pose, VertexConsumer buffer) {
        box(pose, buffer, 0.04F, 0.88F, 0.45F, 0.10F, 1.04F, 0.51F, DARK);
        box(pose, buffer, 0.06F, 1.02F, 0.47F, 0.08F, 1.08F, 0.49F, RED);
        box(pose, buffer, 0.90F, 0.88F, 0.45F, 0.96F, 1.04F, 0.51F, DARK);
        box(pose, buffer, 0.92F, 1.02F, 0.47F, 0.94F, 1.08F, 0.49F, RED);
        box(pose, buffer, 0.45F, 0.90F, 0.04F, 0.51F, 1.06F, 0.10F, DARK);
        box(pose, buffer, 0.47F, 1.04F, 0.06F, 0.49F, 1.10F, 0.08F, RED);
    }

    private static void submitRunes(PoseStack.Pose pose, VertexConsumer buffer) {
        for (int band = 0; band < 4; band++) {
            float y = 0.08F + band * 0.16F;
            int color = band % 2 == 0 ? EMBER : ORANGE;
            box(pose, buffer, 0.445F, y, 0.48F, 0.455F, y + 0.08F, 0.52F, color);
            box(pose, buffer, 0.545F, y, 0.48F, 0.555F, y + 0.08F, 0.52F, color);
            box(pose, buffer, 0.48F, y, 0.445F, 0.52F, y + 0.08F, 0.455F, color);
            box(pose, buffer, 0.48F, y, 0.545F, 0.52F, y + 0.08F, 0.555F, color);
        }
    }

    private static void submitSocketGlow(PoseStack.Pose pose, VertexConsumer buffer) {
        box(pose, buffer, 0.30F, 0.735F, 0.36F, 0.38F, 0.75F, 0.64F, ORANGE);
        box(pose, buffer, 0.62F, 0.735F, 0.36F, 0.70F, 0.75F, 0.64F, ORANGE);
        box(pose, buffer, 0.36F, 0.735F, 0.30F, 0.64F, 0.75F, 0.38F, EMBER);
        box(pose, buffer, 0.10F, 0.94F, 0.42F, 0.18F, 1.00F, 0.58F, ORANGE);
        box(pose, buffer, 0.82F, 0.94F, 0.42F, 0.90F, 1.00F, 0.58F, ORANGE);
        box(pose, buffer, 0.32F, 0.84F, 0.11F, 0.36F, 0.90F, 0.15F, EMBER);
        box(pose, buffer, 0.64F, 0.84F, 0.11F, 0.68F, 0.90F, 0.15F, EMBER);
        box(pose, buffer, 0.23F, 0.80F, 0.83F, 0.29F, 0.84F, 0.87F, ORANGE);
        box(pose, buffer, 0.71F, 0.80F, 0.83F, 0.77F, 0.84F, 0.87F, ORANGE);
        box(pose, buffer, 0.47F, 0.68F, 0.47F, 0.53F, 0.74F, 0.53F, EMBER);
        box(pose, buffer, 0.49F, 0.70F, 0.49F, 0.51F, 0.73F, 0.51F, WHITE);
    }

    private static void submitCrownGlow(PoseStack.Pose pose, VertexConsumer buffer) {
        box(pose, buffer, 0.49F, 1.44F, 0.49F, 0.51F, 1.52F, 0.51F, EMBER);
        box(pose, buffer, 0.495F, 1.48F, 0.495F, 0.505F, 1.51F, 0.505F, WHITE);
        box(pose, buffer, 0.32F, 1.38F, 0.48F, 0.36F, 1.44F, 0.52F, ORANGE);
        box(pose, buffer, 0.64F, 1.38F, 0.48F, 0.68F, 1.44F, 0.52F, ORANGE);
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
            return new AngryMeteorSummonerRenderer();
        }

        @Override
        public MapCodec<? extends NoDataSpecialModelRenderer.Unbaked> type() {
            return MAP_CODEC;
        }
    }
}
