package com.pppopipupu.fxeasy.trade;

import module java.base;

import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;
import com.pppopipupu.fxeasy.market.FeeDiscount;
import com.pppopipupu.fxeasy.market.MarketEngine;
import com.pppopipupu.fxeasy.shop.LoungeGift;
import com.pppopipupu.fxeasy.shop.Membership;

/// Trading account of one player: five per-currency balances plus the open positions. Balances are kept in
/// micro units of a single item each, so one diamond balance unit is one diamond item. A balance may go
/// negative, which is debt the platform keeps on the books and charges interest on. Every position locks its
/// margin in one currency and settles in it, so what is free to trade with is a per-currency figure.
///
/// The credit record of the account is the age of its oldest debt: a currency is marked the tick its balance
/// first goes negative and the mark is cleared as soon as that balance is back at or above zero, so a debt that
/// is partly repaid still ages, and one that is repaid in full stops counting.
public final class Account {

    private static final int MAX_HOLDER_LENGTH = 24;
    private static final int MAX_AGE = 200;

    private final UUID owner;
    private final long[] balances = new long[Currencies.count()];
    private final long[] realized = new long[Currencies.count()];
    private final long[] debtSince = new long[Currencies.count()];
    private final long[] debtDue = new long[Currencies.count()];
    private final List<Position> positions = new ArrayList<>();
    private final List<LoungeGift> lounge = new ArrayList<>();
    private long nextPositionId = 1L;
    private long closedTrades;
    private boolean opened;
    private boolean tutorialSeen;
    private boolean bonusPaid;
    private String holder = "";
    private int age;
    private Membership membership = Membership.NONE;
    private long membershipUntil;
    private boolean autoRenew;
    private long loungeNext;

    public Account(UUID owner) {
        this.owner = owner;
    }

    public UUID owner() {
        return this.owner;
    }

    public List<Position> positions() {
        return Collections.unmodifiableList(this.positions);
    }

    public int positionCount() {
        return this.positions.size();
    }

    public long closedTrades() {
        return this.closedTrades;
    }

    public boolean opened() {
        return this.opened;
    }

    /// Name the account was registered under, empty while the player has not been through the opening.
    public String holder() {
        return this.holder;
    }

    /// Age the account was registered with. The platform stores what the client told it: the age of majority is
    /// the terminal's joke, not a rule this side enforces.
    public int age() {
        return this.age;
    }

    /// Records the registration and opens the account, which only the first one does: a later request, however
    /// it is dressed up, cannot rewrite the name on the ledger. The name is cut to a length the panel can draw
    /// and the age to a range the ledger can hold.
    public boolean register(String holder, int age) {
        if (this.opened) {
            return false;
        }
        String trimmed = holder.strip();
        this.holder = trimmed.length() > MAX_HOLDER_LENGTH ? trimmed.substring(0, MAX_HOLDER_LENGTH) : trimmed;
        this.age = Math.clamp(age, 0, MAX_AGE);
        this.opened = true;
        return true;
    }

    public boolean bonusPaid() {
        return this.bonusPaid;
    }

    public void restoreBonusPaid(boolean paid) {
        this.bonusPaid = paid;
    }

    /// Credits the welcome capital the platform gives a client the first time it opens the terminal, in the
    /// currency and the count the world's configuration names. Returns whether it was paid now, so the caller
    /// can say so; a later call never pays a second time.
    public boolean payWelcomeBonus(Currency currency, long items) {
        if (this.bonusPaid) {
            return false;
        }
        this.bonusPaid = true;
        addBalance(currency, items * Money.UNIT);
        return true;
    }

    public boolean tutorialSeen() {
        return this.tutorialSeen;
    }

    public void markTutorialSeen() {
        this.tutorialSeen = true;
    }

    /// Plan the account is on, [Membership#NONE] while it has none.
    public Membership membership() {
        return this.membership;
    }

    /// Market tick the current period of the plan ends at, zero while the account is on no plan.
    public long membershipUntil() {
        return this.membershipUntil;
    }

    /// Whether the platform charges the next period of the plan by itself when this one ends. A plan is always
    /// bought renewed, and cancelling only turns this flag off.
    public boolean autoRenew() {
        return this.autoRenew;
    }

    public void setAutoRenew(boolean autoRenew) {
        this.autoRenew = autoRenew;
    }

    public int maxLeverage() {
        return this.membership.maxLeverage();
    }

    public FeeDiscount feeDiscount() {
        return this.membership.discount();
    }

    /// Puts the account on a plan until [untilTick]. The price is charged before this is called and the period
    /// is never extended here: the shop decides what a purchase means for the period that was already paid.
    public void startMembership(Membership plan, long untilTick, boolean renew) {
        this.membership = plan;
        this.membershipUntil = untilTick;
        this.autoRenew = renew;
    }

    public void endMembership() {
        this.membership = Membership.NONE;
        this.membershipUntil = 0L;
        this.autoRenew = false;
    }

    /// Market tick the lounge of this account delivers its next gift at, zero while its clock has not started.
    public long loungeNext() {
        return this.loungeNext;
    }

    public void setLoungeNext(long tick) {
        this.loungeNext = tick;
    }

    /// Gifts the lounge holds for this account, oldest first, which is the order it hands them out in.
    public List<LoungeGift> loungeGifts() {
        return Collections.unmodifiableList(this.lounge);
    }

    public void addGift(LoungeGift gift) {
        this.lounge.add(gift);
    }

    /// Takes the gift at [index] out of the lounge, or null when there is no such gift, so a claim that is
    /// answered twice cannot hand the same gift over twice.
    public LoungeGift takeGift(int index) {
        return index < 0 || index >= this.lounge.size() ? null : this.lounge.remove(index);
    }

    public void restoreMembership(Membership plan, long untilTick, boolean renew, long next, List<LoungeGift> gifts) {
        this.membership = plan;
        this.membershipUntil = untilTick;
        this.autoRenew = renew;
        this.loungeNext = next;
        this.lounge.clear();
        this.lounge.addAll(gifts);
    }

    public long balance(Currency currency) {
        return this.balances[Currencies.index(currency)];
    }

    public void addBalance(Currency currency, long micro) {
        this.balances[Currencies.index(currency)] += micro;
    }

    public long realized(Currency currency) {
        return this.realized[Currencies.index(currency)];
    }

    public void addRealized(Currency currency, long micro) {
        this.realized[Currencies.index(currency)] += micro;
    }

    /// One period of interest the platform charged on a debt, in micro units of the currency owed.
    public record Interest(Currency currency, long interestMicro, long debtMicro) {
    }

    /// What the account owes in one currency, in micro units, zero while its balance is not in the red.
    public long debt(Currency currency) {
        return Math.max(0L, -this.balances[Currencies.index(currency)]);
    }

    /// Market tick the debt of one currency started at, zero while that balance is not in the red. This is the
    /// age the credit record of the account is measured from, and it is not reset by a partial repayment or by
    /// the interest the platform charges on top.
    public long debtSince(Currency currency) {
        return this.debtSince[Currencies.index(currency)];
    }

    /// Market tick the next interest period of one currency's debt ends at, zero while that balance is not in
    /// the red.
    public long debtDue(Currency currency) {
        return this.debtDue[Currencies.index(currency)];
    }

    /// Restores the credit record of one currency from a save, so a debt keeps its age across a reload.
    public void restoreDebt(Currency currency, long since, long due) {
        this.debtSince[Currencies.index(currency)] = since;
        this.debtDue[Currencies.index(currency)] = due;
    }

    /// Whether a debt has been outstanding for longer than the platform's credit limit at [marketTick], which
    /// is what stops the account from opening anything new.
    public boolean blacklisted(long marketTick) {
        for (int index = 0; index < Currencies.count(); index++) {
            if (this.debtSince[index] != 0L && marketTick - this.debtSince[index] > TradeEngine.DEBT_BLACKLIST_TICKS) {
                return true;
            }
        }
        return false;
    }

    /// Advances the credit record of every currency to [marketTick] and charges one period of interest on the
    /// ones that owe: a balance that is not in the red is dropped from the record, a balance that just went
    /// negative is marked now, and a debt whose period is up pays [TradeEngine#DEBT_RATE] of what it owes, which
    /// grows the debt the next period is charged on. A period that was skipped while the server was down is
    /// charged one tick at a time, so the record never runs ahead of the clock. Returns what was charged, empty
    /// when no period ended this tick.
    public List<Interest> settleDebt(long marketTick) {
        List<Interest> charges = List.of();
        for (Currency currency : Currencies.all()) {
            int index = Currencies.index(currency);
            long owed = debt(currency);
            if (owed == 0L) {
                this.debtSince[index] = 0L;
                this.debtDue[index] = 0L;
                continue;
            }
            if (this.debtSince[index] == 0L) {
                this.debtSince[index] = marketTick;
                this.debtDue[index] = marketTick + TradeEngine.DEBT_PERIOD_TICKS;
                continue;
            }
            if (marketTick < this.debtDue[index]) {
                continue;
            }
            long interest = Money.round(Money.units(owed) * TradeEngine.DEBT_RATE);
            this.debtDue[index] += TradeEngine.DEBT_PERIOD_TICKS;
            addBalance(currency, -interest);
            if (charges.isEmpty()) {
                charges = new ArrayList<>(2);
            }
            charges.add(new Interest(currency, interest, debt(currency)));
        }
        return charges;
    }

    public Position position(long id) {
        for (Position position : this.positions) {
            if (position.id() == id) {
                return position;
            }
        }
        return null;
    }

    public void addPosition(Position position) {
        this.positions.add(position);
    }

    public void removePosition(Position position) {
        this.positions.remove(position);
        this.closedTrades++;
    }

    public long nextPositionId() {
        return this.nextPositionId;
    }

    public long allocatePositionId() {
        return this.nextPositionId++;
    }

    public void restorePositionId(long next) {
        this.nextPositionId = Math.max(this.nextPositionId, next);
    }

    public void restoreClosedTrades(long trades) {
        this.closedTrades = trades;
    }

    /// Margin locked in one currency by every position that named it as its margin, in micro units.
    public long lockedMargin(Currency currency) {
        long locked = 0L;
        for (Position position : this.positions) {
            if (position.marginCurrency() == currency) {
                locked += position.marginMicro();
            }
        }
        return locked;
    }

    /// Balance that can still be locked as margin or withdrawn, in micro units. Never negative.
    public long freeMargin(Currency currency) {
        return Math.max(0L, balance(currency) - lockedMargin(currency));
    }

    /// Unrealised profit of every position that settles in `currency`, in micro units of that currency. What an
    /// open position is worth is what it would fetch at the closing price under the plan of this account, so the
    /// fee discount of a subscription shows up in the equity of an account and not only at a fill.
    public long unrealizedPnl(Currency currency, MarketEngine market) {
        FeeDiscount discount = feeDiscount();
        long pnl = 0L;
        for (Position position : this.positions) {
            if (position.marginCurrency() == currency) {
                pnl += position.unrealizedMarginMicro(market, discount);
            }
        }
        return pnl;
    }

    /// Net asset value of the account in items of one currency, marked at the closing price of every position
    /// and converted at the live rate. A sum over currencies has to name the currency it is measured in: there
    /// is no unit the whole economy is worth.
    public double equityItems(Currency basis, MarketEngine market) {
        double equity = 0.0D;
        for (Currency currency : Currencies.all()) {
            long net = balance(currency) + unrealizedPnl(currency, market);
            equity += Money.units(net) * market.rate(currency, basis);
        }
        return equity;
    }

    /// Margin currently locked, in items of one currency at the live rate.
    public double usedMarginItems(Currency basis, MarketEngine market) {
        double used = 0.0D;
        for (Position position : this.positions) {
            used += Money.units(position.marginMicro()) * market.rate(position.marginCurrency(), basis);
        }
        return used;
    }

    /// Equity of one currency over the margin locked in that same currency, `inf` while it locks none. Every
    /// position lives in the currency it settles in, so this is the level the platform watches: a currency's own
    /// debt cannot be covered by another currency's balance at a price the platform would have to guess.
    public double marginLevel(Currency currency, MarketEngine market) {
        long used = lockedMargin(currency);
        if (used <= 0L) {
            return Double.POSITIVE_INFINITY;
        }
        long net = balance(currency) + unrealizedPnl(currency, market);
        return Money.units(net) / Money.units(used);
    }

    /// Lowest margin level of any currency the account settles in, `inf` while it locks no margin at all.
    public double marginLevel(MarketEngine market) {
        double lowest = Double.POSITIVE_INFINITY;
        for (Currency currency : Currencies.all()) {
            if (lockedMargin(currency) > 0L) {
                lowest = Math.min(lowest, marginLevel(currency, market));
            }
        }
        return lowest;
    }

    /// Signed exposure of the account in one currency, in items of that currency: a position is long the base of
    /// its pair and short the quote, whatever currency backs its margin. Splitting an order into several smaller
    /// ones therefore changes nothing about the exposure it adds.
    public double signedExposureItems(Currency currency, MarketEngine market) {
        double exposure = 0.0D;
        for (Position position : this.positions) {
            exposure += position.exposureItems(currency, market);
        }
        return exposure;
    }

    /// Absolute risk of the account in one currency, in items of that currency. Two opposite positions in the
    /// same currency do not cancel here: what the platform limits is the size it would have to unwind.
    public double absoluteExposureItems(Currency currency, MarketEngine market) {
        double exposure = 0.0D;
        for (Position position : this.positions) {
            exposure += Math.abs(position.exposureItems(currency, market));
        }
        return exposure;
    }

    /// Risk a limit of that many items in one currency still leaves this account, in items of that currency: the
    /// limit less the absolute risk the open positions already carry, which is what a new order may still add.
    /// Zero once the risk of the account has reached or passed the limit, [Double#POSITIVE_INFINITY] while the
    /// platform limits nothing.
    public double riskRoom(Currency currency, MarketEngine market, double limit) {
        return Math.max(0.0D, limit - this.absoluteExposureItems(currency, market));
    }
}
