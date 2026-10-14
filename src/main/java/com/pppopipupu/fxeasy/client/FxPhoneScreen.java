package com.pppopipupu.fxeasy.client;

import module java.base;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.pppopipupu.fxeasy.FxEasy;
import com.pppopipupu.fxeasy.client.panel.FxAccount;
import com.pppopipupu.fxeasy.client.panel.FxChartView;
import com.pppopipupu.fxeasy.client.panel.FxConfirm;
import com.pppopipupu.fxeasy.client.panel.FxControlRow;
import com.pppopipupu.fxeasy.client.panel.FxField;
import com.pppopipupu.fxeasy.client.panel.FxHeader;
import com.pppopipupu.fxeasy.client.panel.FxInput;
import com.pppopipupu.fxeasy.client.panel.FxMarketView;
import com.pppopipupu.fxeasy.client.panel.FxMetrics;
import com.pppopipupu.fxeasy.client.panel.FxNotice;
import com.pppopipupu.fxeasy.client.panel.FxOverlays;
import com.pppopipupu.fxeasy.client.panel.FxPositions;
import com.pppopipupu.fxeasy.client.panel.FxShop;
import com.pppopipupu.fxeasy.client.panel.FxTab;
import com.pppopipupu.fxeasy.client.panel.FxTerminalLayout;
import com.pppopipupu.fxeasy.client.panel.FxTheme;
import com.pppopipupu.fxeasy.client.panel.FxTicket;
import com.pppopipupu.fxeasy.client.panel.FxUi;
import com.pppopipupu.fxeasy.market.Currency;
import com.pppopipupu.fxeasy.market.CurrencyPair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;

/// This class is the screen itself and nothing else: it measures the frame, dispatches the mouse and the
/// keyboard, reveals the panel with a scan line and hands every part of the frame to the class that draws it -
/// [FxHeader] the header, [FxControlRow] the row of the page tabs and the chart settings, the pages of the panel
/// package the page on screen, [FxMetrics] the strip every page keeps in view, [FxNotice] the bar along the
/// bottom and [FxOverlays] whatever stands in front of all of it. [FxTerminalLayout] says where those parts
/// stand, and [FxScriptedClicks] resolves the target name a scripted run clicks against the same rectangles.
public final class FxPhoneScreen extends Screen {

    private static final int DRAG_CHART = 1;
    /// Two physical pixels per design unit: at that density the bundled font's glyph atlas (oversampled twice)
    /// lands on exactly one texel per pixel whatever the window size and the GUI scale are, and the panel keeps
    /// the metrics it was laid out with instead of being stretched to the GUI scale.
    private static final int PHYSICAL_PER_UNIT = 2;
    private static final int MIN_DESIGN_HEIGHT = 240;
    /// How long the opening scan line takes to travel from the header to the bottom of the panel. A click skips
    /// the rest of it.
    private static final long REVEAL_MILLIS = 700L;
    private static final int SCAN_LINE = 2;
    private static final int SCAN_TRAIL = 22;
    /// The widest price the terminal prints, which is what the price axis of the chart is measured against.
    private static final String AXIS_LABEL = "00000.0000";

    private final FxClientState state = FxClientState.INSTANCE;
    private final FxMarketView market = new FxMarketView();
    private final FxChartView chart = new FxChartView();
    private final FxPositions positions = new FxPositions();
    private final FxAccount account = new FxAccount();
    private final FxShop shop = new FxShop();
    private final FxInput input = new FxInput();
    private final FxOverlays overlays = new FxOverlays();

    private FxTab tab = FxTab.MARKET;
    private int designWidth;
    private int designHeight;
    private float scale = 1.0F;
    private int frame;
    private boolean layoutLogged;
    private boolean testRelease;
    private long openedAt;
    private boolean revealing;

    public FxPhoneScreen() {
        super(Component.translatable("item.fxeasy.fx_phone"));
    }

    @Override
    protected void init() {
        measure();
        this.openedAt = Util.getMillis();
        this.revealing = true;
        Window window = Minecraft.getInstance().getWindow();
        FxEasy.LOGGER.info("FX terminal opened, framebuffer {}x{} with {}x{} design units at scale {}", window.getWidth(),
                window.getHeight(), this.designWidth, this.designHeight, this.scale);
        FxActions.subscribe(this.state.selectedPair(), this.state.selectedTimeframe());
    }

    /// The panel lays itself out in design units and draws them through one scaled pose, so every window size
    /// and GUI scale ends up at the same physical density and the glyph atlas is never stretched.
    private void measure() {
        Window window = Minecraft.getInstance().getWindow();
        this.designHeight = Math.max(MIN_DESIGN_HEIGHT, Math.round(window.getHeight() / (float) PHYSICAL_PER_UNIT));
        this.scale = this.height / (float) this.designHeight;
        this.designWidth = Math.round(this.width / this.scale);
        this.input.focus = null;
        this.overlays.measure(this.buildSteps());
    }

    /// The areas the first run walkthrough points at, in the order it walks them: the control row, the market
    /// list, the chart, the ticket and the metrics strip. The steps are rebuilt with the layout, because they
    /// are rectangles of it.
    private List<FxTutorial.Step> buildSteps() {
        FxTerminalLayout layout = this.layout();
        return List.of(
                new FxTutorial.Step(FxTab.MARKET, layout.controls(), "fxeasy.tutorial.tabs.title",
                        "fxeasy.tutorial.tabs.body", "fxeasy.tutorial.tabs.hint"),
                new FxTutorial.Step(FxTab.MARKET, layout.list(), "fxeasy.tutorial.list.title",
                        "fxeasy.tutorial.list.body", "fxeasy.tutorial.list.hint"),
                new FxTutorial.Step(FxTab.TRADE, layout.plot(), "fxeasy.tutorial.chart.title",
                        "fxeasy.tutorial.chart.body", "fxeasy.tutorial.chart.hint"),
                new FxTutorial.Step(FxTab.TRADE, layout.side(), "fxeasy.tutorial.ticket.title",
                        "fxeasy.tutorial.ticket.body", "fxeasy.tutorial.ticket.hint"),
                new FxTutorial.Step(FxTab.TRADE, layout.sideInfoArea(), "fxeasy.tutorial.metrics.title",
                        "fxeasy.tutorial.metrics.body", "fxeasy.tutorial.metrics.hint"),
                new FxTutorial.Step(FxTab.MARKET, this.intelArea(layout), "fxeasy.tutorial.intel.title",
                        "fxeasy.tutorial.intel.body", "fxeasy.tutorial.intel.hint"));
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        this.measure();
    }

    @Override
    public void removed() {
        FxActions.unsubscribe();
        super.removed();
        FxEasy.LOGGER.info("FX terminal closed, panel bounds {}", FxUi.report());
    }

    private int design(double logical) {
        return Math.round((float) (logical / this.scale));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /// Keeps the dark in-world backdrop of the vanilla transparent background and skips the menu blur.
    @Override
    public boolean isInGameUi() {
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        FxUi.elements = 0;
        FxUi.frame(this.designWidth, this.designHeight);
        this.samplePointer(mouseX, mouseY);
        FxTerminalLayout layout = this.layout();
        if (!this.layoutLogged) {
            this.layoutLogged = true;
            FxControlRow.Geometry controls = this.controlGeometry(layout);
            ScreenRectangle tabs = FxControlRow.cell(controls, 0);
            ScreenRectangle intel = this.intelArea(layout);
            FxEasy.LOGGER.info("FX terminal layout: page {}, design {}x{}, list {}x{} at {},{}, chart {}x{} at {},{}, side {}x{} at {}, tab {}x{} at {}, intel {}x{} at {}",
                    this.tab, this.designWidth, this.designHeight, layout.list().width(), layout.list().height(),
                    layout.list().left(), layout.list().top(), layout.plot().width(), layout.plot().height(),
                    layout.plot().left(), layout.plot().top(), layout.side().width(), layout.side().height(), layout.side().left(),
                    tabs.width(), tabs.height(), tabs.left(), intel.width(), intel.height(), intel.left());
        }
        int revealed = this.revealed();
        graphics.pose().pushMatrix();
        graphics.pose().scale(this.scale, this.scale);
        if (revealed < this.designHeight) {
            FxUi.pushClip(graphics, FxUi.rect(0, 0, this.designWidth, revealed));
        }
        FxHeader.draw(graphics, this.font, this.state, layout.header());
        this.controls(graphics, layout);
        if (this.tab == FxTab.MARKET) {
            CurrencyPair picked = this.market.draw(graphics, this.font, this.state, this.input, layout.list());
            if (picked != null) {
                this.open(picked);
            }
        } else if (this.tab == FxTab.SHOP) {
            switch (this.shop.draw(graphics, this.font, this.state, this.input, layout.list())) {
                case FxShop.Request.Lounge _ -> this.overlays.lounge().open();
                case FxShop.Request.Checkout checkout ->
                    this.overlays.checkout().open(checkout.plan(), this.state);
                case FxShop.Request.None _ -> {
                }
            }
        } else {
            this.chart.draw(graphics, this.font, this.state, layout.plot(), layout.priceAxis(), layout.timeAxis(),
                    this.input.mouseX, this.input.mouseY);
            this.side(graphics, layout);
        }
        if (revealed < this.designHeight) {
            FxUi.popClip(graphics);
            this.scanLine(graphics, revealed);
        }
        FxTab forced = this.overlays.draw(graphics, this.font, this.state, !this.revealing, this.designWidth,
                this.designHeight);
        if (forced != null) {
            this.tab = forced;
        }
        FxNotice.draw(graphics, this.font, this.state, this.designWidth, this.designHeight);
        graphics.pose().popMatrix();
        if (this.input.pressed && !this.input.focusClaimed) {
            this.input.focus = null;
        }
        this.overlays.dropUnclaimedFocus();
        graphics.nextStratum();
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (++this.frame % 60 == 0) {
            FxEasy.LOGGER.info("FX terminal frame {}: {} elements", this.frame, FxUi.elements);
        }
        if (this.testRelease) {
            this.testRelease = false;
            this.input.down = false;
            this.input.endDrag();
            this.overlays.release();
        }
        this.input.clear();
        this.overlays.clearInputs();
    }

    /// Takes the pointer of the frame from the cursor position the renderer sampled, for the panel and for the
    /// walkthrough that takes the mouse over while it is up. An input that just received a press or a release
    /// keeps the position of that event instead: on a real client that is the same point the cursor is at, and it
    /// is the only position input that did not come from the cursor can be aimed with.
    private void samplePointer(int mouseX, int mouseY) {
        if (!this.input.pressed && !this.input.released) {
            this.input.mouseX = this.design(mouseX);
            this.input.mouseY = this.design(mouseY);
        }
        this.overlays.samplePointer(this.input.mouseX, this.input.mouseY);
    }

    /// Height of the panel the opening scan has revealed so far, in design units: the frame starts empty and the
    /// panel is drawn from the top down while the line travels, accelerating out of the header.
    private int revealed() {
        if (!this.revealing) {
            return this.designHeight;
        }
        long elapsed = Util.getMillis() - this.openedAt;
        if (elapsed >= REVEAL_MILLIS) {
            this.revealing = false;
            return this.designHeight;
        }
        float progress = elapsed / (float) REVEAL_MILLIS;
        return Math.round(this.designHeight * progress * progress);
    }

    /// The travelling line itself: a solid core with the accent fading out behind it.
    private void scanLine(GuiGraphicsExtractor graphics, int y) {
        FxUi.gradient(graphics, 0, Math.max(0, y - SCAN_TRAIL), this.designWidth, y,
                ARGB.multiplyAlpha(FxTheme.ACCENT, 0.0F), ARGB.multiplyAlpha(FxTheme.ACCENT, 0.55F));
        FxUi.fill(graphics, 0, y, this.designWidth, Math.min(this.designHeight, y + SCAN_LINE), FxTheme.ACCENT);
    }

    /// Clicks a cell of the frame as if the player had, which is what an end to end run drives the panel with: no
    /// harness command can click, because every command needs the client thread a screen blocks. The target names
    /// a cell instead of a pixel, and the point is the centre of the rectangle this frame draws for it, so the run
    /// does not have to know the layout of the window it is running in. Returns whether the cell was on screen.
    /// The press is released at the end of the frame that handles it, which is what a real click looks like to a
    /// control: a slider is dragged by the press alone, and a button that were left held would keep dragging.
    public boolean testClick(String target) {
        ScreenRectangle area = this.clicks().area(target);
        if (area == null) {
            FxEasy.LOGGER.warn("FX terminal test click on {} found no cell", target);
            return false;
        }
        FxInput input = this.pointer();
        input.mouseX = area.left() + area.width() / 2;
        input.mouseY = area.top() + area.height() / 2;
        input.down = true;
        input.pressed = true;
        this.testRelease = true;
        FxEasy.LOGGER.info("FX terminal test click on {} at {},{}", target, input.mouseX, input.mouseY);
        return true;
    }

    /// Types into the box that holds the focus, which is what a scripted run has instead of a keyboard: the run
    /// has no way to send characters through the window, so the events go through the same handler a real key
    /// takes, and the panel cannot tell the two apart.
    public boolean testType(String text) {
        if (!(this.pointer().focus instanceof FxField)) {
            FxEasy.LOGGER.warn("FX terminal test type {} found no focused box", text);
            return false;
        }
        for (int index = 0; index < text.length(); index++) {
            this.charTyped(new CharacterEvent(text.charAt(index)));
        }
        FxEasy.LOGGER.info("FX terminal test type {}", text);
        return true;
    }

    /// The step of the account opening the frame is on, or -1 while the opening is not up. A run reads it to know
    /// which card it is looking at, because two steps carry a button with the same name.
    public int testOpeningStep() {
        return this.overlays.opening().active() ? this.overlays.opening().step() : -1;
    }

    /// The step of the first run walkthrough the frame is on, or -1 while it is not up.
    public int testTutorialStep() {
        return this.overlays.tutorial().active() ? this.overlays.tutorial().step() : -1;
    }

    /// The currency the checkout of the shop page would settle a payment in, or null while the checkout is not
    /// up or the account holds nothing to pay with. A run reads it to check that a click on a row of the checkout
    /// leaves the payment where it was.
    public Currency testCheckoutPaying() {
        return this.overlays.checkout().active() ? this.overlays.checkout().paying() : null;
    }

    /// Whether the frame the panel is drawing shows the cell a scripted click names, which is what the regression
    /// probe asserts a page or an overlay by, without reaching into the state behind the panel.
    public boolean hasCell(String target) {
        return this.clicks().area(target) != null;
    }

    /// The target names of the frame, resolved against the layout this frame was measured with.
    private FxScriptedClicks clicks() {
        return new FxScriptedClicks(this.tab, this.state, this.layout(), this.designWidth, this.designHeight,
                this.market, this.positions, this.account, this.overlays, this.input);
    }

    /// The pointer state the frame is driven by: while an overlay is up it takes the mouse over, so a click can
    /// never reach a panel control behind it.
    private FxInput pointer() {
        return this.overlays.pointer(this.state, this.input);
    }

    /// Where the parts of this frame stand in design units. The price axis is as wide as the widest price the
    /// terminal prints, which is the room the chart column gives up for it.
    private FxTerminalLayout layout() {
        int axisWidth = Math.max(40, this.font.width(FxText.literal(AXIS_LABEL)) + 6);
        return FxTerminalLayout.of(this.designWidth, this.designHeight, axisWidth);
    }

    private FxControlRow.Geometry controlGeometry(FxTerminalLayout layout) {
        return FxControlRow.geometry(layout.controls(), this.tab.chart());
    }

    /// The intel entry, which is the control row cell after the last page tab.
    private ScreenRectangle intelArea(FxTerminalLayout layout) {
        return FxControlRow.cell(this.controlGeometry(layout), FxTab.values().length);
    }

    private void controls(GuiGraphicsExtractor graphics, FxTerminalLayout layout) {
        FxControlRow.Result picked = FxControlRow.draw(graphics, this.font, this.state, this.input, this.tab,
                this.controlGeometry(layout));
        this.tab = picked.tab();
        if (picked.intelOpened()) {
            this.input.focus = null;
            this.overlays.intel().open();
        }
    }

    /// Picking a pair in the market list also opens its chart, which is the page that carries the series and
    /// the ticket for it.
    private void open(CurrencyPair pair) {
        if (!this.state.selectedPair().equals(pair)) {
            this.state.selectPair(pair);
            this.chart.followLatest();
            FxActions.subscribe(pair, this.state.selectedTimeframe());
        }
        this.tab = FxTab.TRADE;
    }

    /// The side column: the page on screen, then the metrics strip every page keeps in view. A page that reports
    /// a confirmation request - the operations that cannot be taken back - hands it to the overlay stack, which
    /// opens the card in front of the panel.
    private void side(GuiGraphicsExtractor graphics, FxTerminalLayout layout) {
        FxUi.card(graphics, layout.side());
        ScreenRectangle inner = layout.sideInner();
        FxConfirm.Request request = switch (this.tab) {
            case TRADE -> {
                FxTicket.draw(graphics, this.font, this.state, this.input, inner);
                yield null;
            }
            case POSITIONS -> this.positions.draw(graphics, this.font, this.state, this.input, inner);
            case ACCOUNT -> this.account.draw(graphics, this.font, this.state, this.input, inner);
            case MARKET, SHOP -> null;
        };
        if (request != null) {
            this.overlays.confirm().open(request);
        }
        FxMetrics.draw(graphics, this.font, this.state, layout.sideInfoArea());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.revealing) {
            this.revealing = false;
            return true;
        }
        FxInput input = this.pointer();
        input.mouseX = this.design(event.x());
        input.mouseY = this.design(event.y());
        input.down = true;
        if (event.button() != 0) {
            return true;
        }
        input.pressed = true;
        if (this.overlays.active(this.state)) {
            return true;
        }
        FxTerminalLayout layout = this.layout();
        if (this.tab.chart() && FxUi.hit(layout.plot(), input.mouseX, input.mouseY)) {
            this.chart.beginDrag(input.mouseX, this.state.chartCandles().size(), layout.plot().width());
            input.beginDrag(DRAG_CHART);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        FxInput input = this.pointer();
        input.down = false;
        input.released = true;
        this.chart.endDrag();
        input.endDrag();
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        FxInput input = this.pointer();
        input.mouseX = this.design(event.x());
        input.mouseY = this.design(event.y());
        if (input.dragging(DRAG_CHART)) {
            this.chart.dragTo(input.mouseX, this.state.chartCandles().size(), this.layout().plot().width());
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (this.overlays.active(this.state)) {
            FxInput overlay = this.pointer();
            overlay.mouseX = this.design(x);
            overlay.mouseY = this.design(y);
            overlay.scroll = scrollY;
            return true;
        }
        this.input.mouseX = this.design(x);
        this.input.mouseY = this.design(y);
        ScreenRectangle plot = this.layout().plot();
        if (this.tab.chart() && FxUi.hit(plot, this.input.mouseX, this.input.mouseY)) {
            this.chart.zoom(scrollY > 0.0D ? 1 : -1, this.state.chartCandles().size(), plot.width());
            return true;
        }
        this.input.scroll = scrollY;
        return true;
    }

    /// The keyboard belongs to the overlay the pointer belongs to, in the same order: the opening owns it first,
    /// because the platform pays the welcome capital - and raises the card that advertises it - before the
    /// registration is filed, so the card is already up behind the registration and must not swallow the keys
    /// meant for its boxes.
    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean escape = event.key() == InputConstants.KEY_ESCAPE;
        return switch (this.overlays.owner(this.state)) {
            case OPENING -> this.fieldKey(event) || super.keyPressed(event);
            case TUTORIAL -> super.keyPressed(event);
            case CONFIRM -> {
                if (escape) {
                    this.overlays.confirm().close();
                }
                yield true;
            }
            case INTEL -> {
                if (escape) {
                    this.overlays.intel().close();
                }
                yield true;
            }
            case BONUS -> {
                if (escape) {
                    this.overlays.bonus().dismiss();
                }
                yield true;
            }
            case LOUNGE -> {
                if (escape) {
                    this.overlays.lounge().dismiss();
                }
                yield true;
            }
            case CHECKOUT -> {
                if (escape) {
                    this.overlays.checkout().dismiss();
                }
                yield true;
            }
            case PANEL -> this.fieldKey(event) || super.keyPressed(event);
        };
    }

    /// The keyboard of a text field: backspace edits what it holds and Enter or Escape ends the editing. Returns
    /// whether a box took the key, which is what lets the key fall through when there is none or it is empty.
    private boolean fieldKey(KeyEvent event) {
        if (this.pointer().focus instanceof FxField field) {
            switch (event.key()) {
                case InputConstants.KEY_BACKSPACE -> {
                    if (field.backspace()) {
                        return true;
                    }
                }
                case InputConstants.KEY_RETURN, InputConstants.KEY_ESCAPE -> {
                    this.pointer().focus = null;
                    return true;
                }
                default -> {
                }
            }
        }
        return false;
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        return super.keyReleased(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return switch (this.overlays.owner(this.state)) {
            case OPENING -> this.fieldType(event);
            case PANEL -> this.fieldType(event) || super.charTyped(event);
            default -> true;
        };
    }

    /// Appends the characters of a key to the box that holds the focus, which is the only way text gets into one.
    /// Returns whether a box took them, so the screen can let the event fall through when there is none.
    private boolean fieldType(CharacterEvent event) {
        if (this.pointer().focus instanceof FxField field) {
            field.type(event.codepointAsString());
            return true;
        }
        return false;
    }
}
