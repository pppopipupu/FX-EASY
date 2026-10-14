package com.pppopipupu.fxeasy.server;

import module java.base;

import com.pppopipupu.fxeasy.content.FxCurrencies;
import com.pppopipupu.fxeasy.economy.EconomySourceKind;
import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/// Atom value of every item the collector prices, and the classification of the blocks the world can produce
/// them from. The definitions come first: an item a currency names is worth that currency's `atomsPerItem`. The
/// sub units below cover the forms that are not a currency's own item - a gold nugget is one atom and a gold
/// block nine items - so a count is exact whatever form the currency is committed in, and an item no currency
/// and no sub unit claims is worth nothing. An ore block that silk touch returns intact is valued at the
/// resource it would otherwise have dropped, which is what makes a silk touched find and an unenchanted find one
/// fact.
public final class FxCurrencyAtoms {

    /// Atoms of the currency one item stands for. `atoms` is never zero and never negative.
    public record AtomValue(Currency currency, long atoms) {

        public AtomValue {
            if (atoms <= 0L) {
                throw new IllegalArgumentException("atom value must be positive");
            }
        }
    }

    /// Sub unit of the currency whose id it names, in atoms rather than in items of that currency.
    private record SubUnit(String currencyId, long atoms) {
    }

    private static final Map<Item, SubUnit> SUB_UNITS = Map.ofEntries(
            Map.entry(Items.GRASS_BLOCK, new SubUnit("dirt", 1L)),
            Map.entry(Items.COARSE_DIRT, new SubUnit("dirt", 1L)),
            Map.entry(Items.ROOTED_DIRT, new SubUnit("dirt", 1L)),
            Map.entry(Items.PODZOL, new SubUnit("dirt", 1L)),
            Map.entry(Items.MYCELIUM, new SubUnit("dirt", 1L)),
            Map.entry(Items.GOLD_NUGGET, new SubUnit("gold", 1L)),
            Map.entry(Items.GOLD_BLOCK, new SubUnit("gold", 81L)),
            Map.entry(Items.RAW_GOLD, new SubUnit("gold", 9L)),
            Map.entry(Items.RAW_GOLD_BLOCK, new SubUnit("gold", 81L)),
            Map.entry(Items.GOLD_ORE, new SubUnit("gold", 9L)),
            Map.entry(Items.DEEPSLATE_GOLD_ORE, new SubUnit("gold", 9L)),
            Map.entry(Items.NETHER_GOLD_ORE, new SubUnit("gold", 9L)),
            Map.entry(Items.EMERALD_BLOCK, new SubUnit("emerald", 9L)),
            Map.entry(Items.EMERALD_ORE, new SubUnit("emerald", 1L)),
            Map.entry(Items.DEEPSLATE_EMERALD_ORE, new SubUnit("emerald", 1L)),
            Map.entry(Items.DIAMOND_BLOCK, new SubUnit("diamond", 9L)),
            Map.entry(Items.DIAMOND_ORE, new SubUnit("diamond", 1L)),
            Map.entry(Items.DEEPSLATE_DIAMOND_ORE, new SubUnit("diamond", 1L)),
            Map.entry(Items.ANCIENT_DEBRIS, new SubUnit("netherite", 1L)),
            Map.entry(Items.NETHERITE_SCRAP, new SubUnit("netherite", 1L)),
            Map.entry(Items.NETHERITE_BLOCK, new SubUnit("netherite", 36L)));

    private static final Set<Block> ORE_BLOCKS = Set.of(
            Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE,
            Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
            Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
            Blocks.ANCIENT_DEBRIS, Blocks.GILDED_BLACKSTONE);

    private static final Set<Block> DIRT_BLOCKS = Set.of(
            Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT, Blocks.PODZOL, Blocks.MYCELIUM);

    private FxCurrencyAtoms() {
    }

    /// Atom value of one stack, or null when the stack holds no currency this world trades. The stack count is
    /// already multiplied in, so a stack of three gold ingots is twenty seven atoms.
    public static AtomValue value(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        Item item = stack.getItem();
        Currency currency = currencyOf(item);
        if (currency != null) {
            return new AtomValue(currency, currency.atomsPerItem() * stack.getCount());
        }
        SubUnit sub = SUB_UNITS.get(item);
        if (sub == null) {
            return null;
        }
        Currency named = Currencies.byId(sub.currencyId());
        return named == null ? null : new AtomValue(named, sub.atoms() * stack.getCount());
    }

    /// Supply channel a block belongs to when it is broken where it naturally generated: an ore block is a
    /// natural find and a dirt block is a dig. Null means the block is not a natural resource at all, which is
    /// also the test that decides whether a placed block has to be remembered.
    public static EconomySourceKind naturalChannel(BlockState state) {
        Block block = state.getBlock();
        if (ORE_BLOCKS.contains(block)) {
            return EconomySourceKind.NATURAL_ORE;
        }
        return DIRT_BLOCKS.contains(block) ? EconomySourceKind.DIRT_DIG : null;
    }

    /// The currency a world trades that is backed by this item, or null when none is.
    private static Currency currencyOf(Item item) {
        for (Currency currency : Currencies.all()) {
            if (item == FxCurrencies.item(currency)) {
                return currency;
            }
        }
        return null;
    }
}
