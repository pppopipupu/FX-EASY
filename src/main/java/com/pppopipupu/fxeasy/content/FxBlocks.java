package com.pppopipupu.fxeasy.content;

import com.mojang.serialization.MapCodec;
import com.pppopipupu.fxeasy.FxEasy;
import com.pppopipupu.fxeasy.content.block.AngryBlock;
import com.pppopipupu.fxeasy.content.block.AngryFemaleBlock;
import com.pppopipupu.fxeasy.content.block.entity.AngryBlockEntity;
import com.pppopipupu.fxeasy.content.block.entity.AngryFemaleEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class FxBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FxEasy.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, FxEasy.MODID);
    public static final DeferredRegister<MapCodec<? extends Block>> BLOCK_TYPES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_TYPE, FxEasy.MODID);

    public static final DeferredBlock<AngryBlock> ANGRY_BLOCK = BLOCKS.registerBlock("angry_block", AngryBlock::new,
            properties -> properties.strength(2.0F).mapColor(MapColor.STONE).noOcclusion());
    public static final DeferredBlock<AngryFemaleBlock> ANGRY_FEMALE_BLOCK = BLOCKS.registerBlock("angry_female_block",
            AngryFemaleBlock::new, properties -> properties.strength(2.0F).mapColor(MapColor.STONE)
                    .explosionResistance(1000.0F).noOcclusion());
    public static final DeferredBlock<Block> SURPRISE_BLOCK = BLOCKS.registerSimpleBlock("surprise_block");

    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<AngryBlock>> ANGRY_BLOCK_TYPE =
            BLOCK_TYPES.register("angry_block", () -> BlockBehaviour.simpleCodec(AngryBlock::new));
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<AngryFemaleBlock>> ANGRY_FEMALE_BLOCK_TYPE =
            BLOCK_TYPES.register("angry_female_block", () -> BlockBehaviour.simpleCodec(AngryFemaleBlock::new));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AngryBlockEntity>> ANGRY_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("angry_block_entity",
                    () -> new BlockEntityType<>(AngryBlockEntity::new, ANGRY_BLOCK.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AngryFemaleEntity>> ANGRY_FEMALE_ENTITY =
            BLOCK_ENTITY_TYPES.register("angry_female_entity",
                    () -> new BlockEntityType<>(AngryFemaleEntity::new, ANGRY_FEMALE_BLOCK.get()));

    private FxBlocks() {
    }
}
