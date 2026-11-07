package com.dansplugins.merchants;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.Cancellable;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MerchantProtectionListenerTest {

    /** An Entity that only answers getUniqueId, which is all the listener reads. */
    private static Entity entity(UUID id) {
        return (Entity) Proxy.newProxyInstance(Entity.class.getClassLoader(), new Class<?>[]{Entity.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) {
                        return id;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static EntityTransformEvent transform(Entity entity) {
        Entity witch = entity(UUID.randomUUID());
        return new EntityTransformEvent(entity, List.of(witch), EntityTransformEvent.TransformReason.LIGHTNING);
    }

    private static VehicleEnterEvent vehicleEnter(Entity entered) {
        Vehicle vehicle = (Vehicle) Proxy.newProxyInstance(Vehicle.class.getClassLoader(), new Class<?>[]{Vehicle.class},
                (proxy, method, args) -> {
                    throw new UnsupportedOperationException(method.getName());
                });
        return new VehicleEnterEvent(vehicle, entered);
    }

    private static EntityPortalEvent portal(Entity entity) {
        return new EntityPortalEvent(entity, new Location(null, 0, 64, 0), new Location(null, 0, 64, 0));
    }

    /**
     * Stands in for an EntityDamageEvent, which cannot be constructed without a running server.
     * onDamage passes its event to cancelIfMerchant unchanged.
     */
    private static final class DamageStandIn implements Cancellable {
        private boolean cancelled;

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void setCancelled(boolean cancel) {
            cancelled = cancel;
        }
    }

    @Test
    void merchantTakesNoDamage() {
        MerchantRegistry registry = new MerchantRegistry();
        UUID id = UUID.randomUUID();
        registry.add(new Merchant(id, "Baker", "world", 0, 64, 0));

        DamageStandIn event = new DamageStandIn();
        new MerchantProtectionListener(registry).cancelIfMerchant(entity(id), event);

        assertTrue(event.isCancelled());
    }

    @Test
    void otherEntitiesTakeDamageAsNormal() {
        MerchantRegistry registry = new MerchantRegistry();
        registry.add(new Merchant(UUID.randomUUID(), "Baker", "world", 0, 64, 0));

        DamageStandIn event = new DamageStandIn();
        new MerchantProtectionListener(registry).cancelIfMerchant(entity(UUID.randomUUID()), event);

        assertFalse(event.isCancelled());
    }

    @Test
    void merchantIsNotTransformed() {
        MerchantRegistry registry = new MerchantRegistry();
        UUID id = UUID.randomUUID();
        registry.add(new Merchant(id, "Baker", "world", 0, 64, 0));

        EntityTransformEvent event = transform(entity(id));
        new MerchantProtectionListener(registry).onTransform(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void otherEntitiesAreTransformedAsNormal() {
        MerchantRegistry registry = new MerchantRegistry();
        registry.add(new Merchant(UUID.randomUUID(), "Baker", "world", 0, 64, 0));

        EntityTransformEvent event = transform(entity(UUID.randomUUID()));
        new MerchantProtectionListener(registry).onTransform(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void merchantDoesNotEnterAVehicle() {
        MerchantRegistry registry = new MerchantRegistry();
        UUID id = UUID.randomUUID();
        registry.add(new Merchant(id, "Baker", "world", 0, 64, 0));

        VehicleEnterEvent event = vehicleEnter(entity(id));
        new MerchantProtectionListener(registry).onVehicleEnter(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void otherEntitiesEnterVehiclesAsNormal() {
        MerchantRegistry registry = new MerchantRegistry();
        registry.add(new Merchant(UUID.randomUUID(), "Baker", "world", 0, 64, 0));

        VehicleEnterEvent event = vehicleEnter(entity(UUID.randomUUID()));
        new MerchantProtectionListener(registry).onVehicleEnter(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void merchantDoesNotGoThroughAPortal() {
        MerchantRegistry registry = new MerchantRegistry();
        UUID id = UUID.randomUUID();
        registry.add(new Merchant(id, "Baker", "world", 0, 64, 0));

        EntityPortalEvent event = portal(entity(id));
        new MerchantProtectionListener(registry).onPortal(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void otherEntitiesGoThroughPortalsAsNormal() {
        MerchantRegistry registry = new MerchantRegistry();
        registry.add(new Merchant(UUID.randomUUID(), "Baker", "world", 0, 64, 0));

        EntityPortalEvent event = portal(entity(UUID.randomUUID()));
        new MerchantProtectionListener(registry).onPortal(event);

        assertFalse(event.isCancelled());
    }
}
