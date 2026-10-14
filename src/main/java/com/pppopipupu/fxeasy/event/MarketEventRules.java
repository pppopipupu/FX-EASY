package com.pppopipupu.fxeasy.event;

import module java.base;

import com.pppopipupu.fxeasy.event.MarketEventDefinition.ImpactChannel;

/// The static rules the engine decides with, kept apart from the state it holds: whether a family is gated
/// behind a one shot milestone, whether it is allowed to price a channel at all, how long it takes to resolve,
/// and how much of its curve has been applied at a given tick.
///
/// A report of a fact the world already committed and every news only event are never priced, which is what
/// [prices] answers false for. A denied rumour is not stepped to zero either: [factor] gives the premium back
/// along the same curve the event applied it, so a refuted expectation ends exactly at zero and never
/// overshoots.
final class MarketEventRules {

    private MarketEventRules() {
    }

    static boolean gated(MarketEventDefinition definition) {
        String id = definition.id();
        return id.equals("dimension_expansion") || id.equals("dragon_unlock") || id.equals("boss_confidence");
    }

    static boolean prices(MarketEventDefinition definition) {
        return definition.channel() != ImpactChannel.NONE && definition.kind() != MarketEventKind.OBSERVED_REPORT;
    }

    static boolean denied(MarketEventState state) {
        return state.targetLog() < 0.0D;
    }

    static boolean resolved(MarketEventState state) {
        return state.appliedPeakTick() != Long.MIN_VALUE;
    }

    static long resolveTick(MarketEventDefinition definition, MarketEventState state) {
        return state.startTick() + definition.attackTicks() + definition.holdTicks();
    }

    static double factor(MarketEventDefinition definition, MarketEventState state, long tick) {
        if (state.retired()) {
            return 0.0D;
        }
        if (denied(state)) {
            long resolve = resolveTick(definition, state);
            return MarketEventState.phaseCurve(definition, resolve - state.startTick())
                    * (1.0D - givebackRamp(tick, resolve, state.endTick()));
        }
        return MarketEventState.phaseCurve(definition, tick - state.startTick());
    }

    private static double givebackRamp(long tick, long from, long to) {
        if (tick <= from) {
            return 0.0D;
        }
        if (tick >= to) {
            return 1.0D;
        }
        double u = (tick - from) / (double) (to - from);
        return u * u * (3.0D - 2.0D * u);
    }
}
