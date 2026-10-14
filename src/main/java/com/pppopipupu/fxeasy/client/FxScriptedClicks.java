package com.pppopipupu.fxeasy.client;

import module java.base;

import com.pppopipupu.fxeasy.client.panel.FxAccount;
import com.pppopipupu.fxeasy.client.panel.FxConfirm;
import com.pppopipupu.fxeasy.client.panel.FxControlRow;
import com.pppopipupu.fxeasy.client.panel.FxInput;
import com.pppopipupu.fxeasy.client.panel.FxMarketView;
import com.pppopipupu.fxeasy.client.panel.FxOverlays;
import com.pppopipupu.fxeasy.client.panel.FxPositions;
import com.pppopipupu.fxeasy.client.panel.FxShop;
import com.pppopipupu.fxeasy.client.panel.FxTab;
import com.pppopipupu.fxeasy.client.panel.FxTerminalLayout;
import com.pppopipupu.fxeasy.client.panel.FxTicket;
import com.pppopipupu.fxeasy.client.panel.FxWorldIntel;
import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;
import com.pppopipupu.fxeasy.market.Timeframe;
import net.minecraft.client.gui.navigation.ScreenRectangle;

/// The target names a scripted click aims at, resolved against the rectangles of the frame that is being drawn:
/// the cells of the panel and the cells of whatever overlay is up. No harness command can click a client whose
/// screen blocks the client thread, so the end to end walk, the probe and the regression drive the panel by name
/// instead, and a name resolves to the centre of the cell this frame draws for it rather than to a pixel, which
/// is what keeps a run independent of the window it runs in. A target the current layout does not draw, or one
/// an overlay owns while another overlay is up, resolves to nothing.
public record FxScriptedClicks(FxTab tab, FxClientState state, FxTerminalLayout layout, int designWidth,
        int designHeight, FxMarketView market, FxPositions positions, FxAccount account, FxOverlays overlays,
        FxInput panel) {

    /// The rectangle a named click aims at, or null when this frame does not show that cell or when the cell
    /// cannot take a click. The frame draws the panel under whatever overlay is up, so a cell of the panel is
    /// only clickable while no overlay owns the pointer, and a cell of an overlay only while that overlay does.
    public ScreenRectangle area(String target) {
        ScreenRectangle area = geometry(target);
        if (area == null) {
            return null;
        }
        FxInput owner = owner(target);
        return this.overlays.pointer(this.state, this.panel) == (owner == null ? this.panel : owner) ? area : null;
    }

    /// The input the cells a target name belongs to are driven by, or null for a cell of the panel itself.
    private FxInput owner(String target) {
        if (target.startsWith("opening.") || target.startsWith("tutorial.")) {
            return this.overlays.input();
        }
        if (target.startsWith("bonus.")) {
            return this.overlays.bonus().input();
        }
        if (target.startsWith("confirm.")) {
            return this.overlays.confirm().input();
        }
        if (target.startsWith("lounge.")) {
            return this.overlays.lounge().input();
        }
        if (target.startsWith("checkout.")) {
            return this.overlays.checkout().input();
        }
        if (target.startsWith("intel.")) {
            return this.overlays.intel().input();
        }
        return null;
    }

    /// Where a named click aims, without asking whether the cell can take the click: the panel draws its own
    /// cells under an overlay, and the walkthrough points at them, so the geometry is needed either way.
    public ScreenRectangle geometry(String target) {
        FxControlRow.Geometry controls = this.controls();
        if (target.startsWith("tab.")) {
            String name = target.substring("tab.".length());
            for (FxTab candidate : FxTab.values()) {
                if (candidate.name().equalsIgnoreCase(name)) {
                    return FxControlRow.cell(controls, candidate.ordinal());
                }
            }
            return null;
        }
        if (target.startsWith("intel.view.")) {
            String name = target.substring("intel.view.".length());
            for (FxWorldIntel.View candidate : FxWorldIntel.View.values()) {
                if (candidate.name().equalsIgnoreCase(name)) {
                    return this.overlays.intel().viewArea(candidate, this.designWidth, this.designHeight);
                }
            }
            return null;
        }
        return switch (target) {
            case "opening.next", "opening.register" -> {
                FxAccountOpening.Cells cells = this.openingCells();
                yield cells == null ? null : cells.primary();
            }
            case "opening.back" -> {
                FxAccountOpening.Cells cells = this.openingCells();
                yield cells == null ? null : cells.back();
            }
            case "opening.name" -> {
                FxAccountOpening.Cells cells = this.openingCells();
                yield cells == null ? null : cells.name();
            }
            case "opening.age" -> {
                FxAccountOpening.Cells cells = this.openingCells();
                yield cells == null ? null : cells.age();
            }
            case "tutorial.next", "tutorial.back", "tutorial.skip" -> this.tutorialCell(target);
            case "trade.lots" -> this.tab == FxTab.TRADE ? FxTicket.lotsArea(this.layout.sideInner()) : null;
            case "trade.leverage" ->
                this.tab == FxTab.TRADE ? FxTicket.leverageArea(this.layout.sideInner()) : null;
            case "intel" -> FxControlRow.cell(controls, FxTab.values().length);
            case "intel.close" -> this.overlays.intel().closeArea(this.designWidth, this.designHeight);
            case "lounge.close" -> this.overlays.lounge().active()
                    ? FxLounge.closeArea(this.designWidth, this.designHeight) : null;
            case "checkout.pay" -> this.overlays.checkout().active()
                    ? FxCheckout.buttons(FxCheckout.card(this.designWidth, this.designHeight)).pay() : null;
            case "checkout.cancel" -> this.overlays.checkout().active()
                    ? FxCheckout.buttons(FxCheckout.card(this.designWidth, this.designHeight)).cancel() : null;
            case "checkout.close" -> this.overlays.checkout().active()
                    ? FxCheckout.closeArea(this.designWidth, this.designHeight) : null;
            case "shop.renew" -> this.tab == FxTab.SHOP
                    ? FxShop.footerCells(FxShop.footerArea(this.layout.list())).renew() : null;
            case "shop.lounge" -> this.tab == FxTab.SHOP
                    ? FxShop.footerCells(FxShop.footerArea(this.layout.list())).lounge() : null;
            case "bonus.ok" -> this.overlays.bonus().active(this.state)
                    ? this.overlays.bonus().cells(this.designWidth, this.designHeight).action() : null;
            case "bonus.close" -> this.overlays.bonus().active(this.state)
                    ? this.overlays.bonus().cells(this.designWidth, this.designHeight).close() : null;
            case "confirm.ok" -> {
                FxConfirm.Buttons buttons = this.overlays.confirm().buttons(this.designWidth, this.designHeight);
                yield buttons == null ? null : buttons.confirm();
            }
            case "confirm.cancel" -> {
                FxConfirm.Buttons buttons = this.overlays.confirm().buttons(this.designWidth, this.designHeight);
                yield buttons == null ? null : buttons.cancel();
            }
            case "account.slider" ->
                this.tab == FxTab.ACCOUNT ? FxAccount.sliderArea(this.layout.sideInner()) : null;
            case "account.deposit" ->
                this.tab == FxTab.ACCOUNT ? FxAccount.buttons(this.layout.sideInner()).deposit() : null;
            case "account.withdraw" ->
                this.tab == FxTab.ACCOUNT ? FxAccount.buttons(this.layout.sideInner()).withdraw() : null;
            case "positions.close_all" ->
                this.tab == FxTab.POSITIONS ? FxPositions.closeAllArea(this.layout.sideInner()) : null;
            case "trade.open" -> this.tab == FxTab.TRADE ? FxTicket.openArea(this.layout.sideInner()) : null;
            case "basis.next" -> FxControlRow.basisCell(controls);
            default -> this.namedArea(target);
        };
    }

    /// The cells that carry a name of their own: a row of the market list, a margin currency cell, a timeframe
    /// cell, a chart style cell, a position row and its close cell, a card of the shop, a gift of the lounge and
    /// the currency row of the account page.
    private ScreenRectangle namedArea(String target) {
        FxControlRow.Geometry controls = this.controls();
        if (target.startsWith("shop.buy.")) {
            if (this.tab != FxTab.SHOP) {
                return null;
            }
            String name = target.substring("shop.buy.".length());
            for (FxShop.CardCells card : FxShop.cards(this.layout.list())) {
                if (card.plan().id().equalsIgnoreCase(name)) {
                    return card.action();
                }
            }
            return null;
        }
        if (target.startsWith("lounge.claim.")) {
            Integer index = index(target, "lounge.claim.");
            return index == null || !this.overlays.lounge().active() ? null
                    : this.overlays.lounge().claimArea(index, this.state.lounge().size(), this.designWidth,
                            this.designHeight);
        }
        if (target.startsWith("checkout.currency.")) {
            String name = target.substring("checkout.currency.".length());
            FxCheckout checkout = this.overlays.checkout();
            if (!checkout.active()) {
                return null;
            }
            List<Currency> payable = FxCheckout.payable(this.state);
            for (int index = 0; index < payable.size(); index++) {
                if (payable.get(index).id().equalsIgnoreCase(name)) {
                    return checkout.rowArea(index, payable.size(), this.designWidth, this.designHeight);
                }
            }
            return null;
        }
        if (target.startsWith("market.row.")) {
            Integer index = index(target, "market.row.");
            return index == null || this.tab != FxTab.MARKET ? null : this.market.rowArea(this.layout.list(), index);
        }
        if (target.startsWith("trade.margin.")) {
            if (this.tab != FxTab.TRADE) {
                return null;
            }
            String name = target.substring("trade.margin.".length());
            List<ScreenRectangle> cells = FxTicket.marginAreas(this.layout.sideInner());
            for (int index = 0; index < Currencies.count() && index < cells.size(); index++) {
                if (Currencies.byIndex(index).id().equalsIgnoreCase(name)) {
                    return cells.get(index);
                }
            }
            return null;
        }
        if (target.startsWith("timeframe.")) {
            String name = target.substring("timeframe.".length());
            for (int index = 0; index < Timeframe.ALL.size(); index++) {
                if (Timeframe.ALL.get(index).name().equalsIgnoreCase(name)) {
                    return FxControlRow.timeframeCell(controls, index);
                }
            }
            return null;
        }
        if (target.startsWith("chart.")) {
            String name = target.substring("chart.".length());
            FxClientState.ChartStyle[] styles = FxClientState.ChartStyle.values();
            for (int index = 0; index < styles.length; index++) {
                if (styles[index].name().equalsIgnoreCase(name)) {
                    return FxControlRow.chartStyleCell(controls, index);
                }
            }
            return null;
        }
        if (target.startsWith("positions.close.")) {
            Integer index = index(target, "positions.close.");
            return index == null || this.tab != FxTab.POSITIONS ? null
                    : this.positions.closeArea(this.layout.sideInner(), index);
        }
        if (target.startsWith("positions.row.")) {
            Integer index = index(target, "positions.row.");
            return index == null || this.tab != FxTab.POSITIONS ? null
                    : this.positions.rowArea(this.layout.sideInner(), index);
        }
        return this.accountCurrencyArea(target);
    }

    /// The index a target name ends in, or null when it does not end in one.
    private static Integer index(String target, String prefix) {
        try {
            return Integer.valueOf(Integer.parseInt(target.substring(prefix.length()).trim()));
        } catch (NumberFormatException error) {
            return null;
        }
    }

    /// The cells of the card of the walkthrough step that is up, or null while the walkthrough is not.
    private ScreenRectangle tutorialCell(String target) {
        if (!this.overlays.tutorial().active()) {
            return null;
        }
        int step = this.overlays.tutorial().step();
        FxTutorial.Cells cells = FxTutorial.cells(this.overlays.steps().get(step), step, this.designWidth,
                this.designHeight);
        return switch (target) {
            case "tutorial.next" -> cells.primary();
            case "tutorial.back" -> cells.back();
            default -> cells.skip();
        };
    }

    /// The cells of the step of the account opening that is on screen, or null while the opening is not up.
    private FxAccountOpening.Cells openingCells() {
        if (!this.overlays.opening().active()) {
            return null;
        }
        int step = this.overlays.opening().step();
        List<FxAccountOpening.Step> steps = FxAccountOpening.steps();
        return FxAccountOpening.cells(steps.get(step), step, steps.size(), this.designWidth, this.designHeight);
    }

    /// The currency row of the account page named `account.currency.<id>`, which is what a run picks before it
    /// moves items in or out of that currency.
    private ScreenRectangle accountCurrencyArea(String target) {
        if (this.tab != FxTab.ACCOUNT || !target.startsWith("account.currency.")) {
            return null;
        }
        String name = target.substring("account.currency.".length());
        for (Currency currency : Currencies.all()) {
            if (currency.id().equalsIgnoreCase(name)) {
                return this.account.currencyArea(this.layout.sideInner(), currency);
            }
        }
        return null;
    }

    private FxControlRow.Geometry controls() {
        return FxControlRow.geometry(this.layout.controls(), this.tab.chart());
    }
}
