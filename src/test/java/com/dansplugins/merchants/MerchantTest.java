package com.dansplugins.merchants;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MerchantTest {

    @Test
    void nameIsStripped() {
        Merchant merchant = new Merchant(UUID.randomUUID(), "  Baker ", "world", 1, 2, 3);
        assertEquals("Baker", merchant.name());
    }

    @Test
    void blankNameIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Merchant(UUID.randomUUID(), "   ", "world", 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Merchant(UUID.randomUUID(), null, "world", 0, 0, 0));
    }

    @Test
    void entityAndWorldAreRequired() {
        assertThrows(NullPointerException.class, () -> new Merchant(null, "Baker", "world", 0, 0, 0));
        assertThrows(NullPointerException.class, () -> new Merchant(UUID.randomUUID(), "Baker", null, 0, 0, 0));
    }
}
