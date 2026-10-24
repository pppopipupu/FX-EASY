package com.pppopipupu.fxeasy.client;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;

public final class AngryPostEffects {

    private static final Identifier ANGRY = Identifier.fromNamespaceAndPath(FxEasy.MODID, "angry");
    private static final Identifier VOID = Identifier.fromNamespaceAndPath(FxEasy.MODID, "void");
    private static final Identifier STORM = Identifier.fromNamespaceAndPath(FxEasy.MODID, "storm");
    private static final Identifier STORM_VOID = Identifier.fromNamespaceAndPath(FxEasy.MODID, "storm_void");
    private static final Set<Identifier> EFFECTS = Set.of(ANGRY, VOID, STORM, STORM_VOID);
    private static int mode = -1;

    public static int mode() {
        return mode;
    }

    public static void set(int next) {
        if (next == mode) {
            return;
        }
        mode = next;
        apply();
    }

    private static void apply() {
        GameRenderer renderer = Minecraft.getInstance().gameRenderer;
        Identifier current = renderer.currentPostEffect();
        if (mode < 0) {
            if (isOurs(current)) {
                renderer.clearPostEffect();
            }
            return;
        }
        Identifier target = switch (mode) {
            case 0 -> ANGRY;
            case 1 -> VOID;
            case 2 -> STORM;
            case 3 -> STORM_VOID;
            default -> null;
        };
        if (target == null) {
            if (isOurs(current)) {
                renderer.clearPostEffect();
            }
            return;
        }
        if (!target.equals(current)) {
            renderer.setPostEffect(target);
        }
    }

    private static boolean isOurs(Identifier effect) {
        return effect != null && EFFECTS.contains(effect);
    }

    private AngryPostEffects() {
    }
}
