package com.pppopipupu.fxeasy.client;

import module java.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.pppopipupu.fxeasy.client.panel.FxUi;
import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;
import com.pppopipupu.fxeasy.market.FeeDiscount;
import com.pppopipupu.fxeasy.net.FxAccountPayload;
import com.pppopipupu.fxeasy.net.FxMarketPayload;
import com.pppopipupu.fxeasy.shop.Membership;
import com.pppopipupu.fxeasy.trade.Money;
import net.minecraft.client.gui.navigation.ScreenRectangle;

/// The checkout is the card the shop page opens instead of buying a tier, and what it offers is the account's
/// own money: only the currencies it holds a balance in can settle the payment, the one the catalogue prices the
/// plan in is the one it starts on when the account has it, and the figures of the receipt are the price of the
/// catalogue converted at the rate the mirror holds plus the conversion spread of the platform, which the plan
/// the account trades on scales down. A card that drew a row the list does not show, or one that offered a
/// currency the account does not have, would let a scripted click pay with money that is not there.
class FxCheckoutTest {

    private static final Currency DIRT = Currencies.byId("dirt");
    private static final Currency GOLD = Currencies.byId("gold");
    private static final Currency DIAMOND = Currencies.byId("diamond");

    /// An account holding that many items of the three currencies of the test, free to spend all of them, with
    /// the prices of the session behind it: one dirt is worth two gold and half a diamond.
    private static FxClientState state(long dirt, long gold, long diamond) {
        long[] balances = new long[Currencies.count()];
        balances[Currencies.index(DIRT)] = dirt;
        balances[Currencies.index(GOLD)] = gold;
        balances[Currencies.index(DIAMOND)] = diamond;
        double[] prices = new double[Currencies.count()];
        prices[Currencies.index(DIRT)] = 2.0D;
        prices[Currencies.index(GOLD)] = 1.0D;
        prices[Currencies.index(DIAMOND)] = 4.0D;
        double[] unlimited = new double[Currencies.count()];
        Arrays.fill(unlimited, Double.POSITIVE_INFINITY);
        FxClientState state = new FxClientState();
        state.applyMarket(new FxMarketPayload(prices, 0L, 1.0D, null, 0L));
        state.applyAccount(new FxAccountPayload(balances, new long[Currencies.count()], balances.clone(), unlimited,
                List.of(), false, 0L, true, true, true, "kurumi", 21, new long[Currencies.count()], false, 0,
                Money.UNIT, Membership.NONE.id(), 0L, true, 0L, List.of()));
        return state;
    }

    /// A payment is offered in the currencies the account holds and in no others, in the order of the table of
    /// the world, because a currency the player does not have cannot settle anything.
    @Test
    void onlyTheCurrenciesTheAccountHoldsAreOffered() {
        assertEquals(List.of(DIRT, DIAMOND), FxCheckout.payable(state(100L * Money.UNIT, 0L, 1_000L * Money.UNIT)),
                "gold is not held and is not offered");
        assertTrue(FxCheckout.payable(state(0L, 0L, 0L)).isEmpty(), "an account holding nothing offers nothing");
    }

    /// The payment starts on the currency the plan is priced in when the account holds it, because settling in
    /// it costs no conversion, and otherwise on the currency the account holds the most of - or on nothing at
    /// all while it holds nothing.
    @Test
    void thePaymentStartsOnTheCurrencyOfTheCatalogue() {
        FxClientState holding = state(100L * Money.UNIT, 0L, 1_000L * Money.UNIT);
        assertEquals(DIRT, FxCheckout.defaultPaying(Membership.GO, holding), "Go is priced in dirt");
        assertEquals(DIAMOND, FxCheckout.defaultPaying(Membership.PLUS, holding), "Plus is priced in diamond");
        FxClientState without = state(50L * Money.UNIT, 500L * Money.UNIT, 0L);
        assertEquals(GOLD, FxCheckout.defaultPaying(Membership.PLUS, without),
                "a plan whose currency the account does not hold falls back to the balance it holds most of");
        assertNull(FxCheckout.defaultPaying(Membership.GO, state(0L, 0L, 0L)), "nothing to pay with is nothing");
    }

    /// A payment in the currency the catalogue prices the plan in costs exactly the price of the catalogue, and
    /// a payment in another currency costs that price converted at the rate of the market plus the conversion
    /// spread of the platform - which is what the receipt writes as its converted amount and its fee.
    @Test
    void aPaymentQuotesTheConvertedPriceAndItsSpread() {
        FxClientState state = state(100L * Money.UNIT, 1_000L * Money.UNIT, 0L);
        FxCheckout.Quote par = FxCheckout.quote(Membership.GO, DIRT, state.rate(DIRT, DIRT), FeeDiscount.NONE);
        assertEquals(8_888L * Money.UNIT, par.totalMicro(), "settling in dirt costs the price of the catalogue");
        assertEquals(par.totalMicro(), par.convertedMicro(), "and converts nothing");
        assertEquals(0L, par.feeMicro(), "so the conversion is free");
        FxCheckout.Quote converted = FxCheckout.quote(Membership.GO, GOLD, state.rate(DIRT, GOLD),
                FeeDiscount.NONE);
        assertEquals(17_776L * Money.UNIT, converted.convertedMicro(),
                "8,888 dirt is 17,776 gold at two gold the dirt");
        assertEquals(8_888_000L, converted.feeMicro(),
                "the platform takes half of its conversion spread, which is 0.05% of 17,776 gold");
        assertEquals(17_784_888_000L, converted.totalMicro());
    }

    /// The conversion is a cost of the platform like every other, so the plan the account trades on scales it:
    /// an account on Pro pays the third of the spread an account without a plan pays.
    @Test
    void theSpreadOfTheConversionFollowsThePlanOfTheAccount() {
        double rate = 2.0D;
        long retail = FxCheckout.quote(Membership.GO, GOLD, rate, FeeDiscount.NONE).feeMicro();
        long pro = FxCheckout.quote(Membership.GO, GOLD, rate, Membership.PRO.discount()).feeMicro();
        assertTrue(pro < retail, "a plan takes part of the conversion off");
        assertEquals(3_021_920L, pro, "Pro carries 0.66 off the conversion, so a third of 8,888,000 is left");
    }

    /// The card of the smallest panel the terminal draws still holds its receipt, a row of currencies and its
    /// buttons, and every cell it draws stands inside it.
    @Test
    void theCardOfTheSmallestPanelHoldsItsReceiptItsRowsAndItsButtons() {
        int width = 427;
        int height = 240;
        ScreenRectangle card = FxCheckout.card(width, height);
        FxCheckout.Buttons buttons = FxCheckout.buttons(card);
        inside("the card", card, FxUi.rect(0, 0, width, height));
        inside("the close cell", FxCheckout.closeArea(width, height), card);
        inside("the list", FxCheckout.listArea(width, height), card);
        inside("the pay cell", buttons.pay(), card);
        inside("the cancel cell", buttons.cancel(), card);
        assertTrue(buttons.cancel().right() <= buttons.pay().left(), "the buttons stand side by side");
        assertTrue(FxCheckout.rows(width, height) >= 1, "the smallest card still shows a row of currencies");
        assertTrue(FxCheckout.listArea(width, height).bottom() <= buttons.pay().top(),
                "the list stands above the buttons");
    }

    /// The rows the card draws are the cells a scripted click can reach, and a row under the window of the list
    /// - or an index past the end of what the account holds - is no cell of this frame at all.
    @Test
    void theRowsTheCardShowsAreTheCellsItDraws() {
        int width = 640;
        int height = 360;
        FxCheckout checkout = new FxCheckout();
        int rows = FxCheckout.rows(width, height);
        ScreenRectangle list = FxCheckout.listArea(width, height);
        for (int index = 0; index < rows; index++) {
            ScreenRectangle row = checkout.rowArea(index, rows + 3, width, height);
            assertNotNull(row, "row " + index + " is inside the window of the list");
            inside("the cell of row " + index, row, list);
        }
        assertNull(checkout.rowArea(rows, rows + 3, width, height), "a row under the window is not drawn");
        assertNull(checkout.rowArea(0, 0, width, height), "an account holding nothing draws no row to pay with");
        assertNull(checkout.rowArea(7, 3, width, height), "an index past the end of the list is no currency");
    }

    private static void inside(String what, ScreenRectangle inner, ScreenRectangle outer) {
        assertTrue(inner.left() >= outer.left() && inner.top() >= outer.top() && inner.right() <= outer.right()
                && inner.bottom() <= outer.bottom(),
                what + " spans " + inner.left() + "," + inner.top() + " to " + inner.right() + "," + inner.bottom()
                        + ", outside " + outer.left() + "," + outer.top() + " to " + outer.right() + ","
                        + outer.bottom());
    }
}
