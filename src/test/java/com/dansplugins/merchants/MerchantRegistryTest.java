package com.dansplugins.merchants;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MerchantRegistryTest {

    private static Merchant merchant(String name) {
        return new Merchant(UUID.randomUUID(), name, "world", 0, 64, 0);
    }

    @Test
    void namesAreLookedUpIgnoringCase() {
        MerchantRegistry registry = new MerchantRegistry();
        Merchant baker = merchant("Baker");
        registry.add(baker);

        assertSame(baker, registry.getByName("bAKER").orElseThrow());
        assertTrue(registry.isNameTaken(" baker "));
        assertFalse(registry.isNameTaken("Smith"));
    }

    @Test
    void duplicateNameIsRejected() {
        MerchantRegistry registry = new MerchantRegistry();
        registry.add(merchant("Baker"));

        assertThrows(IllegalArgumentException.class, () -> registry.add(merchant("BAKER")));
        assertEquals(1, registry.all().size());
    }

    @Test
    void duplicateEntityIsRejected() {
        MerchantRegistry registry = new MerchantRegistry();
        Merchant baker = merchant("Baker");
        registry.add(baker);

        Merchant sameEntity = new Merchant(baker.entityId(), "Smith", "world", 0, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> registry.add(sameEntity));
        assertFalse(registry.isNameTaken("Smith"));
    }

    @Test
    void isMerchantRecognisesOnlyRegisteredEntities() {
        MerchantRegistry registry = new MerchantRegistry();
        Merchant baker = merchant("Baker");
        registry.add(baker);

        assertTrue(registry.isMerchant(baker.entityId()));
        assertFalse(registry.isMerchant(UUID.randomUUID()));
    }

    @Test
    void removeForgetsNameAndEntity() {
        MerchantRegistry registry = new MerchantRegistry();
        Merchant baker = merchant("Baker");
        registry.add(baker);

        assertSame(baker, registry.remove("baker").orElseThrow());
        assertFalse(registry.isMerchant(baker.entityId()));
        assertFalse(registry.isNameTaken("Baker"));
        assertTrue(registry.remove("Baker").isEmpty());
    }

    @Test
    void allIsSortedByNameIgnoringCase() {
        MerchantRegistry registry = new MerchantRegistry();
        registry.add(merchant("smith"));
        registry.add(merchant("Baker"));
        registry.add(merchant("Cooper"));

        List<String> names = registry.all().stream().map(Merchant::name).toList();
        assertEquals(List.of("Baker", "Cooper", "smith"), names);
    }
}
