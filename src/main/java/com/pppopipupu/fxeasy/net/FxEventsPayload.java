package com.pppopipupu.fxeasy.net;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/// The public market message feed: what the platform has announced, what it has actually applied so far, and
/// which currencies each message concerns. Nothing private travels here - no random outcome that has not been
/// published, no coordinates, no player identity - and the revision lets a client replace an older copy of the
/// same root event instead of stacking a second card for it.
public record FxEventsPayload(long revision, long asOfMarketTick, List<Event> events) implements CustomPacketPayload {

    /// Most messages one payload may carry. The server sends the newest ones; a client that has fallen behind
    /// gets a full snapshot on its next subscription rather than an unbounded catch up list.
    public static final int MAX_EVENTS = 128;
    public static final int MAX_ID_LENGTH = 64;
    public static final int MAX_KEY_LENGTH = 96;

    public static final Type<FxEventsPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(FxEasy.MODID, "events"));
    public static final StreamCodec<FriendlyByteBuf, FxEventsPayload> STREAM_CODEC = StreamCodec.of(FxEventsPayload::encode, FxEventsPayload::decode);

    public FxEventsPayload {
        if (events.size() > MAX_EVENTS) {
            throw new IllegalArgumentException("event feed is bounded to " + MAX_EVENTS + " messages");
        }
        events = List.copyOf(events);
    }

    /// One announced message, as the terminal shows it.
    public record Event(String eventId, String rootId, int kind, int quality, int channel, long revision,
            long announcedTick, long endTick, double[] applied, double[] peak, String titleKey, String bodyKey,
            boolean archived) {

        public Event {
            applied = applied.clone();
            peak = peak.clone();
        }

        /// Whether this message concerns that currency at all, either because it has applied something or because
        /// it has a target to apply.
        public boolean concerns(int currencyOrdinal) {
            return this.applied[currencyOrdinal] != 0.0D || this.peak[currencyOrdinal] != 0.0D;
        }
    }

    private static void encode(FriendlyByteBuf buffer, FxEventsPayload payload) {
        buffer.writeVarLong(payload.revision);
        buffer.writeVarLong(payload.asOfMarketTick);
        buffer.writeVarInt(payload.events.size());
        for (Event event : payload.events) {
            buffer.writeUtf(event.eventId, MAX_ID_LENGTH);
            buffer.writeUtf(event.rootId, MAX_ID_LENGTH);
            buffer.writeVarInt(event.kind);
            buffer.writeVarInt(event.quality);
            buffer.writeVarInt(event.channel);
            buffer.writeVarLong(event.revision);
            buffer.writeVarLong(event.announcedTick);
            buffer.writeVarLong(event.endTick);
            FxStreamCodecs.DOUBLE_ARRAY.encode(buffer, event.applied);
            FxStreamCodecs.DOUBLE_ARRAY.encode(buffer, event.peak);
            buffer.writeUtf(event.titleKey, MAX_KEY_LENGTH);
            buffer.writeUtf(event.bodyKey, MAX_KEY_LENGTH);
            buffer.writeBoolean(event.archived);
        }
    }

    private static FxEventsPayload decode(FriendlyByteBuf buffer) {
        long revision = buffer.readVarLong();
        long asOfMarketTick = buffer.readVarLong();
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_EVENTS) {
            throw new IllegalArgumentException("event feed out of bounds: " + size);
        }
        List<Event> events = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            events.add(new Event(
                    buffer.readUtf(MAX_ID_LENGTH),
                    buffer.readUtf(MAX_ID_LENGTH),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarLong(),
                    buffer.readVarLong(),
                    buffer.readVarLong(),
                    FxStreamCodecs.DOUBLE_ARRAY.decode(buffer),
                    FxStreamCodecs.DOUBLE_ARRAY.decode(buffer),
                    buffer.readUtf(MAX_KEY_LENGTH),
                    buffer.readUtf(MAX_KEY_LENGTH),
                    buffer.readBoolean()));
        }
        return new FxEventsPayload(revision, asOfMarketTick, events);
    }

    @Override
    public Type<FxEventsPayload> type() {
        return TYPE;
    }
}
