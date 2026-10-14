package com.pppopipupu.fxeasy.client;

import module java.base;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.pppopipupu.fxeasy.FxEasy;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.Sheets;
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
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
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

/// Draws the `fxeasy:fx_phone` sprite as a tessellated quad in item model space through `core/fx_phone`. The quad
/// is a unit square, the size of a vanilla flat item, and every vertex is displaced and refracted here, on the
/// CPU: `VertexConsumer.addVertex` bakes the pose into the vertex position, so the `Position` a shader sees is in
/// world or GUI units and the quad's own 0..1 coordinate only exists on this side. That coordinate travels in the
/// overhead layer, which this item does not use; the shader adds the rim and the fog. The sprite is resolved on
/// every submit and never while the model bakes: model baking runs before the item atlas is stitched, and asking
/// the atlas for a sprite at that point throws `NullPointerException: Atlas not initialized`, which leaves the
/// item on the missing model.
public final class FxPhoneRenderer implements NoDataSpecialModelRenderer {

    private static final int CELLS = 8;
    /// The item model space at submit time is a unit cube, not the 0..16 of the model file: `ItemTransform.apply`
    /// shifts the baked model by -0.5 on every axis and the GUI item atlas then scales it by the slot size, so a
    /// quad from 0 to 1 lands exactly on one slot. A 0..16 quad is sixteen times too large: in the GUI only its
    /// first sixteenth falls inside the slot and in the hand it is a sixteen block plane behind the camera.
    private static final float PLANE_SIZE = 1.0F;
    /// The middle of the slab a vanilla flat item occupies, `7.5/16` to `8.5/16` in the same cube.
    private static final float PLANE_Z = 0.5F;
    private static final float PLANE_BACK_Z = 0.46875F;
    private static final float PLANE_FRONT_Z = 0.53125F;
    /// Displacement and refraction at the centre of the quad, both as a fraction of the item: the edge boost
    /// multiplies them towards the rim, and `WARP_MARGIN` is the largest offset a vertex can end up with.
    private static final float WARP_AMPLITUDE = 0.10F;
    private static final float WARP_REFRACTION = 0.06F;
    private static final float WARP_EDGE_BOOST = 1.5F;
    private static final float WARP_MARGIN = WARP_AMPLITUDE * (1.0F + WARP_EDGE_BOOST);
    private static final float WARP_FREQUENCY = 9.0F;
    private static final float WARP_TAU = 6.28318530718F;
    /// Cycles per second, the same rate `warp.glsl` drives the rim with: a slow breathing warp, not a per frame
    /// flicker.
    private static final float WARP_CYCLES = 0.8F;

    private static final List<Vector3fc> EXTENTS = List.of(
            new Vector3f(-WARP_MARGIN, -WARP_MARGIN, PLANE_BACK_Z),
            new Vector3f(PLANE_SIZE + WARP_MARGIN, -WARP_MARGIN, PLANE_BACK_Z),
            new Vector3f(PLANE_SIZE + WARP_MARGIN, PLANE_SIZE + WARP_MARGIN, PLANE_BACK_Z),
            new Vector3f(-WARP_MARGIN, PLANE_SIZE + WARP_MARGIN, PLANE_BACK_Z),
            new Vector3f(-WARP_MARGIN, -WARP_MARGIN, PLANE_FRONT_Z),
            new Vector3f(PLANE_SIZE + WARP_MARGIN, -WARP_MARGIN, PLANE_FRONT_Z),
            new Vector3f(PLANE_SIZE + WARP_MARGIN, PLANE_SIZE + WARP_MARGIN, PLANE_FRONT_Z),
            new Vector3f(-WARP_MARGIN, PLANE_SIZE + WARP_MARGIN, PLANE_FRONT_Z));

    public static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.ITEM_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(FxEasy.MODID, "pipeline/fx_phone"))
            .withVertexShader(Identifier.fromNamespaceAndPath(FxEasy.MODID, "core/fx_phone"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(FxEasy.MODID, "core/fx_phone"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withCull(false)
            .build();

    private static final Identifier ITEMS_ATLAS = Sheets.ITEMS_MAPPER.sheet();

    private static final RenderType RENDER_TYPE = RenderType.create(
            "fx_phone",
            RenderSetup.builder(PIPELINE).withTexture("Sampler0", ITEMS_ATLAS).useLightmap().createRenderSetup());

    private final SpriteGetter sprites;
    private final SpriteId sprite;

    private FxPhoneRenderer(SpriteGetter sprites, SpriteId sprite) {
        this.sprites = sprites;
        this.sprite = sprite;
    }

    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(PIPELINE);
    }

    public static void registerSpecialModelRenderers(RegisterSpecialModelRendererEvent event) {
        event.register(Identifier.fromNamespaceAndPath(FxEasy.MODID, "fx_phone"), Unbaked.MAP_CODEC);
    }

    public static void registerItemModels(RegisterItemModelsEvent event) {
        event.register(Identifier.fromNamespaceAndPath(FxEasy.MODID, "animated_special"), AnimatedSpecialModel.Unbaked.MAP_CODEC);
    }

    public static ItemModel.Unbaked itemModel(Identifier base, Identifier sprite) {
        return new AnimatedSpecialModel.Unbaked(base, new Unbaked(sprite));
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, int overlayCoords, boolean hasFoil, int outlineColor) {
        TextureAtlasSprite atlasSprite = this.sprites.get(this.sprite);
        collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> submitSprite(pose, buffer, atlasSprite, lightCoords));
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        EXTENTS.forEach(output);
    }

    private static void submitSprite(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, int lightCoords) {
        float phase = warpPhase(Util.getMillis());
        for (int row = 0; row < CELLS; row++) {
            for (int column = 0; column < CELLS; column++) {
                float x0 = PLANE_SIZE * column / CELLS;
                float x1 = PLANE_SIZE * (column + 1) / CELLS;
                float y0 = PLANE_SIZE * row / CELLS;
                float y1 = PLANE_SIZE * (row + 1) / CELLS;
                vertex(pose, buffer, sprite, x0, y0, lightCoords, phase);
                vertex(pose, buffer, sprite, x1, y0, lightCoords, phase);
                vertex(pose, buffer, sprite, x1, y1, lightCoords, phase);
                vertex(pose, buffer, sprite, x0, y1, lightCoords, phase);
            }
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, float x, float y, int lightCoords, float phase) {
        float localX = x / PLANE_SIZE;
        float localY = y / PLANE_SIZE;
        float offsetX = localX - 0.5F;
        float offsetY = localY - 0.5F;
        float reach = 1.0F + WARP_EDGE_BOOST * (offsetX * offsetX + offsetY * offsetY);
        float fieldX = Mth.sin(phase + localY * WARP_FREQUENCY + localX * 5.0F);
        float fieldY = Mth.sin(phase * 2.0F + localX * WARP_FREQUENCY - localY * 7.0F);
        float displaced = WARP_AMPLITUDE * PLANE_SIZE * reach;
        float refraction = WARP_REFRACTION * reach;
        buffer.addVertex(pose, x + fieldX * displaced, y + fieldY * displaced, PLANE_Z)
                .setColor(-1)
                .setUv(sprite.getU(Mth.clamp(localX + fieldX * refraction, 0.0F, 1.0F)), sprite.getV(1.0F - Mth.clamp(localY + fieldY * refraction, 0.0F, 1.0F)))
                .setUv1(quantise(localX), quantise(localY))
                .setLight(lightCoords)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
    }

    /// The overhead layer carries the quad's own coordinate at 8 bits per axis: the rim is the only consumer and
    /// it fades over a smoothstep, so 256 steps are plenty. This item never shows the hurt overlay that the
    /// attribute normally holds.
    private static int quantise(float value) {
        return Mth.floor(value * 255.0F + 0.5F);
    }

    private static float warpPhase(long millis) {
        return millis / 1000.0F * WARP_TAU * WARP_CYCLES;
    }

    public record Unbaked(Identifier sprite) implements NoDataSpecialModelRenderer.Unbaked {

        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(
                instance -> instance.group(Identifier.CODEC.fieldOf("sprite").forGetter(Unbaked::sprite)).apply(instance, Unbaked::new));

        @Override
        public NoDataSpecialModelRenderer bake(SpecialModelRenderer.BakingContext context) {
            return new FxPhoneRenderer(context.sprites(), new SpriteId(ITEMS_ATLAS, this.sprite));
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }

    /// Vanilla `minecraft:special` marks an item model animated only for foil stacks, and the GUI draws items
    /// through a texture atlas slot that is reused while the render state is not animated, so a special model
    /// whose texture or shader changes every frame has to declare itself animated here to be redrawn.
    private record AnimatedSpecialModel(ItemModel delegate) implements ItemModel {

        @Override
        public void update(
                ItemStackRenderState output,
                ItemStack item,
                ItemModelResolver resolver,
                ItemDisplayContext displayContext,
                @Nullable ClientLevel level,
                @Nullable ItemOwner owner,
                int seed
        ) {
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
                return new AnimatedSpecialModel(new SpecialModelWrapper.Unbaked(this.base, Optional.empty(), this.model).bake(context, transformation));
            }

            @Override
            public MapCodec<Unbaked> type() {
                return MAP_CODEC;
            }
        }
    }
}
