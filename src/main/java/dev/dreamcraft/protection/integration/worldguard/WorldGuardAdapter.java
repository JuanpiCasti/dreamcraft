package dev.dreamcraft.protection.integration.worldguard;

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
     * Replaces the Ward region's entire WG member list with the given set.
     * This is the single write path for ward region membership: the domain
     * layer computes the expected members (owner + annexed city residents)
     * and WG's list becomes a pure projection of that state — no incremental
     * add/remove bookkeeping that can drift from domain truth.
     *
     * @see dev.dreamcraft.protection.service.WardAccessSync
     */
    void replaceMembers(Ward ward, java.util.Collection<java.util.UUID> members);

    /**
     * Sets the region owner in WorldGuard to match the Ward's owner.
     */
    void syncOwner(Ward ward);

    /**
     * Opens (or closes) the Ward's containers to the general public by flipping
     * the WorldGuard {@code chest-access} flag. Owners and WG members are always
     * exempt from region flags; this only changes what outsiders may do.
     *
     * @param allowed true → outsiders may open containers (public farm area);
     *                false → containers closed for non-members
     */
    void setPublicContainerAccess(Ward ward, boolean allowed);

    /**
     * Returns true if this adapter is operational (WorldGuard present and compatible).
     */
    boolean isAvailable();

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

    /**
     * Configures the vertical band applied to estate area regions (stealth).
     * The region spans anchorY - below to anchorY + above instead of full world
     * height, so surface players never fall inside a stronghold/chamber zone.
     *
     * @param below blocks under the area anchor Y included in the region
     * @param above blocks over the area anchor Y included in the region
     */
    void setEstateAreaBand(int below, int above);

    /**
     * Creates (or replaces) the WorldGuard region covering an Estate's gated
     * area. Vertically it is limited to the configured band around
     * {@code estate.areaY()} — see {@link #setEstateAreaBand(int, int)}.
     * The estate owner becomes the region owner and all estate members become
     * region members, so only the adventuring group can build inside the
     * portal / structure zone.
     *
     * @return the WorldGuard region ID, or null if creation failed or WG unavailable
     */
    String createEstateAreaRegion(Estate estate, String worldName, int centerX, int centerZ, int radius);

    /**
     * Removes the WorldGuard region associated with the Estate's gated area.
     */
    void removeEstateAreaRegion(Estate estate);

    /**
     * Re-syncs the WorldGuard member list of the Estate's area region with the
     * current estate membership (owner + members).
     */
    void syncEstateMembers(Estate estate);
}
