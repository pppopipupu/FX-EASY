package com.pppopipupu.fxeasy.client;

import module java.base;

import com.pppopipupu.fxeasy.content.FxCurrencies;
import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;
import com.pppopipupu.fxeasy.market.CurrencyPair;
import com.pppopipupu.fxeasy.market.Side;
import com.pppopipupu.fxeasy.market.Timeframe;
import com.pppopipupu.fxeasy.net.FxAccountOpenPayload;
import com.pppopipupu.fxeasy.net.FxAutoRenewPayload;
import com.pppopipupu.fxeasy.net.FxClaimBonusPayload;
import com.pppopipupu.fxeasy.net.FxCloseAllPayload;
import com.pppopipupu.fxeasy.net.FxClosePositionPayload;
import com.pppopipupu.fxeasy.net.FxDepositPayload;
import com.pppopipupu.fxeasy.net.FxLoungeClaimPayload;
import com.pppopipupu.fxeasy.net.FxOrderPayload;
import com.pppopipupu.fxeasy.net.FxShopBuyPayload;
import com.pppopipupu.fxeasy.net.FxSubscribePayload;
import com.pppopipupu.fxeasy.net.FxTutorialSeenPayload;
import com.pppopipupu.fxeasy.net.FxUnsubscribePayload;
import com.pppopipupu.fxeasy.net.FxWithdrawPayload;
import com.pppopipupu.fxeasy.shop.Membership;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/// Everything the panel can ask the server for. Every request is validated again server side.
public final class FxActions {

    private FxActions() {
    }

    /// Screens can be closed while the client is already leaving the world, so a request is only sent when a
    /// connection still exists.
    private static void send(CustomPacketPayload payload) {
        if (Minecraft.getInstance().getConnection() != null) {
            ClientPacketDistributor.sendToServer(payload);
        }
    }

    public static void subscribe(CurrencyPair pair, Timeframe timeframe) {
        FxClientState.INSTANCE.onSubscribed(pair, timeframe);
        send(new FxSubscribePayload(pair.index(), Timeframe.ALL.indexOf(timeframe)));
    }

    public static void unsubscribe() {
        FxClientState.INSTANCE.onUnsubscribed();
        send(new FxUnsubscribePayload());
    }

    public static void openOrder(CurrencyPair pair, Side side, Currency marginCurrency, int units, int leverage) {
        send(new FxOrderPayload(pair.index(), side.ordinal(), units, leverage, Currencies.index(marginCurrency)));
    }

    public static void closePosition(long positionId) {
        send(new FxClosePositionPayload(positionId));
    }

    public static void closeAll() {
        send(new FxCloseAllPayload());
    }

    /// The player filled in the registration and accepted the account opening: the terminal stops asking right
    /// away and the platform remembers the name and the age in the account, so the flow never comes back on
    /// another client or after a restart.
    public static void openAccount(String holder, int age) {
        FxClientState.INSTANCE.markOpened(holder, age);
        send(new FxAccountOpenPayload(holder, age));
    }

    /// The player asked for the welcome capital: the platform pays it once and answers with the account and the
    /// notice, which is what turns the button of the card into the confirmation that the money has landed.
    public static void claimBonus() {
        send(FxClaimBonusPayload.INSTANCE);
    }

    /// The player paid for a tier at the checkout, settling it in the currency the checkout was showing. The
    /// price, the rate it was converted at, the period and what the tier buys are all decided by the platform,
    /// which is why nothing is applied here first: the answer is the account snapshot that carries the plan the
    /// account is really on and the balance the platform really charged.
    public static void buyPlan(Membership plan, Currency paying) {
        send(new FxShopBuyPayload(plan.id(), paying.id()));
    }

    /// The player turned the renewal of the subscription off, or back on again: the period that was paid for is
    /// never touched, only whether the platform charges another one when it ends.
    public static void setAutoRenew(boolean enabled) {
        send(new FxAutoRenewPayload(enabled));
    }

    /// The player took the gift at one position of the lounge. The position addresses the list the last account
    /// snapshot carried, which is the only order both sides agree on.
    public static void claimGift(int index) {
        send(new FxLoungeClaimPayload(index));
    }

    /// The walkthrough is over, either finished or skipped: the panel stops offering it right away and the
    /// platform remembers it in the account.
    public static void markTutorialSeen() {
        FxClientState.INSTANCE.markTutorialSeen();
        send(FxTutorialSeenPayload.INSTANCE);
    }

    public static void deposit(Currency currency, long units) {
        if (units > 0L) {
            send(new FxDepositPayload(Currencies.index(currency), units));
        }
    }

    public static void withdraw(Currency currency, long units) {
        if (units > 0L) {
            send(new FxWithdrawPayload(Currencies.index(currency), units));
        }
    }

    /// Currency units a player carries as items, which is what the wallet of the server will count as well.
    public static long inventoryCount(Currency currency) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return 0L;
        }
        Inventory inventory = player.getInventory();
        Item item = FxCurrencies.item(currency);
        if (item == null) {
            return 0L;
        }
        long count = 0L;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }
}
