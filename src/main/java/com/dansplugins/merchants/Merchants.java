package com.dansplugins.merchants;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Objects;

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
        PluginCommand merchantCommand = Objects.requireNonNull(getCommand("merchant"), "merchant command missing from plugin.yml");
        MerchantCommand executor = new MerchantCommand(merchantService, registry, getLogger());
        merchantCommand.setExecutor(executor);
        merchantCommand.setTabCompleter(executor);
        getLogger().info("Loaded " + registry.all().size() + " merchant(s).");
    }

    @Override
    public void onDisable() {
        merchantService = null;
    }

    /**
     * Creates and deletes merchants; {@code /merchant} is the in-game route to it.
     */
    public MerchantService getMerchantService() {
        return merchantService;
    }
}
