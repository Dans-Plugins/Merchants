package com.dansplugins.merchants;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
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
        return world(name, villagerId, villagerCalls, true);
    }

    /**
     * As {@link #world(String, UUID, Map)}, with {@code spawned} false for a villager that never entered
     * the world, as when another plugin cancels its spawn.
     */
    static World world(String name, UUID villagerId, Map<String, Object> villagerCalls, boolean spawned) {
        Villager villager = (Villager) Proxy.newProxyInstance(Villager.class.getClassLoader(),
                new Class<?>[]{Villager.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) {
                        return villagerId;
                    }
                    if (method.getName().equals("isValid")) {
                        return spawned;
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

    @Test
    void createWhoseSpawnWasBlockedRecordsNothing() {
        MerchantRegistry registry = new MerchantRegistry();
        UUID villagerId = UUID.randomUUID();
        Map<String, Object> calls = new LinkedHashMap<>();
        Location location = new Location(world("world", villagerId, calls, false), 0, 64, 0);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service(registry).create(location, "Baker"));

        assertEquals("The merchant could not be placed here: its villager did not spawn."
                + " Another plugin may be blocking mob spawns in this area.", e.getMessage());
        assertEquals(Map.of("spawnedAt", location), calls);
        assertFalse(registry.isMerchant(villagerId));
        assertTrue(registry.all().isEmpty());
        assertFalse(dir.resolve("merchants.yml").toFile().exists(), "nothing should be saved");
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

    // The tests below go through the public constructor, so delete uses the default locator, which
    // asks Bukkit for the world and the entity. Bukkit's server is a JVM-wide singleton that can be
    // set only once, so a single stand-in is installed and each test sets what it answers.

    /** The world {@code Bukkit.getWorld} returns for its name; null for a world that does not exist. */
    private static World serverWorld;
    /** The entity {@code Bukkit.getEntity} returns; null for one that is no longer in the world. */
    private static Entity serverEntity;
    /** Every lookup the locator made, in order, as method name and arguments. */
    private static final List<String> serverCalls = new ArrayList<>();

    private static void installServer() {
        if (Bukkit.getServer() != null) {
            return;
        }
        Bukkit.setServer((Server) Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[]{Server.class},
                (proxy, method, args) -> switch (method.getName()) {
                    // Bukkit.setServer logs the server's name and version.
                    case "getLogger" -> Logger.getAnonymousLogger();
                    case "getName", "getVersion", "getBukkitVersion" -> "test";
                    case "getWorld" -> {
                        serverCalls.add("getWorld " + args[0]);
                        yield serverWorld != null && serverWorld.getName().equals(args[0]) ? serverWorld : null;
                    }
                    case "getEntity" -> {
                        serverCalls.add("getEntity " + args[0]);
                        yield serverEntity;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                }));
    }

    @BeforeEach
    void resetServer() {
        installServer();
        serverWorld = null;
        serverEntity = null;
        serverCalls.clear();
    }

    /**
     * A world named {@code name} that records each chunk check in {@link #serverCalls}, reports every
     * chunk as loaded or not, and reports every loaded chunk's entities as loaded or not.
     */
    private static World chunkWorld(String name, boolean chunkLoaded, boolean entitiesLoaded) {
        Chunk chunk = (Chunk) Proxy.newProxyInstance(Chunk.class.getClassLoader(), new Class<?>[]{Chunk.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("isEntitiesLoaded")) {
                        return entitiesLoaded;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getName")) {
                        return name;
                    }
                    if (method.getName().equals("isChunkLoaded") && args.length == 2) {
                        serverCalls.add("isChunkLoaded " + args[0] + ", " + args[1]);
                        return chunkLoaded;
                    }
                    if (method.getName().equals("getChunkAt") && args.length == 2 && args[0] instanceof Integer) {
                        serverCalls.add("getChunkAt " + args[0] + ", " + args[1]);
                        return chunk;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    /** A registry holding one merchant, Baker, in {@code world} at block 10, 64, -4 (chunk 0, -1). */
    private static MerchantRegistry bakerAt(UUID entityId) {
        MerchantRegistry registry = new MerchantRegistry();
        registry.add(new Merchant(entityId, "Baker", "world", 10.7, 64.0, -3.2));
        return registry;
    }

    @Test
    void defaultLocatorRemovesTheVillagerInALoadedChunk() throws IOException {
        UUID entityId = UUID.randomUUID();
        MerchantRegistry registry = bakerAt(entityId);
        AtomicInteger removals = new AtomicInteger();
        serverWorld = chunkWorld("world", true, true);
        serverEntity = villager(removals);

        service(registry).delete("Baker");

        assertEquals(List.of("getWorld world", "isChunkLoaded 0, -1", "getChunkAt 0, -1", "getEntity " + entityId),
                serverCalls);
        assertEquals(1, removals.get());
        assertTrue(registry.all().isEmpty());
        assertTrue(storage().load().all().isEmpty());
    }

    @Test
    void defaultLocatorDeletesTheRecordWhenTheVillagerIsGone() throws IOException {
        UUID entityId = UUID.randomUUID();
        MerchantRegistry registry = bakerAt(entityId);
        serverWorld = chunkWorld("world", true, true);

        service(registry).delete("Baker");

        assertEquals("getEntity " + entityId, serverCalls.get(serverCalls.size() - 1));
        assertTrue(registry.all().isEmpty());
    }

    @Test
    void defaultLocatorDeletesTheRecordWhenTheWorldIsGone() throws IOException {
        MerchantRegistry registry = bakerAt(UUID.randomUUID());
        serverWorld = chunkWorld("world_nether", true, true);

        service(registry).delete("Baker");

        // No chunk check and no entity lookup: a missing world means a missing villager.
        assertEquals(List.of("getWorld world"), serverCalls);
        assertTrue(registry.all().isEmpty());
        assertTrue(storage().load().all().isEmpty());
    }

    @Test
    void defaultLocatorRefusesWhenTheChunkIsNotLoaded() {
        MerchantRegistry registry = bakerAt(UUID.randomUUID());
        serverWorld = chunkWorld("world", false, true);

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service(registry).delete("Baker"));

        assertEquals("Merchant 'Baker' is in an unloaded area of world at 10, 64, -4. Go there and try again.",
                e.getMessage());
        assertEquals(List.of("getWorld world", "isChunkLoaded 0, -1"), serverCalls);
        assertTrue(registry.isNameTaken("Baker"));
        assertFalse(dir.resolve("merchants.yml").toFile().exists(), "nothing should be saved");
    }

    @Test
    void defaultLocatorRefusesWhenTheChunksEntitiesAreNotLoaded() {
        MerchantRegistry registry = bakerAt(UUID.randomUUID());
        serverWorld = chunkWorld("world", true, false);

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service(registry).delete("Baker"));

        assertEquals("Merchant 'Baker' is in an unloaded area of world at 10, 64, -4. Go there and try again.",
                e.getMessage());
        assertEquals(List.of("getWorld world", "isChunkLoaded 0, -1", "getChunkAt 0, -1"), serverCalls);
        assertTrue(registry.isNameTaken("Baker"));
        assertFalse(dir.resolve("merchants.yml").toFile().exists(), "nothing should be saved");
    }
}
