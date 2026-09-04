package dev.dreamcraft.protection.service;

import dev.dreamcraft.protection.domain.model.Ward;
import dev.dreamcraft.protection.domain.service.CityService;
import dev.dreamcraft.protection.integration.worldguard.WorldGuardAdapter;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The single reconciliation point between domain membership and WorldGuard
 * region members for Wards.
 *
 * <p>WorldGuard owns enforcement (flags, geometry); DreamCraft owns the truth
 * about <i>who</i> has access (owner + annexed city residents). This class
 * projects that truth onto the WG member list by <b>replacing</b> it wholesale,
 * so there is exactly one definition of "who should have access" and no
 * incremental add/remove bookkeeping that can drift from domain state.
 *
 * <p>Call {@link #project} AFTER any mutation that changes who should have
 * access: ward annex/leave, city invite/kick/governorship transfer, city or
 * ward deletion.
 */
public final class WardAccessSync {

    private WardAccessSync() {}

    /**
     * Region members := the annexed City's residents (empty when unannexed).
     * The owners domain is never touched here — the owner keeps access always.
     */
    public static void project(Ward ward, CityService cityService, WorldGuardAdapter worldGuard) {
        if (worldGuard == null || !worldGuard.isAvailable()) return;
        Set<UUID> expected = new HashSet<>(ward.members());
        if (ward.hasCityMembership()) {
            cityService.findById(ward.cityId())
                    .ifPresent(city -> expected.addAll(city.members().keySet()));
        }
        worldGuard.replaceMembers(ward, expected);
    }

    /** Convenience for flows that touch many wards of one city (invite, kick, delete). */
    public static void projectAll(Collection<Ward> wards, CityService cityService, WorldGuardAdapter worldGuard) {
        for (Ward ward : wards) {
            project(ward, cityService, worldGuard);
        }
    }
}
