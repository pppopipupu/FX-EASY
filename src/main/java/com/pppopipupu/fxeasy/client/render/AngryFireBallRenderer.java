package com.pppopipupu.fxeasy.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.pppopipupu.fxeasy.content.FxBlocks;
import com.pppopipupu.fxeasy.content.entity.AngryFireBall;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

public class AngryFireBallRenderer
        extends EntityRenderer<AngryFireBall, AngryFireBallRenderer.State> {

    private final BlockModelResolver modelResolver;

    public AngryFireBallRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.modelResolver = context.getBlockModelResolver();
        this.shadowRadius = 0.2F;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(AngryFireBall entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.rotationY = entity.getRotationY(partialTicks);
        state.rotationX = entity.getRotationX(partialTicks);
        this.modelResolver.update(state.model, FxBlocks.ANGRY_BLOCK.get().defaultBlockState(),
                BlockDisplayContext.create());
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0.0, 0.25, 0.0);
        poseStack.scale(0.3F, 0.3F, 0.3F);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.rotationY));
        poseStack.mulPose(Axis.XP.rotationDegrees(state.rotationX));
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        state.model.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    public static class State extends EntityRenderState {
        public final BlockModelRenderState model = new BlockModelRenderState();
        public float rotationY;
        public float rotationX;
    }
}
