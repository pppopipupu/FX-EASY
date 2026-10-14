package com.pppopipupu.fxeasy.client;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import com.pppopipupu.fxeasy.content.FxCurrencies;
import com.pppopipupu.fxeasy.content.FxItems;
import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/// Developer probe for the panel and the item icon, which need a real client with a working renderer.
/// Enabled with `-Dfxeasy.probe.dir=<screenshot dir>`: the probe hands the player a phone, takes screenshots
/// when the world or the terminal is on screen and quits the client, so a run needs no interaction.
public final class FxProbe {

    private static final String DIRECTORY = System.getProperty("fxeasy.probe.dir", "");
    private static final boolean QUIT = Boolean.getBoolean("fxeasy.probe.quit");
    private static final int RESIZE_TICK = Integer.getInteger("fxeasy.probe.resizeTick", 20);
    private static final int DAYLIGHT_TICK = Integer.getInteger("fxeasy.probe.daylightTick", 40);
    private static final int GIVE_TICK = Integer.getInteger("fxeasy.probe.giveTick", 60);
    private static final int CURRENCY_COUNT = Integer.getInteger("fxeasy.probe.currency", 0);
    private static final String CURRENCY_ID = System.getProperty("fxeasy.probe.currencyId", "gold");
    private static final int WIDTH = Integer.getInteger("fxeasy.probe.width", 0);
    private static final int HEIGHT = Integer.getInteger("fxeasy.probe.height", 0);
    private static final int QUIT_TICK = Integer.getInteger("fxeasy.probe.quitTick", 600);
    private static final int INVENTORY_TICK = Integer.getInteger("fxeasy.probe.inventoryTick", 0);
    private static final int INVENTORY_TICKS = Integer.getInteger("fxeasy.probe.inventoryTicks", 40);
    private static final int TERMINAL_TICK = Integer.getInteger("fxeasy.probe.terminalTick", 0);
    private static final List<Integer> SHOTS = parseShots(System.getProperty("fxeasy.probe.shots", "120,300,480"));
    private static final List<Click> CLICKS = parseClicks(System.getProperty("fxeasy.probe.clicks", ""));

    private static int ticks;
    private static int shots;

    private FxProbe() {
    }

    /// One scripted click: at [tick] the probe clicks a cell of the panel named by [target], or [x],[y] in
    /// framebuffer pixels when the entry carries coordinates, on whatever screen is open. A named cell is what a
    /// run wants, because the panel resolves it against the frame it draws and the name does not depend on the
    /// window size; pixels are for screens the panel does not own.
    private record Click(int tick, String target, double x, double y) {
        @Override
        public String toString() {
            return this.target == null ? this.tick + " at " + this.x + "," + this.y : this.tick + " on " + this.target;
        }
    }

    public static boolean enabled() {
        return !DIRECTORY.isEmpty();
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        ticks++;
        if (ticks == DAYLIGHT_TICK) {
            if (minecraft.player.connection != null) {
                minecraft.player.connection.sendCommand("gamerule doDaylightCycle false");
                minecraft.player.connection.sendCommand("time set noon");
                minecraft.player.connection.sendCommand("weather clear");
            }
        }
        if (ticks == RESIZE_TICK && WIDTH > 0 && HEIGHT > 0) {
            minecraft.getWindow().setWindowed(WIDTH, HEIGHT);
            FxEasy.LOGGER.info("FX probe resized the window to {}x{}", WIDTH, HEIGHT);
        }
        if (ticks == GIVE_TICK) {
            givePhone(minecraft);
            giveCurrency(minecraft);
        }
        if (INVENTORY_TICK > 0) {
            if (ticks == INVENTORY_TICK && minecraft.screen == null) {
                minecraft.setScreen(new InventoryScreen(minecraft.player));
            } else if (ticks == INVENTORY_TICK + INVENTORY_TICKS && minecraft.screen instanceof InventoryScreen) {
                minecraft.setScreen(null);
            }
        }
        if (TERMINAL_TICK > 0 && ticks == TERMINAL_TICK && minecraft.screen == null) {
            FxPhoneTerminal.open();
        }
        for (Click click : CLICKS) {
            if (ticks == click.tick() && minecraft.screen != null) {
                click(minecraft, minecraft.screen, click);
            }
        }
        if (shots < SHOTS.size() && ticks >= SHOTS.get(shots)) {
            shots++;
            FxEasy.LOGGER.info("FX probe slot 0 is {} on the client and {} on the server", describe(minecraft.player.getInventory().getItem(0)),
                    describe(serverSlot(minecraft)));
            capture(minecraft, "probe-" + shots + ".png");
        }
        if (QUIT && ticks >= QUIT_TICK) {
            FxEasy.LOGGER.info("FX probe finished after {} ticks, {} screenshots", ticks, shots);
            minecraft.stop();
        }
    }

    private static void givePhone(Minecraft minecraft) {
        ServerPlayer player = serverPlayer(minecraft);
        if (player == null) {
            return;
        }
        player.getInventory().add(new ItemStack(FxItems.FX_PHONE.get()));
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        player.getInventory().setSelectedSlot(0);
        player.connection.send(new ClientboundSetHeldSlotPacket(0));
        FxEasy.LOGGER.info("FX probe handed the phone to {}", player.getName().getString());
    }

    /// Puts [CURRENCY_COUNT] items of [CURRENCY_ID] into the inventory: a scripted deposit and a scripted order
    /// both move items the player has to carry, the terminal never invents a balance.
    private static void giveCurrency(Minecraft minecraft) {
        if (CURRENCY_COUNT <= 0) {
            return;
        }
        ServerPlayer player = serverPlayer(minecraft);
        if (player == null) {
            return;
        }
        Currency currency = currency();
        player.getInventory().add(FxCurrencies.stack(currency, CURRENCY_COUNT));
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        FxEasy.LOGGER.info("FX probe handed {} items of {} to {}", CURRENCY_COUNT, currency.id(),
                player.getName().getString());
    }

    private static Currency currency() {
        for (Currency candidate : Currencies.all()) {
            if (candidate.id().equalsIgnoreCase(CURRENCY_ID)) {
                return candidate;
            }
        }
        Currency gold = Currencies.byId("gold");
        return gold != null ? gold : Currencies.all().getFirst();
    }

    private static ServerPlayer serverPlayer(Minecraft minecraft) {
        if (minecraft.getSingleplayerServer() == null) {
            return null;
        }
        List<ServerPlayer> players = minecraft.getSingleplayerServer().getPlayerList().getPlayers();
        return players.isEmpty() ? null : players.getFirst();
    }

    private static String describe(ItemStack stack) {
        return stack.isEmpty() ? "empty" : stack.getItem() + " x" + stack.getCount();
    }

    private static ItemStack serverSlot(Minecraft minecraft) {
        ServerPlayer player = serverPlayer(minecraft);
        return player == null ? ItemStack.EMPTY : player.getInventory().getItem(0);
    }

    private static void capture(Minecraft minecraft, String name) {
        File directory = minecraft.gameDirectory;
        Screenshot.grab(directory, name, minecraft.getMainRenderTarget(), 1,
                message -> FxEasy.LOGGER.info("FX probe screenshot {}: {}", name, message.getString()));
    }

    private static void click(Minecraft minecraft, Screen screen, Click click) {
        if (click.target() != null) {
            if (screen instanceof FxPhoneScreen panel) {
                panel.testClick(click.target());
            }
            return;
        }
        double scale = minecraft.getWindow().getGuiScale();
        MouseButtonEvent event = new MouseButtonEvent(click.x() / scale, click.y() / scale, new MouseButtonInfo(0, 0));
        screen.mouseClicked(event, false);
        screen.mouseReleased(event);
        FxEasy.LOGGER.info("FX probe clicked {} on {}", click, screen.getClass().getSimpleName());
    }

    private static List<Click> parseClicks(String value) {
        List<Click> parsed = new ArrayList<>();
        for (String part : value.split(";")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] fields = trimmed.split(":");
            if (fields.length == 2) {
                parsed.add(new Click(Integer.parseInt(fields[0].trim()), fields[1].trim(), 0.0D, 0.0D));
                continue;
            }
            parsed.add(new Click(Integer.parseInt(fields[0].trim()), null, Double.parseDouble(fields[1].trim()),
                    Double.parseDouble(fields[2].trim())));
        }
        return parsed;
    }

    private static List<Integer> parseShots(String value) {
        List<Integer> parsed = new ArrayList<>();
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                parsed.add(Integer.parseInt(trimmed));
            }
        }
        return parsed;
    }
}
