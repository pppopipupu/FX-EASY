package com.pppopipupu.fxeasy.event;

import module java.base;

/// How much the platform trusts one piece of market information. The terminal shows the quality next to every
/// message, and a rumour that turns out to be false gives back only the premium it had already applied.
public enum MarketEventQuality {

    /// The platform saw the fact itself, in the world it runs.
    CONFIRMED("confirmed"),
    /// The platform is modelling a consequence it has not observed directly.
    ESTIMATED("estimated"),
    /// Unverified market talk, announced as such.
    RUMOR("rumor"),
    /// A joke, marked as one, with no price contribution at all.
    ENTERTAINMENT("entertainment");

    private final String id;

    MarketEventQuality(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public static MarketEventQuality byId(String id) {
        for (MarketEventQuality quality : values()) {
            if (quality.id.equals(id)) {
                return quality;
            }
        }
        throw new IllegalArgumentException("unknown market event quality " + id);
    }
}
