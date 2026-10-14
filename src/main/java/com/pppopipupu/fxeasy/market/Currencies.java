package com.pppopipupu.fxeasy.market;

import module java.base;

import net.minecraft.resources.Identifier;

/// The currencies a world trades, in the order their configuration names them. Every price, save entry and
/// packet refers to a currency by id, and its position in this list is what the arrays of the market and the
/// accounts are indexed by, so the order never changes while a world runs.
///
/// The set is loaded from configuration on both sides; a client that joins a server takes the set the server
/// runs, so a panel always draws the currencies its platform trades.
public final class Currencies {

    /// Most currencies a world may define. The bound keeps a set inside what the panel, the packets and the
    /// candle series are built for.
    public static final int MAX = 16;
    private static final int MIN = 2;

    private static volatile Set current = new Set(defaults());

    private Currencies() {
    }

    private record Set(List<Currency> currencies, Map<String, Currency> byId, Map<Currency, Integer> indices,
            List<CurrencyPair> pairs) {

        Set(List<Currency> currencies) {
            this(List.copyOf(currencies), index(currencies), indices(currencies), pairs(currencies));
        }

        private static Map<String, Currency> index(List<Currency> currencies) {
            Map<String, Currency> byId = new HashMap<>(currencies.size());
            for (Currency currency : currencies) {
                if (byId.put(currency.id(), currency) != null) {
                    throw new IllegalArgumentException("currency " + currency.id() + " is defined twice");
                }
            }
            return Map.copyOf(byId);
        }

        private static Map<Currency, Integer> indices(List<Currency> currencies) {
            Map<Currency, Integer> indices = new HashMap<>(currencies.size());
            for (int index = 0; index < currencies.size(); index++) {
                indices.put(currencies.get(index), index);
            }
            return Map.copyOf(indices);
        }

        private static List<CurrencyPair> pairs(List<Currency> currencies) {
            List<CurrencyPair> pairs = new ArrayList<>();
            for (int base = 0; base < currencies.size(); base++) {
                for (int quote = base + 1; quote < currencies.size(); quote++) {
                    pairs.add(new CurrencyPair(currencies.get(base), currencies.get(quote), pairs.size()));
                }
            }
            return List.copyOf(pairs);
        }
    }

    /// Trades the given set from now on. Every world runs one set for its whole life, so this is called by the
    /// configuration load and once per client when the platform reports what it trades.
    public static void set(List<Currency> currencies) {
        if (currencies.size() < MIN || currencies.size() > MAX) {
            throw new IllegalArgumentException("a world trades between " + MIN + " and " + MAX + " currencies, not "
                    + currencies.size());
        }
        current = new Set(currencies);
    }

    public static List<Currency> all() {
        return current.currencies();
    }

    public static int count() {
        return current.currencies().size();
    }

    public static Currency byIndex(int index) {
        return current.currencies().get(index);
    }

    /// Currency with that id, or null when this world does not trade it.
    public static Currency byId(String id) {
        return current.byId().get(id);
    }

    public static int index(Currency currency) {
        Integer index = current.indices().get(currency);
        if (index == null) {
            throw new IllegalArgumentException("currency " + currency.id() + " is not traded by this world");
        }
        return index;
    }

    public static List<CurrencyPair> pairs() {
        return current.pairs();
    }

    public static CurrencyPair pair(int index) {
        return current.pairs().get(index);
    }

    /// The set a world starts on when no configuration names one, which is the five items this economy opened
    /// with.
    public static List<Currency> defaults() {
        return List.of(
                new Currency("dirt", "DIR", "Dirt", Map.of("zh_cn", "土块"),
                        Optional.of(Identifier.withDefaultNamespace("dirt")), 1.0D, 0.004D, 0.000D, 1L, 16384.0D,
                        0.08D, 0.02D),
                new Currency("gold", "GLD", "Gold Ingot", Map.of("zh_cn", "金锭"),
                        Optional.of(Identifier.withDefaultNamespace("gold_ingot")), 512.0D, 0.006D, 0.020D, 9L,
                        4096.0D, 0.35D, 0.08D),
                new Currency("emerald", "EMR", "Emerald", Map.of("zh_cn", "绿宝石"),
                        Optional.of(Identifier.withDefaultNamespace("emerald")), 1024.0D, 0.008D, 0.035D, 1L,
                        2048.0D, 0.35D, 0.08D),
                new Currency("diamond", "DIA", "Diamond", Map.of("zh_cn", "钻石"),
                        Optional.of(Identifier.withDefaultNamespace("diamond")), 4096.0D, 0.011D, 0.050D, 1L,
                        96.0D, 0.35D, 0.08D),
                new Currency("netherite", "NIT", "Netherite Ingot", Map.of("zh_cn", "下界合金锭"),
                        Optional.of(Identifier.withDefaultNamespace("netherite_ingot")), 32768.0D, 0.014D, 0.080D, 4L,
                        8.0D, 0.35D, 0.08D));
    }
}
