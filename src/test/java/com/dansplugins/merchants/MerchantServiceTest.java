package com.dansplugins.merchants;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers create and delete. The world and villager are stand-ins: they answer only the calls the
 * service makes, and record the villager's setter calls.
 */
class MerchantServiceTest {

    /**
     * A world named {@code name} whose spawnEntity returns a villager with the given id. Each setter called
     * on that villager is recorded in {@code villagerCalls} as method name to argument.
     */
    static World world(String name, UUID villagerId, Map<String, Object> villagerCalls) {
        Villager villager = (Villager) Proxy.newProxyInstance(Villager.class.getClassLoader(),
                new Class<?>[]{Villager.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) {
                        return villagerId;
                    }
                    if (method.getName().startsWith("set") && args != null && args.length == 1) {
                        villagerCalls.put(method.getName(), args[0]);
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    // Location.equals compares worlds.
                    if (method.getName().equals("equals")) {
                        return proxy == args[0];
                    }
                    if (method.getName().equals("hashCode")) {
                        return System.identityHashCode(proxy);
                    }
                    if (method.getName().equals("getName")) {
                        return name;
                    }
                    if (method.getName().equals("spawnEntity") && args.length == 2
                            && args[1] == EntityType.VILLAGER) {
                        villagerCalls.put("spawnedAt", args[0]);
                        return villager;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

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

    @Test
    void createSpawnsAStationaryNamedVillagerAndSavesIt() throws IOException {
        MerchantRegistry registry = new MerchantRegistry();
        UUID villagerId = UUID.randomUUID();
        Map<String, Object> calls = new LinkedHashMap<>();
        Location location = new Location(world("world_nether", villagerId, calls), 10.5, 64, -3.25);

        Merchant merchant = service(registry).create(location, "  Old Baker ");

        assertEquals(new Merchant(villagerId, "Old Baker", "world_nether", 10.5, 64, -3.25), merchant);
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("spawnedAt", location);
        expected.put("setAI", false);
        expected.put("setPersistent", true);
        expected.put("setRemoveWhenFarAway", false);
        expected.put("setInvulnerable", true);
        expected.put("setCustomName", "Old Baker");
        expected.put("setCustomNameVisible", true);
        assertEquals(expected, calls);
        assertTrue(registry.isMerchant(villagerId));
        assertEquals(List.of(merchant), storage().load().all());
    }

    @Test
    void createThatCannotBeSavedKeepsTheMerchantAndThrows() {
        MerchantRegistry registry = new MerchantRegistry();
        UUID villagerId = UUID.randomUUID();
        // Saving onto a directory fails with an IOException.
        MerchantService service = new MerchantService(registry,
                new MerchantStorage(dir.toFile(), Logger.getAnonymousLogger()));

        assertThrows(IOException.class, () -> service.create(
                new Location(world("world", villagerId, new LinkedHashMap<>()), 0, 64, 0), "Baker"));
        // The villager is already in the world, so the record is kept rather than orphaning it.
        assertTrue(registry.isMerchant(villagerId));
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
