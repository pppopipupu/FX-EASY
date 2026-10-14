package com.pppopipupu.fxeasy.net;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/// The player left the first run walkthrough, by finishing it or by skipping it. The platform then stops
/// offering it: the flag lives in the trading account and comes back in every account snapshot, so it survives
/// a restart and follows the player to another client.
public record FxTutorialSeenPayload() implements CustomPacketPayload {

    public static final FxTutorialSeenPayload INSTANCE = new FxTutorialSeenPayload();

    public static final Type<FxTutorialSeenPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(FxEasy.MODID, "tutorial_seen"));

    public static final StreamCodec<FriendlyByteBuf, FxTutorialSeenPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<FxTutorialSeenPayload> type() {
        return TYPE;
    }
}
