package com.pppopipupu.fxeasy.client.panel;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import com.pppopipupu.fxeasy.client.FxClientState;
import com.pppopipupu.fxeasy.client.FxText;
import com.pppopipupu.fxeasy.economy.EconomySourceKind;
import com.pppopipupu.fxeasy.event.MarketEventDefinition.ImpactChannel;
import com.pppopipupu.fxeasy.event.MarketEventKind;
import com.pppopipupu.fxeasy.event.MarketEventQuality;
import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;
import com.pppopipupu.fxeasy.market.Timeframe;
import com.pppopipupu.fxeasy.net.FxEconomyPayload;
import com.pppopipupu.fxeasy.net.FxEventsPayload;
import com.pppopipupu.fxeasy.net.FxEventsPayload.Event;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

/// The world intel surface: a scrollable overlay over the terminal with the economy report, the public message
/// feed and the model's own attribution, switched by a row of cells inside the overlay. It owns its own
/// [FxInput], which the screen fills while the overlay is up, so a click can never reach a ticket behind it, the
/// card and the cell geometry, the scroll offset and the line buffer the three view builders fill. The body is
/// clipped in design units and long text is wrapped to the body width rather than truncated.
public final class FxWorldIntel {

    private static final int COVER = ARGB.color(200, 4, 6, 9);

    private final FxInput input = new FxInput();
    private final List<Line> lines = new ArrayList<>();
    private View view = View.ECONOMY;
    private boolean active;
    private boolean layoutLogged;
    private int scroll;
    private int contentHeight;
    private View builtView;
    private FxEconomyPayload builtEconomy;
    private FxEventsPayload builtEvents;
    private Timeframe builtTimeframe;
    private int builtWidth;

    public boolean active() {
        return this.active;
    }

    public FxInput input() {
        return this.input;
    }

    public void open() {
        this.active = true;
        this.scroll = 0;
        FxEasy.LOGGER.info("FX terminal intel opened");
    }

    public void close() {
        this.active = false;
        this.input.clear();
    }

    /// Which way a message is in its life. The payload publishes only when it was announced and when it ends, so
    /// the release a message runs through is the last quarter of that window and everything before it is active.
    public static Phase phase(long announcedTick, long endTick, long asOfMarketTick, boolean archived) {
        if (archived) {
            return Phase.ARCHIVED;
        }
        if (asOfMarketTick < announcedTick) {
            return Phase.ANNOUNCED;
        }
        if (asOfMarketTick >= endTick) {
            return Phase.ARCHIVED;
        }
        return asOfMarketTick >= endTick - (endTick - announcedTick) / 4L ? Phase.RESOLVING : Phase.ACTIVE;
    }

    /// One card per root event, newest announcement first: a repeat update of a known root replaces the card it
    /// already had instead of stacking a second one, and the list keeps its order while the reader scrolls.
    public static List<Event> cards(List<Event> events) {
        Map<String, Event> newest = new HashMap<>();
        for (Event event : events) {
            String root = event.rootId().isEmpty() ? event.eventId() : event.rootId();
            Event known = newest.get(root);
            if (known == null || event.revision() > known.revision()) {
                newest.put(root, event);
            }
        }
        List<Event> cards = new ArrayList<>(newest.values());
        cards.sort(Comparator.comparingLong(Event::announcedTick).reversed().thenComparing(Event::rootId));
        return cards;
    }

    /// Which way the short term flows point: positive while the covered sources put more into the world than it
    /// reabsorbs, negative while absorption wins and zero while the two sides are within a twentieth of the
    /// capacity of the currency.
    public static int flowDirection(double supply, double demand, double capacity) {
        double net = supply - demand;
        double threshold = Math.max(0.01, capacity * 0.05);
        if (net > threshold) {
            return 1;
        }
        return net < -threshold ? -1 : 0;
    }

    /// Band of the revaluation the market has already priced, as a share of the bounded target the model walks
    /// towards. The report publishes the pressure level rather than the exact remaining target, and the model
    /// applies its target at a rate capped per bucket, so a stronger pressure leaves a larger part of the
    /// intended revaluation still unpriced.
    public static String pricedKey(int pressure) {
        return switch (pressure) {
            case 0 -> "fxeasy.intel.priced.low";
            case 1 -> "fxeasy.intel.priced.normal";
            case 2 -> "fxeasy.intel.priced.high";
            default -> "fxeasy.intel.priced.severe";
        };
    }

    public static String pressureKey(int pressure) {
        return switch (pressure) {
            case 0 -> "fxeasy.intel.pressure.low";
            case 1 -> "fxeasy.intel.pressure.normal";
            case 2 -> "fxeasy.intel.pressure.high";
            case 3 -> "fxeasy.intel.pressure.surplus";
            default -> "fxeasy.intel.pressure.severe";
        };
    }

    public static String flowKey(int direction) {
        if (direction > 0) {
            return "fxeasy.intel.flow.building";
        }
        return direction < 0 ? "fxeasy.intel.flow.draining" : "fxeasy.intel.flow.balanced";
    }

    public void draw(GuiGraphicsExtractor graphics, Font font, FxClientState state, int designWidth, int designHeight) {
        ScreenRectangle card = this.card(designWidth, designHeight);
        if (!this.layoutLogged) {
            this.layoutLogged = true;
            ScreenRectangle cells = cellsRow(card);
            ScreenRectangle close = this.closeArea(designWidth, designHeight);
            FxEasy.LOGGER.info("FX intel layout: card {}x{} at {},{}, cells {}x{} at {},{}, close {}x{} at {}",
                    card.width(), card.height(), card.left(), card.top(), cells.width(), cells.height(), cells.left(),
                    cells.top(), close.width(), close.height(), close.left());
        }
        FxUi.fill(graphics, 0, 0, designWidth, designHeight, COVER);
        FxUi.fill(graphics, card.left(), card.top(), card.right(), card.bottom(), FxTheme.CARD);
        FxUi.border(graphics, card, ARGB.multiplyAlpha(FxTheme.ACCENT, 0.8F));
        FxUi.text(graphics, font, card.left() + 3, card.top() + 3, FxText.get("fxeasy.intel.title"), FxTheme.ACCENT);
        ScreenRectangle close = this.closeArea(designWidth, designHeight);
        FxControls.button(graphics, font, close, FxText.get("fxeasy.intel.close"), FxTheme.BUTTON, FxTheme.TEXT,
                this.input.hovered(close), this.input.pressed && this.input.hovered(close), true);
        ScreenRectangle cells = this.cells(graphics, font, card);
        ScreenRectangle body = FxUi.rect(card.left() + 3, cells.bottom() + 2, Math.max(0, card.width() - 6 - 3 - 2),
                Math.max(0, card.bottom() - 3 - cells.bottom() - 2));
        this.body(graphics, font, state, body);
        if (this.view == View.EVENTS) {
            state.markEventsRead();
        }
        if (this.input.clicked(close)) {
            this.close();
        } else if (this.input.pressed && !FxUi.hit(card, this.input.mouseX, this.input.mouseY)) {
            this.close();
        }
    }

    private ScreenRectangle cells(GuiGraphicsExtractor graphics, Font font, ScreenRectangle card) {
        for (View candidate : View.values()) {
            ScreenRectangle box = viewArea(candidate, card);
            FxControls.choice(graphics, font, box, FxText.get(viewKey(candidate)), candidate == this.view, this.input.hovered(box), FxTheme.ACCENT);
            if (this.input.clicked(box)) {
                this.view = candidate;
                this.scroll = 0;
            }
        }
        return cellsRow(card);
    }

    /// The cell of one view. Both the draw and a scripted click read it, so the run aims at the cell the player
    /// sees.
    public ScreenRectangle viewArea(View view, int designWidth, int designHeight) {
        return viewArea(view, this.card(designWidth, designHeight));
    }

    /// The close button of the card, which is the cell a scripted click uses to put the overlay away.
    public ScreenRectangle closeArea(int designWidth, int designHeight) {
        ScreenRectangle card = this.card(designWidth, designHeight);
        return FxUi.rect(card.right() - 3 - 40, card.top() + 3 - 1, 40, 12);
    }

    /// The cell of one view. Both the draw and a scripted click read it, so the run aims at the cell the player
    /// sees.
    private static ScreenRectangle viewArea(View view, ScreenRectangle card) {
        int width = Math.clamp(((card.width() - 6) / (View.values().length + 1)), 30, 72);
        return FxUi.rect(card.left() + 3 + view.ordinal() * (width + 1), card.top() + 3 + 12 + 2, width, 14);
    }

    private static ScreenRectangle cellsRow(ScreenRectangle card) {
        int width = Math.clamp(((card.width() - 6) / (View.values().length + 1)), 30, 72);
        return FxUi.rect(card.left() + 3, card.top() + 3 + 12 + 2, width * View.values().length, 14);
    }

    private void body(GuiGraphicsExtractor graphics, Font font, FxClientState state, ScreenRectangle area) {
        this.rebuild(state, font, area.width());
        int travel = Math.max(0, this.contentHeight - area.height());
        if (this.input.scroll != 0.0) {
            this.scroll = this.scroll - (int) Math.signum(this.input.scroll) * 24;
            this.input.scroll = 0.0;
        }
        this.scroll = Math.clamp(this.scroll, 0, travel);
        FxUi.pushClip(graphics, area);
        int y = area.top() - this.scroll;
        for (Line line : this.lines) {
            if (line.text() != null && y + 10 > area.top() && y < area.bottom()) {
                FxUi.clipped(graphics, font, area, area.left() + line.indent(), y, line.text(), line.colour());
            }
            y += 10;
        }
        FxUi.popClip(graphics);
        if (travel > 0) {
            FxControls.scrollbar(graphics, FxUi.rect(area.right() + 2, area.top(), 3, area.height()),
                    (double) this.scroll / travel, (double) area.height() / this.contentHeight,
                    this.input.hovered(area));
        }
    }

    /// Rebuilds the visible view when the report, the feed, the window or the selection behind it changed. The
    /// mirror replaces a payload with a newer one instead of editing it, so identity is the whole test.
    private void rebuild(FxClientState state, Font font, int width) {
        if (this.builtView != this.view
            || this.builtEconomy != state.economy()
            || this.builtEvents != state.events()
            || this.builtTimeframe != state.selectedTimeframe()
            || this.builtWidth != width) {
            this.builtView = this.view;
            this.builtEconomy = state.economy();
            this.builtEvents = state.events();
            this.builtTimeframe = state.selectedTimeframe();
            this.builtWidth = width;
            this.lines.clear();
            switch (this.view) {
                case ECONOMY:
                    this.economy(state, width);
                    break;
                case EVENTS:
                    this.events(state, font, width);
                    break;
                case ATTRIBUTION:
                    this.attribution(state);
            }
            this.contentHeight = this.lines.size() * 10;
        }
    }

    private void economy(FxClientState state, int width) {
        FxEconomyPayload report = state.economy();
        this.heading("fxeasy.intel.view.economy");
        if (report == null) {
            this.add("fxeasy.intel.economy.empty", FxTheme.MUTED, 0);
        } else {
            this.add(FxText.join(FxText.get("fxeasy.intel.economy.report"), FxText.get(statusKey(report.status()))), statusColour(report.status()), 0);
            this.add(FxText.join(FxText.get("fxeasy.intel.economy.revision"), FxText.literal(Long.toString(report.revision()))), FxTheme.TEXT, 0);
            this.add(FxText.join(FxText.get("fxeasy.intel.economy.bucket"), FxText.literal(FxUi.marketTime(report.marketTick()))), FxTheme.TEXT, 0);
            this.add(FxText.join(FxText.get("fxeasy.intel.economy.day"), FxText.literal(Long.toString(report.rolloverDay()))), FxTheme.TEXT, 0);
            this.add(
                    FxText.join( FxText.get("fxeasy.intel.economy.players"), FxText.literal(days(report.playerDayTenths())), FxText.get("fxeasy.intel.economy.playerDays") ),
                    FxTheme.TEXT, 0);
            this.add(FxText.get("fxeasy.intel.economy.legend"), FxTheme.MUTED, 0);
            this.spacer();
            this.heading("fxeasy.intel.economy.coverage");
            this.coverage(report);
            this.spacer();
            this.heading("fxeasy.intel.economy.sources");
            this.sources(report);
            this.spacer();
            for (Currency currency : Currencies.all()) {
                this.currency(state, report, currency);
                this.spacer();
            }
        }
    }

    private void coverage(FxEconomyPayload report) {
        int[] coverage = report.coverage();
        if (coverage[0] > 0) {
            this.add(FxText.get("fxeasy.intel.coverage.overBudget", Integer.toString(coverage[0])), FxTheme.MUTED, 8);
        }
        if (coverage[1] > 0) {
            this.add(FxText.get("fxeasy.intel.coverage.dimensions", Integer.toString(coverage[1])), FxTheme.MUTED, 8);
        }
        if (coverage[2] > 0) {
            this.add(FxText.get("fxeasy.intel.coverage.disabled", Integer.toString(coverage[2])), FxTheme.MUTED, 8);
        }
        if (coverage[3] != 0) {
            this.add("fxeasy.intel.coverage.migrated", FxTheme.DOWN, 8);
        }
        this.add("fxeasy.intel.coverage.containers", FxTheme.MUTED, 8);
        this.add("fxeasy.intel.coverage.unloaded", FxTheme.MUTED, 8);
        this.add("fxeasy.intel.coverage.supported", FxTheme.MUTED, 8);
    }

    private void sources(FxEconomyPayload report) {
        int[] ordinals = report.supportedSources();
        long[] atoms = report.supportedAtoms();
        if (ordinals.length == 0) {
            this.add("fxeasy.intel.economy.noSources", FxTheme.MUTED, 8);
        } else {
            for (int index = 0; index < ordinals.length; index++) {
                if (ordinals[index] >= 0 && ordinals[index] < EconomySourceKind.COUNT) {
                    this.add(
                            FxText.join( FxText.get("fxeasy.intel.source." + ((EconomySourceKind)EconomySourceKind.ALL.get(ordinals[index])).id()), FxText.literal(Long.toString(atoms[index])) ),
                            FxTheme.MUTED, 8);
                }
            }
        }
    }

    private void currency(FxClientState state, FxEconomyPayload report, Currency currency) {
        int index = Currencies.index(currency);
        double supply = report.supplyFlow()[index];
        double demand = report.demandFlow()[index];
        double capacity = report.capacity()[index];
        this.add(
                FxText.join( FxText.literal(currency.code()), FxText.literal(FxUi.price(state.rate(currency, state.basis()))), FxText.literal(FxUi.percent(state.valuationChange(currency, state.selectedTimeframe()))) ),
                FxTheme.ACCENT, 0);
        if (supply > 0.0) {
            this.add(
                    FxText.join(FxText.get("fxeasy.intel.economy.production"), FxText.literal(decimal(supply, 2)), FxText.get("fxeasy.intel.economy.perDay")),
                    FxTheme.TEXT, 8);
        } else {
            this.add("fxeasy.intel.economy.noProduction", FxTheme.MUTED, 8);
        }
        this.add(
                FxText.join(FxText.get("fxeasy.intel.economy.terminalUse"), FxText.literal(decimal(demand, 2)), FxText.get("fxeasy.intel.economy.perDay")),
                FxTheme.TEXT, 8);
        this.add("fxeasy.intel.economy.discount", FxTheme.MUTED, 16);
        this.add(FxText.join(FxText.get("fxeasy.intel.economy.pressure"), FxText.get(pressureKey(report.pressure()[index]))), FxTheme.TEXT, 8);
        this.add(FxText.join(FxText.get("fxeasy.intel.economy.flows"), FxText.get(flowKey(flowDirection(supply, demand, capacity)))), FxTheme.TEXT, 8);
        this.add(FxText.join(FxText.get("fxeasy.intel.economy.priced"), FxText.get(pricedKey(report.pressure()[index]))), FxTheme.TEXT, 8);
        this.add(
                FxText.join(FxText.get("fxeasy.intel.economy.capacity"), FxText.literal(decimal(capacity, 1)), FxText.get("fxeasy.intel.economy.perDay")),
                FxTheme.MUTED, 8);
    }

    private void events(FxClientState state, Font font, int width) {
        FxEventsPayload feed = state.events();
        this.heading("fxeasy.intel.view.events");
        if (feed != null && !feed.events().isEmpty()) {
            this.add(FxText.join(FxText.get("fxeasy.intel.events.tick"), FxText.literal(FxUi.marketTime(feed.asOfMarketTick()))), FxTheme.MUTED, 0);
            this.add(FxText.join(FxText.get("fxeasy.intel.events.unread"), FxText.literal(Integer.toString(state.unreadEvents()))), FxTheme.MUTED, 0);
            this.spacer();
            for (Event event : cards(feed.events())) {
                this.add(FxText.get(event.titleKey()), FxTheme.ACCENT, 0);
                this.add(FxText.join(FxText.get("fxeasy.intel.events.quality"), FxText.get(qualityKey(event.quality()))), qualityColour(event.quality()), 8);
                this.add(
                        FxText.join( FxText.get("fxeasy.intel.events.channel"), FxText.get(channelKey(event.channel())), FxText.get("fxeasy.intel.events.kind"), FxText.get(kindKey(event.kind())) ),
                        FxTheme.TEXT, 8);
                this.add(
                        FxText.join( FxText.get("fxeasy.intel.events.phase"), FxText.get(phaseKey(phase(event.announcedTick(), event.endTick(), feed.asOfMarketTick(), event.archived()))), FxText.get("fxeasy.intel.events.archived"), FxText.get(event.archived() ? "fxeasy.intel.events.yes" : "fxeasy.intel.events.no") ),
                        FxTheme.TEXT, 8);
                this.add(
                        FxText.join( FxText.get("fxeasy.intel.events.announced"), FxText.literal(FxUi.marketTime(event.announcedTick())), FxText.get("fxeasy.intel.events.currencies"), currencies(event) ),
                        FxTheme.TEXT, 8);
                this.applied(event);
                for (String part : FxUi.wrap(FxText.get(event.bodyKey()).getString(), width - 16, text -> measure(font, text))) {
                    this.add(FxText.literal(part), FxTheme.MUTED, 16);
                }
                this.spacer();
            }
        } else {
            this.add("fxeasy.intel.events.empty", FxTheme.MUTED, 0);
        }
    }

    private void applied(Event event) {
        boolean any = false;
        for (Currency currency : Currencies.all()) {
            int index = Currencies.index(currency);
            if (concerns(event, index)) {
                any = true;
                double peak = event.peak()[index];
                String share = peak == 0.0 ? "-" : String.format(Locale.ROOT, "%.0f%%", event.applied()[index] / peak * 100.0);
                this.add(
                        FxText.join( FxText.get("fxeasy.intel.events.applied"), FxText.literal(currency.code()), FxText.literal(decimal(event.applied()[index], 4)), FxText.literal("/"), FxText.literal(decimal(peak, 4)), FxText.literal(share) ),
                        FxTheme.TEXT, 8);
            }
        }
        if (!any) {
            this.add("fxeasy.intel.events.noEvidence", FxTheme.MUTED, 8);
        }
    }

    /// The event feed bounds neither per currency array, so every read of one goes through this instead of the
    /// accessor that indexes it blindly.
    private static boolean concerns(Event event, int ordinal) {
        return event.applied().length > ordinal && event.peak().length > ordinal && event.concerns(ordinal);
    }

    private void attribution(FxClientState state) {
        FxEconomyPayload report = state.economy();
        this.heading("fxeasy.intel.view.attribution");
        this.add("fxeasy.intel.attribution.note", FxTheme.MUTED, 0);
        if (report == null) {
            this.add("fxeasy.intel.attribution.empty", FxTheme.MUTED, 0);
        } else {
            this.add(FxText.join(FxText.get("fxeasy.intel.economy.revision"), FxText.literal(Long.toString(report.revision()))), FxTheme.MUTED, 0);
            this.spacer();
            for (Currency currency : Currencies.all()) {
                int index = Currencies.index(currency);
                double fundamental = report.appliedFundamental()[index];
                double event = report.appliedEvent()[index];
                this.add(FxText.literal(currency.code()), FxTheme.ACCENT, 0);
                this.add(FxText.join(FxText.get("fxeasy.intel.attribution.fundamental"), FxText.literal(decimal(fundamental, 4))), FxTheme.TEXT, 8);
                this.add(FxText.join(FxText.get("fxeasy.intel.attribution.event"), FxText.literal(decimal(event, 4))), FxTheme.TEXT, 8);
                this.add(FxText.join(FxText.get("fxeasy.intel.attribution.total"), FxText.literal(decimal(fundamental + event, 4))), FxTheme.TEXT, 8);
                this.spacer();
            }
        }
    }

    private ScreenRectangle card(int designWidth, int designHeight) {
        int width = Math.max(80, designWidth - 12);
        int height = Math.max(60, designHeight - 12);
        return FxUi.rect((designWidth - width) / 2, (designHeight - height) / 2, width, height);
    }

    private void heading(String key) {
        this.add(key, FxTheme.ACCENT, 0);
    }

    private void spacer() {
        this.lines.add(new Line(null, FxTheme.TEXT, 0));
    }

    private void add(String key, int colour, int indent) {
        this.add(FxText.get(key), colour, indent);
    }

    private void add(Component text, int colour, int indent) {
        this.lines.add(new Line(text, colour, indent));
    }

    private static Component currencies(Event event) {
        List<Component> codes = new ArrayList<>();
        for (Currency currency : Currencies.all()) {
            if (concerns(event, Currencies.index(currency))) {
                codes.add(FxText.literal(currency.code()));
            }
        }
        return codes.isEmpty() ? FxText.get("fxeasy.intel.events.none") : FxText.join(codes.toArray(Component[]::new));
    }

    private static String statusKey(int status) {
        return switch (status) {
            case 0 -> "fxeasy.intel.status.fresh";
            case 1 -> "fxeasy.intel.status.observing";
            case 2 -> "fxeasy.intel.status.stale";
            default -> "fxeasy.intel.status.disabled";
        };
    }

    private static int statusColour(int status) {
        return switch (status) {
            case 0 -> FxTheme.UP;
            case 1 -> FxTheme.ACCENT;
            case 2 -> FxTheme.DOWN;
            default -> FxTheme.MUTED;
        };
    }

    private static String viewKey(View view) {
        return switch (view) {
            case ECONOMY -> "fxeasy.intel.view.economy";
            case EVENTS -> "fxeasy.intel.view.events";
            case ATTRIBUTION -> "fxeasy.intel.view.attribution";
        };
    }

    private static String phaseKey(Phase phase) {
        return switch (phase) {
            case ANNOUNCED -> "fxeasy.intel.phase.announced";
            case ACTIVE -> "fxeasy.intel.phase.active";
            case RESOLVING -> "fxeasy.intel.phase.resolving";
            case ARCHIVED -> "fxeasy.intel.phase.archived";
        };
    }

    private static String qualityKey(int quality) {
        MarketEventQuality[] values = MarketEventQuality.values();
        return quality >= 0 && quality < values.length ? "fxeasy.intel.quality." + values[quality].id() : "fxeasy.intel.unknown";
    }

    private static int qualityColour(int quality) {
        MarketEventQuality[] values = MarketEventQuality.values();
        if (quality >= 0 && quality < values.length) {
            return switch (values[quality]) {
                case CONFIRMED -> FxTheme.UP;
                case ESTIMATED -> FxTheme.AXIS;
                case RUMOR -> FxTheme.ACCENT;
                case ENTERTAINMENT -> FxTheme.MUTED;
                default -> throw new MatchException(null, null);
            };
        } else {
            return FxTheme.MUTED;
        }
    }

    private static String channelKey(int channel) {
        ImpactChannel[] values = ImpactChannel.values();
        return channel >= 0 && channel < values.length ? "fxeasy.intel.channel." + values[channel].id() : "fxeasy.intel.unknown";
    }

    private static String kindKey(int kind) {
        MarketEventKind[] values = MarketEventKind.values();
        return kind >= 0 && kind < values.length ? "fxeasy.intel.kind." + values[kind].id() : "fxeasy.intel.unknown";
    }

    private static String decimal(double value, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    private static String days(int tenths) {
        return String.format(Locale.ROOT, "%.1f", tenths / 10.0);
    }

    private static int measure(Font font, String text) {
        return font.width(FxText.literal(text));
    }

    /// One drawn line: the text, its colour and how far it is indented under its heading. A line without text is
    /// a spacer that only takes vertical room.
    private record Line(Component text, int colour, int indent) {
    }

    public enum Phase {
        ANNOUNCED,
        ACTIVE,
        RESOLVING,
        ARCHIVED
    }

    public enum View {
        ECONOMY,
        EVENTS,
        ATTRIBUTION
    }
}
