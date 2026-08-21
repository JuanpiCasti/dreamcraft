package dev.dreamcraft.protection.integration.worldguard;

import dev.dreamcraft.protection.domain.model.City;
import dev.dreamcraft.protection.domain.model.Estate;
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

    /**
     * Syncs city-level memberships and policies to the Ward's WorldGuard region.
     * Called when a Ward is annexed to a City — all city members are added as WG
     * region members, inheriting the city's access policies.
     *
     * @param ward the Ward whose region should receive the city memberships
     * @param city the City whose members/policies to inherit
     */
    void syncCityMembership(Ward ward, City city);

    /**
     * Applies temporal access flags to the Ward's region for an active Estate instance.
     * These flags grant temporary access to estate members without a permanent
     * territorial concession. Call {@link #clearEstateInstanceFlags} when the instance ends.
     *
     * @param ward   the Ward whose region should receive the temporal flags
     * @param estate the active Estate instance
     */
    void applyEstateInstanceFlags(Ward ward, Estate estate);

    /**
     * Clears the temporal access flags applied by {@link #applyEstateInstanceFlags}.
     * Called when an Estate instance ends to restore the region's original flag state.
     *
     * @param ward the Ward whose temporal flags should be cleared
     */
    void clearEstateInstanceFlags(Ward ward);
}
