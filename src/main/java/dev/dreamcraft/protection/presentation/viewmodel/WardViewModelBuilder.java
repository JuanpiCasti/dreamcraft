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
    /** Pre-formatted lines describing accepted upkeep materials (static config). */
    private final java.util.List<String> upkeepMaterialLines;
    /** Optional: projects balance → remaining protection time + material equivalences. */
    private final dev.dreamcraft.protection.service.UpkeepProjectionCalculator upkeepCalculator;
    /** Recurring surcharge per gated block placed below the Ward's tier (config). */
    private final int belowTierSurchargeUnits;

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
        this(tierProvider, nameResolver, cityNameResolver, upgradePreviewResolver, java.util.List.of());
    }

    public WardViewModelBuilder(WardTierProvider tierProvider,
                                Function<UUID, String> nameResolver,
                                Function<UUID, String> cityNameResolver,
                                BiFunction<Ward, UUID, WardUpgradePreview> upgradePreviewResolver,
                                java.util.List<String> upkeepMaterialLines) {
        this(tierProvider, nameResolver, cityNameResolver, upgradePreviewResolver,
                upkeepMaterialLines, null);
    }

    /**
     * @param nameResolver           resolves player UUIDs to display names
     * @param cityNameResolver       resolves city UUIDs to city names (null when unknown)
     * @param upgradePreviewResolver computes the upgrade preview; null → unavailable preview
     * @param upkeepMaterialLines    pre-formatted accepted-material lines (legacy ctor path)
     * @param upkeepCalculator       projects balance → protection time; null → no projection
     */
    public WardViewModelBuilder(WardTierProvider tierProvider,
                                Function<UUID, String> nameResolver,
                                Function<UUID, String> cityNameResolver,
                                BiFunction<Ward, UUID, WardUpgradePreview> upgradePreviewResolver,
                                java.util.List<String> upkeepMaterialLines,
                                dev.dreamcraft.protection.service.UpkeepProjectionCalculator upkeepCalculator) {
        this(tierProvider, nameResolver, cityNameResolver, upgradePreviewResolver,
                upkeepMaterialLines, upkeepCalculator, 0);
    }

    /**
     * @param nameResolver           resolves player UUIDs to display names
     * @param cityNameResolver       resolves city UUIDs to city names (null when unknown)
     * @param upgradePreviewResolver computes the upgrade preview; null → unavailable preview
     * @param upkeepMaterialLines    pre-formatted accepted-material lines (legacy ctor path)
     * @param upkeepCalculator       projects balance → protection time; null → no projection
     * @param belowTierSurchargeUnits recurring surcharge per gated block placed below
     *                                the tier (mirrors {@code WardUpkeepTickTask}); 0 → base rate only
     */
    public WardViewModelBuilder(WardTierProvider tierProvider,
                                Function<UUID, String> nameResolver,
                                Function<UUID, String> cityNameResolver,
                                BiFunction<Ward, UUID, WardUpgradePreview> upgradePreviewResolver,
                                java.util.List<String> upkeepMaterialLines,
                                dev.dreamcraft.protection.service.UpkeepProjectionCalculator upkeepCalculator,
                                int belowTierSurchargeUnits) {
        this.tierProvider = tierProvider;
        this.nameResolver = nameResolver;
        this.cityNameResolver = cityNameResolver;
        this.upgradePreviewResolver = upgradePreviewResolver;
        this.upkeepMaterialLines = java.util.List.copyOf(upkeepMaterialLines);
        this.upkeepCalculator = upkeepCalculator;
        this.belowTierSurchargeUnits = Math.max(0, belowTierSurchargeUnits);
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
        // Effective upkeep mirrors WardUpkeepTickTask.run(): base tier cost plus
        // the surcharge for every gated block placed below the required rank.
        int surcharge = belowTierSurchargeUnits * Math.max(0, ward.belowTierBlocks());
        var projection = upkeepCalculator == null ? null
                : upkeepCalculator.project(ward.upkeepBalance(),
                        unitsPerInterval(ward) + surcharge);

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
                ward.members(),
                hasCity,
                preview,
                upkeepMaterialLines,
                canUpgrade,
                true,            // canDeposit — anyone can deposit upkeep
                isOwner,         // canManage
                isOwner,         // canTransfer
                isOwner,         // canSetPermissions
                isOwner && !hasCity, // canAnnexToCity — owner with no city
                isOwner,         // canDisband
                isOwner,         // canInvite
                projection,
                ward.belowTierBlocks(),
                surcharge
        );
    }

    /** Units charged per interval for this Ward's tier (1 when tier unknown). */
    private int unitsPerInterval(Ward ward) {
        return tierProvider.findByKey(ward.tier())
                .map(dev.dreamcraft.protection.domain.model.WardTier::upkeepPerInterval)
                .orElse(1);
    }

    /**
     * True when the Ward is NOT currently protected by its upkeep balance
     * (no projection wired, GRACE or EXPIRED). Same rule the status icon in
     * the sync menu follows — exposed so admin GUIs can mirror it.
     */
    public boolean isUnprotected(Ward ward) {
        int surcharge = belowTierSurchargeUnits * Math.max(0, ward.belowTierBlocks());
        var projection = upkeepCalculator == null ? null
                : upkeepCalculator.project(ward.upkeepBalance(),
                        unitsPerInterval(ward) + surcharge);
        return projection == null
                || projection.state() == dev.dreamcraft.protection.service.UpkeepProjectionCalculator.State.GRACIA
                || projection.state() == dev.dreamcraft.protection.service.UpkeepProjectionCalculator.State.EXPIRADO;
    }

    private boolean hasNextTier(int currentScore, String currentTierKey) {
        var current = tierProvider.findByKey(currentTierKey);
        if (current.isEmpty()) return false;
        // Look for any tier whose min score is strictly above current's min
        return tierProvider.allTiers().values().stream()
                .anyMatch(t -> t.minBaseScore() > current.get().minBaseScore());
    }
}
