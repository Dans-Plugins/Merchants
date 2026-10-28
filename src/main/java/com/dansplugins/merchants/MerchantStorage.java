package com.dansplugins.merchants;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Reads and writes merchant records to a YAML file, keyed by entity UUID.
 */
public final class MerchantStorage {

    private static final String ROOT = "merchants";

    private final File file;
    private final Logger logger;

    public MerchantStorage(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    /**
     * Loads every well-formed record. A malformed or duplicate record is logged and skipped
     * rather than stopping the rest from loading.
     */
    public MerchantRegistry load() {
        MerchantRegistry registry = new MerchantRegistry();
        if (!file.exists()) {
            return registry;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection(ROOT);
        if (root == null) {
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
                logger.warning("Skipping merchant '" + id + "' in " + file.getName() + ": " + e.getMessage());
            }
        }
        return registry;
    }

    public void save(MerchantRegistry registry) throws IOException {
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
}
