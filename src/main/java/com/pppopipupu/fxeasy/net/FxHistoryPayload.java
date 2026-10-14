package com.pppopipupu.fxeasy.net;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import com.pppopipupu.fxeasy.market.TimedCandle;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/// The candle window of one pair and timeframe, as a full snapshot: every candle carries the market tick
/// interval it covers, so an event marker lands on the candle it belongs to instead of always on the newest
/// one. Closed candles and the forming candle travel in one list, and a repeat of the same snapshot replaces
/// the window rather than appending to it.
public record FxHistoryPayload(int pairIndex, int timeframeIndex, List<TimedCandle> candles, long asOfMarketTick)
        implements CustomPacketPayload {

    public static final Type<FxHistoryPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(FxEasy.MODID, "history"));
    public static final StreamCodec<FriendlyByteBuf, FxHistoryPayload> STREAM_CODEC = StreamCodec.of(FxHistoryPayload::encode, FxHistoryPayload::decode);

    private static void encode(FriendlyByteBuf buffer, FxHistoryPayload payload) {
        buffer.writeVarInt(payload.pairIndex);
        buffer.writeVarInt(payload.timeframeIndex);
        buffer.writeVarLong(payload.asOfMarketTick);
        FxStreamCodecs.TIMED_CANDLES.encode(buffer, payload.candles);
    }

    private static FxHistoryPayload decode(FriendlyByteBuf buffer) {
        int pairIndex = buffer.readVarInt();
        int timeframeIndex = buffer.readVarInt();
        long asOfMarketTick = buffer.readVarLong();
        return new FxHistoryPayload(pairIndex, timeframeIndex, FxStreamCodecs.TIMED_CANDLES.decode(buffer), asOfMarketTick);
    }

    @Override
    public Type<FxHistoryPayload> type() {
        return TYPE;
    }
}
