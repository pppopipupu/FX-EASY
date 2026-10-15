package com.pppopipupu.fxeasy.trade;

import module java.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.pppopipupu.fxeasy.FxConfig;
import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;
import com.pppopipupu.fxeasy.market.CurrencyPair;
import com.pppopipupu.fxeasy.market.FeeDiscount;
import com.pppopipupu.fxeasy.market.FeeModel;
import com.pppopipupu.fxeasy.market.MarketDriverFrame;
import com.pppopipupu.fxeasy.market.MarketEngine;
import com.pppopipupu.fxeasy.market.Side;
import com.pppopipupu.fxeasy.shop.Membership;

class TradeEngineTest {

    private static final Currency DIRT = Currencies.byId("dirt");
    private static final Currency GOLD = Currencies.byId("gold");
    private static final Currency DIAMOND = Currencies.byId("diamond");
    private static final CurrencyPair PAIR = CurrencyPair.of(DIRT, GOLD);
    /// Order size the tests trade in, items of the base currency of the pair: one lot is one item, so a notional
    /// of 2048 dirt is four gold at the rate the world opens on.
    private static final int SIZE = 2_048;

    @Test
    void depositTakesItemsAndWithdrawReturnsThem() {
        Account account = new Account(UUID.randomUUID());
        account.register("kurumi", 21);
        FakeWallet wallet = new FakeWallet().with(DIAMOND, 9L);
        assertInstanceOf(TradeEngine.DepositResult.Done.class, TradeEngine.deposit(account, DIAMOND, 9L, wallet));
        assertEquals(9L * Money.UNIT, account.balance(DIAMOND));
        assertEquals(0L, wallet.count(DIAMOND));
        assertInstanceOf(TradeEngine.DepositResult.Rejected.class, TradeEngine.deposit(account, DIAMOND, 1L, wallet));
        assertInstanceOf(TradeEngine.WithdrawResult.Done.class, TradeEngine.withdraw(account, DIAMOND, 4L, wallet));
        assertEquals(5L * Money.UNIT, account.balance(DIAMOND));
        assertEquals(4L, wallet.count(DIAMOND));
        assertInstanceOf(TradeEngine.WithdrawResult.Rejected.class, TradeEngine.withdraw(account, DIAMOND, 6L, wallet));
    }

    @Test
    void welcomeCapitalIsPaidOncePerAccount() {
        Account account = new Account(UUID.randomUUID());
        Currency welcome = FxConfig.get().welcomeCurrency();
        long items = FxConfig.get().welcomeItems();
        assertTrue(account.payWelcomeBonus(welcome, items), "the first open pays the welcome capital");
        assertEquals(items * Money.UNIT, account.balance(welcome));
        assertFalse(account.payWelcomeBonus(welcome, items), "opening the terminal again pays nothing");
        assertEquals(items * Money.UNIT, account.balance(welcome));
        assertTrue(account.freeMargin(welcome) > 0L, "the welcome capital backs orders");
    }

    @Test
    void marginScalesInverselyWithLeverage() {
        MarketEngine market = new MarketEngine(2L);
        Account account = openedAccount(GOLD, 1L);
        double price = market.price(PAIR);
        double collateralRate = FeeModel.collateralRate(PAIR, GOLD, market.rate(GOLD, PAIR.quote()), FeeDiscount.NONE);
        long marginAtHundred = TradeEngine.requiredMargin(SIZE, 100, price, collateralRate);
        assertEquals(Money.round(SIZE * price / 100.0D), marginAtHundred, "the margin is the notional over the leverage");
        assertEquals(Money.round(SIZE * price / 10.0D), TradeEngine.requiredMargin(SIZE, 10, price, collateralRate));
        assertEquals(marginAtHundred * 10L, TradeEngine.requiredMargin(SIZE, 10, price, collateralRate));
        assertInstanceOf(TradeEngine.OpenResult.Opened.class, TradeEngine.open(account, PAIR, Side.BUY, GOLD, SIZE, 100, market));
        assertEquals(marginAtHundred, account.lockedMargin(GOLD));
        assertEquals(Money.UNIT - marginAtHundred, account.freeMargin(GOLD));
    }

    @Test
    void ordersAreRejectedWithoutMarginOrWithInvalidParameters() {
        MarketEngine market = new MarketEngine(2L);
        Account account = openedAccount(GOLD, 1L);
        assertRejected(TradeEngine.open(openedAccount(GOLD, 0L), PAIR, Side.BUY, GOLD, SIZE, 100, market), TradeEngine.REJECT_MARGIN);
        assertRejected(TradeEngine.open(account, PAIR, Side.BUY, GOLD, 0, 100, market), TradeEngine.REJECT_SIZE);
        assertRejected(TradeEngine.open(account, PAIR, Side.BUY, GOLD, SIZE, 0, market), TradeEngine.REJECT_LEVERAGE);
        assertRejected(TradeEngine.open(account, PAIR, Side.BUY, GOLD, SIZE, Membership.NONE.maxLeverage() + 1, market),
                TradeEngine.REJECT_LEVERAGE);
        assertInstanceOf(TradeEngine.OpenResult.Opened.class, TradeEngine.open(openedAccount(GOLD, 1L), PAIR, Side.BUY,
                GOLD, SIZE, Membership.NONE.maxLeverage(), market));
        assertEquals(0, account.positionCount());
    }

    @Test
    void positionCountIsCapped() {
        MarketEngine market = new MarketEngine(3L);
        Account account = openedAccount(GOLD, 4L);
        for (int i = 0; i < TradeEngine.MAX_POSITIONS; i++) {
            assertInstanceOf(TradeEngine.OpenResult.Opened.class, TradeEngine.open(account, PAIR, Side.BUY, GOLD, SIZE, 100, market));
        }
        assertRejected(TradeEngine.open(account, PAIR, Side.BUY, GOLD, SIZE, 100, market), TradeEngine.REJECT_POSITIONS);
    }

    @Test
    void openingPaysTheSpreadAndTheRoundTripCostsItTwice() {
        MarketEngine market = new MarketEngine(17L);
        Account account = openedAccount(GOLD, 1L);
        TradeEngine.OpenResult.Opened opened = open(account, Side.BUY, market);
        double mid = market.price(PAIR);
        double halfSpread = FeeModel.halfSpread(market.regime(PAIR), FeeDiscount.NONE);
        assertTrue(opened.position().openPrice() >= mid * (1.0D + halfSpread) - 1.0E-12D);
        assertTrue(opened.position().openPrice() <= mid * (1.0D + halfSpread) * (1.0D + FeeModel.slippageMagnitude(market.regime(PAIR), FeeDiscount.NONE)) + 1.0E-12D);
        TradeEngine.CloseResult.Closed closed = close(account, opened, market);
        assertTrue(closed.pnlMicro() < 0L, "a position without price movement must lose the spread");
        assertEquals(0, account.positionCount());
        assertEquals(Money.units(account.balance(GOLD)), account.equityItems(GOLD, market), 1.0E-6D);
    }

    @Test
    void shortsMirrorLongs() {
        MarketEngine market = new MarketEngine(19L);
        Account account = openedAccount(GOLD, 2L);
        TradeEngine.OpenResult.Opened shortPosition = open(account, Side.SELL, market);
        double mid = market.price(PAIR);
        assertTrue(shortPosition.position().openPrice() <= mid * (1.0D - FeeModel.halfSpread(market.regime(PAIR), FeeDiscount.NONE)));
        double logPrice = market.logPrice(DIRT);
        market.restore(DIRT, logPrice + Math.log(1.01D), market.absReturn(DIRT));
        assertTrue(shortPosition.position().unrealizedQuoteItems(market, FeeDiscount.NONE) < 0.0D);
        market.restore(DIRT, logPrice + Math.log(0.99D), market.absReturn(DIRT));
        assertTrue(shortPosition.position().unrealizedQuoteItems(market, FeeDiscount.NONE) > 0.0D);
        double ratio = shortPosition.position().unrealizedQuoteItems(market, FeeDiscount.NONE) / (SIZE * mid);
        assertEquals(0.01D, ratio, 0.0015D, "a short gains about the move on its notional");
    }

    @Test
    void marginCallPrecedesLiquidation() {
        MarketEngine market = new MarketEngine(29L);
        double collateralRate = FeeModel.collateralRate(PAIR, GOLD, market.rate(GOLD, PAIR.quote()), FeeDiscount.NONE);
        long margin = TradeEngine.requiredMargin(SIZE, 100, market.price(PAIR), collateralRate);
        Account account = new Account(UUID.randomUUID());
        account.register("kurumi", 21);
        account.addBalance(GOLD, margin);
        TradeEngine.OpenResult.Opened opened = open(account, Side.BUY, market);
        assertTrue(account.marginLevel(market) <= 1.0D);
        assertTrue(account.marginLevel(market) > 0.9D, "opening only costs the spread, level was " + account.marginLevel(market));
        double logPrice = market.logPrice(DIRT);
        market.restore(DIRT, logPrice + Math.log(0.997D), market.absReturn(DIRT));
        assertTrue(account.marginLevel(market) > TradeEngine.LIQUIDATION_LEVEL);
        assertTrue(account.marginLevel(market) < TradeEngine.MARGIN_CALL_LEVEL);
        assertTrue(TradeEngine.marginCall(account, market));
        assertFalse(TradeEngine.needsLiquidation(account, market));
        market.restore(DIRT, logPrice + Math.log(0.994D), market.absReturn(DIRT));
        assertTrue(TradeEngine.needsLiquidation(account, market));
        assertEquals(1, TradeEngine.closeAll(account, market).size());
        assertEquals(0, account.positionCount());
        assertEquals(Double.POSITIVE_INFINITY, account.marginLevel(market));
        assertTrue(account.balance(GOLD) < margin, "the loss exceeds the locked margin and becomes debt");
        assertTrue(opened.position().swapMicro() == 0L);
    }

    /// The worst case the platform promises to survive: a 100x position backed by half the deposit meets a day of
    /// industrial supply. The check runs every tick the way the server runs it, so the liquidation happens while
    /// the account still has equity and never turns the loss into debt the trader cannot pay.
    @Test
    void aDeepAdverseMoveIsLiquidatedWithoutDebt() {
        MarketEngine market = new MarketEngine(37L);
        Account account = new Account(UUID.randomUUID());
        account.register("kurumi", 21);
        account.addBalance(DIRT, 20_000L * Money.UNIT);
        CurrencyPair pair = CurrencyPair.of(DIRT, GOLD);
        TradeEngine.OpenResult result = TradeEngine.open(account, pair, Side.SELL, DIRT, SIZE * 480, 100, market);
        TradeEngine.OpenResult.Opened opened = assertInstanceOf(TradeEngine.OpenResult.Opened.class, result);
        long margin = opened.position().marginMicro();
        double[] fundamental = new double[Currencies.count()];
        int goldIndex = Currencies.index(GOLD);
        boolean liquidated = false;
        for (long tick = 1L; tick <= MarketEngine.TICKS_PER_DAY && !liquidated; tick++) {
            fundamental[goldIndex] = -1.2E-5D;
            market.advance(tick, fundamental, MarketDriverFrame.calm());
            if (TradeEngine.needsLiquidation(account, market)) {
                assertEquals(1, TradeEngine.closeAll(account, market).size());
                liquidated = true;
            }
        }
        assertTrue(liquidated, "a 25% adverse move has to liquidate a 100x position");
        assertTrue(account.balance(DIRT) >= 0L,
                "a liquidation may not leave the trader in debt: " + account.balance(DIRT));
        assertTrue(account.balance(DIRT) >= margin / 2L - 1_500L * Money.UNIT,
                "the liquidation fired past the equity floor and the cost of closing: "
                        + account.balance(DIRT) + " left of a margin of " + margin);
    }

    @Test
    void swapIsSettledOnEveryOpenPosition() {
        MarketEngine market = new MarketEngine(31L);
        Account account = openedAccount(GOLD, 1L);
        TradeEngine.OpenResult.Opened opened = open(account, Side.BUY, market);
        long before = account.balance(GOLD);
        assertTrue(TradeEngine.settleSwap(account, market));
        long swap = opened.position().swapMicro();
        assertEquals(before - swap, account.balance(GOLD));
        assertTrue(swap > 0L, "long dirt pays the gold rate");
        double notionalQuoteItems = SIZE * market.price(PAIR);
        assertEquals(swap, Money.round(FeeModel.toItems(notionalQuoteItems * FeeModel.swapPerDay(PAIR, Side.BUY, FeeDiscount.NONE),
                PAIR, GOLD, market.rate(GOLD, PAIR.quote()), FeeDiscount.NONE)), 1.0D);

        Account carried = openedAccount(GOLD, 1L);
        TradeEngine.OpenResult.Opened credit = open(carried, Side.SELL, market);
        long beforeCredit = carried.balance(GOLD);
        assertTrue(TradeEngine.settleSwap(carried, market));
        assertTrue(credit.position().swapMicro() < 0L, "short dirt is long the gold rate and is paid for it");
        assertEquals(beforeCredit - credit.position().swapMicro(), carried.balance(GOLD),
                "the credit lands in the balance of the currency the position locks");
    }

    @Test
    void marginLocksTheCurrencyTheOrderNames() {
        MarketEngine market = new MarketEngine(43L);
        Account account = new Account(UUID.randomUUID());
        account.register("kurumi", 21);
        account.addBalance(DIRT, 40L * Money.UNIT);
        account.addBalance(GOLD, Money.UNIT);
        double dirtCollateral = FeeModel.collateralRate(PAIR, DIRT, market.rate(DIRT, PAIR.quote()), FeeDiscount.NONE);
        long dirtMargin = TradeEngine.requiredMargin(SIZE, 100, market.price(PAIR), dirtCollateral);
        TradeEngine.OpenResult.Opened opened = assertInstanceOf(TradeEngine.OpenResult.Opened.class,
                TradeEngine.open(account, PAIR, Side.BUY, DIRT, SIZE, 100, market));
        assertEquals(DIRT, opened.position().marginCurrency());
        assertEquals(dirtMargin, opened.position().marginMicro());
        assertEquals(dirtMargin, account.lockedMargin(DIRT));
        assertEquals(0L, account.lockedMargin(GOLD), "the quote of the pair stays free");
        assertEquals(40L * Money.UNIT - dirtMargin, account.freeMargin(DIRT));
        assertEquals(Money.UNIT, account.freeMargin(GOLD));
        assertEquals(Money.units(dirtMargin) * market.rate(DIRT, GOLD), account.usedMarginItems(GOLD, market), 1.0E-6D);

        assertEquals(TradeEngine.REJECT_MARGIN, assertInstanceOf(TradeEngine.WithdrawResult.Rejected.class,
                TradeEngine.withdraw(account, DIRT, 25L, new FakeWallet())).reason());
        assertInstanceOf(TradeEngine.WithdrawResult.Done.class,
                TradeEngine.withdraw(account, DIRT, 19L, new FakeWallet()));
        assertInstanceOf(TradeEngine.WithdrawResult.Done.class,
                TradeEngine.withdraw(account, GOLD, 1L, new FakeWallet()));
    }

    @Test
    void aCurrencyWithoutFreeMarginCannotBackAnOrder() {
        MarketEngine market = new MarketEngine(47L);
        Account account = openedAccount(GOLD, 1L);
        assertRejected(TradeEngine.open(account, PAIR, Side.BUY, DIRT, SIZE, 100, market), TradeEngine.REJECT_MARGIN);
        assertEquals(0, account.positionCount());
        assertInstanceOf(TradeEngine.OpenResult.Opened.class,
                TradeEngine.open(account, PAIR, Side.BUY, GOLD, SIZE, 100, market));
    }

    @Test
    void profitAndSwapSettleInTheCurrencyThatBackedTheOrder() {
        MarketEngine market = new MarketEngine(53L);
        Account account = new Account(UUID.randomUUID());
        account.register("kurumi", 21);
        account.addBalance(DIRT, 100L * Money.UNIT);
        TradeEngine.OpenResult.Opened opened = assertInstanceOf(TradeEngine.OpenResult.Opened.class,
                TradeEngine.open(account, PAIR, Side.BUY, DIRT, SIZE, 100, market));
        Position position = opened.position();
        double before = market.price(PAIR);
        market.restore(DIRT, market.logPrice(DIRT) + Math.log(1.5D), market.absReturn(DIRT));
        double after = market.price(PAIR);
        long beforeSwap = account.balance(DIRT);
        assertTrue(TradeEngine.settleSwap(account, market));
        long swap = position.swapMicro();
        assertTrue(swap > 0L, "a position that holds the lower yielding currency pays the swap");
        assertEquals(beforeSwap - swap, account.balance(DIRT));
        assertEquals(0L, account.balance(GOLD), "a swap never touches the quote of the pair");
        long balanceBeforeClose = account.balance(DIRT);
        TradeEngine.CloseResult.Closed closed = assertInstanceOf(TradeEngine.CloseResult.Closed.class,
                TradeEngine.close(account, position, market));
        assertEquals(balanceBeforeClose + closed.pnlMicro(), account.balance(DIRT));
        assertEquals(closed.pnlMicro(), account.realized(DIRT));
        assertEquals(0L, account.realized(GOLD));

        double expectedItems = SIZE * 0.5D * before / after;
        assertTrue(closed.pnlMicro() > 0L, "the long gained on a market that moved up");
        assertEquals(expectedItems, Money.units(closed.pnlMicro()), expectedItems * 0.02D,
                "the profit is the rate move on the notional, converted into the dirt that backed it");
    }

    @Test
    void equityAndUsedMarginAddUpAcrossMarginCurrencies() {
        MarketEngine market = new MarketEngine(59L);
        Account account = new Account(UUID.randomUUID());
        account.register("kurumi", 21);
        account.addBalance(DIRT, 30L * Money.UNIT);
        account.addBalance(GOLD, Money.UNIT);
        long dirtMargin = TradeEngine.requiredMargin(SIZE, 100, market.price(PAIR),
                FeeModel.collateralRate(PAIR, DIRT, market.rate(DIRT, PAIR.quote()), FeeDiscount.NONE));
        long goldMargin = TradeEngine.requiredMargin(SIZE, 100, market.price(PAIR),
                FeeModel.collateralRate(PAIR, GOLD, market.rate(GOLD, PAIR.quote()), FeeDiscount.NONE));
        assertInstanceOf(TradeEngine.OpenResult.Opened.class,
                TradeEngine.open(account, PAIR, Side.BUY, DIRT, SIZE, 100, market));
        assertInstanceOf(TradeEngine.OpenResult.Opened.class,
                TradeEngine.open(account, PAIR, Side.SELL, GOLD, SIZE, 100, market));
        assertEquals(dirtMargin, account.lockedMargin(DIRT));
        assertEquals(goldMargin, account.lockedMargin(GOLD));
        double expected = Money.units(dirtMargin) * market.rate(DIRT, GOLD)
                + Money.units(goldMargin) * market.rate(GOLD, GOLD);
        assertEquals(expected, account.usedMarginItems(GOLD, market), 1.0E-6D);
        double balances = 30.0D * market.rate(DIRT, GOLD) + Money.units(Money.UNIT) * market.rate(GOLD, GOLD);
        assertTrue(account.equityItems(GOLD, market) < balances, "the round trip costs the two spreads");
        assertTrue(account.equityItems(GOLD, market) > balances * 0.99D);
        assertEquals(Math.min(account.marginLevel(DIRT, market), account.marginLevel(GOLD, market)),
                account.marginLevel(market), 1.0E-12D);
    }

    /// A position is long the base of its pair and short the quote, whatever currency backs its margin, and the
    /// platform measures the absolute risk of each side rather than the net of them.
    @Test
    void exposureIsLongTheBaseAndShortTheQuote() {
        MarketEngine market = new MarketEngine(61L);
        Account account = openedAccount(GOLD, 2L);
        TradeEngine.OpenResult.Opened opened = open(account, Side.BUY, market);
        double rate = market.price(PAIR);
        assertEquals(SIZE, account.signedExposureItems(DIRT, market), 1.0E-9D);
        assertEquals(-SIZE * rate, account.signedExposureItems(GOLD, market), 1.0E-9D);
        assertEquals(0.0D, account.signedExposureItems(DIAMOND, market), 1.0E-9D);
        assertEquals(SIZE, account.absoluteExposureItems(DIRT, market), 1.0E-9D);
        assertEquals(SIZE * rate, account.absoluteExposureItems(GOLD, market), 1.0E-9D);

        TradeEngine.OpenResult.Opened opposite = assertInstanceOf(TradeEngine.OpenResult.Opened.class,
                TradeEngine.open(account, PAIR, Side.SELL, GOLD, SIZE, 100, market));
        assertEquals(0.0D, account.signedExposureItems(DIRT, market), 1.0E-9D);
        assertEquals(2.0D * SIZE, account.absoluteExposureItems(DIRT, market), 1.0E-9D,
                "an opposite position does not cancel the risk of the one already open");
        assertTrue(opposite.position().signedBaseUnits() < 0.0D);
        assertTrue(opened.position().signedBaseUnits() > 0.0D);
    }

    /// The risk limit only ever refuses new risk, and it measures the risk in items of the currency it is carried
    /// in: the size of an order on the base is its own item count whatever the market does, so a crash of that
    /// currency cannot unlock more room.
    @Test
    void riskLimitOnlyRefusesNewRisk() {
        MarketEngine market = new MarketEngine(61L);
        double rate = market.price(PAIR);
        assertEquals(SIZE, TradeEngine.addedExposureItems(SIZE, PAIR, DIRT, rate), 1.0E-9D);
        assertEquals(SIZE * rate, TradeEngine.addedExposureItems(SIZE, PAIR, GOLD, rate), 1.0E-9D);
        assertEquals(0.0D, TradeEngine.addedExposureItems(SIZE, PAIR, DIAMOND, rate), 1.0E-9D);
        assertEquals(-SIZE, TradeEngine.addedExposureItems(-SIZE, PAIR, DIRT, rate), 1.0E-9D,
                "an order that closes the same size adds the same risk with the opposite sign");
        assertEquals(-SIZE * rate, TradeEngine.addedExposureItems(-SIZE, PAIR, GOLD, rate), 1.0E-9D);
        assertEquals(TradeEngine.addedExposureItems(SIZE, PAIR, DIRT, rate),
                TradeEngine.addedExposureItems(SIZE, PAIR, DIRT, rate * 0.1D), 0.0D,
                "the base risk is the order size, whatever the market has done to the rate");
        assertTrue(TradeEngine.withinLimit(0.0D, SIZE, 2.0D * SIZE));
        assertTrue(TradeEngine.withinLimit(2.0D * SIZE, 0.0D, 2.0D * SIZE), "closing is never refused");
        assertFalse(TradeEngine.withinLimit(1.5D * SIZE, SIZE, 2.0D * SIZE));
    }

    /// What a risk limit still leaves an account is the limit less the absolute risk the open positions carry,
    /// which is the room the terminal builds the size of its next order in: it shrinks by the size of every
    /// position, a hedge spends it like any other order, a limit already spent leaves nothing, and a platform
    /// that limits no account leaves the whole room.
    @Test
    void theRiskRoomIsTheLimitLessTheRiskAlreadyCarried() {
        MarketEngine market = new MarketEngine(61L);
        double rate = market.price(PAIR);
        double limit = 10.0D * SIZE;
        Account account = openedAccount(GOLD, 2L);
        assertEquals(limit, account.riskRoom(DIRT, market, limit), 1.0E-9D, "nothing open leaves the whole room");
        assertEquals(limit, account.riskRoom(GOLD, market, limit), 1.0E-9D);
        open(account, Side.BUY, market);
        assertEquals(limit - SIZE, account.riskRoom(DIRT, market, limit), 1.0E-9D);
        assertEquals(limit - SIZE * rate, account.riskRoom(GOLD, market, limit), 1.0E-9D);
        open(account, Side.SELL, market);
        assertEquals(limit - 2.0D * SIZE, account.riskRoom(DIRT, market, limit), 1.0E-9D,
                "the opposite position spends the same room");
        assertEquals(0.0D, account.riskRoom(DIRT, market, SIZE), 1.0E-9D, "a limit already spent leaves nothing");
        assertEquals(Double.POSITIVE_INFINITY, account.riskRoom(DIRT, market, Double.POSITIVE_INFINITY),
                "a platform that limits no account leaves every room open");
    }

    @Test
    void anUnopenedAccountCannotTrade() {
        MarketEngine market = new MarketEngine(41L);
        Account account = new Account(UUID.randomUUID());
        account.addBalance(GOLD, Money.UNIT);
        assertRejected(TradeEngine.open(account, PAIR, Side.BUY, GOLD, SIZE, 100, market), TradeEngine.REJECT_ACCOUNT);
        assertEquals(TradeEngine.REJECT_ACCOUNT, assertInstanceOf(TradeEngine.DepositResult.Rejected.class,
                TradeEngine.deposit(account, DIAMOND, 1L, new FakeWallet().with(DIAMOND, 1L))).reason());
        assertEquals(TradeEngine.REJECT_ACCOUNT, assertInstanceOf(TradeEngine.WithdrawResult.Rejected.class,
                TradeEngine.withdraw(account, GOLD, 1L, new FakeWallet())).reason());
        assertEquals(0, account.positionCount());
        assertEquals(Money.UNIT, account.balance(GOLD));
    }

    /// A deposit or a withdrawal of a currency whose definition names no item cannot be moved through an
    /// inventory, so the platform refuses it before it ever looks at a wallet.
    @Test
    void aCurrencyWithoutAnItemCannotBeDepositedOrWithdrawn() {
        List<Currency> previous = Currencies.all();
        try {
            Currency backed = new Currency("backed", "BCK", "Backed", Map.of(), Optional.of(DIRT.item().orElseThrow()),
                    1.0D, 0.01D, 0.0D, 1L, 100.0D, 0.1D, 0.1D);
            Currency unbacked = new Currency("unbacked", "UNB", "Unbacked", Map.of(), Optional.empty(), 1.0D, 0.01D,
                    0.0D, 1L, 100.0D, 0.1D, 0.1D);
            Currencies.set(List.of(backed, unbacked));
            Account account = new Account(UUID.randomUUID());
            account.register("kurumi", 21);
            assertEquals(TradeEngine.REJECT_BACKING, assertInstanceOf(TradeEngine.DepositResult.Rejected.class,
                    TradeEngine.deposit(account, unbacked, 1L, new FakeWallet().with(unbacked, 1L))).reason());
            assertEquals(TradeEngine.REJECT_BACKING, assertInstanceOf(TradeEngine.WithdrawResult.Rejected.class,
                    TradeEngine.withdraw(account, unbacked, 1L, new FakeWallet())).reason());
            assertEquals(0L, account.balance(unbacked));
        } finally {
            Currencies.set(previous);
        }
    }

    @Test
    void crossCollateralBacksLessNotionalThanTheSettlementCurrency() {
        MarketEngine market = new MarketEngine(61L);
        double price = market.price(PAIR);
        double dirtRate = market.rate(DIRT, PAIR.quote());
        double goldRate = market.rate(GOLD, PAIR.quote());
        assertEquals(goldRate, FeeModel.collateralRate(PAIR, GOLD, goldRate, FeeDiscount.NONE), 0.0D, "the settlement currency backs at par");
        double dirtCollateral = FeeModel.collateralRate(PAIR, DIRT, dirtRate, FeeDiscount.NONE);
        assertTrue(dirtCollateral < dirtRate, "the haircut and the conversion make dirt back less than its mid rate");
        long goldMargin = TradeEngine.requiredMargin(SIZE, 100, price, FeeModel.collateralRate(PAIR, GOLD, goldRate, FeeDiscount.NONE));
        assertEquals(Money.round(SIZE * price / 100.0D), goldMargin);
        long dirtMargin = TradeEngine.requiredMargin(SIZE, 100, price, dirtCollateral);
        assertTrue(dirtMargin > goldMargin, "dirt has to lock more items than the settlement currency does");
    }

    /// A debt is charged [TradeEngine#DEBT_RATE] of what is owed every [TradeEngine#DEBT_PERIOD_TICKS], the
    /// charge is added to the debt and the next period is charged on that grown amount. A period that is not up
    /// charges nothing, and a gap that spans several periods is caught up one period per tick.
    @Test
    void debtIsChargedInterestEveryThreeDays() {
        Account account = new Account(UUID.randomUUID());
        account.register("kurumi", 21);
        account.addBalance(GOLD, -4L * Money.UNIT);
        long start = 5L;
        long period = TradeEngine.DEBT_PERIOD_TICKS;
        assertTrue(account.settleDebt(start).isEmpty(), "a fresh debt is recorded rather than charged");
        assertEquals(start, account.debtSince(GOLD));
        assertEquals(start + period, account.debtDue(GOLD));
        assertTrue(account.settleDebt(start + period - 1L).isEmpty(), "a period that is not up charges nothing");
        assertEquals(4L * Money.UNIT, account.debt(GOLD));

        List<Account.Interest> charges = account.settleDebt(start + period);
        assertEquals(1, charges.size());
        Account.Interest charge = charges.getFirst();
        assertEquals(GOLD, charge.currency());
        assertEquals(Money.round(4.0D * TradeEngine.DEBT_RATE), charge.interestMicro());
        assertEquals(-4L * Money.UNIT - charge.interestMicro(), account.balance(GOLD));
        assertEquals(account.debt(GOLD), charge.debtMicro());
        assertTrue(account.settleDebt(start + period).isEmpty(), "the charge opened the next period");

        List<Account.Interest> second = account.settleDebt(start + 2L * period);
        assertEquals(1, second.size());
        assertEquals(Money.round(Money.units(charge.debtMicro()) * TradeEngine.DEBT_RATE), second.getFirst().interestMicro(),
                1.0D, "the second period is charged on the debt the first one grew");
        assertTrue(account.settleDebt(start + 2L * period).isEmpty());
        assertEquals(1, account.settleDebt(start + 4L * period).size(), "a skipped period is charged late");
        assertEquals(1, account.settleDebt(start + 4L * period).size(), "and the one after it too");
        assertEquals(0, account.settleDebt(start + 4L * period).size());
    }

    /// The credit record is the age of the debt rather than the number of charges: a debt that is still owed past
    /// ten game days blacklists the account, which may then not open anything new, and repaying it in full clears
    /// the record and lets the account trade again. Closing, depositing and withdrawing stay allowed throughout,
    /// because they are the only way a debt ever gets paid.
    @Test
    void aDebtOlderThanTenDaysBlacklistsTheCredit() {
        MarketEngine market = new MarketEngine(67L);
        Account account = new Account(UUID.randomUUID());
        account.register("kurumi", 21);
        account.addBalance(GOLD, -Money.UNIT);
        long start = 5L;
        long limit = TradeEngine.DEBT_BLACKLIST_TICKS;
        account.settleDebt(start);
        assertEquals(start, account.debtSince(GOLD));
        assertFalse(account.blacklisted(start + limit), "ten days of debt is not yet over the limit");
        assertTrue(account.blacklisted(start + limit + 1L));

        market.restoreClock(start + limit + 1L);
        account.addBalance(GOLD, 10L * Money.UNIT);
        assertRejected(TradeEngine.open(account, PAIR, Side.BUY, GOLD, SIZE, 100, market), TradeEngine.REJECT_CREDIT);
        assertEquals(0, account.positionCount());
        assertInstanceOf(TradeEngine.DepositResult.Done.class,
                TradeEngine.deposit(account, DIAMOND, 1L, new FakeWallet().with(DIAMOND, 1L)),
                "a blacklisted account still has to be able to pay its debt");

        account.addBalance(GOLD, -10L * Money.UNIT);
        account.settleDebt(start + limit + 1L);
        assertTrue(account.blacklisted(start + limit + 1L), "a debt that is still owed stays on the record");
        account.addBalance(GOLD, 20L * Money.UNIT);
        account.settleDebt(start + limit + 1L);
        assertEquals(0L, account.debtSince(GOLD));
        assertFalse(account.blacklisted(start + limit + 1L), "repaying the debt clears the record");
        assertInstanceOf(TradeEngine.OpenResult.Opened.class,
                TradeEngine.open(account, PAIR, Side.BUY, GOLD, SIZE, 100, market));
    }

    private static Account openedAccount(Currency currency, long units) {
        Account account = new Account(UUID.randomUUID());
        account.register("kurumi", 21);
        account.addBalance(currency, units * Money.UNIT);
        return account;
    }

    private static TradeEngine.OpenResult.Opened open(Account account, Side side, MarketEngine market) {
        return assertInstanceOf(TradeEngine.OpenResult.Opened.class,
                TradeEngine.open(account, PAIR, side, GOLD, SIZE, 100, market));
    }

    private static TradeEngine.CloseResult.Closed close(Account account, TradeEngine.OpenResult.Opened opened, MarketEngine market) {
        return assertInstanceOf(TradeEngine.CloseResult.Closed.class, TradeEngine.close(account, opened.position().id(), market));
    }

    private static void assertRejected(TradeEngine.OpenResult result, String reason) {
        assertEquals(reason, assertInstanceOf(TradeEngine.OpenResult.Rejected.class, result).reason());
    }

    private static final class FakeWallet implements Wallet {
        private final Map<Currency, Long> items = new HashMap<>();

        FakeWallet with(Currency currency, long units) {
            this.items.put(currency, units);
            return this;
        }

        @Override
        public long count(Currency currency) {
            return this.items.getOrDefault(currency, 0L);
        }

        @Override
        public boolean remove(Currency currency, long units) {
            long held = count(currency);
            if (held < units) {
                return false;
            }
            this.items.put(currency, held - units);
            return true;
        }

        @Override
        public long add(Currency currency, long units) {
            this.items.merge(currency, units, Long::sum);
            return units;
        }
    }
}
