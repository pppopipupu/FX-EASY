package com.pppopipupu.fxeasy.content.block;

import module java.base;

import com.mojang.serialization.MapCodec;
import com.pppopipupu.fxeasy.content.FxBlocks;
import com.pppopipupu.fxeasy.content.FxItems;
import com.pppopipupu.fxeasy.content.block.entity.AngryFemaleEntity;
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

public class AngryFemaleBlock extends MultiPartBlock {

    public static final BooleanProperty IS_LOVE = BooleanProperty.create("is_love");

    public AngryFemaleBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(IS_LOVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(IS_LOVE);
    }

    @Override
    protected MapCodec<AngryFemaleBlock> codec() {
        return FxBlocks.ANGRY_FEMALE_BLOCK_TYPE.get();
    }

    @Nullable
    @Override
    protected BlockEntity createCoreBlockEntity(BlockPos pos, BlockState state) {
        return new AngryFemaleEntity(pos, state);
    }

    @Override
    public Vec3i getStructureDimensions() {
        return new Vec3i(2, 2, 2);
    }

    @Override
    public BlockPos getCoreOffsetInStructure() {
        return BlockPos.ZERO;
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (state.getValue(IS_CORE)) {
            return List.of(FxItems.ANGRY_FEMALE_ITEM.get().getDefaultInstance());
        }
        return List.of();
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> blockEntityType) {
        if (!state.getValue(IS_CORE)) {
            return null;
        }
        if (level.isClientSide()) {
            return createTickerHelper(blockEntityType, FxBlocks.ANGRY_FEMALE_ENTITY.get(),
                    AngryFemaleEntity::tickClient);
        }
        return createTickerHelper(blockEntityType, FxBlocks.ANGRY_FEMALE_ENTITY.get(), AngryFemaleEntity::tick);
    }
}
