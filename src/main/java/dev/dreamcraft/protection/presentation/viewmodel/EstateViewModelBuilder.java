package dev.dreamcraft.protection.presentation.viewmodel;

import dev.dreamcraft.protection.domain.model.Estate;

import java.util.UUID;
import java.util.function.Function;

/**
 * Builds immutable {@link EstateViewModel}s from domain {@link Estate} aggregates.
 *
 * <p>Lives outside the domain but has no Bukkit dependency — only pure Java.
 */
public final class EstateViewModelBuilder {

    private final Function<UUID, String> nameResolver;

    public EstateViewModelBuilder(Function<UUID, String> nameResolver) {
        this.nameResolver = nameResolver;
    }

    /**
     * Builds a view model for an estate, computing validation flags from the viewer's role.
     *
     * @param estate   the domain aggregate
     * @param viewerId the player viewing the menu
     */
    public EstateViewModel build(Estate estate, UUID viewerId) {
        boolean isOwner = estate.isOwner(viewerId);
        boolean isMember = estate.isMember(viewerId);

        return new EstateViewModel(
                estate.id(),
                estate.name(),
                estate.ownerId(),
                nameResolver.apply(estate.ownerId()),
                estate.members(),
                estate.members().size(),
                estate.adventureId(),
                estate.instanceId(),
                estate.createdAt(),
                estate.persistent(),
                estate.isInstanced(),
                estate.isAdventureLinked(),
                estate.type().displayName(),
                estate.hasArea(),
                isOwner,
                isOwner,           // canInvite
                !isMember && !isOwner, // canJoin — non-members only (owner manages, doesn't join)
                isMember,          // canLeave
                isOwner,           // canStart
                isOwner,           // canDisband
                isOwner            // canTransfer
        );
    }
}
