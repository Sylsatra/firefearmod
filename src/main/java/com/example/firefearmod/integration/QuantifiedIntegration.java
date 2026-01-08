package com.example.firefearmod.integration;

import net.minecraftforge.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Optional integration with Quantified-API for performance profiling and caching.
 */
public class QuantifiedIntegration {
    private static final Logger LOGGER = LogManager.getLogger();
    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;

        if (!ModList.get().isLoaded("quantified")) {
            LOGGER.debug("Quantified-API not present, skipping integration.");
            return;
        }

        try {
            Class<?> apiClass = Class.forName("org.admany.quantified.api.QuantifiedAPI");
            java.lang.reflect.Method registerMethod = apiClass.getMethod("register", String.class, String.class, String.class);
            registerMethod.invoke(null, "firefearmod", "Fire Fear Mod", "1.0.0");
            LOGGER.info("Registered with Quantified-API successfully.");
        } catch (ClassNotFoundException e) {
            LOGGER.debug("Quantified-API classes not found at runtime.");
        } catch (Exception e) {
            LOGGER.warn("Failed to register with Quantified-API: {}", e.getMessage());
        }
    }
}
