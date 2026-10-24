package com.pppopipupu.fxeasy.client;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import com.pppopipupu.fxeasy.FxTickScheduler;
import com.pppopipupu.fxeasy.client.panel.FxShopCard;
import com.pppopipupu.fxeasy.client.render.AngryMeteorSummonerRenderer;
import com.pppopipupu.fxeasy.client.render.AngryStormGeneratorRenderer;
import com.pppopipupu.fxeasy.client.render.AngryWandRenderer;
import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;
import com.pppopipupu.fxeasy.net.FxAccountPayload;
import com.pppopipupu.fxeasy.net.FxCurrenciesPayload;
import com.pppopipupu.fxeasy.net.FxEconomyPayload;
import com.pppopipupu.fxeasy.net.FxEventsPayload;
import com.pppopipupu.fxeasy.net.FxHistoryPayload;
import com.pppopipupu.fxeasy.net.FxLoungePoolPayload;
import com.pppopipupu.fxeasy.net.FxMarketPayload;
import com.pppopipupu.fxeasy.net.FxNoticePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/// Client entry point: owns the state mirror that feeds the terminal panel and the client half of the trading
/// channel.
@Mod(value = FxEasy.MODID, dist = Dist.CLIENT)
public final class FxEasyClient {

    /// Automated client runs happen in a survival world without cheats, so they cannot hand the phone to the
    /// player. Setting this JVM property to a tick count asks the client to open the terminal by itself once
    /// a world is up and to close it again after that many ticks, because the test harness only runs its own
    /// commands while no screen is open.
    private static final int TERMINAL_TICKS = Integer.getInteger("fxeasy.e2e.openTerminal", 0);

    /// Clicks an end to end run performs on the panel by itself, as `tick:target` entries separated by `;`. A
    /// target names a cell of the panel rather than a pixel and the panel resolves it against the frame it is
    /// drawing, so the run does not have to know the layout of the window it runs in. No harness command can
    /// click, because every command needs the client thread that a screen blocks.
    private static final List<TestClick> CLICKS = parseClicks(System.getProperty("fxeasy.e2e.clicks", ""));

    /// Characters an end to end run types into the box that holds the focus, as `tick:text` entries separated by
    /// `;`. The harness cannot send a key to the window, so the run types through the same handler a real key
    /// takes and the panel cannot tell the two apart.
    private static final List<TestType> TYPES = parseTypes(System.getProperty("fxeasy.e2e.types", ""));

    /// What the run does about the two overlays every account goes through once: it registers the account and
    /// marks the walkthrough as seen, which are the requests a player would send, so the run gets past them and
    /// can drive the panel behind them. Both are remembered by the platform, so a run on an account that is
    /// already open skips them.
    /// The tick the run registers the account itself at, or -1 to leave the registration to the walk, which is
    /// what covers the boxes of the account opening.
    private static final int REGISTER_TICK = Integer.getInteger("fxeasy.e2e.register", 20);
    private static final int TUTORIAL_TICK = 30;
    private static final String HOLDER = "E2E";
    private static final int AGE = 30;

    private static int terminalTicks = -1;

    public FxEasyClient(IEventBus modBus) {
        AngryClient.install();
        modBus.addListener(AngryClient::registerRenderers);
        modBus.addListener(AngryClient::registerParticleProviders);
        modBus.addListener(AngryClient::registerParticleGroups);
        modBus.addListener(AngryClient::registerPipelines);
        modBus.addListener(AngryWandRenderer::registerSpecialModelRenderers);
        modBus.addListener(AngryWandRenderer::registerItemModels);
        modBus.addListener(AngryMeteorSummonerRenderer::registerSpecialModelRenderers);
        modBus.addListener(AngryStormGeneratorRenderer::registerSpecialModelRenderers);
        modBus.addListener(AngryStormGeneratorRenderer::registerItemModels);
        modBus.addListener(FxEasyClient::registerPayloadHandlers);
        modBus.addListener(FxEasyClient::registerGuiLayers);
        modBus.addListener(FxPhoneRenderer::registerPipelines);
        modBus.addListener(FxShopCard::registerPipelines);
        modBus.addListener(FxPhoneRenderer::registerSpecialModelRenderers);
        modBus.addListener(FxPhoneRenderer::registerItemModels);
        NeoForge.EVENT_BUS.addListener(FxEasyClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(FxEasyClient::onLoggingIn);
        if (FxProbe.enabled()) {
            NeoForge.EVENT_BUS.addListener(FxProbe::onClientTick);
        }
        if (FxRegression.enabled()) {
            NeoForge.EVENT_BUS.addListener(FxRegression::onClientTick);
        }
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.PLAYER_HEALTH,
                Identifier.fromNamespaceAndPath(FxEasy.MODID, "angry_health"), AngryHealthHud::render);
    }

    /// The mirror describes one world and one player, so entering a world drops what the previous one left in
    /// it: without this an account opened in an earlier world would keep the opening behind the panel in a world
    /// whose platform has never heard of it.
    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        FxClientState.INSTANCE.reset();
        FxTickScheduler.clearClient();
        AngryPostEffects.set(-1);
        FxEasy.LOGGER.info("FX terminal mirror reset for a new world");
    }

    private static void registerPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(FxCurrenciesPayload.TYPE, FxEasyClient::onCurrencies);
        event.register(FxLoungePoolPayload.TYPE, FxEasyClient::onLoungePool);
        event.register(FxMarketPayload.TYPE, FxEasyClient::onMarket);
        event.register(FxHistoryPayload.TYPE, FxEasyClient::onHistory);
        event.register(FxAccountPayload.TYPE, FxEasyClient::onAccount);
        event.register(FxNoticePayload.TYPE, FxEasyClient::onNotice);
        event.register(FxEconomyPayload.TYPE, FxEasyClient::onEconomy);
        event.register(FxEventsPayload.TYPE, FxEasyClient::onEvents);
    }

    private static void onEconomy(FxEconomyPayload payload, IPayloadContext context) {
        FxClientState.INSTANCE.applyEconomy(payload);
        int recording = 0;
        for (long atoms : payload.supportedAtoms()) {
            if (atoms > 0L) {
                recording++;
            }
        }
        FxEasy.LOGGER.info("FX terminal economy: status {} at bucket {} of day {}, {} sources with atoms",
                payload.status(), payload.bucketStart(), payload.rolloverDay(), recording);
    }

    private static void onEvents(FxEventsPayload payload, IPayloadContext context) {
        FxClientState.INSTANCE.applyEvents(payload);
        StringBuilder families = new StringBuilder();
        for (FxEventsPayload.Event message : payload.events()) {
            if (!families.isEmpty()) {
                families.append(',');
            }
            families.append(message.rootId());
        }
        FxEasy.LOGGER.info("FX terminal events: {} messages at revision {}, newest {}", payload.events().size(),
                payload.revision(), families);
    }

    private static void onCurrencies(FxCurrenciesPayload payload, IPayloadContext context) {
        List<Currency> definitions = payload.definitions();
        if (!definitions.equals(Currencies.all())) {
            Currencies.set(definitions);
            FxClientState.INSTANCE.reset();
        }
    }

    /// The pool the lounge of the world draws from, which the shop page writes out beside the top tier. It is a
    /// definition of the world like the currency table and is logged for the same reason: a run has to be able to
    /// tell that the page drew the pool of this world rather than none at all.
    private static void onLoungePool(FxLoungePoolPayload payload, IPayloadContext context) {
        FxClientState.INSTANCE.applyLoungePool(payload);
        FxEasy.LOGGER.info("FX terminal lounge pool: {} entries, one every {} market ticks, {} held",
                payload.entries().size(), payload.refreshTicks(), payload.capacity());
    }

    private static void onMarket(FxMarketPayload payload, IPayloadContext context) {
        FxClientState.INSTANCE.applyMarket(payload);
    }

    private static void onHistory(FxHistoryPayload payload, IPayloadContext context) {
        FxClientState.INSTANCE.applyHistory(payload);
        FxEasy.LOGGER.info("FX terminal history: {} candles", payload.candles().size());
    }

    private static void onAccount(FxAccountPayload payload, IPayloadContext context) {
        FxClientState state = FxClientState.INSTANCE;
        state.applyAccount(payload);
        if (payload.marginCall()) {
            FxEasy.LOGGER.warn("FX account under margin call with {} positions", payload.positions().size());
        }
    }

    private static void onNotice(FxNoticePayload payload, IPayloadContext context) {
        FxClientState.INSTANCE.applyNotice(payload);
        FxEasy.LOGGER.info("FX terminal notice: {} {} ({})", payload.key(), payload.argument(),
                payload.error() ? "rejected" : "done");
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        FxClientState.INSTANCE.tick();
        FxTickScheduler.tickClient();
        if (TERMINAL_TICKS > 0) {
            driveTerminalForTests();
        }
    }

    private static void driveTerminalForTests() {
        FxClientState state = FxClientState.INSTANCE;
        Minecraft minecraft = Minecraft.getInstance();
        if (terminalTicks < 0) {
            if (minecraft.level != null && minecraft.player != null && minecraft.screen == null) {
                terminalTicks = 0;
                FxPhoneTerminal.open();
            }
            return;
        }
        if (terminalTicks >= TERMINAL_TICKS) {
            if (minecraft.screen instanceof FxPhoneScreen) {
                minecraft.setScreen(null);
            }
            return;
        }
        if (minecraft.screen instanceof FxPhoneScreen screen) {
            if (!state.opened() && REGISTER_TICK >= 0 && terminalTicks >= REGISTER_TICK) {
                FxActions.openAccount(HOLDER, AGE);
            } else if (state.opened() && !state.tutorialSeen() && terminalTicks >= TUTORIAL_TICK) {
                FxActions.markTutorialSeen();
            }
            for (TestClick click : CLICKS) {
                if (click.tick() == terminalTicks) {
                    screen.testClick(click.target());
                }
            }
            for (TestType type : TYPES) {
                if (type.tick() == terminalTicks) {
                    screen.testType(type.text());
                }
            }
        }
        terminalTicks++;
    }

    private static List<TestClick> parseClicks(String value) {
        List<TestClick> parsed = new ArrayList<>();
        for (String part : value.split(";")) {
            String trimmed = part.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            int separator = trimmed.indexOf(':');
            if (separator <= 0) {
                FxEasy.LOGGER.warn("FX terminal test click {} is not tick:target", trimmed);
                continue;
            }
            try {
                parsed.add(new TestClick(Integer.parseInt(trimmed.substring(0, separator)),
                        trimmed.substring(separator + 1).strip()));
            } catch (NumberFormatException error) {
                FxEasy.LOGGER.warn("FX terminal test click {} carries no tick", trimmed);
            }
        }
        return parsed;
    }

    private static List<TestType> parseTypes(String value) {
        List<TestType> parsed = new ArrayList<>();
        for (String part : value.split(";")) {
            String trimmed = part.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            int separator = trimmed.indexOf(':');
            if (separator <= 0) {
                FxEasy.LOGGER.warn("FX terminal test type {} is not tick:text", trimmed);
                continue;
            }
            try {
                parsed.add(new TestType(Integer.parseInt(trimmed.substring(0, separator)),
                        trimmed.substring(separator + 1)));
            } catch (NumberFormatException error) {
                FxEasy.LOGGER.warn("FX terminal test type {} carries no tick", trimmed);
            }
        }
        return parsed;
    }

    private record TestClick(int tick, String target) {
    }

    private record TestType(int tick, String text) {
    }
}
