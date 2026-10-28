package com.dansplugins.merchants;

import java.util.Objects;
import java.util.UUID;

/**
 * A merchant record: the villager entity that stands in for the merchant, its name, and where it was placed.
 */
public record Merchant(UUID entityId, String name, String worldName, double x, double y, double z) {

    public Merchant {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(worldName, "worldName");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A merchant needs a name.");
        }
        name = name.strip();
    }
}
