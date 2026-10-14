package com.pppopipupu.fxeasy.net;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/// Sent when the player takes one gift out of the VIP lounge. The gift travels as the position it holds in the
/// lounge the account snapshot listed, which is the only order both sides agree on; a claim for a position the
/// lounge does not hold is answered with the account as it stands.
public record FxLoungeClaimPayload(int index) implements CustomPacketPayload {

    public static final Type<FxLoungeClaimPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(FxEasy.MODID, "lounge_claim"));

    public static final StreamCodec<FriendlyByteBuf, FxLoungeClaimPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            FxLoungeClaimPayload::index,
            FxLoungeClaimPayload::new);

    @Override
    public Type<FxLoungeClaimPayload> type() {
        return TYPE;
    }
}
