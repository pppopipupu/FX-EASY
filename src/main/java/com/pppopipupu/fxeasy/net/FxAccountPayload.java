package com.pppopipupu.fxeasy.net;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/// Full account snapshot: per currency balances, realised profit and free margin, the open positions and the
/// margin state the platform acts on. Amounts are micro units of one item of the currency, indexed in the order
/// the currency table of this session names; nothing here is summed, because a sum over currencies is the
/// client's to take in whichever currency the player reads the panel in. `riskRoom` is what the exposure quota of
/// the account still leaves it in items of that currency, which is what the size the ticket builds is bounded by
/// as well, and `inf` while the platform limits no account. `debtSince` carries the market tick each currency's
/// debt started at, zero while that balance is not in the red, and `blacklisted` is the platform's own verdict on
/// the credit record, so the panel never has to derive it.
///
/// The subscription travels by name, with the market tick its period ends at, whether the platform renews it by
/// itself, and what the lounge of the account is holding: the gifts are sent as stacks in the order the lounge
/// holds them, which is the order a claim addresses them by, and an empty stack is a gift this build can no
/// longer build rather than a hole the client has to guess about.
///
/// The codec is written out rather than composed, because the record carries more fields than the composite
/// overloads take.
public record FxAccountPayload(long[] balances, long[] realized, long[] freeMargin, double[] riskRoom, List<FxPositionPayload> positions, boolean marginCall, long closedTrades, boolean opened, boolean tutorialSeen, boolean bonusPaid, String holder, int age, long[] debtSince, boolean blacklisted, int welcomeCurrency, long welcomeItems, String membership, long membershipUntil, boolean autoRenew, long loungeNext, List<ItemStack> lounge) implements CustomPacketPayload {

    public static final Type<FxAccountPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(FxEasy.MODID, "account"));

    private static final StreamCodec<RegistryFriendlyByteBuf, List<FxPositionPayload>> POSITIONS = StreamCodec.of(
            (buffer, positions) -> {
                buffer.writeVarInt(positions.size());
                for (FxPositionPayload position : positions) {
                    FxPositionPayload.STREAM_CODEC.encode(buffer, position);
                }
            },
            buffer -> {
                int size = buffer.readVarInt();
                List<FxPositionPayload> positions = new ArrayList<>(size);
                for (int index = 0; index < size; index++) {
                    positions.add(FxPositionPayload.STREAM_CODEC.decode(buffer));
                }
                return positions;
            });

    private static final StreamCodec<RegistryFriendlyByteBuf, List<ItemStack>> LOUNGE = ItemStack.OPTIONAL_LIST_STREAM_CODEC;

    public static final StreamCodec<RegistryFriendlyByteBuf, FxAccountPayload> STREAM_CODEC = StreamCodec.of(
            FxAccountPayload::write,
            FxAccountPayload::read);

    private static void write(RegistryFriendlyByteBuf buffer, FxAccountPayload payload) {
        FxStreamCodecs.LONG_ARRAY.encode(buffer, payload.balances());
        FxStreamCodecs.LONG_ARRAY.encode(buffer, payload.realized());
        FxStreamCodecs.LONG_ARRAY.encode(buffer, payload.freeMargin());
        FxStreamCodecs.DOUBLE_ARRAY.encode(buffer, payload.riskRoom());
        POSITIONS.encode(buffer, payload.positions());
        buffer.writeBoolean(payload.marginCall());
        buffer.writeVarLong(payload.closedTrades());
        buffer.writeBoolean(payload.opened());
        buffer.writeBoolean(payload.tutorialSeen());
        buffer.writeBoolean(payload.bonusPaid());
        ByteBufCodecs.STRING_UTF8.encode(buffer, payload.holder());
        buffer.writeVarInt(payload.age());
        FxStreamCodecs.LONG_ARRAY.encode(buffer, payload.debtSince());
        buffer.writeBoolean(payload.blacklisted());
        buffer.writeVarInt(payload.welcomeCurrency());
        buffer.writeVarLong(payload.welcomeItems());
        ByteBufCodecs.STRING_UTF8.encode(buffer, payload.membership());
        buffer.writeVarLong(payload.membershipUntil());
        buffer.writeBoolean(payload.autoRenew());
        buffer.writeVarLong(payload.loungeNext());
        LOUNGE.encode(buffer, payload.lounge());
    }

    private static FxAccountPayload read(RegistryFriendlyByteBuf buffer) {
        long[] balances = FxStreamCodecs.LONG_ARRAY.decode(buffer);
        long[] realized = FxStreamCodecs.LONG_ARRAY.decode(buffer);
        long[] freeMargin = FxStreamCodecs.LONG_ARRAY.decode(buffer);
        double[] riskRoom = FxStreamCodecs.DOUBLE_ARRAY.decode(buffer);
        List<FxPositionPayload> positions = POSITIONS.decode(buffer);
        boolean marginCall = buffer.readBoolean();
        long closedTrades = buffer.readVarLong();
        boolean opened = buffer.readBoolean();
        boolean tutorialSeen = buffer.readBoolean();
        boolean bonusPaid = buffer.readBoolean();
        String holder = ByteBufCodecs.STRING_UTF8.decode(buffer);
        int age = buffer.readVarInt();
        long[] debtSince = FxStreamCodecs.LONG_ARRAY.decode(buffer);
        boolean blacklisted = buffer.readBoolean();
        int welcomeCurrency = buffer.readVarInt();
        long welcomeItems = buffer.readVarLong();
        String membership = ByteBufCodecs.STRING_UTF8.decode(buffer);
        long membershipUntil = buffer.readVarLong();
        boolean autoRenew = buffer.readBoolean();
        long loungeNext = buffer.readVarLong();
        List<ItemStack> lounge = LOUNGE.decode(buffer);
        return new FxAccountPayload(balances, realized, freeMargin, riskRoom, positions, marginCall,
                closedTrades, opened, tutorialSeen, bonusPaid, holder, age, debtSince, blacklisted, welcomeCurrency,
                welcomeItems, membership, membershipUntil, autoRenew, loungeNext, lounge);
    }

    @Override
    public Type<FxAccountPayload> type() {
        return TYPE;
    }
}
