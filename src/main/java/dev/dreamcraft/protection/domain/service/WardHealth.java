package dev.dreamcraft.protection.domain.service;

/**
 * Pure classification of a Ward's structural health: is the ward core still
 * placed, and does its WorldGuard region still exist?
 *
 * <p>A Ward is <b>orphan</b> when either component is confirmed MISSING.
 * Unknown or degraded states are not absence: {@code CHUNK_UNLOADED} means the
 * core chunk simply was not loaded during the check (the block may still be
 * there) and {@code WG_INACTIVE} means WorldGuard is unavailable (the region
 * state cannot be known). Neither counts as orphan — only a verified MISSING
 * on either side does.
 */
public final class WardHealth {

    /** Presence of the physical ward core block. */
    public enum CoreState {
        /** The core block is present at the ward's anchor position. */
        PRESENT,
        /** Verified absent: the chunk is loaded and no core block exists. */
        MISSING,
        /** Unknown: the core chunk is not loaded, presence cannot be checked. */
        CHUNK_UNLOADED
    }

    /** Presence of the WorldGuard protection region. */
    public enum RegionState {
        /** The WG region exists in the region registry. */
        PRESENT,
        /** Verified absent: WG is active but the region is gone from the registry. */
        MISSING,
        /** Unknown/inactive: WorldGuard is unavailable, existence cannot be checked. */
        WG_INACTIVE
    }

    /**
     * Immutable result of a health check.
     *
     * @param coreState   core presence observed
     * @param regionState region presence observed
     * @param orphan      true when the ward has lost its core or its region
     */
    public record HealthReport(CoreState coreState, RegionState regionState, boolean orphan) {}

    private WardHealth() {}

    /**
     * Classifies a ward as orphan or healthy.
     *
     * <p>Orphan = core MISSING or region MISSING. {@code CHUNK_UNLOADED} and
     * {@code WG_INACTIVE} are unknown/degraded facts, never treated as absence.
     *
     * @param core   observed core state
     * @param region observed region state
     * @return the health report with the orphan verdict
     */
    public static HealthReport classify(CoreState core, RegionState region) {
        boolean orphan = core == CoreState.MISSING || region == RegionState.MISSING;
        return new HealthReport(core, region, orphan);
    }
}
