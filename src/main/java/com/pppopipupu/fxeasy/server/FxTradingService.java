package com.pppopipupu.fxeasy.server;

import module java.base;

import com.pppopipupu.fxeasy.FxConfig;
import com.pppopipupu.fxeasy.FxEasy;
import com.pppopipupu.fxeasy.economy.EconomyConstants;
import com.pppopipupu.fxeasy.economy.WorldEconomyState;
import com.pppopipupu.fxeasy.event.MarketEventEngine;
import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;
import com.pppopipupu.fxeasy.market.CurrencyFile;
import com.pppopipupu.fxeasy.market.CurrencyPair;
import com.pppopipupu.fxeasy.market.MarketDriverFrame;
import com.pppopipupu.fxeasy.market.MarketEngine;
import com.pppopipupu.fxeasy.market.Side;
import com.pppopipupu.fxeasy.market.Timeframe;
import com.pppopipupu.fxeasy.net.FxAccountOpenPayload;
import com.pppopipupu.fxeasy.net.FxAutoRenewPayload;
import com.pppopipupu.fxeasy.net.FxClaimBonusPayload;
import com.pppopipupu.fxeasy.net.FxCloseAllPayload;
import com.pppopipupu.fxeasy.net.FxClosePositionPayload;
import com.pppopipupu.fxeasy.net.FxDepositPayload;
import com.pppopipupu.fxeasy.net.FxHistoryPayload;
import com.pppopipupu.fxeasy.net.FxLoungeClaimPayload;
import com.pppopipupu.fxeasy.net.FxNoticePayload;
import com.pppopipupu.fxeasy.net.FxOrderPayload;
import com.pppopipupu.fxeasy.net.FxShopBuyPayload;
import com.pppopipupu.fxeasy.net.FxSubscribePayload;
import com.pppopipupu.fxeasy.net.FxTutorialSeenPayload;
import com.pppopipupu.fxeasy.net.FxUnsubscribePayload;
import com.pppopipupu.fxeasy.net.FxWithdrawPayload;
import com.pppopipupu.fxeasy.shop.LoungeFile;
import com.pppopipupu.fxeasy.shop.LoungeGift;
import com.pppopipupu.fxeasy.shop.Membership;
import com.pppopipupu.fxeasy.shop.ShopEngine;
import com.pppopipupu.fxeasy.storage.FxSavedData;
import com.pppopipupu.fxeasy.storage.FxState;
import com.pppopipupu.fxeasy.trade.Account;
import com.pppopipupu.fxeasy.trade.Money;
import com.pppopipupu.fxeasy.trade.Position;
import com.pppopipupu.fxeasy.trade.TradeEngine;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/// Server side owner of the FX economy: advances the market every tick on its own monotonic clock, prices what
/// the world actually produced, runs the published event layer, charges swap at rollover, enforces margin rules
/// and answers every terminal request. All money movements are validated here.
public final class FxTradingService {

    private static final int SYNC_INTERVAL = 5;
    private static final int SNAPSHOT_INTERVAL = 600;
    private static final int INTEL_INTERVAL = EconomyConstants.TICKS_PER_BUCKET;

    private static FxTradingService instance;

    private final MinecraftServer server;
    private final FxSavedData data;
    private final FxState state;
    private final WorldEconomyState economy;
    private final MarketEventEngine events;
    private final FxWorldEconomyTracker tracker;
    private final FxPlayerFeed feed;
    private final Map<UUID, FxPlayerFeed.Subscription> subscriptions = new HashMap<>();
    private final Map<UUID, Boolean> marginCalls = new HashMap<>();
    private final Map<UUID, Boolean> blacklists = new HashMap<>();
    private final double[] eventRate = new double[Currencies.count()];
    private int ticks;

    private FxTradingService(MinecraftServer server, FxSavedData data, FxState state, FxWorldEconomyTracker tracker) {
        this.server = server;
        this.data = data;
        this.state = state;
        this.economy = state.economy();
        this.events = state.events();
        this.tracker = tracker;
        this.feed = new FxPlayerFeed(state, this.economy, this.events, tracker);
    }

    public static void start(MinecraftServer server) {
        Path serverconfig = server.getWorldPath(LevelResource.ROOT).resolve("serverconfig");
        Currencies.set(CurrencyFile.load(serverconfig.resolve(CurrencyFile.FILE_NAME)));
        LoungeFile.load(serverconfig.resolve(LoungeFile.FILE_NAME));
        FxSavedData data = server.getDataStorage().computeIfAbsent(FxSavedData.TYPE);
        long gameTime = server.overworld().getGameTime();
        FxState state = FxState.restore(data.snapshot(), server.overworld().getSeed(), gameTime);
        FxWorldEconomyTracker tracker = new FxWorldEconomyTracker(state);
        tracker.attach();
        tracker.onFact(state.events()::observe);
        instance = new FxTradingService(server, data, state, tracker);
        if (state.migrated()) {
            FxEasy.LOGGER.info("FX market continuing an older world: economic observation starts at market tick {}",
                    state.economy().marketTick());
        }
        FxEasy.LOGGER.info("FX market online with {} accounts at market tick {}", state.accounts().all().size(),
                state.economy().marketTick());
    }

    public static void stop() {
        LoungeFile.reset();
        if (instance == null) {
            return;
        }
        instance.save();
        instance.tracker.detach();
        instance = null;
    }

    public static void tickServer() {
        if (instance != null) {
            instance.tick();
        }
    }

    public static void onSubscribe(FxSubscribePayload payload, IPayloadContext context) {
        if (instance != null && context.player() instanceof ServerPlayer player) {
            instance.subscribe(player, payload.pairIndex(), payload.timeframeIndex());
        }
    }

    public static void onUnsubscribe(FxUnsubscribePayload payload, IPayloadContext context) {
        if (instance != null && context.player() instanceof ServerPlayer player) {
            instance.subscriptions.remove(player.getUUID());
        }
    }

    public static void onOrder(FxOrderPayload payload, IPayloadContext context) {
        if (instance != null && context.player() instanceof ServerPlayer player) {
            instance.order(player, payload);
        }
    }

    public static void onClosePosition(FxClosePositionPayload payload, IPayloadContext context) {
        if (instance != null && context.player() instanceof ServerPlayer player) {
            instance.closePosition(player, payload.positionId());
        }
    }

    public static void onCloseAll(FxCloseAllPayload payload, IPayloadContext context) {
        if (instance != null && context.player() instanceof ServerPlayer player) {
            instance.closeAll(player);
        }
    }

    public static void onDeposit(FxDepositPayload payload, IPayloadContext context) {
        if (instance != null && context.player() instanceof ServerPlayer player) {
            instance.deposit(player, payload.currencyOrdinal(), payload.units());
        }
    }

    public static void onWithdraw(FxWithdrawPayload payload, IPayloadContext context) {
        if (instance != null && context.player() instanceof ServerPlayer player) {
            instance.withdraw(player, payload.currencyOrdinal(), payload.units());
        }
    }

    public static void onTutorialSeen(FxTutorialSeenPayload payload, IPayloadContext context) {
        if (instance != null && context.player() instanceof ServerPlayer player) {
            instance.tutorialSeen(player);
        }
    }

    public static void onAccountOpen(FxAccountOpenPayload payload, IPayloadContext context) {
        if (instance != null && context.player() instanceof ServerPlayer player) {
            instance.openAccount(player, payload.holder(), payload.age());
        }
    }

    public static void onClaimBonus(FxClaimBonusPayload payload, IPayloadContext context) {
        if (instance != null && context.player() instanceof ServerPlayer player) {
            instance.claimBonus(player);
        }
    }

    public static void onShopBuy(FxShopBuyPayload payload, IPayloadContext context) {
        if (instance != null && context.player() instanceof ServerPlayer player) {
            instance.buy(player, payload.plan(), payload.currency());
        }
    }

    public static void onAutoRenew(FxAutoRenewPayload payload, IPayloadContext context) {
        if (instance != null && context.player() instanceof ServerPlayer player) {
            instance.setAutoRenew(player, payload.enabled());
        }
    }

    public static void onLoungeClaim(FxLoungeClaimPayload payload, IPayloadContext context) {
        if (instance != null && context.player() instanceof ServerPlayer player) {
            instance.claimGift(player, payload.index());
        }
    }

    /// One tick of the world: committed facts first, then the market clock, then the bucket boundary, then the
    /// event layer and the price process, and only then the account risk rules. A fact committed this tick is
    /// priced by this tick's bucket, and a bucket boundary belongs to the bucket that just closed.
    private void tick() {
        MarketEngine market = this.state.market();
        this.tracker.onServerTick(this.server);
        boolean boundary = this.economy.advanceClock(this.server.getPlayerList().getPlayerCount());
        if (boundary) {
            this.economy.closeBucket();
            this.tracker.onBucketBoundary();
            market.resetBucketAttribution();
        }
        this.events.tick(this.economy.marketTick(), this.economy, market.eventRng());
        this.economy.setRiskFloor(this.events.riskFloor());
        MarketDriverFrame frame = this.economy.tick(this.events.rateRequest(this.eventRate));
        market.advance(this.economy.marketTick(), this.economy.fundamentalThisTick(), frame);
        if (this.economy.dayRolledOver()) {
            for (Account account : List.copyOf(this.state.accounts().all())) {
                if (TradeEngine.settleSwap(account, market)) {
                    refresh(account);
                }
            }
        }
        settleDebts();
        settleSubscriptions();
        for (Account account : List.copyOf(this.state.accounts().all())) {
            boolean call = TradeEngine.marginCall(account, market);
            if (this.marginCalls.put(account.owner(), call) != Boolean.TRUE && call) {
                notifyMarginCall(account);
            }
        }
        List<Account> liquidated = null;
        for (Account account : List.copyOf(this.state.accounts().all())) {
            if (TradeEngine.needsLiquidation(account, market)) {
                int closed = TradeEngine.closeAll(account, market).size();
                if (closed > 0) {
                    (liquidated == null ? liquidated = new ArrayList<>() : liquidated).add(account);
                }
            }
        }
        if (liquidated != null) {
            for (Account account : liquidated) {
                notifyLiquidation(account);
            }
        }
        this.ticks++;
        if (this.ticks % SNAPSHOT_INTERVAL == 0) {
            save();
        }
        if (this.ticks % SYNC_INTERVAL == 0) {
            broadcast();
        }
        if (this.ticks % INTEL_INTERVAL == 0) {
            broadcastIntel();
        }
    }

    /// Charges the interest of every debt that is due and keeps the credit record of every account, including
    /// the ones whose player is offline: a debt ages on the world clock, not on the clock of a login. An account
    /// whose credit flipped this tick is told about it, whether it was blacklisted or restored.
    private void settleDebts() {
        long marketTick = this.economy.marketTick();
        for (Account account : this.state.accounts().all()) {
            List<Account.Interest> charges = account.settleDebt(marketTick);
            boolean blacklisted = account.blacklisted(marketTick);
            boolean wasBlacklisted = this.blacklists.put(account.owner(), blacklisted) == Boolean.TRUE;
            if (!charges.isEmpty()) {
                notifyDebtInterest(account, charges);
                refresh(account);
            }
            if (blacklisted != wasBlacklisted) {
                notifyCredit(account, blacklisted);
                refresh(account);
            }
        }
    }

    /// Renews the subscriptions whose period is up, delivers what the lounges of the accounts are due and says
    /// so, for every account whether or not its player is online: a plan runs on the market clock and not on the
    /// clock of a login, exactly like the debt of an account does. A plan the account cannot pay for lapses here,
    /// and the shop is the only way back onto one.
    private void settleSubscriptions() {
        long marketTick = this.economy.marketTick();
        LoungeFile.Pool pool = LoungeFile.current();
        for (Account account : this.state.accounts().all()) {
            if (ShopEngine.settle(account, marketTick) instanceof ShopEngine.Renewal renewal) {
                switch (renewal) {
                    case ShopEngine.Renewal.Renewed renewed -> notifyRenewal(account, renewed);
                    case ShopEngine.Renewal.Expired expired -> notifyLapsed(account, expired);
                }
                refresh(account);
            }
            if (ShopEngine.deliver(account, marketTick, pool)) {
                notifyGift(account);
                refresh(account);
            }
        }
    }

    /// Pays for a plan out of the free margin of the currency the player chose at the checkout, and reports what
    /// the shop decided or why it refused. The client quotes the price it draws from its own mirror of the
    /// market; what leaves the balance is the price of the catalogue here, converted at the rate this server
    /// quotes at the tick the request arrives, so a client cannot buy a plan with a price it made up.
    private void buy(ServerPlayer player, String planId, String currencyId) {
        Account account = account(player);
        Membership plan = Membership.byId(planId);
        Currency paying = Currencies.byId(currencyId);
        Currency priced = Currencies.byId(plan.priceCurrencyId());
        double rate = priced == null || paying == null ? 1.0D : this.state.market().rate(priced, paying);
        ShopEngine.BuyResult result = ShopEngine.buy(account, plan, paying, rate, this.economy.marketTick());
        if (result instanceof ShopEngine.BuyResult.Bought bought) {
            FxEasy.LOGGER.info("FX subscription {} {} by {} until market tick {} for {}, paid {}",
                    bought.plan().id(), bought.extended() ? "extended" : "bought",
                    player.getName().getString(), bought.untilTick(), describe(bought.plan()),
                    describe(bought));
            notice(player, bought.extended() ? "fxeasy.notice.shop.extend" : "fxeasy.notice.shop.buy",
                    describe(bought), false);
        } else if (result instanceof ShopEngine.BuyResult.Rejected rejected) {
            notice(player, rejected.reason(), "", true);
        }
        this.feed.sendAccount(player, account);
    }

    /// Turns the renewal of the subscription of the account off or on again. A plan is bought renewed, so this
    /// switch is the cancellation of the shop: what was already paid is never given back, and the plan runs to
    /// the end of the period it is in either way.
    private void setAutoRenew(ServerPlayer player, boolean enabled) {
        Account account = account(player);
        if (account.membership() != Membership.NONE) {
            account.setAutoRenew(enabled);
            FxEasy.LOGGER.info("FX subscription {} renewal {} by {}", account.membership().id(),
                    enabled ? "on" : "off", player.getName().getString());
            notice(player, enabled ? "fxeasy.notice.shop.renew_on" : "fxeasy.notice.shop.renew_off", "", false);
        }
        this.feed.sendAccount(player, account);
    }

    /// Hands one gift out of the lounge of the account. The gift leaves the lounge on the spot, so a claim that
    /// arrives twice cannot hand the same present over twice, and a stack the inventory cannot take is dropped in
    /// front of the player rather than refused: the lounge is a counter, not a warehouse.
    private void claimGift(ServerPlayer player, int index) {
        Account account = account(player);
        LoungeGift gift = account.takeGift(index);
        if (gift != null) {
            ItemStack stack = gift.stack();
            if (stack.isEmpty()) {
                FxEasy.LOGGER.warn("FX lounge gift {} is one this build cannot build, dropped from {}", gift.item(),
                        account.owner());
            } else {
                FxEasy.LOGGER.info("FX lounge gift claimed {} ×{} by {}", gift.item(), gift.count(),
                        player.getName().getString());
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
                notice(player, "fxeasy.notice.lounge.claim",
                        stack.getHoverName().getString() + " ×" + gift.count(), false);
            }
        }
        this.feed.sendAccount(player, account);
    }

    /// Wording of a plan a report speaks about: the tier by id, because a server never learns the language of the
    /// client it answers, and the price with the ticker of its currency, so a world that renames a currency
    /// still says what was charged.
    private static String describe(Membership plan) {
        Currency currency = Currencies.byId(plan.priceCurrencyId());
        String code = currency == null ? plan.priceCurrencyId() : currency.code();
        return plan.id().toUpperCase(Locale.ROOT) + " · " + plan.priceItems() + " × " + code;
    }

    /// What the platform actually charged for a plan, which is the price of the catalogue in the currency the
    /// buyer settled it in: the same figure the checkout quoted, as the platform charged it rather than as the
    /// client drew it.
    private static String describe(ShopEngine.BuyResult.Bought bought) {
        return bought.plan().id().toUpperCase(Locale.ROOT) + " · " + receipt(bought.priceMicro()) + " × "
                + bought.paying().code();
    }

    /// An amount of money as the count of items a receipt writes: grouped thousands and two decimals, with the
    /// decimals dropped when the amount is a whole number of items. A plan settled in the currency it is priced
    /// in then reads as the price of the catalogue and a converted one keeps what the conversion left.
    private static String receipt(long micro) {
        String formatted = String.format(Locale.ROOT, "%,.2f", Money.units(micro));
        int end = formatted.length();
        while (end > 0 && formatted.charAt(end - 1) == '0') {
            end--;
        }
        if (end > 0 && formatted.charAt(end - 1) == '.') {
            end--;
        }
        return formatted.substring(0, end);
    }

    private void notifyRenewal(Account account, ShopEngine.Renewal.Renewed renewed) {
        FxEasy.LOGGER.info("FX subscription {} renewed for {} until market tick {} for {}", renewed.plan().id(),
                account.owner(), renewed.untilTick(), describe(renewed.plan()));
        ServerPlayer player = this.server.getPlayerList().getPlayer(account.owner());
        if (player != null) {
            player.sendSystemMessage(Component.translatable("fxeasy.notice.shop.renew", describe(renewed.plan())));
        }
    }

    private void notifyLapsed(Account account, ShopEngine.Renewal.Expired expired) {
        FxEasy.LOGGER.info("FX subscription {} lapsed for {}", expired.plan().id(), account.owner());
        ServerPlayer player = this.server.getPlayerList().getPlayer(account.owner());
        if (player != null) {
            player.sendSystemMessage(Component.translatable("fxeasy.notice.shop.lapsed",
                    expired.plan().id().toUpperCase(Locale.ROOT)));
        }
    }

    /// Tells an account that is online that its lounge has something new in it, which is the only signal a gift
    /// has: a gift is state of the account rather than a message, and the card of the lounge is where it is read.
    private void notifyGift(Account account) {
        List<LoungeGift> gifts = account.loungeGifts();
        if (gifts.isEmpty()) {
            return;
        }
        LoungeGift latest = gifts.getLast();
        FxEasy.LOGGER.info("FX lounge gift {} ×{} for {}", latest.item(), latest.count(), account.owner());
        ServerPlayer player = this.server.getPlayerList().getPlayer(account.owner());
        if (player != null) {
            player.sendSystemMessage(Component.translatable("fxeasy.notice.lounge.new",
                    latest.stack().getHoverName().getString() + " ×" + latest.count()));
        }
    }

    /// Pushes the market tick and the account to every subscription, and the market tick alone to the account
    /// owners without one: the terminal's market list measures its window in ticks, so the client mirror has to
    /// keep sampling while the phone is closed. A payload without a subscription carries no forming candle.
    private void broadcast() {
        for (UUID owner : List.copyOf(this.subscriptions.keySet())) {
            ServerPlayer player = this.server.getPlayerList().getPlayer(owner);
            if (player == null) {
                this.subscriptions.remove(owner);
                continue;
            }
            this.feed.sendMarket(player, this.subscriptions.get(owner));
            this.feed.sendAccount(player, account(player));
        }
        for (Account account : List.copyOf(this.state.accounts().all())) {
            if (this.subscriptions.containsKey(account.owner())) {
                continue;
            }
            ServerPlayer player = this.server.getPlayerList().getPlayer(account.owner());
            if (player != null) {
                this.feed.sendMarket(player, null);
            }
        }
    }

    /// Sends the economy report and the event feed to every subscribed terminal, and only when the revision the
    /// client already has is behind. A player without a subscription never receives the full report.
    private void broadcastIntel() {
        for (UUID owner : List.copyOf(this.subscriptions.keySet())) {
            ServerPlayer player = this.server.getPlayerList().getPlayer(owner);
            if (player == null) {
                continue;
            }
            this.feed.sendEconomy(player, false);
            this.feed.sendEvents(player, false);
        }
    }

    private void subscribe(ServerPlayer player, int pairIndex, int timeframeIndex) {
        if (pairIndex < 0 || pairIndex >= Currencies.pairs().size() || timeframeIndex < 0 || timeframeIndex >= Timeframe.ALL.size()) {
            return;
        }
        CurrencyPair pair = Currencies.pair(pairIndex);
        Timeframe timeframe = Timeframe.byIndex(timeframeIndex);
        FxPlayerFeed.Subscription subscription = new FxPlayerFeed.Subscription(pair, timeframe);
        this.subscriptions.put(player.getUUID(), subscription);
        this.feed.sendCurrencies(player);
        this.feed.sendLoungePool(player);
        PacketDistributor.sendToPlayer(player, new FxHistoryPayload(pairIndex, timeframeIndex,
                this.state.market().series(pair, timeframe).timedSnapshot(true), this.economy.marketTick()));
        this.feed.sendMarket(player, subscription);
        this.feed.sendAccount(player, account(player));
        this.feed.sendEconomy(player, true);
        this.feed.sendEvents(player, true);
    }

    /// Pays the welcome capital on the request of the card that advertises it, which is the only way it is ever
    /// paid: the platform never hands money over on its own, so a player who never clicks the button keeps an
    /// empty account, and the flag in the account makes the second request pay nothing.
    private void claimBonus(ServerPlayer player) {
        Account account = account(player);
        if (!account.opened()) {
            this.feed.sendAccount(player, account);
            return;
        }
        FxConfig.Snapshot config = FxConfig.get();
        if (account.payWelcomeBonus(config.welcomeCurrency(), config.welcomeItems())) {
            welcome(player, account);
        }
        this.feed.sendAccount(player, account);
    }

    /// The welcome capital the platform hands a client that asks for it: without it a player has nothing to trade
    /// with until they have mined, and it is the only money the platform ever gives away. Paid once per account
    /// and remembered in the save, so asking again pays nothing.
    private void welcome(ServerPlayer player, Account account) {
        FxConfig.Snapshot config = FxConfig.get();
        String argument = config.welcomeItems() + " × " + config.welcomeCurrency().code();
        FxEasy.LOGGER.info("FX welcome capital of {} paid to {}", argument, player.getName().getString());
        player.sendSystemMessage(Component.translatable("fxeasy.notice.bonus", argument));
        notice(player, "fxeasy.notice.bonus", argument, false);
        this.feed.sendAccount(player, account);
    }

    private void order(ServerPlayer player, FxOrderPayload payload) {
        if (payload.pairIndex() < 0 || payload.pairIndex() >= Currencies.pairs().size() || payload.sideOrdinal() < 0
                || payload.sideOrdinal() >= Side.values().length || payload.marginCurrencyOrdinal() < 0
                || payload.marginCurrencyOrdinal() >= Currencies.count()) {
            return;
        }
        Account account = account(player);
        CurrencyPair pair = Currencies.pair(payload.pairIndex());
        Side side = Side.values()[payload.sideOrdinal()];
        Currency marginCurrency = Currencies.byIndex(payload.marginCurrencyOrdinal());
        MarketEngine market = this.state.market();
        switch (TradeEngine.open(account, pair, side, marginCurrency, payload.units(), payload.leverage(), market)) {
            case TradeEngine.OpenResult.Opened opened -> notice(player, "fxeasy.notice.open", describe(opened.position()), false);
            case TradeEngine.OpenResult.Rejected rejected -> notice(player, rejected.reason(), "", true);
        }
        this.feed.sendAccount(player, account);
    }

    private void closePosition(ServerPlayer player, long positionId) {
        Account account = account(player);
        switch (TradeEngine.close(account, positionId, this.state.market())) {
            case TradeEngine.CloseResult.Closed closed -> notice(player, "fxeasy.notice.closed", describe(closed.position()), false);
            case TradeEngine.CloseResult.Rejected rejected -> notice(player, rejected.reason(), "", true);
        }
        this.feed.sendAccount(player, account);
    }

    private void closeAll(ServerPlayer player) {
        Account account = account(player);
        TradeEngine.closeAll(account, this.state.market());
        this.feed.sendAccount(player, account);
    }

    private void deposit(ServerPlayer player, int currencyOrdinal, long units) {
        if (currencyOrdinal < 0 || currencyOrdinal >= Currencies.count()) {
            return;
        }
        Account account = account(player);
        Currency currency = Currencies.byIndex(currencyOrdinal);
        PlayerWallet wallet = new PlayerWallet(player);
        switch (TradeEngine.deposit(account, currency, units, wallet)) {
            case TradeEngine.DepositResult.Done done -> notice(player, "fxeasy.notice.deposit", done.units() + " × " + currency.code(), false);
            case TradeEngine.DepositResult.Rejected rejected -> notice(player, rejected.reason(), "", true);
        }
        this.feed.sendAccount(player, account);
    }

    private void withdraw(ServerPlayer player, int currencyOrdinal, long units) {
        if (currencyOrdinal < 0 || currencyOrdinal >= Currencies.count()) {
            return;
        }
        Account account = account(player);
        Currency currency = Currencies.byIndex(currencyOrdinal);
        PlayerWallet wallet = new PlayerWallet(player);
        switch (TradeEngine.withdraw(account, currency, units, wallet)) {
            case TradeEngine.WithdrawResult.Done done -> notice(player, "fxeasy.notice.withdraw", done.units() + " × " + currency.code(), false);
            case TradeEngine.WithdrawResult.Rejected rejected -> notice(player, rejected.reason(), "", true);
        }
        this.feed.sendAccount(player, account);
    }

    private void tutorialSeen(ServerPlayer player) {
        Account account = account(player);
        if (!account.tutorialSeen()) {
            account.markTutorialSeen();
            this.feed.sendAccount(player, account);
        }
    }

    private void openAccount(ServerPlayer player, String holder, int age) {
        Account account = account(player);
        if (account.register(holder, age)) {
            FxEasy.LOGGER.info("FX account registered by {} as {} (age {})", player.getName().getString(), account.holder(),
                    account.age());
            notice(player, "fxeasy.notice.account_open", "", false);
            this.feed.sendAccount(player, account);
        }
    }

    private Account account(ServerPlayer player) {
        return this.state.accounts().get(player.getUUID());
    }

    private void refresh(Account account) {
        ServerPlayer player = this.server.getPlayerList().getPlayer(account.owner());
        if (player != null && this.subscriptions.containsKey(account.owner())) {
            this.feed.sendMarket(player, this.subscriptions.get(account.owner()));
            this.feed.sendAccount(player, account);
        }
    }

    private void notifyMarginCall(Account account) {
        ServerPlayer player = this.server.getPlayerList().getPlayer(account.owner());
        if (player != null) {
            player.sendSystemMessage(Component.translatable("fxeasy.notice.margin_call"));
        }
        refresh(account);
    }

    private void notifyLiquidation(Account account) {
        ServerPlayer player = this.server.getPlayerList().getPlayer(account.owner());
        if (player != null) {
            player.sendSystemMessage(Component.translatable("fxeasy.notice.liquidated"));
        }
        refresh(account);
    }

    private void notice(ServerPlayer player, String key, String argument, boolean error) {
        PacketDistributor.sendToPlayer(player, new FxNoticePayload(key, argument, error));
    }

    private static String describe(Position position) {
        return position.units() + " " + position.pair().base().code() + " " + position.pair().code() + " "
                + position.leverage() + "x " + position.marginCurrency().code();
    }

    private void notifyDebtInterest(Account account, List<Account.Interest> charges) {
        ServerPlayer player = this.server.getPlayerList().getPlayer(account.owner());
        for (Account.Interest charge : charges) {
            String argument = String.format(Locale.ROOT, "%.2f %s", Money.units(charge.interestMicro()),
                    charge.currency().code());
            FxEasy.LOGGER.info("FX debt interest {} on {} for {}", argument, charge.currency().code(), account.owner());
            if (player != null) {
                player.sendSystemMessage(Component.translatable("fxeasy.notice.debt_interest", argument));
            }
        }
    }

    private void notifyCredit(Account account, boolean blacklisted) {
        FxEasy.LOGGER.info("FX credit {} for {}", blacklisted ? "blacklisted" : "restored", account.owner());
        ServerPlayer player = this.server.getPlayerList().getPlayer(account.owner());
        if (player != null) {
            player.sendSystemMessage(Component.translatable(
                    blacklisted ? "fxeasy.notice.credit_black" : "fxeasy.notice.credit_clear"));
        }
    }

    private void save() {
        this.tracker.snapshot();
        this.data.update(this.state.snapshot());
    }
}
