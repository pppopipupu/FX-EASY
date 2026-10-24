package com.pppopipupu.fxeasy.datagen;

import module java.base;

import com.mojang.math.Transformation;
import com.pppopipupu.fxeasy.FxEasy;
import com.pppopipupu.fxeasy.client.FxPhoneRenderer;
import com.pppopipupu.fxeasy.client.render.AngryMeteorSummonerRenderer;
import com.pppopipupu.fxeasy.client.render.AngryStormGeneratorRenderer;
import com.pppopipupu.fxeasy.client.render.AngryWandRenderer;
import com.pppopipupu.fxeasy.content.FxBlocks;
import com.pppopipupu.fxeasy.content.FxItems;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.renderer.item.CompositeModel;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.model.generators.loaders.ObjModelBuilder;
import net.neoforged.neoforge.client.model.generators.template.ExtendedModelTemplate;
import net.neoforged.neoforge.client.model.generators.template.ExtendedModelTemplateBuilder;
import org.joml.Vector3f;

public final class FxModels extends ModelProvider {

    private static final TextureSlot TEXTURE0 = TextureSlot.create("texture0");
    private static final TextureSlot TEXTURE1 = TextureSlot.create("texture1");
    private static final TextureSlot TEXTURE2 = TextureSlot.create("texture2");
    private static final TextureSlot TEXTURE3 = TextureSlot.create("texture3");

    public FxModels(PackOutput output) {
        super(output, FxEasy.MODID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        Identifier phone = itemModels.createFlatItemModel(FxItems.FX_PHONE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.itemModelOutput.accept(FxItems.FX_PHONE.get(),
                FxPhoneRenderer.itemModel(phone, TextureMapping.getItemTexture(FxItems.FX_PHONE.get()).sprite()));

        Map<TextureSlot, Material> angryBlockTextures = Map.of(
                TextureSlot.PARTICLE, texture("block/face_angry"),
                TEXTURE0, texture("block/angry_model"));
        Map<TextureSlot, Material> femaleBlockTextures = Map.of(
                TextureSlot.PARTICLE, texture("block/face_angry"),
                TEXTURE0, texture("block/angry_female"),
                TEXTURE1, texture("block/bow_tie"));
        Map<TextureSlot, Material> surpriseBlockTextures = Map.of(
                TextureSlot.PARTICLE, texture("block/face_hagry"),
                TEXTURE0, texture("block/surprise_model"));

        objBlock(blockModels, FxBlocks.ANGRY_BLOCK.get(), "angry_block", "angry", angryBlockTextures);
        objBlock(blockModels, FxBlocks.ANGRY_FEMALE_BLOCK.get(), "angry_female_block", "angry_female",
                femaleBlockTextures);
        objBlock(blockModels, FxBlocks.SURPRISE_BLOCK.get(), "surprise_block", "surprise", surpriseBlockTextures);

        objItem(itemModels, FxItems.ANGRY_BLOCK_ITEM.get(), "angry_block", "angry", angryBlockTextures,
                blockTransforms(0.3125F));
        objItem(itemModels, FxItems.ANGRY_LIGHTNING_BLOCK_ITEM.get(), "angry_lightning_block", "angry",
                angryBlockTextures, blockTransforms(0.5125F));
        objItem(itemModels, FxItems.ANGRY_ATOMIC_BLOCK_ITEM.get(), "angry_atomic_block", "angry",
                angryBlockTextures, atomicTransforms());
        objItem(itemModels, FxItems.ANGRY_FEMALE_ITEM.get(), "angry_female_block", "angry_female",
                femaleBlockTextures, blockTransforms(0.3125F));

        objItem(itemModels, FxItems.ANGRY_SWORD.get(), "angry_sword", "angry_sword",
                Map.of(TEXTURE0, texture("block/angry_model"), TextureSlot.PARTICLE, texture("block/face_angry")),
                swordTransforms());
        objItem(itemModels, FxItems.ANGRY_SPEAR.get(), "angry_spear", "angry_spear", Map.of(
                TEXTURE0, texture("block/angry_model"),
                TEXTURE1, texture("block/bow_tie"),
                TEXTURE2, texture("block/surprise_model"),
                TextureSlot.PARTICLE, texture("block/face_angry")), spearTransforms());
        objItem(itemModels, FxItems.ANGRY_GUN.get(), "angry_gun", "angry_gun", Map.of(
                TEXTURE0, texture("block/surprise_model"),
                TEXTURE1, texture("block/angry_model"),
                TEXTURE2, texture("block/face_angry_gun"),
                TEXTURE3, texture("block/bow_tie"),
                TextureSlot.PARTICLE, texture("block/face_angry_gun")), gunTransforms());

        Identifier wand = handheldBaseModel(itemModels, FxItems.ANGRY_WAND.get(), texture("block/face_angry"));
        itemModels.itemModelOutput.accept(FxItems.ANGRY_WAND.get(), AngryWandRenderer.itemModel(wand));
        Map<ItemDisplayContext, Transform> transforms = summonerTransforms();
        Identifier summoner = handheldBaseModel(itemModels, FxItems.ANGRY_METEOR_SUMMONER.get(),
                texture("block/face_angry"), transforms);
        Identifier summonerBlock = objModel(itemModels, "angry_meteor_summoner_block", "angry", angryBlockTextures,
                transforms);
        Transformation blockFit = new Transformation(new Vector3f(0.18F, 0.72F, 0.18F), null,
                new Vector3f(0.32F, 0.32F, 0.32F), null);
        ItemModel.Unbaked blockLayer = new CuboidItemModelWrapper.Unbaked(summonerBlock, Optional.of(blockFit),
                List.of());
        itemModels.itemModelOutput.accept(FxItems.ANGRY_METEOR_SUMMONER.get(),
                new CompositeModel.Unbaked(List.of(blockLayer, AngryMeteorSummonerRenderer.itemModel(summoner)),
                        Optional.empty()));

        Identifier stormBase = handheldBaseModel(itemModels, FxItems.ANGRY_STORM_GENERATOR.get(),
                texture("block/face_angry"), transforms);
        itemModels.itemModelOutput.accept(FxItems.ANGRY_STORM_GENERATOR.get(),
                AngryStormGeneratorRenderer.itemModel(stormBase));
    }

    private static Identifier handheldBaseModel(ItemModelGenerators itemModels, Item item, Material texture) {
        return handheldBaseModel(itemModels, item, texture, handheldTransforms());
    }

    private static Identifier handheldBaseModel(ItemModelGenerators itemModels, Item item, Material texture,
            Map<ItemDisplayContext, Transform> transforms) {
        ExtendedModelTemplateBuilder builder = ExtendedModelTemplateBuilder.builder()
                .parent(Identifier.withDefaultNamespace("item/generated"))
                .requiredTextureSlot(TextureSlot.LAYER0);
        transforms.forEach((context, transform) -> builder.transform(context, vector -> vector
                .rotation(transform.rotation()[0], transform.rotation()[1], transform.rotation()[2])
                .translation(transform.translation()[0], transform.translation()[1], transform.translation()[2])
                .scale(transform.scale()[0], transform.scale()[1], transform.scale()[2])));
        return builder.build().create(ModelLocationUtils.getModelLocation(item),
                TextureMapping.layer0(texture), itemModels.modelOutput);
    }

    private static Map<ItemDisplayContext, Transform> handheldTransforms() {
        Transform third = new Transform(new float[]{0, 0, 0}, new float[]{0, 3, 1},
                new float[]{1.1F, 1.1F, 1.1F});
        Transform first = new Transform(new float[]{0, -90, 25}, new float[]{1.13F, 3.2F, 1.13F},
                new float[]{1.36F, 1.36F, 1.36F});
        return Map.ofEntries(
                Map.entry(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, third),
                Map.entry(ItemDisplayContext.THIRD_PERSON_LEFT_HAND, third),
                Map.entry(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, first),
                Map.entry(ItemDisplayContext.FIRST_PERSON_LEFT_HAND, first),
                Map.entry(ItemDisplayContext.GROUND,
                        new Transform(new float[]{0, 0, 0}, new float[]{0, 2, 0},
                                new float[]{1, 1, 1})));
    }

    private static Map<ItemDisplayContext, Transform> summonerTransforms() {
        Map<ItemDisplayContext, Transform> transforms = new EnumMap<>(ItemDisplayContext.class);
        transforms.put(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                new Transform(new float[]{0, 0, 0}, new float[]{0, 3, 1}, new float[]{0.9F, 0.9F, 0.9F}));
        transforms.put(ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
                new Transform(new float[]{0, 0, 0}, new float[]{0, 3, 1}, new float[]{0.9F, 0.9F, 0.9F}));
        transforms.put(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                new Transform(new float[]{0, -90, 25}, new float[]{1.13F, 3.2F, 1.13F},
                        new float[]{0.85F, 0.85F, 0.85F}));
        transforms.put(ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                new Transform(new float[]{0, -90, 25}, new float[]{1.13F, 3.2F, 1.13F},
                        new float[]{0.85F, 0.85F, 0.85F}));
        transforms.put(ItemDisplayContext.GROUND,
                new Transform(new float[]{0, 0, 0}, new float[]{0, 2, 0}, new float[]{0.55F, 0.55F, 0.55F}));
        transforms.put(ItemDisplayContext.GUI,
                new Transform(new float[]{30, 225, 0}, new float[]{0, 0, 0}, new float[]{0.55F, 0.55F, 0.55F}));
        return transforms;
    }

    private static Identifier objModel(ItemModelGenerators itemModels, String name, String model,
            Map<TextureSlot, Material> textures, Map<ItemDisplayContext, Transform> transforms) {
        Identifier id = Identifier.fromNamespaceAndPath(FxEasy.MODID, "item/" + name);
        objTemplate(model, textures, transforms).create(id, mapping(textures), itemModels.modelOutput);
        return id;
    }

    private static void objBlock(BlockModelGenerators blockModels, Block block, String name, String model,
            Map<TextureSlot, Material> textures) {
        Identifier id = Identifier.fromNamespaceAndPath(FxEasy.MODID, "block/" + name);
        objTemplate(model, textures, Map.of()).create(id, mapping(textures), blockModels.modelOutput);
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(block,
                BlockModelGenerators.plainVariant(id)));
    }

    private static void objItem(ItemModelGenerators itemModels, Item item, String name, String model,
            Map<TextureSlot, Material> textures, Map<ItemDisplayContext, Transform> transforms) {
        Identifier id = Identifier.fromNamespaceAndPath(FxEasy.MODID, "item/" + name);
        objTemplate(model, textures, transforms).create(id, mapping(textures), itemModels.modelOutput);
        itemModels.itemModelOutput.accept(item, ItemModelUtils.plainModel(id));
    }

    private static ExtendedModelTemplate objTemplate(String model, Map<TextureSlot, Material> textures,
            Map<ItemDisplayContext, Transform> transforms) {
        ExtendedModelTemplateBuilder builder = ExtendedModelTemplateBuilder.builder()
                .customLoader(ObjModelBuilder::new, loader -> loader
                        .modelLocation(Identifier.fromNamespaceAndPath(FxEasy.MODID, "models/" + model + ".obj"))
                        .overrideMaterialLibrary(
                                Identifier.fromNamespaceAndPath(FxEasy.MODID, "models/" + model + ".mtl"))
                        .automaticCulling(false)
                        .shadeQuads(true)
                        .flipV(true)
                        .emissiveAmbient(true));
        transforms.forEach((context, transform) -> builder.transform(context, vector -> vector
                .rotation(transform.rotation()[0], transform.rotation()[1], transform.rotation()[2])
                .translation(transform.translation()[0], transform.translation()[1], transform.translation()[2])
                .scale(transform.scale()[0], transform.scale()[1], transform.scale()[2])));
        return builder.build();
    }

    private static TextureMapping mapping(Map<TextureSlot, Material> textures) {
        TextureMapping mapping = new TextureMapping();
        textures.forEach(mapping::putForced);
        return mapping;
    }

    private static Material texture(String path) {
        return new Material(Identifier.fromNamespaceAndPath(FxEasy.MODID, path));
    }

    private static Map<ItemDisplayContext, Transform> blockTransforms(float guiScale) {
        Transform third = new Transform(new float[]{75, 45, 0}, new float[]{0, 2.5F, 0},
                new float[]{0.1875F, 0.1875F, 0.1875F});
        return Map.ofEntries(
                Map.entry(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, third),
                Map.entry(ItemDisplayContext.THIRD_PERSON_LEFT_HAND, third),
                Map.entry(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                        new Transform(new float[]{0, 45, 0}, new float[]{0, 0, 0},
                                new float[]{0.2F, 0.2F, 0.2F})),
                Map.entry(ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                        new Transform(new float[]{0, 225, 0}, new float[]{0, 0, 0},
                                new float[]{0.2F, 0.2F, 0.2F})),
                Map.entry(ItemDisplayContext.GROUND,
                        new Transform(new float[]{0, 0, 0}, new float[]{0, 3, 0},
                                new float[]{0.125F, 0.125F, 0.125F})),
                Map.entry(ItemDisplayContext.GUI,
                        new Transform(new float[]{30, 225, 0}, new float[]{0, 0, 0},
                                new float[]{guiScale, guiScale, guiScale})),
                Map.entry(ItemDisplayContext.HEAD,
                        new Transform(new float[]{0, 180, 0}, new float[]{0, 0, 0},
                                new float[]{0.5F, 0.5F, 0.5F})),
                Map.entry(ItemDisplayContext.FIXED,
                        new Transform(new float[]{0, 0, 0}, new float[]{0, 0, 0},
                                new float[]{0.25F, 0.25F, 0.25F})));
    }

    private static Map<ItemDisplayContext, Transform> atomicTransforms() {
        Transform third = new Transform(new float[]{75, 45, 0}, new float[]{0, 2.5F, 0},
                new float[]{0.1875F, 0.1875F, 0.1875F});
        return Map.ofEntries(
                Map.entry(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, third),
                Map.entry(ItemDisplayContext.THIRD_PERSON_LEFT_HAND, third),
                Map.entry(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                        new Transform(new float[]{0, 45, 0}, new float[]{0, 0, 0},
                                new float[]{0.5F, 0.5F, 0.5F})),
                Map.entry(ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                        new Transform(new float[]{0, 225, 0}, new float[]{0, 0, 0},
                                new float[]{0.5F, 0.5F, 0.5F})),
                Map.entry(ItemDisplayContext.GROUND,
                        new Transform(new float[]{0, 0, 0}, new float[]{0, 3, 0},
                                new float[]{1.0F, 1.0F, 1.0F})),
                Map.entry(ItemDisplayContext.GUI,
                        new Transform(new float[]{30, 225, 0}, new float[]{0, 0, 0},
                                new float[]{1.0F, 1.0F, 1.0F})),
                Map.entry(ItemDisplayContext.HEAD,
                        new Transform(new float[]{0, 180, 0}, new float[]{0, 0, 0},
                                new float[]{0.5F, 0.5F, 0.5F})),
                Map.entry(ItemDisplayContext.FIXED,
                        new Transform(new float[]{0, 0, 0}, new float[]{0, 0, 0},
                                new float[]{0.55F, 0.55F, 0.55F})));
    }

    private static Map<ItemDisplayContext, Transform> swordTransforms() {
        return Map.ofEntries(
                Map.entry(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                        new Transform(new float[]{0, -90, 45}, new float[]{0, 12, 2.5F},
                                new float[]{0.9F, 0.9F, 0.9F})),
                Map.entry(ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
                        new Transform(new float[]{0, 90, -45}, new float[]{0, 12, 2.5F},
                                new float[]{0.9F, 0.9F, 0.9F})),
                Map.entry(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                        new Transform(new float[]{0, -90, 25}, new float[]{2, 8, 1},
                                new float[]{0.4F, 0.4F, 0.4F})),
                Map.entry(ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                        new Transform(new float[]{0, 90, -25}, new float[]{2, 8, 1},
                                new float[]{1.05F, 1.05F, 1.05F})),
                Map.entry(ItemDisplayContext.GROUND,
                        new Transform(new float[]{0, 0, 0}, new float[]{0, 11, 0},
                                new float[]{0.75F, 0.75F, 0.75F})),
                Map.entry(ItemDisplayContext.GUI,
                        new Transform(new float[]{30, 225, 0}, new float[]{0, -10, 0},
                                new float[]{0.66F, 0.66F, 0.66F})),
                Map.entry(ItemDisplayContext.FIXED,
                        new Transform(new float[]{0, 180, 0}, new float[]{0, -8, -7},
                                new float[]{0.9F, 0.9F, 0.9F})));
    }

    private static Map<ItemDisplayContext, Transform> spearTransforms() {
        return Map.ofEntries(
                Map.entry(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                        new Transform(new float[]{0, -90, 45}, new float[]{0, 12, 2.5F},
                                new float[]{0.9F, 0.9F, 0.9F})),
                Map.entry(ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
                        new Transform(new float[]{0, 90, -45}, new float[]{0, 12, 2.5F},
                                new float[]{0.9F, 0.9F, 0.9F})),
                Map.entry(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                        new Transform(new float[]{0, -90, 25}, new float[]{2, 8, 1},
                                new float[]{0.3F, 0.3F, 0.3F})),
                Map.entry(ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                        new Transform(new float[]{0, 90, -25}, new float[]{2, 8, 1},
                                new float[]{0.3F, 0.3F, 0.3F})),
                Map.entry(ItemDisplayContext.GROUND,
                        new Transform(new float[]{0, 0, 0}, new float[]{0, 11, 0},
                                new float[]{0.75F, 0.75F, 0.75F})),
                Map.entry(ItemDisplayContext.GUI,
                        new Transform(new float[]{30, 225, 0}, new float[]{0, -10, 0},
                                new float[]{0.33F, 0.33F, 0.33F})),
                Map.entry(ItemDisplayContext.FIXED,
                        new Transform(new float[]{0, 180, 0}, new float[]{0, -8, -7},
                                new float[]{0.9F, 0.9F, 0.9F})));
    }

    private static Map<ItemDisplayContext, Transform> gunTransforms() {
        return Map.ofEntries(
                Map.entry(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                        new Transform(new float[]{75, 45, 0}, new float[]{0, 2.5F, 0},
                                new float[]{0.4F, 0.4F, 0.4F})),
                Map.entry(ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
                        new Transform(new float[]{75, 45, 0}, new float[]{0, 2.5F, 0},
                                new float[]{0.4F, 0.4F, 0.4F})),
                Map.entry(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                        new Transform(new float[]{0, 180, 0}, new float[]{0, 0, 0},
                                new float[]{0.4F, 0.4F, 0.4F})),
                Map.entry(ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                        new Transform(new float[]{0, 180, 0}, new float[]{0, 0, 0},
                                new float[]{0.4F, 0.4F, 0.4F})),
                Map.entry(ItemDisplayContext.GROUND,
                        new Transform(new float[]{0, 0, 0}, new float[]{0, 3, 0},
                                new float[]{0.5F, 0.5F, 0.5F})),
                Map.entry(ItemDisplayContext.GUI,
                        new Transform(new float[]{30, 225, 0}, new float[]{0, 0, 0},
                                new float[]{1.0F, 1.0F, 1.0F})),
                Map.entry(ItemDisplayContext.HEAD,
                        new Transform(new float[]{0, 180, 0}, new float[]{0, 0, 0},
                                new float[]{0.5F, 0.5F, 0.5F})),
                Map.entry(ItemDisplayContext.FIXED,
                        new Transform(new float[]{0, 0, 0}, new float[]{0, 0, 0},
                                new float[]{1.05F, 1.05F, 1.05F})));
    }

    private record Transform(float[] rotation, float[] translation, float[] scale) {
    }
}
