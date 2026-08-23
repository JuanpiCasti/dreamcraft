package dev.dreamcraft.protection.presentation.viewmodel;

import dev.dreamcraft.protection.domain.model.OwnerType;
import dev.dreamcraft.protection.domain.model.WardPermission;
import dev.dreamcraft.protection.service.UpkeepProjectionCalculator;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable view model for Ward menus.
 *
 * <p>Contains only display data and pre-computed validation flags so the menu
 * builder can render active/inactive/blocked states without reaching into the domain.
 * No Bukkit types, no domain aggregate references.
 */
public record WardViewModel(
        UUID id,
        String name,
        String worldName,
        UUID ownerId,
        String ownerName,
        OwnerType ownerType,
        UUID cityId,
        String cityName,
        int baseScore,
        String tier,
        int radius,
        int upkeepBalance,
        Instant nextUpkeepAt,
        int centerX,
        int centerY,
        int centerZ,
        Set<WardPermission> permissions,
        boolean hasCityMembership,
        WardUpgradePreview upgradePreview,
        /** Pre-formatted lines describing accepted upkeep materials, e.g. "Diamante ×64 u". */
        List<String> upkeepMaterials,
        // Pre-computed validation flags for menu rendering
        boolean canUpgrade,
        boolean canDeposit,
        boolean canManage,
        boolean canTransfer,
        boolean canSetPermissions,
        boolean canAnnexToCity,
        boolean canDisband,
        /** Pre-computed upkeep time projection (state + remaining time + material
         *  equivalences); null when the calculator was not wired upstream. */
        UpkeepProjectionCalculator.Projection upkeepProjection,
        /** Gated blocks placed while the Ward was below its tier (drives the surcharge). */
        int belowTierBlocks,
        /** Total extra units charged per interval due to below-tier blocks
         *  (surchargeUnits × belowTierBlocks, mirrored from WardUpkeepTickTask). */
        int belowTierSurchargePerInterval
) {
    /** Legacy-arity constructor — projection unavailable (null). */
    public WardViewModel(
            UUID id,
            String name,
            String worldName,
            UUID ownerId,
            String ownerName,
            OwnerType ownerType,
            UUID cityId,
            String cityName,
            int baseScore,
            String tier,
            int radius,
            int upkeepBalance,
            Instant nextUpkeepAt,
            int centerX,
            int centerY,
            int centerZ,
            Set<WardPermission> permissions,
            boolean hasCityMembership,
            WardUpgradePreview upgradePreview,
            List<String> upkeepMaterials,
            boolean canUpgrade,
            boolean canDeposit,
            boolean canManage,
            boolean canTransfer,
            boolean canSetPermissions,
            boolean canAnnexToCity,
            boolean canDisband
    ) {
        this(id, name, worldName, ownerId, ownerName, ownerType, cityId, cityName,
                baseScore, tier, radius, upkeepBalance, nextUpkeepAt,
                centerX, centerY, centerZ, permissions, hasCityMembership, upgradePreview,
                upkeepMaterials, canUpgrade, canDeposit, canManage, canTransfer,
                canSetPermissions, canAnnexToCity, canDisband, null, 0, 0);
    }
}
