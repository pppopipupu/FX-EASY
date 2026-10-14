package com.pppopipupu.fxeasy.client;
import module java.base;
import com.pppopipupu.fxeasy.FxEasy;
import com.pppopipupu.fxeasy.market.Candle;
import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;
import com.pppopipupu.fxeasy.market.CurrencyPair;
import com.pppopipupu.fxeasy.market.FeeDiscount;
import com.pppopipupu.fxeasy.market.FeeModel;
import com.pppopipupu.fxeasy.market.Side;
import com.pppopipupu.fxeasy.market.TimedCandle;
import com.pppopipupu.fxeasy.market.Timeframe;
import com.pppopipupu.fxeasy.net.FxAccountPayload;
import com.pppopipupu.fxeasy.net.FxEconomyPayload;
import com.pppopipupu.fxeasy.net.FxEventsPayload;
import com.pppopipupu.fxeasy.net.FxEventsPayload.Event;
import com.pppopipupu.fxeasy.net.FxHistoryPayload;
import com.pppopipupu.fxeasy.net.FxLoungePoolPayload;
import com.pppopipupu.fxeasy.net.FxMarketPayload;
import com.pppopipupu.fxeasy.net.FxNoticePayload;
import com.pppopipupu.fxeasy.net.FxPositionPayload;
import com.pppopipupu.fxeasy.shop.LoungeFile;
import com.pppopipupu.fxeasy.shop.Membership;
import com.pppopipupu.fxeasy.trade.Money;
import com.pppopipupu.fxeasy.trade.TradeEngine;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/// Client side mirror of everything the terminal displays: the live currency prices, the candle history of every
/// pair and timeframe, the account snapshot, the order ticket inputs and the current selection. The server feed
/// writes into it through the `apply` methods, the panel only reads, and one instance lives for the whole
/// session, because the feed arrives whether or not the terminal is open.
public final class FxClientState {

    public static final FxClientState INSTANCE = new FxClientState();
    private static final int EQUITY_SAMPLES = 240;
    /// Grid the samples sit on, in game ticks.
    public static final int LISTING_STEP_TICKS = 5;
    /// Samples the ring holds, which is the longest window the terminal offers. Written out instead of derived
    /// from the timeframes because a ring sized from anything that is not a compile time constant can come out
    /// empty when the terminal is built during the initialization of the class that holds it.
    public static final int LISTING_SAMPLES = 3600;
    /// Prices of the session, indexed in the order its currency table names them. Only the ratios between them
    /// are meaningful, which is why every rate is one entry over another.
    private final double[] prices = new double[Currencies.MAX];
    private static final int MAX_PAIRS = Currencies.MAX * (Currencies.MAX - 1) / 2;
    private final double[][] listing = new double[MAX_PAIRS][LISTING_SAMPLES];
    /// The valuation of every currency on the same grid as the listing, because the intel overlay reports the
    /// change of a currency over the ticks of the selected timeframe.
    private final double[][] valuation = new double[Currencies.MAX][LISTING_SAMPLES];
    private int listingHead;
    private int listingSamples;
    private long listingTick;
    private final long[] balances = new long[Currencies.MAX];
    private final long[] realized = new long[Currencies.MAX];
    private final long[] freeMargin = new long[Currencies.MAX];
    /// What the exposure quota of this account still leaves it in each currency, in items of that currency, as
    /// the platform reported it; [Double#POSITIVE_INFINITY] while the platform limits no account.
    private final double[] riskRoom = new double[Currencies.MAX];
    /// Market tick the debt of one currency started at, zero while that balance is not in the red.
    private final long[] debtSince = new long[Currencies.MAX];
    private final Map<CurrencyPair, Map<Timeframe, List<TimedCandle>>> candles = new HashMap<>();
    private final List<FxPositionPayload> positions = new ArrayList<>();
    private final double[] equityHistory = new double[EQUITY_SAMPLES];
    private int equityHead;
    private int equitySamples;
    private int equityTicks;
    private CurrencyPair selectedPair = Currencies.pairs().getFirst();
    private Timeframe selectedTimeframe = Timeframe.M1;
    private CurrencyPair subscribedPair;
    private Timeframe subscribedTimeframe;
    private ChartStyle chartStyle = ChartStyle.CANDLES;
    private Candle forming;
    private Side orderSide = Side.BUY;
    /// Size of the order the ticket is building, in items of the base currency of the selected pair, between
    /// nothing and what [#maxOrderUnits] backs.
    private int orderUnits;
    private int orderLeverage = 100;
    /// Currency the next order locks its margin in. Any currency back any pair, so the ticket keeps whatever the
    /// player picked and only falls back to the quote of the selected pair until they pick one.
    private Currency orderMargin;
    private boolean connected;
    private boolean marginCall;
    /// Whether the platform has blacklisted the credit of this account, which stops it from opening anything
    /// new until the debt is repaid.
    private boolean blacklisted;
    /// Registry index of the currency every aggregate the panel draws is quoted in, which the player cycles.
    private int basisIndex;
    /// Registry index of the currency the platform pays its welcome capital in, and how many items of it.
    private int welcomeCurrencyIndex;
    private long welcomeItems;
    private double regime = 1.0;
    private long closedTrades;
    /// Whether the platform has an open account for this player. Like [tutorialSeen] the mirror only ever turns
    /// the flag on, so the panel cannot fall back behind the opening while an older snapshot is in flight.
    private boolean opened;
    /// Whether the platform has already walked this player through the terminal. The mirror only ever turns the
    /// flag on: the acknowledgement is sent while the panel is open, and an account snapshot that was already in
    /// flight would otherwise bring the walkthrough back for a frame.
    private boolean tutorialSeen;
    /// Whether the platform has handed this account its welcome capital, which is what takes the button that
    /// asks for it off the card and keeps the card away for good. The mirror only ever turns the flag on, so a
    /// snapshot that was already in flight cannot put the button back after the payment landed.
    private boolean bonusPaid;
    /// Name the account was registered under, empty until the platform has answered the opening.
    private String holder = "";
    /// Age the account was registered with.
    private int age;
    /// Plan the account is on, the market tick its period ends at and whether the platform renews it by itself.
    private Membership membership = Membership.NONE;
    private long membershipUntil;
    private boolean autoRenew;
    /// Market tick the lounge of the account hands its next gift out at, and what it is holding, oldest first,
    /// which is the order a claim addresses a gift by.
    private long loungeNext;
    private final List<ItemStack> lounge = new ArrayList<>();
    /// The pool the lounge of this world draws from, null until the platform has reported one: it is a
    /// definition of the world rather than state of the account, so it arrives with the session.
    private LoungeFile.Pool loungePool;
    private String noticeKey = "";
    private String noticeArgument = "";
    private boolean noticeError;
    private int noticeTicks;
    /// Newest economy report, null until the platform has sent one.
    private FxEconomyPayload economy;
    /// Newest public message feed, null until the platform has sent one. Rejected exactly like [#economy()].
    private FxEventsPayload events;
    private long eventsReadRevision;
    /// The market clock of the newest tick, which is the clock the history payload and the event feed are
    /// stamped with as well.
    private long marketTick = Long.MIN_VALUE;
    /// Market tick the newest candle format starts at. Anything the world did before it has no trustworthy time
    /// anchor, so the chart must not place a marker there.
    private long chartEpoch;

    public boolean connected() {
        return this.connected;
    }

    /// A tick is only readable against the currency set it was built for, and the table arrives before the
    /// first one of a session: a vector of another length belongs to another set and is dropped whole rather
    /// than indexed.
    public void applyMarket(FxMarketPayload payload) {
        double[] incoming = payload.prices();
        if (incoming.length != Currencies.count()) {
            return;
        }
        System.arraycopy(incoming, 0, this.prices, 0, incoming.length);
        if (payload.forming() != null) {
            this.regime = payload.regime();
            this.forming = payload.forming();
        }
        this.connected = true;
        if (payload.marketTick() < this.marketTick) {
            this.newWorld();
        }
        this.marketTick = payload.marketTick();
        this.chartEpoch = payload.chartEpoch();
        this.sampleListing(payload.marketTick());
    }

    /// The market clock of the newest tick, which is the clock the history payload and the event feed are
    /// stamped with as well.
    public long marketTick() {
        return this.marketTick;
    }

    /// Market tick the newest candle format starts at. Anything the world did before it has no trustworthy time
    /// anchor, so the chart must not place a marker there.
    public long chartEpoch() {
        return this.chartEpoch;
    }

    /// Forgets the report, the feed and the watermark, which a market clock that went backwards means: neither
    /// of them belongs to the world the client just entered.
    private void newWorld() {
        this.economy = null;
        this.events = null;
        this.eventsReadRevision = 0L;
        this.chartEpoch = 0L;
        this.loungePool = null;
        this.clearAccount();
    }

    /// Drops the market of the world the client was in, which is what entering another one does. The regime is
    /// not part of it: the costs of the new world are only known once its first tick arrives.
    public void reset() {
        Arrays.fill(this.prices, 0.0);
        Arrays.fill(this.balances, 0L);
        Arrays.fill(this.realized, 0L);
        Arrays.fill(this.freeMargin, 0L);
        Arrays.fill(this.riskRoom, 0.0D);
        Arrays.fill(this.debtSince, 0L);
        this.positions.clear();
        this.candles.clear();
        this.listingHead = 0;
        this.listingSamples = 0;
        this.listingTick = 0L;
        this.connected = false;
        this.selectedPair = Currencies.pairs().getFirst();
        this.selectedTimeframe = Timeframe.M1;
        this.subscribedPair = null;
        this.subscribedTimeframe = null;
        this.forming = null;
        this.chartStyle = ChartStyle.CANDLES;
        this.orderSide = Side.BUY;
        this.orderUnits = 0;
        this.orderLeverage = Membership.NONE.maxLeverage();
        this.orderMargin = null;
        this.basisIndex = 0;
        this.marketTick = Long.MIN_VALUE;
        this.noticeKey = "";
        this.noticeArgument = "";
        this.noticeError = false;
        this.noticeTicks = 0;
        this.newWorld();
    }

    private void clearAccount() {
        Arrays.fill(this.balances, 0L);
        Arrays.fill(this.realized, 0L);
        Arrays.fill(this.freeMargin, 0L);
        Arrays.fill(this.riskRoom, 0.0D);
        Arrays.fill(this.debtSince, 0L);
        this.positions.clear();
        this.equityHistory[0] = 0.0;
        this.equityHead = 0;
        this.equitySamples = 0;
        this.equityTicks = 0;
        this.welcomeCurrencyIndex = 0;
        this.welcomeItems = 0L;
        this.marginCall = false;
        this.blacklisted = false;
        this.closedTrades = 0L;
        this.opened = false;
        this.tutorialSeen = false;
        this.bonusPaid = false;
        this.holder = "";
        this.age = 0;
        this.membership = Membership.NONE;
        this.membershipUntil = 0L;
        this.autoRenew = false;
        this.loungeNext = 0L;
        this.lounge.clear();
    }

    /// Newest economy report, null until the platform has sent one.
    public FxEconomyPayload economy() {
        return this.economy;
    }

    public void applyEconomy(FxEconomyPayload payload) {
        if (this.economy == null || payload.revision() > this.economy.revision()) {
            this.economy = payload;
        }
    }

    /// Newest public message feed, null until the platform has sent one. Rejected exactly like [#economy()].
    public FxEventsPayload events() {
        return this.events;
    }

    public void applyEvents(FxEventsPayload payload) {
        if (this.events == null || payload.revision() > this.events.revision()) {
            this.events = payload;
        }
    }

    /// Messages the newest feed carries, one entry per root event at most as the server sends it.
    public int eventCount() {
        return this.events == null ? 0 : this.events.events().size();
    }

    /// Messages the player has not looked at yet: every entry of the newest feed whose own revision is newer than
    /// the watermark [#markEventsRead()] last recorded. A repeat update of a known root counts again, because it
    /// carries new information about it.
    public int unreadEvents() {
        if (this.events == null) {
            return 0;
        } else {
            int unread = 0;
            for (Event event : this.events.events()) {
                if (event.revision() > this.eventsReadRevision) {
                    unread++;
                }
            }
            return unread;
        }
    }

    public void markEventsRead() {
        if (this.events != null) {
            for (Event event : this.events.events()) {
                this.eventsReadRevision = Math.max(this.eventsReadRevision, event.revision());
            }
        }
    }

    /// Samples the price and the valuation of every currency on the [#LISTING_STEP_TICKS] grid of market ticks.
    /// The feed arrives more often than the grid, so a packet that lands on a slot the ring already holds is
    /// ignored and the newest price fills the slots the feed skipped.
    private void sampleListing(long tick) {
        if (this.listingSamples != 0 && tick >= this.listingTick && tick - this.listingTick <= 200L) {
            long steps = (tick - this.listingTick) / 5L;
            for (long step = 0L; step < steps; step++) {
                this.pushListing(this.listingTick + 5L);
            }
        } else {
            this.listingSamples = 0;
            this.pushListing(tick);
        }
    }

    private void pushListing(long tick) {
        if (this.listingSamples > 0) {
            this.listingHead = this.listingHead + 1 == LISTING_SAMPLES ? 0 : this.listingHead + 1;
            this.listingSamples = Math.min(this.listingSamples + 1, LISTING_SAMPLES);
        } else {
            this.listingSamples = 1;
        }
        this.listingTick = tick;
        for (Currency currency : Currencies.all()) {
            int index = Currencies.index(currency);
            this.valuation[index][this.listingHead] = this.prices[index];
        }
        for (CurrencyPair pair : Currencies.pairs()) {
            this.listing[pair.index()][this.listingHead] = this.price(pair);
        }
    }

    public int listingSamples() {
        return this.listingSamples;
    }

    /// Samples the list column covers for that timeframe, which is all of them until the window has filled.
    public int listingWindow(Timeframe timeframe) {
        return Math.min(this.listingSamples, timeframe.ticks() / 5);
    }

    private double listingSample(CurrencyPair pair, int index) {
        return this.listing[pair.index()][this.ringSlot(index)];
    }

    /// Slot of the sample [index] of the window, oldest first, in every ring the mirror keeps on the listing grid.
    private int ringSlot(int index) {
        return (this.listingHead - this.listingSamples + 1 + index + 2 * LISTING_SAMPLES) % LISTING_SAMPLES;
    }

    /// Price at [index] of the window the timeframe covers, oldest first.
    public double listingWindowSample(CurrencyPair pair, Timeframe timeframe, int index) {
        return this.listingSample(pair, this.listingSamples - this.listingWindow(timeframe) + index);
    }

    /// Percentage change of the pair over the ticks the timeframe covers.
    public double listingChange(CurrencyPair pair, Timeframe timeframe) {
        int window = this.listingWindow(timeframe);
        if (window < 2) {
            return 0.0;
        }
        double first = this.listingSample(pair, this.listingSamples - window);
        return first == 0.0 ? 0.0 : (this.listingSample(pair, this.listingSamples - 1) / first - 1.0) * 100.0;
    }

    /// Percentage change of one currency's own valuation over the ticks the timeframe covers, which is the
    /// window the intel overlay reports for a currency that is not a pair.
    public double valuationChange(Currency currency, Timeframe timeframe) {
        int window = this.listingWindow(timeframe);
        if (window < 2) {
            return 0.0;
        }
        int index = Currencies.index(currency);
        double first = this.valuation[index][this.ringSlot(this.listingSamples - window)];
        return first == 0.0 ? 0.0 : (this.valuation[index][this.ringSlot(this.listingSamples - 1)] / first - 1.0) * 100.0;
    }

    /// Replaces the series of one pair and timeframe with what the server answered.
    public void applyHistory(FxHistoryPayload payload) {
        this.forming = null;
        this.setCandles(Currencies.pair(payload.pairIndex()), Timeframe.byIndex(payload.timeframeIndex()), payload.candles());
    }

    public void applyAccount(FxAccountPayload payload) {
        System.arraycopy(payload.balances(), 0, this.balances, 0, Math.min(payload.balances().length, this.balances.length));
        System.arraycopy(payload.realized(), 0, this.realized, 0, Math.min(payload.realized().length, this.realized.length));
        System.arraycopy(payload.freeMargin(), 0, this.freeMargin, 0, Math.min(payload.freeMargin().length, this.freeMargin.length));
        System.arraycopy(payload.riskRoom(), 0, this.riskRoom, 0, Math.min(payload.riskRoom().length, this.riskRoom.length));
        System.arraycopy(payload.debtSince(), 0, this.debtSince, 0, Math.min(payload.debtSince().length, this.debtSince.length));
        this.positions.clear();
        this.positions.addAll(payload.positions());
        this.welcomeCurrencyIndex = payload.welcomeCurrency();
        this.welcomeItems = payload.welcomeItems();
        this.marginCall = payload.marginCall();
        this.blacklisted = payload.blacklisted();
        this.closedTrades = payload.closedTrades();
        this.opened = this.opened | payload.opened();
        this.tutorialSeen = this.tutorialSeen | payload.tutorialSeen();
        this.bonusPaid = this.bonusPaid | payload.bonusPaid();
        if (!payload.holder().isEmpty()) {
            this.holder = payload.holder();
            this.age = payload.age();
        }
        this.membership = Membership.byId(payload.membership());
        this.membershipUntil = payload.membershipUntil();
        this.autoRenew = payload.autoRenew();
        this.loungeNext = payload.loungeNext();
        this.lounge.clear();
        this.lounge.addAll(payload.lounge());
        this.orderLeverage = Math.clamp(this.orderLeverage, TradeEngine.MIN_LEVERAGE, this.maxOrderLeverage());
        this.connected = true;
    }

    /// Whether the platform has an open account for this player. Like [tutorialSeen] the mirror only ever turns
    /// the flag on, so the panel cannot fall back behind the opening while an older snapshot is in flight.
    public boolean opened() {
        return this.opened;
    }

    public void markOpened(String holder, int age) {
        this.opened = true;
        this.holder = holder;
        this.age = age;
    }

    /// Name the account was registered under, empty until the platform has answered the opening.
    public String holder() {
        return this.holder;
    }

    /// Age the account was registered with.
    public int age() {
        return this.age;
    }

    /// Whether the platform has already walked this player through the terminal. The mirror only ever turns the
    /// flag on: the acknowledgement is sent while the panel is open, and an account snapshot that was already in
    /// flight would otherwise bring the walkthrough back for a frame.
    public boolean tutorialSeen() {
        return this.tutorialSeen;
    }

    public void markTutorialSeen() {
        this.tutorialSeen = true;
    }

    public void applyNotice(FxNoticePayload payload) {
        this.noticeKey = payload.key();
        this.noticeArgument = payload.argument();
        this.noticeError = payload.error();
        this.noticeTicks = 240;
        FxEasy.LOGGER.info("FX notice {}", payload.key());
    }

    /// Whether the platform has handed this account its welcome capital, which is what takes the button that
    /// asks for it off the card and keeps the card away for good. The mirror only ever turns the flag on, so a
    /// snapshot that was already in flight cannot put the button back after the payment landed.
    public boolean bonusPaid() {
        return this.bonusPaid;
    }

    /// Plan the account is on, [Membership#NONE] while it has none.
    public Membership membership() {
        return this.membership;
    }

    /// Market tick the period of the plan ends at, which is what the shop page counts down.
    public long membershipUntil() {
        return this.membershipUntil;
    }

    /// Market ticks the period of the plan still runs, zero while the account is on no plan or its period is
    /// over, and zero until the first tick of the world has arrived.
    public long membershipTicksLeft() {
        if (this.membership == Membership.NONE || this.marketTick == Long.MIN_VALUE) {
            return 0L;
        }
        return Math.max(0L, this.membershipUntil - this.marketTick);
    }

    /// Whether the platform charges the next period of the plan by itself, which is what the switch on the shop
    /// page turns off and on again.
    public boolean autoRenew() {
        return this.autoRenew;
    }

    /// Highest leverage an order of this account may name, which is what its plan buys.
    public int maxOrderLeverage() {
        return this.membership.maxLeverage();
    }

    /// What the plan of this account takes off every cost the platform charges, which is what the metrics strip
    /// and the ticket read their figures through.
    public FeeDiscount feeDiscount() {
        return this.membership.discount();
    }

    /// Gifts the lounge of the account is holding, oldest first, which is the order a claim addresses them by.
    public List<ItemStack> lounge() {
        return Collections.unmodifiableList(this.lounge);
    }

    /// Whether the plan of this account carries the lounge at all, which is what the cell that opens it is drawn
    /// on.
    public boolean loungeOpen() {
        return this.membership.lounge();
    }

    /// The pool the lounge of this world draws its gifts from, or null while the platform has not reported one.
    /// The shop page writes out the pool of the world it is connected to and never one of its own, because the
    /// file belongs to the world and not to the client.
    public LoungeFile.Pool loungePool() {
        return this.loungePool;
    }

    /// Takes the pool of the session, which the platform sends when the terminal subscribes. Every bound of the
    /// file is applied on the way in, so the page draws the list the lounge itself draws from.
    public void applyLoungePool(FxLoungePoolPayload payload) {
        this.loungePool = payload.pool();
    }

    /// Market ticks until the lounge hands its next gift out, zero while its clock has not started or the plan
    /// carries no lounge.
    public long loungeTicksLeft() {
        if (this.loungeNext == 0L || this.marketTick == Long.MIN_VALUE) {
            return 0L;
        }
        return Math.max(0L, this.loungeNext - this.marketTick);
    }

    /// Equity is sampled client side once a second, in items of the display currency; the server keeps no equity
    /// series, so the curve spans the current session.
    public void tick() {
        if (this.noticeTicks > 0) {
            this.noticeTicks--;
        }
        this.sampleEquity();
    }

    private void sampleEquity() {
        if (++this.equityTicks >= 20) {
            this.equityTicks = 0;
            this.equityHistory[this.equityHead] = this.equityItems(this.basis());
            this.equityHead = (this.equityHead + 1) % 240;
            if (this.equitySamples < 240) {
                this.equitySamples++;
            }
        }
    }

    public int equitySamples() {
        return this.equitySamples;
    }

    /// Sample [index] of the session, oldest first.
    public double equitySample(int index) {
        return this.equityHistory[(this.equityHead - this.equitySamples + index + 480) % 240];
    }

    public boolean noticeVisible() {
        return this.noticeTicks > 0 && !this.noticeKey.isEmpty();
    }

    public Component noticeComponent() {
        return this.noticeArgument.isEmpty() ? FxText.get(this.noticeKey) : FxText.get(this.noticeKey, this.noticeArgument);
    }

    public boolean noticeError() {
        return this.noticeError;
    }

    /// Items of [to] one item of [from] is worth right now, which is the ratio of the two mirrored prices.
    public double rate(Currency from, Currency to) {
        return this.prices[Currencies.index(from)] / this.prices[Currencies.index(to)];
    }

    /// Mid price of a pair: items of its quote one item of its base is worth right now.
    public double price(CurrencyPair pair) {
        return this.rate(pair.base(), pair.quote());
    }

    /// Currency every aggregate the panel draws is quoted in, which the player picks.
    public Currency basis() {
        return Currencies.byIndex(Math.clamp(this.basisIndex, 0, Currencies.count() - 1));
    }

    /// Advances the display currency to the next one of the table and restarts the equity curve, whose samples
    /// are in the currency that was showing.
    public void cycleBasis() {
        this.basisIndex = (Math.clamp(this.basisIndex, 0, Currencies.count() - 1) + 1) % Currencies.count();
        this.equityHead = 0;
        this.equitySamples = 0;
        this.equityTicks = 0;
        FxEasy.LOGGER.info("FX terminal basis {}", this.basis().code());
    }

    /// Currency the platform pays its welcome capital in, or the first currency of the table before the account
    /// snapshot has arrived.
    public Currency welcomeCurrency() {
        return Currencies.byIndex(Math.clamp(this.welcomeCurrencyIndex, 0, Currencies.count() - 1));
    }

    /// Items of [welcomeCurrency] the platform pays, zero until the account snapshot has arrived.
    public long welcomeItems() {
        return this.welcomeItems;
    }

    public double regime() {
        return this.regime;
    }

    public double spreadHalf() {
        return FeeModel.halfSpread(this.regime, feeDiscount());
    }

    /// Total spread the platform charges around a mid rate when it converts one currency into another, which is
    /// what the metrics strip prints and what the plan of the account has already been taken off.
    public double conversionSpread() {
        return FeeModel.conversionSpread(feeDiscount());
    }

    /// Swap one market day of a position costs as a fraction of its notional, positive when the position pays
    /// the platform and negative when the platform pays it, which is the side that holds the higher yielding
    /// currency of the pair. The carry is the interest difference, so the regime never widens it.
    public double swapPerDay(CurrencyPair pair, Side side) {
        return FeeModel.swapPerDay(pair, side, feeDiscount());
    }

    public double slippageChance() {
        return FeeModel.slippageChance(this.regime);
    }

    public double bid(CurrencyPair pair) {
        return this.price(pair) * (1.0 - this.spreadHalf());
    }

    public double ask(CurrencyPair pair) {
        return this.price(pair) * (1.0 + this.spreadHalf());
    }

    public void setCandles(CurrencyPair pair, Timeframe timeframe, List<TimedCandle> series) {
        this.candles.computeIfAbsent(pair, key -> new EnumMap<>(Timeframe.class)).put(timeframe, List.copyOf(series));
    }

    public List<TimedCandle> candles(CurrencyPair pair, Timeframe timeframe) {
        return this.candles.getOrDefault(pair, Map.of()).getOrDefault(timeframe, List.of());
    }

    /// Closed candles of the current selection plus the candle still forming on the server. The forming candle
    /// the feed carries has no interval of its own, so it replaces the newest candle the history snapshot
    /// anchored when it covers the same market tick and opens a new interval otherwise.
    public List<TimedCandle> chartCandles() {
        List<TimedCandle> series = this.candles(this.selectedPair, this.selectedTimeframe);
        if (this.forming != null && this.selectedPair.equals(this.subscribedPair) && this.selectedTimeframe == this.subscribedTimeframe) {
            List<TimedCandle> merged = new ArrayList<>(series.size() + 1);
            merged.addAll(series);
            if (!series.isEmpty() && series.getLast().covers(this.marketTick)) {
                TimedCandle last = series.getLast();
                merged.set(merged.size() - 1, new TimedCandle(last.startTick(), last.endTick(), this.forming));
                return merged;
            } else {
                int ticks = this.selectedTimeframe.ticks();
                long start = series.isEmpty() ? this.marketTick - Math.floorMod(this.marketTick, ticks) : series.getLast().endTick();
                merged.add(new TimedCandle(start, start + ticks, this.forming));
                return merged;
            }
        } else {
            return series;
        }
    }

    public long balance(Currency currency) {
        return this.balances[Currencies.index(currency)];
    }

    public long realized(Currency currency) {
        return this.realized[Currencies.index(currency)];
    }

    public long freeMargin(Currency currency) {
        return this.freeMargin[Currencies.index(currency)];
    }

    public List<FxPositionPayload> positions() {
        return List.copyOf(this.positions);
    }

    /// Net worth of the account in items of one currency: every balance and every open profit is converted into
    /// it at the live rate. Zero before the basis has a price, which is what the panel draws as nothing.
    public double equityItems(Currency basis) {
        double unit = this.prices[Currencies.index(basis)];
        if (!(unit > 0.0) || !Double.isFinite(unit)) {
            return 0.0;
        }
        double equity = 0.0;
        for (Currency currency : Currencies.all()) {
            equity += Money.units(this.balance(currency) + this.unrealizedMicro(currency)) * this.rate(currency, basis);
        }
        return equity;
    }

    /// Margin currently locked, in items of one currency at the live rate: a currency locks what its balance
    /// holds over what is free.
    public double usedMarginItems(Currency basis) {
        double unit = this.prices[Currencies.index(basis)];
        if (!(unit > 0.0) || !Double.isFinite(unit)) {
            return 0.0;
        }
        double used = 0.0;
        for (Currency currency : Currencies.all()) {
            used += Money.units(this.lockedMarginMicro(currency)) * this.rate(currency, basis);
        }
        return used;
    }

    /// Unrealised profit of every open position, in items of one currency at the live rate.
    public double unrealizedItems(Currency basis) {
        double unit = this.prices[Currencies.index(basis)];
        if (!(unit > 0.0) || !Double.isFinite(unit)) {
            return 0.0;
        }
        double pnl = 0.0;
        for (Currency currency : Currencies.all()) {
            pnl += Money.units(this.unrealizedMicro(currency)) * this.rate(currency, basis);
        }
        return pnl;
    }

    /// Unrealised profit of every position that settles in [currency], in micro units of it.
    public long unrealizedMicro(Currency currency) {
        long pnl = 0L;
        for (FxPositionPayload position : this.positions) {
            if (this.marginCurrency(position) == currency) {
                pnl += position.pnlMicro();
            }
        }
        return pnl;
    }

    /// Unrealised profit of one position in items of the currency its pair settles in, marked at the price the
    /// server last valued it against.
    public double unrealizedQuoteItems(FxPositionPayload position) {
        double signed = position.sideOrdinal() == Side.BUY.ordinal() ? position.units() : -position.units();
        return signed * (position.markPrice() - position.openPrice());
    }

    /// Unrealised profit of one position in micro units of the currency it locked its margin in.
    public long unrealizedMarginMicro(FxPositionPayload position) {
        return position.pnlMicro();
    }

    /// Margin one currency has locked, in micro units, which is the balance of it that is not free.
    private long lockedMarginMicro(Currency currency) {
        return this.balance(currency) - this.freeMargin(currency);
    }

    private Currency marginCurrency(FxPositionPayload position) {
        return Currencies.byIndex(position.marginCurrencyOrdinal());
    }

    /// Equity of one currency over the margin locked in that same currency, `inf` while it locks none: a
    /// currency's own debt cannot be covered by another currency's balance.
    public double marginLevel(Currency currency) {
        long used = this.lockedMarginMicro(currency);
        if (used <= 0L) {
            return Double.POSITIVE_INFINITY;
        }
        long net = this.balance(currency) + this.unrealizedMicro(currency);
        return Money.units(net) / Money.units(used);
    }

    /// Lowest margin level of the currencies that lock margin, `inf` while none does.
    public double marginLevel() {
        double lowest = Double.POSITIVE_INFINITY;
        for (Currency currency : Currencies.all()) {
            if (this.lockedMarginMicro(currency) > 0L) {
                lowest = Math.min(lowest, this.marginLevel(currency));
            }
        }
        return lowest;
    }

    /// What the account owes in one currency, in micro units of it, zero while that balance is not in the red.
    public long debt(Currency currency) {
        return Math.max(0L, -this.balance(currency));
    }

    /// Market tick the debt of one currency started at, zero while that balance is not in the red.
    public long debtSince(Currency currency) {
        return this.debtSince[Currencies.index(currency)];
    }

    /// Game days the oldest debt of one currency has been outstanding for, which is what the credit record of
    /// the account is measured in.
    public double debtAgeDays(Currency currency) {
        long since = this.debtSince[Currencies.index(currency)];
        return since != 0L && this.marketTick >= since ? (this.marketTick - since) / 24000.0 : 0.0;
    }

    /// Whether the platform has blacklisted the credit of this account, which stops it from opening anything
    /// new until the debt is repaid.
    public boolean blacklisted() {
        return this.blacklisted;
    }

    public boolean marginCall() {
        return this.marginCall;
    }

    public long closedTrades() {
        return this.closedTrades;
    }

    public CurrencyPair selectedPair() {
        return this.selectedPair;
    }

    public void selectPair(CurrencyPair pair) {
        if (!this.selectedPair.equals(pair)) {
            this.selectedPair = pair;
            FxEasy.LOGGER.info("FX terminal pair {}", pair.code());
        }
    }

    public Timeframe selectedTimeframe() {
        return this.selectedTimeframe;
    }

    public void selectTimeframe(Timeframe timeframe) {
        if (this.selectedTimeframe != timeframe) {
            this.selectedTimeframe = timeframe;
            FxEasy.LOGGER.info("FX terminal timeframe {}", timeframe.label());
        }
    }

    public void onSubscribed(CurrencyPair pair, Timeframe timeframe) {
        this.subscribedPair = pair;
        this.subscribedTimeframe = timeframe;
        this.forming = null;
        this.connected = false;
    }

    public void onUnsubscribed() {
        this.subscribedPair = null;
        this.subscribedTimeframe = null;
        this.forming = null;
    }

    public ChartStyle chartStyle() {
        return this.chartStyle;
    }

    public void setChartStyle(ChartStyle style) {
        if (this.chartStyle != style) {
            this.chartStyle = style;
            FxEasy.LOGGER.info("FX terminal chart style {}", style);
        }
    }

    public Side orderSide() {
        return this.orderSide;
    }

    public void setOrderSide(Side side) {
        this.orderSide = side;
    }

    /// Size of the order the ticket is building, in items of the base currency of the selected pair, between
    /// nothing and what [#maxOrderUnits] backs.
    public int orderUnits() {
        return this.orderUnits;
    }

    /// Moves the size of the order into the range the margin backs: an order that was not sized reads zero, and
    /// a size the free margin no longer covers at the leverage of the order is pulled down to what it covers.
    public void setOrderUnits(int units) {
        int clamped = Math.clamp(units, 0, this.maxOrderUnits());
        if (clamped != this.orderUnits) {
            this.orderUnits = clamped;
            FxEasy.LOGGER.info("FX terminal size {}", clamped);
        }
    }

    public int orderLeverage() {
        return this.orderLeverage;
    }

    public void setOrderLeverage(int leverage) {
        this.orderLeverage = Math.clamp(leverage, TradeEngine.MIN_LEVERAGE, this.maxOrderLeverage());
    }

    /// What an order of that size is worth in micro units of the quote currency of the pair, which is its size
    /// at the live price: a hundred items of a pair trading at 2.00 are 200 items of its quote.
    public long notional(CurrencyPair pair, int units) {
        return Money.round(units * this.price(pair));
    }

    /// Currency the next order locks its margin in. Any currency back any pair, so the ticket keeps whatever the
    /// player picked and only falls back to the quote of the selected pair until they pick one.
    public Currency orderMargin(CurrencyPair pair) {
        return this.orderMargin == null ? pair.quote() : this.orderMargin;
    }

    public void setOrderMargin(Currency currency) {
        this.orderMargin = currency;
    }

    /// Margin the platform would lock for an order of that size, which it prices off the collateral value of the
    /// currency: a margin currency that is not what the pair settles in is worth less than its mid value.
    public long requiredMargin(Currency currency, int units, int leverage) {
        return TradeEngine.requiredMargin(units, leverage, this.price(this.selectedPair),
                FeeModel.collateralRate(this.selectedPair, currency,
                        this.rate(currency, this.selectedPair.quote()), feeDiscount()));
    }

    /// Largest size the ticket may build, in items of the base currency of the selected pair: what the free
    /// margin of the currency the order locks backs at the leverage of the order, what the exposure quota of this
    /// account still leaves on either currency of the pair, and the size the platform accepts at the most,
    /// whichever binds first, and zero while that margin backs nothing. The size slider of the ticket runs from
    /// zero to this, so the panel needs no other way to set a size and never offers one the platform would refuse
    /// for want of margin or for want of the account's own quota; what the quota of the whole server still leaves
    /// is not part of it, because only the server can know it, and a size that runs into it is refused and
    /// reported rather than never offered.
    public int maxOrderUnits() {
        CurrencyPair pair = this.selectedPair;
        Currency margin = this.orderMargin(pair);
        double price = this.price(pair);
        double collateral = FeeModel.collateralRate(pair, margin, this.rate(margin, pair.quote()), feeDiscount());
        long free = this.freeMargin(margin);
        if (free <= 0L || !(price > 0.0D) || !(collateral > 0.0D)) {
            return 0;
        }
        double units = Money.units(free) * this.orderLeverage * collateral / price;
        units = Math.min(units, this.riskRoomUnits(pair, price));
        int maximum = (int) Math.clamp(units, 0.0D, TradeEngine.MAX_ORDER_UNITS);
        return maximum > 0 && this.requiredMargin(margin, maximum, this.orderLeverage) > free ? maximum - 1 : maximum;
    }

    /// Size the exposure quota of this account still leaves on a pair, in items of its base currency: the room
    /// the platform reported on the base itself, and the room on the quote converted at the live rate, because a
    /// position carries its size in the base while its exposure in the quote is what that size is worth. Both
    /// rooms are absolute, so an order that hedges an open position spends them like any other.
    private double riskRoomUnits(CurrencyPair pair, double price) {
        double base = this.riskRoom[Currencies.index(pair.base())];
        double quote = this.riskRoom[Currencies.index(pair.quote())] / price;
        return Math.max(0.0D, Math.min(base, quote));
    }

    public static enum ChartStyle {
        CANDLES,
        LINE;
        private ChartStyle() {
        }
    }
}
