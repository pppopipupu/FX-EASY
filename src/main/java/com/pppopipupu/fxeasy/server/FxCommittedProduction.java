package com.pppopipupu.fxeasy.server;

import module java.base;

import com.pppopipupu.fxeasy.economy.EconomySourceKind;
import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;

/// The committed production of one world: crafting and smithing takes, the crafter that assembled a result and
/// then consumed its inputs, and the villager trades whose payment the merchant committed. Every terminal fact
/// is the net of the result over the inputs, so a recipe that gives back what it consumed contributes nothing,
/// and a trade counts only after the payment was taken.
///
/// Only the server thread touches this state, which is why the crafter scratch fields are plain fields.
final class FxCommittedProduction {

    /// The currency whose payout the report separates from every other villager payment: the emerald channel a
    /// world that trades emeralds reports on its own.
    private static final String VILLAGER_ISSUANCE_ID = "emerald";

    private final FxWorldEconomyTracker tracker;
    private final FxTradeRegions regions;

    private ServerLevel crafterLevel;
    private ItemStack crafterResult = ItemStack.EMPTY;
    private long[] crafterInputAtoms;
    private boolean crafterAssembled;

    FxCommittedProduction(FxWorldEconomyTracker tracker, FxTradeRegions regions) {
        this.tracker = tracker;
        this.regions = regions;
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, FxCommittedProduction::onTrade);
    }

    private static void onTrade(TradeWithVillagerEvent event) {
        FxWorldEconomyTracker tracker = FxWorldEconomyTracker.current();
        if (tracker != null) {
            tracker.production.observeTrade(event);
        }
    }

    private void observeTrade(TradeWithVillagerEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        MerchantOffer offer = event.getMerchantOffer();
        BlockPos pos = event.getAbstractVillager().blockPosition();
        this.recordVillagerTrade(level, offer);
        this.regions.observeTradeRegion(level.dimension(), pos, event.getAbstractVillager());
    }

    /// The commit point of a crafting take: the player removed the result and the grid was consumed.
    void craftingTaken(ServerLevel level, RecipeInput input, ItemStack result) {
        this.recordNet(EconomySourceKind.CRAFT_TERMINAL_USE, level, result, input);
    }

    /// The commit point of a smithing take: the three input slots were shrunk for the result.
    void smithingTaken(ServerLevel level, ItemStack template, ItemStack base, ItemStack addition, ItemStack result) {
        this.recordNet(EconomySourceKind.SMITHING_TERMINAL_USE, level, result, template, base, addition);
    }

    /// Called when a crafter assembled a result, which is the only branch that shrinks its inputs.
    void crafterAssembled(RecipeInput input, ItemStack result, ServerLevel level) {
        this.crafterAssembled = true;
        this.crafterResult = result;
        this.crafterLevel = level;
        long[] atoms = this.crafterInputAtoms;
        if (atoms == null) {
            atoms = new long[Currencies.count()];
            this.crafterInputAtoms = atoms;
        }
        Arrays.fill(atoms, 0L);
        for (int slot = 0; slot < input.size(); slot++) {
            FxCurrencyAtoms.AtomValue value = FxCurrencyAtoms.value(input.getItem(slot));
            if (value != null) {
                atoms[Currencies.index(value.currency())] += value.atoms();
            }
        }
    }

    /// The commit point of a crafter: it assembled and then shrank every input it consumed.
    void crafterCommitted() {
        if (!this.crafterAssembled) {
            return;
        }
        this.crafterAssembled = false;
        ServerLevel level = this.crafterLevel;
        ItemStack result = this.crafterResult;
        long[] inputs = this.crafterInputAtoms;
        this.crafterLevel = null;
        this.crafterResult = ItemStack.EMPTY;
        if (level == null || inputs == null) {
            return;
        }
        this.recordNet(EconomySourceKind.CRAFTER_TERMINAL_USE, level, result, inputs);
    }

    private void recordVillagerTrade(ServerLevel level, MerchantOffer offer) {
        FxCurrencyAtoms.AtomValue result = FxCurrencyAtoms.value(offer.getResult());
        if (result != null) {
            EconomySourceKind kind = VILLAGER_ISSUANCE_ID.equals(result.currency().id())
                    ? EconomySourceKind.VILLAGER_PAYS_EMERALD
                    : EconomySourceKind.VILLAGER_PAYS_CURRENCY;
            this.tracker.record(kind, result.currency(), result.atoms(), level.dimension());
        }
        this.recordVillagerPayment(level, offer.getCostA());
        this.recordVillagerPayment(level, offer.getCostB());
    }

    private void recordVillagerPayment(ServerLevel level, ItemStack payment) {
        FxCurrencyAtoms.AtomValue value = FxCurrencyAtoms.value(payment);
        if (value != null) {
            this.tracker.record(EconomySourceKind.VILLAGER_REABSORB, value.currency(), value.atoms(), level.dimension());
        }
    }

    private void recordNet(EconomySourceKind kind, ServerLevel level, ItemStack result, RecipeInput input) {
        for (Currency currency : Currencies.all()) {
            long atoms = 0L;
            for (int slot = 0; slot < input.size(); slot++) {
                atoms += atomsOf(currency, input.getItem(slot));
            }
            if (atoms == 0L) {
                continue;
            }
            recordNet(kind, level, result, currency, atoms);
        }
    }

    private void recordNet(EconomySourceKind kind, ServerLevel level, ItemStack result,
            ItemStack first, ItemStack second, ItemStack third) {
        for (Currency currency : Currencies.all()) {
            long atoms = atomsOf(currency, first) + atomsOf(currency, second) + atomsOf(currency, third);
            if (atoms == 0L) {
                continue;
            }
            recordNet(kind, level, result, currency, atoms);
        }
    }

    private static long atomsOf(Currency currency, ItemStack stack) {
        FxCurrencyAtoms.AtomValue value = FxCurrencyAtoms.value(stack);
        return value != null && value.currency() == currency ? value.atoms() : 0L;
    }

    private void recordNet(EconomySourceKind kind, ServerLevel level, ItemStack result, long[] inputs) {
        for (Currency currency : Currencies.all()) {
            long atoms = inputs[Currencies.index(currency)];
            if (atoms == 0L) {
                continue;
            }
            recordNet(kind, level, result, currency, atoms);
        }
    }

    private void recordNet(EconomySourceKind kind, ServerLevel level, ItemStack result, Currency currency, long atoms) {
        FxCurrencyAtoms.AtomValue produced = FxCurrencyAtoms.value(result);
        long net = produced != null && produced.currency() == currency ? atoms - produced.atoms() : atoms;
        if (net > 0L) {
            this.tracker.record(kind, currency, net, level.dimension());
        }
    }
}
