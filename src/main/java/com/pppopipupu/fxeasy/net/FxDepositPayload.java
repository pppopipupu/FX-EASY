package com.pppopipupu.fxeasy.net;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/// Moves items of one currency from the inventory into the trading account, one item being one unit.
public record FxDepositPayload(int currencyOrdinal, long units) implements CustomPacketPayload {

    public static final Type<FxDepositPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(FxEasy.MODID, "deposit"));
    public static final StreamCodec<FriendlyByteBuf, FxDepositPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            FxDepositPayload::currencyOrdinal,
            ByteBufCodecs.VAR_LONG,
            FxDepositPayload::units,
            FxDepositPayload::new);

    @Override
    public Type<FxDepositPayload> type() {
        return TYPE;
    }
}
