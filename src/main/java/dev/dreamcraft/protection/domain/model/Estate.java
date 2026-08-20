package dev.dreamcraft.protection.domain.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * DreamCraft domain model for an Estate.
 *
 * <p>An Estate is a temporary or persistent group of players formed for a specific
 * adventure or shared purpose. It is <b>independent</b> of City/Ward membership:
 * any player from any City or Ward may belong to an Estate.
 *
 * <p>An Estate does not grant territorial claims, protection regions, or
 * LuckPerms groups. It is purely a social/organizational grouping for adventure
 * instances, coordinated tasks, or shared purposes.
 */
public final class Estate {

    private final UUID id;
    private UUID ownerId;
    private final Set<UUID> members;
    /** Optional: links this Estate to a DreamCraft adventure definition. Null if not adventure-linked. */
    private final String adventureId;
    /** Optional: links this Estate to a specific running instance. Null if not instanced. */
    private final String instanceId;
    private final Instant createdAt;
    private boolean persistent;
    private String name;

    public Estate(
            UUID id,
            UUID ownerId,
            Set<UUID> members,
            String adventureId,
            String instanceId,
            Instant createdAt,
            boolean persistent,
            String name
    ) {
        this.id = id;
        this.ownerId = ownerId;
        this.members = new HashSet<>(members);
        this.adventureId = adventureId;
        this.instanceId = instanceId;
        this.createdAt = createdAt;
        this.persistent = persistent;
        this.name = name;
        // owner always included
        this.members.add(ownerId);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public UUID id() { return id; }
    public UUID ownerId() { return ownerId; }
    public Set<UUID> members() { return members; }
    public String adventureId() { return adventureId; }
    public String instanceId() { return instanceId; }
    public Instant createdAt() { return createdAt; }
    public boolean persistent() { return persistent; }
    public String name() { return name; }

    // ── Mutators ──────────────────────────────────────────────────────────────

    public void ownerId(UUID ownerId) { this.ownerId = ownerId; }
    public void persistent(boolean persistent) { this.persistent = persistent; }
    public void name(String name) { this.name = name; }

    public void addMember(UUID playerId) { members.add(playerId); }
    public boolean removeMember(UUID playerId) {
        if (playerId.equals(ownerId)) return false;
        return members.remove(playerId);
    }
    public boolean isMember(UUID playerId) { return members.contains(playerId); }
    public boolean isOwner(UUID playerId) { return ownerId.equals(playerId); }

    /** True if this Estate is linked to an adventure. */
    public boolean isAdventureLinked() { return adventureId != null; }
    /** True if this Estate is linked to a specific instance. */
    public boolean isInstanced() { return instanceId != null; }
}
