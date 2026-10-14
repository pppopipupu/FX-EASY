package com.pppopipupu.fxeasy.net;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record FxCloseAllPayload() implements CustomPacketPayload {

    public static final Type<FxCloseAllPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(FxEasy.MODID, "close_all"));
    public static final StreamCodec<FriendlyByteBuf, FxCloseAllPayload> STREAM_CODEC = StreamCodec.unit(new FxCloseAllPayload());

    @Override
    public Type<FxCloseAllPayload> type() {
        return TYPE;
    }
}
