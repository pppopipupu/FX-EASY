package com.pppopipupu.fxeasy.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pppopipupu.fxeasy.content.block.MultiPartBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.BlockBreakingRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "submitBlockDestroyAnimation", at = @At("HEAD"), cancellable = true)
    private void fxeasy$submitMultiPartDestroyAnimation(PoseStack poseStack, SubmitNodeCollector collector,
            LevelRenderState levelRenderState, CallbackInfo callback) {
        boolean hasMultiPart = false;
        for (BlockBreakingRenderState state : levelRenderState.blockBreakingRenderStates) {
            if (state.blockState().getBlock() instanceof MultiPartBlock) {
                hasMultiPart = true;
                break;
            }
        }
        if (!hasMultiPart) {
            return;
        }
        callback.cancel();

        Vec3 cameraPos = levelRenderState.cameraRenderState.pos;
        for (BlockBreakingRenderState state : levelRenderState.blockBreakingRenderStates) {
            BlockState blockState = state.blockState();
            BlockPos pos = state.blockPos();
            poseStack.pushPose();
            if (blockState.getBlock() instanceof MultiPartBlock multiPartBlock) {
                if (blockState.getValue(MultiPartBlock.IS_CORE) || this.minecraft.level == null) {
                    poseStack.popPose();
                    continue;
                }
                BlockPos corePos = multiPartBlock.getCorePos(blockState, pos);
                BlockState coreState = this.minecraft.level.getBlockState(corePos);
                if (!(coreState.getBlock() instanceof MultiPartBlock)
                        || !coreState.getValue(MultiPartBlock.IS_CORE)) {
                    poseStack.popPose();
                    continue;
                }
                poseStack.translate(corePos.getX() - cameraPos.x(), corePos.getY() - cameraPos.y(),
                        corePos.getZ() - cameraPos.z());
                collector.submitBreakingBlockModel(poseStack,
                        this.minecraft.getModelManager().getBlockStateModelSet().get(coreState),
                        coreState.getSeed(corePos), state.progress());
            } else if (blockState.getRenderShape() == RenderShape.MODEL) {
                poseStack.translate(pos.getX() - cameraPos.x(), pos.getY() - cameraPos.y(),
                        pos.getZ() - cameraPos.z());
                collector.submitBreakingBlockModel(poseStack,
                        this.minecraft.getModelManager().getBlockStateModelSet().get(blockState),
                        blockState.getSeed(pos), state.progress());
            }
            poseStack.popPose();
        }
    }
}
