package com.pppopipupu.fxeasy.client.render;

import module java.base;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.pppopipupu.fxeasy.content.block.AngryBlock;
import com.pppopipupu.fxeasy.content.block.MultiPartBlock;
import com.pppopipupu.fxeasy.content.block.entity.AngryBlockEntity;
import com.pppopipupu.fxeasy.content.block.entity.AngryLightingBolt;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public class AngryBlockRenderer implements BlockEntityRenderer<AngryBlockEntity, AngryBlockRenderer.State> {

    private final BlockModelResolver modelResolver;

    public AngryBlockRenderer(BlockEntityRendererProvider.Context context) {
        this.modelResolver = context.blockModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(AngryBlockEntity blockEntity, State state, float partialTicks,
            Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(blockEntity, state, breakProgress);
        BlockState blockState = blockEntity.getBlockState();
        state.facing = blockState.getValue(MultiPartBlock.FACING);
        state.lightning = blockState.getValue(AngryBlock.IS_LIGHTNING);
        state.unstable = blockState.getValue(AngryBlock.IS_UNSTABLE);
        state.unstableTicks = blockEntity.getUnstableTicks();
        state.bolts = List.copyOf(blockEntity.getActiveBolts());
        this.modelResolver.update(state.model, blockState, BlockDisplayContext.create());
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        switch (state.facing) {
            case WEST -> {
                poseStack.translate(2.0, 0.0, 1.0);
                poseStack.mulPose(Axis.YP.rotationDegrees(180));
            }
            case NORTH -> {
                poseStack.translate(0.0, 0.0, 2.0);
                poseStack.mulPose(Axis.YP.rotationDegrees(90));
            }
            case SOUTH -> {
                poseStack.translate(1.0, 0.0, -1.0);
                poseStack.mulPose(Axis.YP.rotationDegrees(-90));
            }
            case EAST -> poseStack.translate(-1.0, 0.0, 0.0);
            default -> {
            }
        }
        if (state.unstable) {
            float pulse = 1.0F + Mth.sin(state.unstableTicks * 0.45F) * 0.10F;
            float jitterX = Mth.sin(state.unstableTicks * 1.7F) * 0.03F;
            float jitterZ = Mth.cos(state.unstableTicks * 1.9F) * 0.03F;
            poseStack.pushPose();
            poseStack.translate(1.0F + jitterX, 1.0F, 1.0F + jitterZ);
            poseStack.scale(pulse, pulse, pulse);
            poseStack.translate(-1.0F, -1.0F, -1.0F);
            state.model.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        } else {
            state.model.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        }
        if (state.lightning || state.unstable) {
            poseStack.pushPose();
            poseStack.translate(1.0, 1.0, 1.0);
            if (!state.bolts.isEmpty()) {
                collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, buffer) -> {
                    for (AngryLightingBolt bolt : state.bolts) {
                        recursivelyRenderBolt(pose, buffer, bolt);
                    }
                });
            }
            if (state.unstable) {
                submitUnstableContainmentRings(poseStack, collector, state.unstableTicks);
            }
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private static void recursivelyRenderBolt(PoseStack.Pose pose, VertexConsumer builder, AngryLightingBolt bolt) {
        if (bolt.life <= 0) {
            return;
        }
        Matrix4fc matrix = pose.pose();
        Vector3f lastSegment = null;
        for (Vector3f segment : bolt.segments) {
            if (lastSegment != null) {
                renderVolumetricSegment(matrix, builder, lastSegment, segment, bolt.thickness,
                        bolt.red, bolt.green, bolt.blue, 1.0F);
            }
            lastSegment = segment;
        }
        for (AngryLightingBolt child : bolt.children) {
            recursivelyRenderBolt(pose, builder, child);
        }
    }

    private static void renderVolumetricSegment(Matrix4fc matrix, VertexConsumer builder, Vector3f start,
            Vector3f end, float thickness, float red, float green, float blue, float alpha) {
        Vector3f direction = new Vector3f(end).sub(start);
        if (direction.lengthSquared() == 0) {
            return;
        }
        direction.normalize();

        Vector3f up = new Vector3f(0, 1, 0);
        if (Math.abs(direction.dot(up)) > 0.999F) {
            up = new Vector3f(1, 0, 0);
        }
        Vector3f side1 = new Vector3f(direction).cross(up).normalize().mul(thickness / 2.0F);
        Vector3f side2 = new Vector3f(direction).cross(side1).normalize().mul(thickness / 2.0F);

        addQuad(matrix, builder, new Vector3f(start).sub(side1), new Vector3f(start).add(side1),
                new Vector3f(end).add(side1), new Vector3f(end).sub(side1), red, green, blue, alpha);
        addQuad(matrix, builder, new Vector3f(start).sub(side2), new Vector3f(start).add(side2),
                new Vector3f(end).add(side2), new Vector3f(end).sub(side2), red, green, blue, alpha * 0.75F);
    }

    private static void addQuad(Matrix4fc matrix, VertexConsumer builder, Vector3f first, Vector3f second,
            Vector3f third, Vector3f fourth, float red, float green, float blue, float alpha) {
        builder.addVertex(matrix, first.x(), first.y(), first.z()).setColor(red, green, blue, alpha);
        builder.addVertex(matrix, second.x(), second.y(), second.z()).setColor(red, green, blue, alpha);
        builder.addVertex(matrix, third.x(), third.y(), third.z()).setColor(red, green, blue, alpha);
        builder.addVertex(matrix, fourth.x(), fourth.y(), fourth.z()).setColor(red, green, blue, alpha);
    }

    private static void submitUnstableContainmentRings(PoseStack poseStack, SubmitNodeCollector collector, int ticks) {
        int crimson = 0xEEFF1E56;
        int violet = 0xEEAA42FF;
        for (int ring = 0; ring < 2; ring++) {
            float radius = 1.45F + ring * 0.22F;
            float speed = ring == 0 ? 7.5F : -9.0F;
            float tilt = ring == 0 ? 28.0F : -34.0F;
            int color = ring == 0 ? crimson : violet;
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(ticks * speed));
            poseStack.mulPose(Axis.ZP.rotationDegrees(tilt));
            collector.submitCustomGeometry(poseStack, AngryThunderRenderer.RENDER_TYPE, (pose, buffer) -> {
                int sides = 12;
                for (int i = 0; i < sides; i++) {
                    float a0 = i * Mth.TWO_PI / sides;
                    float a1 = (i + 1) * Mth.TWO_PI / sides;
                    float x0 = Mth.cos(a0) * radius;
                    float z0 = Mth.sin(a0) * radius;
                    float x1 = Mth.cos(a1) * radius;
                    float z1 = Mth.sin(a1) * radius;
                    buffer.addVertex(pose, x0, -0.08F, z0).setColor(color);
                    buffer.addVertex(pose, x1, -0.08F, z1).setColor(color);
                    buffer.addVertex(pose, x1, 0.08F, z1).setColor(color);
                    buffer.addVertex(pose, x0, 0.08F, z0).setColor(color);
                }
            });
            poseStack.popPose();
        }
    }

    public static class State extends BlockEntityRenderState {
        public final BlockModelRenderState model = new BlockModelRenderState();
        public Direction facing = Direction.NORTH;
        public boolean lightning;
        public boolean unstable;
        public int unstableTicks;
        public List<AngryLightingBolt> bolts = List.of();
    }
}
