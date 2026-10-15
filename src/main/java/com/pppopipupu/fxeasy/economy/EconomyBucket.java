package com.pppopipupu.fxeasy.economy;

import module java.base;

import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;

/// Committed world facts of one economy bucket, in integer atom units and with a fixed source breakdown. One
/// bucket is [EconomyConstants#TICKS_PER_BUCKET] market ticks of real, already committed operations: an entry
/// here is a fact that happened, never a forecast. Atoms are the smallest indivisible unit of a currency - a
/// gold nugget is one atom and a gold ingot is nine - so a count is exact and a price never weights it. Terminal
/// use keeps one total per currency and one breakdown per source kind, because how reversible a use was decides
/// what demand it may pay.
public record EconomyBucket(long[] supplyAtoms, long[][] terminalUseAtoms, int[] sourceCounts) {

    public EconomyBucket {
        supplyAtoms = supplyAtoms.clone();
        terminalUseAtoms = cloneMatrix(terminalUseAtoms);
        sourceCounts = sourceCounts.clone();
        if (supplyAtoms.length != Currencies.count() || terminalUseAtoms.length != EconomySourceKind.COUNT) {
            throw new IllegalArgumentException("economy bucket needs one atom count per currency");
        }
        for (long[] row : terminalUseAtoms) {
            if (row.length != Currencies.count()) {
                throw new IllegalArgumentException("economy bucket needs one atom count per currency");
            }
        }
        if (sourceCounts.length != EconomySourceKind.COUNT) {
            throw new IllegalArgumentException("economy bucket needs one count per source kind");
        }
        for (int index = 0; index < Currencies.count(); index++) {
            if (supplyAtoms[index] < 0L) {
                throw new IllegalArgumentException("economy bucket cannot hold negative atoms");
            }
        }
        for (long[] row : terminalUseAtoms) {
            for (long atoms : row) {
                if (atoms < 0L) {
                    throw new IllegalArgumentException("economy bucket cannot hold negative atoms");
                }
            }
        }
        for (int count : sourceCounts) {
            if (count < 0) {
                throw new IllegalArgumentException("economy bucket cannot hold negative source counts");
            }
        }
    }

    public static EconomyBucket empty() {
        return new EconomyBucket(new long[Currencies.count()], new long[EconomySourceKind.COUNT][Currencies.count()],
                new int[EconomySourceKind.COUNT]);
    }

    public long supplyAtoms(Currency currency) {
        return this.supplyAtoms[Currencies.index(currency)];
    }

    public long terminalUseAtoms(EconomySourceKind kind, Currency currency) {
        return this.terminalUseAtoms[kind.ordinal()][Currencies.index(currency)];
    }

    public long terminalUseAtoms(Currency currency) {
        long total = 0L;
        for (long[] row : this.terminalUseAtoms) {
            total += row[Currencies.index(currency)];
        }
        return total;
    }

    private static long[][] cloneMatrix(long[][] source) {
        long[][] copy = new long[source.length][];
        for (int index = 0; index < source.length; index++) {
            copy[index] = source[index].clone();
        }
        return copy;
    }

    /// Accumulator of the bucket that is currently open. The service owns one per server and seals it at the
    /// bucket boundary, so nothing here is persisted: a save in the middle of a bucket keeps the counts through
    /// the runtime state, and a reload reopens the same bucket.
    public static final class Mutable {

        private final long[] supplyAtoms = new long[Currencies.count()];
        private final long[][] terminalUseAtoms = new long[EconomySourceKind.COUNT][Currencies.count()];
        private final int[] sourceCounts = new int[EconomySourceKind.COUNT];

        /// Records committed primary supply. Only server thread, non negative, and only for a kind that is
        /// declared primary supply.
        public void addSupply(EconomySourceKind kind, Currency currency, long atoms) {
            require(kind.primarySupply(), kind, "is not a primary supply source");
            this.supplyAtoms[Currencies.index(currency)] = add(this.supplyAtoms[Currencies.index(currency)], atoms);
            this.sourceCounts[kind.ordinal()] = increment(this.sourceCounts[kind.ordinal()]);
        }

        /// Records a committed terminal use. Only server thread, non negative, and only for a kind that is
        /// declared terminal use.
        public void addTerminalUse(EconomySourceKind kind, Currency currency, long atoms) {
            require(kind.terminalUse(), kind, "is not a terminal use source");
            this.terminalUseAtoms[kind.ordinal()][Currencies.index(currency)] =
                    add(this.terminalUseAtoms[kind.ordinal()][Currencies.index(currency)], atoms);
            this.sourceCounts[kind.ordinal()] = increment(this.sourceCounts[kind.ordinal()]);
        }

        public void addActivity(EconomySourceKind kind) {
            require(kind.activity(), kind, "is not an activity source");
            this.sourceCounts[kind.ordinal()] = increment(this.sourceCounts[kind.ordinal()]);
        }

        public boolean isEmpty() {
            for (int index = 0; index < Currencies.count(); index++) {
                if (this.supplyAtoms[index] != 0L) {
                    return false;
                }
            }
            for (long[] row : this.terminalUseAtoms) {
                for (long atoms : row) {
                    if (atoms != 0L) {
                        return false;
                    }
                }
            }
            for (int count : this.sourceCounts) {
                if (count != 0) {
                    return false;
                }
            }
            return true;
        }

        /// Freezes the bucket and resets the accumulator, which is the boundary the service runs once per
        /// [#EconomyConstants#TICKS_PER_BUCKET] ticks.
        public EconomyBucket seal() {
            EconomyBucket bucket = new EconomyBucket(this.supplyAtoms, this.terminalUseAtoms, this.sourceCounts);
            Arrays.fill(this.supplyAtoms, 0L);
            for (long[] row : this.terminalUseAtoms) {
                Arrays.fill(row, 0L);
            }
            Arrays.fill(this.sourceCounts, 0);
            return bucket;
        }

        private static void require(boolean holds, EconomySourceKind kind, String message) {
            if (!holds) {
                throw new IllegalArgumentException("economy source kind " + kind.id() + " " + message);
            }
        }

        private static long add(long total, long atoms) {
            if (atoms < 0L) {
                throw new IllegalArgumentException("economy atoms cannot be negative");
            }
            if (Long.MAX_VALUE - total < atoms) {
                throw new IllegalArgumentException("economy bucket atom total overflowed");
            }
            return total + atoms;
        }

        private static int increment(int count) {
            return count == Integer.MAX_VALUE ? count : count + 1;
        }
    }
}
