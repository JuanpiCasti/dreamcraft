package dev.dreamcraft.protection.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ProtectionClaim {
    private final UUID id;
    private final String world;
    private UUID ownerUuid;
    private final int centerX;
    private final int centerY;
    private final int centerZ;
    private final int radius;
    private final int buildRadius;
    private ProtectionState status;
    private final String tier;
    private final Instant createdAt;
    private Instant lastUpkeepAt;
    private Instant nextUpkeepAt;
    /** Last time any claim member (owner or member) performed an action in this claim. */
    private Instant lastActivityAt;
    private final Set<UUID> members;
    private final Map<String, Object> settings;
    private final ClaimStats stats;
    private final UpkeepStorage upkeepStorage;
    private final int wardrobeX;
    private final int wardrobeY;
    private final int wardrobeZ;
    /**
     * Tracks which warning state was last notified so we don't spam members on every tick.
     * null means no notification has been sent yet.
     */
    private ProtectionState lastNotifiedState;

    public ProtectionClaim(
            UUID id,
            String world,
            UUID ownerUuid,
            int centerX,
            int centerY,
            int centerZ,
            int radius,
            int buildRadius,
            ProtectionState status,
            String tier,
            Instant createdAt,
            Instant lastUpkeepAt,
            Instant nextUpkeepAt,
            Instant lastActivityAt,
            Set<UUID> members,
            Map<String, Object> settings,
            ClaimStats stats,
            UpkeepStorage upkeepStorage,
            int wardrobeX,
            int wardrobeY,
            int wardrobeZ
    ) {
        this.id = id;
        this.world = world;
        this.ownerUuid = ownerUuid;
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.radius = radius;
        this.buildRadius = buildRadius;
        this.status = status;
        this.tier = tier;
        this.createdAt = createdAt;
        this.lastUpkeepAt = lastUpkeepAt;
        this.nextUpkeepAt = nextUpkeepAt;
        this.lastActivityAt = lastActivityAt;
        this.members = new HashSet<>(members);
        this.settings = settings;
        this.stats = stats;
        this.upkeepStorage = upkeepStorage;
        this.wardrobeX = wardrobeX;
        this.wardrobeY = wardrobeY;
        this.wardrobeZ = wardrobeZ;
        this.lastNotifiedState = null;
    }

    public UUID id() { return id; }
    public String world() { return world; }
    public UUID ownerUuid() { return ownerUuid; }
    public void ownerUuid(UUID ownerUuid) { this.ownerUuid = ownerUuid; }
    public int centerX() { return centerX; }
    public int centerY() { return centerY; }
    public int centerZ() { return centerZ; }
    public int radius() { return radius; }
    public int buildRadius() { return buildRadius; }
    public ProtectionState status() { return status; }
    public void status(ProtectionState status) { this.status = status; }
    public String tier() { return tier; }
    public Instant createdAt() { return createdAt; }
    public Instant lastUpkeepAt() { return lastUpkeepAt; }
    public void lastUpkeepAt(Instant lastUpkeepAt) { this.lastUpkeepAt = lastUpkeepAt; }
    public Instant nextUpkeepAt() { return nextUpkeepAt; }
    public void nextUpkeepAt(Instant nextUpkeepAt) { this.nextUpkeepAt = nextUpkeepAt; }
    public Instant lastActivityAt() { return lastActivityAt; }
    public void lastActivityAt(Instant lastActivityAt) { this.lastActivityAt = lastActivityAt; }
    public ProtectionState lastNotifiedState() { return lastNotifiedState; }
    public void lastNotifiedState(ProtectionState lastNotifiedState) { this.lastNotifiedState = lastNotifiedState; }
    public Set<UUID> members() { return members; }
    public Map<String, Object> settings() { return settings; }
    public ClaimStats stats() { return stats; }
    public UpkeepStorage upkeepStorage() { return upkeepStorage; }
    public int wardrobeX() { return wardrobeX; }
    public int wardrobeY() { return wardrobeY; }
    public int wardrobeZ() { return wardrobeZ; }

    public ClaimBounds protectionBounds() {
        return new ClaimBounds(centerX - radius, centerX + radius, centerZ - radius, centerZ + radius);
    }

    public ClaimBounds buildBounds() {
        return new ClaimBounds(centerX - buildRadius, centerX + buildRadius, centerZ - buildRadius, centerZ + buildRadius);
    }

    public boolean isMember(UUID uuid) {
        return ownerUuid.equals(uuid) || members.contains(uuid);
    }

    public boolean isWardrobe(int x, int y, int z) {
        return wardrobeX == x && wardrobeY == y && wardrobeZ == z;
    }

    /**
     * Returns true if this claim has been abandoned: no activity from any member for longer
     * than the given abandonedAfter duration. Uses lastActivityAt for the check.
     */
    public boolean isAbandoned(java.time.Duration abandonedAfter) {
        return lastActivityAt.plus(abandonedAfter).isBefore(Instant.now());
    }
}
