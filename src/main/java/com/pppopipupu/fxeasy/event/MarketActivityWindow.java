package com.pppopipupu.fxeasy.event;

import module java.base;

import com.pppopipupu.fxeasy.economy.EconomyConstants;
import com.pppopipupu.fxeasy.economy.EconomySourceKind;
import com.pppopipupu.fxeasy.market.Currency;

/// The bounded window of committed world facts the event layer decides from. Every fact the tracker commits is
/// forwarded through [MarketEventEngine#observe] and lands here; the engine reads the window back only through
/// the queries below and never scans the world itself. The window holds at most [CAPACITY] facts and never
/// reaches further back than [WINDOW_TICKS] market ticks, so a fact the engine reacted to long ago cannot keep
/// a family alive: [#prune] drops whatever fell out of the window before the engine's queries run, and
/// [#removeIf] drops the facts an event was started from so the same fact cannot start a second one.
///
/// The window also remembers every dimension a fact was recorded in. That set is not bounded by the window: a
/// dimension the players have travelled to stays known, which is what lets an expansion be priced exactly once.
final class MarketActivityWindow {

    private static final long WINDOW_TICKS = EconomyConstants.TICKS_PER_DAY;
    private static final int CAPACITY = 4096;
    private static final String OVERWORLD = "minecraft:overworld";

    private final ArrayDeque<WorldActivity> recent = new ArrayDeque<>();
    private final Set<String> dimensions = new HashSet<>();

    void observe(WorldActivity fact) {
        this.recent.addLast(fact);
        while (this.recent.size() > CAPACITY) {
            this.recent.removeFirst();
        }
        if (fact.dimension() != null) {
            this.dimensions.add(fact.dimension());
        }
    }

    /// Oldest market tick a query still sees when the engine is advancing `marketTick`.
    long windowStart(long marketTick) {
        return marketTick - WINDOW_TICKS;
    }

    void prune(long marketTick) {
        long since = windowStart(marketTick);
        while (!this.recent.isEmpty() && this.recent.peekFirst().marketTick() < since) {
            this.recent.removeFirst();
        }
    }

    void removeIf(Predicate<WorldActivity> filter) {
        this.recent.removeIf(filter);
    }

    boolean hasRecent(EconomySourceKind kind, long since, Currency currency) {
        for (WorldActivity fact : this.recent) {
            if (fact.marketTick() < since || fact.kind() != kind) {
                continue;
            }
            if (currency != null && fact.currency() != currency) {
                continue;
            }
            return true;
        }
        return false;
    }

    int countRecent(EconomySourceKind kind, long since) {
        int count = 0;
        for (WorldActivity fact : this.recent) {
            if (fact.marketTick() >= since && fact.kind() == kind) {
                count++;
            }
        }
        return count;
    }

    /// The dimension the world has reached but never priced as an expansion. The overworld is where every world
    /// starts, so logging into it is not an expansion: only a second dimension the players actually travelled to
    /// is worth a supply expectation.
    String nextUnmilestonedDimension(long since, Set<String> milestones) {
        for (WorldActivity fact : this.recent) {
            if (fact.kind() != EconomySourceKind.DIMENSION_ENTERED || fact.marketTick() < since
                    || fact.dimension() == null || OVERWORLD.equals(fact.dimension())) {
                continue;
            }
            if (!milestones.contains("dimension:" + fact.dimension())) {
                return fact.dimension();
            }
        }
        return null;
    }

    boolean containsDimension(String dimension) {
        return this.dimensions.contains(dimension);
    }

    boolean hasAnyDimension() {
        return !this.dimensions.isEmpty();
    }
}
