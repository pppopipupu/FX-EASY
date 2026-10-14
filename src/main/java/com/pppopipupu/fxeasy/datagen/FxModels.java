package com.pppopipupu.fxeasy.datagen;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import com.pppopipupu.fxeasy.client.FxPhoneRenderer;
import com.pppopipupu.fxeasy.content.FxItems;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;

public final class FxModels extends ModelProvider {

    public FxModels(PackOutput output) {
        super(output, FxEasy.MODID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        Identifier model = itemModels.createFlatItemModel(FxItems.FX_PHONE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.itemModelOutput.accept(FxItems.FX_PHONE.get(), FxPhoneRenderer.itemModel(model, TextureMapping.getItemTexture(FxItems.FX_PHONE.get()).sprite()));
    }
}
