package com.pppopipupu.fxeasy.client.render;

import module java.base;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.pppopipupu.fxeasy.content.block.MultiPartBlock;
import com.pppopipupu.fxeasy.content.block.entity.AngryFemaleEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class AngryFemaleBlockRenderer
        implements BlockEntityRenderer<AngryFemaleEntity, AngryFemaleBlockRenderer.State> {

    private final BlockModelResolver modelResolver;

    public AngryFemaleBlockRenderer(BlockEntityRendererProvider.Context context) {
        this.modelResolver = context.blockModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(AngryFemaleEntity blockEntity, State state, float partialTicks,
            Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(blockEntity, state, breakProgress);
        BlockState blockState = blockEntity.getBlockState();
        state.facing = blockState.getValue(MultiPartBlock.FACING);
        state.angle = Mth.lerp(partialTicks, blockEntity.prevRotationAngle, blockEntity.rotationAngle);
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
        if (state.angle != 0.0F) {
            poseStack.translate(1.0, 1.0, 1.0);
            poseStack.mulPose(Axis.YP.rotationDegrees(state.angle));
            poseStack.translate(-1.0, -1.0, -1.0);
        }
        state.model.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    public static class State extends BlockEntityRenderState {
        public final BlockModelRenderState model = new BlockModelRenderState();
        public Direction facing = Direction.NORTH;
        public float angle;
    }
}
