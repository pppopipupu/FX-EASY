package com.pppopipupu.fxeasy.net;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/// One open position as the terminal displays it: `units` is the size in items of the base currency of the pair
/// and every amount is in micro units of the currency the position named as its margin.
public record FxPositionPayload(long id, int pairIndex, int sideOrdinal, int units, int leverage, double openPrice, double markPrice, long marginMicro, long swapMicro, long pnlMicro, long openMarketTick, int marginCurrencyOrdinal) implements CustomPacketPayload {

    public static final Type<FxPositionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(FxEasy.MODID, "position"));
    public static final StreamCodec<FriendlyByteBuf, FxPositionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG,
            FxPositionPayload::id,
            ByteBufCodecs.VAR_INT,
            FxPositionPayload::pairIndex,
            ByteBufCodecs.VAR_INT,
            FxPositionPayload::sideOrdinal,
            ByteBufCodecs.VAR_INT,
            FxPositionPayload::units,
            ByteBufCodecs.VAR_INT,
            FxPositionPayload::leverage,
            ByteBufCodecs.DOUBLE,
            FxPositionPayload::openPrice,
            ByteBufCodecs.DOUBLE,
            FxPositionPayload::markPrice,
            ByteBufCodecs.VAR_LONG,
            FxPositionPayload::marginMicro,
            ByteBufCodecs.VAR_LONG,
            FxPositionPayload::swapMicro,
            ByteBufCodecs.VAR_LONG,
            FxPositionPayload::pnlMicro,
            ByteBufCodecs.VAR_LONG,
            FxPositionPayload::openMarketTick,
            ByteBufCodecs.VAR_INT,
            FxPositionPayload::marginCurrencyOrdinal,
            FxPositionPayload::new);

    @Override
    public Type<FxPositionPayload> type() {
        return TYPE;
    }
}
