package com.dansplugins.merchants;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;

import java.io.IOException;
import java.util.Optional;

/**
 * Creates and deletes merchants: spawns or removes the villager, keeps it in place, and records it.
 */
public final class MerchantService {

    /**
     * Finds a merchant's villager in the world.
     */
    interface VillagerLocator {

        /**
         * @return the villager, or empty if it no longer exists
         * @throws IllegalStateException if the villager's chunk is not loaded, so whether it still
         *         exists cannot be told
         */
        Optional<Entity> find(Merchant merchant);
    }

    private final MerchantRegistry registry;
    private final MerchantStorage storage;
    private final VillagerLocator locator;

    public MerchantService(MerchantRegistry registry, MerchantStorage storage) {
        this(registry, storage, MerchantService::findLoadedVillager);
    }

    MerchantService(MerchantRegistry registry, MerchantStorage storage, VillagerLocator locator) {
        this.registry = registry;
        this.storage = storage;
        this.locator = locator;
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
     * Removes the merchant's villager and its record, and saves.
     *
     * @return the deleted merchant
     * @throws IllegalArgumentException if no merchant has this name
     * @throws IllegalStateException if the villager's chunk is not loaded; nothing is changed, because
     *         deleting only the record would leave an invulnerable villager standing in the world
     * @throws IOException if the merchant was removed but the change could not be saved
     */
    public Merchant delete(String name) throws IOException {
        Merchant merchant = registry.getByName(name == null ? "" : name)
                .orElseThrow(() -> new IllegalArgumentException(
                        "There is no merchant named '" + (name == null ? "" : name.strip()) + "'."));
        locator.find(merchant).ifPresent(Entity::remove);
        registry.remove(merchant.name());
        storage.save(registry);
        return merchant;
    }

    /**
     * A villager whose world is gone is treated as gone too, so its record can still be deleted.
     */
    private static Optional<Entity> findLoadedVillager(Merchant merchant) {
        World world = Bukkit.getWorld(merchant.worldName());
        if (world == null) {
            return Optional.empty();
        }
        int chunkX = Location.locToBlock(merchant.x()) >> 4;
        int chunkZ = Location.locToBlock(merchant.z()) >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ) || !world.getChunkAt(chunkX, chunkZ).isEntitiesLoaded()) {
            throw new IllegalStateException("Merchant '" + merchant.name() + "' is in an unloaded area of "
                    + world.getName() + " at " + Location.locToBlock(merchant.x()) + ", "
                    + Location.locToBlock(merchant.y()) + ", " + Location.locToBlock(merchant.z())
                    + ". Go there and try again.");
        }
        return Optional.ofNullable(Bukkit.getEntity(merchant.entityId()));
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
