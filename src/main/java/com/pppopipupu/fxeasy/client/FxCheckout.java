package com.pppopipupu.fxeasy.client;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import com.pppopipupu.fxeasy.client.panel.FxControls;
import com.pppopipupu.fxeasy.client.panel.FxInput;
import com.pppopipupu.fxeasy.client.panel.FxTheme;
import com.pppopipupu.fxeasy.client.panel.FxUi;
import com.pppopipupu.fxeasy.content.FxCurrencies;
import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;
import com.pppopipupu.fxeasy.market.FeeDiscount;
import com.pppopipupu.fxeasy.market.MarketEngine;
import com.pppopipupu.fxeasy.shop.Membership;
import com.pppopipupu.fxeasy.shop.ShopEngine;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

/// The checkout the shop page opens when the player picks a tier: the receipt of the period they are buying and
/// the currencies they may settle it in, one row each with the free margin that backs it. The plan is priced in
/// the currency the catalogue quotes, and paying in another one is that price converted at the live rate plus
/// the conversion spread of the platform, so the card writes the price, the rate, the converted amount, what the
/// conversion costs and the total, in that order, and the total is what the row the player picks has to cover.
///
/// Only the currencies the account holds a balance in are offered - a currency the player does not have cannot
/// pay for anything - and a currency whose free margin does not cover the total is offered but marked short, so
/// the button under the rows refuses the payment the platform would refuse. Nothing is charged here: the plan
/// and the currency travel to the platform, which converts at its own rate and charges its own price.
///
/// The overlay owns the pointer and the keyboard while it is up, like every other overlay of the terminal. It is
/// the player's to close, and closing it pays for nothing.
public final class FxCheckout {

    private static final int MAX_ROWS = 5;
    private static final int CARD_WIDTH = 272;
    private static final int PAD = 8;
    private static final int LINE = 10;
    private static final int ROW = 14;
    private static final int ICON = 16;
    private static final int BUTTON_WIDTH = 72;
    private static final int BUTTON_HEIGHT = 14;
    private static final int CLOSE_WIDTH = 10;
    private static final int SCROLLBAR = 3;
    private static final int SCROLL_STEP = 2;
    /// Lines of the receipt between the header and the rows of currencies: what is bought, the price of the
    /// catalogue, the rate the payment converts at, the converted amount, what the conversion costs and the
    /// total.
    private static final int RECEIPT = 6;
    private static final int COVER = ARGB.color(200, 4, 6, 9);

    /// The two buttons of the card, which the screen aims a scripted click at.
    public record Buttons(ScreenRectangle pay, ScreenRectangle cancel) {
    }

    private final FxInput input = new FxInput();
    private boolean visible;
    private Membership plan = Membership.NONE;
    private Currency paying;
    private int scroll;
    private boolean logged;

    public boolean active() {
        return this.visible;
    }

    /// The pointer state of the overlay, which the screen fills from the mouse callbacks while it is up.
    public FxInput input() {
        return this.input;
    }

    public Membership plan() {
        return this.plan;
    }

    /// The currency the payment would settle in, or null while the account holds nothing to pay with.
    public Currency paying() {
        return this.paying;
    }

    public void open(Membership plan, FxClientState state) {
        this.plan = plan;
        this.paying = defaultPaying(plan, state);
        this.scroll = 0;
        this.visible = true;
        this.logged = false;
        this.input.clear();
    }

    /// Puts the checkout away, which is what the cancel cell, the close cell and a click outside the card do.
    /// Nothing is paid by closing it.
    public void dismiss() {
        if (!this.visible) {
            return;
        }
        this.visible = false;
        this.plan = Membership.NONE;
        this.paying = null;
        this.input.clear();
        this.logged = false;
        FxEasy.LOGGER.info("FX terminal checkout closed");
    }

    public void draw(GuiGraphicsExtractor graphics, Font font, FxClientState state, int designWidth,
            int designHeight) {
        ScreenRectangle card = card(designWidth, designHeight);
        ScreenRectangle close = closeArea(designWidth, designHeight);
        ScreenRectangle list = listArea(designWidth, designHeight);
        List<Currency> payable = payable(state);
        int visible = rows(designWidth, designHeight);
        int maximum = Math.max(0, payable.size() - visible);
        if (this.input.scroll != 0.0D && this.input.hovered(list)) {
            this.scroll -= (int) Math.signum(this.input.scroll) * SCROLL_STEP;
            this.input.scroll = 0.0D;
        }
        this.scroll = Math.clamp(this.scroll, 0, maximum);
        long total = total(state);
        if (!this.logged) {
            this.logged = true;
            FxEasy.LOGGER.info("FX checkout layout: card {}x{} at {}, {}, {} currencies, paying {}, total {}",
                    card.width(), card.height(), card.left(), card.top(), payable.size(),
                    this.paying == null ? "nothing" : this.paying.code(),
                    this.paying == null ? "nothing" : FxUi.units(total) + " × " + this.paying.code());
        }
        FxUi.fill(graphics, 0, 0, designWidth, designHeight, COVER);
        FxUi.fill(graphics, card.left(), card.top(), card.right(), card.bottom(), FxTheme.TOOLTIP);
        FxUi.border(graphics, card, ARGB.multiplyAlpha(FxTheme.ACCENT, 0.9F));
        FxUi.text(graphics, font, card.left() + PAD, card.top() + PAD, FxText.get("fxeasy.checkout.title"),
                FxTheme.ACCENT);
        FxControls.button(graphics, font, close, FxText.get("fxeasy.lounge.close"), FxTheme.BUTTON, FxTheme.TEXT,
                this.input.hovered(close), this.input.pressed && this.input.hovered(close), true);
        FxUi.clipped(graphics, font, card, card.left() + PAD, card.top() + PAD + LINE,
                FxText.get("fxeasy.checkout.subtitle", FxText.get(this.plan.nameKey()).getString(), days()),
                FxTheme.MUTED);
        receipt(graphics, font, state, card, total);
        FxUi.separator(graphics, card.left() + PAD, card.right() - PAD, sectionTop(card) - 1 - FxTheme.GAP);
        FxUi.text(graphics, font, card.left() + PAD, sectionTop(card), FxText.get("fxeasy.checkout.paying"),
                FxTheme.MUTED);
        if (payable.isEmpty()) {
            FxUi.text(graphics, font, list.left(), list.top() + 2, FxText.get("fxeasy.checkout.empty"),
                    FxTheme.MUTED);
        }
        FxUi.pushClip(graphics, list);
        for (int index = this.scroll; index < Math.min(payable.size(), this.scroll + visible); index++) {
            row(graphics, font, state, payable.get(index), index, payable.size(), total, designWidth, designHeight);
        }
        FxUi.popClip(graphics);
        if (maximum > 0) {
            FxControls.scrollbar(graphics, FxUi.rect(card.right() - PAD - SCROLLBAR, list.top(), SCROLLBAR,
                    list.height()), this.scroll / (double) maximum, visible / (double) payable.size(),
                    this.input.hovered(list));
        }
        FxUi.separator(graphics, card.left() + PAD, card.right() - PAD, noteTop(card) - 1 - FxTheme.GAP);
        FxUi.clipped(graphics, font, card, card.left() + PAD, noteTop(card),
                FxText.get("fxeasy.checkout.renew", pricedCode()), FxTheme.MUTED);
        Buttons buttons = buttons(card);
        String refusal = refusal(state, total);
        FxControls.button(graphics, font, buttons.cancel(), FxText.get("fxeasy.confirm.cancel"), FxTheme.BUTTON,
                FxTheme.TEXT, this.input.hovered(buttons.cancel()),
                this.input.pressed && this.input.hovered(buttons.cancel()), true);
        FxControls.button(graphics, font, buttons.pay(),
                FxText.get(refusal == null ? "fxeasy.checkout.pay" : refusal),
                ARGB.multiplyAlpha(FxTheme.ACCENT, 0.82F), ARGB.color(255, 12, 16, 20),
                this.input.hovered(buttons.pay()), this.input.pressed && this.input.hovered(buttons.pay()),
                refusal == null);
        if (refusal == null && this.input.clicked(buttons.pay())) {
            FxActions.buyPlan(this.plan, this.paying);
            FxEasy.LOGGER.info("FX terminal checkout pays {} with {} × {}", this.plan.id(), FxUi.units(total),
                    this.paying.code());
            dismiss();
            return;
        }
        if (this.input.clicked(buttons.cancel()) || this.input.clicked(close)
                || (this.input.pressed && !FxUi.hit(card, this.input.mouseX, this.input.mouseY))) {
            dismiss();
        }
    }

    private void receipt(GuiGraphicsExtractor graphics, Font font, FxClientState state, ScreenRectangle card,
            long total) {
        int x = card.left() + PAD;
        int right = card.right() - PAD;
        int y = receiptTop(card);
        FxUi.separator(graphics, x, right, y - 1 - FxTheme.GAP);
        y = figure(graphics, font, x, right, y, "fxeasy.checkout.item", FxText.get(this.plan.nameKey()),
                FxTheme.TEXT);
        y = figure(graphics, font, x, right, y, "fxeasy.checkout.price", price(), FxTheme.TEXT);
        y = figure(graphics, font, x, right, y, "fxeasy.checkout.rate", rateText(state), FxTheme.MUTED);
        y = figure(graphics, font, x, right, y, "fxeasy.checkout.converted", converted(state), FxTheme.TEXT);
        y = figure(graphics, font, x, right, y, "fxeasy.checkout.fee", fee(state), FxTheme.MUTED);
        figure(graphics, font, x, right, y, "fxeasy.checkout.total", total(total), FxTheme.ACCENT);
    }

    private static int figure(GuiGraphicsExtractor graphics, Font font, int x, int right, int y, String key,
            Component value, int colour) {
        FxUi.text(graphics, font, x, y, FxText.get(key), FxTheme.MUTED);
        FxUi.right(graphics, font, right, y, value, colour);
        return y + LINE;
    }

    private void row(GuiGraphicsExtractor graphics, Font font, FxClientState state, Currency currency, int index,
            int count, long total, int designWidth, int designHeight) {
        ScreenRectangle area = rowArea(index, count, designWidth, designHeight);
        if (area == null) {
            return;
        }
        boolean selected = currency == this.paying;
        long free = state.freeMargin(currency);
        boolean shortOfTotal = free < total;
        if (selected) {
            FxUi.fill(graphics, area.left(), area.top(), area.right(), area.bottom(), FxTheme.SELECTED);
            FxUi.border(graphics, area, ARGB.multiplyAlpha(FxTheme.ACCENT, 0.7F));
        } else if (this.input.hovered(area)) {
            FxUi.fill(graphics, area.left(), area.top(), area.right(), area.bottom(), FxTheme.BUTTON_HOVER);
        }
        FxUi.item(graphics, FxCurrencies.stack(currency, 1), area.left() + 1, area.top() + 1, 1);
        FxUi.clipped(graphics, font, area, area.left() + ICON + FxTheme.GAP, area.top() + (ROW - 8) / 2,
                FxText.literal(currency.code()), selected ? FxTheme.ACCENT : FxTheme.TEXT);
        Component marker = selected ? FxText.get("fxeasy.checkout.selected")
                : shortOfTotal ? FxText.get("fxeasy.checkout.short") : null;
        int markerWidth = marker == null ? 0 : font.width(marker) + FxTheme.GAP;
        FxUi.right(graphics, font, area.right() - markerWidth - 1, area.top() + (ROW - 8) / 2,
                FxText.literal(FxUi.units(free)), shortOfTotal ? FxTheme.DOWN : FxTheme.TEXT);
        if (marker != null) {
            FxUi.right(graphics, font, area.right() - 1, area.top() + (ROW - 8) / 2, marker,
                    selected ? FxTheme.ACCENT : FxTheme.DOWN);
        }
        if (this.input.clicked(area) && !selected) {
            this.paying = currency;
            FxEasy.LOGGER.info("FX terminal checkout pays in {}", currency.code());
        }
    }

    /// What one period costs in the currency the payment is set to, split the way the receipt writes it: the
    /// whole micro items the platform charges, and the amount the price of the catalogue converts to at the mid
    /// rate before the conversion spread is added. The platform charges the same figures off its own market at
    /// the tick the payment arrives, so this is a quote and not a promise.
    public record Quote(long totalMicro, long convertedMicro) {

        /// What the conversion costs on top of the converted amount, which is nothing when the payment settles
        /// in the currency the plan is priced in.
        public long feeMicro() {
            return this.totalMicro - this.convertedMicro;
        }
    }

    /// Quotes a payment of `plan` in `paying` at `rate` items of `paying` per item of the currency the plan is
    /// priced in, under the discount the account trades on.
    public static Quote quote(Membership plan, Currency paying, double rate, FeeDiscount discount) {
        if (paying == null) {
            return new Quote(0L, 0L);
        }
        return new Quote(ShopEngine.priceMicro(plan, paying, rate, discount),
                (long) Math.ceil(plan.priceMicro() * rate));
    }

    /// What stands between the player and the payment, as the key of the wording the button carries, or null
    /// when the platform would take it: a currency the account holds with a free margin that covers the total.
    private String refusal(FxClientState state, long total) {
        return this.paying == null || state.freeMargin(this.paying) < total ? ShopEngine.REJECT_FUNDS : null;
    }

    /// The currencies the account holds a balance in, in the order of the table of the world.
    public static List<Currency> payable(FxClientState state) {
        List<Currency> held = new ArrayList<>();
        for (Currency currency : Currencies.all()) {
            if (state.balance(currency) > 0L) {
                held.add(currency);
            }
        }
        return held;
    }

    /// The currency the payment starts on: the one the plan is priced in when the account holds it, because
    /// settling in it costs no conversion, and otherwise the one the account holds most of.
    static Currency defaultPaying(Membership plan, FxClientState state) {
        Currency priced = Currencies.byId(plan.priceCurrencyId());
        Currency best = null;
        for (Currency currency : payable(state)) {
            if (currency == priced) {
                return currency;
            }
            if (best == null || state.freeMargin(currency) > state.freeMargin(best)) {
                best = currency;
            }
        }
        return best;
    }

    /// The currency the plan is priced in, or null when this world does not trade it.
    private Currency priced() {
        return Currencies.byId(this.plan.priceCurrencyId());
    }

    private String pricedCode() {
        Currency priced = priced();
        return priced == null ? this.plan.priceCurrencyId() : priced.code();
    }

    /// Rate of one item of the currency the plan is priced in against one item of the currency the payment is
    /// set to, which is par while both are the same currency.
    private double rate(FxClientState state) {
        Currency priced = priced();
        return priced == null || this.paying == null ? 1.0D : state.rate(priced, this.paying);
    }

    private long total(FxClientState state) {
        return quote(state).totalMicro();
    }

    private Quote quote(FxClientState state) {
        return quote(this.plan, this.paying, rate(state), state.feeDiscount());
    }

    private Component price() {
        Currency priced = priced();
        return priced == null ? FxText.get("fxeasy.shop.unavailable")
                : FxText.literal("%s × %s", Long.toString(this.plan.priceItems()), priced.code());
    }

    private Component rateText(FxClientState state) {
        Currency priced = priced();
        if (priced == null || this.paying == null || this.paying == priced) {
            return FxText.literal("1:1");
        }
        return FxText.literal("1 %s ≈ %s %s", priced.code(), FxUi.rate(rate(state)), this.paying.code());
    }

    private Component converted(FxClientState state) {
        if (this.paying == null) {
            return FxText.get("fxeasy.checkout.empty");
        }
        return FxText.literal("%s × %s", FxUi.units(quote(state).convertedMicro()), this.paying.code());
    }

    private Component fee(FxClientState state) {
        if (this.paying == null) {
            return FxText.get("fxeasy.checkout.empty");
        }
        if (this.paying == priced()) {
            return FxText.get("fxeasy.shop.fee.standard");
        }
        return FxText.literal("%s · +%s × %s", FxUi.rate(state.conversionSpread()),
                FxUi.units(quote(state).feeMicro()), this.paying.code());
    }

    private Component total(long total) {
        return this.paying == null ? FxText.get("fxeasy.checkout.empty")
                : FxText.literal("%s × %s", FxUi.units(total), this.paying.code());
    }

    /// Game days one period of the plan runs, which is what the card writes under its title.
    private static String days() {
        return Long.toString(Membership.PERIOD_TICKS / MarketEngine.TICKS_PER_DAY);
    }

    public static ScreenRectangle card(int designWidth, int designHeight) {
        int width = Math.min(CARD_WIDTH, designWidth - FxTheme.PAD * 2);
        int wanted = listOffset() + MAX_ROWS * ROW + FxTheme.GAP + 1 + FxTheme.GAP + LINE + FxTheme.GAP
                + BUTTON_HEIGHT + PAD;
        int height = Math.min(wanted, designHeight - FxTheme.PAD * 2);
        return FxUi.rect((designWidth - width) / 2, (designHeight - height) / 2, width, height);
    }

    private static int listOffset() {
        return PAD + LINE + LINE + FxTheme.GAP + 1 + FxTheme.GAP + RECEIPT * LINE + FxTheme.GAP + 1 + FxTheme.GAP
                + LINE + FxTheme.GAP;
    }

    private static int receiptTop(ScreenRectangle card) {
        return card.top() + PAD + LINE + LINE + FxTheme.GAP + 1 + FxTheme.GAP;
    }

    private static int sectionTop(ScreenRectangle card) {
        return card.top() + listOffset() - LINE - FxTheme.GAP;
    }

    private static int noteTop(ScreenRectangle card) {
        return buttons(card).pay().top() - LINE - FxTheme.GAP;
    }

    public static ScreenRectangle closeArea(int designWidth, int designHeight) {
        ScreenRectangle card = card(designWidth, designHeight);
        return FxUi.rect(card.right() - PAD - CLOSE_WIDTH, card.top() + PAD - 1, CLOSE_WIDTH, LINE + 1);
    }

    public static ScreenRectangle listArea(int designWidth, int designHeight) {
        ScreenRectangle card = card(designWidth, designHeight);
        int top = card.top() + listOffset();
        int bottom = noteTop(card) - 1 - FxTheme.GAP - FxTheme.GAP;
        return FxUi.rect(card.left() + PAD, top, Math.max(1, card.width() - PAD * 2),
                Math.max(ROW, bottom - top));
    }

    public static int rows(int designWidth, int designHeight) {
        return Math.max(1, listArea(designWidth, designHeight).height() / ROW);
    }

    /// The cell of one currency of the list, or null while the list is not showing it: the list scrolls and a
    /// currency that does not exist is no currency at all, so one under the window of the list - or an index
    /// past the end names - is no cell of this frame.
    public ScreenRectangle rowArea(int index, int count, int designWidth, int designHeight) {
        if (index < 0 || index >= count) {
            return null;
        }
        int visible = rows(designWidth, designHeight);
        int top = Math.clamp(this.scroll, 0, Math.max(0, count - visible));
        if (index < top || index >= top + visible) {
            return null;
        }
        ScreenRectangle list = listArea(designWidth, designHeight);
        return FxUi.rect(list.left(), list.top() + (index - top) * ROW, list.width(), ROW - 2);
    }

    /// The two buttons of the card, which the screen aims a scripted click at.
    public static Buttons buttons(ScreenRectangle card) {
        ScreenRectangle pay = FxUi.rect(card.right() - PAD - BUTTON_WIDTH, card.bottom() - PAD - BUTTON_HEIGHT,
                BUTTON_WIDTH, BUTTON_HEIGHT);
        ScreenRectangle cancel = FxUi.rect(pay.left() - FxTheme.GAP - BUTTON_WIDTH, pay.top(), BUTTON_WIDTH,
                BUTTON_HEIGHT);
        return new Buttons(pay, cancel);
    }
}
