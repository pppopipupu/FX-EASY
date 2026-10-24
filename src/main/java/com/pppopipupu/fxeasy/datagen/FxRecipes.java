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
        ShapelessRecipeBuilder.shapeless(this.items, RecipeCategory.COMBAT, FxItems.ANGRY_SPEAR.get())
                .requires(FxItems.ANGRY_LIGHTNING_BLOCK_ITEM.get(), 4)
                .requires(FxItems.ANGRY_SWORD.get())
                .unlockedBy("has_atomic_block", has(FxItems.ANGRY_ATOMIC_BLOCK_ITEM.get()))
                .save(this.output, ResourceKey.create(Registries.RECIPE,
                        Identifier.fromNamespaceAndPath(FxEasy.MODID, "angry_spear")));
        ShapelessRecipeBuilder.shapeless(this.items, RecipeCategory.COMBAT, FxItems.ANGRY_GUN.get())
                .requires(FxItems.ANGRY_ATOMIC_BLOCK_ITEM.get(), 8)
                .requires(FxItems.ANGRY_SWORD.get())
                .unlockedBy("has_atomic_block", has(FxItems.ANGRY_ATOMIC_BLOCK_ITEM.get()))
                .save(this.output, ResourceKey.create(Registries.RECIPE,
                        Identifier.fromNamespaceAndPath(FxEasy.MODID, "angry_gun")));
        ShapelessRecipeBuilder.shapeless(this.items, RecipeCategory.MISC, FxItems.ANGRY_METEOR_SUMMONER.get())
                .requires(FxItems.ANGRY_ATOMIC_BLOCK_ITEM.get(), 4)
                .requires(FxItems.ANGRY_LIGHTNING_BLOCK_ITEM.get(), 4)
                .requires(FxItems.ANGRY_BLOCK_ITEM.get())
                .unlockedBy("has_atomic_block", has(FxItems.ANGRY_ATOMIC_BLOCK_ITEM.get()))
                .save(this.output, ResourceKey.create(Registries.RECIPE,
                        Identifier.fromNamespaceAndPath(FxEasy.MODID, "angry_meteor_summoner")));
        ShapelessRecipeBuilder.shapeless(this.items, RecipeCategory.COMBAT, FxItems.ANGRY_STORM_GENERATOR.get())
                .requires(FxItems.ANGRY_METEOR_SUMMONER.get())
                .requires(FxItems.ANGRY_WAND.get())
                .requires(FxItems.ANGRY_GUN.get())
                .requires(FxItems.ANGRY_ATOMIC_BLOCK_ITEM.get(), 6)
                .unlockedBy("has_atomic_block", has(FxItems.ANGRY_ATOMIC_BLOCK_ITEM.get()))
                .save(this.output, ResourceKey.create(Registries.RECIPE,
                        Identifier.fromNamespaceAndPath(FxEasy.MODID, "angry_storm_generator")));
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
