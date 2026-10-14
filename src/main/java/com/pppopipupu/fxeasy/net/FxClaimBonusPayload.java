package com.pppopipupu.fxeasy.net;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/// The player asked for the welcome capital by clicking the button of the card that advertises it. The platform
/// pays it on this request and never again, so a client cannot collect it twice, and the account carries the flag
/// back in every snapshot.
public record FxClaimBonusPayload() implements CustomPacketPayload {

    public static final FxClaimBonusPayload INSTANCE = new FxClaimBonusPayload();

    public static final Type<FxClaimBonusPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(FxEasy.MODID, "claim_bonus"));

    public static final StreamCodec<FriendlyByteBuf, FxClaimBonusPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<FxClaimBonusPayload> type() {
        return TYPE;
    }
}
