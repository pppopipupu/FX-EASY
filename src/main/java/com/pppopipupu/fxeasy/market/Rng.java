package com.pppopipupu.fxeasy.market;

import module java.base;

/// SplitMix64 based generator. The whole state is one long, so market randomness survives a save/load cycle
/// bit for bit and tests can pin a seed.
public final class Rng {

    private long state;

    public Rng(long seed) {
        this.state = seed;
    }

    public long state() {
        return this.state;
    }

    public long nextLong() {
        this.state += 0x9E3779B97F4A7C15L;
        long z = this.state;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /// Uniform in [0, 1).
    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    public boolean nextChance(double probability) {
        return probability > 0.0D && nextDouble() < probability;
    }

    /// Standard normal via Box-Muller.
    public double nextGaussian() {
        double u1 = Math.max(nextDouble(), Double.MIN_NORMAL);
        double u2 = nextDouble();
        return Math.sqrt(-2.0D * Math.log(u1)) * Math.cos(2.0D * Math.PI * u2);
    }
}
