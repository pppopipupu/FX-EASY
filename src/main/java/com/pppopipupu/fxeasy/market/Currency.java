package com.pppopipupu.fxeasy.market;

import module java.base;

import net.minecraft.resources.Identifier;

/// One legal tender of the platform, exactly as its configuration defines it. This economy knows two kinds of
/// quantity: a count of items of a currency, and an exchange rate between two currencies, which is items of the
/// quote per item of the base. Nothing is measured in a third unit, so a currency carries no value of its own,
/// only the numbers the market prices it with and the numbers the world economy accounts it for.
///
/// [initialPrice] is the scale the world opens on: only the ratios between the definitions are meaningful, so it
/// fixes the starting rates and nothing else. An empty [item] is a currency that trades but cannot be deposited
/// or withdrawn, and [atomsPerItem] is how many world atoms one of its items is worth, which is what lets a gold
/// nugget and a gold ingot feed the same currency. [name] is the display name and [names] are the ones a
/// language reads it as, so a world can name its own currencies in every language its players speak.
public record Currency(String id, String code, String name, Map<String, String> names, Optional<Identifier> item,
        double initialPrice, double dailySigma, double annualRate, long atomsPerItem, double capacity,
        double supplyWeight, double demandWeight) {

    public static final int MAX_ID_LENGTH = 24;
    public static final int MAX_CODE_LENGTH = 6;
    public static final int MAX_NAME_LENGTH = 24;
    /// Languages one definition may name itself in. The panel draws one of them, so this only bounds a file.
    public static final int MAX_NAMES = 8;

    public Currency {
        id = require(id, "id");
        code = require(code, "code");
        name = require(name, "name");
        names = Map.copyOf(Objects.requireNonNull(names, "names"));
        Objects.requireNonNull(item, "item");
        requireIdentifier(id);
        requireText(code, MAX_CODE_LENGTH, "code");
        requirePrintable(name, MAX_NAME_LENGTH, "name");
        if (names.size() > MAX_NAMES) {
            throw new IllegalArgumentException("currency " + id + " carries more than " + MAX_NAMES + " names");
        }
        for (Map.Entry<String, String> entry : names.entrySet()) {
            requireLocale(entry.getKey(), id);
            requirePrintable(entry.getValue(), MAX_NAME_LENGTH, "name");
        }
        if (!(initialPrice > 0.0D) || !Double.isFinite(initialPrice)) {
            throw new IllegalArgumentException("initial price of " + id + " must be a positive number");
        }
        if (!(dailySigma > 0.0D) || !Double.isFinite(dailySigma)) {
            throw new IllegalArgumentException("daily sigma of " + id + " must be a positive number");
        }
        if (!(annualRate >= 0.0D) || annualRate >= 1.0D || !Double.isFinite(annualRate)) {
            throw new IllegalArgumentException("annual rate of " + id + " must be a fraction below one");
        }
        if (atomsPerItem < 1L) {
            throw new IllegalArgumentException("atoms per item of " + id + " must be at least one");
        }
        if (!(capacity > 0.0D) || !Double.isFinite(capacity)) {
            throw new IllegalArgumentException("capacity of " + id + " must be a positive number");
        }
        if (!(supplyWeight >= 0.0D) || !Double.isFinite(supplyWeight)) {
            throw new IllegalArgumentException("supply weight of " + id + " must not be negative");
        }
        if (!(demandWeight >= 0.0D) || !Double.isFinite(demandWeight)) {
            throw new IllegalArgumentException("demand weight of " + id + " must not be negative");
        }
    }

    /// Display name a language reads, which is the one the definition carries for it or the plain name when it
    /// carries none. A locale is the code a language file is named after, so a client asks with its own.
    public String name(String locale) {
        return this.names.getOrDefault(locale, this.name);
    }

    /// Whether the platform can move this currency in and out of an inventory.
    public boolean backed() {
        return this.item.isPresent();
    }

    private static String require(String value, String field) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("currency " + field + " must not be empty");
        }
        return value;
    }

    /// Ids are the keys of every save, packet and log line, so they stay inside a set that survives a file name.
    private static void requireIdentifier(String id) {
        if (id.length() > MAX_ID_LENGTH) {
            throw new IllegalArgumentException("currency id " + id + " is longer than " + MAX_ID_LENGTH);
        }
        for (int index = 0; index < id.length(); index++) {
            char character = id.charAt(index);
            boolean allowed = character >= 'a' && character <= 'z'
                    || character >= '0' && character <= '9'
                    || character == '_';
            if (!allowed) {
                throw new IllegalArgumentException("currency id " + id + " must be lower case letters, digits and underscores");
            }
        }
    }

    /// The panel draws every name with the font that ships with the mod, so a definition stays inside what that
    /// face can draw.
    private static void requireText(String value, int length, String field) {
        if (value.length() > length) {
            throw new IllegalArgumentException("currency " + field + " " + value + " is longer than " + length);
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < ' ' || character > '~') {
                throw new IllegalArgumentException("currency " + field + " " + value + " must be printable ascii");
            }
        }
    }

    /// A display name is drawn in whatever language the client reads, so any printable character is allowed and
    /// only control characters and a length that would not fit a cell are refused.
    private static void requirePrintable(String value, int length, String field) {
        if (value.isEmpty()) {
            throw new IllegalArgumentException("currency " + field + " must not be empty");
        }
        if (value.length() > length) {
            throw new IllegalArgumentException("currency " + field + " " + value + " is longer than " + length);
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < ' ' || character == 0x7F) {
                throw new IllegalArgumentException("currency " + field + " must not carry control characters");
            }
        }
    }

    /// A locale is one of the codes the game reads its language files under.
    private static void requireLocale(String locale, String id) {
        if (locale.length() < 2 || locale.length() > 16) {
            throw new IllegalArgumentException("currency " + id + " carries the locale " + locale);
        }
        for (int index = 0; index < locale.length(); index++) {
            char character = locale.charAt(index);
            boolean allowed = character >= 'a' && character <= 'z'
                    || character >= '0' && character <= '9'
                    || character == '_';
            if (!allowed) {
                throw new IllegalArgumentException("currency " + id + " carries the locale " + locale);
            }
        }
    }
}
