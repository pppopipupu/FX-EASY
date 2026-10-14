package com.pppopipupu.fxeasy.net;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/// Sent when the player pays for a plan at the checkout. The plan travels by name rather than by ordinal, so a
/// client that does not know a tier this build sells cannot buy one by accident, and the currency the player
/// chose to settle it in travels by id as well. Nothing about the price travels: the server charges the price of
/// its own catalogue converted at its own live rate, whatever the client quoted.
public record FxShopBuyPayload(String plan, String currency) implements CustomPacketPayload {

    public static final Type<FxShopBuyPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(FxEasy.MODID, "shop_buy"));

    public static final StreamCodec<FriendlyByteBuf, FxShopBuyPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            FxShopBuyPayload::plan,
            ByteBufCodecs.STRING_UTF8,
            FxShopBuyPayload::currency,
            FxShopBuyPayload::new);

    @Override
    public Type<FxShopBuyPayload> type() {
        return TYPE;
    }
}
