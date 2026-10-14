package com.pppopipupu.fxeasy.content;

import module java.base;

import com.pppopipupu.fxeasy.client.FxPhoneTerminal;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

public final class FxPhoneItem extends Item {

    public FxPhoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (FMLEnvironment.getDist() == Dist.CLIENT && level.isClientSide()) {
            FxPhoneTerminal.open();
        }
        return InteractionResult.SUCCESS;
    }
}
