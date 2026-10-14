package com.pppopipupu.fxeasy.net;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/// Result of an action: either a rejection reason or a confirmation, both as translation keys.
public record FxNoticePayload(String key, String argument, boolean error) implements CustomPacketPayload {

    public static final Type<FxNoticePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(FxEasy.MODID, "notice"));
    public static final StreamCodec<FriendlyByteBuf, FxNoticePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            FxNoticePayload::key,
            ByteBufCodecs.STRING_UTF8,
            FxNoticePayload::argument,
            ByteBufCodecs.BOOL,
            FxNoticePayload::error,
            FxNoticePayload::new);

    @Override
    public Type<FxNoticePayload> type() {
        return TYPE;
    }
}
