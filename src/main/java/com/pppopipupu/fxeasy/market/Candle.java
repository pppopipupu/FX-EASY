package com.pppopipupu.fxeasy.market;

import module java.base;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/// OHLC sample of one candle period. Values are pair mid prices. [#volume] is the turnover the market process
/// moved during the candle, which is what a synthetic market can report as volume.
public record Candle(double open, double high, double low, double close, double volume) {

    public static final Codec<Candle> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.fieldOf("open").forGetter(Candle::open),
            Codec.DOUBLE.fieldOf("high").forGetter(Candle::high),
            Codec.DOUBLE.fieldOf("low").forGetter(Candle::low),
            Codec.DOUBLE.fieldOf("close").forGetter(Candle::close),
            Codec.DOUBLE.optionalFieldOf("volume", 0.0D).forGetter(Candle::volume)).apply(instance, Candle::new));

    public Candle(double open, double high, double low, double close) {
        this(open, high, low, close, 0.0D);
    }

    public static Candle of(double price) {
        return new Candle(price, price, price, price, 0.0D);
    }

    public Candle merge(double price) {
        return new Candle(
                this.open,
                Math.max(this.high, price),
                Math.min(this.low, price),
                price,
                this.volume);
    }

    public Candle addVolume(double turnover) {
        return new Candle(this.open, this.high, this.low, this.close, this.volume + turnover);
    }

    public boolean isRising() {
        return this.close >= this.open;
    }
}
