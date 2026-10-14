package com.pppopipupu.fxeasy.shop;

import module java.base;

import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;
import com.pppopipupu.fxeasy.market.FeeDiscount;
import com.pppopipupu.fxeasy.market.FeeModel;
import com.pppopipupu.fxeasy.market.MarketEngine;
import com.pppopipupu.fxeasy.market.Rng;
import com.pppopipupu.fxeasy.trade.Account;
import com.pppopipupu.fxeasy.trade.TradeEngine;

/// Everything the shop sells and everything it settles on its own. A plan is bought out of the free margin of
/// an account in the currency the plan is priced in, an account on the same plan extends its period, an account
/// on a lower plan upgrades at once, and a plan only ever lapses when its period is up and the balance cannot
/// cover another one: cancelling stops the renewal, it never takes back what was paid. The lounge of the top
/// plan has one gift waiting per refresh period while that plan runs, and the gifts already delivered stay
/// where they are, claimable, after the plan lapses.
public final class ShopEngine {

    public static final String REJECT_FUNDS = "fxeasy.reject.shop.funds";
    public static final String REJECT_CURRENCY = "fxeasy.reject.shop.currency";
    public static final String REJECT_DOWNGRADE = "fxeasy.reject.shop.downgrade";

    private ShopEngine() {
    }

    public sealed interface BuyResult {

        /// A period that was paid for: the plan, the currency the price left the balance in with the whole
        /// micro items it took, the tick the period runs to and whether it extended a period already running.
        record Bought(Membership plan, Currency paying, long priceMicro, long untilTick, boolean extended)
                implements BuyResult {
        }

        record Rejected(String reason) implements BuyResult {
        }
    }

    /// What one period of `plan` costs in the currency `paying`, quoted at `rate` items of `paying` per item of
    /// the currency the plan is priced in. Paying in the currency the plan is priced in is par; anything else is
    /// converted at the rate of the market, widened by the conversion spread the platform charges for turning one
    /// currency into another, which the plan of the account scales down like every other cost. The price is
    /// rounded up to the whole micro item, so a conversion never rounds in the buyer's favour.
    public static long priceMicro(Membership plan, Currency paying, double rate, FeeDiscount discount) {
        if (paying == Currencies.byId(plan.priceCurrencyId())) {
            return plan.priceMicro();
        }
        return (long) Math.ceil(plan.priceMicro() * rate * (1.0D + FeeModel.halfConversionSpread(discount)));
    }

    /// Buys a plan for an account, out of the free margin of the currency the buyer pays in: the price of the
    /// plan in its own currency is what the catalogue quotes, and paying in another currency is that price
    /// converted at the live rate, so a tier is worth the same however it is settled. A world that does not trade
    /// the currency the plan is priced in cannot sell it at all, because the catalogue price would name nothing;
    /// only the free margin counts, because money locked behind an open position is not the account's to spend.
    public static BuyResult buy(Account account, Membership plan, Currency paying, double rate, long marketTick) {
        if (!account.opened()) {
            return new BuyResult.Rejected(TradeEngine.REJECT_ACCOUNT);
        }
        if (!plan.purchasable()) {
            return new BuyResult.Rejected(REJECT_CURRENCY);
        }
        if (Currencies.byId(plan.priceCurrencyId()) == null || paying == null) {
            return new BuyResult.Rejected(REJECT_CURRENCY);
        }
        if (plan.ordinal() < account.membership().ordinal()) {
            return new BuyResult.Rejected(REJECT_DOWNGRADE);
        }
        long priceMicro = priceMicro(plan, paying, rate, account.feeDiscount());
        if (account.freeMargin(paying) < priceMicro) {
            return new BuyResult.Rejected(REJECT_FUNDS);
        }
        boolean extended = plan == account.membership();
        long until = extended ? Math.max(account.membershipUntil(), marketTick) + Membership.PERIOD_TICKS
                : marketTick + Membership.PERIOD_TICKS;
        account.addBalance(paying, -priceMicro);
        account.startMembership(plan, until, true);
        return new BuyResult.Bought(plan, paying, priceMicro, until, extended);
    }

    public sealed interface Renewal {

        record Renewed(Membership plan, long untilTick) implements Renewal {
        }

        record Expired(Membership plan) implements Renewal {
        }
    }

    /// Settles the subscription of an account whose period is up: a plan that still renews itself is charged for
    /// another period out of its own currency and runs on, and one the account cannot cover lapses. The clock
    /// restarts from the tick of the settlement rather than from the tick the period ended on, so a server that
    /// was down for a week costs one period and not seven.
    public static Renewal settle(Account account, long marketTick) {
        Membership plan = account.membership();
        if (plan == Membership.NONE || marketTick < account.membershipUntil()) {
            return null;
        }
        Currency currency = Currencies.byId(plan.priceCurrencyId());
        long priceMicro = plan.priceMicro();
        if (account.autoRenew() && currency != null && account.freeMargin(currency) >= priceMicro) {
            account.addBalance(currency, -priceMicro);
            account.startMembership(plan, marketTick + Membership.PERIOD_TICKS, true);
            return new Renewal.Renewed(plan, account.membershipUntil());
        }
        account.endMembership();
        account.setLoungeNext(0L);
        return new Renewal.Expired(plan);
    }

    /// Delivers what the lounge of an account is due: one gift per refresh period that has passed while the plan
    /// of the account carries a lounge, held to the capacity of the pool, and nothing at all while a plan without
    /// one runs. A lounge that is full waits for the next period instead of growing without bound. Returns
    /// whether anything was delivered.
    ///
    /// Every gift is drawn from its own stream, seeded with the account and the tick the period was due at, so a
    /// reload delivers the same gift it would have delivered before the save, and the price, event and execution
    /// generators of the market are never advanced by a lounge: a player cannot re-roll the news by collecting
    /// a present.
    public static boolean deliver(Account account, long marketTick, LoungeFile.Pool pool) {
        if (!account.membership().lounge()) {
            return false;
        }
        if (account.loungeNext() == 0L) {
            account.setLoungeNext(marketTick + pool.refreshTicks());
            return false;
        }
        boolean delivered = false;
        while (marketTick >= account.loungeNext() && account.loungeGifts().size() < pool.capacity()) {
            long due = account.loungeNext();
            account.setLoungeNext(due + pool.refreshTicks());
            LoungeGift gift = pool.roll(new Rng(MarketEngine.derive(loungeSeed(account), due)));
            if (gift != null) {
                account.addGift(gift);
                delivered = true;
            }
        }
        if (marketTick >= account.loungeNext()) {
            account.setLoungeNext(marketTick + pool.refreshTicks());
        }
        return delivered;
    }

    /// Seed the delivery of one period is drawn under: the owner of the account decides the gift, the tick it was
    /// due at decides which drawing it is.
    private static long loungeSeed(Account account) {
        return account.owner().getMostSignificantBits() ^ account.owner().getLeastSignificantBits();
    }
}
