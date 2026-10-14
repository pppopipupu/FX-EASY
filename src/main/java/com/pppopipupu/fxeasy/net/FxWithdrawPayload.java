package com.pppopipupu.fxeasy.net;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/// Turns free margin back into items in the inventory, one item being one unit.
public record FxWithdrawPayload(int currencyOrdinal, long units) implements CustomPacketPayload {

    public static final Type<FxWithdrawPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(FxEasy.MODID, "withdraw"));
    public static final StreamCodec<FriendlyByteBuf, FxWithdrawPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            FxWithdrawPayload::currencyOrdinal,
            ByteBufCodecs.VAR_LONG,
            FxWithdrawPayload::units,
            FxWithdrawPayload::new);

    @Override
    public Type<FxWithdrawPayload> type() {
        return TYPE;
    }
}
