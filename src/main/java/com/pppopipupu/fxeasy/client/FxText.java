package com.pppopipupu.fxeasy.client;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import com.pppopipupu.fxeasy.market.Currency;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/// Terminal text. Labels are resolved through the vanilla language files and every component carries the
/// bundled Maple Mono face, which holds the CJK glyphs the default font cannot draw. The whole panel goes
/// through this class, and the layout measures with the styled component, so the monospaced metrics of the
/// bundled font are what the layout sees.
public final class FxText {

    private static final Style STYLE = Style.EMPTY
            .withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath(FxEasy.MODID, "terminal")));
    /// The large face: the same font at twice the size, which is a second provider over the same file, so a
    /// line drawn with it is still one atlas texel per screen pixel.
    private static final Style BIG_STYLE = Style.EMPTY
            .withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath(FxEasy.MODID, "terminal_big")));
    private static final Map<String, Component> CACHE = new HashMap<>();

    private static Language cachedLanguage;

    private FxText() {
    }

    public static Component get(String key) {
        Language language = Language.getInstance();
        if (language != cachedLanguage) {
            CACHE.clear();
            cachedLanguage = language;
        }
        return CACHE.computeIfAbsent(key, name -> Component.translatable(name).withStyle(STYLE));
    }

    /// Keys with one argument are transient: only the notice feed produces them.
    public static Component get(String key, String argument) {
        return Component.translatable(key, argument).withStyle(STYLE);
    }

    public static Component get(String key, String... arguments) {
        return Component.translatable(key, (Object[]) arguments).withStyle(STYLE);
    }

    public static Component big(String text) {
        return Component.literal(text).withStyle(BIG_STYLE);
    }

    /// Display name of a currency in the language this client reads: the definition carries one name per
    /// language next to the plain one, because the name is data of the world and not a key of the mod.
    public static Component name(Currency currency) {
        return literal(currency.name(Minecraft.getInstance().getLanguageManager().getSelected()));
    }

    public static Component literal(String text) {
        return Component.literal(text).withStyle(STYLE);
    }

    public static Component literal(String format, Object... arguments) {
        return literal(String.format(Locale.ROOT, format, arguments));
    }

    /// Joins already styled parts with single spaces. Every part keeps the bundled font it was built with, so a
    /// label and the value next to it stay one component the layout can measure.
    public static Component join(Component... parts) {
        MutableComponent joined = Component.empty();
        boolean first = true;
        for (Component part : parts) {
            if (!first) {
                joined.append(" ");
            }
            joined.append(part);
            first = false;
        }
        return joined;
    }
}
