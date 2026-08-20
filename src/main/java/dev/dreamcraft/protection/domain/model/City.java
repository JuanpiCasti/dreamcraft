package dev.dreamcraft.protection.domain.model;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * DreamCraft domain model for a City.
 *
 * <p><b>Responsibility:</b> DreamCraft owns governor, member roster with roles,
 * treasury balance, City Score, and governance policies.
 *
 * <p><b>Not here:</b> No LuckPerms group is created per City. Permission resolution
 * is handled globally via LuckPerms API in the integration layer.
 */
public final class City {

    private final UUID id;
    private UUID governorId;
    /** member UUID → role */
    private final Map<UUID, CityRole> members;
    private long treasury;
    private int cityScore;
    private final Instant createdAt;
    private String name;
    private final EnumSet<CityPolicy> policies;

    public City(
            UUID id,
            UUID governorId,
            Map<UUID, CityRole> members,
            long treasury,
            int cityScore,
            Instant createdAt,
            String name,
            Set<CityPolicy> policies
    ) {
        this.id = id;
        this.governorId = governorId;
        this.members = new HashMap<>(members);
        this.treasury = treasury;
        this.cityScore = cityScore;
        this.createdAt = createdAt;
        this.name = name;
        this.policies = policies != null && !policies.isEmpty()
                ? EnumSet.copyOf(policies)
                : EnumSet.noneOf(CityPolicy.class);
        // Ensure governor is always in members map
        this.members.putIfAbsent(governorId, CityRole.GOVERNOR);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public UUID id() { return id; }
    public UUID governorId() { return governorId; }
    public Map<UUID, CityRole> members() { return members; }
    public long treasury() { return treasury; }
    public int cityScore() { return cityScore; }
    public Instant createdAt() { return createdAt; }
    public String name() { return name; }
    public Set<CityPolicy> policies() { return policies; }

    // ── Mutators ──────────────────────────────────────────────────────────────

    public void governorId(UUID governorId) { this.governorId = governorId; }
    public void treasury(long treasury) { this.treasury = treasury; }
    public void cityScore(int cityScore) { this.cityScore = cityScore; }
    public void name(String name) { this.name = name; }

    public void depositTreasury(long amount) { this.treasury += amount; }
    public boolean withdrawTreasury(long amount) {
        if (this.treasury < amount) return false;
        this.treasury -= amount;
        return true;
    }

    public void addMember(UUID playerId, CityRole role) {
        members.put(playerId, role);
    }

    public boolean removeMember(UUID playerId) {
        if (playerId.equals(governorId)) return false; // governor cannot be removed
        return members.remove(playerId) != null;
    }

    public CityRole roleOf(UUID playerId) {
        return members.getOrDefault(playerId, null);
    }

    public boolean isMember(UUID playerId) {
        return members.containsKey(playerId);
    }

    public boolean isGovernor(UUID playerId) {
        return governorId.equals(playerId);
    }

    public void enablePolicy(CityPolicy policy) { policies.add(policy); }
    public void disablePolicy(CityPolicy policy) { policies.remove(policy); }
    public boolean hasPolicy(CityPolicy policy) { return policies.contains(policy); }

    public void addCityScore(int delta) { this.cityScore = Math.max(0, this.cityScore + delta); }
}
