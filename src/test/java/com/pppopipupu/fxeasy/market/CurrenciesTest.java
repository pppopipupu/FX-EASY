package com.pppopipupu.fxeasy.market;

import module java.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/// Invariants of the currency registry. Every test trades a set of its own and restores the set the world had,
/// so nothing leaks into another test class in the same JVM.
class CurrenciesTest {

    @Test
    void setRefusesFewerThanTwoCurrencies() {
        List<Currency> previous = Currencies.all();
        try {
            List<Currency> single = List.of(currency("only"));
            assertThrows(IllegalArgumentException.class, () -> Currencies.set(single));
        } finally {
            Currencies.set(previous);
        }
    }

    @Test
    void setRefusesMoreThanTheBound() {
        List<Currency> previous = Currencies.all();
        try {
            List<Currency> tooMany = new ArrayList<>();
            for (int index = 0; index <= Currencies.MAX; index++) {
                tooMany.add(currency("c" + index));
            }
            assertThrows(IllegalArgumentException.class, () -> Currencies.set(tooMany));
        } finally {
            Currencies.set(previous);
        }
    }

    @Test
    void setRefusesADuplicateId() {
        List<Currency> previous = Currencies.all();
        try {
            List<Currency> duplicated = List.of(currency("gold"), currency("gold"));
            assertThrows(IllegalArgumentException.class, () -> Currencies.set(duplicated));
        } finally {
            Currencies.set(previous);
        }
    }

    @Test
    void setKeepsTheOrderItWasGivenAndIndexesIt() {
        List<Currency> previous = Currencies.all();
        List<Currency> wanted = List.of(currency("alpha"), currency("beta"), currency("gamma"));
        try {
            Currencies.set(wanted);
            assertEquals(wanted, Currencies.all());
            assertEquals(wanted.size(), Currencies.count());
            for (int index = 0; index < wanted.size(); index++) {
                assertSame(wanted.get(index), Currencies.byIndex(index));
                assertEquals(index, Currencies.index(wanted.get(index)));
            }
            assertSame(wanted.get(1), Currencies.byId("beta"));
        } finally {
            Currencies.set(previous);
        }
    }

    @Test
    void byIdAnswersNullForAnUnknownId() {
        List<Currency> previous = Currencies.all();
        try {
            Currencies.set(List.of(currency("alpha"), currency("beta")));
            assertNull(Currencies.byId("missing"));
        } finally {
            Currencies.set(previous);
        }
    }

    @Test
    void pairsHoldEveryUnorderedPairExactlyOnce() {
        List<Currency> previous = Currencies.all();
        List<Currency> wanted = List.of(currency("alpha"), currency("beta"), currency("gamma"), currency("delta"));
        try {
            Currencies.set(wanted);
            int count = Currencies.count();
            List<CurrencyPair> expected = new ArrayList<>();
            for (int base = 0; base < count; base++) {
                for (int quote = base + 1; quote < count; quote++) {
                    CurrencyPair pair = CurrencyPair.of(Currencies.byIndex(base), Currencies.byIndex(quote));
                    expected.add(pair);
                    assertEquals(Currencies.byIndex(base), pair.base());
                    assertEquals(Currencies.byIndex(quote), pair.quote());
                    assertNull(CurrencyPair.of(Currencies.byIndex(quote), Currencies.byIndex(base)),
                            "a pair only exists in the order the set names it");
                }
            }
            assertEquals(count * (count - 1) / 2, Currencies.pairs().size());
            assertEquals(expected, Currencies.pairs());
            assertEquals(expected.size(), Set.copyOf(expected).size(), "no pair is listed twice");
            for (int index = 0; index < expected.size(); index++) {
                assertSame(expected.get(index), Currencies.pair(index));
            }
        } finally {
            Currencies.set(previous);
        }
    }

    @Test
    void indexRejectsACurrencyTheWorldDoesNotTrade() {
        List<Currency> previous = Currencies.all();
        try {
            Currencies.set(List.of(currency("alpha"), currency("beta")));
            assertThrows(IllegalArgumentException.class, () -> Currencies.index(currency("stranger")));
        } finally {
            Currencies.set(previous);
        }
    }

    @Test
    void aNameFallsBackOnThePlainOne() {
        Currency named = new Currency("gold", "GLD", "Gold Ingot", Map.of("zh_cn", "金锭"), Optional.empty(), 1.0D,
                0.01D, 0.0D, 1L, 100.0D, 0.1D, 0.1D);
        assertEquals("金锭", named.name("zh_cn"));
        assertEquals("Gold Ingot", named.name("en_us"), "a language the definition does not name reads the plain name");
        assertEquals("Gold Ingot", named.name(), "the plain name stays what the server prints");
    }

    @Test
    void aDefinitionRefusesNamesThePanelCannotDraw() {
        assertThrows(IllegalArgumentException.class,
                () -> new Currency("gold", "GLD", "Gold", Map.of("zh-cn", "金锭"), Optional.empty(), 1.0D, 0.01D, 0.0D,
                        1L, 100.0D, 0.1D, 0.1D));
        assertThrows(IllegalArgumentException.class,
                () -> new Currency("gold", "GLD", "Gold", Map.of("zh_cn", "金\n锭"), Optional.empty(), 1.0D, 0.01D,
                        0.0D, 1L, 100.0D, 0.1D, 0.1D));
        assertThrows(IllegalArgumentException.class,
                () -> new Currency("gold", "GLD", "Gold", Map.of("x".repeat(Currency.MAX_NAME_LENGTH + 1), "Gold"),
                        Optional.empty(), 1.0D, 0.01D, 0.0D, 1L, 100.0D, 0.1D, 0.1D));
        Map<String, String> tooMany = new HashMap<>();
        for (int index = 0; index <= Currency.MAX_NAMES; index++) {
            tooMany.put("l" + index, "Gold");
        }
        assertThrows(IllegalArgumentException.class,
                () -> new Currency("gold", "GLD", "Gold", tooMany, Optional.empty(), 1.0D, 0.01D, 0.0D, 1L, 100.0D,
                        0.1D, 0.1D));
    }

    private static Currency currency(String id) {
        return new Currency(id, id.substring(0, Math.min(3, id.length())).toUpperCase(Locale.ROOT), id, Map.of(),
                Optional.empty(), 1.0D, 0.01D, 0.0D, 1L, 100.0D, 0.1D, 0.1D);
    }
}
