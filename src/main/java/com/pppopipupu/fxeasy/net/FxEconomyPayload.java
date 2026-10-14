package com.pppopipupu.fxeasy.net;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import com.pppopipupu.fxeasy.market.Currencies;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/// Read only snapshot of the world economy as the terminal reports it: how much the world produced, how much it
/// reabsorbed, how much of that the market has priced, and how complete the observation is. Everything is
/// already normalised - flows in capacity units per economic day, the capacity itself in items per day, pressure
/// as an ordinal level, revaluation as log units - so the client never has to know how the server estimates
/// anything.
public record FxEconomyPayload(int status, long revision, long marketTick, long bucketStart, long rolloverDay,
        int playerDayTenths, double[] supplyFlow, double[] demandFlow, int[] pressure, double[] appliedFundamental,
        double[] appliedEvent, double[] capacity, int[] coverage, int[] supportedSources, long[] supportedAtoms)
        implements CustomPacketPayload {

    /// One entry per currency in every per currency array, in the order the currency table of this session names.
    public static final int COVERAGE_ENTRIES = 4;
    public static final int MAX_COVERAGE = 32;

    /// The report describes the state of the world right now, at the bucket the market is in.
    public static final int STATUS_FRESH = 0;
    /// The world has been running this extension for less than one economic day, so the flow estimates are still
    /// filling up and the terminal says so instead of pretending to know.
    public static final int STATUS_OBSERVING = 1;
    /// The report is older than the newest bucket, which happens after a load or while nothing was running.
    public static final int STATUS_STALE = 2;
    /// The collector is switched off, so there is no observation to report at all.
    public static final int STATUS_DISABLED = 3;

    /// Pressure levels, from the market's point of view.
    public static final int PRESSURE_LOW = 0;
    public static final int PRESSURE_NORMAL = 1;
    public static final int PRESSURE_HIGH = 2;
    public static final int PRESSURE_SURPLUS = 3;
    public static final int PRESSURE_SEVERE = 4;

    /// What the coverage entries mean, in order: chunks whose placed block marks were dropped because the chunk
    /// went over its budget, whether any dimension lacked a supported source, the number of source kinds the
    /// configuration switched off, and whether the saved world predates this extension.
    public static final int COVERAGE_OVER_BUDGET_CHUNKS = 0;
    public static final int COVERAGE_UNSUPPORTED_DIMENSIONS = 1;
    public static final int COVERAGE_DISABLED_SOURCES = 2;
    public static final int COVERAGE_MIGRATED = 3;

    public static final Type<FxEconomyPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(FxEasy.MODID, "economy"));
    public static final StreamCodec<FriendlyByteBuf, FxEconomyPayload> STREAM_CODEC = StreamCodec.of(FxEconomyPayload::encode, FxEconomyPayload::decode);

    public FxEconomyPayload {
        int size = Currencies.count();
        requireLength(supplyFlow, size, "supply flow");
        requireLength(demandFlow, size, "demand flow");
        requireLength(pressure, size, "pressure");
        requireLength(appliedFundamental, size, "applied fundamental");
        requireLength(appliedEvent, size, "applied event");
        requireLength(capacity, size, "capacity");
        requireLength(coverage, COVERAGE_ENTRIES, "coverage");
        if (supportedSources.length > MAX_COVERAGE || supportedAtoms.length != supportedSources.length) {
            throw new IllegalArgumentException("supported source list is malformed");
        }
        supplyFlow = supplyFlow.clone();
        demandFlow = demandFlow.clone();
        pressure = pressure.clone();
        appliedFundamental = appliedFundamental.clone();
        appliedEvent = appliedEvent.clone();
        capacity = capacity.clone();
        coverage = coverage.clone();
        supportedSources = supportedSources.clone();
        supportedAtoms = supportedAtoms.clone();
    }

    private static void requireLength(double[] values, int length, String name) {
        if (values.length != length) {
            throw new IllegalArgumentException(name + " needs " + length + " entries");
        }
    }

    private static void requireLength(int[] values, int length, String name) {
        if (values.length != length) {
            throw new IllegalArgumentException(name + " needs " + length + " entries");
        }
    }

    private static void encode(FriendlyByteBuf buffer, FxEconomyPayload payload) {
        buffer.writeVarInt(payload.status);
        buffer.writeVarLong(payload.revision);
        buffer.writeVarLong(payload.marketTick);
        buffer.writeVarLong(payload.bucketStart);
        buffer.writeVarLong(payload.rolloverDay);
        buffer.writeVarInt(payload.playerDayTenths);
        FxStreamCodecs.DOUBLE_ARRAY.encode(buffer, payload.supplyFlow);
        FxStreamCodecs.DOUBLE_ARRAY.encode(buffer, payload.demandFlow);
        FxStreamCodecs.VAR_INT_ARRAY.encode(buffer, payload.pressure);
        FxStreamCodecs.DOUBLE_ARRAY.encode(buffer, payload.appliedFundamental);
        FxStreamCodecs.DOUBLE_ARRAY.encode(buffer, payload.appliedEvent);
        FxStreamCodecs.DOUBLE_ARRAY.encode(buffer, payload.capacity);
        FxStreamCodecs.VAR_INT_ARRAY.encode(buffer, payload.coverage);
        FxStreamCodecs.VAR_INT_ARRAY.encode(buffer, payload.supportedSources);
        FxStreamCodecs.LONG_ARRAY.encode(buffer, payload.supportedAtoms);
    }

    private static FxEconomyPayload decode(FriendlyByteBuf buffer) {
        int status = buffer.readVarInt();
        long revision = buffer.readVarLong();
        long marketTick = buffer.readVarLong();
        long bucketStart = buffer.readVarLong();
        long rolloverDay = buffer.readVarLong();
        int playerDayTenths = buffer.readVarInt();
        double[] supplyFlow = FxStreamCodecs.DOUBLE_ARRAY.decode(buffer);
        double[] demandFlow = FxStreamCodecs.DOUBLE_ARRAY.decode(buffer);
        int[] pressure = FxStreamCodecs.VAR_INT_ARRAY.decode(buffer);
        double[] appliedFundamental = FxStreamCodecs.DOUBLE_ARRAY.decode(buffer);
        double[] appliedEvent = FxStreamCodecs.DOUBLE_ARRAY.decode(buffer);
        double[] capacity = FxStreamCodecs.DOUBLE_ARRAY.decode(buffer);
        int[] coverage = FxStreamCodecs.VAR_INT_ARRAY.decode(buffer);
        int[] supportedSources = FxStreamCodecs.VAR_INT_ARRAY.decode(buffer);
        long[] supportedAtoms = FxStreamCodecs.LONG_ARRAY.decode(buffer);
        return new FxEconomyPayload(status, revision, marketTick, bucketStart, rolloverDay, playerDayTenths, supplyFlow,
                demandFlow, pressure, appliedFundamental, appliedEvent, capacity, coverage, supportedSources,
                supportedAtoms);
    }

    @Override
    public Type<FxEconomyPayload> type() {
        return TYPE;
    }
}
