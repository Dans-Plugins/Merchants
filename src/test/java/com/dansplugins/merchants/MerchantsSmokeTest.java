package com.dansplugins.merchants;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MerchantsSmokeTest {

    @Test
    void mainClassIsAJavaPlugin() {
        assertTrue(JavaPlugin.class.isAssignableFrom(Merchants.class));
    }

    @Test
    void pluginYmlPointsAtTheMainClass() throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("plugin.yml")) {
            assertNotNull(in, "plugin.yml must be on the classpath");
            String yml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(yml.contains("main: " + Merchants.class.getName()), yml);
            assertTrue(yml.contains("name: Merchants"), yml);
            assertTrue(!yml.contains("${version}"), "version placeholder must be expanded");
        }
    }
}
