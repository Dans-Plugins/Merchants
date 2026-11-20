package com.dansplugins.merchants;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A merchant record: the villager entity that stands in for the merchant, its name, and where it was placed.
 */
public record Merchant(UUID entityId, String name, String worldName, double x, double y, double z) {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public Merchant {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(worldName, "worldName");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A merchant needs a name.");
        }
        name = normalizeName(name);
    }

    /**
     * Strips the ends of a name and collapses each run of whitespace inside it to one space.
     * Bukkit splits a command on single spaces, so a name typed with two spaces in a row would
     * otherwise keep both and no longer match the same name typed with one.
     */
    static String normalizeName(String name) {
        return WHITESPACE.matcher(name.strip()).replaceAll(" ");
    }
}
