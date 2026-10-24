package com.pppopipupu.fxeasy.content.block;

import module java.base;

import com.mojang.serialization.MapCodec;
import com.pppopipupu.fxeasy.content.FxBlocks;
import com.pppopipupu.fxeasy.content.FxItems;
import com.pppopipupu.fxeasy.content.block.entity.AngryBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import org.jspecify.annotations.Nullable;

public class AngryBlock extends MultiPartBlock {

    public static final BooleanProperty IS_LIGHTNING = BooleanProperty.create("is_lightning");
    public static final BooleanProperty IS_ATOMIC = BooleanProperty.create("is_atomic");
    public static final BooleanProperty IS_UNSTABLE = BooleanProperty.create("is_unstable");

    public AngryBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(IS_LIGHTNING, false)
                .setValue(IS_ATOMIC, false)
                .setValue(IS_UNSTABLE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(IS_LIGHTNING, IS_ATOMIC, IS_UNSTABLE);
    }

    @Override
    protected MapCodec<AngryBlock> codec() {
        return FxBlocks.ANGRY_BLOCK_TYPE.get();
    }

    @Nullable
    @Override
    protected BlockEntity createCoreBlockEntity(BlockPos pos, BlockState state) {
        return new AngryBlockEntity(pos, state);
    }

    @Override
    public Vec3i getStructureDimensions() {
        return new Vec3i(2, 2, 2);
    }

    @Override
    public BlockPos getCoreOffsetInStructure() {
        return BlockPos.ZERO;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> blockEntityType) {
        if (!state.getValue(IS_CORE)) {
            return null;
        }
        if (level.isClientSide()) {
            return createTickerHelper(blockEntityType, FxBlocks.ANGRY_BLOCK_ENTITY.get(),
                    AngryBlockEntity::tickClient);
        }
        return createTickerHelper(blockEntityType, FxBlocks.ANGRY_BLOCK_ENTITY.get(), AngryBlockEntity::tick);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (state.getValue(IS_UNSTABLE)) {
            return List.of();
        }
        if (state.getValue(IS_LIGHTNING)) {
            return List.of(FxItems.ANGRY_LIGHTNING_BLOCK_ITEM.get().getDefaultInstance());
        }
        if (state.getValue(IS_ATOMIC)) {
            return List.of(FxItems.ANGRY_ATOMIC_BLOCK_ITEM.get().getDefaultInstance());
        }
        return List.of(FxItems.ANGRY_BLOCK_ITEM.get().getDefaultInstance());
    }
}
