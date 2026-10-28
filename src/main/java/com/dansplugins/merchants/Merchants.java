package com.dansplugins.merchants;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * Plugin entry point. The features are scoped in MVP.md.
 */
public final class Merchants extends JavaPlugin {

    private MerchantService merchantService;

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        MerchantStorage storage = new MerchantStorage(new File(getDataFolder(), "merchants.yml"), getLogger());
        MerchantRegistry registry = storage.load();
        merchantService = new MerchantService(registry, storage);
        getServer().getPluginManager().registerEvents(new MerchantProtectionListener(registry), this);
        getLogger().info("Loaded " + registry.all().size() + " merchant(s).");
    }

    @Override
    public void onDisable() {
        merchantService = null;
    }

    /**
     * Creates merchants. Not yet reachable from in game: the {@code /merchant} command is a separate change.
     */
    public MerchantService getMerchantService() {
        return merchantService;
    }
}
