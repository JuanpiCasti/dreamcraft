package dev.dreamcraft.protection.domain.model;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * DreamCraft domain model for a Ward.
 *
 * <p><b>Responsibility split:</b>
 * <ul>
 *   <li>DreamCraft owns: ownerId, ownerType, cityId, baseScore, tier, calculated radius,
 *       upkeep accounting, domain permissions.</li>
 *   <li>WorldGuard owns: region geometry, block/entity protection flags, membership
 *       in WG regions, region priorities, parent/child region relationships.</li>
 * </ul>
 *
 * <p>A Ward can exist without belonging to a City ({@code cityId} is null).
 *
 * <p>The {@code worldGuardRegionId} is the identifier of the corresponding WorldGuard
 * region. DreamCraft stores it as an opaque string so the integration layer can
 * resolve it without the domain depending on WorldGuard APIs.
 */
public final class Ward {

    private final UUID id;
    private String name;
    private final String worldName;
    private UUID ownerId;
    private OwnerType ownerType;
    private UUID cityId; // nullable — Ward without City
    private int baseScore;
    private String tier;
    private int radius; // computed from tier + baseScore
    private int upkeepBalance;
    private final Instant createdAt;
    private Instant lastUpkeepAt;
    private Instant nextUpkeepAt;
    private final int centerX;
    private final int centerY;
    private final int centerZ;
    /** Opaque reference to the WorldGuard region managed by the integration layer. */
    private String worldGuardRegionId;
    private final Set<WardPermission> permissions;
    /**
     * Gated blocks placed while the Ward tier was below the required rank
     * (tier-gated-blocks). Each unit adds a recurring upkeep surcharge per
     * interval instead of denying placement. Persisted as
     * {@code below-tier-blocks}; absent entries load as 0 (legacy wards).
     */
    private int belowTierBlocks;

    public Ward(
            UUID id,
            String name,
            String worldName,
            UUID ownerId,
            OwnerType ownerType,
            UUID cityId,
            int baseScore,
            String tier,
            int radius,
            int upkeepBalance,
            Instant createdAt,
            Instant lastUpkeepAt,
            Instant nextUpkeepAt,
            int centerX,
            int centerY,
            int centerZ,
            String worldGuardRegionId,
            Set<WardPermission> permissions
    ) {
        this.id = id;
        this.name = name != null && !name.isBlank() ? name : "Ward";
        this.worldName = worldName;
        this.ownerId = ownerId;
        this.ownerType = ownerType;
        this.cityId = cityId;
        this.baseScore = baseScore;
        this.tier = tier;
        this.radius = radius;
        this.upkeepBalance = upkeepBalance;
        this.createdAt = createdAt;
        this.lastUpkeepAt = lastUpkeepAt;
        this.nextUpkeepAt = nextUpkeepAt;
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.worldGuardRegionId = worldGuardRegionId;
        this.permissions = permissions != null ? EnumSet.copyOf(permissions.isEmpty()
                ? EnumSet.noneOf(WardPermission.class) : permissions)
                : EnumSet.noneOf(WardPermission.class);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public UUID id() { return id; }
    public String name() { return name; }
    public void name(String name) { this.name = name != null && !name.isBlank() ? name : this.name; }
    public String worldName() { return worldName; }
    public UUID ownerId() { return ownerId; }
    public OwnerType ownerType() { return ownerType; }
    public UUID cityId() { return cityId; }
    public int baseScore() { return baseScore; }
    public String tier() { return tier; }
    public int radius() { return radius; }
    public int upkeepBalance() { return upkeepBalance; }
    public Instant createdAt() { return createdAt; }
    public Instant lastUpkeepAt() { return lastUpkeepAt; }
    public Instant nextUpkeepAt() { return nextUpkeepAt; }
    public int centerX() { return centerX; }
    public int centerY() { return centerY; }
    public int centerZ() { return centerZ; }
    public String worldGuardRegionId() { return worldGuardRegionId; }
    public Set<WardPermission> permissions() { return permissions; }
    public int belowTierBlocks() { return belowTierBlocks; }

    // ── Mutators ──────────────────────────────────────────────────────────────

    public void ownerId(UUID ownerId) { this.ownerId = ownerId; }
    public void ownerType(OwnerType ownerType) { this.ownerType = ownerType; }
    public void cityId(UUID cityId) { this.cityId = cityId; }
    public void baseScore(int baseScore) { this.baseScore = baseScore; }
    public void tier(String tier) { this.tier = tier; }
    public void radius(int radius) { this.radius = radius; }
    public void upkeepBalance(int upkeepBalance) { this.upkeepBalance = upkeepBalance; }
    public void lastUpkeepAt(Instant lastUpkeepAt) { this.lastUpkeepAt = lastUpkeepAt; }
    public void nextUpkeepAt(Instant nextUpkeepAt) { this.nextUpkeepAt = nextUpkeepAt; }
    public void worldGuardRegionId(String worldGuardRegionId) { this.worldGuardRegionId = worldGuardRegionId; }
    public void belowTierBlocks(int belowTierBlocks) {
        this.belowTierBlocks = Math.max(0, belowTierBlocks);
    }

    public void grantPermission(WardPermission permission) { permissions.add(permission); }
    public void revokePermission(WardPermission permission) { permissions.remove(permission); }
    public boolean hasPermission(WardPermission permission) { return permissions.contains(permission); }

    /** True if this Ward belongs to a City. */
    public boolean hasCityMembership() { return cityId != null; }
}
