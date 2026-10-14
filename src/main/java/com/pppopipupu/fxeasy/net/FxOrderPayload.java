package com.pppopipupu.fxeasy.net;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/// Market order request. `units` is the size in items of the base currency of the pair, which is the lot the
/// terminal quotes it in, and `marginCurrencyOrdinal` the currency the order locks its margin in, which may be any
/// of them.
public record FxOrderPayload(int pairIndex, int sideOrdinal, int units, int leverage, int marginCurrencyOrdinal) implements CustomPacketPayload {

    public static final Type<FxOrderPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(FxEasy.MODID, "order"));
    public static final StreamCodec<FriendlyByteBuf, FxOrderPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            FxOrderPayload::pairIndex,
            ByteBufCodecs.VAR_INT,
            FxOrderPayload::sideOrdinal,
            ByteBufCodecs.VAR_INT,
            FxOrderPayload::units,
            ByteBufCodecs.VAR_INT,
            FxOrderPayload::leverage,
            ByteBufCodecs.VAR_INT,
            FxOrderPayload::marginCurrencyOrdinal,
            FxOrderPayload::new);

    @Override
    public Type<FxOrderPayload> type() {
        return TYPE;
    }
}
