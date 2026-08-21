package dev.dreamcraft.protection.presentation.viewmodel;

import dev.dreamcraft.protection.domain.model.City;
import dev.dreamcraft.protection.domain.model.CityRole;

import java.util.UUID;
import java.util.function.Function;

/**
 * Builds immutable {@link CityViewModel}s from domain {@link City} aggregates.
 *
 * <p>Lives outside the domain but has no Bukkit dependency — only pure Java.
 */
public final class CityViewModelBuilder {

    private final Function<UUID, String> nameResolver;
    private final Function<City, Integer> wardCountResolver;
    /** Optional: computes the city's level status (wards/members/wealth based). */
    private final java.util.function.Function<City, dev.dreamcraft.protection.service.CityLevelService.CityLevelStatus> levelResolver;

    public CityViewModelBuilder(Function<UUID, String> nameResolver,
                                Function<City, Integer> wardCountResolver) {
        this(nameResolver, wardCountResolver, null);
    }

    public CityViewModelBuilder(Function<UUID, String> nameResolver,
                                Function<City, Integer> wardCountResolver,
                                java.util.function.Function<City, dev.dreamcraft.protection.service.CityLevelService.CityLevelStatus> levelResolver) {
        this.nameResolver = nameResolver;
        this.wardCountResolver = wardCountResolver;
        this.levelResolver = levelResolver;
    }

    /**
     * Builds a view model for a city, computing validation flags from the viewer's role.
     *
     * @param city     the domain aggregate
     * @param viewerId the player viewing the menu
     */
    public CityViewModel build(City city, UUID viewerId) {
        boolean isGovernor = city.isGovernor(viewerId);
        CityRole role = city.roleOf(viewerId);
        boolean isCouncil = role == CityRole.COUNCIL || isGovernor;

        return new CityViewModel(
                city.id(),
                city.name(),
                city.governorId(),
                nameResolver.apply(city.governorId()),
                city.members(),
                city.members().size(),
                city.treasury(),
                city.cityScore(),
                city.createdAt(),
                city.policies(),
                wardCountResolver.apply(city),
                levelResolver != null ? levelResolver.apply(city) : null,
                isGovernor,
                isCouncil,
                isCouncil,          // canManageResidents — governor + council
                isGovernor,         // canSetRoles
                isCouncil,          // canManageTreasury
                isGovernor,         // canSetPolicy
                isGovernor,         // canAnnexWard
                isGovernor,         // canDelete
                isGovernor          // canTransferGovernor
        );
    }
}
