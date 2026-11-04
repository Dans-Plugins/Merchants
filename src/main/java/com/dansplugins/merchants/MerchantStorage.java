package com.dansplugins.merchants;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.logging.Logger;

/**
 * Reads and writes merchant records to a YAML file, keyed by entity UUID.
 */
public final class MerchantStorage {

    private static final String ROOT = "merchants";

    private final File file;
    private final Logger logger;
    private final LongSupplier clock;
    private boolean copyBeforeNextSave;

    public MerchantStorage(File file, Logger logger) {
        this(file, logger, System::currentTimeMillis);
    }

    MerchantStorage(File file, Logger logger, LongSupplier clock) {
        this.file = file;
        this.logger = logger;
        this.clock = clock;
    }

    /**
     * Loads every well-formed record. A malformed or duplicate record is logged and skipped
     * rather than stopping the rest from loading, and a file that cannot be parsed or has no
     * merchants section loads no merchants. In each case the file is copied before the next
     * {@link #save} replaces it, because saving writes only what loaded.
     */
    public MerchantRegistry load() {
        MerchantRegistry registry = new MerchantRegistry();
        if (!file.exists()) {
            return registry;
        }
        // Not YamlConfiguration.loadConfiguration: it reports a parse failure only by returning an
        // empty configuration, which looks the same as a file with no merchants in it.
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            copyBeforeNextSave = true;
            logger.severe("Could not read " + file.getName() + ", so no merchants were loaded: " + e.getMessage());
            return registry;
        }
        ConfigurationSection root = yaml.getConfigurationSection(ROOT);
        if (root == null) {
            // save always writes the section, so any other content is a hand edit worth keeping.
            if (!yaml.getKeys(false).isEmpty()) {
                copyBeforeNextSave = true;
                logger.severe("Found no '" + ROOT + "' section in " + file.getName() + ", so no merchants were loaded.");
            }
            return registry;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            try {
                if (section == null) {
                    throw new IllegalArgumentException("not a section");
                }
                registry.add(new Merchant(
                        UUID.fromString(id),
                        section.getString("name"),
                        section.getString("world"),
                        section.getDouble("x"),
                        section.getDouble("y"),
                        section.getDouble("z")));
            } catch (IllegalArgumentException | NullPointerException e) {
                copyBeforeNextSave = true;
                logger.warning("Skipping merchant '" + id + "' in " + file.getName() + ": " + e.getMessage());
            }
        }
        return registry;
    }

    /**
     * Replaces the file with the merchants in {@code registry}.
     *
     * @throws IOException if the file could not be written, or if it held records that did not load
     *         and could not be copied, in which case it is left as it was
     */
    public void save(MerchantRegistry registry) throws IOException {
        if (copyBeforeNextSave) {
            keepCopy();
            copyBeforeNextSave = false;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection(ROOT);
        for (Merchant merchant : registry.all()) {
            ConfigurationSection section = root.createSection(merchant.entityId().toString());
            section.set("name", merchant.name());
            section.set("world", merchant.worldName());
            section.set("x", merchant.x());
            section.set("y", merchant.y());
            section.set("z", merchant.z());
        }
        yaml.save(file);
    }

    private void keepCopy() throws IOException {
        if (!file.exists()) {
            return;
        }
        File copy = new File(file.getParentFile(), file.getName() + ".broken-" + clock.getAsLong());
        Files.copy(file.toPath(), copy.toPath());
        logger.warning("Kept a copy of " + file.getName() + " as " + copy.getName()
                + ", because some of it could not be loaded and it is about to be replaced.");
    }
}
