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
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/// The exchange a village is extended with, written as a structure template. It is a wool building of black,
/// yellow and white over a thirteen by thirteen footprint and three storeys high, framed in copper: a copper
/// plinth course, copper corners, a copper string course in the course the floor of a storey above the ground
/// sits in and a copper parapet around the roof. The doorway in the middle of the west wall is three courses high
/// between copper pilasters, the string course above it is its lintel and the sign that names the building in
/// both of the languages the mod ships hangs above that; a pair of redstone lamps is set into the plinth either
/// side of it. Windows of glass sit in the white course of every wall of every storey, a stairwell against the
/// north wall reaches the two storeys above the ground, four torches on the floor of every storey light the hall
/// and a chest against the far wall hands out the phone and ten stacks of the base currency.
///
/// The doorway carries the entrance jigsaw a vanilla village house carries, with an empty pool: the street piece
/// that drew this building connects its `minecraft:building_entrance` jigsaw to this one, and the building itself
/// hangs nothing off its own entrance, so one template fits every village style. The plinth beside the door
/// carries the same thing under the name a street ends on, which is what lets the same template cap a dead end.
/// Every cell of the volume is written, air included, which is why the pool element that places it is a plain
/// single pool element and not the legacy one: the legacy element skips air and would leave the terrain inside
/// the building standing.
public final class FxStructures implements DataProvider {

    /// The template the village house pools name. The game test loads it back by this id.
    public static final Identifier EXCHANGE = Identifier.fromNamespaceAndPath(FxEasy.MODID, "fx_exchange");

    private static final int WIDTH = 13;
    private static final int HEIGHT = 14;
    private static final int DEPTH = 13;
    /// Middle of the building, where the doorway, the sign above it and the chest sit.
    private static final int MIDDLE_Z = (DEPTH - 1) / 2;
    /// Middle of the west wall, where the doorway, its lintel and the sign above it sit, and the plinth block one
    /// step beside it that the dead ends of a village connect to.
    private static final int DOOR_Z = MIDDLE_Z;
    private static final int STREET_Z = DOOR_Z - 1;
    /// A storey is three courses of room under the course its floor sits in. Three of them are built, the roof
    /// closes the last of them and the parapet stands one course above the roof.
    private static final int STOREY_HEIGHT = 4;
    private static final int STOREYS = 3;
    private static final int ROOF_Y = STOREY_HEIGHT * STOREYS;
    private static final int PARAPET_Y = ROOF_Y + 1;
    /// Distance of a window and of a lamp from a corner, the width of a window in cells, and the distance of a
    /// torch and of a pillar of the hall from a wall.
    private static final int WINDOW_INSET = 2;
    private static final int WINDOW_WIDTH = 2;
    /// The course of a storey, counted from the black course its floor sits in, that carries the windows of its
    /// walls.
    private static final int WINDOW_COURSE = 2;
    private static final int LAMP_INSET = 2;
    private static final int TORCH_INSET = 3;
    private static final int PILLAR_INSET = 4;
    /// The course the lintel of the doorway sits in with the sign above it, which is the string course the floor
    /// of the storey above sits in, and the courses the doorway leaves open under that lintel. The lowest of them
    /// is the plinth, which the entrance jigsaw turns back into the floor it already is, so what is left to walk
    /// through is the three courses above it.
    private static final int LINTEL_Y = STOREY_HEIGHT;
    private static final int SIGN_Y = LINTEL_Y + 1;
    /// The stairwell: three steps per storey against the north wall, the first of them two cells from the west
    /// wall, and the rail one course above the floor of the storey above, along the side of the opening they climb
    /// through that faces the middle of it.
    private static final int STAIR_Z = 1;
    private static final int STAIR_FIRST_X = 2;
    private static final int STAIR_STEPS = STOREY_HEIGHT - 1;
    /// The quarter turns of the sign over the door. A standing sign is written facing the player who places it,
    /// so a player walking in from the street to the west reads the face a sign placed by that player would show,
    /// which is what the game test checks this against.
    private static final int SIGN_ROTATION = 4;
    /// The lines of the sign, one per language. A sign is world data and holds no translation key, so the name of
    /// the building is written here twice rather than resolved like every other string of the mod.
    public static final List<String> SIGN_LINES = List.of("证券交易所", "Stock Exchange");
    /// The chest on the floor against the far wall of the hall, and the slots the exchange deals in.
    private static final int CHEST_X = WIDTH - 2;
    private static final int CHEST_Z = MIDDLE_Z;
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
        BlockState sign = signState();
        builder.fill((x, y, z) -> shell(x, y, z, sign));
        BlockState jigsaw = Blocks.JIGSAW.defaultBlockState()
                .setValue(JigsawBlock.ORIENTATION, FrontAndTop.WEST_UP);
        builder.put(0, 0, DOOR_Z, jigsaw, entrance());
        builder.put(0, 0, STREET_Z, jigsaw, street());
        builder.put(0, SIGN_Y, DOOR_Z, sign, signText());
        BlockState lamp = lamp();
        BlockState redstone = Blocks.REDSTONE_BLOCK.defaultBlockState();
        BlockState mat = Blocks.YELLOW_CARPET.defaultBlockState();
        for (int z : List.of(DOOR_Z - LAMP_INSET, DOOR_Z + LAMP_INSET)) {
            builder.put(0, 0, z, lamp, null);
            builder.put(1, 0, z, redstone, null);
            builder.put(1, 1, z, mat, null);
        }
        builder.put(CHEST_X, 1, CHEST_Z, Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.WEST), chest());
        pillars(builder);
        stairs(builder);
        torches(builder);
        return builder.save();
    }

    /// The four copper pillars of the hall, one at each corner of the square its middle leaves free, standing from
    /// the floor of the ground storey through the floors of the two above it.
    private static void pillars(Builder builder) {
        BlockState copper = Blocks.CUT_COPPER.defaultBlockState();
        for (int x : List.of(PILLAR_INSET, WIDTH - 1 - PILLAR_INSET)) {
            for (int z : List.of(PILLAR_INSET, DEPTH - 1 - PILLAR_INSET)) {
                for (int y = 1; y < ROOF_Y; y++) {
                    builder.put(x, y, z, copper, null);
                }
            }
        }
    }

    /// The stairwell against the north wall: three steps per storey, the floor of the storey above left open over
    /// them so that the climb has headroom, and a rail along the open side of that opening.
    private static void stairs(Builder builder) {
        BlockState copper = Blocks.CUT_COPPER.defaultBlockState();
        for (int storey = 0; storey < STOREYS - 1; storey++) {
            int floor = storey * STOREY_HEIGHT;
            for (int step = 0; step < STAIR_STEPS; step++) {
                builder.put(STAIR_FIRST_X + step, floor + 1 + step, STAIR_Z, copper, null);
            }
            for (int x = STAIR_FIRST_X - 1; x < STAIR_FIRST_X + STAIR_STEPS; x++) {
                builder.put(x, floor + STOREY_HEIGHT, STAIR_Z, Blocks.AIR.defaultBlockState(), null);
                builder.put(x, floor + STOREY_HEIGHT + 1, STAIR_Z + 1, copper, null);
            }
        }
    }

    /// Four torches on the floor of every storey, set in a square around the middle of it. The middle of a storey
    /// is then six steps from the nearest of them and lit to eight, which is brighter than anything hostile spawns
    /// at.
    private static void torches(Builder builder) {
        for (int storey = 0; storey < STOREYS; storey++) {
            int floor = storey * STOREY_HEIGHT + 1;
            for (int x : List.of(TORCH_INSET, WIDTH - 1 - TORCH_INSET)) {
                for (int z : List.of(TORCH_INSET, DEPTH - 1 - TORCH_INSET)) {
                    builder.put(x, floor, z, Blocks.TORCH.defaultBlockState(), null);
                }
            }
        }
    }

    private static BlockState shell(int x, int y, int z, BlockState sign) {
        if (y == 0) {
            return edge(x, z) ? Blocks.CUT_COPPER.defaultBlockState() : Blocks.WHITE_WOOL.defaultBlockState();
        }
        if (y == PARAPET_Y) {
            return edge(x, z) ? Blocks.CUT_COPPER.defaultBlockState() : Blocks.AIR.defaultBlockState();
        }
        if (y == ROOF_Y) {
            return roof(x, z);
        }
        if (storeyFloor(y)) {
            return edge(x, z) ? Blocks.CUT_COPPER.defaultBlockState() : Blocks.WHITE_WOOL.defaultBlockState();
        }
        if (!edge(x, z)) {
            return Blocks.AIR.defaultBlockState();
        }
        if (corner(x, z)) {
            return Blocks.CUT_COPPER.defaultBlockState();
        }
        if (x == 0 && z == DOOR_Z && y <= SIGN_Y) {
            if (y < LINTEL_Y) {
                return Blocks.AIR.defaultBlockState();
            }
            return y == SIGN_Y ? sign : Blocks.CUT_COPPER.defaultBlockState();
        }
        if (x == 0 && y < LINTEL_Y && (z == STREET_Z || z == DOOR_Z + 1)) {
            return Blocks.CUT_COPPER.defaultBlockState();
        }
        if (storeyCourse(y) == WINDOW_COURSE && window(x, z)) {
            return Blocks.GLASS.defaultBlockState();
        }
        return band(y);
    }

    private static boolean edge(int x, int z) {
        return x == 0 || x == WIDTH - 1 || z == 0 || z == DEPTH - 1;
    }

    private static boolean corner(int x, int z) {
        return (x == 0 || x == WIDTH - 1) && (z == 0 || z == DEPTH - 1);
    }

    /// Whether a course is the course the floor of a storey above the ground sits in, which the walls carry as a
    /// copper string course.
    private static boolean storeyFloor(int y) {
        return y > 0 && y < ROOF_Y && y % STOREY_HEIGHT == 0;
    }

    /// Which course of a storey a wall course is, counted from the black course its floor sits in: one is the
    /// yellow course, two the white one and three the black one under the floor of the storey above.
    private static int storeyCourse(int y) {
        return (y - 1) % STOREY_HEIGHT;
    }

    private static boolean window(int x, int z) {
        if (z == 0 || z == DEPTH - 1) {
            return inWindow(x, WIDTH);
        }
        return (x == 0 || x == WIDTH - 1) && inWindow(z, DEPTH);
    }

    /// Whether a cell of a wall of the given length lies in one of the two windows that wall carries, each of
    /// them two cells wide and starting two cells from a corner of the building.
    private static boolean inWindow(int cell, int size) {
        int inset = Math.min(cell, size - 1 - cell);
        return inset >= WINDOW_INSET && inset < WINDOW_INSET + WINDOW_WIDTH;
    }

    private static BlockState band(int y) {
        return switch (storeyCourse(y)) {
            case 1 -> Blocks.YELLOW_WOOL.defaultBlockState();
            case WINDOW_COURSE -> Blocks.WHITE_WOOL.defaultBlockState();
            default -> Blocks.BLACK_WOOL.defaultBlockState();
        };
    }

    private static BlockState roof(int x, int z) {
        if (edge(x, z)) {
            return Blocks.CUT_COPPER.defaultBlockState();
        }
        return switch (ring(x, z)) {
            case 1 -> Blocks.BLACK_WOOL.defaultBlockState();
            case 2 -> Blocks.YELLOW_WOOL.defaultBlockState();
            default -> Blocks.WHITE_WOOL.defaultBlockState();
        };
    }

    /// Distance of a cell of the roof from the nearest wall.
    private static int ring(int x, int z) {
        return Math.min(Math.min(x, WIDTH - 1 - x), Math.min(z, DEPTH - 1 - z));
    }

    /// The entrance jigsaw, field for field what a vanilla village house carries: the street connects to it, the
    /// joint is aligned, and the block it leaves behind once the village is built is the copper of the plinth it
    /// stands in.
    private static CompoundTag entrance() {
        CompoundTag jigsaw = new CompoundTag();
        jigsaw.putString("name", key("building_entrance"));
        jigsaw.putString("target", key("building_entrance"));
        jigsaw.putString("pool", key("empty"));
        jigsaw.putString("joint", "aligned");
        jigsaw.putString("final_state", blockKey(Blocks.CUT_COPPER));
        return jigsaw;
    }

    /// The jigsaw a dead end connects to, field for field what a vanilla terminator carries: a street piece that
    /// could not continue connects its `minecraft:street` jigsaw here rather than drawing a terminator, and the
    /// building hangs nothing off its own end, so that street stops at the door. It sits in the plinth beside the
    /// doorway, one course above its floor, which is the height a terminator's own jigsaw sits at.
    private static CompoundTag street() {
        CompoundTag jigsaw = new CompoundTag();
        jigsaw.putString("name", key("street"));
        jigsaw.putString("target", key("street"));
        jigsaw.putString("pool", key("empty"));
        jigsaw.putString("joint", "aligned");
        jigsaw.putString("final_state", blockKey(Blocks.CUT_COPPER));
        return jigsaw;
    }

    private static BlockState signState() {
        return Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, SIGN_ROTATION);
    }

    /// A lamp of the entrance, written lit. A redstone lamp is put out by the first neighbour update that finds
    /// it unpowered, so the lit state only holds because the redstone block of the plinth sits beside it.
    private static BlockState lamp() {
        return Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true);
    }

    /// The sign over the door, in the save format a sign block entity is written in: the lines of the front text,
    /// its colour and the wax that keeps a player from editing the name of the building away.
    private static CompoundTag signText() {
        List<Component> lines = new ArrayList<>(SIGN_LINES.stream().map(Component::literal).toList());
        while (lines.size() < SignText.LINES) {
            lines.add(Component.empty());
        }
        Component[] messages = lines.toArray(Component[]::new);
        CompoundTag sign = new CompoundTag();
        sign.put("front_text", SignText.DIRECT_CODEC
                .encodeStart(NbtOps.INSTANCE, new SignText(messages, messages, DyeColor.BLACK, false))
                .getOrThrow());
        sign.putBoolean("is_waxed", true);
        sign.putString("id", blockKey(Blocks.OAK_SIGN));
        return sign;
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
