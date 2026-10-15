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

/// Extends the house pool of every village style with the exchange. A village draws its buildings from
/// `minecraft:village/<style>/houses`, and the only way into that pool is the pool itself: this provider reads the
/// file the game ships, keeps every element of it and appends the exchange, so a village keeps all the houses it
/// always had and gains one more. The file it writes lives in the `minecraft` namespace and overrides the vanilla
/// one, which is to say that a pack replacing the same pool replaces this exchange with it.
public final class FxVillages implements DataProvider {

    /// Village styles the exchange is built in. The zombie villages keep their own house pools untouched, so an
    /// abandoned village is the one kind without an exchange.
    public static final List<String> STYLES = List.of("plains", "desert", "savanna", "snowy", "taiga");

    /// Weight of the exchange among the houses of a pool. A vanilla pool weighs 68 to 87 in total, so the
    /// exchange is drawn on roughly one placement in twenty five, which is the order of a village's library.
    public static final int WEIGHT = 3;

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
            tasks.add(save(cache, style));
        }
        return CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "FX EASY village pools";
    }

    private CompletableFuture<?> save(CachedOutput cache, String style) {
        JsonObject pool = read(Identifier.withDefaultNamespace(POOLS + style + "/houses.json"));
        elements(pool).add(exchange());
        return DataProvider.saveStable(cache, pool, this.output
                .createPathProvider(PackOutput.Target.DATA_PACK, POOLS + style)
                .json(Identifier.withDefaultNamespace("houses")));
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
    private static JsonObject exchange() {
        JsonObject element = new JsonObject();
        element.addProperty("element_type", "minecraft:single_pool_element");
        element.addProperty("location", FxStructures.EXCHANGE.toString());
        JsonObject processors = new JsonObject();
        processors.add("processors", new JsonArray());
        element.add("processors", processors);
        element.addProperty("projection", "rigid");
        JsonObject entry = new JsonObject();
        entry.add("element", element);
        entry.addProperty("weight", WEIGHT);
        return entry;
    }
}
