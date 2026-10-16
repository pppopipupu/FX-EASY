package com.pppopipupu.fxeasy.storage;

import module java.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.pppopipupu.fxeasy.economy.EconomyConstants;
import com.pppopipupu.fxeasy.economy.EconomySourceKind;
import com.pppopipupu.fxeasy.economy.WorldEconomyState;
import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;
import com.pppopipupu.fxeasy.market.CurrencyPair;
import com.pppopipupu.fxeasy.market.MarketDriverFrame;
import com.pppopipupu.fxeasy.market.MarketEngine;
import com.pppopipupu.fxeasy.market.Side;
import com.pppopipupu.fxeasy.market.Timeframe;
import com.pppopipupu.fxeasy.shop.LoungeGift;
import com.pppopipupu.fxeasy.shop.Membership;
import com.pppopipupu.fxeasy.shop.ShopEngine;
import com.pppopipupu.fxeasy.trade.Account;
import com.pppopipupu.fxeasy.trade.Money;
import com.pppopipupu.fxeasy.trade.Position;
import com.pppopipupu.fxeasy.trade.TradeEngine;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;

class FxSnapshotTest {

    private static final long SEED = 0x5EEDL;
    private static final CurrencyPair PAIR = CurrencyPair.of(Currencies.byId("dirt"), Currencies.byId("gold"));
    private static final Currency MARGIN = Currencies.byId("emerald");
    private static final Currency GOLD = Currencies.byId("gold");

    @Test
    void snapshotSurvivesTheNbtRoundTrip() {
        FxState state = FxState.restore(FxSnapshot.EMPTY, SEED, 0L);
        MarketEngine market = state.market();
        for (long tick = 1L; tick <= 4_000L; tick++) {
            market.advance(tick, new double[Currencies.count()], MarketDriverFrame.calm());
        }
        UUID owner = UUID.randomUUID();
        Account account = state.accounts().get(owner);
        account.register("kurumi", 21);
        account.addBalance(MARGIN, 64L * Money.UNIT);
        TradeEngine.OpenResult result = TradeEngine.open(account, PAIR, Side.BUY, MARGIN, 1_000, 100, market);
        assertInstanceOf(TradeEngine.OpenResult.Opened.class, result);
        Position opened = ((TradeEngine.OpenResult.Opened) result).position();
        account.markTutorialSeen();
        account.addBalance(MARGIN, -70L * Money.UNIT);
        account.settleDebt(4_000L);
        assertEquals(6L * Money.UNIT, account.debt(MARGIN), "the test needs a real debt on the account");
        account.addBalance(Currencies.byId("diamond"), 1_000L * Money.UNIT);
        assertInstanceOf(ShopEngine.BuyResult.Bought.class,
                ShopEngine.buy(account, Membership.PRO, Currencies.byId("diamond"), 1.0D, 4_000L),
                "the test needs a subscription to save");
        account.addGift(new LoungeGift(Identifier.parse("minecraft:diamond"), 3, null));
        account.addGift(new LoungeGift(Identifier.parse("minecraft:potion"), 1, Identifier.parse("minecraft:healing")));

        FxSnapshot snapshot = state.snapshot();
        CompoundTag tag = (CompoundTag) FxSnapshot.CODEC.encodeStart(NbtOps.INSTANCE, snapshot).getOrThrow();
        FxSnapshot decoded = FxSnapshot.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
        assertEquals(snapshot, decoded);

        FxState restored = FxState.restore(decoded, SEED, 0L);
        assertEquals(market.priceRng().state(), restored.market().priceRng().state());
        assertEquals(market.eventRng().state(), restored.market().eventRng().state());
        assertEquals(market.executionRng().state(), restored.market().executionRng().state());
        assertEquals(market.volumeRng().state(), restored.market().volumeRng().state());
        assertEquals(market.lastRolloverDay(), restored.market().lastRolloverDay());
        assertEquals(state.economy().marketTick(), restored.economy().marketTick());
        for (Currency currency : Currencies.all()) {
            assertEquals(market.logPrice(currency), restored.market().logPrice(currency), currency.name());
            assertEquals(market.absReturn(currency), restored.market().absReturn(currency), currency.name());
        }
        assertEquals(market.price(PAIR), restored.market().price(PAIR));
        assertEquals(market.series(PAIR, Timeframe.M1).snapshot(true), restored.market().series(PAIR, Timeframe.M1).snapshot(true));
        assertEquals(market.series(PAIR, Timeframe.M1).nextRollTick(), restored.market().series(PAIR, Timeframe.M1).nextRollTick());

        Account restoredAccount = restored.accounts().get(owner);
        assertEquals(account.balance(MARGIN), restoredAccount.balance(MARGIN));
        assertEquals(account.realized(MARGIN), restoredAccount.realized(MARGIN));
        assertEquals(account.nextPositionId(), restoredAccount.nextPositionId());
        assertEquals(account.closedTrades(), restoredAccount.closedTrades());
        assertEquals(account.opened(), restoredAccount.opened());
        assertEquals("kurumi", restoredAccount.holder());
        assertEquals(21, restoredAccount.age());
        assertEquals(account.tutorialSeen(), restoredAccount.tutorialSeen());
        assertEquals(account.bonusPaid(), restoredAccount.bonusPaid());
        assertEquals(account.debtSince(MARGIN), restoredAccount.debtSince(MARGIN));
        assertEquals(account.debtDue(MARGIN), restoredAccount.debtDue(MARGIN));
        assertEquals(account.membership(), restoredAccount.membership());
        assertEquals(account.membershipUntil(), restoredAccount.membershipUntil());
        assertEquals(account.autoRenew(), restoredAccount.autoRenew());
        assertEquals(account.loungeNext(), restoredAccount.loungeNext());
        assertEquals(account.loungeGifts(), restoredAccount.loungeGifts());
        assertEquals(1, restoredAccount.positionCount());
        Position restoredPosition = restoredAccount.position(opened.id());
        assertEquals(opened.openPrice(), restoredPosition.openPrice());
        assertEquals(opened.baseUnits(), restoredPosition.baseUnits());
        assertEquals(opened.marginMicro(), restoredPosition.marginMicro());
        assertEquals(opened.swapMicro(), restoredPosition.swapMicro());
        assertEquals(opened.openMarketTick(), restoredPosition.openMarketTick());
        assertEquals(opened.pair(), restoredPosition.pair());
        assertEquals(opened.side(), restoredPosition.side());
        assertEquals(opened.leverage(), restoredPosition.leverage());
        assertEquals(opened.units(), restoredPosition.units());
        assertEquals(MARGIN, restoredPosition.marginCurrency());
    }

    /// A save written while the world economy has revalued a currency carries the level, the revaluation and the
    /// level the process reached without it, so a load resumes on exactly the rate it stopped at. Without the
    /// decomposition the whole revaluation would be applied a second time on every load.
    @Test
    void aSaveInTheMiddleOfARevaluationKeepsItsPrice() {
        FxState state = FxState.restore(FxSnapshot.EMPTY, SEED, 0L);
        MarketEngine market = state.market();
        WorldEconomyState economy = state.economy();
        double[] noEvent = new double[Currencies.count()];
        long capacity = Math.round(economy.capacity(GOLD));
        for (int day = 0; day < 3; day++) {
            for (int bucket = 0; bucket < EconomyConstants.BUCKETS_PER_DAY; bucket++) {
                economy.openBucket().addSupply(EconomySourceKind.PIGLIN_GOLD, GOLD,
                        4L * capacity / EconomyConstants.BUCKETS_PER_DAY);
                for (int tick = 0; tick < EconomyConstants.TICKS_PER_BUCKET; tick++) {
                    if (economy.advanceClock(1)) {
                        economy.closeBucket();
                        market.resetBucketAttribution();
                    }
                    economy.setRiskFloor(MarketDriverFrame.NO_RISK_FLOOR);
                    MarketDriverFrame frame = economy.tick(noEvent);
                    market.advance(economy.marketTick(), economy.fundamentalThisTick(), frame);
                }
            }
        }
        assertTrue(economy.fundamental(GOLD) < -1.0E-3D,
                "the test needs a real revaluation to be worth anything");
        FxSnapshot.CurrencyRow row = state.snapshot().currencies().get(Currencies.index(GOLD));
        assertEquals(market.logPrice(GOLD), row.anchor() + row.fundamental() + row.event(), 1.0E-12D,
                "the row has to decompose into the level the process reached plus what the world added");
        FxState restored = FxState.restore(state.snapshot(), SEED, 0L);
        assertEquals(market.logPrice(GOLD), restored.market().logPrice(GOLD), 1.0E-12D,
                "a load must not apply the revaluation a second time");
        assertEquals(market.price(PAIR), restored.market().price(PAIR), 1.0E-12D);
    }

    /// A world saved before positions named the currency they lock their margin in has to keep trading: those
    /// rows carry no currency and locked the quote of their pair.
    @Test
    void positionsWithoutAMarginCurrencyFallBackToTheQuote() {
        UUID owner = UUID.randomUUID();
        FxSnapshot.AccountRow row = new FxSnapshot.AccountRow(owner.toString(), List.of(), List.of(),
                List.of(new FxSnapshot.PositionRow(7L, PAIR.id(), Side.SELL.name(), 0, 100, 1.0D, 2048.0D, 40_000L, 0L, 0L, "")),
                8L, 0L, true, true, false, "kurumi", 21, List.of(), Optional.empty());
        FxSnapshot legacy = new FxSnapshot(1, 0L, 0L, 0L, Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE, -1L,
                Long.MIN_VALUE, List.of(), List.of(), Optional.empty(), EventRow.empty(), List.of(row));
        FxState state = FxState.restore(legacy, SEED, 0L);
        Position restored = state.accounts().get(owner).position(7L);
        assertEquals(PAIR.quote(), restored.marginCurrency());
        assertEquals(40_000L, restored.marginMicro());
        assertEquals(2048, restored.units(), "a row without a size falls back to the base amount it holds");
    }

    /// A world that predates the world economy keeps its prices and its day continuity and starts observing from
    /// the load: nothing is back filled, the fundamental component is zero, and the first rollover is the one the
    /// world was already counting towards rather than an extra charge on load.
    @Test
    void anOlderSaveKeepsItsPricesAndContinuesItsDay() {
        long gameTime = 5L * MarketEngine.TICKS_PER_DAY + 1_234L;
        MarketEngine old = new MarketEngine(SEED);
        double[] noChange = new double[Currencies.count()];
        for (long tick = 0L; tick < 40_000L; tick++) {
            old.advance(tick, noChange, MarketDriverFrame.calm());
        }
        List<FxSnapshot.CurrencyRow> currencies = new ArrayList<>();
        for (Currency currency : Currencies.all()) {
            currencies.add(new FxSnapshot.CurrencyRow(currency.id(), old.logPrice(currency), old.absReturn(currency),
                    Double.NaN, 0.0D, 0.0D));
        }
        FxSnapshot legacy = new FxSnapshot(1, 0L, 0L, old.priceRng().state(), Long.MIN_VALUE, Long.MIN_VALUE,
                Long.MIN_VALUE, Math.floorDiv(gameTime, MarketEngine.TICKS_PER_DAY) - 1L, Long.MIN_VALUE, currencies,
                List.of(), Optional.empty(), EventRow.empty(), List.of());

        FxState state = FxState.restore(legacy, SEED, gameTime);
        assertTrue(state.migrated());
        assertEquals(gameTime, state.economy().marketTick());
        assertEquals(gameTime, state.chartEpoch());
        for (Currency currency : Currencies.all()) {
            assertEquals(old.logPrice(currency), state.market().logPrice(currency), 1.0E-12D, currency.name());
            assertEquals(0.0D, state.economy().fundamental(currency), 1.0E-12D, currency.name());
            assertEquals(0.0D, state.economy().event(currency), 1.0E-12D, currency.name());
        }
        assertEquals(old.priceRng().state(), state.market().priceRng().state());
        long dayEnd = (Math.floorDiv(gameTime, MarketEngine.TICKS_PER_DAY) + 1L) * MarketEngine.TICKS_PER_DAY;
        assertEquals(Math.floorDiv(gameTime, MarketEngine.TICKS_PER_DAY) - 1L, state.economy().rolloverDay());
        assertFalse(state.economy().dayRolledOver(), "a load does not complete a day by itself");
        assertFalse(state.economy().advanceClock(true), "a load in the middle of a bucket does not close it");
        for (long tick = gameTime + 2L; tick < dayEnd; tick++) {
            state.economy().advanceClock(true);
        }
        assertFalse(state.economy().dayRolledOver(), "the day the world was in has not ended yet");
        state.economy().advanceClock(true);
        assertTrue(state.economy().dayRolledOver(), "the first rollover is the one the world was counting towards");
        assertEquals(Math.floorDiv(gameTime, MarketEngine.TICKS_PER_DAY), state.economy().rolloverDay());
    }

    /// The market clock is the save's, not the world clock's: a save written in the middle of a market day keeps
    /// counting from where it stopped whatever the world time does afterwards. The clock is advanced the way the
    /// service advances it, one market tick at a time, which is what keeps the saved market tick meaningful.
    @Test
    void theMarketClockSurvivesAReloadWithoutJumping() {
        FxState state = FxState.restore(FxSnapshot.EMPTY, SEED, 0L);
        advance(state, 1_000L);
        FxSnapshot snapshot = state.snapshot();
        assertEquals(1_000L, snapshot.marketTick());

        FxState reloaded = FxState.restore(snapshot, SEED, 900_000L);
        assertEquals(1_000L, reloaded.economy().marketTick());
        assertEquals(1_000L, reloaded.market().marketTick());
        assertEquals(0L, reloaded.marketAnchor());
    }

    /// Advances a restored world the way the service does: the economy clock, the market tick it drives and the
    /// bucket boundary all move together, so the save that comes out is the save a running server would write.
    private static void advance(FxState state, long ticks) {
        MarketEngine market = state.market();
        WorldEconomyState economy = state.economy();
        for (long tick = 0L; tick < ticks; tick++) {
            if (economy.advanceClock(false)) {
                economy.closeBucket();
                market.resetBucketAttribution();
            }
            economy.setRiskFloor(economy.riskFloor());
            MarketDriverFrame frame = economy.tick(new double[Currencies.count()]);
            market.advance(economy.marketTick(), economy.fundamentalThisTick(), frame);
            economy.dayRolledOver();
        }
    }
}
