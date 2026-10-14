package com.pppopipupu.fxeasy.net;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import com.pppopipupu.fxeasy.shop.LoungeFile;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/// The pool the lounge of the world hands its gifts out of, as the shop page writes it beside the tier that opens
/// it: the market ticks between two gifts, how many the lounge may hold and every item it draws from with the
/// weight that item carries. The pool is a file of the world rather than something a client decides, so it
/// travels with the session the way the currency table does and a panel never writes out a pool the platform it
/// is connected to does not run. Every bound of the file is already applied to what is sent, so the entries here
/// are exactly the ones the lounge itself may draw.
public record FxLoungePoolPayload(long refreshTicks, int capacity, List<Entry> entries) implements CustomPacketPayload {

    public static final Type<FxLoungePoolPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(FxEasy.MODID, "lounge_pool"));
    public static final StreamCodec<FriendlyByteBuf, FxLoungePoolPayload> STREAM_CODEC = StreamCodec.of(FxLoungePoolPayload::encode, FxLoungePoolPayload::decode);
    /// The longest identifier a pool entry may name, which is what keeps a malformed payload from asking for a
    /// string the page could never resolve.
    private static final int MAX_ID_LENGTH = 128;

    public FxLoungePoolPayload {
        entries = List.copyOf(entries);
        if (entries.size() > LoungeFile.MAX_ENTRIES) {
            throw new IllegalArgumentException("a pool holds at most " + LoungeFile.MAX_ENTRIES + " entries");
        }
    }

    public static FxLoungePoolPayload of(LoungeFile.Pool pool) {
        return new FxLoungePoolPayload(pool.refreshTicks(), pool.capacity(),
                pool.entries().stream().map(Entry::of).toList());
    }

    /// The pool as the page reads it, held to the bounds of the file again because a payload is whatever the
    /// server on the other end sent.
    public LoungeFile.Pool pool() {
        return new LoungeFile.Pool(this.refreshTicks, this.capacity,
                this.entries.stream().map(Entry::toEntry).toList());
    }

    private static void encode(FriendlyByteBuf buffer, FxLoungePoolPayload payload) {
        buffer.writeVarLong(payload.refreshTicks);
        buffer.writeVarInt(payload.capacity);
        buffer.writeVarInt(payload.entries.size());
        for (Entry entry : payload.entries) {
            buffer.writeUtf(entry.item(), MAX_ID_LENGTH);
            buffer.writeVarInt(entry.count());
            buffer.writeVarInt(entry.weight());
            buffer.writeUtf(entry.potion(), MAX_ID_LENGTH);
        }
    }

    private static FxLoungePoolPayload decode(FriendlyByteBuf buffer) {
        long refreshTicks = buffer.readVarLong();
        int capacity = buffer.readVarInt();
        int size = buffer.readVarInt();
        if (size < 0 || size > LoungeFile.MAX_ENTRIES) {
            throw new IllegalArgumentException("invalid pool entry count " + size);
        }
        List<Entry> entries = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            entries.add(new Entry(buffer.readUtf(MAX_ID_LENGTH), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readUtf(MAX_ID_LENGTH)));
        }
        return new FxLoungePoolPayload(refreshTicks, capacity, entries);
    }

    @Override
    public Type<FxLoungePoolPayload> type() {
        return TYPE;
    }

    /// One entry as the wire carries it: the item, how many of it a gift holds, the weight it is drawn by and the
    /// potion the stack carries when the item is a potion, which has no item id of its own. An empty potion is an
    /// entry that names none.
    public record Entry(String item, int count, int weight, String potion) {

        static Entry of(LoungeFile.Entry entry) {
            return new Entry(entry.item().toString(), entry.count(), entry.weight(),
                    entry.potion().map(Identifier::toString).orElse(""));
        }

        LoungeFile.Entry toEntry() {
            return new LoungeFile.Entry(Identifier.parse(this.item), this.count, this.weight,
                    this.potion.isEmpty() ? Optional.empty() : Optional.of(Identifier.parse(this.potion)));
        }
    }
}
