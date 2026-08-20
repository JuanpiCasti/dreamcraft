package dev.dreamcraft.protection.integration.worldguard;

import dev.dreamcraft.protection.domain.model.Ward;

/**
 * Integration adapter contract for WorldGuard.
 *
 * <p>WorldGuard is the authority for: region geometry, block/entity protection flags,
 * region membership (WG member lists), region priorities, and parent/child regions.
 *
 * <p>DreamCraft calls these methods when domain events (Ward creation, deletion,
 * radius change) require WorldGuard to be updated. The adapter is a no-op when
 * WorldGuard is unavailable.
 */
public interface WorldGuardAdapter {

    /**
     * Creates a WorldGuard cuboid region for the given Ward and returns the region ID.
     * The region covers (cx-radius, minY, cz-radius) to (cx+radius, maxY, cz+radius).
     *
     * @return the WorldGuard region ID, or null if creation failed or WG unavailable
     */
    String createRegion(Ward ward, String worldName, int minY, int maxY);

    /**
     * Resizes an existing WorldGuard region to match the Ward's new radius.
     */
    void resizeRegion(Ward ward, int minY, int maxY);

    /**
     * Removes the WorldGuard region associated with the Ward.
     */
    void removeRegion(Ward ward);

    /**
     * Adds a player as a WorldGuard member of the Ward's region (not a DreamCraft member).
     * Called by domain logic when WG-level membership is needed (e.g., build access).
     */
    void addMember(Ward ward, java.util.UUID playerId);

    /**
     * Removes a player from the WorldGuard member list of the Ward's region.
     */
    void removeMember(Ward ward, java.util.UUID playerId);

    /**
     * Sets the region owner in WorldGuard to match the Ward's owner.
     */
    void syncOwner(Ward ward);

    /**
     * Returns true if this adapter is operational (WorldGuard present and compatible).
     */
    boolean isAvailable();
}
