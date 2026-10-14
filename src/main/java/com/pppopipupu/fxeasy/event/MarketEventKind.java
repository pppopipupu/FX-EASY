package com.pppopipupu.fxeasy.event;

import module java.base;

/// Which channel a market message belongs to. The three kinds are kept strictly apart: an observation report
/// never adds a price contribution of its own, a world event prices a bounded expectation about a state the
/// world actually reached, and a flavour event is small, bounded and often nothing but a joke.
public enum MarketEventKind {

    /// A report about production or terminal use the collector already committed. It quotes the contribution the
    /// fundamental component has already applied and adds nothing on top of it.
    OBSERVED_REPORT("observed_report"),
    /// A confirmed world state with a bounded expectation attached: a raid, a first boss kill, a weather spell.
    WORLD_EVENT("world_event"),
    /// An independent small event, filtered by preconditions, with a limited lifetime.
    FLAVOR_EVENT("flavor_event");

    private final String id;

    MarketEventKind(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public static MarketEventKind byId(String id) {
        for (MarketEventKind kind : values()) {
            if (kind.id.equals(id)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("unknown market event kind " + id);
    }
}
