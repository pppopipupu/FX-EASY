package com.pppopipupu.fxeasy.event;

import module java.base;

import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;

/// One market event family: what it is, which channel it prices through, how large its whole contribution may
/// be, and how long it lasts. Definitions are immutable tables in [MarketEventCatalog]; the state of a running
/// event lives in [MarketEventState] and is persisted. An event definition never carries a random outcome: the
/// outcome is decided once by the event generator and then only ever published.
public record MarketEventDefinition(String id, MarketEventKind kind, MarketEventQuality quality, ImpactChannel channel,
        long attackTicks, long holdTicks, long releaseTicks, long cooldownTicks, double[] weight, double[] peakImpact,
        String titleKey, String bodyKey) {

    /// Which part of the economy an event prices through. The channel decides what the terminal says about the
    /// event and how the engine is allowed to use it.
    public enum ImpactChannel {
        /// Production, terminal use or inventory pressure the world itself produced. Priced by the fundamental
        /// component, never by the event layer.
        SUPPLY("supply"),
        /// A real use of a currency that reabsorbs supply.
        USE("use"),
        /// The cost or convenience of trading itself, e.g. a trade network that is disrupted.
        LIQUIDITY("liquidity"),
        /// An expectation about the future, which a later fact can confirm or deny.
        EXPECTATION("expectation"),
        /// News only. Every price contribution of such an event is exactly zero.
        NONE("none");

        private final String id;

        ImpactChannel(String id) {
            this.id = id;
        }

        public String id() {
            return this.id;
        }

        public static ImpactChannel byId(String id) {
            for (ImpactChannel channel : values()) {
                if (channel.id.equals(id)) {
                    return channel;
                }
            }
            throw new IllegalArgumentException("unknown market event channel " + id);
        }
    }

    public MarketEventDefinition {
        weight = weight.clone();
        peakImpact = peakImpact.clone();
        if (weight.length != Currencies.count() || peakImpact.length != Currencies.count()) {
            throw new IllegalArgumentException("event definition needs one entry per currency");
        }
        if (attackTicks < 0L || holdTicks < 0L || releaseTicks < 0L || cooldownTicks < 0L) {
            throw new IllegalArgumentException("event durations cannot be negative");
        }
        if (attackTicks + holdTicks + releaseTicks <= 0L) {
            throw new IllegalArgumentException("an event with no lifetime cannot be priced");
        }
        for (int index = 0; index < Currencies.count(); index++) {
            if (!Double.isFinite(weight[index]) || weight[index] < 0.0D) {
                throw new IllegalArgumentException("event weights must be finite and non negative");
            }
            if (!Double.isFinite(peakImpact[index]) || Math.abs(peakImpact[index]) > 0.02D) {
                throw new IllegalArgumentException("an event may not price more than 2% on one currency");
            }
        }
        if (channel == ImpactChannel.NONE) {
            for (double impact : peakImpact) {
                if (impact != 0.0D) {
                    throw new IllegalArgumentException("a news only event cannot carry a price impact");
                }
            }
        }
    }

    /// Total lifetime of one run of the event, in market ticks.
    public long lifetimeTicks() {
        return this.attackTicks + this.holdTicks + this.releaseTicks;
    }

    /// Whether this event may move a price at all.
    public boolean prices() {
        return this.channel != ImpactChannel.NONE;
    }

    /// Relative probability of being picked among the eligible candidates of its family set. Zero means the
    /// family never fires on its own.
    public double weight(Currency currency) {
        return this.weight[Currencies.index(currency)];
    }

    public double peakImpact(Currency currency) {
        return this.peakImpact[Currencies.index(currency)];
    }
}
