package com.pppopipupu.fxeasy.shop;

import module java.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;
import com.pppopipupu.fxeasy.market.MarketEngine;
import com.pppopipupu.fxeasy.trade.Account;
import com.pppopipupu.fxeasy.trade.Money;
import com.pppopipupu.fxeasy.trade.TradeEngine;
import net.minecraft.resources.Identifier;

/// What the shop charges and when it lets a plan go. A plan is bought out of the free margin of the currency it
/// is priced in, and only there: a world that does not trade that currency cannot sell it, an account below the
/// plan on offer cannot buy its way down, and a price the balance does not cover buys nothing at all. A period
/// that ends is charged again out of the same currency while the account still renews and the balance covers it,
/// and lapses the moment either stops - the plan, never the gifts it already delivered.
class ShopEngineTest {

    private static final Currency DIRT = Currencies.byId("dirt");

    /// A lounge that restocks one gift a thousand market ticks and holds three of them: the clock, the capacity
    /// and the pool are the file's to decide, so a test can decide them too.
    private static final LoungeFile.Pool POOL = new LoungeFile.Pool(1_000L, 3,
            List.of(new LoungeFile.Entry(Identifier.parse("minecraft:diamond"), 2, 1, Optional.empty())));

    @Test
    void buyingChargesThePriceOfThePlanAndRunsAPeriod() {
        Account account = opened();
        account.addBalance(DIRT, 10_000L * Money.UNIT);
        ShopEngine.BuyResult.Bought bought = assertInstanceOf(ShopEngine.BuyResult.Bought.class,
                buy(account, Membership.GO, 1_000L));
        assertFalse(bought.extended());
        assertEquals(Membership.GO, account.membership());
        assertEquals(1_000L + Membership.PERIOD_TICKS, account.membershipUntil());
        assertEquals(1_000L + Membership.PERIOD_TICKS, bought.untilTick());
        assertEquals(8_888L * Money.UNIT, bought.priceMicro());
        assertEquals(1_112L * Money.UNIT, account.balance(DIRT), "the price left the balance");
        assertTrue(account.autoRenew(), "a plan is bought renewing itself");
        assertEquals(Membership.GO.maxLeverage(), account.maxLeverage());
        assertEquals(15L, Membership.PERIOD_TICKS / MarketEngine.TICKS_PER_DAY,
                "a period is fifteen game days, whatever the plan");
    }

    /// Buying the plan the account is already on is another period rather than a second plan: the ceiling does
    /// not move and the period the account holds is the one it extends.
    @Test
    void buyingThePlanTheAccountIsOnExtendsItsPeriod() {
        Account account = opened();
        account.addBalance(DIRT, 20_000L * Money.UNIT);
        buy(account, Membership.GO, 0L);
        long until = account.membershipUntil();
        long balance = account.balance(DIRT);
        ShopEngine.BuyResult.Bought bought = assertInstanceOf(ShopEngine.BuyResult.Bought.class,
                buy(account, Membership.GO, 500L));
        assertTrue(bought.extended());
        assertEquals(Membership.GO, account.membership());
        assertEquals(until + Membership.PERIOD_TICKS, account.membershipUntil());
        assertEquals(balance - 8_888L * Money.UNIT, account.balance(DIRT));
    }

    /// A higher plan is the one purchase that replaces what the account holds: the ceiling moves the moment it is
    /// paid for, and the period it runs is a fresh one rather than the rest of the plan it leaves behind. Only the
    /// currency of the plan pays for it, so an account that holds the cheaper plan still has to hold the dearer
    /// price.
    @Test
    void upgradingToAHigherPlanStartsAFreshPeriod() {
        Account account = opened();
        account.addBalance(DIRT, 10_000L * Money.UNIT);
        buy(account, Membership.GO, 0L);
        Currency diamond = Currencies.byId(Membership.PLUS.priceCurrencyId());
        account.addBalance(diamond, 100L * Money.UNIT);
        ShopEngine.BuyResult.Bought bought = assertInstanceOf(ShopEngine.BuyResult.Bought.class,
                buy(account, Membership.PLUS, 5_000L));
        assertFalse(bought.extended(), "a higher plan is a new plan and not an extension of the old one");
        assertEquals(Membership.PLUS, account.membership());
        assertEquals(5_000L + Membership.PERIOD_TICKS, account.membershipUntil(),
                "the period runs from the upgrade rather than from the plan it replaced");
        assertEquals(100L * Money.UNIT - 64L * Money.UNIT, account.balance(diamond));
        assertEquals(Membership.PLUS.maxLeverage(), account.maxLeverage());
        assertTrue(account.autoRenew());
    }

    @Test
    void aPurchaseTheAccountCannotAffordBuysNothing() {
        Account account = opened();
        account.addBalance(DIRT, 8_887L * Money.UNIT);
        ShopEngine.BuyResult.Rejected rejected = assertInstanceOf(ShopEngine.BuyResult.Rejected.class,
                buy(account, Membership.GO, 0L));
        assertEquals(ShopEngine.REJECT_FUNDS, rejected.reason());
        assertEquals(Membership.NONE, account.membership());
        assertEquals(8_887L * Money.UNIT, account.balance(DIRT), "a refused purchase charges nothing");
    }

    /// A world only sells the plans it can be paid for: the currency a plan is priced in is data of the world,
    /// and a platform that does not trade it reports that instead of inventing a rate.
    @Test
    void aWorldThatDoesNotTradeThePriceCurrencySellsNoPlan() {
        List<Currency> previous = Currencies.all();
        try {
            Currencies.set(List.of(Currencies.byId("dirt"), Currencies.byId("gold")));
            Account account = opened();
            account.addBalance(Currencies.byId("dirt"), 1_000_000L * Money.UNIT);
            ShopEngine.BuyResult.Rejected rejected = assertInstanceOf(ShopEngine.BuyResult.Rejected.class,
                    buy(account, Membership.PRO, 0L));
            assertEquals(ShopEngine.REJECT_CURRENCY, rejected.reason());
            assertEquals(Membership.NONE, account.membership());
        } finally {
            Currencies.set(previous);
        }
    }

    /// A tier is worth the same however it is settled: paying in a currency the plan is not priced in converts
    /// the price of the catalogue at the rate the market quotes and adds the conversion spread of the platform,
    /// which is what a trader pays to turn one currency into another. The plan is bought either way, and the
    /// currency the catalogue prices it in is never touched.
    @Test
    void aPaymentInAnotherCurrencyChargesTheConvertedPrice() {
        Account account = opened();
        Currency gold = Currencies.byId("gold");
        account.addBalance(gold, 20_000L * Money.UNIT);
        account.addBalance(DIRT, 1_000L * Money.UNIT);
        ShopEngine.BuyResult.Bought bought = assertInstanceOf(ShopEngine.BuyResult.Bought.class,
                ShopEngine.buy(account, Membership.GO, gold, 2.0D, 0L));
        assertEquals(gold, bought.paying(), "the price left the currency the player paid in");
        assertEquals(17_784_888_000L, bought.priceMicro(),
                "8,888 dirt at two gold the dirt, plus the half of the conversion spread the trader pays");
        assertEquals(20_000L * Money.UNIT - 17_784_888_000L, account.balance(gold));
        assertEquals(1_000L * Money.UNIT, account.balance(DIRT), "the currency of the catalogue is untouched");
        assertEquals(Membership.GO, account.membership());
        assertEquals(Membership.PERIOD_TICKS, account.membershipUntil());
    }

    /// The conversion of a payment is a cost of the platform like every other, so the plan the account already
    /// trades under scales it down: an account on Pro pays a third of the spread of one without a plan, and a
    /// plan that takes nothing off the conversion - Plus - pays the retail one.
    @Test
    void aPaymentInAnotherCurrencyPaysTheSpreadOfThePlanInForce() {
        Account account = opened();
        Currency diamond = Currencies.byId("diamond");
        account.addBalance(diamond, 1_000L * Money.UNIT);
        account.addBalance(DIRT, 1_000_000L * Money.UNIT);
        buy(account, Membership.PLUS, 0L);
        long before = account.balance(DIRT);
        ShopEngine.BuyResult.Bought plus = assertInstanceOf(ShopEngine.BuyResult.Bought.class,
                ShopEngine.buy(account, Membership.PLUS, DIRT, 0.5D, 0L));
        assertEquals(32_016_000L, plus.priceMicro(),
                "Plus takes nothing off the conversion, so 64 diamond at half a dirt pays the retail spread");
        assertEquals(before - 32_016_000L, account.balance(DIRT));
        assertEquals(Membership.PLUS, account.membership());
        buy(account, Membership.PRO, 0L);
        long upgraded = account.balance(DIRT);
        ShopEngine.BuyResult.Bought pro = assertInstanceOf(ShopEngine.BuyResult.Bought.class,
                ShopEngine.buy(account, Membership.PRO, DIRT, 0.5D, 0L));
        assertEquals(333_056_610L, pro.priceMicro(),
                "Pro takes two thirds off the conversion, so 666 diamond at half a dirt pays a third of it");
        assertEquals(upgraded - 333_056_610L, account.balance(DIRT));
        assertEquals(Membership.PRO, account.membership());
    }

    /// A currency this world does not trade cannot settle anything, which is what a client that names one is
    /// told rather than charged in a currency the platform does not know.
    @Test
    void aPaymentInACurrencyTheWorldDoesNotTradeBuysNothing() {
        Account account = opened();
        account.addBalance(DIRT, 1_000_000L * Money.UNIT);
        ShopEngine.BuyResult.Rejected rejected = assertInstanceOf(ShopEngine.BuyResult.Rejected.class,
                ShopEngine.buy(account, Membership.GO, null, 1.0D, 0L));
        assertEquals(ShopEngine.REJECT_CURRENCY, rejected.reason());
        assertEquals(Membership.NONE, account.membership());
        assertEquals(1_000_000L * Money.UNIT, account.balance(DIRT), "a refused payment charges nothing");
    }

    /// Only the balance of the currency the player pays in settles the payment, whatever the account holds
    /// elsewhere: a platform that reached into another balance would be choosing the currency for the buyer.
    @Test
    void onlyTheBalanceOfThePayingCurrencySettlesThePayment() {
        Account account = opened();
        Currency gold = Currencies.byId("gold");
        account.addBalance(DIRT, 1_000_000L * Money.UNIT);
        ShopEngine.BuyResult.Rejected rejected = assertInstanceOf(ShopEngine.BuyResult.Rejected.class,
                ShopEngine.buy(account, Membership.GO, gold, 2.0D, 0L));
        assertEquals(ShopEngine.REJECT_FUNDS, rejected.reason(),
                "a million dirt does not settle a payment the player set to gold");
        assertEquals(1_000_000L * Money.UNIT, account.balance(DIRT), "a refused payment charges nothing");
        assertEquals(Membership.NONE, account.membership());
    }

    @Test
    void aPlanBelowTheOneTheAccountIsOnCannotBeBought() {
        Account account = opened();
        account.addBalance(Currencies.byId("diamond"), 1_000L * Money.UNIT);
        assertInstanceOf(ShopEngine.BuyResult.Bought.class, buy(account, Membership.PRO, 0L));
        long balance = account.balance(Currencies.byId("diamond"));
        ShopEngine.BuyResult.Rejected rejected = assertInstanceOf(ShopEngine.BuyResult.Rejected.class,
                buy(account, Membership.GO, 0L));
        assertEquals(ShopEngine.REJECT_DOWNGRADE, rejected.reason());
        assertEquals(Membership.PRO, account.membership());
        assertEquals(balance, account.balance(Currencies.byId("diamond")));
    }

    /// The opening cannot be skipped by a modified client: an account the platform has not opened buys nothing.
    @Test
    void anUnopenedAccountBuysNothing() {
        Account account = new Account(UUID.randomUUID());
        account.addBalance(DIRT, 1_000_000L * Money.UNIT);
        ShopEngine.BuyResult.Rejected rejected = assertInstanceOf(ShopEngine.BuyResult.Rejected.class,
                buy(account, Membership.GO, 0L));
        assertEquals(TradeEngine.REJECT_ACCOUNT, rejected.reason());
        assertEquals(Membership.NONE, account.membership());
    }

    @Test
    void theTierWithoutASubscriptionIsNotForSale() {
        Account account = opened();
        account.addBalance(DIRT, 1_000_000L * Money.UNIT);
        ShopEngine.BuyResult.Rejected rejected = assertInstanceOf(ShopEngine.BuyResult.Rejected.class,
                buy(account, Membership.NONE, 0L));
        assertEquals(ShopEngine.REJECT_CURRENCY, rejected.reason());
        assertEquals(Membership.NONE, account.membership());
    }

    /// A plan nobody sells comes back as the tier without one rather than as the plan of some other ordinal,
    /// which is what keeps a save written by a build that knew a tier this one does not from turning into a
    /// subscription the account never bought.
    @Test
    void anUnknownPlanIdComesBackAsNoPlan() {
        assertEquals(Membership.NONE, Membership.byId("tier_this_build_never_sold"));
        assertEquals(Membership.PRO, Membership.byId(Membership.PRO.id()));
    }

    /// A period that ends is charged again out of the currency of the plan while the account renews itself and
    /// covers it; the clock restarts from the tick of the settlement, so a server that was down for a week owes
    /// one period and not seven.
    @Test
    void aPeriodThatEndsIsChargedAgain() {
        Account account = opened();
        account.addBalance(DIRT, 40_000L * Money.UNIT);
        buy(account, Membership.GO, 0L);
        long until = account.membershipUntil();
        long balance = account.balance(DIRT);
        assertNull(ShopEngine.settle(account, until - 1L), "a period that still runs settles nothing");
        ShopEngine.Renewal.Renewed renewed = assertInstanceOf(ShopEngine.Renewal.Renewed.class,
                ShopEngine.settle(account, until));
        assertEquals(Membership.GO, renewed.plan());
        assertEquals(Membership.GO, account.membership());
        assertEquals(until + Membership.PERIOD_TICKS, account.membershipUntil());
        assertEquals(balance - 8_888L * Money.UNIT, account.balance(DIRT));
        assertInstanceOf(ShopEngine.Renewal.Renewed.class,
                ShopEngine.settle(account, until + 4L * Membership.PERIOD_TICKS));
        assertEquals(until + 4L * Membership.PERIOD_TICKS + Membership.PERIOD_TICKS, account.membershipUntil(),
                "a settlement the world slept through costs one period and not four");
    }

    /// Cancelling stops the renewal and never takes back what was paid: the period the account holds runs to its
    /// end, and the plan lapses when it does.
    @Test
    void aCancelledPlanRunsItsPeriodOutAndLapses() {
        Account account = opened();
        account.addBalance(DIRT, 20_000L * Money.UNIT);
        buy(account, Membership.GO, 0L);
        long until = account.membershipUntil();
        account.setAutoRenew(false);
        assertNull(ShopEngine.settle(account, until - 1L), "a cancelled plan still holds its period");
        assertEquals(Membership.GO, account.membership());
        assertInstanceOf(ShopEngine.Renewal.Expired.class, ShopEngine.settle(account, until));
        assertEquals(Membership.NONE, account.membership());
        assertEquals(0L, account.loungeNext(), "a lapsed plan stops the clock of the lounge");
        assertNull(ShopEngine.settle(account, until + 10L * Membership.PERIOD_TICKS),
                "an account without a plan settles nothing");
    }

    @Test
    void aRenewalTheBalanceCannotCoverLapsesThePlan() {
        Account account = opened();
        account.addBalance(DIRT, 8_888L * Money.UNIT);
        buy(account, Membership.GO, 0L);
        long until = account.membershipUntil();
        assertEquals(0L, account.balance(DIRT));
        ShopEngine.Renewal.Expired expired = assertInstanceOf(ShopEngine.Renewal.Expired.class,
                ShopEngine.settle(account, until));
        assertEquals(Membership.GO, expired.plan());
        assertEquals(Membership.NONE, account.membership());
    }

    /// A plan that carries no lounge delivers nothing and does not start its clock, which is what keeps a gift
    /// from being back filled when an account upgrades: the clock starts when the plan that carries the lounge
    /// does.
    @Test
    void aPlanWithoutALoungeDeliversNothing() {
        Account account = opened();
        account.addBalance(DIRT, 20_000L * Money.UNIT);
        buy(account, Membership.GO, 0L);
        assertFalse(ShopEngine.deliver(account, 50_000L, POOL));
        assertEquals(0L, account.loungeNext());
        assertTrue(account.loungeGifts().isEmpty());
    }

    /// The lounge hands one gift out per refresh period that passed, waits instead of growing past its capacity,
    /// and reads its clock from the market rather than from a login: a lounge that was left alone for four
    /// periods holds what three of them delivered.
    @Test
    void theLoungeDeliversOneGiftAPeriodUpToItsCapacity() {
        Account account = loungeAccount();
        assertFalse(ShopEngine.deliver(account, 0L, POOL), "the first delivery only starts the clock");
        assertEquals(1_000L, account.loungeNext());
        assertFalse(ShopEngine.deliver(account, 999L, POOL));
        assertTrue(ShopEngine.deliver(account, 1_000L, POOL));
        assertEquals(1, account.loungeGifts().size());
        assertEquals(2_000L, account.loungeNext());
        assertTrue(ShopEngine.deliver(account, 4_000L, POOL));
        assertEquals(3, account.loungeGifts().size(), "the pool holds three gifts and no more");
        assertFalse(ShopEngine.deliver(account, 9_000L, POOL), "a full lounge waits for the next period");
        assertEquals(3, account.loungeGifts().size());
    }

    /// Every gift is drawn from its own stream, seeded with the account and the period it was due at, so a
    /// reload hands over what it would have handed over before the save. Nothing the lounge does touches the
    /// generators of the market, so a player cannot re-roll the news by collecting a present.
    @Test
    void theGiftsOfAnAccountAreTheSameAfterAReload() {
        Account before = loungeAccount();
        Account after = new Account(before.owner());
        after.register("kurumi", 21);
        after.startMembership(Membership.PRO, 0L, false);
        ShopEngine.deliver(after, 0L, POOL);
        ShopEngine.deliver(before, 0L, POOL);
        for (long tick = 1_000L; tick <= 3_000L; tick += 1_000L) {
            assertTrue(ShopEngine.deliver(before, tick, POOL));
            assertTrue(ShopEngine.deliver(after, tick, POOL));
        }
        assertEquals(before.loungeGifts(), after.loungeGifts(),
                "the same account and the same periods draw the same gifts");
        assertEquals(3, before.loungeGifts().size());
    }

    /// What a plan already delivered stays where it is: the gifts are the account's, and a plan that lapsed
    /// leaves them claimable, which is what makes a subscription worth what was paid for it.
    @Test
    void giftsSurviveTheLapseOfThePlan() {
        Account account = loungeAccount();
        ShopEngine.deliver(account, 0L, POOL);
        for (long tick = 1_000L; tick <= 2_000L; tick += 1_000L) {
            ShopEngine.deliver(account, tick, POOL);
        }
        assertEquals(2, account.loungeGifts().size());
        account.endMembership();
        assertFalse(ShopEngine.deliver(account, 9_000L, POOL));
        assertEquals(2, account.loungeGifts().size(), "a lapsed plan delivers nothing and takes nothing back");
        LoungeGift claimed = account.takeGift(0);
        assertNotNull(claimed, "a gift the lounge holds can still be claimed");
        assertEquals(1, account.loungeGifts().size());
    }

    /// Buys a plan the way the checkout does when the player settles it in the currency the catalogue prices it
    /// in, which is par and costs no conversion. The payments in another currency call the engine themselves,
    /// because that is where the rate and the spread of the conversion show up.
    private static ShopEngine.BuyResult buy(Account account, Membership plan, long marketTick) {
        return ShopEngine.buy(account, plan, Currencies.byId(plan.priceCurrencyId()), 1.0D, marketTick);
    }

    private static Account opened() {
        Account account = new Account(UUID.randomUUID());
        account.register("kurumi", 21);
        return account;
    }

    /// An opened account on the top plan, which is the one that carries the lounge.
    private static Account loungeAccount() {
        Account account = opened();
        account.startMembership(Membership.PRO, Membership.PERIOD_TICKS, false);
        return account;
    }
}
