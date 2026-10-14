package com.pppopipupu.fxeasy.market;

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
import net.minecraft.resources.Identifier;

/// The currencies of a world, read from the JSON file next to the world's other server configuration. A world
/// that has no file yet is handed the set it would have started with, so the file is the whole definition of
/// what is legal tender here and a player adds a currency by adding an entry and restarting.
///
/// A file that does not parse, or that names a set the platform cannot trade, is reported and ignored: a world
/// keeps trading what it was running with rather than refusing to start over a typo.
public final class CurrencyFile {

    public static final String FILE_NAME = "fxeasy-currencies.json";
    /// Capacity a definition keeps when it does not name one, in items per economic day.
    public static final double DEFAULT_CAPACITY = 1024.0D;
    public static final double DEFAULT_SUPPLY_WEIGHT = 0.35D;
    public static final double DEFAULT_DEMAND_WEIGHT = 0.08D;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private CurrencyFile() {
    }

    /// Currencies of the file, or the default set when the file is missing (which writes it) or unusable.
    public static List<Currency> load(Path path) {
        List<Currency> defaults = Currencies.defaults();
        if (!Files.isRegularFile(path)) {
            write(path, defaults);
            return defaults;
        }
        try {
            JsonElement json = JsonParser.parseString(Files.readString(path));
            DataResult<File> parsed = File.CODEC.parse(JsonOps.INSTANCE, json);
            return parsed.getOrThrow().currencies().stream().map(Entry::toCurrency).toList();
        } catch (IOException | RuntimeException failure) {
            FxEasy.LOGGER.error("FX currency file {} is unusable, keeping the currencies the world runs: {}",
                    path, failure.getMessage());
            return defaults;
        }
    }

    /// Writes a set in the shape [#load] reads, which is also what a world without a file is handed.
    public static void write(Path path, List<Currency> currencies) {
        try {
            JsonElement json = File.CODEC.encodeStart(JsonOps.INSTANCE, File.of(currencies)).getOrThrow();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(json));
        } catch (IOException | RuntimeException failure) {
            FxEasy.LOGGER.error("FX currency file {} could not be written: {}", path, failure.getMessage());
        }
    }

    private record File(List<Entry> currencies) {

        static final Codec<File> CODEC = RecordCodecBuilder.create((RecordCodecBuilder.Instance<File> instance) -> instance.group(
                Entry.CODEC.listOf().fieldOf("currencies").forGetter(File::currencies)).apply(instance, File::new));

        static File of(List<Currency> currencies) {
            return new File(currencies.stream().map(Entry::of).toList());
        }
    }

    /// One entry of the file. Its optional fields are what a definition may leave out, and the defaults they fall
    /// back on are the ones this class documents.
    private record Entry(String id, String code, Optional<String> name, Map<String, String> names, Optional<String> item,
            double initialPrice, double dailySigma, double annualRate, Optional<Long> atomsPerItem,
            Optional<Double> capacity, Optional<Double> supplyWeight, Optional<Double> demandWeight) {

        static final Codec<Entry> CODEC = RecordCodecBuilder.create((RecordCodecBuilder.Instance<Entry> instance) -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Entry::id),
                Codec.STRING.fieldOf("code").forGetter(Entry::code),
                Codec.STRING.optionalFieldOf("name").forGetter(Entry::name),
                Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("names", Map.of()).forGetter(Entry::names),
                Codec.STRING.optionalFieldOf("item").forGetter(Entry::item),
                Codec.DOUBLE.fieldOf("initial_price").forGetter(Entry::initialPrice),
                Codec.DOUBLE.fieldOf("daily_sigma").forGetter(Entry::dailySigma),
                Codec.DOUBLE.fieldOf("annual_rate").forGetter(Entry::annualRate),
                Codec.LONG.optionalFieldOf("atoms_per_item").forGetter(Entry::atomsPerItem),
                Codec.DOUBLE.optionalFieldOf("capacity").forGetter(Entry::capacity),
                Codec.DOUBLE.optionalFieldOf("supply_weight").forGetter(Entry::supplyWeight),
                Codec.DOUBLE.optionalFieldOf("demand_weight").forGetter(Entry::demandWeight)).apply(instance, Entry::new));

        static Entry of(Currency currency) {
            return new Entry(currency.id(), currency.code(), Optional.of(currency.name()), currency.names(),
                    currency.item().map(Identifier::toString), currency.initialPrice(), currency.dailySigma(),
                    currency.annualRate(), Optional.of(currency.atomsPerItem()), Optional.of(currency.capacity()),
                    Optional.of(currency.supplyWeight()), Optional.of(currency.demandWeight()));
        }

        Currency toCurrency() {
            return new Currency(this.id, this.code, this.name.orElse(this.code), this.names,
                    this.item.map(Entry::item), this.initialPrice, this.dailySigma, this.annualRate,
                    this.atomsPerItem.orElse(1L), this.capacity.orElse(DEFAULT_CAPACITY),
                    this.supplyWeight.orElse(DEFAULT_SUPPLY_WEIGHT), this.demandWeight.orElse(DEFAULT_DEMAND_WEIGHT));
        }

        private static Identifier item(String id) {
            Identifier item = Identifier.tryParse(id);
            if (item == null) {
                throw new IllegalArgumentException("currency item " + id + " is not a valid item id");
            }
            return item;
        }
    }
}
