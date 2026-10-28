package com.dansplugins.merchants;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MerchantStorageTest {

    @TempDir
    Path dir;

    private final List<String> warnings = new ArrayList<>();

    private MerchantStorage storage(File file) {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                warnings.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return new MerchantStorage(file, logger);
    }

    @Test
    void missingFileLoadsNoMerchants() {
        MerchantStorage storage = storage(dir.resolve("merchants.yml").toFile());
        assertTrue(storage.load().all().isEmpty());
    }

    @Test
    void savedMerchantsLoadBack() throws IOException {
        File file = dir.resolve("merchants.yml").toFile();
        MerchantRegistry registry = new MerchantRegistry();
        Merchant baker = new Merchant(UUID.randomUUID(), "Baker", "world", 10.5, 64, -3.25);
        Merchant smith = new Merchant(UUID.randomUUID(), "Smith", "world_nether", -1, 70, 2);
        registry.add(baker);
        registry.add(smith);

        storage(file).save(registry);
        MerchantRegistry loaded = storage(file).load();

        assertEquals(List.of(baker, smith), loaded.all());
        assertTrue(loaded.isMerchant(baker.entityId()));
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void malformedRecordsAreSkippedAndLogged() throws IOException {
        UUID good = UUID.randomUUID();
        UUID duplicateName = UUID.randomUUID();
        UUID noWorld = UUID.randomUUID();
        File file = dir.resolve("merchants.yml").toFile();
        Files.writeString(file.toPath(), String.join("\n",
                "merchants:",
                "  " + good + ":",
                "    name: Baker",
                "    world: world",
                "    x: 1.0",
                "    y: 2.0",
                "    z: 3.0",
                "  not-a-uuid:",
                "    name: Cooper",
                "    world: world",
                "  " + noWorld + ":",
                "    name: Fletcher",
                "  " + duplicateName + ":",
                "    name: baker",
                "    world: world",
                ""), StandardCharsets.UTF_8);

        MerchantRegistry loaded = storage(file).load();

        assertEquals(List.of(new Merchant(good, "Baker", "world", 1, 2, 3)), loaded.all());
        assertEquals(3, warnings.size(), warnings.toString());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("not-a-uuid")), warnings.toString());
        assertTrue(warnings.stream().anyMatch(w -> w.contains(noWorld.toString())), warnings.toString());
        assertTrue(warnings.stream().anyMatch(w -> w.contains(duplicateName.toString())), warnings.toString());
    }
}
