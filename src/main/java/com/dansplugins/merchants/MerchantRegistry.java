package com.dansplugins.merchants;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The merchants that exist on this server, looked up by name (ignoring case) or by entity.
 */
public final class MerchantRegistry {

    private final Map<String, Merchant> byName = new HashMap<>();
    private final Map<UUID, Merchant> byEntityId = new HashMap<>();

    /**
     * @throws IllegalArgumentException if a merchant already has this name or this entity
     */
    public void add(Merchant merchant) {
        String key = key(merchant.name());
        if (byName.containsKey(key)) {
            throw new IllegalArgumentException("A merchant named '" + merchant.name() + "' already exists.");
        }
        if (byEntityId.containsKey(merchant.entityId())) {
            throw new IllegalArgumentException("Entity " + merchant.entityId() + " is already a merchant.");
        }
        byName.put(key, merchant);
        byEntityId.put(merchant.entityId(), merchant);
    }

    public Optional<Merchant> remove(String name) {
        Merchant removed = byName.remove(key(name));
        if (removed != null) {
            byEntityId.remove(removed.entityId());
        }
        return Optional.ofNullable(removed);
    }

    public Optional<Merchant> getByName(String name) {
        return Optional.ofNullable(byName.get(key(name)));
    }

    public boolean isNameTaken(String name) {
        return byName.containsKey(key(name));
    }

    public boolean isMerchant(UUID entityId) {
        return byEntityId.containsKey(entityId);
    }

    /**
     * @return every merchant, sorted by name ignoring case
     */
    public List<Merchant> all() {
        List<Merchant> merchants = new ArrayList<>(byName.values());
        merchants.sort(Comparator.comparing(merchant -> key(merchant.name())));
        return merchants;
    }

    private static String key(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }
}
