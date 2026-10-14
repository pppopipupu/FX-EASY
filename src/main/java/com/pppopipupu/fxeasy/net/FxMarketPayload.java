package com.pppopipupu.fxeasy.net;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import com.pppopipupu.fxeasy.market.Candle;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/// Live market tick: the price of every currency as the platform quotes it, the monotonic market clock, the
/// volatility regime of the subscribed pair, the candle it is currently forming and the market tick the new
/// candle format starts at. A price here is only meaningful against another price of the same array - the rate
/// of two currencies is the ratio of their entries - and the array is indexed in the order the currency table of
/// this session names. The clock is what the client samples its listing window on, so the window keeps running
/// whether or not the terminal is open.
public record FxMarketPayload(double[] prices, long marketTick, double regime, Candle forming, long chartEpoch)
        implements CustomPacketPayload {

    public static final Type<FxMarketPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(FxEasy.MODID, "market"));
    public static final StreamCodec<FriendlyByteBuf, FxMarketPayload> STREAM_CODEC = StreamCodec.of(FxMarketPayload::encode, FxMarketPayload::decode);

    private static void encode(FriendlyByteBuf buffer, FxMarketPayload payload) {
        FxStreamCodecs.DOUBLE_ARRAY.encode(buffer, payload.prices);
        buffer.writeVarLong(payload.marketTick);
        buffer.writeDouble(payload.regime);
        buffer.writeBoolean(payload.forming != null);
        if (payload.forming != null) {
            FxStreamCodecs.CANDLE.encode(buffer, payload.forming);
        }
        buffer.writeVarLong(payload.chartEpoch);
    }

    private static FxMarketPayload decode(FriendlyByteBuf buffer) {
        double[] prices = FxStreamCodecs.DOUBLE_ARRAY.decode(buffer);
        long marketTick = buffer.readVarLong();
        double regime = buffer.readDouble();
        Candle forming = buffer.readBoolean() ? FxStreamCodecs.CANDLE.decode(buffer) : null;
        long chartEpoch = buffer.readVarLong();
        return new FxMarketPayload(prices, marketTick, regime, forming, chartEpoch);
    }

    @Override
    public Type<FxMarketPayload> type() {
        return TYPE;
    }
}
