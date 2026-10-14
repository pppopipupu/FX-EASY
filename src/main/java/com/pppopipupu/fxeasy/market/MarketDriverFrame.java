package com.pppopipupu.fxeasy.market;

import module java.base;

/// What one tick of the world economy and the event layer hands the price process: the log change of each
/// currency's event component for this tick, and the risk floor the platform has publicly warned about. The
/// fundamental revaluation of the world is applied by the economy itself and never travels through this frame,
/// so the two channels cannot be confused in attribution.
public record MarketDriverFrame(double[] eventChange, double riskFloor) {

    /// A risk floor of one is a market with nothing announced, which is exactly the behaviour before this
    /// extension existed.
    public static final double NO_RISK_FLOOR = MarketEngine.REGIME_FLOOR;

    public MarketDriverFrame {
        eventChange = eventChange.clone();
        if (eventChange.length != Currencies.count()) {
            throw new IllegalArgumentException("driver frame needs one event change per currency");
        }
        for (double change : eventChange) {
            if (!Double.isFinite(change)) {
                throw new IllegalArgumentException("driver frame event change must be finite");
            }
        }
        if (!Double.isFinite(riskFloor) || riskFloor < NO_RISK_FLOOR) {
            throw new IllegalArgumentException("driver frame risk floor must be finite and at least the calm regime");
        }
    }

    public static MarketDriverFrame calm() {
        return new MarketDriverFrame(new double[Currencies.count()], NO_RISK_FLOOR);
    }

    public double eventChange(Currency currency) {
        return this.eventChange[Currencies.index(currency)];
    }
}
