package com.pppopipupu.fxeasy.storage;

import module java.base;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.pppopipupu.fxeasy.event.MarketEventState;

/// Persisted market event layer: the events that are running or have just finished, the family cooldowns that
/// stop one world from rerunning the same story, and the one off milestones a world can only reach once. A save
/// in the middle of an event resumes on the same tick of the same curve, with the same message identity and the
/// same remaining budget.
public record EventRow(List<MarketEventState> events, List<CooldownRow> cooldowns, List<String> milestones, long revision) {

    /// Events kept after they ended, newest last. A bounded history is all the terminal shows.
    public static final int HISTORY_CAPACITY = 128;
    public static final long COOLDOWN_RETENTION_TICKS = 20L * 24000L;

    public static final Codec<EventRow> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            MarketEventState.CODEC.listOf().fieldOf("events").forGetter(EventRow::events),
            CooldownRow.CODEC.listOf().fieldOf("cooldowns").forGetter(EventRow::cooldowns),
            Codec.STRING.listOf().fieldOf("milestones").forGetter(EventRow::milestones),
            Codec.LONG.fieldOf("revision").forGetter(EventRow::revision)).apply(instance, EventRow::new));

    public EventRow {
        if (events.size() > HISTORY_CAPACITY) {
            throw new IllegalArgumentException("event history is bounded to " + HISTORY_CAPACITY + " entries");
        }
        events = List.copyOf(events);
        cooldowns = List.copyOf(cooldowns);
        milestones = List.copyOf(milestones);
    }

    public static EventRow empty() {
        return new EventRow(List.of(), List.of(), List.of(), 0L);
    }

    public record CooldownRow(String family, long readyTick, int runs) {

        public static final Codec<CooldownRow> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("family").forGetter(CooldownRow::family),
                Codec.LONG.fieldOf("ready_tick").forGetter(CooldownRow::readyTick),
                Codec.INT.fieldOf("runs").forGetter(CooldownRow::runs)).apply(instance, CooldownRow::new));
    }
}
