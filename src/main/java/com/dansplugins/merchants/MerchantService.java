package com.dansplugins.merchants;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;

import java.io.IOException;

/**
 * Creates merchants: spawns the villager, keeps it in place, and records it.
 */
public final class MerchantService {

    private final MerchantRegistry registry;
    private final MerchantStorage storage;

    public MerchantService(MerchantRegistry registry, MerchantStorage storage) {
        this.registry = registry;
        this.storage = storage;
    }

    /**
     * Spawns a named, stationary merchant villager at {@code location} and saves it.
     *
     * @throws IllegalArgumentException if the name is blank or already used by another merchant
     * @throws IOException if the merchant was spawned but could not be saved
     */
    public Merchant create(Location location, String name) throws IOException {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A merchant needs a name.");
        }
        if (registry.isNameTaken(name)) {
            throw new IllegalArgumentException("A merchant named '" + name.strip() + "' already exists.");
        }
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("The location has no world.");
        }

        // spawnEntity rather than spawn(Location, Class, Consumer): the Consumer overload's
        // parameter type changed after 1.19.4, so it would not resolve on every supported version.
        Villager villager = (Villager) world.spawnEntity(location, EntityType.VILLAGER);
        makeStationary(villager, name.strip());

        Merchant merchant = new Merchant(villager.getUniqueId(), name, world.getName(),
                location.getX(), location.getY(), location.getZ());
        registry.add(merchant);
        storage.save(registry);
        return merchant;
    }

    /**
     * With no AI a villager does not wander, panic, breed or take a job. These flags are saved
     * with the entity, so they hold across restarts without being reapplied.
     */
    private static void makeStationary(Villager villager, String name) {
        villager.setAI(false);
        villager.setPersistent(true);
        villager.setRemoveWhenFarAway(false);
        villager.setInvulnerable(true);
        villager.setCustomName(name);
        villager.setCustomNameVisible(true);
    }
}
