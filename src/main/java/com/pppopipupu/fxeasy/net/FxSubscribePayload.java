package com.pppopipupu.fxeasy.net;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record FxSubscribePayload(int pairIndex, int timeframeIndex) implements CustomPacketPayload {

    public static final Type<FxSubscribePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(FxEasy.MODID, "subscribe"));
    public static final StreamCodec<FriendlyByteBuf, FxSubscribePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            FxSubscribePayload::pairIndex,
            ByteBufCodecs.VAR_INT,
            FxSubscribePayload::timeframeIndex,
            FxSubscribePayload::new);

    @Override
    public Type<FxSubscribePayload> type() {
        return TYPE;
    }
}
