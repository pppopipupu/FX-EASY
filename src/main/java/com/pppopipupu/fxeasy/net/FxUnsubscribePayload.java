package com.pppopipupu.fxeasy.net;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/// Sent when the terminal closes, so the server stops streaming market data.
public record FxUnsubscribePayload() implements CustomPacketPayload {

    public static final Type<FxUnsubscribePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(FxEasy.MODID, "unsubscribe"));
    public static final StreamCodec<FriendlyByteBuf, FxUnsubscribePayload> STREAM_CODEC = StreamCodec.unit(new FxUnsubscribePayload());

    @Override
    public Type<FxUnsubscribePayload> type() {
        return TYPE;
    }
}
