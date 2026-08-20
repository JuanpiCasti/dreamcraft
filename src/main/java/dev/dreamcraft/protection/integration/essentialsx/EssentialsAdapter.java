package dev.dreamcraft.protection.integration.essentialsx;

import java.util.UUID;

/**
 * Integration adapter contract for EssentialsX.
 *
 * <p>EssentialsX handles homes, spawns, chat format, and nickname management.
 * DreamCraft reuses these capabilities rather than reimplementing them.
 *
 * <p>This adapter provides read access to player homes and teleport coordination
 * without duplicating EssentialsX's own logic.
 */
public interface EssentialsAdapter {

    /**
     * Returns the primary home location of the player as a LocationSnapshot,
     * or null if the player has no home or EssentialsX is unavailable.
     */
    LocationSnapshot getHome(UUID playerId, String homeName);

    /**
     * Returns whether the player has a home with the given name.
     */
    boolean hasHome(UUID playerId, String homeName);

    /**
     * Returns the number of homes the player has set.
     */
    int homeCount(UUID playerId);

    /**
     * Returns the global spawn location or null if unavailable.
     */
    LocationSnapshot getSpawn();

    boolean isAvailable();

    /**
     * Minimal immutable location snapshot — keeps the adapter independent of Bukkit Location.
     */
    record LocationSnapshot(String worldName, double x, double y, double z, float yaw, float pitch) {}
}
