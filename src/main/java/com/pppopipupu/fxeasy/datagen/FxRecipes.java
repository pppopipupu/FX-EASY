package com.pppopipupu.fxeasy.datagen;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import com.pppopipupu.fxeasy.content.FxItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Items;

public final class FxRecipes extends RecipeProvider {

    public FxRecipes(HolderLookup.Provider registries, RecipeOutput output) {
        super(registries, output);
    }

    @Override
    protected void buildRecipes() {
        ShapelessRecipeBuilder.shapeless(this.items, RecipeCategory.MISC, FxItems.FX_PHONE.get())
                .requires(Items.IRON_INGOT)
                .requires(Items.GOLD_INGOT)
                .requires(Items.EMERALD)
                .requires(Items.DIRT)
                .requires(Items.DIAMOND)
                .requires(Items.AMETHYST_SHARD)
                .requires(Items.COPPER_INGOT)
                .unlockedBy("has_diamond", has(Items.DIAMOND))
                .save(this.output, ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath(FxEasy.MODID, "fx_phone")));
    }

    public static final class Runner extends RecipeProvider.Runner {

        public Runner(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries);
        }

        @Override
        protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
            return new FxRecipes(registries, output);
        }

        @Override
        public String getName() {
            return "FX EASY recipes";
        }
    }
}
