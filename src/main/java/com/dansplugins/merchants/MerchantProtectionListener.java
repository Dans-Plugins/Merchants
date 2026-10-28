package com.dansplugins.merchants;

import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTransformEvent;

/**
 * Keeps merchants alive and unchanged: they take no damage from any source, and are never
 * turned into a witch by lightning or into a zombie villager by a zombie.
 */
public final class MerchantProtectionListener implements Listener {

    private final MerchantRegistry registry;

    public MerchantProtectionListener(MerchantRegistry registry) {
        this.registry = registry;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        cancelIfMerchant(event.getEntity(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTransform(EntityTransformEvent event) {
        cancelIfMerchant(event.getEntity(), event);
    }

    // Package-private so tests can cover damage without constructing an EntityDamageEvent,
    // whose constructor needs a running server.
    void cancelIfMerchant(Entity entity, Cancellable event) {
        if (registry.isMerchant(entity.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
