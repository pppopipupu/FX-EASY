package com.pppopipupu.fxeasy.shop;

import module java.base;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;

/// One gift the VIP lounge hands out: an item, how many of it, and the potion the stack carries when the pool
/// of the world names one, because a potion has no item id of its own. The gift is data rather than a stack, so
/// it survives a save and travels to a client that has to draw its icon; [stack] is the only place it becomes
/// an item.
public record LoungeGift(Identifier item, int count, Identifier potion) {

    public LoungeGift(Identifier item, int count) {
        this(item, count, null);
    }

    /// Whether the registry holds the item of this gift, and the potion when it names one, which is what makes it
    /// a gift the lounge can hand over at all. A pool leaves out what this answers no to, so a typo in a world's
    /// file costs nothing rather than a gift that can only ever be drawn as an empty stack.
    public boolean resolvable() {
        if (this.count <= 0 || BuiltInRegistries.ITEM.getValue(this.item) == Items.AIR) {
            return false;
        }
        return this.potion == null || BuiltInRegistries.POTION.get(this.potion).isPresent();
    }

    /// The stack this gift is handed over as, empty when [resolvable] is false.
    public ItemStack stack() {
        if (!this.resolvable()) {
            return ItemStack.EMPTY;
        }
        Item resolved = BuiltInRegistries.ITEM.getValue(this.item);
        if (this.potion == null) {
            return new ItemStack(resolved, this.count);
        }
        return BuiltInRegistries.POTION.get(this.potion).map(holder -> potionStack(resolved, holder))
                .orElse(ItemStack.EMPTY);
    }

    private ItemStack potionStack(Item item, Holder<Potion> potion) {
        ItemStack stack = PotionContents.createItemStack(item, potion);
        stack.setCount(this.count);
        return stack;
    }
}
