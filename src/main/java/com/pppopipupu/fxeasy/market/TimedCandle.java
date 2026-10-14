package com.pppopipupu.fxeasy.market;

import module java.base;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/// One candle with the half open market tick interval it covers, `[startTick, endTick)`. The chart needs the
/// interval to place an event marker on the candle it actually belongs to; a candle without a time anchor can
/// only ever be drawn as the newest one.
public record TimedCandle(long startTick, long endTick, Candle candle) {

    public static final Codec<TimedCandle> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("start").forGetter(TimedCandle::startTick),
            Codec.LONG.fieldOf("end").forGetter(TimedCandle::endTick),
            Candle.CODEC.fieldOf("candle").forGetter(TimedCandle::candle)).apply(instance, TimedCandle::new));

    public TimedCandle {
        if (endTick <= startTick) {
            throw new IllegalArgumentException("a candle interval must be non empty");
        }
    }

    public boolean covers(long marketTick) {
        return marketTick >= this.startTick && marketTick < this.endTick;
    }
}
