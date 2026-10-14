package com.pppopipupu.fxeasy.event;

import module java.base;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;

/// State of one running or archived market event. The curve is defined by when the event was announced and how
/// long its three phases are, so a save in the middle of an event resumes on exactly the tick it was at: nothing
/// is re-rolled and no phase is paid twice.
public record MarketEventState(String definitionId, long announcementTick, long startTick, long appliedPeakTick,
        long endTick, double appliedLog, double targetLog, boolean archived, boolean retired) {

    public static final Codec<MarketEventState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("definition").forGetter(MarketEventState::definitionId),
            Codec.LONG.fieldOf("announced").forGetter(MarketEventState::announcementTick),
            Codec.LONG.fieldOf("start").forGetter(MarketEventState::startTick),
            Codec.LONG.fieldOf("peak_tick").forGetter(MarketEventState::appliedPeakTick),
            Codec.LONG.fieldOf("end").forGetter(MarketEventState::endTick),
            Codec.DOUBLE.fieldOf("applied").forGetter(MarketEventState::appliedLog),
            Codec.DOUBLE.fieldOf("target").forGetter(MarketEventState::targetLog),
            Codec.BOOL.optionalFieldOf("archived", false).forGetter(MarketEventState::archived),
            Codec.BOOL.optionalFieldOf("retired", false).forGetter(MarketEventState::retired)).apply(instance, MarketEventState::new));

    public MarketEventState {
        if (!Double.isFinite(appliedLog) || !Double.isFinite(targetLog)) {
            throw new IllegalArgumentException("event contributions must be finite");
        }
        if (appliedLog < 0.0D) {
            throw new IllegalArgumentException("applied contribution cannot be negative");
        }
    }

    public static MarketEventState announced(MarketEventDefinition definition, long announcementTick) {
        return new MarketEventState(definition.id(), announcementTick, announcementTick, Long.MIN_VALUE,
                announcementTick + definition.lifetimeTicks(), 0.0D, totalBudget(definition), false, false);
    }

    /// Total log magnitude one full run of the definition may ever apply, across every currency.
    public static double totalBudget(MarketEventDefinition definition) {
        double budget = 0.0D;
        for (Currency currency : Currencies.all()) {
            budget += Math.abs(definition.peakImpact(currency));
        }
        return budget;
    }

    /// Cumulative share of the whole event applied `elapsed` ticks after its start: zero at the start and one
    /// at the end of the release. The curve rises through a smooth attack, holds at its peak and releases
    /// smoothly, and it is normalised by the area it actually sweeps, `0.5 * attack + hold + 0.5 * release`, so
    /// a full run applies exactly the target once however long each phase is. The caller passes an elapsed time
    /// rather than a tick, because the same curve is what a denied event gives back along.
    public static double phaseCurve(MarketEventDefinition definition, long elapsed) {
        long attack = definition.attackTicks();
        long hold = definition.holdTicks();
        long release = definition.releaseTicks();
        long total = attack + hold + release;
        if (elapsed <= 0L) {
            return 0.0D;
        }
        if (elapsed >= total) {
            return 1.0D;
        }
        double swept = 0.5D * attack + hold + 0.5D * release;
        if (elapsed <= attack && attack > 0L) {
            return attack * smoothstepIntegral(elapsed / (double) attack) / swept;
        }
        if (elapsed <= attack + hold) {
            return (0.5D * attack + (elapsed - attack)) / swept;
        }
        double u = (elapsed - attack - hold) / (double) release;
        return (0.5D * attack + hold + release * (u - smoothstepIntegral(u))) / swept;
    }

    /// Integral of the smoothstep shape from 0 to `u`, which is also the share of a phase it has swept.
    private static double smoothstepIntegral(double u) {
        return u * u * u * (1.0D - 0.5D * u);
    }

    /// Largest log contribution this event will ever apply on one currency, used to publish the risk the market
    /// has been warned about.
    public double peakMagnitude(MarketEventDefinition definition) {
        double largest = 0.0D;
        for (Currency currency : Currencies.all()) {
            largest = Math.max(largest, Math.abs(definition.peakImpact(currency)));
        }
        return largest;
    }

    public boolean covers(long marketTick) {
        return marketTick >= this.startTick && marketTick < this.endTick;
    }

    public MarketEventState archive() {
        return new MarketEventState(this.definitionId, this.announcementTick, this.startTick, this.appliedPeakTick,
                this.endTick, this.appliedLog, this.targetLog, true, this.retired);
    }
}
