package com.pppopipupu.fxeasy.client.panel;

import module java.base;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.pppopipupu.fxeasy.FxEasy;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

/// The picture of a shop card and the effect that lies over it. The illustration is drawn the way every other
/// texture of the panel is, at the opacity the card shows it at, and `core/shop_card` is then laid over the whole
/// card as a layer of light: the sheen of the tier the card sells, added on top of what is already on screen
/// rather than blended into the picture, so the illustration keeps the colours it was drawn with and only gains
/// the light. The two are therefore separate decisions - how transparent a card reads is the panel's, and what
/// the effect adds is this file's.
///
/// A card says everything the effect needs in the channels of the vertex colour, because the GUI blit path hands
/// a draw nothing else: the tier in red, the slice of the file the card shows in green, which is where the effect
/// samples the picture it masks itself with, and how strongly it is drawn in blue. The clock is the global
/// settings buffer, which the GUI pass binds for every draw, so no uniform has to be uploaded per card and a page
/// of three cards costs six draws.
public final class FxShopCard {

    /// Built from the vanilla GUI snippet, which carries the vertex format, the sampler and the matrix
    /// projection, and from the global settings snippet, which is where `GameTime` lives. The blend is the one
    /// thing it takes from neither: an additive pass adds its light to what stands under it, which is what makes
    /// the effect a layer over the card instead of a second coat of paint on it.
    public static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET,
            RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(FxEasy.MODID, "pipeline/shop_card"))
            .withVertexShader(Identifier.fromNamespaceAndPath(FxEasy.MODID, "core/shop_card"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(FxEasy.MODID, "core/shop_card"))
            .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
            .build();

    private FxShopCard() {
    }

    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(PIPELINE);
    }

    /// [intensity] is how strongly the effect is drawn - a card the account is on carries a stronger sheen than
    /// one it merely could buy.
    public static void draw(GuiGraphicsExtractor graphics, Identifier texture, ScreenRectangle area, int artWidth,
            int artHeight, int tier, float intensity, float opacity) {
        if (area.width() <= 0 || area.height() <= 0 || artWidth <= 0 || artHeight <= 0) {
            return;
        }
        FxUi.Crop crop = FxUi.cover(area.width(), area.height(), artWidth, artHeight, 0.0D);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, area.left(), area.top(),
                Math.round(crop.u0() * artWidth), Math.round(crop.v0() * artHeight), area.width(), area.height(),
                Math.max(1, Math.round((crop.u1() - crop.u0()) * artWidth)),
                Math.max(1, Math.round((crop.v1() - crop.v0()) * artHeight)), artWidth, artHeight,
                ARGB.color(byteOf(opacity), 255, 255, 255));
        FxUi.audit("portrait", area.left(), area.top(), area.right(), area.bottom());
        FxUi.elements++;
        graphics.blit(PIPELINE, texture, area.left(), area.top(), 0.0F, 0.0F, area.width(), area.height(),
                artWidth, artHeight, artWidth, artHeight, tint(tier, crop.v1() - crop.v0(), intensity));
        FxUi.elements++;
    }

    /// The vertex colour the effect is drawn with, one channel per figure: red the tier, green the slice of the
    /// file the card shows, blue the strength of the effect. Each channel is eight bits wide in the vertex format,
    /// so every figure is rounded into its own byte and clamped rather than left to wrap.
    static int tint(int tier, float slice, float intensity) {
        return ARGB.color(255, Math.clamp(tier, 0, 255), byteOf(slice), byteOf(intensity));
    }

    private static int byteOf(float value) {
        return Math.clamp(Math.round(value * 255.0F), 0, 255);
    }
}
