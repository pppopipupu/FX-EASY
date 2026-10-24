package com.pppopipupu.fxeasy.content;

import com.pppopipupu.fxeasy.content.block.entity.AngryFemaleEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class AngryClientHooks {

    @FunctionalInterface
    public interface FemaleTick {
        void tick(AngryFemaleEntity entity, Level level, BlockPos pos, BlockState state);
    }

    @FunctionalInterface
    public interface SpearEffect {
        void run(Level level, LivingEntity user);
    }

    @FunctionalInterface
    public interface WandEffect {
        void run(Level level, LivingEntity user);
    }

    @FunctionalInterface
    public interface WandRelease {
        void run(Level level, LivingEntity user);
    }

    public static FemaleTick femaleTick = (entity, level, pos, state) -> {
    };
    public static SpearEffect spearEffect = (level, user) -> {
    };
    public static WandEffect wandEffect = (level, user) -> {
    };
    public static WandRelease wandRelease = (level, user) -> {
    };

    private AngryClientHooks() {
    }
}
