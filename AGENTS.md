# FX EASY - agent guide

NeoForge mod for Minecraft 26.1.2 (NeoForge 26.1.2.115, Java 25, Gradle 9.2.1).
Mod id `fxeasy`, package root `com.pppopipupu.fxeasy`, metadata template `src/main/templates/META-INF/neoforge.mods.toml`.

## Environment

- Shell is Git Bash on Windows. Run the wrapper as `bash gradlew <task>` (`gradlew` alone is not on the path and the harness cannot exec the extensionless script); `JAVA_HOME` must point at a JDK 25.
- `run/` is gitignored runtime state: never commit it and never treat what is in it as a source of truth.
- `src/generated/resources` is datagen output and a resource root: never edit or commit-by-hand files in it.
- `src/main/resources` holds only what datagen cannot produce: mixin config, structures, hand-authored data, shaders, textures, fonts.

## Commands

| Command | Purpose |
| --- | --- |
| `bash gradlew build` | compile, unit tests, assemble the jar |
| `bash gradlew test` | unit tests, pure logic |
| `bash gradlew runGameTestServer` | game tests on a dedicated server; a failed required test fails the build |
| `bash gradlew runData` | regenerate `src/generated/resources` |
| `bash gradlew runClient`, `runServer` | development runs |
| `bash gradlew runClientProbe` | development client that opens the panel itself for `-PprobeTerminal=<ticks>` and writes screenshots into `run/screenshots/`; needs a world named `probe` |
| `scripts/client-e2e.sh [test.json]` | end to end run of the shipped jar on a production client, headless |
| `MCRT_RENDER=1 scripts/client-e2e.sh` | the same on a real GL context |
| `scripts/client-regression.sh` | panel regression in a development client (headed) |

- Probe knobs: `-PprobeShots=<tick,...>`, `-PprobeTerminalTick`, `-PprobeWidth`/`-PprobeHeight`, `-PprobeCurrency=<n>` with `-PprobeCurrencyId=<id>`, `-PprobeClicks=<tick>:<x>:<y>` or `<tick>:<target>`; a scripted click is a press the panel releases in the same frame.
- E2E knobs: `MCRT_TERMINAL_TICKS`, `MCRT_CLICKS`, `MCRT_TYPES`, `MCRT_MIN_TESTS` and the driver properties `-Dfxeasy.e2e.openTerminal/clicks/types/register`.
- Headed clients are the user's to run: `runClient`, `runClientProbe` and `client-regression.sh`. Headless ones are the agent's: `runServer`, `runGameTestServer` and `client-e2e.sh`. A green E2E run is evidence about the shipped artifact, not about the development classpath.

## Code rules

These are mandatory for every Java file.

1. **No fully qualified names.** Import the type and use the simple name, including nested types (`Map.Entry`, not `java.util.Map.Entry`). The only exception is reflection, where the name cannot be imported: `Class.forName("net.minecraft.world.item.Item")`, `MethodHandles.lookup().findStatic(...)`, mixin `@Mixin(targets = "...")` strings.
2. **No comments.** Never write `//`, `/* */` or trailing comments, and no commented-out code. Javadoc is allowed only when it states something the signature cannot: an invariant, a unit, a threading or ownership contract, a non-obvious reason for the implementation. Javadoc that restates the method name is deleted.
3. **Java 25, stable features only** (never `--enable-preview`): `var`, records, sealed interfaces, record patterns, `switch` patterns with `when` guards, text blocks, unnamed variables `_`, `SequencedCollection` (`getFirst`, `getLast`, `reversed`), `Math.clamp`, `Stream.gather`, and `import module java.base;` instead of individual `java.*` imports. Explicit single-type imports still win over the module import, so `import module java.base;` plus specific imports of Minecraft/NeoForge types is the normal file header.
4. **Registration** happens through the `DeferredRegister` constants next to the content they belong to (start in `FxEasy`) and is wired on the mod event bus in the mod constructor. Never touch a registry from a static initializer, and never create a registry singleton with `new` outside a `register(...)` lambda.
5. **Sides.** Client-only entrypoints are annotated `@Mod(value = FxEasy.MODID, dist = Dist.CLIENT)` or `@EventBusSubscriber(modid = FxEasy.MODID, value = Dist.CLIENT)` and live under `com.pppopipupu.fxeasy.client` (`FxEasyClient` next to `FxEasy`). Never reference a client-only type from code that runs on both sides; guard with `Dist`/`FMLEnvironment` instead.
6. **Mixins** live in `com.pppopipupu.fxeasy.mixin` and must be listed in `src/main/resources/fxeasy.mixins.json`. Prefer NeoForge events, capabilities and extension interfaces over mixins; `@Overwrite` needs explicit user approval.
7. **Identity and logging.** Use `FxEasy.MODID` for the namespace and `FxEasy.LOGGER` for log output (`System.out` is forbidden). Identifiers are built with `Identifier.fromNamespaceAndPath(FxEasy.MODID, ...)`.
8. **Dependencies.** Do not add a library, or a new Gradle plugin, without asking.

## Data rules

- All `assets/` and `data/` JSON is generated: add a `DataProvider`, register it in a `GatherDataEvent.Client` handler on the mod bus (`event.createProvider(...)`, `event.createDatapackRegistryObjects(...)`), then run `bash gradlew runData`. Never hand-edit the output.
- User-visible text is a translation key (`Component.translatable`) resolved by a `LanguageProvider`; literals in gameplay code are forbidden. A currency's name is the exception that proves the rule: it is a field of its definition in the world's data file (`Currency#names`, resolved by `FxText.name`), so nothing may build a key out of a currency id.
- `serverconfig/fxeasy-currencies.json` (`market/CurrencyFile`, defaults in `market/Currencies`) and `serverconfig/fxeasy-lounge.json` (`shop/LoungeFile`) are hand-authored per world and no command regenerates them: never generate or overwrite them, and keep them tolerant - both clamp what they read and a file that does not parse is reported and ignored, so a typo costs a world a feature and never its start.
- Registries that exist in datapacks only (worldgen, damage types, enchantments, ...) go through `createDatapackRegistryObjects` and `RegistrySetBuilder`, not through JSON files.
- Game test scenes are `.nbt` structures hand-authored into `src/main/resources/data/fxeasy/structure/`, or `minecraft:empty` when a test needs no scene.
- The village exchange is the one generated structure: `datagen/FxStructures` writes `fxeasy:fx_exchange` cell by cell (a 9x6x9 placeholder house with a doorway, windows, two torches, a chest and a jigsaw), and `datagen/FxVillages` appends it to each `minecraft:village/<style>/houses` pool of the five living styles. The pool files are built by reading the vanilla pool of the running version and copying its elements before the exchange is added at weight 3, because a restated pool would silently drop a house of a future version; item stacks inside the structure are written as plain `{Slot, id, count}` tags, because a datagen run has no bound item components to build an `ItemStack` with.
- The bundled TrueType font is the whole Maple Mono NL CN Medium (`assets/fxeasy/font/terminal.ttf`, SIL OFL beside it in `OFL.txt`), never a subset: every character the panel can draw - the mod's own language table, the names of the default currency set, the item names of a world's lounge pool and of anything a pack renames - has to be in it, and a character outside it draws as a box rather than as itself. `terminal.json`/`terminal_big.json` are the panel's font set (size 9 and 18) and pin `size`/`oversample` to the panel's design units: changing one without the other makes the text blurry.
- A shader change is checked with `python scripts/check-shaders.py` (needs `moderngl`), which inlines `#moj_import` the way `ShaderManager` does and compiles both stages through the real driver: a registered `.vsh`/`.fsh` is preloaded while the resource packs load, so a broken one fails the whole reload and kills the client on its first GUI draw, and a headless run stubs the compiler and never sees it.

## Design rules

- Everything authoritative is server side; the client is a read-only mirror and never sends money. A price the panel draws before a purchase is a quote: the checkout of the shop sends the plan and the currency the player picked, and the server converts at its own rate and charges its own price (`ShopEngine.priceMicro` is the one rule both sides quote with).
- Nothing is measured in anything but items: a price is only a ratio, every rate is `MarketEngine#rate(from, to)`, and a sum over currencies names the currency it is quoted in.
- The market runs on the economy's own monotonic clock, never on world time, so sleeping or `/time set` cannot skip a day or an event.
- A saved price is `anchor + fundamental + event`; restore it through `MarketEngine.restoreComponents`, or a load re-applies the whole revaluation.
- Randomness is separated per concern (prices, event outcomes, execution, lounge gifts): a player must never be able to re-roll a fill or pick the news.
- The currency set is a definition, not a code path: any currency backs any pair and settles in its own margin currency, and one whose definition names no item is still traded - it just cannot leave the platform.
- Closing is never refused; opening, depositing and withdrawing need an open account. Exposure limits refuse only what would add risk, and are measured at the reference value.
- Committed facts are counted in atoms, never in items that moved, and a block a player placed is never a natural find.
- How far a world's rates swing is a per world multiple of the calibration the shipped constants carry (`MarketEngine.DEFAULT_VOLATILITY_SCALE`, config `market.volatility_scale`, read when a world loads and never on the tick path). The noise and the reference the volatility regime is measured against scale with it, so a world tuned wilder does not read as a nervous one and its spreads, swaps and haircuts keep their calibration.
- The phone has two sources and both are data: the crafting recipe of `datagen/FxRecipes` and the chest of the village exchange that `datagen/FxStructures` builds. The exchange's other nine slots are the margin a player who has never traded opens a first position with, so what it carries (ten stacks of the base currency of the default set) is a balance decision, not decoration - a world whose set trades something else replaces the structure file, which is ordinary datapack content.
- The panel draws in design units (one design unit is two physical pixels at scale 1), so the mouse and the keyboard are divided by that scale on the way in and text entry is `client/panel/FxField` - a vanilla widget would draw its own font at GUI scale.

## Testing

Four layers, cheapest first: a behaviour belongs to the cheapest layer that can express it.

- Unit tests (`src/test/java/com/pppopipupu/fxeasy/...`, `bash gradlew test`) cover pure logic with Minecraft and NeoForge classes on the classpath. Deterministic and order-independent: no sleeps, no shared mutable state, no network. `@ExtendWith(EphemeralTestServerProvider.class)` gives a live server that loads no level, so anything level-bound belongs to the next layer.
- Game tests (`src/main/java/com/pppopipupu/fxeasy/gametest/`, shipped in the jar) cover behaviour inside a level. Register the function through `DeferredRegister` and the instance through `RegisterGameTestsEvent` on the mod bus, assert with the `GameTestHelper` API and never by reading logs, and copy the shape of an existing test.
- Client E2E (`scripts/client-e2e.sh`) runs the shipped jar on a production client. The harness cannot click or type while a screen blocks the client thread, so `-Dfxeasy.e2e.openTerminal` with `MCRT_CLICKS`/`MCRT_TYPES` makes the client drive the panel itself through the target names of `client/FxScriptedClicks`; the assertions are read back from `run/e2e/game/logs/latest.log`. Test files stay small: one scenario per file, short timeouts, no dependence on wall-clock time or the local display.
- Panel regression (`scripts/client-regression.sh`) is the only layer that drives the panel: it clicks the cells of the frame and types into its boxes, asserting the client mirror and the drawn cells. It needs a player name the world has never seen, or its first steps fail on their own.
- mc-runtime-test cannot discover this mod's game tests on 26.1.x (it looks for test blocks near the player), so `MCRT_MIN_TESTS` stays 0; re-check that when `MRT_VERSION` is bumped.
- Harness versions are pinned in `scripts/client-e2e.sh`; bump them together with `minecraft_version`/`neo_version`.
- Re-measure the balance recorded in `.agent/fx-world-market-calibration.md` when `economy/EconomyConstants`, `market/FeeModel`, the event catalog or `MarketEngine`'s noise and volatility constants (`RESIDUAL_NOISE_SHARE`, `DEFAULT_VOLATILITY_SCALE`) change: the driver of that run was a temporary program that is gone, so write it again and update the file with what the new run measured and what it did not.

## Working agreements

- NEVER batch-edit Java sources with a script (`python`, `sed`, `awk`, a codegen helper): the grammar is too sensitive for text surgery and one wrong brace silently truncates a whole file. Every change to `src/main/java`, `src/test/java` and `src/generated` sources is made by hand, one file at a time, or with an AST-aware tool that reparses what it writes; the shell is for reading, searching (`rg`) and running builds. A scripted sweep is allowed only over data that is not source (JSON, language files, generated assets).
- When a requirement is unclear, or two designs are materially different (data model, public API, dependency, file layout), ask the user before implementing.
- Delete dead code, dead config and dead locale entries instead of keeping them commented out. `README.md` is the short overview a player reads (Chinese, one sentence and example screenshots from `docs/`), and the detailed English description of every feature lives in `.agent/README.md`; a feature change updates whichever of the two it belongs to, and this file.
- Do not commit `run/` artifacts, generated files that datagen can rebuild, or IDE files. Example screenshots are the one exception, and only in one direction: a curated copy of a probe output belongs in `docs/` for `README.md` to point at, while `run/` itself stays out of the tree.
- `.agent/` is gitignored. `.agent/fx-world-market-calibration.md` was committed before that rule, so it stays tracked and its edits still show up, while anything else in the folder is local-only and needs `git add -f` to travel with the repository.
