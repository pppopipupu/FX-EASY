package com.pppopipupu.fxeasy.content.block.entity;

import module java.base;

import com.pppopipupu.fxeasy.content.AngryClientHooks;
import com.pppopipupu.fxeasy.content.FxBlocks;
import com.pppopipupu.fxeasy.content.block.AngryBlock;
import com.pppopipupu.fxeasy.content.block.AngryFemaleBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class AngryFemaleEntity extends BlockEntity {

    public float prevRotationAngle;
    public float rotationAngle;
    public boolean prevTickFlag;
    public boolean is_jiaopei;
    public int jiaopeiTime;

    public AngryFemaleEntity(BlockPos pos, BlockState blockState) {
        super(FxBlocks.ANGRY_FEMALE_ENTITY.get(), pos, blockState);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, AngryFemaleEntity blockEntity) {
        if (blockEntity.is_jiaopei && blockEntity.jiaopeiTime > 0) {
            blockEntity.jiaopeiTime--;
        } else if (blockEntity.is_jiaopei) {
            BlockPos.betweenClosed(pos.offset(-2, -2, -2), pos.offset(2, 2, 2)).forEach(blockPos -> {
                BlockState neighbour = level.getBlockState(blockPos);
                if (neighbour.getBlock() instanceof AngryBlock && neighbour.getValue(AngryBlock.IS_CORE)) {
                    BlockState atomic = FxBlocks.ANGRY_BLOCK.get().defaultBlockState()
                            .setValue(AngryBlock.IS_ATOMIC, true);
                    level.createFireworks(pos.getX(), pos.getY(), pos.getZ(), 0, 0, 0,
                            List.of(FireworkExplosion.DEFAULT));
                    level.setBlock(blockPos, atomic, 3);
                    level.destroyBlock(pos, false);
                }
            });
        }
        if (level.getGameTime() % 20 == 0 && !blockEntity.is_jiaopei) {
            BlockPos.betweenClosed(pos.offset(-2, -2, -2), pos.offset(2, 2, 2)).forEach(blockPos -> {
                BlockState neighbour = level.getBlockState(blockPos);
                if (neighbour.getBlock() instanceof AngryBlock
                        && !neighbour.getValue(AngryBlock.IS_ATOMIC)
                        && neighbour.getValue(AngryBlock.IS_CORE)) {
                    blockEntity.is_jiaopei = true;
                    blockEntity.jiaopeiTime = 100;
                }
            });
        }
    }

    public static void tickClient(Level level, BlockPos pos, BlockState state, AngryFemaleEntity blockEntity) {
        AngryClientHooks.femaleTick.tick(blockEntity, level, pos, state);
    }
}
