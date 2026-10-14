package com.pppopipupu.fxeasy.client.panel;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import com.pppopipupu.fxeasy.client.FxActions;
import com.pppopipupu.fxeasy.client.FxClientState;
import com.pppopipupu.fxeasy.client.FxText;
import com.pppopipupu.fxeasy.content.FxCurrencies;
import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;
import com.pppopipupu.fxeasy.market.FeeModel;
import com.pppopipupu.fxeasy.trade.Money;
import com.pppopipupu.fxeasy.trade.TradeEngine;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

/// Account tab: the session equity curve in items of the display currency, one amount slider that acts on the
/// selected currency and one row per currency with its balance, realized profit, free margin and how much the
/// player carries as items. The slider moves in whole items of the selected currency and its range is what either action could
/// move - the items the player carries for a deposit, what the account has free for a withdrawal - so the amount
/// can never be dragged past what the platform would accept. Picking a currency starts it on the items the
/// player carries, or on the free margin when they carry none, and it follows what they pick up or hand over:
/// the free margin is marked to market, so a slider that followed it would be reset by a moving price instead of
/// holding the amount the player dragged it to.
/// A currency in the red shows what it owes and how long the debt has been outstanding, because that is what the
/// credit of the account is measured from. Moving items in or out of the account is the part of the panel that
/// changes what the player owns, so both buttons report a [FxConfirm.Request] carrying the currency and the
/// amount of that click instead of sending it.
public final class FxAccount {

    public static final int DRAG_AMOUNT = 12;
    private static final int MIN_CHART_HEIGHT = 50;
    private static final int MAX_CHART_HEIGHT = 120;
    private static final int AMOUNT_ROW = 13;
    private static final int HOLDER_ROW = 11;
    private static final int INFO_ROW = 10;
    private static final int ROW_HEIGHT = 21;
    private static final int SCROLL_STEP = 3;

    /// The deposit and the withdraw button of the page.
    public record Buttons(ScreenRectangle deposit, ScreenRectangle withdraw) {
    }

    private long amount;
    private long lastInventory = -1L;
    private Currency lastSelected;
    private Currency loggedCurrency;
    private long loggedAmount = Long.MIN_VALUE;
    private long loggedMaximum = Long.MIN_VALUE;
    private boolean loggedDeposit;
    private boolean loggedWithdraw;
    private long previousAmount = Long.MIN_VALUE;
    private long previousMaximum = Long.MIN_VALUE;
    private int scroll;
    private Currency selected = Currencies.all().getFirst();

    public FxConfirm.Request draw(GuiGraphicsExtractor graphics, Font font, FxClientState state, FxInput input,
            ScreenRectangle area) {
        if (!Currencies.all().contains(this.selected)) {
            this.selected = Currencies.all().getFirst();
        }
        int x = area.left();
        int width = area.width();
        int y = area.top();
        holder(graphics, font, state, area, y);
        y += HOLDER_ROW + FxTheme.GAP;
        int chartHeight = Math.clamp(area.height() / 4, MIN_CHART_HEIGHT, MAX_CHART_HEIGHT);
        FxEquityChart.draw(graphics, font, state, FxUi.rect(x, y, width, chartHeight), input.mouseX, input.mouseY);
        y += chartHeight + FxTheme.GAP;
        long inventory = FxActions.inventoryCount(this.selected);
        long free = state.freeMargin(this.selected) / Money.UNIT;
        long maximum = Math.max(inventory, free);
        if (this.selected != this.lastSelected || inventory != this.lastInventory) {
            this.lastSelected = this.selected;
            this.lastInventory = inventory;
            this.amount = inventory > 0L ? inventory : free;
        }
        this.amount = Math.clamp(this.amount, 0L, maximum);
        ScreenRectangle slider = sliderArea(area);
        FxControls.slider(graphics, font, slider, maximum <= 0L ? 0.0D : this.amount / (double) maximum,
                input.hovered(slider), input.dragging(DRAG_AMOUNT));
        if (input.clicked(slider)) {
            input.beginDrag(DRAG_AMOUNT);
        }
        if (input.dragging(DRAG_AMOUNT)) {
            double fraction = (input.mouseX - slider.left()) / (double) Math.max(1, slider.width() - 1);
            this.amount = Math.clamp(Math.round(Math.clamp(fraction, 0.0D, 1.0D) * maximum), 0L, maximum);
        }
        FxUi.text(graphics, font, slider.left() + 2, slider.top() + 1,
                FxText.literal(FxUi.units(this.amount * Money.UNIT)), FxTheme.TEXT);
        FxUi.right(graphics, font, slider.right() - 2, slider.top() + 1, FxText.literal(this.selected.code()), FxTheme.MUTED);
        long amount = this.amount;
        y += AMOUNT_ROW + FxTheme.GAP;
        Buttons row = buttons(area);
        ScreenRectangle deposit = row.deposit();
        ScreenRectangle withdraw = row.withdraw();
        int half = (width - FxTheme.GAP) / 2;
        boolean canDeposit = amount > 0L && amount <= FxActions.inventoryCount(this.selected);
        boolean canWithdraw = amount > 0L && amount * Money.UNIT <= state.freeMargin(this.selected);
        FxControls.button(graphics, font, deposit, FxText.get("fxeasy.panel.deposit"), ARGB.multiplyAlpha(FxTheme.TEXT, 0.16F),
                FxTheme.TEXT, input.hovered(deposit), false, canDeposit);
        FxControls.button(graphics, font, withdraw, FxText.get("fxeasy.panel.withdraw"), ARGB.multiplyAlpha(FxTheme.TEXT, 0.16F),
                FxTheme.TEXT, input.hovered(withdraw), false, canWithdraw);
        logAmount(state, maximum, amount, canDeposit, canWithdraw);
        Currency moving = this.selected;
        if (input.clicked(deposit) && amount > 0L) {
            return new FxConfirm.Request("deposit", FxText.get("fxeasy.confirm.deposit.title"),
                    List.of(FxText.get("fxeasy.confirm.deposit.body", FxUi.units(amount * Money.UNIT), moving.code())),
                    FxText.get("fxeasy.confirm.deposit.confirm"), () -> FxActions.deposit(moving, amount));
        }
        if (input.clicked(withdraw) && amount > 0L) {
            return new FxConfirm.Request("withdraw", FxText.get("fxeasy.confirm.withdraw.title"),
                    List.of(FxText.get("fxeasy.confirm.withdraw.body", FxUi.units(amount * Money.UNIT), moving.code())),
                    FxText.get("fxeasy.confirm.withdraw.confirm"), () -> FxActions.withdraw(moving, amount));
        }
        y += deposit.height() + FxTheme.GAP;
        FxUi.text(graphics, font, x, y, FxText.get("fxeasy.panel.rate"), FxTheme.MUTED);
        FxUi.right(graphics, font, half - 2, y, FxText.literal(FxUi.rate(this.selected.annualRate())), FxTheme.TEXT);
        FxUi.text(graphics, font, half + 2, y, FxText.get("fxeasy.panel.haircut"), FxTheme.MUTED);
        FxUi.right(graphics, font, area.right(), y, FxText.literal(FxUi.rate(FeeModel.haircut(this.selected))), FxTheme.TEXT);
        y += INFO_ROW;
        FxUi.text(graphics, font, x, y, FxText.get("fxeasy.panel.debt.rate"), FxTheme.MUTED);
        FxUi.right(graphics, font, half - 2, y, FxText.literal(FxUi.rate(TradeEngine.DEBT_RATE)), FxTheme.TEXT);
        FxUi.text(graphics, font, half + 2, y, FxText.get("fxeasy.panel.credit"), FxTheme.MUTED);
        FxUi.right(graphics, font, area.right(), y,
                FxText.get(state.blacklisted() ? "fxeasy.panel.credit.black" : "fxeasy.panel.credit.good"),
                state.blacklisted() ? FxTheme.DOWN : FxTheme.TEXT);
        y += INFO_ROW;
        ScreenRectangle list = listArea(area);
        int visible = Math.max(1, list.height() / ROW_HEIGHT);
        int scrollMax = Math.max(0, Currencies.count() - visible);
        if (input.scroll != 0.0D && input.hovered(list)) {
            this.scroll -= (int) Math.signum(input.scroll) * SCROLL_STEP;
            input.scroll = 0.0D;
        }
        this.scroll = Math.clamp(this.scroll, 0, scrollMax);
        FxUi.pushClip(graphics, list);
        int rowY = list.top();
        for (int index = this.scroll; index < Math.min(Currencies.count(), this.scroll + visible); index++) {
            Currency currency = Currencies.byIndex(index);
            row(graphics, font, state, input, list, currency, rowY);
            rowY += ROW_HEIGHT;
        }
        FxUi.popClip(graphics);
        if (scrollMax > 0) {
            FxControls.scrollbar(graphics, FxUi.rect(area.right() - 3, list.top(), 3, list.height()),
                    this.scroll / (double) scrollMax, visible / (double) Currencies.count(), input.hovered(list));
        }
        return null;
    }

    /// The scrolling currency list of the page, which the draw and a scripted click both read: the chart, the
    /// slider, the two buttons and the two information rows sit above it.
    public static ScreenRectangle listArea(ScreenRectangle area) {
        int chartHeight = Math.clamp(area.height() / 4, MIN_CHART_HEIGHT, MAX_CHART_HEIGHT);
        int top = area.top() + HOLDER_ROW + FxTheme.GAP + chartHeight + FxTheme.GAP + AMOUNT_ROW + FxTheme.GAP
                + FxTheme.ROW + 2 + FxTheme.GAP + INFO_ROW + INFO_ROW;
        return FxUi.rect(area.left(), top, area.width() - 4, area.bottom() - top);
    }

    /// The row of one currency, or null when it is scrolled out of the list and therefore not on screen.
    public ScreenRectangle currencyArea(ScreenRectangle area, Currency currency) {
        ScreenRectangle list = listArea(area);
        int visible = Math.max(1, list.height() / ROW_HEIGHT);
        int maximum = Math.max(0, Currencies.count() - visible);
        int scroll = Math.clamp(this.scroll, 0, maximum);
        int index = Currencies.index(currency);
        if (index < scroll || index >= scroll + visible) {
            return null;
        }
        return FxUi.rect(list.left(), list.top() + (index - scroll) * ROW_HEIGHT - 1, list.width(), ROW_HEIGHT - 3);
    }

    public static ScreenRectangle sliderArea(ScreenRectangle area) {
        int chartHeight = Math.clamp(area.height() / 4, MIN_CHART_HEIGHT, MAX_CHART_HEIGHT);
        return FxUi.rect(area.left(), area.top() + HOLDER_ROW + FxTheme.GAP + chartHeight + FxTheme.GAP,
                area.width(), AMOUNT_ROW);
    }

    private void logAmount(FxClientState state, long maximum, long amount, boolean canDeposit, boolean canWithdraw) {
        boolean settled = amount == this.previousAmount && maximum == this.previousMaximum;
        this.previousAmount = amount;
        this.previousMaximum = maximum;
        if (settled && (this.selected != this.loggedCurrency || amount != this.loggedAmount
                || maximum != this.loggedMaximum || canDeposit != this.loggedDeposit
                || canWithdraw != this.loggedWithdraw)) {
            this.loggedCurrency = this.selected;
            this.loggedAmount = amount;
            this.loggedMaximum = maximum;
            this.loggedDeposit = canDeposit;
            this.loggedWithdraw = canWithdraw;
            FxEasy.LOGGER.info(
                    "FX account slider: {} carries {}, free {}, range {}, amount {}, deposit {}, withdraw {}, equity {} {}, used {} {}, unrealized {} {}",
                    this.selected.code(), FxActions.inventoryCount(this.selected),
                    state.freeMargin(this.selected) / Money.UNIT, maximum, amount, canDeposit, canWithdraw,
                    FxUi.amount(state.equityItems(state.basis())), state.basis().code(),
                    FxUi.amount(state.usedMarginItems(state.basis())), state.basis().code(),
                    FxUi.signedAmount(state.unrealizedItems(state.basis())), state.basis().code());
            FxEasy.LOGGER.info("FX account row: {} balance {}, realized {}, free {}, carried x{}",
                    this.selected.code(), FxUi.units(state.balance(this.selected)),
                    FxUi.signedAmount(Money.units(state.realized(this.selected))),
                    FxUi.units(state.freeMargin(this.selected)),
                    FxActions.inventoryCount(this.selected));
        }
    }

    /// The two buttons of the page, under the equity curve and the amount slider. Both the draw and a scripted
    /// click read this one row, so the rectangle a run aims at is the rectangle the player sees.
    public static Buttons buttons(ScreenRectangle area) {
        int chartHeight = Math.clamp(area.height() / 4, MIN_CHART_HEIGHT, MAX_CHART_HEIGHT);
        int y = area.top() + HOLDER_ROW + FxTheme.GAP + chartHeight + FxTheme.GAP + AMOUNT_ROW + FxTheme.GAP;
        int half = (area.width() - FxTheme.GAP) / 2;
        return new Buttons(FxUi.rect(area.left(), y, half, FxTheme.ROW + 2),
                FxUi.rect(area.left() + half + FxTheme.GAP, y, area.width() - half - FxTheme.GAP, FxTheme.ROW + 2));
    }

    /// Who the account is registered to, which is what the registration in the opening asks for.
    private static void holder(GuiGraphicsExtractor graphics, Font font, FxClientState state, ScreenRectangle area,
            int y) {
        boolean registered = !state.holder().isEmpty();
        Component label = FxText.get("fxeasy.panel.holder");
        FxUi.text(graphics, font, area.left(), y, label, FxTheme.MUTED);
        FxUi.text(graphics, font, area.left() + font.width(label) + 4, y,
                registered ? FxText.literal(state.holder()) : FxText.get("fxeasy.panel.holder.none"),
                FxTheme.TEXT);
        FxUi.labeledRight(graphics, font, area.right(), y, FxText.get("fxeasy.panel.age"),
                FxText.literal(registered ? Integer.toString(state.age()) : "-"), FxTheme.TEXT);
    }

    private void row(GuiGraphicsExtractor graphics, Font font, FxClientState state, FxInput input,
            ScreenRectangle list, Currency currency, int y) {
        ScreenRectangle row = FxUi.rect(list.left(), y - 1, list.width(), ROW_HEIGHT - 3);
        boolean active = currency == this.selected;
        if (active) {
            FxUi.fill(graphics, row.left(), row.top(), row.right(), row.bottom(), FxTheme.SELECTED);
        } else if (input.hovered(row)) {
            FxUi.fill(graphics, row.left(), row.top(), row.right(), row.bottom(), FxTheme.BUTTON);
        }
        if (input.clicked(row)) {
            this.selected = currency;
        }
        long realized = state.realized(currency);
        long debt = state.debt(currency);
        FxUi.item(graphics, FxCurrencies.stack(currency, 1), list.left(), y - 2, 1);
        Component realizedText = FxText.literal(FxUi.signedAmount(Money.units(realized)));
        FxUi.clipped(graphics, font, list, list.left() + 20, y, FxText.name(currency),
                active ? FxTheme.ACCENT : FxTheme.TEXT);
        FxUi.right(graphics, font, list.right() - 2, y, FxText.literal(FxUi.units(state.balance(currency))),
                debt > 0L ? FxTheme.DOWN : FxTheme.TEXT);
        FxUi.clipped(graphics, font, list, list.left() + 20, y + 10, realizedText,
                realized >= 0L ? FxTheme.UP : FxTheme.DOWN);
        Component second = debt > 0L
                ? FxText.get("fxeasy.panel.debt.age", FxUi.days(state.debtAgeDays(currency)))
                : FxText.literal(FxUi.units(state.freeMargin(currency)));
        FxUi.clipped(graphics, font, list, list.left() + 26 + font.width(realizedText), y + 10, second,
                debt > 0L ? FxTheme.DOWN : FxTheme.MUTED);
        FxUi.right(graphics, font, list.right() - 2, y + 10,
                FxText.literal("x" + FxActions.inventoryCount(currency)), FxTheme.MUTED);
        FxUi.separator(graphics, list.left(), list.right(), y + ROW_HEIGHT - 3);
    }
}