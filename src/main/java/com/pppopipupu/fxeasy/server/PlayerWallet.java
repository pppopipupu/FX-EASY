package com.pppopipupu.fxeasy.server;

import module java.base;

import com.pppopipupu.fxeasy.content.FxCurrencies;
import com.pppopipupu.fxeasy.market.Currency;
import com.pppopipupu.fxeasy.trade.Wallet;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/// Bridges a trading account to the player inventory, one item being one currency unit. Only a currency its
/// definition backs ever reaches it, because the trade engine refuses to move an unbacked one, and a currency
/// whose item the registry does not hold moves nothing rather than counting an empty stack.
public final class PlayerWallet implements Wallet {

    private final Inventory inventory;

    public PlayerWallet(Player player) {
        this.inventory = player.getInventory();
    }

    @Override
    public long count(Currency currency) {
        Item item = FxCurrencies.item(currency);
        if (item == null) {
            return 0L;
        }
        long count = 0L;
        for (int slot = 0; slot < this.inventory.getContainerSize(); slot++) {
            ItemStack stack = this.inventory.getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    @Override
    public boolean remove(Currency currency, long units) {
        if (units > Integer.MAX_VALUE) {
            return false;
        }
        Item item = FxCurrencies.item(currency);
        if (item == null) {
            return false;
        }
        long remaining = units;
        for (int slot = 0; slot < this.inventory.getContainerSize() && remaining > 0L; slot++) {
            ItemStack stack = this.inventory.getItem(slot);
            if (!stack.is(item)) {
                continue;
            }
            int taken = (int) Math.min(remaining, stack.getCount());
            stack.shrink(taken);
            remaining -= taken;
        }
        return remaining == 0L;
    }

    @Override
    public long add(Currency currency, long units) {
        Item item = FxCurrencies.item(currency);
        if (item == null) {
            return 0L;
        }
        int maxStack = new ItemStack(item).getMaxStackSize();
        long remaining = units;
        while (remaining > 0L) {
            ItemStack stack = new ItemStack(item, (int) Math.min(remaining, maxStack));
            int before = stack.getCount();
            this.inventory.add(stack);
            long added = before - stack.getCount();
            remaining -= added;
            if (added <= 0L) {
                break;
            }
        }
        return units - remaining;
    }
}
