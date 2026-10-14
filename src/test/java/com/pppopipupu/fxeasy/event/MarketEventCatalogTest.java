package com.pppopipupu.fxeasy.event;

import module java.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pppopipupu.fxeasy.economy.EconomyConstants;
import com.pppopipupu.fxeasy.event.MarketEventDefinition.ImpactChannel;
import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;
import org.junit.jupiter.api.Test;

class MarketEventCatalogTest {

    private static final Currency EMERALD = Currencies.byId("emerald");
    private static final Currency GOLD = Currencies.byId("gold");

    private static final List<String> FAMILIES = List.of(
            "gold_industrial_output", "emerald_trade_issuance", "trade_reabsorption", "diamond_mining_boom",
            "netherite_resource_wave", "barter_boom",
            "raid_disruption", "raid_recovery", "trade_network_damage", "reconstruction_demand", "dimension_expansion",
            "dragon_unlock", "boss_confidence", "weather_logistics",
            "piglin_festival", "villager_trade_fair", "end_shipping_delay", "smithing_order_rumor",
            "resource_discovery_rumor", "gold_quality_dispute", "kurumi_margin_review", "cleric_forecast",
            "creeper_press_release", "allay_receipt_mixup");

    @Test
    void everyFamilyExistsWithDerivedKeys() {
        assertEquals(FAMILIES.size(), MarketEventCatalog.ALL.size());
        for (String id : FAMILIES) {
            MarketEventDefinition definition = MarketEventCatalog.byId(id);
            assertEquals(id, definition.id());
            assertEquals("fxeasy.event." + id + ".title", definition.titleKey());
            assertEquals("fxeasy.event." + id + ".body", definition.bodyKey());
            assertTrue(definition.lifetimeTicks() > 0L);
        }
    }

    @Test
    void unknownIdIsRejected() {
        assertTrue(MarketEventCatalog.find("not_a_family").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> MarketEventCatalog.byId("not_a_family"));
    }

    @Test
    void kindsAreSplitAsSpecified() {
        assertEquals(6, MarketEventCatalog.byKind(MarketEventKind.OBSERVED_REPORT).size());
        assertEquals(8, MarketEventCatalog.byKind(MarketEventKind.WORLD_EVENT).size());
        assertEquals(10, MarketEventCatalog.byKind(MarketEventKind.FLAVOR_EVENT).size());
    }

    @Test
    void reportsAndNewsOnlyEventsCarryNoImpact() {
        for (MarketEventDefinition definition : MarketEventCatalog.byKind(MarketEventKind.OBSERVED_REPORT)) {
            for (Currency currency : Currencies.all()) {
                assertEquals(0.0D, definition.peakImpact(currency), 0.0D);
            }
        }
        for (MarketEventDefinition definition : MarketEventCatalog.ALL) {
            if (definition.channel() == ImpactChannel.NONE) {
                for (Currency currency : Currencies.all()) {
                    assertEquals(0.0D, definition.peakImpact(currency), 0.0D);
                }
            }
        }
    }

    @Test
    void reportsPriceOnlySupplyOrUse() {
        for (MarketEventDefinition definition : MarketEventCatalog.byKind(MarketEventKind.OBSERVED_REPORT)) {
            assertNotNull(definition.channel());
            assertTrue(definition.channel() == ImpactChannel.SUPPLY || definition.channel() == ImpactChannel.USE);
        }
    }

    @Test
    void raidDisruptionMatchesItsSpecification() {
        MarketEventDefinition definition = MarketEventCatalog.RAID_DISRUPTION;
        assertEquals(MarketEventKind.WORLD_EVENT, definition.kind());
        assertEquals(MarketEventQuality.ESTIMATED, definition.quality());
        assertEquals(ImpactChannel.LIQUIDITY, definition.channel());
        double peak = definition.peakImpact(EMERALD);
        assertTrue(peak <= -0.002D && peak >= -0.008D, "emerald disruption is 0.2% to 0.8% down");
        assertTrue(definition.lifetimeTicks() >= EconomyConstants.TICKS_PER_DAY / 2L);
        assertTrue(definition.lifetimeTicks() <= EconomyConstants.TICKS_PER_DAY);
        assertEquals(5L * EconomyConstants.TICKS_PER_DAY, definition.cooldownTicks());
    }

    @Test
    void rumourFamiliesAreRumours() {
        assertEquals(MarketEventQuality.RUMOR, MarketEventCatalog.PIGLIN_FESTIVAL.quality());
        assertEquals(MarketEventQuality.RUMOR, MarketEventCatalog.VILLAGER_TRADE_FAIR.quality());
        assertEquals(MarketEventQuality.RUMOR, MarketEventCatalog.GOLD_QUALITY_DISPUTE.quality());
        assertEquals(MarketEventQuality.ENTERTAINMENT, MarketEventCatalog.KURUMI_MARGIN_REVIEW.quality());
        assertTrue(MarketEventCatalog.PIGLIN_FESTIVAL.peakImpact(GOLD) >= 0.002D);
        assertTrue(MarketEventCatalog.PIGLIN_FESTIVAL.peakImpact(GOLD) <= 0.006D);
    }
}
