package dev.dreamcraft.protection.integration.luckperms;

import java.util.UUID;

/**
 * Integration adapter contract for LuckPerms.
 *
 * <p>LuckPerms is the authority for global and administrative permissions.
 * DreamCraft does NOT create per-City or per-Ward LuckPerms groups.
 * This adapter is used only to:
 * <ul>
 *   <li>Check whether a player holds a specific DreamCraft permission node.</li>
 *   <li>Grant/revoke transient or persistent permission nodes for admin functions.</li>
 * </ul>
 */
public interface LuckPermsAdapter {

    /**
     * Returns true if the player holds the given permission node
     * according to LuckPerms (resolves inheritance, contexts, etc.).
     */
    boolean hasPermission(UUID playerId, String permission);

    /**
     * Grants a persistent permission node to a player.
     */
    void grantPermission(UUID playerId, String permission);

    /**
     * Revokes a persistent permission node from a player.
     */
    void revokePermission(UUID playerId, String permission);

    /**
     * Returns the primary group name for the player, or null if unavailable.
     */
    String primaryGroup(UUID playerId);

    boolean isAvailable();
}
