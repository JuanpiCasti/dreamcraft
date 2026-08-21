package dev.dreamcraft.protection.presentation.viewmodel;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable view model for Estate menus (lobby / instance views).
 *
 * <p>Contains only display data and pre-computed validation flags so the menu
 * builder can render active/inactive/blocked states without reaching into the domain.
 * No Bukkit types, no domain aggregate references.
 */
public record EstateViewModel(
        UUID id,
        String name,
        UUID ownerId,
        String ownerName,
        Set<UUID> members,
        int memberCount,
        String adventureId,
        String instanceId,
        Instant createdAt,
        boolean persistent,
        boolean isInstanced,
        boolean isAdventureLinked,
        // Pre-computed validation flags for menu rendering
        boolean isOwner,
        boolean canInvite,
        boolean canJoin,
        boolean canLeave,
        boolean canStart,
        boolean canDisband,
        boolean canTransfer
) {
}
