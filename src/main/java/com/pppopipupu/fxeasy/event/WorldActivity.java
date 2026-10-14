package com.pppopipupu.fxeasy.event;

import module java.base;

import com.pppopipupu.fxeasy.economy.EconomySourceKind;
import com.pppopipupu.fxeasy.market.Currency;

/// One committed world fact the collector hands the event layer. A world event is only ever started from a fact
/// recorded here, never from a scan of the world. The kind names the source, the dimension id names where it
/// happened (a string, so this record stays free of Minecraft types), the market tick says when, the atom count
/// is the amount the fact carried, and the currency is set when the fact concerns exactly one currency (a natural
/// diamond find versus a generic ore find). The record exists to tell facts apart; it never carries a region or
/// player identity, because a family cooldown is always per world.
public record WorldActivity(EconomySourceKind kind, String dimension, long marketTick, long atoms, Currency currency) {

    public WorldActivity {
        Objects.requireNonNull(kind, "world activity source kind");
        if (marketTick < 0L) {
            throw new IllegalArgumentException("world activity tick cannot be negative");
        }
        if (atoms < 0L) {
            throw new IllegalArgumentException("world activity atom count cannot be negative");
        }
    }

    public static WorldActivity of(EconomySourceKind kind, long marketTick) {
        return new WorldActivity(kind, null, marketTick, 0L, null);
    }

    public static WorldActivity of(EconomySourceKind kind, Currency currency, long marketTick, long atoms) {
        return new WorldActivity(kind, null, marketTick, atoms, currency);
    }

    public static WorldActivity inDimension(EconomySourceKind kind, String dimension, long marketTick) {
        return new WorldActivity(kind, dimension, marketTick, 0L, null);
    }
}
