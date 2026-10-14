package com.pppopipupu.fxeasy.market;

import module java.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/// The world's own currency file, which is hand authored: what it writes has to be what it reads back, and a
/// file written before a field existed has to keep loading.
class CurrencyFileTest {

    @Test
    void aWrittenFileReadsBackAsItWasWritten() throws IOException {
        Path directory = Files.createTempDirectory("fxeasy-currencies");
        Path path = directory.resolve(CurrencyFile.FILE_NAME);
        List<Currency> wanted = List.of(
                new Currency("gold", "GLD", "Gold Ingot", Map.of("zh_cn", "金锭"),
                        Optional.of(Identifier.withDefaultNamespace("gold_ingot")), 512.0D, 0.006D, 0.020D, 9L,
                        4096.0D, 0.35D, 0.08D),
                new Currency("token", "TKN", "Token", Map.of(), Optional.empty(), 1.0D, 0.01D, 0.0D, 1L, 100.0D, 0.1D,
                        0.1D));

        CurrencyFile.write(path, wanted);

        assertTrue(Files.isRegularFile(path));
        assertEquals(wanted, CurrencyFile.load(path));
    }

    @Test
    void anEntryWithoutANameOrNamesReadsBackUnderItsCode() throws IOException {
        Path directory = Files.createTempDirectory("fxeasy-currencies");
        Path path = directory.resolve(CurrencyFile.FILE_NAME);
        Files.writeString(path, """
                {
                  "currencies": [
                    {"id": "gold", "code": "GLD", "name": "Gold Ingot", "item": "minecraft:gold_ingot",
                     "initial_price": 512.0, "daily_sigma": 0.006, "annual_rate": 0.02},
                    {"id": "dirt", "code": "DIR", "item": "minecraft:dirt", "initial_price": 1.0,
                     "daily_sigma": 0.004, "annual_rate": 0.0}
                  ]
                }
                """);

        List<Currency> loaded = CurrencyFile.load(path);

        assertEquals(2, loaded.size());
        assertEquals("Gold Ingot", loaded.getFirst().name());
        assertEquals(Set.of(), loaded.getFirst().names().keySet());
        assertEquals("DIR", loaded.getLast().name(), "a definition without a name is drawn as its code");
        assertEquals(Optional.of(Identifier.withDefaultNamespace("dirt")), loaded.getLast().item());
    }
}
