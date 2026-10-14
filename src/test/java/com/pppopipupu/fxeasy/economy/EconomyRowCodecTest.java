package com.pppopipupu.fxeasy.economy;

import module java.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.pppopipupu.fxeasy.market.Currencies;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;

class EconomyRowCodecTest {

    @Test
    void currencyAmountsSurviveTheJsonRoundTrip() {
        List<CurrencyAmount> values = values(0.0D);
        JsonElement json = CurrencyAmount.LIST_CODEC.encodeStart(JsonOps.INSTANCE, values).getOrThrow();

        assertEquals(values, CurrencyAmount.LIST_CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void economyRowSurvivesTheNbtRoundTrip() {
        EconomyRow row = new EconomyRow(
                new EconomyRow.Clock(1200L, 600L, 48000L, 3L),
                new EconomyRow.Flow(values(0.0D), values(10.0D), values(20.0D)),
                new EconomyRow.Drivers(values(30.0D), values(40.0D), values(50.0D), values(60.0D), values(70.0D), values(80.0D)),
                new EconomyRow.CapacityValues(values(90.0D), values(100.0D)),
                7.5D, 1234, 5678,
                List.of(new EconomyRow.AttributionRow(600L, values(110.0D), values(120.0D), values(130.0D))));

        assertEquals(row, roundTrip(EconomyRow.CODEC, row));
    }

    @Test
    void anOldNamedFieldRowStillLoads() {
        EconomyRow row = EconomyRow.CODEC.parse(JsonOps.INSTANCE, oldRowJson()).getOrThrow();

        assertEquals(1200L, row.clock().marketTick());
        assertEquals(1.0D, CurrencyAmount.get(row.flow().supplyEwma(), Currencies.byId("dirt")), 0.0D);
        assertEquals(5.0D, CurrencyAmount.get(row.flow().supplyEwma(), Currencies.byId("netherite")), 0.0D);
        assertEquals(32.0D, CurrencyAmount.get(row.drivers().fundamental(), Currencies.byId("emerald")), 0.0D);
        assertEquals(94.0D, CurrencyAmount.get(row.capacity().current(), Currencies.byId("netherite")), 0.0D);
    }

    @Test
    void aMigratedRowEncodesAsAnIdKeyedList() {
        EconomyRow row = EconomyRow.CODEC.parse(JsonOps.INSTANCE, oldRowJson()).getOrThrow();
        JsonObject encoded = EconomyRow.CODEC.encodeStart(JsonOps.INSTANCE, row).getOrThrow().getAsJsonObject();
        JsonObject flow = encoded.getAsJsonObject("flow");

        assertInstanceOf(JsonArray.class, flow.get("supply"));
        JsonObject first = flow.getAsJsonArray("supply").get(0).getAsJsonObject();
        assertEquals(Currencies.byIndex(0).id(), first.get("currency").getAsString());
    }

    @Test
    void currencyAmountsRejectWrongLengthAndNonFiniteInput() {
        assertThrows(IllegalArgumentException.class, () -> CurrencyAmount.of(new double[] {1.0D, 2.0D, 3.0D, 4.0D}));
        assertThrows(IllegalArgumentException.class, () -> CurrencyAmount.of(new double[] {1.0D, 2.0D, 3.0D, 4.0D, 5.0D, 6.0D}));
        assertThrows(IllegalArgumentException.class, () -> new CurrencyAmount("gold", Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new CurrencyAmount("emerald", Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> new CurrencyAmount("", 1.0D));
    }

    @Test
    void economyRowRejectsNonFinitePlayerDayAndOverlongAttribution() {
        List<CurrencyAmount> values = CurrencyAmount.zero();
        EconomyRow.CapacityValues capacity = new EconomyRow.CapacityValues(values, values);
        List<EconomyRow.AttributionRow> tooMany = new ArrayList<>();
        for (int index = 0; index <= EconomyRow.ATTRIBUTION_CAPACITY; index++) {
            tooMany.add(new EconomyRow.AttributionRow(index, values, values, values));
        }

        assertThrows(IllegalArgumentException.class, () -> new EconomyRow(EconomyRow.genesis().clock(),
                EconomyRow.Flow.zero(), EconomyRow.Drivers.zero(), capacity, Double.NaN, 0, 0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new EconomyRow(EconomyRow.genesis().clock(),
                EconomyRow.Flow.zero(), EconomyRow.Drivers.zero(), capacity, -1.0D, 0, 0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new EconomyRow(EconomyRow.genesis().clock(),
                EconomyRow.Flow.zero(), EconomyRow.Drivers.zero(), capacity, 1.0D, 0, 0, tooMany));
    }

    private static List<CurrencyAmount> values(double base) {
        List<CurrencyAmount> amounts = new ArrayList<>();
        for (int index = 0; index < Currencies.count(); index++) {
            amounts.add(new CurrencyAmount(Currencies.byIndex(index).id(), base + index));
        }
        return List.copyOf(amounts);
    }

    /// The shape a save written before the id-keyed list holds: five named fields per currency-bearing value.
    private static JsonObject oldRowJson() {
        JsonObject row = new JsonObject();
        JsonObject clock = new JsonObject();
        clock.addProperty("market_tick", 1200L);
        clock.addProperty("bucket_start", 600L);
        clock.addProperty("next_day_tick", 48000L);
        clock.addProperty("rollover_day", 3L);
        row.add("clock", clock);
        JsonObject flow = new JsonObject();
        flow.add("supply", oldAmounts(1.0D));
        flow.add("demand", oldAmounts(10.0D));
        flow.add("pressure", oldAmounts(20.0D));
        row.add("flow", flow);
        JsonObject drivers = new JsonObject();
        drivers.add("fundamental", oldAmounts(30.0D));
        drivers.add("event", oldAmounts(40.0D));
        drivers.add("target", oldAmounts(50.0D));
        drivers.add("rate", oldAmounts(60.0D));
        drivers.add("applied", oldAmounts(70.0D));
        drivers.add("applied_last", oldAmounts(80.0D));
        row.add("drivers", drivers);
        JsonObject capacity = new JsonObject();
        capacity.add("current", oldAmounts(90.0D));
        capacity.add("next", oldAmounts(100.0D));
        row.add("capacity", capacity);
        row.addProperty("player_day_ewma", 7.5D);
        row.addProperty("player_ticks", 1234);
        row.addProperty("watched_ticks", 5678);
        row.add("attribution", new JsonArray());
        return row;
    }

    private static JsonObject oldAmounts(double base) {
        JsonObject amounts = new JsonObject();
        amounts.addProperty("dirt", base);
        amounts.addProperty("gold", base + 1.0D);
        amounts.addProperty("emerald", base + 2.0D);
        amounts.addProperty("diamond", base + 3.0D);
        amounts.addProperty("netherite", base + 4.0D);
        return amounts;
    }

    private static <T> T roundTrip(Codec<T> codec, T value) {
        CompoundTag tag = (CompoundTag) codec.encodeStart(NbtOps.INSTANCE, value).getOrThrow();
        return codec.parse(NbtOps.INSTANCE, tag).getOrThrow();
    }
}
