package com.pppopipupu.fxeasy.storage;

import module java.base;

import com.pppopipupu.fxeasy.FxConfig;
import com.pppopipupu.fxeasy.economy.EconomyConstants;
import com.pppopipupu.fxeasy.economy.WorldEconomyState;
import com.pppopipupu.fxeasy.event.MarketEventEngine;
import com.pppopipupu.fxeasy.market.CandleSeries;
import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;
import com.pppopipupu.fxeasy.market.CurrencyPair;
import com.pppopipupu.fxeasy.market.MarketEngine;
import com.pppopipupu.fxeasy.market.Side;
import com.pppopipupu.fxeasy.market.Timeframe;
import com.pppopipupu.fxeasy.shop.LoungeGift;
import com.pppopipupu.fxeasy.shop.Membership;
import com.pppopipupu.fxeasy.storage.FxSnapshot.AccountRow;
import com.pppopipupu.fxeasy.storage.FxSnapshot.AmountRow;
import com.pppopipupu.fxeasy.storage.FxSnapshot.CurrencyRow;
import com.pppopipupu.fxeasy.storage.FxSnapshot.DebtRow;
import com.pppopipupu.fxeasy.storage.FxSnapshot.GiftRow;
import com.pppopipupu.fxeasy.storage.FxSnapshot.PositionRow;
import com.pppopipupu.fxeasy.storage.FxSnapshot.SeriesRow;
import com.pppopipupu.fxeasy.storage.FxSnapshot.SubscriptionRow;
import com.pppopipupu.fxeasy.trade.Account;
import com.pppopipupu.fxeasy.trade.AccountStore;
import com.pppopipupu.fxeasy.trade.Position;
import net.minecraft.resources.Identifier;

/// Live FX economy of one server: the price process, the world economy, the published event layer and every
/// account. A fresh world derives its market randomness from the world seed and starts its market clock at zero.
/// A world saved before this extension existed keeps its prices, its rollover continuity and every account, and
/// begins its economic observation at the moment it was loaded - nothing is back filled and no price jumps to
/// the genesis valuation.
public final class FxState {

    private final MarketEngine market;
    private final WorldEconomyState economy;
    private final MarketEventEngine events;
    private final AccountStore accounts = new AccountStore();
    private final long marketAnchor;
    private final long chartEpoch;
    private final boolean migrated;

    private FxState(MarketEngine market, WorldEconomyState economy, MarketEventEngine events, long marketAnchor,
            long chartEpoch, boolean migrated) {
        this.market = market;
        this.economy = economy;
        this.events = events;
        this.marketAnchor = marketAnchor;
        this.chartEpoch = chartEpoch;
        this.migrated = migrated;
    }

    /// Restores a save, or starts a fresh world when the snapshot is empty. `gameTime` is the overworld clock at
    /// load time and is only ever used as the one time anchor that keeps an older save's day boundary and candle
    /// times continuous.
    public static FxState restore(FxSnapshot snapshot, long worldSeed, long gameTime) {
        boolean loaded = !snapshot.currencies().isEmpty();
        boolean legacy = snapshot.legacy();
        long marketTick = loaded ? (legacy ? gameTime : snapshot.marketTick()) : 0L;
        long anchor = loaded && snapshot.marketAnchor() != Long.MIN_VALUE ? snapshot.marketAnchor() : marketTick - gameTime;
        long priceRng = loaded ? snapshot.priceRng() : worldSeed;
        long eventRng = snapshot.eventRng() == Long.MIN_VALUE
                ? MarketEngine.derive(priceRng, MarketEngine.EVENT_RNG_DOMAIN)
                : snapshot.eventRng();
        long executionRng = snapshot.executionRng() == Long.MIN_VALUE
                ? MarketEngine.derive(priceRng, MarketEngine.EXECUTION_RNG_DOMAIN)
                : snapshot.executionRng();
        long volumeRng = snapshot.volumeRng() == Long.MIN_VALUE
                ? MarketEngine.derive(priceRng, MarketEngine.VOLUME_RNG_DOMAIN)
                : snapshot.volumeRng();
        MarketEngine market = new MarketEngine(worldSeed, priceRng, eventRng, executionRng, volumeRng,
                FxConfig.get().volatilityScale());
        market.restoreClock(marketTick);
        for (CurrencyRow row : snapshot.currencies()) {
            Currency currency = Currencies.byId(row.currency());
            if (currency == null) {
                continue;
            }
            if (Double.isNaN(row.anchor())) {
                market.restore(currency, row.logPrice(), row.absReturn());
            } else {
                market.restore(currency, row.anchor(), row.absReturn());
                market.restoreComponents(currency, row.anchor(), row.fundamental(), row.event());
            }
        }
        for (SeriesRow row : snapshot.series()) {
            CurrencyPair pair = CurrencyPair.byId(row.pair());
            if (pair == null) {
                continue;
            }
            CandleSeries series = market.series(pair, Timeframe.valueOf(row.timeframe()));
            long nextRoll = row.nextRollTick() == Long.MIN_VALUE ? Long.MIN_VALUE : row.nextRollTick() - anchor;
            series.restore(row.closed(), row.forming().orElse(null), marketTick, nextRoll);
        }
        long rolloverDay = loaded ? dayOf(marketTick, snapshot.rolloverDay()) : -1L;
        market.restoreRollover(rolloverDay);
        long epoch = legacy ? marketTick : loaded ? snapshot.chartEpoch() : 0L;
        long economyDay = loaded ? snapshot.rolloverDay() : -1L;
        long economyBucket = marketTick - Math.floorMod(marketTick, EconomyConstants.TICKS_PER_BUCKET);
        WorldEconomyState economy = snapshot.economy().isPresent()
                ? WorldEconomyState.restore(snapshot.economy().orElseThrow())
                : WorldEconomyState.migration(marketTick, economyBucket, nextDayTick(economyDay), economyDay);
        MarketEventEngine events = MarketEventEngine.restore(snapshot.events());
        FxState state = new FxState(market, economy, events, anchor, epoch, snapshot.legacy());
        for (AccountRow row : snapshot.accounts()) {
            state.accounts.restore(readAccount(row));
        }
        return state;
    }

    /// Last day the market rolled over on. A save written with a market clock derives it from that clock; a save
    /// old enough to predate the clock keeps the day it recorded, so neither one charges a swap it should not.
    private static long dayOf(long marketTick, long rolloverDay) {
        return marketTick > 0L ? Math.floorDiv(marketTick, MarketEngine.TICKS_PER_DAY) : rolloverDay;
    }

    /// Market tick the next economic day ends at. The day the economy last rolled over on decides it, so a load
    /// in the middle of a day never charges an extra swap, and a save that predates the market clock continues
    /// on the world day it was loaded in rather than on a day it never counted.
    private static long nextDayTick(long rolloverDay) {
        return Math.max(1L, rolloverDay + 2L) * MarketEngine.TICKS_PER_DAY;
    }

    public MarketEngine market() {
        return this.market;
    }

    public WorldEconomyState economy() {
        return this.economy;
    }

    public MarketEventEngine events() {
        return this.events;
    }

    public AccountStore accounts() {
        return this.accounts;
    }

    /// Market tick the overworld clock was at when this save was loaded, which is what an older candle time was
    /// rebased by. Zero for a world that was born with this extension.
    public long marketAnchor() {
        return this.marketAnchor;
    }

    /// First market tick whose candles carry a trustworthy time anchor. A candle from before it is drawn as
    /// price history and never carries an event marker.
    public long chartEpoch() {
        return this.chartEpoch;
    }

    public boolean migrated() {
        return this.migrated;
    }

    public FxSnapshot snapshot() {
        List<CurrencyRow> currencies = new ArrayList<>(Currencies.count());
        for (Currency currency : Currencies.all()) {
            double fundamental = this.economy.fundamental(currency);
            double event = this.economy.event(currency);
            currencies.add(new CurrencyRow(currency.id(), this.market.logPrice(currency), this.market.absReturn(currency),
                    this.market.logPrice(currency) - fundamental - event, fundamental, event));
        }
        List<SeriesRow> series = new ArrayList<>(Currencies.pairs().size() * Timeframe.ALL.size());
        for (CurrencyPair pair : Currencies.pairs()) {
            for (Timeframe timeframe : Timeframe.ALL) {
                CandleSeries candleSeries = this.market.series(pair, timeframe);
                series.add(new SeriesRow(pair.id(), timeframe.name(), candleSeries.snapshot(false),
                        Optional.ofNullable(candleSeries.current()), candleSeries.nextRollTick()));
            }
        }
        List<AccountRow> accounts = new ArrayList<>();
        for (Account account : this.accounts.all()) {
            accounts.add(writeAccount(account));
        }
        return new FxSnapshot(FxSnapshot.SCHEMA_VERSION, this.economy.marketTick(), this.chartEpoch, this.market.priceRng().state(),
                this.market.eventRng().state(), this.market.executionRng().state(), this.market.volumeRng().state(),
                this.market.lastRolloverDay(),
                this.marketAnchor, currencies, series, Optional.of(this.economy.snapshot()), this.events.snapshot(), accounts);
    }

    private static AccountRow writeAccount(Account account) {
        List<AmountRow> balances = new ArrayList<>(Currencies.count());
        List<AmountRow> realized = new ArrayList<>(Currencies.count());
        for (Currency currency : Currencies.all()) {
            balances.add(new AmountRow(currency.id(), account.balance(currency)));
            realized.add(new AmountRow(currency.id(), account.realized(currency)));
        }
        List<PositionRow> positions = new ArrayList<>(account.positionCount());
        for (Position position : account.positions()) {
            positions.add(new PositionRow(
                    position.id(),
                    position.pair().id(),
                    position.side().name(),
                    position.units(),
                    position.leverage(),
                    position.openPrice(),
                    position.baseUnits(),
                    position.marginMicro(),
                    position.openMarketTick(),
                    position.swapMicro(),
                    position.marginCurrency().id()));
        }
        List<DebtRow> debts = new ArrayList<>(Currencies.count());
        for (Currency currency : Currencies.all()) {
            long since = account.debtSince(currency);
            if (since != 0L) {
                debts.add(new DebtRow(currency.id(), since, account.debtDue(currency)));
            }
        }
        List<GiftRow> lounge = new ArrayList<>(account.loungeGifts().size());
        for (LoungeGift gift : account.loungeGifts()) {
            lounge.add(new GiftRow(gift.item().toString(), gift.count(),
                    Optional.ofNullable(gift.potion()).map(Identifier::toString)));
        }
        SubscriptionRow subscription = new SubscriptionRow(account.membership().id(), account.membershipUntil(),
                account.autoRenew(), account.loungeNext(), lounge);
        return new AccountRow(account.owner().toString(), balances, realized, positions, account.nextPositionId(), account.closedTrades(),
                account.opened(), account.tutorialSeen(), account.bonusPaid(), account.holder(), account.age(), debts,
                Optional.of(subscription));
    }

    /// Size of a restored position in items of the base currency of its pair. A row written before sizes were
    /// counted in items carries none, so the base amount it holds is rounded to whole items: the notional and the
    /// exposure of that position keep following the base amount it was opened with, and only its size readout
    /// changes.
    private static int units(PositionRow row) {
        return row.units() > 0 ? row.units() : (int) Math.max(1L, Math.round(row.baseUnits()));
    }

    /// Currency a restored position locks its margin in. An empty entry means the quote of its pair, which is
    /// what saves written before positions named a currency locked; a currency this world no longer trades
    /// returns null and the position is dropped with it.
    private static Currency marginCurrency(String name, CurrencyPair pair) {
        return name.isEmpty() ? pair.quote() : Currencies.byId(name);
    }

    private static Account readAccount(AccountRow row) {
        Account account = new Account(UUID.fromString(row.owner()));
        SubscriptionRow subscription = row.subscription().orElse(SubscriptionRow.NONE);
        for (AmountRow amount : row.balances()) {
            Currency currency = Currencies.byId(amount.currency());
            if (currency != null) {
                account.addBalance(currency, amount.micro());
            }
        }
        for (AmountRow amount : row.realized()) {
            Currency currency = Currencies.byId(amount.currency());
            if (currency != null) {
                account.addRealized(currency, amount.micro());
            }
        }
        for (PositionRow position : row.positions()) {
            CurrencyPair pair = CurrencyPair.byId(position.pair());
            if (pair == null) {
                continue;
            }
            Currency margin = marginCurrency(position.marginCurrency(), pair);
            if (margin == null) {
                continue;
            }
            Position restored = new Position(
                    position.id(),
                    pair,
                    Side.valueOf(position.side()),
                    margin,
                    units(position),
                    position.leverage(),
                    position.openPrice(),
                    position.baseUnits(),
                    position.marginMicro(),
                    position.openMarketTick());
            restored.addSwap(position.swapMicro());
            account.addPosition(restored);
        }
        for (DebtRow debt : row.debts()) {
            Currency currency = Currencies.byId(debt.currency());
            if (currency != null) {
                account.restoreDebt(currency, debt.since(), debt.due());
            }
        }
        List<LoungeGift> lounge = new ArrayList<>(subscription.lounge().size());
        for (GiftRow gift : subscription.lounge()) {
            Identifier item = Identifier.tryParse(gift.item());
            if (item == null || gift.count() <= 0) {
                continue;
            }
            Identifier potion = gift.potion().map(Identifier::tryParse).orElse(null);
            lounge.add(new LoungeGift(item, gift.count(), potion));
        }
        account.restoreMembership(Membership.byId(subscription.plan()), subscription.until(),
                subscription.autoRenew(), subscription.loungeNext(), lounge);
        account.restorePositionId(row.nextPositionId());
        account.restoreClosedTrades(row.closedTrades());
        account.restoreBonusPaid(row.bonusPaid());
        if (row.opened()) {
            account.register(row.holder(), row.age());
        }
        if (row.tutorialSeen()) {
            account.markTutorialSeen();
        }
        return account;
    }
}
