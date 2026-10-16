package com.pppopipupu.fxeasy.datagen;

import module java.base;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/// Extends the house pool and the terminator pool of every village style with the exchange. A village draws its
/// buildings from `minecraft:village/<style>/houses`, and the only way into that pool is the pool itself: this
/// provider reads the file the game ships, keeps every element of it and appends the exchange, so a village keeps
/// all the houses it always had and gains one more. A street piece that cannot continue draws from
/// `minecraft:village/<style>/terminators` instead, which is the fallback of its own pool, so the same element
/// there lets the exchange cap a dead end as well. Both files it writes live in the `minecraft` namespace and
/// override the vanilla ones, which is to say that a pack replacing the same pool replaces this exchange with it.
///
/// The two hooks together are what makes an exchange likely rather than certain, and how likely is what the
/// `village_probe` game test measured: over 1553 placements of the village structure set on three seeds, which
/// became 366 villages, the share left without an exchange is ten and a half per cent of the 190 plains villages,
/// eleven of the 45 taiga, twelve of the 34 snowy, fifteen of the 68 savanna and seventeen of the 29 desert, where
/// the exchange is the largest building a village draws and often fits nowhere. A village carries two to three
/// exchanges on average, most of them at a dead end.
public final class FxVillages implements DataProvider {

    /// Village styles the exchange is built in. The zombie villages keep their own pools untouched, so an
    /// abandoned village is the one kind without an exchange.
    public static final List<String> STYLES = List.of("plains", "desert", "savanna", "snowy", "taiga");

    /// Weight of the exchange among the houses of a pool, where a vanilla pool weighs 68 to 87 in total. A
    /// village draws ten to eighteen houses, so this is the hook that puts an exchange in the middle of one. The
    /// share the exchange holds of a pool is the chance a house slot draws it, and eight against pools of that
    /// size is nine to eleven per cent, where the twelve this opened at was twelve to fifteen: a cut of thirty
    /// per cent, which is what the balance asks for.
    public static final int HOUSE_WEIGHT = 8;

    /// Weight of the exchange among the terminators of a pool, where a vanilla pool holds two to five elements of
    /// weight one each. This is the hook that carries a village: the dead ends of one are ten to nineteen, more
    /// than its houses, and a street that could not continue is where a whole building fits. One is the smallest
    /// weight a pool element can carry, so the thirty per cent cut lands on the house pools and the chance that a
    /// dead end is capped by an exchange stays where it was.
    public static final int TERMINATOR_WEIGHT = 1;

    /// Styles that weigh the exchange higher among their terminators. A desert village is the densest of the
    /// five, so the exchange fits between its houses less often than anywhere else and its dead ends are the only
    /// placement left to it: at the weight of the other styles a tenth of its villages would be without one.
    private static final Map<String, Integer> TERMINATOR_WEIGHTS = Map.of("desert", 4);

    private static final String POOLS = "worldgen/template_pool/village/";

    private final PackOutput output;
    private final ResourceManager resources;

    public FxVillages(PackOutput output, ResourceManager resources) {
        this.output = output;
        this.resources = resources;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> tasks = new ArrayList<>();
        for (String style : STYLES) {
            tasks.add(save(cache, style, "houses", HOUSE_WEIGHT));
            tasks.add(save(cache, style, "terminators", TERMINATOR_WEIGHTS.getOrDefault(style, TERMINATOR_WEIGHT)));
        }
        return CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "FX EASY village pools";
    }

    private CompletableFuture<?> save(CachedOutput cache, String style, String pool, int weight) {
        JsonObject file = read(Identifier.withDefaultNamespace(POOLS + style + "/" + pool + ".json"));
        elements(file).add(exchange(weight));
        return DataProvider.saveStable(cache, file, this.output
                .createPathProvider(PackOutput.Target.DATA_PACK, POOLS + style)
                .json(Identifier.withDefaultNamespace(pool)));
    }

    /// The pool as the game ships it. A pool that cannot be read is a datagen failure and never an empty file: the
    /// exchange would otherwise be written over a pool with no houses left in it.
    private JsonObject read(Identifier file) {
        Resource resource = this.resources.getResource(file)
                .orElseThrow(() -> new IllegalStateException("village pool " + file + " is missing"));
        try (InputStream stream = resource.open()) {
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static JsonArray elements(JsonObject pool) {
        JsonElement elements = pool.get("elements");
        if (elements == null || !elements.isJsonArray()) {
            throw new IllegalStateException("a village pool without elements cannot be extended");
        }
        return elements.getAsJsonArray();
    }

    /// One exchange as a pool element: rigid like every village house and with no processors of its own. It is a
    /// plain single pool element and not the legacy one, because the building carves its own interior out with air
    /// and the legacy element skips air.
    private static JsonObject exchange(int weight) {
        JsonObject element = new JsonObject();
        element.addProperty("element_type", "minecraft:single_pool_element");
        element.addProperty("location", FxStructures.EXCHANGE.toString());
        JsonObject processors = new JsonObject();
        processors.add("processors", new JsonArray());
        element.add("processors", processors);
        element.addProperty("projection", "rigid");
        JsonObject entry = new JsonObject();
        entry.add("element", element);
        entry.addProperty("weight", weight);
        return entry;
    }
}
