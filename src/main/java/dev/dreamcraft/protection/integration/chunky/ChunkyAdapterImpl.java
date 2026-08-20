package dev.dreamcraft.protection.integration.chunky;

import dev.dreamcraft.protection.integration.registry.CapabilityRegistry;
import dev.dreamcraft.protection.integration.registry.IntegrationKey;
import org.bukkit.Bukkit;

import java.util.logging.Logger;

/**
 * Chunky 1.x implementation of {@link ChunkyAdapter}.
 *
 * <p>Chunky Bukkit 1.5.3 does not expose a public Java API service class.
 * This adapter uses Bukkit's plugin messaging / command dispatch as a
 * best-effort integration. For programmatic control in a future Chunky
 * version that does register a service API, this class can be enhanced.
 *
 * <p>Current behaviour: dispatches {@code /chunky start} console commands.
 * All methods are no-ops when Chunky is unavailable.
 */
public final class ChunkyAdapterImpl implements ChunkyAdapter {

    private final CapabilityRegistry registry;
    private final Logger logger;

    public ChunkyAdapterImpl(CapabilityRegistry registry, Logger logger) {
        this.registry = registry;
        this.logger = logger;
    }

    @Override
    public boolean isAvailable() {
        return registry.isAvailable(IntegrationKey.CHUNKY);
    }

    @Override
    public void pregenerateRadius(String worldName, int centerX, int centerZ, int radius, String taskLabel) {
        if (!isAvailable()) return;
        try {
            // Chunky command syntax: /chunky world <world> → /chunky center <x> <z> → /chunky radius <r> → /chunky start
            // We dispatch these as console commands; Chunky queues them internally.
            var console = Bukkit.getConsoleSender();
            Bukkit.dispatchCommand(console, "chunky world " + worldName);
            Bukkit.dispatchCommand(console, "chunky center " + centerX + " " + centerZ);
            Bukkit.dispatchCommand(console, "chunky radius " + radius);
            Bukkit.dispatchCommand(console, "chunky start");
            logger.info("[Chunky] Dispatched pre-generation for task '" + taskLabel +
                    "' at (" + centerX + "," + centerZ + ") r=" + radius + " in " + worldName);
        } catch (Exception e) {
            logger.warning("[Chunky] pregenerateRadius failed for '" + taskLabel + "': " + e.getMessage());
        }
    }

    @Override
    public void cancelTask(String taskLabel) {
        if (!isAvailable()) return;
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "chunky cancel");
            logger.info("[Chunky] Dispatched cancel for task '" + taskLabel + "'.");
        } catch (Exception e) {
            logger.warning("[Chunky] cancelTask failed for '" + taskLabel + "': " + e.getMessage());
        }
    }
}
