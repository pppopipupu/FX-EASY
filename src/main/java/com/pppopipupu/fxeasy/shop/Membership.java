package com.pppopipupu.fxeasy.shop;

import module java.base;

import com.pppopipupu.fxeasy.market.FeeDiscount;
import com.pppopipupu.fxeasy.market.MarketEngine;
import com.pppopipupu.fxeasy.trade.Money;

/// One tier of the subscription the terminal sells. A plan is bought out of the free margin of an account in
/// the currency the plan is priced in, runs for [PERIOD_TICKS] market ticks and renews itself while the balance
/// covers it. What a plan buys is a higher ceiling on the leverage an order may name and a [FeeDiscount] on
/// everything the platform charges, and the top tier opens the VIP lounge. Nothing here is money: the price is
/// a count of items of a currency of the world, and a world that does not trade that currency cannot sell the
/// plan, which the shop reports instead of inventing a rate.
public enum Membership {

    NONE("none", "", 0L, 100, FeeDiscount.NONE, false),
    GO("go", "dirt", 8_888L, 200, FeeDiscount.NONE, false),
    PLUS("plus", "diamond", 64L, 300, new FeeDiscount(0.33D, 0.33D, 0.33D, 0.0D), false),
    PRO("pro", "diamond", 666L, 500, new FeeDiscount(0.66D, 0.66D, 0.66D, 0.66D), true);

    /// Market ticks one period of a plan lasts, which is also the period it is charged over again.
    public static final int PERIOD_TICKS = 15 * MarketEngine.TICKS_PER_DAY;

    private final String id;
    private final String priceCurrency;
    private final long priceItems;
    private final int maxLeverage;
    private final FeeDiscount discount;
    private final boolean lounge;

    Membership(String id, String priceCurrency, long priceItems, int maxLeverage, FeeDiscount discount,
            boolean lounge) {
        this.id = id;
        this.priceCurrency = priceCurrency;
        this.priceItems = priceItems;
        this.maxLeverage = maxLeverage;
        this.discount = discount;
        this.lounge = lounge;
    }

    /// The plans the shop sells, in the order the page draws them. The tier an account without a subscription
    /// holds is not one of them.
    public static List<Membership> plans() {
        return List.of(GO, PLUS, PRO);
    }

    /// The plan an id names, [NONE] for an id no plan carries, which is what a save written by a build that knew
    /// a tier this one does not comes back to.
    public static Membership byId(String id) {
        for (Membership plan : values()) {
            if (plan.id.equals(id)) {
                return plan;
            }
        }
        return NONE;
    }

    /// Short name of the plan, which is the key its wording and the panels are built from.
    public String id() {
        return this.id;
    }

    /// Id of the currency the plan is priced in, which may be one no world trades.
    public String priceCurrencyId() {
        return this.priceCurrency;
    }

    public long priceItems() {
        return this.priceItems;
    }

    /// The price in the micro units a balance is kept in.
    public long priceMicro() {
        return this.priceItems * Money.UNIT;
    }

    public int maxLeverage() {
        return this.maxLeverage;
    }

    public FeeDiscount discount() {
        return this.discount;
    }

    public boolean lounge() {
        return this.lounge;
    }

    public boolean purchasable() {
        return this != NONE;
    }

    public String nameKey() {
        return "fxeasy.shop.tier." + this.id + ".name";
    }

    /// The lore line the card of the tier carries: the character the plan is sold with. A plan is the catalogue
    /// of the platform and not a currency of a world, so this is a key of the mod rather than data of a save.
    public String loreKey() {
        return "fxeasy.shop.tier." + this.id + ".lore";
    }
}
