package com.pppopipupu.fxeasy.economy;

import module java.base;

import com.pppopipupu.fxeasy.market.Currency;

/// Normalisation of committed world facts into the numbers the price process is driven by. Nothing here touches
/// Minecraft or a level, and nothing here holds state: it turns one bucket of atoms into a flow estimate, an
/// inventory pressure and a bounded fundamental revaluation target. The properties the market depends on are
/// that production saturates instead of compounding, that stopping production clears pressure slowly, and that
/// a sustained output settles at a new level instead of falling every day forever.
public final class EconomyModel {

    /// Weight one terminal use is judged with, by source kind rather than by currency: a barter or an emerald
    /// the player hands back to a villager is reversible and must never mint revaluation on its own, while a
    /// crafting, crafter or smithing input is a net irreversible consumption of the currency.
    public static double terminalUseWeight(EconomySourceKind kind) {
        return switch (kind) {
            case CRAFT_TERMINAL_USE, CRAFTER_TERMINAL_USE, SMITHING_TERMINAL_USE -> 1.0D;
            case BARTER_TERMINAL_USE -> 0.35D;
            case VILLAGER_REABSORB -> 0.25D;
            default -> 0.0D;
        };
    }

    public static final double FLOW_ALPHA = EconomyConstants.bucketAlpha(EconomyConstants.FLOW_TAU);
    public static final double PRESSURE_RISE_ALPHA = EconomyConstants.bucketAlpha(EconomyConstants.PRESSURE_RISE_TAU);
    public static final double PRESSURE_FALL_ALPHA = EconomyConstants.bucketAlpha(EconomyConstants.PRESSURE_FALL_TAU);

    private EconomyModel() {
    }

    /// Normalised primary supply flow of one bucket, in capacity units per economic day. Atoms are converted to
    /// items first, so a bucket that produced exactly one capacity worth of items normalises to `1.0`.
    public static double normalizedSupply(EconomyBucket bucket, Currency currency, double capacity) {
        return itemUnits(bucket.supplyAtoms(currency), currency) * (double) EconomyConstants.TICKS_PER_DAY
                / ((double) EconomyConstants.TICKS_PER_BUCKET * capacity);
    }

    /// Normalised weighted terminal use of one bucket, in capacity units per economic day. Each source kind
    /// carries its own reversibility weight, so a round trip through a villager cannot pay the same demand as a
    /// recipe that consumed the currency for good.
    public static double normalizedDemand(EconomyBucket bucket, Currency currency, double capacity) {
        double items = 0.0D;
        for (EconomySourceKind kind : EconomySourceKind.ALL) {
            double weight = terminalUseWeight(kind);
            if (weight == 0.0D) {
                continue;
            }
            items += itemUnits(bucket.terminalUseAtoms(kind, currency), currency) * weight;
        }
        return items * (double) EconomyConstants.TICKS_PER_DAY
                / ((double) EconomyConstants.TICKS_PER_BUCKET * capacity);
    }

    /// Atoms of one currency expressed in whole items, which is the unit capacity is quoted in.
    public static double itemUnits(long atoms, Currency currency) {
        return (double) atoms / currency.atomsPerItem();
    }

    /// Pressure target of one bucket: saturating supply against a fraction of the demand that offsets it.
    public static double pressureTarget(double supply, double demand) {
        return Math.max(0.0D, Math.log1p(Math.max(0.0D, supply))
                - EconomyConstants.DEMAND_OFFSET * Math.log1p(Math.max(0.0D, demand)));
    }

    /// Demand left after the supply it offsets, in the same normalised units. The pressure term already counts
    /// a share of every terminal use against the supply, so only what is left over is real demand: a villager
    /// round trip that hands back exactly what it just paid cannot revalue the currency up.
    public static double residualDemand(double supply, double demand) {
        return Math.max(0.0D, Math.max(0.0D, demand) - Math.max(0.0D, supply) / EconomyConstants.DEMAND_OFFSET);
    }

    /// Fundamental revaluation target of one currency, in log units: negative under supply pressure, positive
    /// under real demand, clamped to the band the model is allowed to reach. The weights are the currency's own.
    public static double fundamentalTarget(double pressure, double demand, Currency currency) {
        double target = -currency.supplyWeight() * Math.tanh(pressure)
                + currency.demandWeight() * Math.tanh(Math.max(0.0D, demand));
        return Math.clamp(target, EconomyConstants.MIN_FUNDAMENTAL, EconomyConstants.MAX_FUNDAMENTAL);
    }

    /// Per tick revaluation rate that walks the applied fundamental component towards its target inside one
    /// bucket, capped so a bucket can never take a step larger than the model allows.
    public static double revaluationRate(double target, double applied) {
        double remaining = target - applied;
        return Math.clamp(remaining, -EconomyConstants.MAX_FUNDAMENTAL_STEP_PER_BUCKET,
                EconomyConstants.MAX_FUNDAMENTAL_STEP_PER_BUCKET) / EconomyConstants.TICKS_PER_BUCKET;
    }
}
