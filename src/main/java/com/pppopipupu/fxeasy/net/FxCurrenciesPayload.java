package com.pppopipupu.fxeasy.net;

import module java.base;

import com.pppopipupu.fxeasy.FxEasy;
import com.pppopipupu.fxeasy.market.Currencies;
import com.pppopipupu.fxeasy.market.Currency;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/// The currencies the platform trades, in the order every price array of this session is indexed by. The client
/// panel has to draw the very set the server runs, so the definitions travel with the session and the client
/// registry is rebuilt from them; a definition the player wrote into a config file on their own machine never
/// decides what their panel shows.
public record FxCurrenciesPayload(List<Entry> currencies) implements CustomPacketPayload {

    public static final Type<FxCurrenciesPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(FxEasy.MODID, "currencies"));
    public static final StreamCodec<FriendlyByteBuf, FxCurrenciesPayload> STREAM_CODEC = StreamCodec.of(FxCurrenciesPayload::encode, FxCurrenciesPayload::decode);

    public FxCurrenciesPayload {
        currencies = List.copyOf(currencies);
        if (currencies.size() > Currencies.MAX) {
            throw new IllegalArgumentException("a world trades at most " + Currencies.MAX + " currencies");
        }
    }

    /// Definitions of this session, in the order the platform trades them.
    public List<Currency> definitions() {
        return this.currencies.stream().map(Entry::toCurrency).toList();
    }

    public static FxCurrenciesPayload of(List<Currency> currencies) {
        return new FxCurrenciesPayload(currencies.stream().map(Entry::of).toList());
    }

    private static void encode(FriendlyByteBuf buffer, FxCurrenciesPayload payload) {
        buffer.writeVarInt(payload.currencies.size());
        for (Entry entry : payload.currencies) {
            buffer.writeUtf(entry.id());
            buffer.writeUtf(entry.code());
            buffer.writeUtf(entry.name());
            buffer.writeVarInt(entry.names().size());
            for (Map.Entry<String, String> name : entry.names().entrySet()) {
                buffer.writeUtf(name.getKey());
                buffer.writeUtf(name.getValue());
            }
            buffer.writeUtf(entry.item());
            buffer.writeDouble(entry.initialPrice());
            buffer.writeDouble(entry.dailySigma());
            buffer.writeDouble(entry.annualRate());
            buffer.writeVarLong(entry.atomsPerItem());
            buffer.writeDouble(entry.capacity());
            buffer.writeDouble(entry.supplyWeight());
            buffer.writeDouble(entry.demandWeight());
        }
    }

    private static FxCurrenciesPayload decode(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 1 || size > Currencies.MAX) {
            throw new IllegalArgumentException("invalid currency count " + size);
        }
        List<Entry> entries = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            String id = buffer.readUtf(Currency.MAX_ID_LENGTH);
            String code = buffer.readUtf(Currency.MAX_CODE_LENGTH);
            String name = buffer.readUtf(Currency.MAX_NAME_LENGTH);
            int names = buffer.readVarInt();
            if (names < 0 || names > Currency.MAX_NAMES) {
                throw new IllegalArgumentException("invalid name count " + names);
            }
            Map<String, String> translated = new HashMap<>(names);
            for (int entry = 0; entry < names; entry++) {
                translated.put(buffer.readUtf(16), buffer.readUtf(Currency.MAX_NAME_LENGTH));
            }
            entries.add(new Entry(id, code, name, Map.copyOf(translated), buffer.readUtf(),
                    buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readVarLong(),
                    buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
        }
        return new FxCurrenciesPayload(entries);
    }

    @Override
    public Type<FxCurrenciesPayload> type() {
        return TYPE;
    }

    /// One currency as the wire carries it, with an empty item meaning a currency the platform cannot move in or
    /// out of an inventory.
    public record Entry(String id, String code, String name, Map<String, String> names, String item,
            double initialPrice, double dailySigma, double annualRate, long atomsPerItem, double capacity,
            double supplyWeight, double demandWeight) {

        static Entry of(Currency currency) {
            return new Entry(currency.id(), currency.code(), currency.name(), currency.names(),
                    currency.item().map(Identifier::toString).orElse(""), currency.initialPrice(),
                    currency.dailySigma(), currency.annualRate(), currency.atomsPerItem(), currency.capacity(),
                    currency.supplyWeight(), currency.demandWeight());
        }

        Currency toCurrency() {
            Optional<Identifier> item = this.item.isEmpty() ? Optional.empty() : Optional.of(Identifier.parse(this.item));
            return new Currency(this.id, this.code, this.name, this.names, item, this.initialPrice, this.dailySigma,
                    this.annualRate, this.atomsPerItem, this.capacity, this.supplyWeight, this.demandWeight);
        }
    }
}
