package com.pppopipupu.fxeasy.shop;

import module java.base;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.pppopipupu.fxeasy.FxEasy;
import com.pppopipupu.fxeasy.market.MarketEngine;
import com.pppopipupu.fxeasy.market.Rng;
import net.minecraft.resources.Identifier;

/// The pool the VIP lounge draws its gifts from, read from the JSON file next to the world's other server
/// configuration. A world that has no file yet is handed the pool it would have started with, which writes the
/// file out, and a file that does not parse is reported and ignored so a typo never keeps a world from
/// starting. The refresh period and how many gifts the lounge may hold are part of the file as well: a pool is
/// what the world says it is.
public final class LoungeFile {

    public static final String FILE_NAME = "fxeasy-lounge.json";
    /// One gift per economic day unless the file says otherwise.
    public static final long DEFAULT_REFRESH_TICKS = MarketEngine.TICKS_PER_DAY;
    public static final int DEFAULT_CAPACITY = 10;
    /// Bounds a file is held to, so a hand written pool can neither restock every tick, fill a save with gifts
    /// nor hand a client a list longer than the page that writes it out could ever draw.
    public static final int MAX_ENTRIES = 64;
    private static final long MIN_REFRESH_TICKS = 200L;
    private static final long MAX_REFRESH_TICKS = 64L * MarketEngine.TICKS_PER_DAY;
    private static final int MAX_CAPACITY = 24;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static volatile Pool current = Pool.defaults();

    private LoungeFile() {
    }

    /// The pool the running world draws from, which is the default until [#load] has read a file.
    public static Pool current() {
        return current;
    }

    /// Drops the pool back to the defaults, which is what leaving a world does: the next world must not draw
    /// from the pool of the last one.
    public static void reset() {
        current = Pool.defaults();
    }

    /// The pool of the file, or the default pool when the file is missing (which writes it) or unusable.
    public static Pool load(Path path) {
        if (!Files.isRegularFile(path)) {
            current = Pool.defaults();
            write(path, current);
            return current;
        }
        try {
            JsonElement json = JsonParser.parseString(Files.readString(path));
            DataResult<Pool> parsed = Pool.CODEC.parse(JsonOps.INSTANCE, json);
            current = parsed.getOrThrow();
        } catch (IOException | RuntimeException failure) {
            FxEasy.LOGGER.error("FX lounge file {} is unusable, keeping the pool the world runs: {}", path,
                    failure.getMessage());
            current = Pool.defaults();
        }
        return current;
    }

    /// Writes a pool in the shape [#load] reads, which is also what a world without a file is handed.
    private static void write(Path path, Pool pool) {
        try {
            JsonElement json = Pool.CODEC.encodeStart(JsonOps.INSTANCE, pool).getOrThrow();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(json));
        } catch (IOException | RuntimeException failure) {
            FxEasy.LOGGER.error("FX lounge file {} could not be written: {}", path, failure.getMessage());
        }
    }

    /// One entry of the pool: the item, how many of it a gift carries, how likely it is to be drawn and the
    /// potion the stack carries when the item is a potion, which has no item id of its own.
    public record Entry(Identifier item, int count, int weight, Optional<Identifier> potion) {

        static final Codec<Entry> CODEC = RecordCodecBuilder.create((RecordCodecBuilder.Instance<Entry> instance) -> instance.group(
                Identifier.CODEC.fieldOf("item").forGetter(Entry::item),
                Codec.INT.optionalFieldOf("count", 1).forGetter(Entry::count),
                Codec.INT.optionalFieldOf("weight", 1).forGetter(Entry::weight),
                Identifier.CODEC.optionalFieldOf("potion").forGetter(Entry::potion)).apply(instance, Entry::new));

        public LoungeGift gift() {
            return new LoungeGift(this.item, this.count, this.potion.orElse(null));
        }

        /// Whether this entry can be drawn at all: a weight of nothing or a gift the registry cannot hand over is
        /// skipped instead of being drawn as an empty stack.
        public boolean usable() {
            return this.weight > 0 && gift().resolvable();
        }
    }

    /// A whole pool: how often the lounge is restocked, how many gifts it may hold and what is drawn from. The
    /// bounds of the file are applied here, the entry count included, so every reader of a pool - the lounge that
    /// draws from it and the client page that writes it out - sees the same list.
    public record Pool(long refreshTicks, int capacity, List<Entry> entries) {

        public static final Codec<Pool> CODEC = RecordCodecBuilder.create((RecordCodecBuilder.Instance<Pool> instance) -> instance.group(
                Codec.LONG.optionalFieldOf("refresh_ticks", DEFAULT_REFRESH_TICKS).forGetter(Pool::refreshTicks),
                Codec.INT.optionalFieldOf("capacity", DEFAULT_CAPACITY).forGetter(Pool::capacity),
                Entry.CODEC.listOf().fieldOf("items").forGetter(Pool::entries)).apply(instance, Pool::new));

        public Pool {
            refreshTicks = Math.clamp(refreshTicks, MIN_REFRESH_TICKS, MAX_REFRESH_TICKS);
            capacity = Math.clamp(capacity, 0, MAX_CAPACITY);
            entries = List.copyOf(entries.subList(0, Math.min(entries.size(), MAX_ENTRIES)));
        }

        /// The pool a world without a file starts on: one gift per economic day, ten gifts held, drawn from the
        /// valuables, the potions and the food a client of the group would actually want.
        public static Pool defaults() {
            return new Pool(DEFAULT_REFRESH_TICKS, DEFAULT_CAPACITY, List.of(
                    entry("minecraft:enchanted_golden_apple", 1, 1),
                    entry("minecraft:mace", 1, 1),
                    potion("minecraft:healing", 2),
                    potion("minecraft:strong_healing", 2),
                    potion("minecraft:regeneration", 2),
                    potion("minecraft:strength", 2),
                    potion("minecraft:swiftness", 2),
                    potion("minecraft:fire_resistance", 2),
                    potion("minecraft:water_breathing", 2),
                    potion("minecraft:night_vision", 2),
                    entry("minecraft:golden_apple", 1, 3),
                    entry("minecraft:experience_bottle", 2, 3),
                    entry("minecraft:honey_bottle", 2, 4),
                    entry("minecraft:cooked_beef", 4, 5),
                    entry("minecraft:golden_carrot", 3, 5),
                    entry("minecraft:bread", 4, 5)));
        }

        /// One gift drawn by weight, or null when the pool holds nothing it can hand over.
        public LoungeGift roll(Rng rng) {
            int total = 0;
            for (Entry entry : this.entries) {
                if (entry.usable()) {
                    total += entry.weight();
                }
            }
            if (total <= 0) {
                return null;
            }
            int pick = (int) Math.min(total - 1L, (long) (rng.nextDouble() * total));
            for (Entry entry : this.entries) {
                if (!entry.usable()) {
                    continue;
                }
                pick -= entry.weight();
                if (pick < 0) {
                    return entry.gift();
                }
            }
            return null;
        }

        private static Entry entry(String item, int count, int weight) {
            return new Entry(Identifier.parse(item), count, weight, Optional.empty());
        }

        private static Entry potion(String potion, int weight) {
            return new Entry(Identifier.withDefaultNamespace("potion"), 1, weight,
                    Optional.of(Identifier.parse(potion)));
        }
    }
}
