package com.pppopipupu.fxeasy.datagen;

import module java.base;

import com.google.common.hash.Hashing;
import com.google.common.hash.HashingOutputStream;
import com.pppopipupu.fxeasy.FxEasy;
import com.pppopipupu.fxeasy.content.FxItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/// The exchange a village is extended with, written as a structure template. It is a wool building of black,
/// yellow and white over a nine by nine footprint: a doorway in the middle of the west wall, windows in the white
/// course of every wall, a flat roof, two torches and a chest against the far wall that hands out the phone and
/// ten stacks of the base currency.
///
/// The doorway carries the entrance jigsaw a vanilla village house carries, with an empty pool: the street piece
/// that drew this building connects its `minecraft:building_entrance` jigsaw to this one, and the building itself
/// hangs nothing off its own entrance, so one template fits every village style. Every cell of the volume is
/// written, air included, which is why the pool element that places it is a plain single pool element and not the
/// legacy one: the legacy element skips air and would leave the terrain inside the building standing.
public final class FxStructures implements DataProvider {

    /// The template the village house pools name. The game test loads it back by this id.
    public static final Identifier EXCHANGE = Identifier.fromNamespaceAndPath(FxEasy.MODID, "fx_exchange");

    private static final int WIDTH = 9;
    private static final int HEIGHT = 6;
    private static final int DEPTH = 9;
    /// Middle of the west wall, where the doorway and its jigsaw sit, and the torches either side of it.
    private static final int DOOR_Z = 4;
    private static final int TORCH_X = 2;
    /// Distance of a window and of the white square of the roof from a corner.
    private static final int WINDOW_INSET = 2;
    private static final int ROOF_INSET = 2;
    /// The chest on the floor against the far wall, and the slots the exchange deals in.
    private static final int CHEST_X = 4;
    private static final int CHEST_Z = 7;
    private static final int PHONE_SLOT = 0;
    private static final int DIRT_SLOTS = 10;
    private static final int DIRT_FIRST_SLOT = PHONE_SLOT + 1;

    private final PackOutput output;

    public FxStructures(PackOutput output) {
        this.output = output;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        Path file = this.output
                .createPathProvider(PackOutput.Target.DATA_PACK, StructureTemplateManager.STRUCTURE_DIRECTORY_NAME)
                .file(EXCHANGE, "nbt");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            HashingOutputStream hashed = new HashingOutputStream(Hashing.sha256(), bytes);
            NbtIo.writeCompressed(exchange(), hashed);
            cache.writeIfNeeded(file, bytes.toByteArray(), hashed.hash());
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public String getName() {
        return "FX EASY structures";
    }

    private static CompoundTag exchange() {
        Builder builder = new Builder(WIDTH, HEIGHT, DEPTH);
        builder.fill(FxStructures::shell);
        builder.put(0, 0, DOOR_Z, Blocks.JIGSAW.defaultBlockState()
                .setValue(JigsawBlock.ORIENTATION, FrontAndTop.WEST_UP), entrance());
        builder.put(CHEST_X, 1, CHEST_Z, Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH), chest());
        builder.put(TORCH_X, 1, DOOR_Z, Blocks.TORCH.defaultBlockState(), null);
        builder.put(WIDTH - 1 - TORCH_X, 1, DOOR_Z, Blocks.TORCH.defaultBlockState(), null);
        return builder.save();
    }

    private static BlockState shell(int x, int y, int z) {
        if (y == 0) {
            return Blocks.WHITE_WOOL.defaultBlockState();
        }
        if (y == HEIGHT - 1) {
            return roof(x, z);
        }
        if (!edge(x, z)) {
            return Blocks.AIR.defaultBlockState();
        }
        if (x == 0 && z == DOOR_Z && y <= 2) {
            return Blocks.AIR.defaultBlockState();
        }
        if (y == 3 && window(x, z)) {
            return Blocks.AIR.defaultBlockState();
        }
        boolean corner = (x == 0 || x == WIDTH - 1) && (z == 0 || z == DEPTH - 1);
        return corner ? Blocks.BLACK_WOOL.defaultBlockState() : band(y);
    }

    private static boolean edge(int x, int z) {
        return x == 0 || x == WIDTH - 1 || z == 0 || z == DEPTH - 1;
    }

    private static boolean window(int x, int z) {
        if (z == 0 || z == DEPTH - 1) {
            return x == WINDOW_INSET || x == WIDTH - 1 - WINDOW_INSET;
        }
        return (x == 0 || x == WIDTH - 1) && (z == WINDOW_INSET || z == DEPTH - 1 - WINDOW_INSET);
    }

    private static BlockState band(int y) {
        return switch (y) {
            case 2 -> Blocks.YELLOW_WOOL.defaultBlockState();
            case 3 -> Blocks.WHITE_WOOL.defaultBlockState();
            default -> Blocks.BLACK_WOOL.defaultBlockState();
        };
    }

    private static BlockState roof(int x, int z) {
        if (edge(x, z)) {
            return Blocks.YELLOW_WOOL.defaultBlockState();
        }
        boolean centre = x >= ROOF_INSET && x <= WIDTH - 1 - ROOF_INSET
                && z >= ROOF_INSET && z <= DEPTH - 1 - ROOF_INSET;
        return centre ? Blocks.WHITE_WOOL.defaultBlockState() : Blocks.BLACK_WOOL.defaultBlockState();
    }

    /// The entrance jigsaw, field for field what a vanilla village house carries: the street connects to it, the
    /// joint is aligned, and the block it leaves behind once the village is built is the floor it stands in.
    private static CompoundTag entrance() {
        CompoundTag jigsaw = new CompoundTag();
        jigsaw.putString("name", key("building_entrance"));
        jigsaw.putString("target", key("building_entrance"));
        jigsaw.putString("pool", key("empty"));
        jigsaw.putString("joint", "aligned");
        jigsaw.putString("final_state", blockKey(Blocks.WHITE_WOOL));
        return jigsaw;
    }

    private static CompoundTag chest() {
        CompoundTag chest = new CompoundTag();
        chest.putString("id", blockKey(Blocks.CHEST));
        chest.put("Items", items());
        return chest;
    }

    /// The exchange deals in two things: the phone an account is opened with, and ten full stacks of the base
    /// currency the margin is handed over in. The stacks are written in the plain form of the current save
    /// format, because a datagen run has no bound item components to build an [ItemStack] with.
    private static ListTag items() {
        ListTag items = new ListTag();
        items.add(stack(PHONE_SLOT, FxItems.FX_PHONE.get(), 1));
        for (int slot = DIRT_FIRST_SLOT; slot < DIRT_FIRST_SLOT + DIRT_SLOTS; slot++) {
            items.add(stack(slot, Items.DIRT, Item.DEFAULT_MAX_STACK_SIZE));
        }
        return items;
    }

    private static CompoundTag stack(int slot, Item item, int count) {
        CompoundTag stack = new CompoundTag();
        stack.putByte("Slot", (byte) slot);
        stack.putString("id", BuiltInRegistries.ITEM.getKey(item).toString());
        stack.putInt("count", count);
        return stack;
    }

    private static String key(String path) {
        return Identifier.withDefaultNamespace(path).toString();
    }

    private static String blockKey(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).toString();
    }

    @FunctionalInterface
    private interface Shape {

        BlockState at(int x, int y, int z);
    }

    private record Cell(BlockPos pos, BlockState state, CompoundTag nbt) {
    }

    /// Structure template under construction: one cell per position, the palette gains a state the first time it
    /// is used, and the cells are written in the order a vanilla template writes them, blocks first and block
    /// entities last.
    private static final class Builder {

        private static final Comparator<Cell> ORDER = Comparator.comparingInt((Cell cell) -> cell.pos().getY())
                .thenComparingInt(cell -> cell.pos().getX())
                .thenComparingInt(cell -> cell.pos().getZ());

        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private final Map<BlockPos, Cell> cells = new LinkedHashMap<>();
        private final Map<BlockState, Integer> palette = new LinkedHashMap<>();

        private Builder(int sizeX, int sizeY, int sizeZ) {
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
        }

        private void fill(Shape shape) {
            for (int y = 0; y < this.sizeY; y++) {
                for (int z = 0; z < this.sizeZ; z++) {
                    for (int x = 0; x < this.sizeX; x++) {
                        this.put(x, y, z, shape.at(x, y, z), null);
                    }
                }
            }
        }

        private void put(int x, int y, int z, BlockState state, CompoundTag nbt) {
            if (!this.palette.containsKey(state)) {
                this.palette.put(state, this.palette.size());
            }
            BlockPos pos = new BlockPos(x, y, z);
            this.cells.put(pos, new Cell(pos, state, nbt));
        }

        private CompoundTag save() {
            List<Cell> plain = new ArrayList<>();
            List<Cell> entities = new ArrayList<>();
            for (Cell cell : this.cells.values()) {
                (cell.nbt() == null ? plain : entities).add(cell);
            }
            plain.sort(ORDER);
            entities.sort(ORDER);
            ListTag blocks = new ListTag();
            for (Cell cell : plain) {
                blocks.add(block(cell));
            }
            for (Cell cell : entities) {
                blocks.add(block(cell));
            }
            CompoundTag tag = new CompoundTag();
            ListTag states = new ListTag();
            for (BlockState state : this.palette.keySet()) {
                states.add(NbtUtils.writeBlockState(state));
            }
            tag.put("palette", states);
            tag.put("blocks", blocks);
            tag.put("entities", new ListTag());
            tag.put("size", integers(this.sizeX, this.sizeY, this.sizeZ));
            return NbtUtils.addCurrentDataVersion(tag);
        }

        private CompoundTag block(Cell cell) {
            CompoundTag block = new CompoundTag();
            block.put("pos", integers(cell.pos().getX(), cell.pos().getY(), cell.pos().getZ()));
            block.putInt("state", this.palette.get(cell.state()));
            if (cell.nbt() != null) {
                block.put("nbt", cell.nbt());
            }
            return block;
        }

        private static ListTag integers(int... values) {
            ListTag list = new ListTag();
            for (int value : values) {
                list.add(IntTag.valueOf(value));
            }
            return list;
        }
    }
}
