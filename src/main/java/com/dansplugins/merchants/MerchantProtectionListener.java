package com.dansplugins.merchants;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Keeps merchants alive, unchanged and in place: they take no damage from any source, are never
 * turned into a witch by lightning or into a zombie villager by a zombie, and never get into a
 * boat or minecart or go through a portal, either of which would carry them away from their
 * stored location. A name tag cannot rename them.
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        cancelIfMerchant(event.getEntered(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(EntityPortalEvent event) {
        cancelIfMerchant(event.getEntity(), event);
    }

    /**
     * Stops a name tag from renaming a merchant, so its nameplate keeps matching the name that
     * {@code /merchant list} and {@code /merchant delete} use. Every other interaction is left
     * alone, because trading is expected to start from the same click.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItem(event.getHand());
        if (item != null && item.getType() == Material.NAME_TAG) {
            cancelIfMerchant(event.getRightClicked(), event);
        }
    }

    // Package-private so tests can cover damage without constructing an EntityDamageEvent,
    // whose constructor needs a running server.
    void cancelIfMerchant(Entity entity, Cancellable event) {
        if (registry.isMerchant(entity.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
