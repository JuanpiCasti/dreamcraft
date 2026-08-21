package dev.dreamcraft.protection.presentation.viewmodel;

import dev.dreamcraft.protection.domain.model.Ward;
import dev.dreamcraft.protection.domain.port.WardTierProvider;

import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Builds immutable {@link WardViewModel}s from domain {@link Ward} aggregates.
 *
 * <p>Lives outside the domain but has no Bukkit dependency — only pure Java.
 * Player name resolution is injected as a function so the command/adaptor layer
 * can supply an online-name lookup without leaking Bukkit into the mapper.
 */
public final class WardViewModelBuilder {

    private final WardTierProvider tierProvider;
    private final Function<UUID, String> nameResolver;
    private final Function<UUID, String> cityNameResolver;
    /** Optional: computes the upgrade preview (needs Bukkit inventory access upstream). */
    private final BiFunction<Ward, UUID, WardUpgradePreview> upgradePreviewResolver;

    public WardViewModelBuilder(WardTierProvider tierProvider, Function<UUID, String> nameResolver) {
        this(tierProvider, nameResolver, id -> null);
    }

    public WardViewModelBuilder(WardTierProvider tierProvider,
                                Function<UUID, String> nameResolver,
                                Function<UUID, String> cityNameResolver) {
        this(tierProvider, nameResolver, cityNameResolver, null);
    }

    /**
     * @param nameResolver           resolves player UUIDs to display names
     * @param cityNameResolver       resolves city UUIDs to city names (null when unknown)
     * @param upgradePreviewResolver computes the upgrade preview; null → unavailable preview
     */
    public WardViewModelBuilder(WardTierProvider tierProvider,
                                Function<UUID, String> nameResolver,
                                Function<UUID, String> cityNameResolver,
                                BiFunction<Ward, UUID, WardUpgradePreview> upgradePreviewResolver) {
        this.tierProvider = tierProvider;
        this.nameResolver = nameResolver;
        this.cityNameResolver = cityNameResolver;
        this.upgradePreviewResolver = upgradePreviewResolver;
    }

    /**
     * Builds a view model for a ward, computing validation flags from the viewer's
     * role (owner vs. non-owner) and the tier provider.
     *
     * @param ward     the domain aggregate
     * @param viewerId the player viewing the menu (determines canManage flags)
     */
    public WardViewModel build(Ward ward, UUID viewerId) {
        boolean isOwner = ward.ownerId().equals(viewerId);
        boolean hasCity = ward.hasCityMembership();
        String cityName = null;
        if (hasCity && ward.cityId() != null) {
            cityName = cityNameResolver.apply(ward.cityId());
        }

        // Determine if an upgrade is available: check the next tier above current
        boolean canUpgrade = isOwner && hasNextTier(ward.baseScore(), ward.tier());
        WardUpgradePreview preview = upgradePreviewResolver != null
                ? upgradePreviewResolver.apply(ward, viewerId)
                : WardUpgradePreview.unavailable();

        return new WardViewModel(
                ward.id(),
                ward.name(),
                ward.worldName(),
                ward.ownerId(),
                nameResolver.apply(ward.ownerId()),
                ward.ownerType(),
                ward.cityId(),
                cityName,
                ward.baseScore(),
                ward.tier(),
                ward.radius(),
                ward.upkeepBalance(),
                ward.nextUpkeepAt(),
                ward.centerX(),
                ward.centerY(),
                ward.centerZ(),
                ward.permissions(),
                hasCity,
                preview,
                canUpgrade,
                true,            // canDeposit — anyone can deposit upkeep
                isOwner,         // canManage
                isOwner,         // canTransfer
                isOwner,         // canSetPermissions
                isOwner && !hasCity, // canAnnexToCity — owner with no city
                isOwner          // canDisband
        );
    }

    private boolean hasNextTier(int currentScore, String currentTierKey) {
        var current = tierProvider.findByKey(currentTierKey);
        if (current.isEmpty()) return false;
        // Look for any tier whose min score is strictly above current's min
        return tierProvider.allTiers().values().stream()
                .anyMatch(t -> t.minBaseScore() > current.get().minBaseScore());
    }
}
