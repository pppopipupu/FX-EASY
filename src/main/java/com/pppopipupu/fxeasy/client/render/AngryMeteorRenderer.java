package com.pppopipupu.fxeasy.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.pppopipupu.fxeasy.content.FxBlocks;
import com.pppopipupu.fxeasy.content.entity.AngryMeteor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/// Draws the meteor as the Angry block model at four times its block size: the block model is two blocks on every
/// axis, so a scale of two gives a four block wide meteor that spins while it falls.
public class AngryMeteorRenderer extends EntityRenderer<AngryMeteor, AngryMeteorRenderer.State> {

    private static final float MODEL_SCALE = 2.0F;

    private final BlockModelResolver modelResolver;

    public AngryMeteorRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.modelResolver = context.getBlockModelResolver();
        this.shadowRadius = 2.0F;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(AngryMeteor entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.rotationY = Mth.lerp(partialTicks, entity.spinYO, entity.spinY);
        state.rotationX = Mth.lerp(partialTicks, entity.spinXO, entity.spinX);
        this.modelResolver.update(state.model, FxBlocks.ANGRY_BLOCK.get().defaultBlockState(),
                BlockDisplayContext.create());
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.rotationY));
        poseStack.mulPose(Axis.XP.rotationDegrees(state.rotationX));
        poseStack.translate(-1.0F, -1.0F, -1.0F);
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
