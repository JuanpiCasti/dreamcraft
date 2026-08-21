package dev.dreamcraft.protection.presentation.viewmodel;

import dev.dreamcraft.protection.domain.model.CityPolicy;
import dev.dreamcraft.protection.domain.model.CityRole;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable view model for City menus.
 *
 * <p>Contains only display data and pre-computed validation flags so the menu
 * builder can render active/inactive/blocked states without reaching into the domain.
 * No Bukkit types, no domain aggregate references.
 */
public record CityViewModel(
        UUID id,
        String name,
        UUID governorId,
        String governorName,
        Map<UUID, CityRole> members,
        int memberCount,
        long treasury,
        int cityScore,
        Instant createdAt,
        Set<CityPolicy> policies,
        int wardCount,
        /** Computed city level + progression (null when the level service isn't wired). */
        dev.dreamcraft.protection.service.CityLevelService.CityLevelStatus levelStatus,
        // Pre-computed validation flags for menu rendering
        boolean isGovernor,
        boolean isCouncil,
        boolean canManageResidents,
        boolean canSetRoles,
        boolean canManageTreasury,
        boolean canSetPolicy,
        boolean canAnnexWard,
        boolean canDelete,
        boolean canTransferGovernor
) {
}
