package com.pppopipupu.fxeasy.client;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import com.pppopipupu.fxeasy.content.AngryHealth;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public final class AngryHealthHud {

    private static final Identifier ANGRY_HEART_FULL =
            Identifier.fromNamespaceAndPath(FxEasy.MODID, "hud/heart/angry_full");
    private static final Identifier ANGRY_HEART_HALF =
            Identifier.fromNamespaceAndPath(FxEasy.MODID, "hud/heart/angry_half");
    private static final Identifier HEART_CONTAINER =
            Identifier.withDefaultNamespace("hud/heart/container");
    private static final Identifier HEART_CONTAINER_HARDCORE =
            Identifier.withDefaultNamespace("hud/heart/container_hardcore");
    private static final int HEART_SIZE = 9;
    private static final int HEART_STEP = 8;
    private static final int HEARTS_PER_ROW = 10;
    private static final int HEALTH_BAR_LEFT = -91;
    private static final int HEALTH_BAR_BASE = 39;

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || minecraft.gameMode == null
                || !minecraft.gameMode.canHurtPlayer() || minecraft.player.isCreative()
                || minecraft.player.isSpectator()) {
            return;
        }
        float value = AngryHealth.get(minecraft.player);
        if (value <= 0.0F) {
            return;
        }
        int halves = Mth.ceil(value);
        int tempContainers = (halves + 1) / 2;
        float maxHealth = Math.max(minecraft.player.getMaxHealth(), minecraft.player.getHealth());
        float absorption = minecraft.player.getAbsorptionAmount();
        int healthContainers = Mth.ceil(maxHealth / 2.0F);
        int absorptionContainers = Mth.ceil(absorption / 2.0F);
        int vanillaContainers = healthContainers + absorptionContainers;
        int vanillaRows = Mth.ceil((maxHealth + absorption) / 2.0F / HEARTS_PER_ROW);
        int totalRows = (vanillaContainers + tempContainers + HEARTS_PER_ROW - 1) / HEARTS_PER_ROW;
        int rowHeight = Math.max(10 - (vanillaRows - 2), 3);
        minecraft.gui.leftHeight += Math.max(0, totalRows - vanillaRows) * rowHeight;

        int xLeft = graphics.guiWidth() / 2 + HEALTH_BAR_LEFT;
        int yLineBase = graphics.guiHeight() - HEALTH_BAR_BASE;
        boolean hardcore = minecraft.level != null && minecraft.level.getLevelData().isHardcore();
        Identifier container = hardcore ? HEART_CONTAINER_HARDCORE : HEART_CONTAINER;
        for (int slot = 0; slot < tempContainers; slot++) {
            int index = vanillaContainers + slot;
            int x = xLeft + (index % HEARTS_PER_ROW) * HEART_STEP;
            int y = yLineBase - (index / HEARTS_PER_ROW) * rowHeight;
            boolean half = slot * 2 + 1 == halves;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, container, x, y, HEART_SIZE, HEART_SIZE);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                    half ? ANGRY_HEART_HALF : ANGRY_HEART_FULL, x, y, HEART_SIZE, HEART_SIZE);
        }
    }

    private AngryHealthHud() {
    }
}
