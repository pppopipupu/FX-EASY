package com.pppopipupu.fxeasy.client;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import com.pppopipupu.fxeasy.content.FxCurrencies;
import com.pppopipupu.fxeasy.content.FxItems;
import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;
import com.pppopipupu.fxeasy.market.Timeframe;
import com.pppopipupu.fxeasy.shop.Membership;
import com.pppopipupu.fxeasy.trade.Money;
import com.pppopipupu.fxeasy.trade.TradeEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/// Full chain regression of the terminal, run inside a development client by `bash gradlew runClientRegression`
/// (`-Dfxeasy.regression=true`, `scripts/client-regression.sh` wraps it). One client launch: it hands the player
/// a phone through the server, opens the panel through the item the player would use, then walks every page,
/// every overlay and every path that moves money, and asserts the client mirror and the cells of the panel after
/// each step. It ends with the line `FX regression: <n> steps, <m> failed, <t> ticks` and stops the client, so a
/// failing step is a failing run and not something a human has to look at.
///
/// The opening, the walkthrough and the welcome capital happen once per account, so a run needs a player whose
/// account the world has never seen: the script passes a fresh name, and a run that finds the opening already
/// gone fails on its own first steps instead of quietly testing less.
///
/// A step runs at its tick and is checked [SETTLE] ticks later, once the server has answered. A step that is not
/// ready yet - the opening starts only after the account snapshot arrives - waits, and a step that waits longer
/// than [WAIT_LIMIT] ticks runs anyway so that its check reports what is missing instead of the run hanging.
public final class FxRegression {

    private static final boolean ENABLED = Boolean.getBoolean("fxeasy.regression");
    private static final int RESIZE_TICK = 10;
    private static final int START_TICK = 20;
    private static final int GAP = 10;
    private static final int SETTLE = 7;
    private static final int WAIT_LIMIT = 60;
    private static final int STARTUP_LIMIT = 600;
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final String HOLDER = "Regress";
    private static final int AGE = 30;
    private static final long ITEMS = 64L;
    private static final long MORE_ITEMS = 32L;
    /// Range the size slider offered when a step last dragged it, in items of the base currency, which the
    /// position the next step opens has to narrow.
    private static int draggedRange = -1;

    /// One step of the run: [waitFor] gates it, [action] runs, and [check] has to hold [settle] ticks later. A
    /// check returns null when it holds and the reason it does not otherwise, so a failure names what it saw.
    private record Step(String name, Supplier<Boolean> waitFor, Runnable action, int settle, Supplier<String> check) {
    }

    private record Pending(Step step, int due) {
    }

    private static final List<Step> STEPS = steps();
    private static final List<Pending> PENDING = new ArrayList<>();
    private static int index;
    private static int failed;
    private static int ticks;
    private static int nextTick = START_TICK;
    private static int waitingSince;
    private static int startup;
    private static boolean finished;

    private FxRegression() {
    }

    public static boolean enabled() {
        return ENABLED;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (finished) {
            return;
        }
        if (minecraft.player == null || minecraft.level == null) {
            if (++startup > STARTUP_LIMIT) {
                FxEasy.LOGGER.error("FX regression: no world after {} ticks, stopping: a world that is still"
                        + " locked by a client that is closing cannot be joined", startup);
                finished = true;
                minecraft.stop();
            }
            return;
        }
        ticks++;
        if (ticks == RESIZE_TICK) {
            minecraft.getWindow().setWindowed(WIDTH, HEIGHT);
            FxEasy.LOGGER.info("FX regression resized the window to {}x{}", WIDTH, HEIGHT);
        }
        for (Iterator<Pending> iterator = PENDING.iterator(); iterator.hasNext(); ) {
            Pending pending = iterator.next();
            if (ticks < pending.due()) {
                continue;
            }
            iterator.remove();
            String problem = pending.step().check().get();
            if (problem == null) {
                FxEasy.LOGGER.info("FX regression ok {}", pending.step().name());
            } else {
                failed++;
                FxEasy.LOGGER.error("FX regression FAIL {}: {}", pending.step().name(), problem);
            }
        }
        if (index < STEPS.size()) {
            Step step = STEPS.get(index);
            boolean ready = ticks >= nextTick && step.waitFor().get();
            if (!ready && ticks - waitingSince <= WAIT_LIMIT) {
                return;
            }
            if (!ready) {
                FxEasy.LOGGER.warn("FX regression ran {} {} ticks after the previous step", step.name(),
                        ticks - waitingSince);
            }
            index++;
            step.action().run();
            PENDING.add(new Pending(step, ticks + step.settle()));
            nextTick = ticks + GAP;
            waitingSince = ticks;
            return;
        }
        if (PENDING.isEmpty()) {
            finished = true;
            FxEasy.LOGGER.info("FX regression: {} steps, {} failed, {} ticks", STEPS.size(), failed, ticks);
            minecraft.stop();
        }
    }

    /// The scenario: every page, every overlay and every path that moves money, in the order a player meets them.
    /// The waits are the states the panel has to have reached - the opening is up, a page is showing its cells,
    /// an overlay has taken the pointer - because the server answers over ticks and the run has to follow.
    private static List<Step> steps() {
        Script script = new Script();
        script.step("give the phone", FxRegression::givePhone,
                () -> carried(FxItems.FX_PHONE.get()) > 0 ? null : "the phone is not in the inventory");
        script.step("use the phone", FxRegression::usePhone,
                () -> terminal() != null ? null : "the terminal did not open");
        script.step("opening: the disclosure", () -> openingStep() >= 0, () -> click("opening.next"),
                () -> openingAt(1));
        script.step("opening: the terms", () -> openingStep() == 1, () -> click("opening.next"),
                () -> openingAt(2));
        script.step("opening: the name box", () -> openingStep() == 2, () -> click("opening.name"),
                () -> openingAt(2));
        script.step("opening: type the holder", () -> openingStep() == 2, () -> type(HOLDER),
                () -> openingAt(2));
        script.step("opening: the age box", () -> openingStep() == 2, () -> click("opening.age"),
                () -> openingAt(2));
        script.step("opening: type the age", () -> openingStep() == 2, () -> type(Integer.toString(AGE)),
                () -> openingAt(2));
        script.step("opening: register", () -> openingStep() == 2, () -> click("opening.register"),
                FxRegression::registered);
        script.step("walkthrough: the card", () -> tutorialStep() >= 0, () -> {
        }, () -> upCheck("tutorial.next"));
        script.step("walkthrough: the market list", FxRegression::touring, () -> click("tutorial.next"),
                FxRegression::walking);
        script.step("walkthrough: the chart", FxRegression::touring, () -> click("tutorial.next"),
                FxRegression::walking);
        script.step("walkthrough: the ticket", FxRegression::touring, () -> click("tutorial.next"),
                FxRegression::walking);
        script.step("walkthrough: the metrics", FxRegression::touring, () -> click("tutorial.next"),
                FxRegression::walking);
        script.step("walkthrough: the intel", FxRegression::touring, () -> click("tutorial.next"),
                FxRegression::walking);
        script.step("walkthrough: finish", FxRegression::touring, () -> click("tutorial.next"),
                () -> state().tutorialSeen() ? null : "the walkthrough is not marked as seen");
        script.step("welcome capital: the card", FxRegression::bonusUp, () -> {
        }, () -> upCheck("bonus.ok"));
        script.step("welcome capital: claim", FxRegression::bonusUp, () -> click("bonus.ok"),
                () -> state().bonusPaid() && balance() == state().welcomeItems() * Money.UNIT ? null
                        : "the capital is not paid (" + state().bonusPaid() + ", " + balance() + ")");
        script.step("welcome capital: close", () -> up("bonus.close"), () -> click("bonus.close"),
                () -> absent("bonus.ok"));
        script.step("market: the list page", () -> up("tab.MARKET"), () -> click("tab.MARKET"),
                () -> upCheck("market.row.1"));
        script.step("market: pick a pair", () -> up("market.row.1"), () -> click("market.row.1"),
                () -> state().selectedPair().equals(Currencies.pairs().get(1)) ? null
                        : "the pair of the row is not selected (" + state().selectedPair().code() + ")");
        script.step("chart: the line style", () -> up("chart.LINE"), () -> click("chart.LINE"),
                () -> state().chartStyle() == FxClientState.ChartStyle.LINE ? null : "the chart is not a line");
        script.step("chart: the candle style", () -> up("chart.CANDLES"), () -> click("chart.CANDLES"),
                () -> state().chartStyle() == FxClientState.ChartStyle.CANDLES ? null : "the chart is not candles");
        script.step("chart: a longer timeframe", () -> up("timeframe." + Timeframe.M5.name()),
                () -> click("timeframe." + Timeframe.M5.name()),
                () -> state().selectedTimeframe() == Timeframe.M5 ? null : "the timeframe did not move");
        script.step("chart: the longest timeframe", () -> up("timeframe." + Timeframe.H1.name()),
                () -> click("timeframe." + Timeframe.H1.name()),
                () -> state().selectedTimeframe() == Timeframe.H1 ? null : "the hourly chart did not open");
        script.step("chart: the shortest timeframe", () -> up("timeframe." + Timeframe.M1.name()),
                () -> click("timeframe." + Timeframe.M1.name()),
                () -> state().selectedTimeframe() == Timeframe.M1 ? null : "the timeframe did not come back");
        script.step("ticket: a margin currency", () -> up("trade.margin." + state().welcomeCurrency().id()),
                () -> click("trade.margin." + state().welcomeCurrency().id()),
                () -> state().orderMargin(state().selectedPair()) == state().welcomeCurrency() ? null
                        : "the margin is " + state().orderMargin(state().selectedPair()).code());
        script.step("ticket: the leverage slider", () -> up("trade.leverage"), () -> click("trade.leverage"),
                () -> state().orderLeverage() == middleLeverage() ? null
                        : "the leverage is " + state().orderLeverage());
        script.step("ticket: the size slider", () -> up("trade.lots"), () -> click("trade.lots"),
                FxRegression::sizedOrder);
        script.step("ticket: open", () -> up("trade.open"), () -> click("trade.open"),
                () -> state().positions().size() == 1 && openPosition() ? null
                        : "the position is not booked (" + state().positions().size() + ")");
        script.step("ticket: the slider follows the margin", () -> up("trade.lots"), () -> click("trade.lots"),
                FxRegression::narrowerRange);
        script.step("ticket: open again", () -> up("trade.open"), () -> click("trade.open"),
                () -> state().positions().size() == 2 ? null : "the second position is missing");
        script.step("positions: the page", () -> up("tab.POSITIONS"), () -> click("tab.POSITIONS"),
                () -> upCheck("positions.close.0"));
        script.step("positions: close one", () -> up("positions.close.0"), () -> click("positions.close.0"),
                () -> state().positions().size() == 1 ? null : "the position was not closed");
        script.step("positions: close all", () -> up("positions.close_all"), () -> click("positions.close_all"),
                () -> upCheck("confirm.ok"));
        script.step("positions: confirm", () -> up("confirm.ok"), () -> click("confirm.ok"),
                () -> state().positions().isEmpty() ? null : "the positions are still open");
        script.step("account: the page", () -> up("tab.ACCOUNT"), () -> click("tab.ACCOUNT"),
                () -> upCheck("account.currency." + moved().id()));
        script.step("account: hand over items", () -> true, () -> giveItems(ITEMS),
                () -> carried(FxCurrencies.item(moved())) == ITEMS ? null
                        : "the items did not arrive (client " + carried(FxCurrencies.item(moved())) + ", server "
                                + serverCarried(FxCurrencies.item(moved())) + ")");
        script.step("account: the currency row", () -> up("account.currency." + moved().id()),
                () -> click("account.currency." + moved().id()), () -> upCheck("account.deposit"));
        script.step("account: deposit", () -> up("account.deposit"), () -> click("account.deposit"),
                () -> upCheck("confirm.ok"));
        script.step("account: confirm the deposit", () -> up("confirm.ok"), () -> click("confirm.ok"),
                () -> state().balance(moved()) == ITEMS * Money.UNIT && !state().noticeError() ? null
                        : "the balance is " + state().balance(moved()) / Money.UNIT + " and the notice says "
                                + state().noticeError());
        script.step("account: hand over more items", () -> true, () -> giveItems(MORE_ITEMS),
                () -> carried(FxCurrencies.item(moved())) == MORE_ITEMS ? null
                        : "the items did not arrive (client " + carried(FxCurrencies.item(moved())) + ", server "
                                + serverCarried(FxCurrencies.item(moved())) + ")");
        script.step("account: withdraw", () -> up("account.withdraw"), () -> click("account.withdraw"),
                () -> upCheck("confirm.ok"));
        script.step("account: confirm the withdrawal", () -> up("confirm.ok"), () -> click("confirm.ok"),
                () -> state().balance(moved()) == (ITEMS - MORE_ITEMS) * Money.UNIT && !state().noticeError() ? null
                        : "the balance is " + state().balance(moved()) / Money.UNIT + " and the notice says "
                                + state().noticeError());
        script.step("intel: open", () -> up("intel"), () -> click("intel"), () -> upCheck("intel.view.EVENTS"));
        script.step("intel: the message feed", () -> up("intel.view.EVENTS"), () -> click("intel.view.EVENTS"),
                () -> upCheck("intel.view.ATTRIBUTION"));
        script.step("intel: the attribution", () -> up("intel.view.ATTRIBUTION"),
                () -> click("intel.view.ATTRIBUTION"),
                () -> state().economy() != null && state().events() != null ? null
                        : "the economy or the message feed never arrived");
        script.step("intel: close", () -> up("intel.close"), () -> click("intel.close"),
                () -> absent("intel.close"));
        script.step("shop: the page", () -> up("tab.SHOP"), () -> click("tab.SHOP"), () -> upCheck("shop.buy.go"));
        script.step("shop: the renewal of a plan the account does not hold", () -> up("shop.renew"),
                () -> click("shop.renew"), FxRegression::noSubscription);
        script.step("shop: the lounge the top plan opens", () -> up("shop.lounge"), () -> click("shop.lounge"),
                FxRegression::noSubscription);
        script.step("shop: the checkout of a tier", () -> up("shop.buy.go"), () -> click("shop.buy.go"),
                () -> upCheck("checkout.pay"));
        script.step("shop: the currencies the account holds", () -> up("checkout.pay"), () -> {
            Currency paying = FxCheckout.defaultPaying(Membership.GO, state());
            if (paying != null) {
                click("checkout.currency." + paying.id());
            }
        }, FxRegression::checkoutStandsWhereItWas);
        script.step("shop: the checkout closed", () -> up("checkout.cancel"), () -> click("checkout.cancel"),
                () -> upCheck("shop.buy.go"));
        script.step("shop: the card of the tier again", () -> up("shop.buy.go"), () -> click("shop.buy.go"),
                () -> upCheck("checkout.pay"));
        script.step("shop: pay for the tier", () -> up("checkout.pay"), () -> click("checkout.pay"),
                FxRegression::subscribed);
        script.step("shop: the renewal of the plan just bought", () -> up("shop.renew"), () -> click("shop.renew"),
                () -> state().autoRenew() ? "the renewal of the plan just bought is still on" : null);
        script.step("close the terminal", () -> terminal() != null, FxRegression::closeTerminal,
                () -> terminal() == null ? null : "the terminal is still open");
        return script.steps;
    }

    /// What a click on a row of the checkout leaves standing: the card is still up with its buttons and the
    /// payment still stands on the currency of the account the card started on, because the row that was clicked
    /// is the one it was already set to.
    private static String checkoutStandsWhereItWas() {
        FxPhoneScreen panel = terminal();
        if (panel == null) {
            return "no terminal is open";
        }
        if (!panel.hasCell("checkout.pay")) {
            return "the click on a row of the checkout closed it";
        }
        Currency expected = FxCheckout.defaultPaying(Membership.GO, state());
        Currency paying = panel.testCheckoutPaying();
        return paying == expected ? null
                : "the checkout pays with " + (paying == null ? "nothing" : paying.code());
    }

    /// What paying at the checkout leaves of an account that covers the price: the account is on the tier the
    /// card sold, its period is running and the checkout is gone. What the platform charged for it is its own
    /// price off its own market, which is the rule the unit tests of ShopEngine pin.
    private static String subscribed() {
        if (state().membership() != Membership.GO) {
            return "the account is on " + state().membership().id();
        }
        if (state().membershipTicksLeft() <= 0L) {
            return "the period of the plan is not running";
        }
        FxPhoneScreen panel = terminal();
        return panel != null && panel.hasCell("checkout.pay") ? "the checkout is still up after the payment" : null;
    }

    /// What the shop leaves of an account the platform would refuse everything: the page is drawn and its cells
    /// are there, the card of a tier opens its checkout whose button - with a balance that cannot cover the total
    /// - buys nothing, and the cells that lead to a renewal or the lounge of a plan the account does not hold
    /// change nothing at all, because the panel never sends a request it knows the platform would answer with a
    /// refusal.
    private static String noSubscription() {
        if (state().membership() != Membership.NONE) {
            return "the account went on " + state().membership().id();
        }
        if (state().autoRenew()) {
            return "the renewal is on for an account without a plan";
        }
        if (state().loungeOpen() || !state().lounge().isEmpty()) {
            return "the lounge of an account without a plan is open";
        }
        return null;
    }

    /// Currency the run moves items in and out of, resolved when a step runs so it follows the currency table of
    /// the session it is walking.
    private static Currency moved() {
        for (Currency currency : Currencies.all()) {
            if (currency.backed()) {
                return currency;
            }
        }
        return Currencies.all().getFirst();
    }

    private static void givePhone() {
        onServer(player -> {
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().clearContent();
            player.getInventory().add(new ItemStack(FxItems.FX_PHONE.get()));
            player.getInventory().setSelectedSlot(0);
        });
    }

    /// Opens the terminal the way a player does: with the phone in hand, through the item's own use path, so the
    /// run covers the item and not only the screen.
    private static void usePhone() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode != null) {
            minecraft.gameMode.useItem(minecraft.player, InteractionHand.MAIN_HAND);
        }
    }

    private static void giveItems(long count) {
        onServer(player -> player.getInventory().add(FxCurrencies.stack(moved(), (int) count)));
    }

    private static void onServer(Consumer<ServerPlayer> action) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSingleplayerServer() == null) {
            return;
        }
        minecraft.getSingleplayerServer().execute(() -> {
            ServerPlayer player = serverPlayer();
            if (player != null) {
                action.accept(player);
                player.getInventory().setChanged();
                player.inventoryMenu.broadcastChanges();
            }
        });
    }

    private static void closeTerminal() {
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null) {
            screen.onClose();
        }
    }

    private static boolean click(String target) {
        FxPhoneScreen panel = terminal();
        if (panel == null) {
            FxEasy.LOGGER.warn("FX regression found no terminal to click {} on", target);
            return false;
        }
        return panel.testClick(target);
    }

    private static void type(String text) {
        FxPhoneScreen panel = terminal();
        if (panel != null) {
            panel.testType(text);
        }
    }

    private static ServerPlayer serverPlayer() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSingleplayerServer() == null) {
            return null;
        }
        List<ServerPlayer> players = minecraft.getSingleplayerServer().getPlayerList().getPlayers();
        return players.isEmpty() ? null : players.getFirst();
    }

    private static FxClientState state() {
        return FxClientState.INSTANCE;
    }

    private static FxPhoneScreen terminal() {
        return Minecraft.getInstance().screen instanceof FxPhoneScreen panel ? panel : null;
    }

    private static int openingStep() {
        FxPhoneScreen panel = terminal();
        return panel == null ? -1 : panel.testOpeningStep();
    }

    private static int tutorialStep() {
        FxPhoneScreen panel = terminal();
        return panel == null ? -1 : panel.testTutorialStep();
    }

    private static String upCheck(String target) {
        return up(target) ? null : "the terminal does not show " + target;
    }

    private static long serverCarried(Item item) {
        ServerPlayer player = serverPlayer();
        if (player == null) {
            return -1L;
        }
        long count = 0L;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static boolean up(String target) {
        FxPhoneScreen panel = terminal();
        return panel != null && panel.hasCell(target);
    }

    private static boolean touring() {
        return tutorialStep() >= 0 && !state().tutorialSeen();
    }

    private static String walking() {
        return touring() ? null : "the walkthrough is not up";
    }

    private static boolean bonusUp() {
        return up("bonus.ok");
    }

    /// What the balance of the welcome currency is in micro units, which is what the claim has to have moved.
    private static long balance() {
        return state().balance(state().welcomeCurrency());
    }

    private static boolean openPosition() {
        return state().positions().stream().allMatch(position -> position.leverage() == middleLeverage()
                && position.marginCurrencyOrdinal() == Currencies.index(state().welcomeCurrency()));
    }

    /// The leverage a click on the middle of the log slider sets, which is the square root of the range the plan
    /// of the account opens.
    private static int middleLeverage() {
        return Math.round((float) Math.sqrt(TradeEngine.MIN_LEVERAGE * (double) state().maxOrderLeverage()));
    }

    /// Whether the size the slider set is one the free margin of the margin currency backs, which is the whole
    /// point of the range: the ticket may never build a size the platform would refuse for want of margin, and a
    /// size of zero means the drag never landed on the slider. The range it was dragged in is remembered, because
    /// the position the next step opens has to narrow it.
    private static String sizedOrder() {
        String problem = backedSize();
        if (problem == null) {
            draggedRange = state().maxOrderUnits();
        }
        return problem;
    }

    /// Whether the slider now offers a narrower range than the last drag found. The range is what the free margin
    /// of the margin currency backs, so the margin the first position locked is what narrows it.
    private static String narrowerRange() {
        String problem = backedSize();
        if (problem != null) {
            return problem;
        }
        int range = state().maxOrderUnits();
        return range < draggedRange ? null : "the range is still " + range + ", not below " + draggedRange;
    }

    /// Whether the size the slider set is inside the range and one the free margin of the margin currency backs,
    /// or the reason it is not: a size of zero means the drag never landed on the slider.
    private static String backedSize() {
        FxClientState state = state();
        Currency margin = state.orderMargin(state.selectedPair());
        int units = state.orderUnits();
        int maximum = state.maxOrderUnits();
        long required = state.requiredMargin(margin, units, state.orderLeverage());
        long free = state.freeMargin(margin);
        if (units <= 0) {
            return "the size slider set nothing";
        }
        return units <= maximum && required <= free ? null
                : "the size " + units + " needs " + required + " of " + free + " free " + margin.code()
                        + " and the range is " + maximum;
    }

    private static long carried(Item item) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return 0L;
        }
        long count = 0L;
        for (int slot = 0; slot < minecraft.player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = minecraft.player.getInventory().getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static String openingAt(int expected) {
        int step = openingStep();
        return step == expected ? null : "the opening is on step " + step + ", not " + expected;
    }

    private static String registered() {
        if (!state().opened()) {
            return "the account is not open (" + state().holder() + ", " + state().age() + ")";
        }
        return HOLDER.equals(state().holder()) && state().age() == AGE
                ? null
                : "the account is open as " + state().holder() + " (" + state().age() + ")";
    }

    private static String absent(String target) {
        FxPhoneScreen panel = terminal();
        if (panel == null) {
            return "no terminal is open";
        }
        return panel.hasCell(target) ? "the terminal still shows " + target : null;
    }

    private static final class Script {

        private final List<Step> steps = new ArrayList<>();

        private void step(String name, Supplier<Boolean> waitFor, Runnable action, Supplier<String> check) {
            this.steps.add(new Step(name, waitFor, action, SETTLE, check));
        }

        private void step(String name, Runnable action, Supplier<String> check) {
            this.step(name, () -> true, action, check);
        }
    }
}
