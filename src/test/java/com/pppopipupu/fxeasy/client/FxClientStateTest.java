package com.pppopipupu.fxeasy.client;

import module java.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;
import com.pppopipupu.fxeasy.market.CurrencyPair;
import com.pppopipupu.fxeasy.market.FeeDiscount;
import com.pppopipupu.fxeasy.market.FeeModel;
import com.pppopipupu.fxeasy.market.Side;
import com.pppopipupu.fxeasy.market.Timeframe;
import com.pppopipupu.fxeasy.net.FxAccountPayload;
import com.pppopipupu.fxeasy.net.FxEconomyPayload;
import com.pppopipupu.fxeasy.net.FxEventsPayload;
import com.pppopipupu.fxeasy.net.FxMarketPayload;
import com.pppopipupu.fxeasy.net.FxPositionPayload;
import com.pppopipupu.fxeasy.shop.Membership;
import com.pppopipupu.fxeasy.trade.Money;
import com.pppopipupu.fxeasy.trade.TradeEngine;
import net.minecraft.world.item.ItemStack;

/// The market list reads its sparkline and its change column from the price samples the mirror keeps, so the
/// ring has to sample on the tick grid, ignore packets inside a slot, wrap after a full turn, scope a window to
/// the ticks of its timeframe and restart when the feed missed samples. The intel mirrors answer to the same
/// discipline: a payload only moves them forward, and a market clock that went backwards is a new world.
///
/// A price the mirror holds is the platform's own quote of one currency, in the order the table of the session
/// names its currencies, so a rate is one price over another and every aggregate is a sum over those currencies
/// converted at those rates. Nothing in this model is measured in a unit of its own.
class FxClientStateTest {

    /// First pair of the table a client holds before the platform reports its own, which is dirt over gold.
    private static final CurrencyPair PAIR = Currencies.pairs().getFirst();
    private static final Currency DIRT = Currencies.all().getFirst();
    private static final Currency GOLD = PAIR.quote();
    private static final Timeframe TIMEFRAME = Timeframe.M1;

    @Test
    void windowStartsAtTheOldestSample() {
        FxClientState state = new FxClientState();
        state.applyMarket(market(0L, 1.0D));
        state.applyMarket(market(5L, 2.0D));
        state.applyMarket(market(10L, 4.0D));
        assertEquals(3, state.listingSamples());
        assertEquals(3, state.listingWindow(TIMEFRAME));
        assertEquals(1.0D / 512.0D, state.listingWindowSample(PAIR, TIMEFRAME, 0));
        assertEquals(4.0D / 512.0D, state.listingWindowSample(PAIR, TIMEFRAME, 2));
        assertEquals(300.0D, state.listingChange(PAIR, TIMEFRAME));
    }

    @Test
    void packetsInsideASlotKeepTheSampleTheyLandedOn() {
        FxClientState state = new FxClientState();
        state.applyMarket(market(0L, 1.0D));
        state.applyMarket(market(2L, 2.0D));
        state.applyMarket(market(4L, 3.0D));
        state.applyMarket(market(5L, 4.0D));
        assertEquals(2, state.listingSamples());
        assertEquals(1.0D / 512.0D, state.listingWindowSample(PAIR, TIMEFRAME, 0));
        assertEquals(4.0D / 512.0D, state.listingWindowSample(PAIR, TIMEFRAME, 1));
    }

    @Test
    void windowCoversTheTicksOfItsTimeframe() {
        FxClientState state = new FxClientState();
        for (int sample = 0; sample < FxClientState.LISTING_SAMPLES; sample++) {
            state.applyMarket(market(sample * FxClientState.LISTING_STEP_TICKS, sample + 1.0D));
        }
        int newest = FxClientState.LISTING_SAMPLES;
        int window = Timeframe.M1.ticks() / FxClientState.LISTING_STEP_TICKS;
        assertEquals(FxClientState.LISTING_SAMPLES, state.listingSamples());
        assertEquals(window, state.listingWindow(Timeframe.M1));
        assertEquals(FxClientState.LISTING_SAMPLES, state.listingWindow(Timeframe.ALL.getLast()));
        assertEquals((newest - window + 1) / 512.0D, state.listingWindowSample(PAIR, Timeframe.M1, 0));
        assertEquals(newest / 512.0D, state.listingWindowSample(PAIR, Timeframe.M1, window - 1));
    }

    @Test
    void ringDropsTheOldestSampleOnceItIsFull() {
        FxClientState state = new FxClientState();
        for (int sample = 0; sample < FxClientState.LISTING_SAMPLES + 10; sample++) {
            state.applyMarket(market(sample * FxClientState.LISTING_STEP_TICKS, sample + 1.0D));
        }
        int oldest = 11;
        assertEquals(FxClientState.LISTING_SAMPLES, state.listingSamples());
        assertEquals(oldest / 512.0D, state.listingWindowSample(PAIR, Timeframe.ALL.getLast(), 0));
        assertEquals((3610.0D / oldest - 1.0D) * 100.0D, state.listingChange(PAIR, Timeframe.ALL.getLast()));
    }

    @Test
    void feedGapRestartsTheWindow() {
        FxClientState state = new FxClientState();
        state.applyMarket(market(0L, 1.0D));
        state.applyMarket(market(5L, 2.0D));
        state.applyMarket(market(10_000L, 4.0D));
        assertEquals(1, state.listingSamples());
        assertEquals(0.0D, state.listingChange(PAIR, TIMEFRAME));
    }

    /// The mirror the panel and the feed share is built while the class is still initializing, so its arrays must
    /// not be sized from a static field of this class that is assigned later in the same pass. A ring of length
    /// zero crashes the first market tick and the first frame that reads the change column.
    @Test
    void sharedMirrorHasAUsableRing() {
        FxClientState.INSTANCE.applyMarket(market(0L, 1.0D));
        FxClientState.INSTANCE.applyMarket(market(5L, 2.0D));
        assertEquals(2, FxClientState.INSTANCE.listingSamples());
        assertEquals(2, FxClientState.INSTANCE.listingWindow(TIMEFRAME));
        assertEquals(100.0D, FxClientState.INSTANCE.listingChange(PAIR, TIMEFRAME));
    }

    /// The ring has to cover the longest window the terminal offers, which is the tick length of the last
    /// timeframe divided by the sampling step.
    @Test
    void ringCoversTheLongestTimeframe() {
        assertEquals(Timeframe.ALL.getLast().ticks() / FxClientState.LISTING_STEP_TICKS, FxClientState.LISTING_SAMPLES);
    }

    /// A tick is only readable against the table it was built for, and a client that takes the currency set of
    /// the server it joined can meet a tick of that set before the set itself. Such a vector belongs to another
    /// table, so it is dropped whole: neither the prices of the session nor the clock it carries may move, or
    /// the mirror would read a rate off prices of the wrong currencies.
    @Test
    void aTickOfAnotherCurrencyTableIsDropped() {
        FxClientState state = new FxClientState();
        state.applyMarket(market(0L, 2.0D));
        double heldBase = state.rate(DIRT, GOLD);
        state.applyMarket(new FxMarketPayload(new double[Currencies.count() + 1], 5L, 1.0D, null, 0L));
        assertEquals(heldBase, state.rate(DIRT, GOLD), 1.0E-12D, "a longer vector is not indexed either");
        assertEquals(0L, state.marketTick(), "and the clock of a dropped tick never lands");
        double[] shorter = new double[Currencies.count() - 1];
        Arrays.fill(shorter, 9.0D);
        state.applyMarket(new FxMarketPayload(shorter, 5L, 1.0D, null, 0L));
        assertEquals(heldBase, state.rate(DIRT, GOLD), 1.0E-12D, "a shorter vector must not replace the prices it covers");
        assertEquals(0L, state.marketTick());
    }

    /// Every rate is one price over another, so the ratios compose: what a currency is worth in a third is what
    /// it is worth in a second times what the second is worth in the third, and a currency is worth one of
    /// itself.
    @Test
    void ratesComposeAcrossTheTable() {
        FxClientState state = new FxClientState();
        state.applyMarket(market(0L, 2.0D));
        Currency first = Currencies.all().getFirst();
        Currency second = Currencies.all().get(1);
        Currency third = Currencies.all().get(2);
        assertEquals(1.0D, state.rate(first, first), 1.0E-12D);
        assertEquals(1.0D, state.rate(third, third), 1.0E-12D);
        assertEquals(state.rate(first, second) * state.rate(second, third), state.rate(first, third), 1.0E-12D);
        assertEquals(1.0D, state.rate(first, second) * state.rate(second, first), 1.0E-12D);
    }

    /// The ticket prices an order in the currency the player picked, so the margin it shows is the amount the
    /// server will lock. Before the player picks one it is the quote of the selected pair, and a currency that
    /// is not what the pair settles in backs less notional than its mid rate, because the platform takes its
    /// haircut and its conversion spread off it.
    @Test
    void theTicketBacksAnOrderWithTheChosenCurrency() {
        FxClientState state = new FxClientState();
        state.applyMarket(market(0L, 2.0D));
        assertEquals(PAIR.quote(), state.orderMargin(PAIR));
        assertEquals(TradeEngine.requiredMargin(64, 100, state.price(PAIR), 1.0D),
                state.requiredMargin(state.orderMargin(PAIR), 64, 100));
        state.setOrderMargin(DIRT);
        assertEquals(DIRT, state.orderMargin(PAIR));
        assertEquals(DIRT, state.orderMargin(Currencies.pairs().getLast()), "the choice holds until it is changed");
        assertEquals(TradeEngine.requiredMargin(64, 100, state.price(PAIR),
                FeeModel.collateralRate(PAIR, DIRT, state.rate(DIRT, PAIR.quote()), FeeDiscount.NONE)),
                state.requiredMargin(DIRT, 64, 100));
        assertTrue(state.requiredMargin(DIRT, 64, 100) > TradeEngine.requiredMargin(64, 100, state.price(PAIR), 1.0D),
                "collateral that is not what the pair settles in backs less notional than its mid value");
    }

    /// The two amounts the ticket prints one above the other are one order in one unit: the notional of an order
    /// in the quote currency of its pair is its margin at the leverage of the order. The ticket used to print a
    /// notional that had been rounded as a whole item count through the formatter for micro units, so a hundred
    /// dirt read as `0 GLD` beside a margin of `0.001953 GLD`.
    @Test
    void theNotionalOfAnOrderIsItsMarginAtItsLeverage() {
        FxClientState state = new FxClientState();
        state.applyMarket(market(0L, 1.0D));
        CurrencyPair pair = state.selectedPair();
        long notional = state.notional(pair, 100);
        long margin = state.requiredMargin(state.orderMargin(pair), 100, 100);
        assertTrue(notional > 0L, "a hundred items of a pair are worth something");
        assertTrue(margin > 0L);
        assertEquals(notional, margin * 100L, 100L, "the margin is the notional divided by the leverage");
    }

    /// The size slider of the ticket reaches exactly as far as the free margin of the currency the order locks
    /// backs at the leverage of the order: the largest size it offers is affordable, one item more is not, and
    /// the mirror never holds a size above the range however that size was set. A lower leverage narrows the
    /// range and pulls the size down with it, and a currency the account has nothing free of backs nothing at
    /// all, which is what leaves the slider empty.
    @Test
    void theSizeSliderNeverReachesPastWhatTheFreeMarginBacks() {
        FxClientState state = new FxClientState();
        state.applyMarket(market(0L, 1.0D));
        state.applyAccount(account());
        state.setOrderMargin(DIRT);
        state.setOrderLeverage(100);
        int maximum = state.maxOrderUnits();
        assertTrue(maximum > 0, "forty free dirt items back an order");
        assertTrue(state.requiredMargin(DIRT, maximum, 100) <= state.freeMargin(DIRT), "the whole range is affordable");
        assertTrue(state.requiredMargin(DIRT, maximum + 1, 100) > state.freeMargin(DIRT), "one item more is not");
        state.setOrderUnits(maximum + 1);
        assertEquals(maximum, state.orderUnits(), "a size above the range is pulled down to it");
        state.setOrderLeverage(1);
        state.setOrderUnits(state.orderUnits());
        assertTrue(state.orderUnits() < maximum, "a lower leverage narrows the range");
        assertEquals(state.maxOrderUnits(), state.orderUnits(), "and the size follows it down");
        state.setOrderUnits(-1);
        assertEquals(0, state.orderUnits(), "the range starts at nothing");
        state.setOrderMargin(Currencies.byId("diamond"));
        assertEquals(0, state.maxOrderUnits(), "a currency the account has nothing free of backs nothing");
        state.setOrderUnits(1);
        assertEquals(0, state.orderUnits(), "so the ticket cannot build an order in it");
    }

    /// The size slider reaches only as far as the exposure quota of the account still leaves it, which the
    /// platform reports per currency: a room narrower than the free margin is what bounds the range, a room on
    /// the quote bounds it through what one item of the base is worth, and a room that is already spent leaves
    /// the slider empty. A platform that limits no account reports no bound at all.
    @Test
    void theSizeSliderNeverReachesPastTheRoomTheQuotaLeaves() {
        FxClientState state = new FxClientState();
        state.applyMarket(market(0L, 1.0D));
        state.applyAccount(account(room(2_048.0D, 4.0D)));
        state.setOrderMargin(DIRT);
        state.setOrderLeverage(100);
        assertEquals(2_048, state.maxOrderUnits(), "the room bounds the range before the free margin does");
        state.setOrderUnits(2_049);
        assertEquals(2_048, state.orderUnits(), "a size past the room is pulled down to it");
        state.applyAccount(account(room(2_048.0D, 2.0D)));
        assertEquals(1_024, state.maxOrderUnits(), "half the room on the quote buys half the gold");
        state.applyAccount(account(room(0.0D, 4.0D)));
        assertEquals(0, state.maxOrderUnits(), "a room that is spent backs nothing");
        state.setOrderUnits(1);
        assertEquals(0, state.orderUnits(), "so the ticket cannot build an order in that pair");
        state.applyAccount(account(unlimited()));
        assertTrue(state.maxOrderUnits() > 2_048, "a platform that limits no account bounds nothing");
    }

    /// The plan of the subscription is what the ticket is bounded by: the ceiling of the plan is where the
    /// leverage stops, and every cost the panel quotes is the discount of that plan off the retail figure, so a
    /// client that was told about a plan quotes the prices that plan really trades at. The plan the platform
    /// reports is the only thing that moves the mirror, and an account snapshot without one is an account on
    /// retail pricing.
    @Test
    void thePlanOfTheAccountBoundsTheTicketAndDiscountsItsCosts() {
        FxClientState state = new FxClientState();
        state.applyMarket(market(0L, 1.0D));
        state.applyAccount(account(unlimited()));
        assertEquals(Membership.NONE, state.membership());
        assertEquals(Membership.NONE.maxLeverage(), state.maxOrderLeverage());
        assertEquals(FeeDiscount.NONE, state.feeDiscount());
        assertFalse(state.loungeOpen(), "the lounge comes with the top plan");
        state.setOrderLeverage(Membership.PRO.maxLeverage());
        assertEquals(Membership.NONE.maxLeverage(), state.orderLeverage(),
                "an account without a subscription stops at the retail ceiling");
        state.applyAccount(account(Membership.PRO, 4_000L, true, List.of(), unlimited()));
        assertEquals(Membership.PRO, state.membership());
        assertEquals(Membership.PRO.discount(), state.feeDiscount());
        assertEquals(4_000L, state.membershipTicksLeft());
        assertTrue(state.autoRenew());
        assertTrue(state.loungeOpen());
        state.setOrderLeverage(Membership.PRO.maxLeverage());
        assertEquals(Membership.PRO.maxLeverage(), state.orderLeverage(), "the plan raises the ceiling");
        assertTrue(state.spreadHalf() < FeeModel.BASE_SPREAD, "and quotes the spread the plan pays for");
        assertEquals(FeeModel.swapPerDay(PAIR, Side.BUY, Membership.PRO.discount()), state.swapPerDay(PAIR, Side.BUY),
                1.0E-15D);
    }

    /// A snapshot of an account that was opened and walked through the terminal in some earlier world, holding
    /// 64 dirt items of balance with 24 of them locked, and 2 gold items with one of them locked. The risk room
    /// is what the platform still leaves the account, [Double#POSITIVE_INFINITY] while it limits no account. The
    /// account carries no subscription, which is what a world that never saw the shop reports.
    private static FxAccountPayload account(double[] riskRoom, FxPositionPayload... positions) {
        return account(Membership.NONE, 0L, false, List.of(), riskRoom, positions);
    }

    /// The same account on a plan of the subscription: the plan by id, the market tick its period runs to,
    /// whether the platform charges another period when it ends and what its lounge is holding.
    private static FxAccountPayload account(Membership plan, long until, boolean autoRenew, List<ItemStack> lounge,
            double[] riskRoom, FxPositionPayload... positions) {
        long[] balances = new long[Currencies.count()];
        long[] freeMargin = new long[Currencies.count()];
        balances[Currencies.index(DIRT)] = 64L * Money.UNIT;
        freeMargin[Currencies.index(DIRT)] = 40L * Money.UNIT;
        balances[Currencies.index(GOLD)] = 2L * Money.UNIT;
        freeMargin[Currencies.index(GOLD)] = Money.UNIT;
        return new FxAccountPayload(balances, new long[Currencies.count()], freeMargin, riskRoom, List.of(positions),
                false, 0L, true, true, true, "kurumi", 21, new long[Currencies.count()], false, 0, 0L,
                plan.id(), until, autoRenew, 0L, lounge);
    }

    private static FxAccountPayload account(FxPositionPayload... positions) {
        return account(unlimited(), positions);
    }

    /// Room of a platform that limits the exposure of no account: every currency unlimited.
    private static double[] unlimited() {
        double[] riskRoom = new double[Currencies.count()];
        Arrays.fill(riskRoom, Double.POSITIVE_INFINITY);
        return riskRoom;
    }

    /// Room of a platform that limits the risk an account carries: that many items of dirt and of gold, and
    /// nothing said about the other currencies, which stay unlimited.
    private static double[] room(double dirt, double gold) {
        double[] riskRoom = unlimited();
        riskRoom[Currencies.index(DIRT)] = dirt;
        riskRoom[Currencies.index(GOLD)] = gold;
        return riskRoom;
    }

    /// The same account in a world whose platform has not handed the welcome capital over yet, with nothing on
    /// the balance either.
    private static FxAccountPayload unpaid() {
        long[] zeros = new long[Currencies.count()];
        return new FxAccountPayload(zeros.clone(), zeros.clone(), zeros.clone(), unlimited(), List.of(), false, 0L,
                true, true, false, "kurumi", 21, zeros.clone(), false, 0, 0L, Membership.NONE.id(), 0L, false, 0L,
                List.of());
    }

    /// One open position that settles in [currency] with an unrealised mark of [pnlMicro] micro units of it.
    private static FxPositionPayload position(Currency currency, long pnlMicro) {
        return new FxPositionPayload(1L, PAIR.index(), Side.BUY.ordinal(), 1, 100, 1.0D, 1.0D, 0L, 0L, pnlMicro, 0L,
                Currencies.index(currency));
    }

    /// The card that offers the welcome capital follows the account, not the notice: it is up for an account
    /// that has not been paid, it goes away for good once the payment is in the account, and a card that was put
    /// away without claiming the capital comes back the next time the terminal opens, which is a new card.
    @Test
    void theBonusCardOffersTheCapitalUntilThePlatformPaysIt() {
        FxClientState state = new FxClientState();
        FxWelcomeBonus card = new FxWelcomeBonus();
        assertFalse(card.active(state), "there is no card before the account is open");
        state.applyAccount(account());
        assertFalse(card.active(state), "an account that has been paid is offered nothing");
        state.reset();
        assertFalse(card.active(state), "a new world starts without the card of the previous one");
        state.applyAccount(unpaid());
        assertTrue(card.active(state), "an open account that carries no capital is offered the card");
        card.dismiss();
        assertFalse(card.active(state), "the card stays away for the rest of the session once it is put away");
        assertTrue(new FxWelcomeBonus().active(state), "and comes back the next time the terminal opens");
        state.applyAccount(account());
        assertFalse(new FxWelcomeBonus().active(state), "the payment keeps the card away for good");
    }

    /// The mirror describes one world and one player: entering another world drops the account flags with the rest
    /// of the panel, so an account opened in an earlier save cannot hide the opening in a world whose platform has
    /// never heard of it.
    @Test
    void resetDropsTheAccountOfThePreviousWorld() {
        FxClientState state = new FxClientState();
        state.applyMarket(market(0L, 1.0D));
        state.applyAccount(account());
        assertTrue(state.opened());
        assertTrue(state.tutorialSeen());
        assertEquals("kurumi", state.holder());
        state.reset();
        assertFalse(state.opened(), "a new world starts with an account that was never opened");
        assertFalse(state.tutorialSeen(), "and one that has never been walked through");
        assertEquals("", state.holder());
    }

    /// Equity is what the account is worth in the currency the panel quotes in: every balance plus the
    /// unrealised mark of its positions, each converted at the live rate. The used margin is every lock the
    /// same way. Cycling the display currency changes the unit and the number, never the per-currency amounts
    /// the account carries.
    @Test
    void theAggregatesConvertEveryCurrencyIntoTheDisplayOne() {
        FxClientState state = new FxClientState();
        state.applyMarket(market(0L, 2.0D));
        state.applyAccount(account(position(DIRT, 20L * Money.UNIT)));
        assertEquals(84.0D + 2.0D * state.rate(GOLD, DIRT), state.equityItems(DIRT), 1.0E-9D,
                "64 dirt of balance and a 20 dirt mark beside 2 gold");
        assertEquals(20.0D, state.unrealizedItems(DIRT), 1.0E-9D, "only the dirt position carries a mark");
        assertEquals(20.0D * state.rate(DIRT, GOLD), state.unrealizedItems(GOLD), 1.0E-9D);
        assertEquals(24.0D + state.rate(GOLD, DIRT), state.usedMarginItems(DIRT), 1.0E-9D, "24 dirt and 1 gold are locked");
        double dirtWorth = state.equityItems(DIRT);
        double goldWorth = 84.0D * state.rate(DIRT, GOLD) + 2.0D;
        assertEquals(goldWorth, state.equityItems(GOLD), 1.0E-9D);
        state.cycleBasis();
        assertEquals(GOLD, state.basis(), "the display currency advances along the table");
        assertEquals(goldWorth, state.equityItems(state.basis()), 1.0E-9D, "the same worth in the new unit");
        assertTrue(state.equityItems(state.basis()) != dirtWorth, "and the same worth reads another number");
        assertEquals(64L * Money.UNIT, state.balance(DIRT), "cycling the display currency never moves a balance");
        assertEquals(20L * Money.UNIT, state.unrealizedMicro(DIRT), "nor the mark, which stays in the currency it settles in");
    }

    /// The margin level of a currency is what it is worth over what it locks, its own balance and its own mark:
    /// a balance in one currency never covers the margin of a position that settles in another. The account is
    /// only as strong as its thinnest currency, and one that locks nothing is not at risk at all.
    @Test
    void theMarginLevelIsTheThinnestCurrencyOfTheAccount() {
        FxClientState state = new FxClientState();
        state.applyMarket(market(0L, 2.0D));
        assertEquals(Double.POSITIVE_INFINITY, state.marginLevel(), "an account that locks nothing cannot be at risk");
        assertEquals(Double.POSITIVE_INFINITY, state.marginLevel(DIRT));
        state.applyAccount(unpaid());
        assertEquals(Double.POSITIVE_INFINITY, state.marginLevel(), "nor an open account that holds nothing");
        state.applyAccount(account(position(DIRT, 20L * Money.UNIT)));
        assertEquals(84.0D / 24.0D, state.marginLevel(DIRT), 1.0E-9D, "84 items of worth over 24 locked");
        assertEquals(2.0D, state.marginLevel(GOLD), 1.0E-9D, "2 items of worth over one locked");
        assertEquals(2.0D, state.marginLevel(), "the thinnest currency of the account decides");
    }

    private static FxMarketPayload market(long marketTick, double basePrice) {
        double[] prices = new double[Currencies.count()];
        for (Currency currency : Currencies.all()) {
            prices[Currencies.index(currency)] = currency.initialPrice();
        }
        prices[Currencies.index(DIRT)] = basePrice;
        return new FxMarketPayload(prices, marketTick, 1.0D, null, 0L);
    }

    /// An economy report with nothing in it but the revision and the clock it was taken at, carrying one entry
    /// per currency of the table.
    private static FxEconomyPayload economy(long revision, long marketTick) {
        double[] flows = new double[Currencies.count()];
        int[] pressure = new int[Currencies.count()];
        return new FxEconomyPayload(FxEconomyPayload.STATUS_FRESH, revision, marketTick, marketTick, 1L, 10,
                flows.clone(), flows.clone(), pressure, flows.clone(), flows.clone(), flows.clone(),
                new int[FxEconomyPayload.COVERAGE_ENTRIES], new int[0], new long[0]);
    }

    private static FxEventsPayload events(long revision, long asOfMarketTick, FxEventsPayload.Event... messages) {
        return new FxEventsPayload(revision, asOfMarketTick, List.of(messages));
    }

    private static FxEventsPayload.Event event(String rootId, long revision) {
        double[] values = new double[Currencies.count()];
        return new FxEventsPayload.Event("event-" + revision, rootId, 0, 0, 0, revision, 100L, 200L,
                values, values.clone(), "fxeasy.event.test.title", "fxeasy.event.test.body", false);
    }

    /// A repeat of the revision the mirror already holds is idempotent, and a report that lost the race against a
    /// newer one is dropped: neither may replace what the panel is reading.
    @Test
    void anEconomyReportOnlyEverMovesTheMirrorForward() {
        FxClientState state = new FxClientState();
        assertNull(state.economy());
        FxEconomyPayload held = economy(4L, 100L);
        state.applyEconomy(held);
        assertSame(held, state.economy());
        state.applyEconomy(economy(4L, 120L));
        assertSame(held, state.economy(), "a repeated revision must not disturb the mirror");
        state.applyEconomy(economy(3L, 140L));
        assertSame(held, state.economy(), "a stale report must not walk the mirror backwards");
        FxEconomyPayload newer = economy(5L, 160L);
        state.applyEconomy(newer);
        assertSame(newer, state.economy());
    }

    @Test
    void anEventFeedOnlyEverMovesTheMirrorForward() {
        FxClientState state = new FxClientState();
        FxEventsPayload held = events(2L, 100L, event("raid", 1L));
        state.applyEvents(held);
        assertSame(held, state.events());
        state.applyEvents(events(2L, 110L, event("raid", 1L)));
        assertSame(held, state.events(), "a repeated revision must not disturb the mirror");
        state.applyEvents(events(1L, 120L, event("raid", 1L)));
        assertSame(held, state.events(), "a stale feed must not walk the mirror backwards");
        FxEventsPayload newer = events(3L, 130L, event("raid", 1L), event("gold", 2L));
        state.applyEvents(newer);
        assertSame(newer, state.events());
        assertEquals(2, state.eventCount());
    }

    /// A market clock that went backwards is another world: the report, the feed and the unread watermark of the
    /// previous save must not survive into it.
    @Test
    void aMarketClockThatGoesBackwardsStartsANewWorld() {
        FxClientState state = new FxClientState();
        state.applyMarket(market(1_000L, 2.0D));
        state.applyEconomy(economy(7L, 1_000L));
        state.applyEvents(events(9L, 1_000L, event("raid", 3L)));
        assertEquals(1, state.eventCount());
        assertEquals(1, state.unreadEvents());
        state.applyMarket(market(5L, 2.0D));
        assertNull(state.economy(), "a new world must not inherit the report of the previous save");
        assertNull(state.events());
        assertEquals(0, state.eventCount());
        assertEquals(0, state.unreadEvents());
        assertEquals(5L, state.marketTick());
    }

    /// The unread count follows the watermark the panel records when the messages are on screen, so an update of
    /// a message the player already read counts again and an unchanged feed does not.
    @Test
    void unreadMessagesFollowTheReadWatermark() {
        FxClientState state = new FxClientState();
        state.applyEvents(events(2L, 100L, event("raid", 1L), event("gold", 2L)));
        assertEquals(2, state.unreadEvents());
        state.markEventsRead();
        assertEquals(0, state.unreadEvents());
        state.applyEvents(events(3L, 120L, event("raid", 1L), event("gold", 4L)));
        assertEquals(1, state.unreadEvents(), "only the message newer than the watermark is unread");
    }

    /// The intel overlay reports the change of a currency over the ticks of its timeframe, which the mirror keeps
    /// on the same grid as the listing.
    @Test
    void aCurrencyChangeComesFromTheSameGridAsTheListing() {
        FxClientState state = new FxClientState();
        state.applyMarket(market(0L, 1.0D));
        state.applyMarket(market(5L, 2.0D));
        state.applyMarket(market(10L, 4.0D));
        assertEquals(300.0D, state.valuationChange(DIRT, TIMEFRAME));
        assertEquals(300.0D, state.valuationChange(DIRT, Timeframe.ALL.getLast()));
    }
}
