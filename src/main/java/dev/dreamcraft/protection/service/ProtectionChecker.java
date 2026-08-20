package dev.dreamcraft.protection.service;

import dev.dreamcraft.protection.config.ProtectionConfig;
import dev.dreamcraft.protection.model.ClaimRole;
import dev.dreamcraft.protection.model.ProtectionAction;
import dev.dreamcraft.protection.model.ProtectionCheckResult;
import dev.dreamcraft.protection.model.ProtectionClaim;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

public final class ProtectionChecker {
    private final ProtectionConfig config;
    private final ClaimIndex claimIndex;

    public ProtectionChecker(ProtectionConfig config, ClaimIndex claimIndex) {
        this.config = config;
        this.claimIndex = claimIndex;
    }

    public Optional<ProtectionClaim> findClaim(String world, int x, int z) {
        return claimIndex.findClaim(world, x, z);
    }

    public ProtectionCheckResult check(Player player, String world, int x, int z, ProtectionAction action) {
        if (!config.enabled()) {
            return ProtectionCheckResult.PROTECTION_DISABLED;
        }
        Optional<ProtectionClaim> optionalClaim = claimIndex.findClaim(world, x, z);
        if (optionalClaim.isEmpty()) {
            return ProtectionCheckResult.NOT_PROTECTED;
        }
        ProtectionClaim claim = optionalClaim.get();
        if (!claim.status().isProtectionActive()) {
            return ProtectionCheckResult.NOT_PROTECTED;
        }
        return isAllowed(player.getUniqueId(), claim, action)
                ? ProtectionCheckResult.PROTECTED_ALLOWED
                : ProtectionCheckResult.PROTECTED_DENIED;
    }

    /**
     * Server-side check with no player actor (explosions, pistons, hoppers, redstone).
     * Returns {@link ProtectionCheckResult#PROTECTED_DENIED} for any actively protected position.
     */
    public ProtectionCheckResult checkNoPlayer(String world, int x, int z, ProtectionAction action) {
        if (!config.enabled()) {
            return ProtectionCheckResult.PROTECTION_DISABLED;
        }
        Optional<ProtectionClaim> optionalClaim = claimIndex.findClaim(world, x, z);
        if (optionalClaim.isEmpty()) {
            return ProtectionCheckResult.NOT_PROTECTED;
        }
        ProtectionClaim claim = optionalClaim.get();
        if (!claim.status().isProtectionActive()) {
            return ProtectionCheckResult.NOT_PROTECTED;
        }
        return ProtectionCheckResult.PROTECTED_DENIED;
    }

    public boolean isAllowed(UUID uuid, ProtectionClaim claim, ProtectionAction action) {
        if (claim.ownerUuid().equals(uuid)) {
            return true;
        }
        boolean member = claim.members().contains(uuid);
        if (!member) {
            return false;
        }
        return action != ProtectionAction.MANAGE_MEMBERS
                && action != ProtectionAction.TRANSFER
                && action != ProtectionAction.REMOVE_WARDROBE;
    }

    public ClaimRole role(UUID uuid, ProtectionClaim claim) {
        return claim.ownerUuid().equals(uuid) ? ClaimRole.OWNER : ClaimRole.MEMBER;
    }
}
