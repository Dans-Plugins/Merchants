package com.dansplugins.merchants;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
                "    x: 4.0",
                "    y: 5.0",
                "    z: 6.0",
                ""), StandardCharsets.UTF_8);

        MerchantRegistry loaded = storage(file).load();

        assertEquals(List.of(new Merchant(good, "Baker", "world", 1, 2, 3)), loaded.all());
        assertEquals(3, warnings.size(), warnings.toString());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("not-a-uuid")), warnings.toString());
        assertTrue(warnings.stream().anyMatch(w -> w.contains(noWorld.toString())), warnings.toString());
        assertTrue(warnings.stream().anyMatch(w -> w.contains(duplicateName.toString())
                && w.contains("already exists")), warnings.toString());
    }

    private static String recordWithZ(UUID id, String zLine) {
        return String.join("\n",
                "merchants:",
                "  " + id + ":",
                "    name: Baker",
                "    world: world",
                "    x: 1.0",
                "    y: 2.0",
                zLine,
                "");
    }

    @ParameterizedTest
    @ValueSource(strings = {"    other: 3.0", "    z: three", "    z: '3.0'", "    z: .nan", "    z: .inf"})
    void aRecordWithABadCoordinateIsSkippedNotLoadedAtZero(String zLine) throws IOException {
        UUID id = UUID.randomUUID();
        File file = dir.resolve("merchants.yml").toFile();
        Files.writeString(file.toPath(), recordWithZ(id, zLine), StandardCharsets.UTF_8);

        MerchantRegistry loaded = storage(file).load();

        assertTrue(loaded.all().isEmpty(), loaded.all().toString());
        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains(id.toString()) && warnings.get(0).contains("'z'"), warnings.toString());
    }

    @Test
    void savingOverABadCoordinateKeepsACopyOfTheOldFile() throws IOException {
        File file = dir.resolve("merchants.yml").toFile();
        String original = recordWithZ(UUID.randomUUID(), "    z: three");
        Files.writeString(file.toPath(), original, StandardCharsets.UTF_8);
        MerchantStorage storage = storage(file);

        storage.save(storage.load());

        assertEquals(1, backups().size());
        assertEquals(original, Files.readString(backups().get(0), StandardCharsets.UTF_8));
    }

    @Test
    void wholeNumberCoordinatesLoad() throws IOException {
        UUID id = UUID.randomUUID();
        File file = dir.resolve("merchants.yml").toFile();
        Files.writeString(file.toPath(), recordWithZ(id, "    z: -3"), StandardCharsets.UTF_8);

        assertEquals(List.of(new Merchant(id, "Baker", "world", 1, 2, -3)), storage(file).load().all());
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void savingOverAMalformedRecordKeepsACopyOfTheOldFile() throws IOException {
        File file = dir.resolve("merchants.yml").toFile();
        String original = String.join("\n",
                "merchants:",
                "  not-a-uuid:",
                "    name: Cooper",
                "    world: world",
                "");
        Files.writeString(file.toPath(), original, StandardCharsets.UTF_8);
        MerchantStorage storage = storage(file);

        MerchantRegistry loaded = storage.load();
        assertTrue(backups().isEmpty(), "loading alone should not write anything");
        storage.save(loaded);

        assertEquals(1, backups().size());
        Path copy = backups().get(0);
        assertEquals(original, Files.readString(copy, StandardCharsets.UTF_8));
        assertTrue(warnings.stream().anyMatch(w -> w.contains(copy.getFileName().toString())), warnings.toString());
    }

    @Test
    void anUnparseableFileLoadsNoMerchantsAndIsCopiedBeforeItIsOverwritten() throws IOException {
        File file = dir.resolve("merchants.yml").toFile();
        String original = "merchants:\n\tbroken: [\n";
        Files.writeString(file.toPath(), original, StandardCharsets.UTF_8);
        MerchantStorage storage = storage(file);

        MerchantRegistry loaded = storage.load();
        assertTrue(loaded.all().isEmpty());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("merchants.yml")), warnings.toString());

        Merchant baker = new Merchant(UUID.randomUUID(), "Baker", "world", 1, 2, 3);
        loaded.add(baker);
        storage.save(loaded);

        assertEquals(1, backups().size());
        assertEquals(original, Files.readString(backups().get(0), StandardCharsets.UTF_8));
        assertEquals(List.of(baker), storage(file).load().all());
    }

    @Test
    void aFileWithoutTheMerchantsSectionIsCopiedBeforeItIsOverwritten() throws IOException {
        File file = dir.resolve("merchants.yml").toFile();
        String original = "merchantz:\n  some-id:\n    name: Cooper\n";
        Files.writeString(file.toPath(), original, StandardCharsets.UTF_8);
        MerchantStorage storage = storage(file);

        MerchantRegistry loaded = storage.load();
        assertTrue(warnings.stream().anyMatch(w -> w.contains("merchants.yml")), warnings.toString());
        storage.save(loaded);

        assertEquals(1, backups().size());
        assertEquals(original, Files.readString(backups().get(0), StandardCharsets.UTF_8));
    }

    @Test
    void aMerchantsKeyThatIsNotASectionIsCopiedBeforeItIsOverwritten() throws IOException {
        File file = dir.resolve("merchants.yml").toFile();
        String original = "merchants: [Cooper, Fletcher]\n";
        Files.writeString(file.toPath(), original, StandardCharsets.UTF_8);
        MerchantStorage storage = storage(file);

        storage.save(storage.load());

        assertEquals(1, backups().size());
        assertEquals(original, Files.readString(backups().get(0), StandardCharsets.UTF_8));
    }

    @Test
    void savingOverAFileWithNoMerchantsKeepsNoCopy() throws IOException {
        File file = dir.resolve("merchants.yml").toFile();
        storage(file).save(new MerchantRegistry());
        MerchantStorage storage = storage(file);

        storage.save(storage.load());

        assertTrue(backups().isEmpty());
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void onlyTheFirstSaveAfterAFailedLoadKeepsACopy() throws IOException {
        File file = dir.resolve("merchants.yml").toFile();
        Files.writeString(file.toPath(), "merchants:\n  not-a-uuid:\n    name: Cooper\n", StandardCharsets.UTF_8);
        MerchantStorage storage = storage(file);
        MerchantRegistry loaded = storage.load();

        storage.save(loaded);
        storage.save(loaded);

        assertEquals(1, backups().size());
    }

    @Test
    void savingOverACleanlyLoadedFileKeepsNoCopy() throws IOException {
        File file = dir.resolve("merchants.yml").toFile();
        MerchantRegistry registry = new MerchantRegistry();
        registry.add(new Merchant(UUID.randomUUID(), "Baker", "world", 1, 2, 3));
        storage(file).save(registry);
        MerchantStorage storage = storage(file);

        storage.save(storage.load());

        assertTrue(backups().isEmpty());
    }

    @Test
    void aFailedCopyStopsTheSaveAndLeavesTheFileAlone() throws IOException {
        // The copy goes next to the file, so a directory already holding the name makes it fail.
        File file = dir.resolve("merchants.yml").toFile();
        String original = "merchants:\n  not-a-uuid:\n    name: Cooper\n";
        Files.writeString(file.toPath(), original, StandardCharsets.UTF_8);
        MerchantStorage storage = new MerchantStorage(file, Logger.getAnonymousLogger(), () -> 1L);
        MerchantRegistry loaded = storage.load();
        Files.createDirectory(dir.resolve("merchants.yml.broken-1"));

        assertThrows(IOException.class, () -> storage.save(loaded));

        assertEquals(original, Files.readString(file.toPath(), StandardCharsets.UTF_8));
    }

    private List<Path> backups() throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(path -> path.getFileName().toString().startsWith("merchants.yml.broken-")).toList();
        }
    }
}
