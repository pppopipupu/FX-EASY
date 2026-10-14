package com.pppopipupu.fxeasy.datagen;

import module java.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pppopipupu.fxeasy.economy.EconomySourceKind;
import com.pppopipupu.fxeasy.event.MarketEventDefinition.ImpactChannel;
import com.pppopipupu.fxeasy.event.MarketEventKind;
import com.pppopipupu.fxeasy.event.MarketEventQuality;
import com.pppopipupu.fxeasy.market.MarketEngine;
import com.pppopipupu.fxeasy.shop.Membership;
import com.pppopipupu.fxeasy.trade.TradeEngine;
import org.junit.jupiter.api.Test;

class FxLanguageTest {

    /// The game loads the generated files and not the table, so a key that never reached them is drawn as its own
    /// name in the terminal. This fails whenever the table grew and the datagen was not run after it.
    @Test
    void everyTableEntryReachesBothGeneratedLocales() {
        Map<String, String> english = generated("en_us");
        Map<String, String> chinese = generated("zh_cn");
        for (FxLanguage.Entry entry : FxLanguage.table()) {
            assertEquals(entry.english(), english.get(entry.key()), entry.key());
            assertEquals(entry.chinese(), chinese.get(entry.key()), entry.key());
        }
        assertEquals(FxLanguage.table().size(), english.size(),
                "the generated file carries keys the table does not know");
        assertEquals(english.keySet(), chinese.keySet(), "the two locales do not carry the same keys");
    }

    /// The panels build these keys from an id at runtime, so a missing member of a family cannot be found by
    /// comparing the table with the files: every id each family can produce is looked up instead.
    @Test
    void everyRuntimeKeyFamilyCoversItsIds() {
        Map<String, String> english = generated("en_us");
        for (EconomySourceKind kind : EconomySourceKind.ALL) {
            assertTrue(english.containsKey("fxeasy.intel.source." + kind.id()), kind.id());
        }
        for (MarketEventQuality quality : MarketEventQuality.values()) {
            assertTrue(english.containsKey("fxeasy.intel.quality." + quality.id()), quality.id());
        }
        for (ImpactChannel channel : ImpactChannel.values()) {
            assertTrue(english.containsKey("fxeasy.intel.channel." + channel.id()), channel.id());
        }
        for (MarketEventKind kind : MarketEventKind.values()) {
            assertTrue(english.containsKey("fxeasy.intel.kind." + kind.id()), kind.id());
        }
        for (Membership plan : Membership.plans()) {
            assertTrue(english.containsKey(plan.nameKey()), plan.nameKey());
            assertTrue(english.containsKey(plan.loreKey()), plan.loreKey());
        }
    }

    /// The rate, the period and the blacklist age are the platform's numbers, not the translator's, so the
    /// wording has to carry the numbers the code charges.
    @Test
    void debtWordingCarriesThePlatformNumbers() {
        String percent = String.format(Locale.ROOT, "%.0f", TradeEngine.DEBT_RATE * 100.0D);
        String days = Integer.toString(TradeEngine.DEBT_PERIOD_TICKS / MarketEngine.TICKS_PER_DAY);
        String limit = Integer.toString(TradeEngine.DEBT_BLACKLIST_TICKS / MarketEngine.TICKS_PER_DAY);
        for (String locale : List.of("en_us", "zh_cn")) {
            Map<String, String> values = generated(locale);
            assertTrue(values.get("fxeasy.panel.debt.rate").contains(percent), locale + " " + percent);
            assertTrue(values.get("fxeasy.panel.debt.rate").contains(days), locale + " " + days);
            assertTrue(values.get("fxeasy.notice.credit_black").contains(limit), locale + " " + limit);
        }
    }

    private static Map<String, String> generated(String locale) {
        String path = "/assets/fxeasy/lang/" + locale + ".json";
        try (InputStream stream = FxLanguageTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path + " is not on the classpath, run the datagen");
            JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            Map<String, String> values = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                values.put(entry.getKey(), entry.getValue().getAsString());
            }
            return values;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
