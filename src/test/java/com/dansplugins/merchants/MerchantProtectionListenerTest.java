package com.dansplugins.merchants;

import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.entity.EntityTransformEvent;
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
}
