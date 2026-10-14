package com.pppopipupu.fxeasy.economy;

import module java.base;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;

/// One amount of one currency in a saved row, keyed by the id the world trades it under. Keeping every amount
/// next to its id means adding or reordering a currency never reassigns an amount to the wrong one.
public record CurrencyAmount(String currency, double amount) {

    public static final Codec<CurrencyAmount> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("currency").forGetter(CurrencyAmount::currency),
            Codec.DOUBLE.fieldOf("amount").forGetter(CurrencyAmount::amount)).apply(instance, CurrencyAmount::new));

    /// Codec of a whole row. A list of [CurrencyAmount] is the current shape; the older five named fields are
    /// still read and turned into the same list, so a world saved before the list shape loads.
    public static final Codec<List<CurrencyAmount>> LIST_CODEC =
            CODEC.listOf().withAlternative(LegacyAmounts.CODEC, LegacyAmounts::amounts);

    public CurrencyAmount {
        if (currency == null || currency.isEmpty()) {
            throw new IllegalArgumentException("currency amount needs a currency id");
        }
        if (!Double.isFinite(amount)) {
            throw new IllegalArgumentException("currency amount of " + currency + " must be finite");
        }
    }

    /// An all zero amount list, one entry per traded currency.
    public static List<CurrencyAmount> zero() {
        return of(new double[Currencies.count()]);
    }

    /// The amounts of one double array indexed by [Currencies#index], in the world's currency order.
    public static List<CurrencyAmount> of(double[] values) {
        if (values.length != Currencies.count()) {
            throw new IllegalArgumentException("expected one amount per currency");
        }
        List<CurrencyAmount> amounts = new ArrayList<>(values.length);
        for (int index = 0; index < values.length; index++) {
            amounts.add(new CurrencyAmount(Currencies.byIndex(index).id(), values[index]));
        }
        return List.copyOf(amounts);
    }

    /// The double array a saved list stands for, indexed by [Currencies#index]. An amount whose currency this
    /// world does not trade is dropped.
    public static double[] toArray(List<CurrencyAmount> amounts) {
        double[] values = new double[Currencies.count()];
        for (CurrencyAmount amount : amounts) {
            Currency currency = Currencies.byId(amount.currency());
            if (currency == null) {
                continue;
            }
            values[Currencies.index(currency)] = amount.amount();
        }
        return values;
    }

    /// The amount of one currency in a saved list, zero when the list does not name it.
    public static double get(List<CurrencyAmount> amounts, Currency currency) {
        String id = currency.id();
        for (CurrencyAmount amount : amounts) {
            if (amount.currency().equals(id)) {
                return amount.amount();
            }
        }
        return 0.0D;
    }

    /// The five named fields a save from before the list shape holds, so an existing world still loads.
    private record LegacyAmounts(double dirt, double gold, double emerald, double diamond, double netherite) {

        static final Codec<LegacyAmounts> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.DOUBLE.fieldOf("dirt").forGetter(LegacyAmounts::dirt),
                Codec.DOUBLE.fieldOf("gold").forGetter(LegacyAmounts::gold),
                Codec.DOUBLE.fieldOf("emerald").forGetter(LegacyAmounts::emerald),
                Codec.DOUBLE.fieldOf("diamond").forGetter(LegacyAmounts::diamond),
                Codec.DOUBLE.fieldOf("netherite").forGetter(LegacyAmounts::netherite))
                .apply(instance, LegacyAmounts::new));

        List<CurrencyAmount> amounts() {
            return List.of(new CurrencyAmount("dirt", this.dirt), new CurrencyAmount("gold", this.gold),
                    new CurrencyAmount("emerald", this.emerald), new CurrencyAmount("diamond", this.diamond),
                    new CurrencyAmount("netherite", this.netherite));
        }
    }
}
