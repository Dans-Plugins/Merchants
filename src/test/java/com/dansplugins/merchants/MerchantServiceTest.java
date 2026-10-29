package com.dansplugins.merchants;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers delete, and the checks create makes before spawning. The spawn itself needs a running server: a
 * stand-in World or Villager cannot be built, because their proxy classes load registry-backed
 * types (Biome, Villager.Profession) whose static initialisers call into Bukkit.
 */
class MerchantServiceTest {

    @TempDir
    Path dir;

    private MerchantService service(MerchantRegistry registry) {
        MerchantStorage storage = new MerchantStorage(dir.resolve("merchants.yml").toFile(), Logger.getAnonymousLogger());
        return new MerchantService(registry, storage);
    }

    // No world: reaching the spawn step fails with "The location has no world." instead.
    private static final Location NO_WORLD = new Location(null, 0, 64, 0);

    @Test
    void takenNameIsRejectedBeforeSpawning() {
        MerchantRegistry registry = new MerchantRegistry();
        registry.add(new Merchant(UUID.randomUUID(), "Baker", "world", 0, 64, 0));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service(registry).create(NO_WORLD, " baker "));
        assertEquals("A merchant named 'baker' already exists.", e.getMessage());
        assertEquals(1, registry.all().size());
    }

    @Test
    void blankNameIsRejectedBeforeSpawning() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service(new MerchantRegistry()).create(NO_WORLD, "  "));
        assertEquals("A merchant needs a name.", e.getMessage());
    }

    @Test
    void locationWithoutAWorldIsRejected() {
        MerchantRegistry registry = new MerchantRegistry();

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service(registry).create(NO_WORLD, "Baker"));
        assertEquals("The location has no world.", e.getMessage());
        assertTrue(registry.all().isEmpty());
        assertTrue(!dir.resolve("merchants.yml").toFile().exists(), "nothing should be saved");
    }

    private MerchantStorage storage() {
        return new MerchantStorage(dir.resolve("merchants.yml").toFile(), Logger.getAnonymousLogger());
    }

    /** An Entity that only answers remove, counting the calls. */
    private static Entity villager(AtomicInteger removals) {
        return (Entity) Proxy.newProxyInstance(Entity.class.getClassLoader(), new Class<?>[]{Entity.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("remove")) {
                        removals.incrementAndGet();
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @Test
    void deleteRemovesTheVillagerAndTheRecord() throws IOException {
        MerchantRegistry registry = new MerchantRegistry();
        registry.add(new Merchant(UUID.randomUUID(), "Baker", "world", 0, 64, 0));
        registry.add(new Merchant(UUID.randomUUID(), "Smith", "world", 5, 64, 5));
        AtomicInteger removals = new AtomicInteger();
        MerchantStorage storage = storage();

        Merchant deleted = new MerchantService(registry, storage, merchant -> Optional.of(villager(removals)))
                .delete(" baker ");

        assertEquals("Baker", deleted.name());
        assertEquals(1, removals.get());
        assertFalse(registry.isNameTaken("Baker"));
        assertEquals(List.of("Smith"), storage.load().all().stream().map(Merchant::name).toList());
    }

    @Test
    void deleteOfAMissingVillagerStillRemovesTheRecord() throws IOException {
        MerchantRegistry registry = new MerchantRegistry();
        registry.add(new Merchant(UUID.randomUUID(), "Baker", "world", 0, 64, 0));
        MerchantStorage storage = storage();

        new MerchantService(registry, storage, merchant -> Optional.empty()).delete("Baker");

        assertTrue(registry.all().isEmpty());
        assertTrue(storage.load().all().isEmpty());
    }

    @Test
    void deleteInAnUnloadedAreaChangesNothing() {
        MerchantRegistry registry = new MerchantRegistry();
        registry.add(new Merchant(UUID.randomUUID(), "Baker", "world", 0, 64, 0));
        MerchantService service = new MerchantService(registry, storage(), merchant -> {
            throw new IllegalStateException("unloaded");
        });

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.delete("Baker"));
        assertEquals("unloaded", e.getMessage());
        assertTrue(registry.isNameTaken("Baker"));
        assertFalse(dir.resolve("merchants.yml").toFile().exists(), "nothing should be saved");
    }

    @Test
    void deleteOfAnUnknownNameIsRejected() {
        MerchantService service = new MerchantService(new MerchantRegistry(), storage(), merchant -> {
            throw new AssertionError("should not look for a villager");
        });

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.delete(" Baker "));
        assertEquals("There is no merchant named 'Baker'.", e.getMessage());
    }
}
