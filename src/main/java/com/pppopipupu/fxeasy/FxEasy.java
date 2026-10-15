package com.pppopipupu.fxeasy;

import module java.base;

import com.mojang.logging.LogUtils;
import com.pppopipupu.fxeasy.content.FxItems;
import com.pppopipupu.fxeasy.datagen.FxLanguage;
import com.pppopipupu.fxeasy.datagen.FxModels;
import com.pppopipupu.fxeasy.datagen.FxRecipes;
import com.pppopipupu.fxeasy.datagen.FxStructures;
import com.pppopipupu.fxeasy.datagen.FxVillages;
import com.pppopipupu.fxeasy.gametest.FxEconomyGameTests;
import com.pppopipupu.fxeasy.gametest.FxMarketGameTests;
import com.pppopipupu.fxeasy.gametest.FxVillageGameTests;
import com.pppopipupu.fxeasy.net.FxNetwork;
import com.pppopipupu.fxeasy.server.FxTradingService;
import com.pppopipupu.fxeasy.server.FxWorldEconomyTracker;
import net.minecraft.server.packs.PackType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

@Mod(FxEasy.MODID)
public final class FxEasy {

    public static final String MODID = "fxeasy";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FxEasy(IEventBus modBus, ModContainer container) {
        FxConfig.register(container);
        FxItems.ITEMS.register(modBus);
        FxNetwork.register(modBus);
        FxMarketGameTests.register(modBus);
        FxEconomyGameTests.register(modBus);
        FxVillageGameTests.register(modBus);
        FxWorldEconomyTracker.register(modBus);
        modBus.addListener(FxEasy::gatherData);
        modBus.addListener(FxEasy::onConfigChanged);
        NeoForge.EVENT_BUS.addListener(FxEasy::onServerStarted);
        NeoForge.EVENT_BUS.addListener(FxEasy::onServerStopping);
        NeoForge.EVENT_BUS.addListener(FxEasy::onServerTick);
    }

    /// The economy is retuned by editing a file, so the cached values are refreshed whenever the config loads or
    /// is reloaded and never read live from the per tick path. Unloading drops them back to the defaults: the
    /// values cannot be read once the file is gone, and a stopped world must not leave its tuning behind for the
    /// next one the client joins.
    private static void onConfigChanged(ModConfigEvent event) {
        if (event.getConfig().getSpec() != FxConfig.spec()) {
            return;
        }
        if (event instanceof ModConfigEvent.Unloading) {
            FxConfig.reset();
            return;
        }
        FxConfig.reload();
    }

    private static void gatherData(GatherDataEvent.Client event) {
        event.createProvider(output -> new FxLanguage(output, "en_us"));
        event.createProvider(output -> new FxLanguage(output, "zh_cn"));
        event.createProvider(FxModels::new);
        event.createProvider(FxRecipes.Runner::new);
        event.createProvider(FxStructures::new);
        event.addProvider(new FxVillages(event.getGenerator().getPackOutput(),
                event.getResourceManager(PackType.SERVER_DATA)));
    }

    private static void onServerStarted(ServerStartedEvent event) {
        FxTradingService.start(event.getServer());
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        FxTradingService.stop();
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        FxTradingService.tickServer();
    }
}
