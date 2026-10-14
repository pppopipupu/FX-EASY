package com.pppopipupu.fxeasy.client.panel;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import com.pppopipupu.fxeasy.client.FxActions;
import com.pppopipupu.fxeasy.client.FxClientState;
import com.pppopipupu.fxeasy.client.FxText;
import com.pppopipupu.fxeasy.content.FxCurrencies;
import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;
import com.pppopipupu.fxeasy.market.FeeDiscount;
import com.pppopipupu.fxeasy.market.MarketEngine;
import com.pppopipupu.fxeasy.shop.LoungeFile;
import com.pppopipupu.fxeasy.shop.Membership;
import com.pppopipupu.fxeasy.shop.ShopEngine;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;

/// The shop page: the tiers the platform sells side by side, each card carrying the picture of its own character
/// as a translucent backdrop with the whole of the tier written over it - the name and the state at its top, the
/// character it is sold with, the price of a period and the length of that period under them, then what the tier
/// buys line by line: the ceiling it puts on the leverage of an order, one line per cost the platform charges
/// with what the plan takes off that cost, the lounge of the top tier with the pace of its gifts and the pool
/// that lounge draws from, gift by gift with the share of the draws each of them takes - and the button that
/// opens the checkout of the tier at the foot of the card. Under the cards stands the bar that manages the
/// subscription the account is on - which plan it is, how long its period still runs, whether the platform renews
/// it by itself and the entry into the lounge of the top tier.
///
/// A card never buys anything itself: the button at its foot opens the checkout of that tier, where the price of
/// a period is quoted in whichever currency of the account the player picks and the platform decides whether the
/// payment goes through. What the page decides on its own is only whether a tier can be sold at all - a world
/// that does not trade the currency the plan is priced in cannot price it, and a tier below the one the account
/// is on can never be bought back down to - and the server enforces both of those as well. Nothing is applied
/// until the platform answers: the page reads the plan out of the account snapshot like every other page does.
public final class FxShop {

    /// The tiers of the page, each with the picture of the character it is sold with and the pixel size of that
    /// file, which is what the cover crop of its card is computed from.
    private static final List<Tier> TIERS = List.of(
            new Tier(Membership.GO, art("tier_go"), 305, 720),
            new Tier(Membership.PLUS, art("tier_plus"), 433, 742),
            new Tier(Membership.PRO, art("tier_pro"), 315, 723));

    private static final int PAD = 4;
    private static final int LINE = 9;
    private static final int TITLE = 12;
    private static final int NAME_ROW = 11;
    /// Height of the row that carries the price of a period, which is what the icon of the currency is drawn
    /// into as well.
    private static final int PRICE_ROW = LINE + 3;
    private static final int BUTTON = FxTheme.ROW + 1;
    private static final int ICON = 16;
    private static final int GAP = FxTheme.GAP + 2;
    /// How far a note under a line is drawn in from the left edge of the detail, so it reads as the line it
    /// explains rather than as a line of its own.
    private static final int NOTE_INDENT = 6;
    /// Width of the scrollbar of a pool too long for the room its card leaves, and the rows one wheel notch
    /// moves it by.
    private static final int SCROLLBAR = 3;
    private static final int SCROLL_STEP = 2;
    /// The alpha the picture of a card is blended at over the card colour it stands on: the higher, the more of
    /// the picture stands and the less transparent it reads, and the card carries a gradient at its foot so the
    /// wording stands on it either way. The effect over the picture is a layer of its own and does not follow it.
    private static final float OPACITY = 0.02F;
    /// Height of the gradient that carries the wording into the foot of a card, and the share of a card it may
    /// take at most, so a card too short to hold one is not swallowed by it.
    private static final int FADE = 74;
    private static final int FADE_SHARE = 3;
    /// Share of the card colour the gradient has reached a quarter of the way down from where it starts, which is
    /// where its two segments join. One linear ramp leaves the name of a card barely a third covered where it
    /// stands, and a name over the pale parts of an illustration cannot be read at that.
    private static final float FADE_KNEE = 0.48F;
    /// Shortest a card may be drawn, a band of its picture with the wording under it. A page too short to hold
    /// both a card of this height and the bar under it draws its cards at this height and leaves the rest out.
    private static final int CARD_MIN = 118;
    /// Width of a cell of the subscription bar: the renewal switch and the entry into the lounge.
    private static final int CELL = 84;

    private boolean logged;
    private int poolScroll;

    /// One tier of the page: the plan, the picture of its character and the pixel size of that file.
    private record Tier(Membership plan, Identifier art, int width, int height) {
    }

    /// The cells of one card, in the order they are stacked at its foot: [name] carries the plan and the state it
    /// is in, [lore] the character it is sold with, [price] what a period costs, [period] how long that period
    /// runs, [detail] everything the tier buys - which is where the pool of the lounge is scrolled, because it is
    /// the one part of a card that can run longer than the card - and [action] the button that buys it, which is
    /// also the cell a scripted click aims at. [card] is the picture of the tier as much as it is the card,
    /// because the backdrop is drawn over the whole of it.
    public record CardCells(Membership plan, ScreenRectangle card, ScreenRectangle name, ScreenRectangle lore,
            ScreenRectangle price, ScreenRectangle period, ScreenRectangle detail, ScreenRectangle action) {
    }

    /// The cells of the subscription bar under the cards: the switch that turns the renewal off and on again and
    /// the button that opens the lounge.
    public record FooterCells(ScreenRectangle renew, ScreenRectangle lounge) {
    }

    /// What a click on the page asked the screen to open in front of it: the checkout of a tier the player would
    /// pay for, the lounge of the tier the account holds, or nothing. The page never buys anything itself - the
    /// platform decides what a plan costs and in which currency it is settled, and the checkout is where the
    /// player chooses that currency.
    public sealed interface Request {

        Request NONE = new None();

        record None() implements Request {
        }

        record Checkout(Membership plan) implements Request {
        }

        record Lounge() implements Request {
        }
    }

    /// One line of the detail of a card. [label] stands against the left edge of the region the detail is drawn
    /// in and [value] against its right edge; a line without a value is the heading of a group, a line marked as a
    /// note says what the line above it is, and [colour] is the colour the label, or the value when it has one, is
    /// drawn in.
    record Line(Component label, Component value, int colour, boolean note) {

        static Line heading(String key) {
            return new Line(FxText.get(key), null, FxTheme.MUTED, false);
        }

        static Line note(String key) {
            return new Line(FxText.get(key), null, FxTheme.MUTED, true);
        }

        static Line of(String key, Component value, int colour) {
            return new Line(FxText.get(key), value, colour, false);
        }

        static Line gift(Component label, Component value) {
            return new Line(label, value, FxTheme.TEXT, false);
        }
    }

    /// The detail of one card: the lines the tier itself writes and how many entries the pool its lounge draws
    /// from has, which is what the card weighs the room it has against. The pool itself is written out entry by
    /// entry where the card draws it, because a gift is named by the stack the lounge would hand over.
    record Detail(List<Line> lines, int entries) {
    }

    /// Draws the page and applies what the player clicked. Returns what the click asked the screen to open in
    /// front of the page, which is the checkout of a tier or the lounge of the plan the account holds.
    public Request draw(GuiGraphicsExtractor graphics, Font font, FxClientState state, FxInput input,
            ScreenRectangle area) {
        List<CardCells> cards = cards(area);
        ScreenRectangle footer = footerArea(area);
        if (!this.logged) {
            this.logged = true;
            CardCells first = cards.getFirst();
            FxEasy.LOGGER.info("FX shop layout: area {}x{} at {},{}, card {}x{} at {},{}, detail {}x{} at {},{}, "
                    + "footer {}x{} at {},{}", area.width(), area.height(), area.left(), area.top(),
                    first.card().width(), first.card().height(), first.card().left(), first.card().top(),
                    first.detail().width(), first.detail().height(), first.detail().left(), first.detail().top(),
                    footer.width(), footer.height(), footer.left(), footer.top());
        }
        FxUi.text(graphics, font, area.left() + PAD, area.top() + PAD, FxText.get("fxeasy.shop.title"), FxTheme.TEXT);
        FxUi.right(graphics, font, area.right() - PAD, area.top() + PAD, FxText.get("fxeasy.shop.subtitle"),
                FxTheme.MUTED);
        for (CardCells cells : cards) {
            Request request = card(graphics, font, state, input, cells);
            if (request != Request.NONE) {
                return request;
            }
        }
        return footer(graphics, font, state, input, footer) ? new Request.Lounge() : Request.NONE;
    }

    private Request card(GuiGraphicsExtractor graphics, Font font, FxClientState state, FxInput input,
            CardCells cells) {
        Membership plan = cells.plan();
        Tier tier = tier(plan);
        ScreenRectangle card = cells.card();
        FxUi.card(graphics, card);
        FxShopCard.draw(graphics, tier.art(), card, tier.width(), tier.height(),
                Membership.plans().indexOf(plan), intensity(state, plan), OPACITY);
        int fade = Math.min(FADE, card.height() * FADE_SHARE / 5);
        int top = card.bottom() - fade;
        int knee = top + fade / 4;
        FxUi.gradient(graphics, card.left(), top, card.right(), knee,
                ARGB.multiplyAlpha(FxTheme.CARD, 0.0F), ARGB.multiplyAlpha(FxTheme.CARD, FADE_KNEE));
        FxUi.gradient(graphics, card.left(), knee, card.right(), card.bottom(),
                ARGB.multiplyAlpha(FxTheme.CARD, FADE_KNEE), ARGB.multiplyAlpha(FxTheme.CARD, 0.96F));
        boolean current = state.membership() == plan;
        boolean owned = plan.ordinal() < state.membership().ordinal();
        FxUi.border(graphics, card, ARGB.multiplyAlpha(current ? FxTheme.ACCENT : owned ? FxTheme.UP
                : FxTheme.CARD_BORDER, 0.85F));
        header(graphics, font, cells, plan, current, owned);
        detail(graphics, font, state, input, cells, plan);
        return button(graphics, font, input, cells, plan, current, owned);
    }

    /// The top of a card: the name of the tier with the state it is in, the character it is sold with, the price
    /// of one period next to the icon of the currency it is charged in and how long that period runs.
    private static void header(GuiGraphicsExtractor graphics, Font font, CardCells cells, Membership plan,
            boolean current, boolean owned) {
        ScreenRectangle card = cells.card();
        int x = cells.name().left();
        FxUi.clipped(graphics, font, card, x, cells.name().top(), FxText.get(plan.nameKey()),
                current ? FxTheme.ACCENT : FxTheme.TEXT);
        Component badge = owned ? FxText.get("fxeasy.shop.owned") : current ? FxText.get("fxeasy.shop.current") : null;
        if (badge != null) {
            FxUi.right(graphics, font, card.right() - PAD, cells.name().top(), badge,
                    current ? FxTheme.UP : FxTheme.MUTED);
        }
        FxUi.clipped(graphics, font, card, x, cells.lore().top(), FxText.get(plan.loreKey()), FxTheme.MUTED);
        FxUi.separator(graphics, x, card.right() - PAD, cells.lore().bottom() + 1);
        Currency price = priceCurrency(plan);
        if (price == null) {
            FxUi.text(graphics, font, x, cells.price().top(), FxText.get("fxeasy.shop.unavailable"), FxTheme.MUTED);
        } else {
            FxUi.item(graphics, FxCurrencies.stack(price, 1), x, cells.price().top() - 3, 1);
            FxUi.text(graphics, font, x + ICON + FxTheme.GAP, cells.price().top(),
                    FxText.literal("%s × %s", Long.toString(plan.priceItems()), price.code()), FxTheme.ACCENT);
        }
        FxUi.clipped(graphics, font, card, x, cells.period().top(),
                FxText.get("fxeasy.shop.benefit.period", days()), FxTheme.MUTED);
        FxUi.separator(graphics, x, card.right() - PAD, cells.period().bottom() + 1);
    }

    /// What the tier buys, written down the card under its price: the lines of the tier and, for the tier that
    /// carries a lounge, the entries of the pool that lounge draws from. The lines are spread over the room the
    /// card leaves between its price and its button when the whole of them fits there, and the pool is a list of
    /// its own, scrolled by the wheel while the mouse is over it, when it does not.
    private void detail(GuiGraphicsExtractor graphics, Font font, FxClientState state, FxInput input,
            CardCells cells, Membership plan) {
        ScreenRectangle area = cells.detail();
        if (area.height() <= 0) {
            return;
        }
        Detail content = detail(plan, state.loungePool(), area.height());
        List<Line> lines = content.lines();
        int count = lines.size() + content.entries();
        int pitch = count * LINE <= area.height() ? area.height() / Math.max(1, count) : LINE;
        int y = area.top();
        for (Line line : lines) {
            if (y + LINE > area.bottom()) {
                break;
            }
            row(graphics, font, area, y, line);
            y += pitch;
        }
        int room = Math.max(0, area.bottom() - y);
        if (content.entries() <= 0 || room < LINE) {
            return;
        }
        List<Line> pool = poolLines(plan, state.loungePool());
        int visible = room / LINE;
        int maximum = Math.max(0, pool.size() - visible);
        ScreenRectangle window = FxUi.rect(area.left(), y, Math.max(1, area.width() - (maximum > 0 ? SCROLLBAR : 0)),
                room);
        if (input.scroll != 0.0D && input.hovered(window)) {
            this.poolScroll -= (int) Math.signum(input.scroll) * SCROLL_STEP;
            input.scroll = 0.0D;
        }
        this.poolScroll = Math.clamp(this.poolScroll, 0, maximum);
        FxUi.pushClip(graphics, window);
        int rowY = window.top();
        for (int index = this.poolScroll; index < Math.min(pool.size(), this.poolScroll + visible); index++) {
            row(graphics, font, window, rowY, pool.get(index));
            rowY += pitch;
        }
        FxUi.popClip(graphics);
        if (maximum > 0) {
            FxControls.scrollbar(graphics, FxUi.rect(area.right() - SCROLLBAR, window.top(), SCROLLBAR,
                    window.height()), this.poolScroll / (double) maximum, visible / (double) pool.size(),
                    input.hovered(window));
        }
    }

    /// One line of the detail: the label against the left edge of [area] and the value against its right edge. A
    /// heading and a note carry no value and stand alone, and a note is drawn further in, so it reads as the line
    /// it explains. A label that would run into its value is cut where the value starts rather than drawn under
    /// it.
    private static void row(GuiGraphicsExtractor graphics, Font font, ScreenRectangle area, int y, Line line) {
        int x = area.left() + (line.note() ? NOTE_INDENT : 0);
        if (line.value() == null) {
            FxUi.clipped(graphics, font, area, x, y, line.label(), line.colour());
            return;
        }
        int valueWidth = font.width(line.value());
        FxUi.clipped(graphics, font, FxUi.rect(x, area.top(), Math.max(1, area.right() - valueWidth - 4 - x),
                area.height()), x, y, line.label(), FxTheme.MUTED);
        FxUi.right(graphics, font, area.right(), y, line.value(), line.colour());
    }

    /// The button at the foot of a card, which opens the checkout of the tier rather than buying anything: the
    /// price of a period is the platform's to quote and the currency it is settled in is the player's to choose,
    /// so the page hands the plan over and the checkout does the rest. The wording of the button is the refusal
    /// of the platform while there is one, which is the only thing the page can decide on its own.
    private static Request button(GuiGraphicsExtractor graphics, Font font, FxInput input, CardCells cells,
            Membership plan, boolean current, boolean owned) {
        FxUi.separator(graphics, cells.action().left(), cells.action().right(), cells.action().top() - 3);
        String refusal = refusal(owned, plan);
        boolean enabled = refusal == null;
        Component label = FxText.get(enabled ? current ? "fxeasy.shop.extend" : "fxeasy.shop.buy" : refusal);
        FxControls.button(graphics, font, cells.action(), label,
                ARGB.multiplyAlpha(current ? FxTheme.UP : FxTheme.ACCENT, 0.82F), ARGB.color(255, 12, 16, 20),
                input.hovered(cells.action()), input.pressed && input.hovered(cells.action()), enabled);
        return enabled && input.clicked(cells.action()) ? new Request.Checkout(plan) : Request.NONE;
    }

    /// How strongly the sheen of a card is drawn, which is what tells a card the player is paying for from one
    /// they only could: the plan the account is on sweeps strongest, a plan below it has been outgrown and
    /// barely moves, and an offer anywhere else is a middle ground. Which effect a card carries is decided by
    /// its tier alone, so the three cards stay each other's own.
    private static float intensity(FxClientState state, Membership plan) {
        if (state.membership() == plan) {
            return 1.0F;
        }
        return plan.ordinal() < state.membership().ordinal() ? 0.35F : 0.7F;
    }

    /// The detail of one tier as a card with [height] design units to spare writes it: what the tier buys and how
    /// many entries the pool of its lounge holds. The notes that say what each cost of the platform is are
    /// written only where the card has the room for them as well as for the whole pool, so a card that has to
    /// scroll its pool is not also carrying prose it cannot show.
    static Detail detail(Membership plan, LoungeFile.Pool pool, int height) {
        int entries = plan.lounge() && pool != null ? usable(pool) : 0;
        List<Line> noted = lines(plan, pool, true);
        List<Line> plain = lines(plan, pool, false);
        List<Line> fixed = (noted.size() + entries) * LINE <= height ? noted : plain;
        return new Detail(fixed, entries);
    }

    /// What one tier writes about itself, in the order a card stacks it: the ceiling it puts on the leverage of
    /// an order, one line per cost the platform charges with what the plan takes off it - and, where the card has
    /// the room, the note that says what that cost is - and the lounge the tier carries with the pace of its
    /// gifts and the pool it draws from. Every figure is read from the plan and the pool of the world, so a tier
    /// that is repriced or a pool that is rewritten cannot leave a card saying something else.
    static List<Line> lines(Membership plan, LoungeFile.Pool pool, boolean notes) {
        List<Line> lines = new ArrayList<>();
        lines.add(Line.of("fxeasy.shop.benefit.leverage", FxText.literal(plan.maxLeverage() + "x"), FxTheme.TEXT));
        lines.add(Line.heading("fxeasy.shop.fee.title"));
        FeeDiscount discount = plan.discount();
        fee(lines, "fxeasy.shop.fee.spread", "fxeasy.shop.fee.spread.note", discount.spread(), notes);
        fee(lines, "fxeasy.shop.fee.slippage", "fxeasy.shop.fee.slippage.note", discount.slippage(), notes);
        fee(lines, "fxeasy.shop.fee.swap", "fxeasy.shop.fee.swap.note", discount.swap(), notes);
        fee(lines, "fxeasy.shop.fee.conversion", "fxeasy.shop.fee.conversion.note", discount.conversion(), notes);
        lines.add(loungeLine(plan, pool));
        if (plan.lounge() && pool != null) {
            lines.add(Line.of("fxeasy.shop.pool.title",
                    FxText.get("fxeasy.shop.pool.count", Integer.toString(usable(pool)),
                            Integer.toString(pool.capacity())),
                    FxTheme.MUTED));
        }
        return lines;
    }

    /// One cost of the platform with what the plan takes off it: the share of the cost the discount carries, or
    /// the plain rate of the platform when the plan takes nothing off it, with the note that says what that cost
    /// is where the card has the room for it.
    private static void fee(List<Line> lines, String labelKey, String noteKey, double discount, boolean notes) {
        boolean reduced = discount > 0.0D;
        lines.add(Line.of(labelKey, reduced
                ? FxText.get("fxeasy.shop.fee.off", Long.toString(Math.round(discount * 100.0D)))
                : FxText.get("fxeasy.shop.fee.standard"), reduced ? FxTheme.UP : FxTheme.MUTED));
        if (notes) {
            lines.add(Line.note(noteKey));
        }
    }

    /// The line the lounge of a tier writes: the pace of its gifts for a tier that carries one, the plain answer
    /// of the platform for one that does not, and a word that the pool has not arrived yet for a tier whose pool
    /// the client cannot know before the platform has reported it.
    private static Line loungeLine(Membership plan, LoungeFile.Pool pool) {
        if (!plan.lounge()) {
            return Line.of("fxeasy.shop.lounge", FxText.get("fxeasy.shop.lounge.none"), FxTheme.MUTED);
        }
        if (pool == null) {
            return Line.of("fxeasy.shop.lounge", FxText.get("fxeasy.shop.lounge.pending"), FxTheme.MUTED);
        }
        return Line.of("fxeasy.shop.lounge",
                FxText.get("fxeasy.shop.lounge.rate", FxUi.marketTime(pool.refreshTicks())), FxTheme.UP);
    }

    /// The entries of the pool a lounge draws from, one line each: the gift with how many of it one delivery
    /// carries, against the share of the draws that gift takes. A tier without a lounge, a platform that has not
    /// reported its pool and an entry the registry cannot hand over write no line at all.
    static List<Line> poolLines(Membership plan, LoungeFile.Pool pool) {
        if (!plan.lounge() || pool == null) {
            return List.of();
        }
        int total = weight(pool);
        if (total <= 0) {
            return List.of();
        }
        List<Line> lines = new ArrayList<>();
        for (LoungeFile.Entry entry : pool.entries()) {
            if (!entry.usable()) {
                continue;
            }
            ItemStack stack = entry.gift().stack();
            lines.add(Line.gift(stack.getHoverName().copy().append(" ×" + entry.count()),
                    FxText.literal("%.1f%%", share(entry.weight(), total))));
        }
        return lines;
    }

    /// Weight of every entry of a pool that can be drawn at all, which is what the share of one entry is measured
    /// against: an entry the registry cannot hand over is never drawn and must not take a share of the draws.
    static int weight(LoungeFile.Pool pool) {
        int total = 0;
        for (LoungeFile.Entry entry : pool.entries()) {
            if (entry.usable()) {
                total += entry.weight();
            }
        }
        return total;
    }

    /// How many entries of a pool can be drawn at all, which is what the heading of the pool writes out.
    static int usable(LoungeFile.Pool pool) {
        int count = 0;
        for (LoungeFile.Entry entry : pool.entries()) {
            if (entry.usable()) {
                count++;
            }
        }
        return count;
    }

    /// The share of the draws one entry of the pool takes, in percent.
    static double share(int weight, int total) {
        return total <= 0 ? 0.0D : weight * 100.0D / total;
    }

    private static boolean footer(GuiGraphicsExtractor graphics, Font font, FxClientState state, FxInput input,
            ScreenRectangle footer) {
        FooterCells cells = footerCells(footer);
        Membership plan = state.membership();
        FxUi.card(graphics, footer);
        Component status = plan == Membership.NONE
                ? FxText.get("fxeasy.shop.status.none")
                : FxText.get("fxeasy.shop.status.plan", FxText.get(plan.nameKey()).getString(),
                        FxUi.marketTime(state.membershipTicksLeft()));
        FxUi.clipped(graphics, font, footer, footer.left() + PAD, footer.top() + (footer.height() - 8) / 2, status,
                plan == Membership.NONE ? FxTheme.MUTED : FxTheme.TEXT);
        boolean renewing = plan != Membership.NONE && state.autoRenew();
        FxControls.button(graphics, font, cells.renew(),
                FxText.get(renewing ? "fxeasy.shop.renew.on" : "fxeasy.shop.renew.off"), FxTheme.BUTTON,
                renewing ? FxTheme.UP : FxTheme.MUTED, input.hovered(cells.renew()),
                input.pressed && input.hovered(cells.renew()), plan != Membership.NONE);
        if (plan != Membership.NONE && input.clicked(cells.renew())) {
            FxActions.setAutoRenew(!state.autoRenew());
            FxEasy.LOGGER.info("FX terminal subscription renewal {}", state.autoRenew() ? "off" : "on");
        }
        int gifts = state.lounge().size();
        boolean lounge = state.loungeOpen() || gifts > 0;
        FxControls.button(graphics, font, cells.lounge(),
                gifts > 0 ? FxText.get("fxeasy.shop.lounge.count", Integer.toString(gifts))
                        : FxText.get("fxeasy.shop.lounge"),
                ARGB.multiplyAlpha(FxTheme.ACCENT, 0.72F), ARGB.color(255, 12, 16, 20), input.hovered(cells.lounge()),
                input.pressed && input.hovered(cells.lounge()), lounge);
        return lounge && input.clicked(cells.lounge());
    }

    /// The cards of the page, in the order the catalogue sells them. One is computed from the page area alone, so
    /// the draw and a scripted click read the same rectangles without the click having to know the frame.
    public static List<CardCells> cards(ScreenRectangle area) {
        int left = area.left() + PAD;
        int width = Math.max(1, area.width() - PAD * 2);
        int top = area.top() + PAD + TITLE;
        int height = Math.max(CARD_MIN, footerArea(area).top() - GAP - top);
        int cardWidth = Math.max(1, (width - GAP * (TIERS.size() - 1)) / TIERS.size());
        List<CardCells> cards = new ArrayList<>(TIERS.size());
        for (int index = 0; index < TIERS.size(); index++) {
            Tier tier = TIERS.get(index);
            ScreenRectangle card = FxUi.rect(left + index * (cardWidth + GAP), top, cardWidth, height);
            int inner = Math.max(1, card.width() - PAD * 2);
            int x = card.left() + PAD;
            ScreenRectangle name = FxUi.rect(x, card.top() + PAD, inner, NAME_ROW);
            ScreenRectangle lore = FxUi.rect(x, name.bottom(), inner, LINE);
            ScreenRectangle price = FxUi.rect(x, lore.bottom() + 3, inner, PRICE_ROW);
            ScreenRectangle period = FxUi.rect(x, price.bottom(), inner, LINE);
            ScreenRectangle action = FxUi.rect(x, card.bottom() - PAD - BUTTON, inner, BUTTON);
            int detailTop = period.bottom() + 3;
            ScreenRectangle detail = FxUi.rect(x, detailTop, inner, Math.max(0, action.top() - GAP - detailTop));
            cards.add(new CardCells(tier.plan(), card, name, lore, price, period, detail, action));
        }
        return cards;
    }

    /// The subscription bar at the bottom of the page.
    public static ScreenRectangle footerArea(ScreenRectangle area) {
        return FxUi.rect(area.left() + PAD, area.bottom() - PAD - FxTheme.ROW, Math.max(1, area.width() - PAD * 2),
                FxTheme.ROW);
    }

    /// The two cells of the bar, right aligned in the order they are drawn.
    public static FooterCells footerCells(ScreenRectangle footer) {
        int width = Math.min(CELL, Math.max(1, footer.width() / 3));
        ScreenRectangle lounge = FxUi.rect(footer.right() - PAD - width, footer.top(), width, footer.height());
        ScreenRectangle renew = FxUi.rect(lounge.left() - FxTheme.GAP - width, lounge.top(), width, lounge.height());
        return new FooterCells(renew, lounge);
    }

    /// Length of one period of every plan, in game days.
    private static String days() {
        return Long.toString(Membership.PERIOD_TICKS / MarketEngine.TICKS_PER_DAY);
    }

    /// What stands between the player and the checkout of a tier, as the key of the wording the button carries,
    /// or null when the platform would sell it at all: a world that does not trade the currency the plan is
    /// priced in cannot price the plan, and a tier below the one the account is on cannot be bought back down to.
    /// Whether the account can afford it is decided at the checkout and not here, because the player pays in
    /// whichever currency they choose and the checkout weighs every one of them.
    private static String refusal(boolean owned, Membership plan) {
        if (priceCurrency(plan) == null) {
            return ShopEngine.REJECT_CURRENCY;
        }
        return owned ? ShopEngine.REJECT_DOWNGRADE : null;
    }

    /// Currency a tier is priced in, or null when this world does not trade it, which is the one thing that makes
    /// a card unbuyable whatever the balance is.
    private static Currency priceCurrency(Membership plan) {
        return Currencies.byId(plan.priceCurrencyId());
    }

    private static Tier tier(Membership plan) {
        for (Tier tier : TIERS) {
            if (tier.plan() == plan) {
                return tier;
            }
        }
        return TIERS.getFirst();
    }

    private static Identifier art(String name) {
        return Identifier.fromNamespaceAndPath(FxEasy.MODID, "textures/gui/shop/" + name + ".png");
    }
}
