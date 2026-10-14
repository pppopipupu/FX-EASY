package com.pppopipupu.fxeasy.net;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/// The player filled in the registration and accepted the account opening in the terminal. The platform then
/// stops asking: the flag, the name and the age live in the trading account and come back in every account
/// snapshot, so they survive a restart and follow the player to another client. Opening the account is the only
/// thing the client ever asks for that does not carry an amount.
public record FxAccountOpenPayload(String holder, int age) implements CustomPacketPayload {

    public static final Type<FxAccountOpenPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(FxEasy.MODID, "account_open"));

    public static final StreamCodec<FriendlyByteBuf, FxAccountOpenPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            FxAccountOpenPayload::holder,
            ByteBufCodecs.VAR_INT,
            FxAccountOpenPayload::age,
            FxAccountOpenPayload::new);

    @Override
    public Type<FxAccountOpenPayload> type() {
        return TYPE;
    }
}
