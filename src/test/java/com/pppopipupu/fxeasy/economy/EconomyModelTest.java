package com.pppopipupu.fxeasy.economy;

import module java.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;

class EconomyModelTest {

    private static final Currency DIRT = Currencies.byId("dirt");
    private static final Currency GOLD = Currencies.byId("gold");
    private static final Currency NETHERITE = Currencies.byId("netherite");

    @Test
    void goldNuggetsNormaliseAsNinthIngotsNotWholeIngots() {
        EconomyBucket.Mutable gold = new EconomyBucket.Mutable();
        gold.addSupply(EconomySourceKind.PIGLIN_GOLD, GOLD, 128L);
        EconomyBucket bucket = gold.seal();
        double capacity = GOLD.capacity();
        double perDay = (double) EconomyConstants.TICKS_PER_DAY
                / ((double) EconomyConstants.TICKS_PER_BUCKET * capacity);

        assertEquals(128.0D / 9.0D, EconomyModel.itemUnits(bucket.supplyAtoms(GOLD), GOLD), 1e-12);
        assertEquals(128.0D / 9.0D * perDay, EconomyModel.normalizedSupply(bucket, GOLD, capacity), 1e-12);
        assertNotEquals(128.0D * perDay, EconomyModel.normalizedSupply(bucket, GOLD, capacity));
    }

    @Test
    void netheriteScrapNormalisesAsQuarterIngots() {
        EconomyBucket.Mutable netherite = new EconomyBucket.Mutable();
        netherite.addSupply(EconomySourceKind.NATURAL_ORE, NETHERITE, 128L);
        EconomyBucket bucket = netherite.seal();
        double capacity = NETHERITE.capacity();

        assertEquals(32.0D, EconomyModel.itemUnits(bucket.supplyAtoms(NETHERITE), NETHERITE), 1e-12);
        assertEquals(32.0D * (double) EconomyConstants.TICKS_PER_DAY
                / ((double) EconomyConstants.TICKS_PER_BUCKET * capacity),
                EconomyModel.normalizedSupply(bucket, NETHERITE, capacity), 1e-12);
    }

    @Test
    void aBucketProducingAtExactlyOneCapacityPerDayNormalisesToOne() {
        double capacity = 4000.0D;
        for (Currency currency : Currencies.all()) {
            long atoms = (long) (capacity / EconomyConstants.BUCKETS_PER_DAY * currency.atomsPerItem());
            EconomyBucket.Mutable supply = new EconomyBucket.Mutable();
            supply.addSupply(EconomySourceKind.NATURAL_ORE, currency, atoms);
            assertEquals(1.0D, EconomyModel.normalizedSupply(supply.seal(), currency, capacity), 1e-12, currency.name());
        }
    }

    @Test
    void aBucketProducingAFullDayOfCapacityInOneBucketNormalisesToForty() {
        double capacity = GOLD.capacity();
        EconomyBucket.Mutable supply = new EconomyBucket.Mutable();
        supply.addSupply(EconomySourceKind.NATURAL_ORE, GOLD, (long) capacity * GOLD.atomsPerItem());
        assertEquals((double) EconomyConstants.BUCKETS_PER_DAY,
                EconomyModel.normalizedSupply(supply.seal(), GOLD, capacity), 1e-9);
    }

    @Test
    void pressureTargetSaturatesAndLetsDemandOffsetSupply() {
        assertEquals(0.0D, EconomyModel.pressureTarget(0.0D, 0.0D), 0.0D);
        assertEquals(Math.log1p(16.0D) - 0.6D * Math.log1p(10.0D), EconomyModel.pressureTarget(16.0D, 10.0D), 1e-12);
        assertEquals(0.0D, EconomyModel.pressureTarget(1.0D, 1000.0D), 0.0D);
        assertEquals(Math.log1p(4.0D), EconomyModel.pressureTarget(4.0D, 0.0D), 1e-12);
    }

    @Test
    void fundamentalTargetSaturatesAtThePerCurrencyWeights() {
        assertEquals(-GOLD.supplyWeight() * Math.tanh(2.0D),
                EconomyModel.fundamentalTarget(2.0D, 0.0D, GOLD), 1e-12);
        assertEquals(-DIRT.supplyWeight() * Math.tanh(2.0D),
                EconomyModel.fundamentalTarget(2.0D, 0.0D, DIRT), 1e-12);
        assertEquals(-GOLD.supplyWeight(),
                EconomyModel.fundamentalTarget(1000.0D, 0.0D, GOLD), 1e-12);
        assertEquals(GOLD.demandWeight(),
                EconomyModel.fundamentalTarget(0.0D, 1000.0D, GOLD), 1e-12);
        assertEquals(DIRT.demandWeight(),
                EconomyModel.fundamentalTarget(0.0D, 1000.0D, DIRT), 1e-12);
        assertEquals(EconomyConstants.SUPPLY_WEIGHT, GOLD.supplyWeight(), 0.0D);
        assertEquals(EconomyConstants.DEMAND_WEIGHT, GOLD.demandWeight(), 0.0D);
    }

    @Test
    void revaluationRateSpreadsTheBucketStepAndNeverOvershoots() {
        double capped = EconomyModel.revaluationRate(-1.0D, 0.0D);
        assertEquals(-EconomyConstants.MAX_FUNDAMENTAL_STEP_PER_BUCKET / EconomyConstants.TICKS_PER_BUCKET, capped, 1e-15);
        assertEquals(-EconomyConstants.MAX_FUNDAMENTAL_STEP_PER_BUCKET, capped * EconomyConstants.TICKS_PER_BUCKET, 1e-15);

        double withinReach = EconomyModel.revaluationRate(-0.0002D, 0.0D);
        assertEquals(-0.0002D / EconomyConstants.TICKS_PER_BUCKET, withinReach, 1e-15);

        assertEquals(0.0D, EconomyModel.revaluationRate(0.3D, 0.3D), 0.0D);
        assertTrue(Math.abs(EconomyModel.revaluationRate(0.3D, -0.3D)) <= EconomyConstants.MAX_FUNDAMENTAL_STEP_PER_BUCKET / EconomyConstants.TICKS_PER_BUCKET);
    }
}
