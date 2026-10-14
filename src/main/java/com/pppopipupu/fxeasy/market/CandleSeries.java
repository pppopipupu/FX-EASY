package com.pppopipupu.fxeasy.market;

import module java.base;

/// Rolling window of closed candles plus the candle currently being formed. Closed candles are stored in a
/// ring buffer of [#Timeframe.MAX_CANDLES] entries, oldest first when read through [#snapshot]. Time is the
/// monotonic market clock, so a candle boundary cannot be skipped by sleeping or by a time command.
public final class CandleSeries {

    private final Timeframe timeframe;
    private final Candle[] closed;
    private int filled;
    private long nextRollTick = Long.MIN_VALUE;
    private Candle current;

    public CandleSeries(Timeframe timeframe) {
        this.timeframe = timeframe;
        this.closed = new Candle[Timeframe.MAX_CANDLES];
    }

    public Timeframe timeframe() {
        return this.timeframe;
    }

    /// Market tick the candle being formed will close at, or [#Long#MIN_VALUE] while there is none.
    public long nextRollTick() {
        return this.nextRollTick;
    }

    public Candle current() {
        return this.current;
    }

    public void accept(long marketTick, double price) {
        if (this.current == null) {
            this.current = Candle.of(price);
            this.nextRollTick = alignUp(marketTick);
        } else {
            double moved = Math.abs(price - this.current.close());
            this.current = this.current.merge(price).addVolume(moved);
            if (marketTick >= this.nextRollTick) {
                push(this.current);
                this.current = null;
                this.nextRollTick = alignUp(marketTick);
            }
        }
    }

    public void push(Candle candle) {
        this.closed[(this.filled) % this.closed.length] = candle;
        this.filled++;
    }

    /// Closed candles from oldest to newest, followed by the forming candle when `includeCurrent` is set.
    public List<Candle> snapshot(boolean includeCurrent) {
        int count = Math.min(this.filled, this.closed.length);
        List<Candle> candles = new ArrayList<>(count + 1);
        int start = this.filled - count;
        for (int i = 0; i < count; i++) {
            candles.add(this.closed[(start + i) % this.closed.length]);
        }
        if (includeCurrent && this.current != null) {
            candles.add(this.current);
        }
        return candles;
    }

    /// Closed candles with their market tick intervals, oldest first, followed by the forming candle when
    /// `includeCurrent` is set. The closed ring does not store an interval, so the intervals are derived
    /// backwards from the boundary the forming candle will close at: a closed candle always ends where the next
    /// one begins.
    public List<TimedCandle> timedSnapshot(boolean includeCurrent) {
        int count = Math.min(this.filled, this.closed.length);
        List<TimedCandle> candles = new ArrayList<>(count + 1);
        long period = this.timeframe.ticks();
        int start = this.filled - count;
        long boundary = this.nextRollTick == Long.MIN_VALUE ? Long.MIN_VALUE : this.nextRollTick;
        if (boundary != Long.MIN_VALUE) {
            long end = boundary;
            long[] ends = new long[count];
            for (int i = count - 1; i >= 0; i--) {
                ends[i] = end;
                end -= period;
            }
            for (int i = 0; i < count; i++) {
                candles.add(new TimedCandle(ends[i] - period, ends[i], this.closed[(start + i) % this.closed.length]));
            }
        } else {
            for (int i = 0; i < count; i++) {
                candles.add(new TimedCandle((long) i, i + 1L, this.closed[(start + i) % this.closed.length]));
            }
        }
        if (includeCurrent && this.current != null) {
            long end = this.nextRollTick == Long.MIN_VALUE ? Long.MIN_VALUE : this.nextRollTick;
            long startTick = end == Long.MIN_VALUE ? 0L : end - period;
            candles.add(new TimedCandle(startTick, startTick + period, this.current));
        }
        return candles;
    }

    public boolean isEmpty() {
        return this.filled == 0 && this.current == null;
    }

    public void restore(List<Candle> candles, Candle forming, long marketTick, long nextRoll) {
        Arrays.fill(this.closed, null);
        this.filled = 0;
        for (Candle candle : candles) {
            push(candle);
        }
        this.current = forming;
        this.nextRollTick = nextRoll != Long.MIN_VALUE ? nextRoll : (forming != null ? alignUp(marketTick) : Long.MIN_VALUE);
    }

    private long alignUp(long marketTick) {
        long period = this.timeframe.ticks();
        return Math.floorDiv(marketTick, period) * period + period;
    }
}
