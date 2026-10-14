package com.pppopipupu.fxeasy.content;

import module java.base;

import com.pppopipupu.fxeasy.market.Currency;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/// The one place a currency is turned into the items that back it. The item comes from the definition, so a
/// world that defines its own currencies backs them without a table here, and a currency whose definition names
/// no item has none: every caller has to handle the absence instead of dereferencing it.
public final class FxCurrencies {

    private FxCurrencies() {
    }

    /// Item backing a currency, or null when its definition names no item or names one the registry does not
    /// hold.
    public static Item item(Currency currency) {
        return currency.item().map(FxCurrencies::resolve).orElse(null);
    }

    /// A stack of the backing item, empty for a currency without one.
    public static ItemStack stack(Currency currency, int count) {
        Item item = item(currency);
        return item == null ? ItemStack.EMPTY : new ItemStack(item, count);
    }

    private static Item resolve(Identifier identifier) {
        Item item = BuiltInRegistries.ITEM.getValue(identifier);
        return item == Items.AIR ? null : item;
    }
}
