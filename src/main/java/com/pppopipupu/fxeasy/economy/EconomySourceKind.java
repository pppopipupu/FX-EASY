package com.pppopipupu.fxeasy.economy;

import module java.base;

/// Where one committed world fact came from. A source kind is a fixed, small classification that the terminal
/// may name, never a per-operation token: it says which channel produced the atoms, not who produced them. The
/// kinds are split between supply, terminal use and activity, so an adapter can never decide on its own how a
/// fact is priced - the weights live in [EconomyModel] and the event catalog, not in the collector.
public enum EconomySourceKind {

    /// Gold the world created: zombie piglin loot that was actually committed to the level.
    PIGLIN_GOLD("piglin_gold", Kind.PRIMARY_SUPPLY),
    /// Emerald a villager paid the player: new player usable currency.
    VILLAGER_PAYS_EMERALD("villager_pays_emerald", Kind.PRIMARY_SUPPLY),
    /// Any other currency a villager paid the player.
    VILLAGER_PAYS_CURRENCY("villager_pays_currency", Kind.PRIMARY_SUPPLY),
    /// Natural ore taken out of the ground and actually committed, valued at usable resource equivalence.
    NATURAL_ORE("natural_ore", Kind.PRIMARY_SUPPLY),
    /// Dirt dug from natural ground, the one primary source with its own much lower supply weight.
    DIRT_DIG("dirt_dig", Kind.PRIMARY_SUPPLY),
    /// A net irreversible crafting input the player actually took out of a result slot.
    CRAFT_TERMINAL_USE("craft_terminal_use", Kind.TERMINAL_USE),
    /// A net irreversible input a crafter block actually consumed.
    CRAFTER_TERMINAL_USE("crafter_terminal_use", Kind.TERMINAL_USE),
    /// A net irreversible input a smithing table actually consumed.
    SMITHING_TERMINAL_USE("smithing_terminal_use", Kind.TERMINAL_USE),
    /// Gold a piglin actually accepted for one barter.
    BARTER_TERMINAL_USE("barter_terminal_use", Kind.TERMINAL_USE),
    /// Currency the player handed a villager, which is reabsorption rather than permanent destruction.
    VILLAGER_REABSORB("villager_reabsorb", Kind.TERMINAL_USE),
    /// A raid started in a level that holds a trade region with recent real trades.
    RAID_STARTED("raid_started", Kind.ACTIVITY),
    /// A raid ended, whichever way it went.
    RAID_ENDED("raid_ended", Kind.ACTIVITY),
    /// A trading villager that had recently traded with a player died.
    TRADE_VILLAGER_DIED("trade_villager_died", Kind.ACTIVITY),
    /// A player broke a block inside a trade region that holds recent real trades.
    TRADE_REGION_ACTIVITY("trade_region_activity", Kind.ACTIVITY),
    /// A player placed a block inside a trade region that holds recent real trades.
    TRADE_REGION_BUILD("trade_region_build", Kind.ACTIVITY),
    /// The ender dragon was killed for the first time in this world.
    DRAGON_DEFEATED("dragon_defeated", Kind.ACTIVITY),
    /// The wither was killed for the first time in this world.
    WITHER_DEFEATED("wither_defeated", Kind.ACTIVITY),
    /// A player first entered a dimension of this world.
    DIMENSION_ENTERED("dimension_entered", Kind.ACTIVITY),
    /// Rain or a thunderstorm started in a level that can have weather.
    WEATHER_STARTED("weather_started", Kind.ACTIVITY),
    /// Weather ended in a level that can have weather.
    WEATHER_ENDED("weather_ended", Kind.ACTIVITY),
    /// A currency item was destroyed without a use: lava, explosions, despawn. Reporting only, it never enters
    /// a price channel.
    LOSS("loss", Kind.ACTIVITY),
    /// A transfer between holders was observed. It contributes nothing; the kind exists so adapters can report
    /// what they deliberately ignored.
    TRANSFER("transfer", Kind.ACTIVITY);

    private enum Kind {
        PRIMARY_SUPPLY,
        TERMINAL_USE,
        ACTIVITY
    }

    private final String id;
    private final Kind kind;

    EconomySourceKind(String id, Kind kind) {
        this.id = id;
        this.kind = kind;
    }

    public static final List<EconomySourceKind> ALL = List.of(values());
    public static final int COUNT = ALL.size();

    /// Stable id used in logs, in the report payload and in the saved state.
    public String id() {
        return this.id;
    }

    /// Whether this kind feeds new usable supply into the economy.
    public boolean primarySupply() {
        return this.kind == Kind.PRIMARY_SUPPLY;
    }

    /// Whether this kind is a terminal use that reabsorbs supply.
    public boolean terminalUse() {
        return this.kind == Kind.TERMINAL_USE;
    }

    /// Whether this kind is activity evidence only: it can qualify an event but never moves a price by itself.
    public boolean activity() {
        return this.kind == Kind.ACTIVITY;
    }

    public static EconomySourceKind byId(String id) {
        for (EconomySourceKind kind : ALL) {
            if (kind.id.equals(id)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("unknown economy source kind " + id);
    }
}
