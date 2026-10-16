package com.pppopipupu.fxeasy.net;

import module java.base;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.pppopipupu.fxeasy.market.Candle;
import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;
import com.pppopipupu.fxeasy.shop.Membership;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;

/// The chart draws volume bars and the hover readout reports the volume of the hovered candle, so a codec that
/// silently drops the field would only show up as missing bars.
class FxStreamCodecsTest {

    private static final Currency DIRT = Currencies.byId("dirt");
    private static final Currency GOLD = Currencies.byId("gold");
    private static final Currency EMERALD = Currencies.byId("emerald");

    @Test
    void candleRoundTripKeepsTheVolume() {
        Candle candle = new Candle(1.5D, 2.0D, 1.0D, 1.75D, 42.5D);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            FxStreamCodecs.CANDLE.encode(buffer, candle);
            assertEquals(candle, FxStreamCodecs.CANDLE.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void candleListRoundTripKeepsEveryField() {
        List<Candle> candles = List.of(
                new Candle(1.0D, 1.0D, 1.0D, 1.0D, 0.0D),
                new Candle(2.0D, 3.0D, 1.5D, 2.5D, 12.25D));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            FxStreamCodecs.CANDLES.encode(buffer, candles);
            assertEquals(candles, FxStreamCodecs.CANDLES.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    /// An order names the currency it locks its margin in and a position reports the one it settles in, so a codec
    /// that dropped the field would quietly lock the balance of the pair's quote instead.
    @Test
    void orderRoundTripKeepsTheMarginCurrency() {
        FxOrderPayload order = new FxOrderPayload(3, 1, 64, 100, Currencies.index(EMERALD));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            FxOrderPayload.STREAM_CODEC.encode(buffer, order);
            assertEquals(order, FxOrderPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    /// The account snapshot carries the credit record next to the balances, the welcome capital the platform
    /// handed out and the subscription the shop sold. Its codec is written out rather than composed, because the
    /// record has more fields than the composite overloads take, so a field that slipped out of order would only
    /// show up as a panel that reads a debt off the wrong currency, a welcome bonus in the wrong one or a
    /// subscription the platform never sold.
    @Test
    void accountRoundTripKeepsTheCreditRecordAndTheWelcomeCapital() {
        int count = Currencies.count();
        long[] balances = new long[count];
        long[] realized = new long[count];
        long[] freeMargin = new long[count];
        long[] debtSince = new long[count];
        int goldIndex = Currencies.index(GOLD);
        int dirtIndex = Currencies.index(DIRT);
        balances[goldIndex] = -3_000_000L;
        realized[goldIndex] = 12L;
        freeMargin[dirtIndex] = 5_000_000L;
        debtSince[goldIndex] = 4_000L;
        int welcomeCurrency = Currencies.index(EMERALD);
        long welcomeItems = 10L;
        FxAccountPayload payload = new FxAccountPayload(balances, realized, freeMargin,
                List.of(new FxPositionPayload(7L, 3, 1, 64, 100, 1.5D, 1.6D, 40_000L, 12L, 25L, 900L, dirtIndex)),
                true, 9L, true, true, true, "kurumi", 21, debtSince, true, welcomeCurrency, welcomeItems,
                Membership.PRO.id(), 40_000L, false, 12_000L, List.of());
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            FxAccountPayload.STREAM_CODEC.encode(buffer, payload);
            FxAccountPayload decoded = FxAccountPayload.STREAM_CODEC.decode(buffer);
            assertArrayEquals(balances, decoded.balances());
            assertArrayEquals(realized, decoded.realized());
            assertArrayEquals(freeMargin, decoded.freeMargin());
            assertArrayEquals(debtSince, decoded.debtSince());
            assertEquals(payload.positions(), decoded.positions());
            assertEquals(payload.marginCall(), decoded.marginCall());
            assertEquals(payload.closedTrades(), decoded.closedTrades());
            assertEquals(payload.opened(), decoded.opened());
            assertEquals(payload.tutorialSeen(), decoded.tutorialSeen());
            assertEquals(payload.bonusPaid(), decoded.bonusPaid());
            assertEquals(payload.holder(), decoded.holder());
            assertEquals(payload.age(), decoded.age());
            assertEquals(payload.blacklisted(), decoded.blacklisted());
            assertEquals(welcomeCurrency, decoded.welcomeCurrency());
            assertEquals(welcomeItems, decoded.welcomeItems());
            assertEquals(payload.membership(), decoded.membership());
            assertEquals(payload.membershipUntil(), decoded.membershipUntil());
            assertEquals(payload.autoRenew(), decoded.autoRenew());
            assertEquals(payload.loungeNext(), decoded.loungeNext());
            assertEquals(payload.lounge(), decoded.lounge());
        } finally {
            buffer.release();
        }
    }

    @Test
    void positionRoundTripKeepsTheMarginCurrency() {
        FxPositionPayload position = new FxPositionPayload(7L, 3, 1, 64, 100, 1.5D, 1.6D, 40_000L, 12L, 25L, 900L,
                Currencies.index(DIRT));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            FxPositionPayload.STREAM_CODEC.encode(buffer, position);
            assertEquals(position, FxPositionPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    /// The set of currencies has to travel with the session, definitions and all, so the client panel draws the
    /// set the server trades and indexes every price array the same way; a currency the platform cannot move in or
    /// out of an inventory travels with an empty item.
    @Test
    void currenciesRoundTripKeepsEveryDefinition() {
        List<Currency> definitions = new ArrayList<>(Currencies.defaults());
        definitions.add(new Currency("unbacked", "UNB", "Unbacked", Map.of("zh_cn", "无背书"), Optional.empty(), 1.0D,
                0.01D, 0.0D, 1L, 100.0D, 0.1D, 0.1D));
        FxCurrenciesPayload payload = FxCurrenciesPayload.of(definitions);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            FxCurrenciesPayload.STREAM_CODEC.encode(buffer, payload);
            FxCurrenciesPayload decoded = FxCurrenciesPayload.STREAM_CODEC.decode(buffer);
            assertEquals(payload, decoded);
            assertEquals(definitions, decoded.definitions());
        } finally {
            buffer.release();
        }
    }

    /// The pool of the lounge is a definition of the world like the currency table, so it travels with the
    /// session and the shop page writes out the very list the lounge draws from: a codec that dropped the weight
    /// of an entry would leave the page advertising shares that add up to something else, and one that dropped
    /// the potion would draw a gift the lounge would never hand over.
    @Test
    void loungePoolRoundTripKeepsEveryEntryAndItsWeight() {
        FxLoungePoolPayload payload = new FxLoungePoolPayload(24_000L, 10, List.of(
                new FxLoungePoolPayload.Entry("minecraft:enchanted_golden_apple", 1, 1, ""),
                new FxLoungePoolPayload.Entry("minecraft:potion", 2, 5, "minecraft:strong_healing")));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            FxLoungePoolPayload.STREAM_CODEC.encode(buffer, payload);
            FxLoungePoolPayload decoded = FxLoungePoolPayload.STREAM_CODEC.decode(buffer);
            assertEquals(payload, decoded);
            assertEquals(payload.entries(), decoded.pool().entries().stream()
                    .map(entry -> new FxLoungePoolPayload.Entry(entry.item().toString(), entry.count(),
                            entry.weight(), entry.potion().map(Identifier::toString).orElse("")))
                    .toList());
        } finally {
            buffer.release();
        }
    }
}
