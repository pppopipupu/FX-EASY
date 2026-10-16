package com.pppopipupu.fxeasy.server;

import module java.base;

import com.pppopipupu.fxeasy.FxConfig;
import com.pppopipupu.fxeasy.economy.EconomyConstants;
import com.pppopipupu.fxeasy.economy.EconomySourceKind;
import com.pppopipupu.fxeasy.economy.WorldEconomyState;
import com.pppopipupu.fxeasy.event.MarketEventEngine;
import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;
import com.pppopipupu.fxeasy.market.CurrencyPair;
import com.pppopipupu.fxeasy.market.FeeDiscount;
import com.pppopipupu.fxeasy.market.MarketEngine;
import com.pppopipupu.fxeasy.market.Timeframe;
import com.pppopipupu.fxeasy.net.FxAccountPayload;
import com.pppopipupu.fxeasy.net.FxCurrenciesPayload;
import com.pppopipupu.fxeasy.net.FxEconomyPayload;
import com.pppopipupu.fxeasy.net.FxEventsPayload;
import com.pppopipupu.fxeasy.net.FxLoungePoolPayload;
import com.pppopipupu.fxeasy.net.FxMarketPayload;
import com.pppopipupu.fxeasy.net.FxPositionPayload;
import com.pppopipupu.fxeasy.shop.LoungeFile;
import com.pppopipupu.fxeasy.shop.LoungeGift;
import com.pppopipupu.fxeasy.storage.FxState;
import com.pppopipupu.fxeasy.trade.Account;
import com.pppopipupu.fxeasy.trade.Position;
import com.pppopipupu.fxeasy.trade.TradeEngine;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/// Builds and sends everything one player's terminal reads from the platform: the market tick, the account, the
/// economy report and the event feed. It only reads the state it is handed and never mutates an account, the
/// market or the world economy, so every number that leaves here is one the service already decided. The two
/// revision maps below are the per player record of what a client already holds, which is the only state this
/// class owns; the subscription it shapes the market payload with stays with the service that grants it.
final class FxPlayerFeed {

    private final FxState state;
    private final WorldEconomyState economy;
    private final MarketEventEngine events;
    private final FxWorldEconomyTracker tracker;
    private final Map<UUID, Long> sentEconomyRevision = new HashMap<>();
    private final Map<UUID, Long> sentEventRevision = new HashMap<>();

    FxPlayerFeed(FxState state, WorldEconomyState economy, MarketEventEngine events, FxWorldEconomyTracker tracker) {
        this.state = state;
        this.economy = economy;
        this.events = events;
        this.tracker = tracker;
    }

    void sendMarket(ServerPlayer player, Subscription subscription) {
        MarketEngine market = this.state.market();
        double[] prices = new double[Currencies.count()];
        for (Currency currency : Currencies.all()) {
            prices[Currencies.index(currency)] = market.price(currency);
        }
        if (subscription == null) {
            PacketDistributor.sendToPlayer(player, new FxMarketPayload(prices, this.economy.marketTick(),
                    MarketEngine.REGIME_FLOOR, null, this.state.chartEpoch()));
            return;
        }
        PacketDistributor.sendToPlayer(player, new FxMarketPayload(
                prices,
                this.economy.marketTick(),
                market.regime(subscription.pair()),
                market.series(subscription.pair(), subscription.timeframe()).current(),
                this.state.chartEpoch()));
    }

    /// The set the platform trades, which the panel draws and every price array is indexed by.
    void sendCurrencies(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, FxCurrenciesPayload.of(Currencies.all()));
    }

    /// The pool the lounge of this world draws from, which the shop page writes out beside the tier that opens
    /// it. It is a definition of the world like the currency table, so it is sent when the terminal subscribes
    /// and never with the account.
    void sendLoungePool(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, FxLoungePoolPayload.of(LoungeFile.current()));
    }

    void sendAccount(ServerPlayer player, Account account) {
        MarketEngine market = this.state.market();
        long[] balances = new long[Currencies.count()];
        long[] realized = new long[Currencies.count()];
        long[] freeMargin = new long[Currencies.count()];
        long[] debtSince = new long[Currencies.count()];
        for (Currency currency : Currencies.all()) {
            int index = Currencies.index(currency);
            balances[index] = account.balance(currency);
            realized[index] = account.realized(currency);
            freeMargin[index] = account.freeMargin(currency);
            debtSince[index] = account.debtSince(currency);
        }
        List<FxPositionPayload> positions = new ArrayList<>(account.positionCount());
        FeeDiscount discount = account.feeDiscount();
        for (Position position : account.positions()) {
            positions.add(new FxPositionPayload(
                    position.id(),
                    position.pair().index(),
                    position.side().ordinal(),
                    position.units(),
                    position.leverage(),
                    position.openPrice(),
                    position.closingPrice(market, discount),
                    position.marginMicro(),
                    position.swapMicro(),
                    position.unrealizedMarginMicro(market, discount),
                    position.openMarketTick(),
                    Currencies.index(position.marginCurrency())));
        }
        List<ItemStack> lounge = new ArrayList<>(account.loungeGifts().size());
        for (LoungeGift gift : account.loungeGifts()) {
            lounge.add(gift.stack());
        }
        boolean marginCall = TradeEngine.marginCall(account, market);
        PacketDistributor.sendToPlayer(player, new FxAccountPayload(
                balances,
                realized,
                freeMargin,
                positions,
                marginCall,
                account.closedTrades(),
                account.opened(),
                account.tutorialSeen(),
                account.bonusPaid(),
                account.holder(),
                account.age(),
                debtSince,
                account.blacklisted(market.marketTick()),
                Currencies.index(FxConfig.get().welcomeCurrency()),
                FxConfig.get().welcomeItems(),
                account.membership().id(),
                account.membershipUntil(),
                account.autoRenew(),
                account.loungeNext(),
                lounge));
    }

    void sendEconomy(ServerPlayer player, boolean force) {
        long revision = this.economy.marketTick() / EconomyConstants.TICKS_PER_BUCKET;
        if (!force && this.sentEconomyRevision.getOrDefault(player.getUUID(), -1L) >= revision) {
            return;
        }
        this.sentEconomyRevision.put(player.getUUID(), revision);
        PacketDistributor.sendToPlayer(player, economyPayload());
    }

    /// The economy report as the terminal reads it. Flows travel in capacity units per economic day, pressure as
    /// an ordinal level, and the revaluation as the log units the market has actually applied, so the client
    /// never has to know how any of it was estimated.
    private FxEconomyPayload economyPayload() {
        FxConfig.Snapshot config = FxConfig.get();
        double[] supplyFlow = new double[Currencies.count()];
        double[] demandFlow = new double[Currencies.count()];
        int[] pressure = new int[Currencies.count()];
        double[] appliedFundamental = new double[Currencies.count()];
        double[] appliedEvent = new double[Currencies.count()];
        double[] capacity = new double[Currencies.count()];
        for (Currency currency : Currencies.all()) {
            int index = Currencies.index(currency);
            supplyFlow[index] = this.economy.supplyEwma(currency);
            demandFlow[index] = this.economy.demandEwma(currency);
            pressure[index] = pressureLevel(this.economy.pressure(currency));
            appliedFundamental[index] = this.economy.fundamental(currency);
            appliedEvent[index] = this.economy.event(currency);
            capacity[index] = this.economy.capacity(currency);
        }
        int[] coverage = this.tracker.coverage().clone();
        if (!config.collectEnabled()) {
            coverage[FxEconomyPayload.COVERAGE_DISABLED_SOURCES] = EconomySourceKind.COUNT;
        }
        if (this.state.migrated()) {
            coverage[FxEconomyPayload.COVERAGE_MIGRATED] = 1;
        }
        return new FxEconomyPayload(status(), this.economy.marketTick() / EconomyConstants.TICKS_PER_BUCKET, this.economy.marketTick(),
                this.economy.bucketStart(), this.economy.rolloverDay(),
                (int) Math.round(this.economy.playerDayEwma() * 10.0D), supplyFlow, demandFlow, pressure,
                appliedFundamental, appliedEvent, capacity, coverage, this.tracker.supportedSourceOrdinals(),
                this.tracker.supportedSourceAtoms());
    }

    private int status() {
        if (!FxConfig.get().collectEnabled()) {
            return FxEconomyPayload.STATUS_DISABLED;
        }
        if (this.state.migrated() && this.economy.marketTick() < EconomyConstants.TICKS_PER_DAY) {
            return FxEconomyPayload.STATUS_OBSERVING;
        }
        return this.economy.bucketStart() + EconomyConstants.TICKS_PER_BUCKET >= this.economy.marketTick()
                ? FxEconomyPayload.STATUS_FRESH
                : FxEconomyPayload.STATUS_STALE;
    }

    private static int pressureLevel(double pressure) {
        if (pressure < 0.05D) {
            return FxEconomyPayload.PRESSURE_LOW;
        }
        if (pressure < 0.25D) {
            return FxEconomyPayload.PRESSURE_NORMAL;
        }
        if (pressure < 0.60D) {
            return FxEconomyPayload.PRESSURE_HIGH;
        }
        return pressure < 1.20D ? FxEconomyPayload.PRESSURE_SURPLUS : FxEconomyPayload.PRESSURE_SEVERE;
    }

    void sendEvents(ServerPlayer player, boolean force) {
        long revision = this.events.revision();
        if (!force && this.sentEventRevision.getOrDefault(player.getUUID(), -1L) >= revision) {
            return;
        }
        this.sentEventRevision.put(player.getUUID(), revision);
        PacketDistributor.sendToPlayer(player, new FxEventsPayload(revision, this.economy.marketTick(),
                this.events.publish(this.economy.marketTick())));
    }

    record Subscription(CurrencyPair pair, Timeframe timeframe) {
    }
}
