package com.pppopipupu.fxeasy.economy;

import module java.base;

/// Calibration of the world economy. Every number here is a starting value to be calibrated against a running
/// server, not a measurement: the model is deliberately slow, saturating and bounded so a factory changes the
/// level a currency trades at instead of granting a permanent daily return.
public final class EconomyConstants {

    /// Market ticks in one economic day. One market tick is one advanced server tick, so a day is a day whether
    /// or not anyone slept.
    public static final int TICKS_PER_DAY = 24000;
    /// Market ticks in one economy bucket. A day is 40 buckets.
    public static final int TICKS_PER_BUCKET = 600;
    public static final int BUCKETS_PER_DAY = TICKS_PER_DAY / TICKS_PER_BUCKET;

    /// Time constant of the flow estimate, in market ticks. A bucket of unusual production moves it, but not
    /// all the way: a factory is judged on its sustained output.
    public static final double FLOW_TAU = 4800.0D;
    /// Time constant of the inventory pressure on the way up and on the way down. Pressure builds faster than
    /// it clears, which is why a factory that stops does not immediately restore the old valuation.
    public static final double PRESSURE_RISE_TAU = 12000.0D;
    public static final double PRESSURE_FALL_TAU = 48000.0D;

    /// Share of the demand side that offsets supply pressure inside the pressure term. A terminal use takes some
    /// of the new supply out of the market but never all of it.
    public static final double DEMAND_OFFSET = 0.60D;
    /// Log units of fundamental revaluation per unit of saturating supply pressure and of demand. Which weight
    /// a currency reacts with is its own definition.
    public static final double SUPPLY_WEIGHT = 0.35D;
    public static final double DEMAND_WEIGHT = 0.08D;
    /// Bounds on the fundamental component of one currency, in log units: a 39% discount at the worst and a
    /// 28% premium at the best.
    public static final double MIN_FUNDAMENTAL = -0.50D;
    public static final double MAX_FUNDAMENTAL = 0.25D;
    /// Most the fundamental component may move in one bucket, in log units. Applied tick by tick across the
    /// bucket, never as a step at the boundary.
    public static final double MAX_FUNDAMENTAL_STEP_PER_BUCKET = 0.0015D;

    /// Total absolute log change the fundamentals and the event layer together may apply in one bucket. This is
    /// the budget both channels draw from, so a bullish event cannot be used to make room for a bearish one.
    public static final double MAX_DRIVER_STEP_PER_BUCKET = 0.0020D;

    /// Capacity scales with the players actually playing, measured as online player ticks over the last
    /// economic day. A ten second login cannot move it, and neither can one player logging off.
    public static final double MAX_CAPACITY_STEP_PER_DAY = 0.10D;

    private EconomyConstants() {
    }

    /// Exponential moving average factor for one bucket of a process with the given time constant in ticks.
    public static double bucketAlpha(double tau) {
        return 1.0D - Math.exp(-(double) TICKS_PER_BUCKET / tau);
    }
}
