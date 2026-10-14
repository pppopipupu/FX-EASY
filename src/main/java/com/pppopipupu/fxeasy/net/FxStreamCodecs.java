package com.pppopipupu.fxeasy.net;

import module java.base;

import com.pppopipupu.fxeasy.market.Candle;
import com.pppopipupu.fxeasy.market.TimedCandle;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/// Compact binary codecs for the collection shaped payload fields.
final class FxStreamCodecs {

    static final StreamCodec<FriendlyByteBuf, double[]> DOUBLE_ARRAY = StreamCodec.of(
            (buffer, values) -> {
                buffer.writeVarInt(values.length);
                for (double value : values) {
                    buffer.writeDouble(value);
                }
            },
            buffer -> {
                double[] values = new double[buffer.readVarInt()];
                for (int index = 0; index < values.length; index++) {
                    values[index] = buffer.readDouble();
                }
                return values;
            });

    static final StreamCodec<FriendlyByteBuf, long[]> LONG_ARRAY = StreamCodec.of(
            (buffer, values) -> {
                buffer.writeVarInt(values.length);
                for (long value : values) {
                    buffer.writeLong(value);
                }
            },
            buffer -> {
                long[] values = new long[buffer.readVarInt()];
                for (int index = 0; index < values.length; index++) {
                    values[index] = buffer.readLong();
                }
                return values;
            });

    static final StreamCodec<FriendlyByteBuf, int[]> VAR_INT_ARRAY = StreamCodec.of(
            (buffer, values) -> {
                buffer.writeVarInt(values.length);
                for (int value : values) {
                    buffer.writeVarInt(value);
                }
            },
            buffer -> {
                int[] values = new int[buffer.readVarInt()];
                for (int index = 0; index < values.length; index++) {
                    values[index] = buffer.readVarInt();
                }
                return values;
            });

    static final StreamCodec<FriendlyByteBuf, Candle> CANDLE = StreamCodec.of(
            (buffer, candle) -> {
                buffer.writeDouble(candle.open());
                buffer.writeDouble(candle.high());
                buffer.writeDouble(candle.low());
                buffer.writeDouble(candle.close());
                buffer.writeDouble(candle.volume());
            },
            buffer -> new Candle(buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readDouble()));

    static final StreamCodec<FriendlyByteBuf, TimedCandle> TIMED_CANDLE = StreamCodec.of(
            (buffer, candle) -> {
                buffer.writeVarLong(candle.startTick());
                buffer.writeVarLong(candle.endTick());
                CANDLE.encode(buffer, candle.candle());
            },
            buffer -> {
                long start = buffer.readVarLong();
                long end = buffer.readVarLong();
                return new TimedCandle(start, end, CANDLE.decode(buffer));
            });

    /// Bounded so a malformed or hostile payload can never ask the client to allocate an unbounded list.
    static final int MAX_TIMED_CANDLES = 4_096;

    static final StreamCodec<FriendlyByteBuf, List<TimedCandle>> TIMED_CANDLES = StreamCodec.of(
            (buffer, candles) -> {
                buffer.writeVarInt(candles.size());
                for (TimedCandle candle : candles) {
                    TIMED_CANDLE.encode(buffer, candle);
                }
            },
            buffer -> {
                int size = buffer.readVarInt();
                if (size < 0 || size > MAX_TIMED_CANDLES) {
                    throw new DecoderException("candle list out of bounds: " + size);
                }
                List<TimedCandle> candles = new ArrayList<>(size);
                for (int index = 0; index < size; index++) {
                    candles.add(TIMED_CANDLE.decode(buffer));
                }
                return candles;
            });

    static final StreamCodec<FriendlyByteBuf, List<Candle>> CANDLES = StreamCodec.of(
            (buffer, candles) -> {
                buffer.writeVarInt(candles.size());
                for (Candle candle : candles) {
                    buffer.writeDouble(candle.open());
                    buffer.writeDouble(candle.high());
                    buffer.writeDouble(candle.low());
                    buffer.writeDouble(candle.close());
                    buffer.writeDouble(candle.volume());
                }
            },
            buffer -> {
                int size = buffer.readVarInt();
                List<Candle> candles = new ArrayList<>(size);
                for (int index = 0; index < size; index++) {
                    candles.add(new Candle(buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                            buffer.readDouble()));
                }
                return candles;
            });

    private FxStreamCodecs() {
    }
}
