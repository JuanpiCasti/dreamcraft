package dev.dreamcraft.protection.domain.model;

/**
 * Domain-level permissions for a Ward, managed by DreamCraft.
 * Each flag determines what non-members may do inside the Ward's domain.
 * WorldGuard flags govern block/entity interaction at the region level;
 * these permissions govern DreamCraft-specific mechanics.
 */
public enum WardPermission {
    /** Allow non-members to build inside the Ward boundary. */
    PUBLIC_BUILD,
    /** Allow non-members to break blocks inside the Ward boundary. */
    PUBLIC_BREAK,
    /**
     * Allow non-members to open containers (chests, barrels, furnaces, ...)
     * inside the Ward — mirrors the WorldGuard {@code chest-access} flag.
     * When granted, the flag flips to {@code allow}; when revoked, back to
     * {@code deny} (owners and members are always exempt).
     */
    PUBLIC_CONTAINERS,
    /** Allow non-members to deposit upkeep resources. */
    PUBLIC_UPKEEP_DEPOSIT,
    /** Allow non-members to view Ward status information. */
    PUBLIC_STATUS_VIEW
}
