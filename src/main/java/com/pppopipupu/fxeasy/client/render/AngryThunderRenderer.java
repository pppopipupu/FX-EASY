package com.pppopipupu.fxeasy.client.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.pppopipupu.fxeasy.FxEasy;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

public final class AngryThunderRenderer {

    public static final RenderPipeline PIPELINE = RenderPipeline
            .builder(RenderPipelines.MATRICES_FOG_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(FxEasy.MODID, "pipeline/angry_thunder"))
            .withVertexShader(Identifier.fromNamespaceAndPath(FxEasy.MODID, "core/angry_thunder"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(FxEasy.MODID, "core/angry_thunder"))
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withCull(false)
            .build();

    public static final RenderType RENDER_TYPE = RenderType.create("angry_thunder",
            RenderSetup.builder(PIPELINE).createRenderSetup());

    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(PIPELINE);
    }

    private AngryThunderRenderer() {
    }
}
