package com.pppopipupu.fxeasy.server;

import module java.base;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.pppopipupu.fxeasy.FxConfig;
import com.pppopipupu.fxeasy.FxEasy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/// Per chunk record of the resource blocks a player or a machine put there. A position is marked when a
/// placement is committed, so breaking it later is a recovery rather than a find, and the marks follow a piston
/// that moves the block. A chunk whose marks would exceed the configured budget becomes permanently excluded:
/// from then on nothing in it counts as natural, which under counts instead of quietly forgetting that a player
/// built there.
///
/// Marks are absolute packed block positions kept in the owning chunk's attachment, so they survive a save, a
/// chunk reload and a world upgrade. Only the server thread touches them.
public final class FxWorldSourceMarkers {

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, FxEasy.MODID);

    private static final DeferredHolder<AttachmentType<?>, AttachmentType<Marks>> MARKS = ATTACHMENT_TYPES.register(
            "source_marks",
            () -> AttachmentType.builder(Marks::new).serialize(Marks.CODEC, marks -> !marks.isEmpty()).build());

    private FxWorldSourceMarkers() {
    }

    public static void register(IEventBus modBus) {
        ATTACHMENT_TYPES.register(modBus);
    }

    /// Whether the block at this position may count as a natural find, clearing the mark when it had one. An
    /// excluded chunk never counts, and a chunk with no record at all is untouched world.
    public static boolean claimNatural(ServerLevel level, BlockPos pos) {
        if (!FxConfig.get().oreMarkersEnabled()) {
            return true;
        }
        LevelChunk chunk = level.getChunkAt(pos);
        Marks marks = chunk.getExistingDataOrNull(MARKS.get());
        if (marks == null) {
            return true;
        }
        if (marks.isExcluded()) {
            FxWorldEconomyTracker.noteExcludedChunk(level, chunk.getPos());
            return false;
        }
        if (!marks.remove(pos.asLong())) {
            return true;
        }
        chunk.markUnsaved();
        return false;
    }

    public static void markPlaced(ServerLevel level, BlockPos pos, BlockState state) {
        if (!FxConfig.get().oreMarkersEnabled() || FxCurrencyAtoms.naturalChannel(state) == null) {
            return;
        }
        mark(level, pos);
    }

    /// Copies the mark of every block a piston moved to the position it ends up in. All sources are read before
    /// any source is cleared, so the result does not depend on the order the resolver listed the positions in.
    public static void moveMarks(ServerLevel level, List<BlockPos> sources, Direction pushDirection) {
        if (!FxConfig.get().oreMarkersEnabled() || sources.isEmpty()) {
            return;
        }
        long[] marked = new long[sources.size()];
        int count = 0;
        for (BlockPos source : sources) {
            if (isMarked(level, source)) {
                marked[count++] = source.asLong();
            }
        }
        for (int index = 0; index < count; index++) {
            clear(level, BlockPos.of(marked[index]));
        }
        for (int index = 0; index < count; index++) {
            mark(level, BlockPos.of(marked[index]).relative(pushDirection));
        }
    }

    private static boolean isMarked(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        Marks marks = chunk.getExistingDataOrNull(MARKS.get());
        return marks != null && !marks.isExcluded() && marks.contains(pos.asLong());
    }

    private static void mark(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        Marks marks = chunk.getData(MARKS.get());
        if (marks.isExcluded() || marks.contains(pos.asLong())) {
            return;
        }
        if (!marks.add(pos.asLong(), FxConfig.get().oreMarkerBudget())) {
            FxWorldEconomyTracker.noteExcludedChunk(level, chunk.getPos());
        }
        chunk.markUnsaved();
    }

    private static void clear(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        Marks marks = chunk.getExistingDataOrNull(MARKS.get());
        if (marks != null && marks.remove(pos.asLong())) {
            chunk.markUnsaved();
        }
    }

    /// Marks of one chunk: a sorted, bounded set of packed block positions plus the flag that retires the chunk
    /// from natural collection for good.
    public static final class Marks {

        private static final long[] NONE = new long[0];

        public static final MapCodec<Marks> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.LONG.listOf().optionalFieldOf("positions", List.of()).forGetter(Marks::positions),
                Codec.BOOL.optionalFieldOf("excluded", false).forGetter(Marks::isExcluded))
                .apply(instance, Marks::new));

        private long[] positions = NONE;
        private boolean excluded;

        private Marks(List<Long> positions, boolean excluded) {
            this.positions = new long[positions.size()];
            for (int index = 0; index < this.positions.length; index++) {
                this.positions[index] = positions.get(index);
            }
            Arrays.sort(this.positions);
            this.excluded = excluded;
        }

        Marks() {
        }

        public boolean isEmpty() {
            return this.positions.length == 0 && !this.excluded;
        }

        public boolean isExcluded() {
            return this.excluded;
        }

        public boolean contains(long position) {
            return Arrays.binarySearch(this.positions, position) >= 0;
        }

        /// Adds one position and returns false when the chunk has reached the budget, in which case the chunk is
        /// excluded and the marks it already holds are kept.
        public boolean add(long position, int budget) {
            int slot = Arrays.binarySearch(this.positions, position);
            if (slot >= 0) {
                return true;
            }
            if (this.positions.length >= budget) {
                this.excluded = true;
                return false;
            }
            int insertion = -slot - 1;
            this.positions = Arrays.copyOf(this.positions, this.positions.length + 1);
            System.arraycopy(this.positions, insertion, this.positions, insertion + 1, this.positions.length - insertion - 1);
            this.positions[insertion] = position;
            return true;
        }

        public boolean remove(long position) {
            int slot = Arrays.binarySearch(this.positions, position);
            if (slot < 0) {
                return false;
            }
            System.arraycopy(this.positions, slot + 1, this.positions, slot, this.positions.length - slot - 1);
            this.positions = Arrays.copyOf(this.positions, this.positions.length - 1);
            return true;
        }

        private List<Long> positions() {
            List<Long> copy = new ArrayList<>(this.positions.length);
            for (long position : this.positions) {
                copy.add(position);
            }
            return copy;
        }
    }
}
