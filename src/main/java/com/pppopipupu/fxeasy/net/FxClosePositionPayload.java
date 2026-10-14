package com.pppopipupu.fxeasy.net;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record FxClosePositionPayload(long positionId) implements CustomPacketPayload {

    public static final Type<FxClosePositionPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(FxEasy.MODID, "close_position"));
    public static final StreamCodec<FriendlyByteBuf, FxClosePositionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG,
            FxClosePositionPayload::positionId,
            FxClosePositionPayload::new);

    @Override
    public Type<FxClosePositionPayload> type() {
        return TYPE;
    }
}
