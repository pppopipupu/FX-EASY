package com.pppopipupu.fxeasy.net;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/// Sent when the player turns the automatic renewal of their subscription off or on again. Cancelling never
/// takes back a period that was already paid: it stops the platform from charging the next one, and the plan
/// runs to the end of the period it is in.
public record FxAutoRenewPayload(boolean enabled) implements CustomPacketPayload {

    public static final Type<FxAutoRenewPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(FxEasy.MODID, "auto_renew"));

    public static final StreamCodec<FriendlyByteBuf, FxAutoRenewPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            FxAutoRenewPayload::enabled,
            FxAutoRenewPayload::new);

    @Override
    public Type<FxAutoRenewPayload> type() {
        return TYPE;
    }
}
