package com.dansplugins.merchants;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the checks create makes before spawning. The spawn itself needs a running server: a
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
}
