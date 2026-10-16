package com.pppopipupu.fxeasy.trade;

import module java.base;

import com.pppopipupu.fxeasy.market.Currency;
import com.pppopipupu.fxeasy.market.CurrencyPair;
import com.pppopipupu.fxeasy.market.FeeDiscount;
import com.pppopipupu.fxeasy.market.FeeModel;
import com.pppopipupu.fxeasy.market.MarketEngine;
import com.pppopipupu.fxeasy.market.Side;

/// Every operation a client can ask for, validated and applied on the server. A position locks its margin in
/// whichever currency the order names, free margin is measured in that currency and the position settles in it
/// as well: unrealised profit and swap accrue there, converted from the items of the pair at the live rate the
/// platform sells that currency for. Collateral that is not the currency the pair settles in is discounted by
/// its haircut and by the conversion spread, and the swap of a position is the interest difference of the two
/// currencies of its pair, paid or taken once a market day. A market order pays the spread plus an occasional
/// adverse slippage.
///
/// The size of an order is an item count of the base currency of its pair, which is the lot the terminal quotes
/// it in, and the notional it carries is that count at the live price of the pair. A balance that a close or a
/// liquidation leaves below zero is debt: it is charged interest every
/// [DEBT_PERIOD_TICKS] and an account whose debt has been outstanding for longer than [DEBT_BLACKLIST_TICKS]
/// may not open anything new until it is repaid.
public final class TradeEngine {

    public static final int MIN_LEVERAGE = 1;
    public static final int MAX_POSITIONS = 64;
    public static final long MAX_UNITS = 1_000_000L;
    /// Most items one order may name, which is the ceiling the ticket builds its size against. The bound is here
    /// so a hand written packet cannot ask the price process for an arbitrary notional; what an account can
    /// actually reach is its own free margin.
    public static final int MAX_ORDER_UNITS = 100_000_000;
    /// Equity of a currency over the margin locked in that same currency below which the platform calls for more
    /// margin, and at or below which it closes the account out. The level it watches is the lowest one of the
    /// currencies the account holds, each measured on its own: a balance in one currency never covers the
    /// margin of a position that settles in another. These are the two levels a regulated retail broker runs.
    public static final double MARGIN_CALL_LEVEL = 1.0D;
    public static final double LIQUIDATION_LEVEL = 0.5D;
    /// Interest a debt is charged every [DEBT_PERIOD_TICKS], as a fraction of what is owed at that moment: the
    /// charge is added to the debt, so the next period is charged on the interest as well.
    public static final double DEBT_RATE = 0.11D;
    public static final int DEBT_PERIOD_TICKS = 3 * MarketEngine.TICKS_PER_DAY;
    /// Debt older than this blacklists the credit of the account, which may then not open anything new until the
    /// debt is repaid in full.
    public static final int DEBT_BLACKLIST_TICKS = 10 * MarketEngine.TICKS_PER_DAY;
    public static final String REJECT_BACKING = "fxeasy.reject.backing";

    public static final String REJECT_ACCOUNT = "fxeasy.reject.account_closed";
    public static final String REJECT_SIZE = "fxeasy.reject.size";
    public static final String REJECT_LEVERAGE = "fxeasy.reject.leverage";
    public static final String REJECT_POSITIONS = "fxeasy.reject.positions";
    public static final String REJECT_MARGIN = "fxeasy.reject.margin";
    public static final String REJECT_POSITION = "fxeasy.reject.position";
    public static final String REJECT_AMOUNT = "fxeasy.reject.amount";
    public static final String REJECT_ITEMS = "fxeasy.reject.items";
    public static final String REJECT_SPACE = "fxeasy.reject.space";
    public static final String REJECT_CREDIT = "fxeasy.reject.credit";

    private TradeEngine() {
    }

    /// Margin an order of that size needs, in micro units of the currency it is locked in. `pairPrice` is what
    /// one item of the base is worth in items of the quote, which is the notional of one unit of the order, and
    /// `collateralRate` is what one item of the collateral backs a trade on that pair with, in quote items:
    /// anything but the settlement currency itself pays its haircut and the conversion spread.
    public static long requiredMargin(int units, int leverage, double pairPrice, double collateralRate) {
        double marginQuoteItems = units * pairPrice / leverage;
        return Money.round(marginQuoteItems / collateralRate);
    }

    public sealed interface OpenResult {
        record Opened(Position position, FeeModel.Fill fill) implements OpenResult {
        }

        record Rejected(String reason) implements OpenResult {
        }
    }

    /// Opens a position of `units` items of the base currency on `pair`, locking the margin such a notional
    /// needs from `marginCurrency`. Any of the legal tender currencies may back any pair, which is the whole
    /// point of the platform: a trader who holds dirt backs their netherite trade with dirt rather than having
    /// to buy netherite first. An account whose debt has been outstanding too long may not open anything, but it
    /// may still close, deposit and withdraw, which is the only way the debt ever gets repaid. The entry is
    /// filled at the spread of the side it takes, which the platform charges more of on the short side than on
    /// the long one, so a position that sells pays more to open than one that buys.
    public static OpenResult open(Account account, CurrencyPair pair, Side side, Currency marginCurrency, int units,
            int leverage, MarketEngine market) {
        if (!account.opened()) {
            return new OpenResult.Rejected(REJECT_ACCOUNT);
        }
        if (units < 1 || units > MAX_ORDER_UNITS) {
            return new OpenResult.Rejected(REJECT_SIZE);
        }
        if (leverage < MIN_LEVERAGE || leverage > account.maxLeverage()) {
            return new OpenResult.Rejected(REJECT_LEVERAGE);
        }
        if (account.positionCount() >= MAX_POSITIONS) {
            return new OpenResult.Rejected(REJECT_POSITIONS);
        }
        if (account.blacklisted(market.marketTick())) {
            return new OpenResult.Rejected(REJECT_CREDIT);
        }
        FeeDiscount discount = account.feeDiscount();
        long margin = requiredMargin(units, leverage, market.price(pair),
                FeeModel.collateralRate(pair, marginCurrency, market.rate(marginCurrency, pair.quote()), discount));
        if (account.freeMargin(marginCurrency) < margin) {
            return new OpenResult.Rejected(REJECT_MARGIN);
        }
        FeeModel.Fill fill = FeeModel.openFill(side, market.price(pair), market.regime(pair), market.executionRng(),
                discount);
        Position position = new Position(account.allocatePositionId(), pair, side, marginCurrency, units, leverage,
                fill.price(), units, margin, market.marketTick());
        account.addPosition(position);
        market.addTradeVolume(pair, units);
        return new OpenResult.Opened(position, fill);
    }

    public sealed interface CloseResult {
        record Closed(Position position, FeeModel.Fill fill, long pnlMicro) implements CloseResult {
        }

        record Rejected(String reason) implements CloseResult {
        }
    }

    public static CloseResult close(Account account, long positionId, MarketEngine market) {
        Position position = account.position(positionId);
        if (position == null) {
            return new CloseResult.Rejected(REJECT_POSITION);
        }
        return close(account, position, market);
    }

    /// Closes the position at the unfavourable side of the spread and settles the profit in the currency the
    /// position locked its margin in, which is where the trader deposited from and what they can withdraw again.
    /// A profit made in the quote currency of the pair is converted into that currency at the platform's rate,
    /// which costs the conversion spread unless the two currencies are the same.
    public static CloseResult close(Account account, Position position, MarketEngine market) {
        CurrencyPair pair = position.pair();
        Currency marginCurrency = position.marginCurrency();
        FeeDiscount discount = account.feeDiscount();
        FeeModel.Fill fill = FeeModel.fill(position.side().opposite(), market.price(pair), market.regime(pair),
                market.executionRng(), discount);
        double delta = fill.price() - position.openPrice();
        double pnlQuoteItems = position.signedBaseUnits() * delta;
        long pnlMicro = Money.round(FeeModel.toItems(pnlQuoteItems, pair, marginCurrency,
                market.rate(marginCurrency, pair.quote()), discount));
        account.addBalance(marginCurrency, pnlMicro);
        account.addRealized(marginCurrency, pnlMicro);
        account.removePosition(position);
        market.addTradeVolume(pair, position.baseUnits());
        return new CloseResult.Closed(position, fill, pnlMicro);
    }

    public static List<CloseResult.Closed> closeAll(Account account, MarketEngine market) {
        List<CloseResult.Closed> closed = new ArrayList<>();
        for (Position position : List.copyOf(account.positions())) {
            if (close(account, position, market) instanceof CloseResult.Closed done) {
                closed.add(done);
            }
        }
        return closed;
    }

    /// Settles one market day of swap on every open position, taken from the currency each position locks and
    /// converted at the platform's rate. A position that holds the higher yielding currency of its pair is
    /// credited, one that holds the lower yielding currency pays, and the platform keeps its markup on both
    /// sides. Positions are valued at the current mid price, so a dearer market costs more to hold. Returns
    /// whether any balance moved.
    public static boolean settleSwap(Account account, MarketEngine market) {
        boolean moved = false;
        FeeDiscount discount = account.feeDiscount();
        for (Position position : account.positions()) {
            CurrencyPair pair = position.pair();
            Currency currency = position.marginCurrency();
            double notionalQuoteItems = position.baseUnits() * market.price(pair);
            long swap = Money.round(FeeModel.toItems(
                    notionalQuoteItems * FeeModel.swapPerDay(pair, position.side(), discount), pair, currency,
                    market.rate(currency, pair.quote()), discount));
            if (swap == 0L) {
                continue;
            }
            position.addSwap(swap);
            account.addBalance(currency, -swap);
            moved = true;
        }
        return moved;
    }

    public static boolean marginCall(Account account, MarketEngine market) {
        return account.positionCount() > 0 && account.marginLevel(market) < MARGIN_CALL_LEVEL;
    }

    public static boolean needsLiquidation(Account account, MarketEngine market) {
        return account.positionCount() > 0 && account.marginLevel(market) <= LIQUIDATION_LEVEL;
    }

    public sealed interface DepositResult {
        record Done(long units, Currency currency) implements DepositResult {
        }

        record Rejected(String reason) implements DepositResult {
        }
    }

    public static DepositResult deposit(Account account, Currency currency, long units, Wallet wallet) {
        if (!account.opened()) {
            return new DepositResult.Rejected(REJECT_ACCOUNT);
        }
        if (!currency.backed()) {
            return new DepositResult.Rejected(REJECT_BACKING);
        }
        if (units < 1L || units > MAX_UNITS) {
            return new DepositResult.Rejected(REJECT_AMOUNT);
        }
        if (wallet.count(currency) < units || !wallet.remove(currency, units)) {
            return new DepositResult.Rejected(REJECT_ITEMS);
        }
        account.addBalance(currency, units * Money.UNIT);
        return new DepositResult.Done(units, currency);
    }

    public sealed interface WithdrawResult {
        record Done(long units, Currency currency) implements WithdrawResult {
        }

        record Rejected(String reason) implements WithdrawResult {
        }
    }

    public static WithdrawResult withdraw(Account account, Currency currency, long units, Wallet wallet) {
        if (!account.opened()) {
            return new WithdrawResult.Rejected(REJECT_ACCOUNT);
        }
        if (!currency.backed()) {
            return new WithdrawResult.Rejected(REJECT_BACKING);
        }
        if (units < 1L || units > MAX_UNITS) {
            return new WithdrawResult.Rejected(REJECT_AMOUNT);
        }
        long micro = units * Money.UNIT;
        if (account.freeMargin(currency) < micro) {
            return new WithdrawResult.Rejected(REJECT_MARGIN);
        }
        account.addBalance(currency, -micro);
        long added = wallet.add(currency, units);
        if (added < units) {
            account.addBalance(currency, (units - added) * Money.UNIT);
        }
        if (added <= 0L) {
            return new WithdrawResult.Rejected(REJECT_SPACE);
        }
        return new WithdrawResult.Done(added, currency);
    }
}
