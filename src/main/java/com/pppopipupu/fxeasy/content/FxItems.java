package com.pppopipupu.fxeasy.content;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class FxItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FxEasy.MODID);

    public static final DeferredItem<FxPhoneItem> FX_PHONE =
            ITEMS.registerItem("fx_phone", FxPhoneItem::new, properties -> properties.stacksTo(1).rarity(Rarity.RARE));

    private FxItems() {
    }
}
