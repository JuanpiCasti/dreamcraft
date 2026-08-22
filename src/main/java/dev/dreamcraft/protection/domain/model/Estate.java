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
    private String instanceId;
    private final Instant createdAt;
    private boolean persistent;
    private String name;
    /** Adventure kind — controls portal gating and instancing behavior. */
    private EstateType type;
    /** Gated area anchor: world name. Null if the estate has no physical area. */
    private String areaWorld;
    /** Gated area anchor: center X (block coords). */
    private int areaX;
    /** Gated area anchor: center Y (block coords) — used for portal frame scans. */
    private int areaY;
    /** Gated area anchor: center Z (block coords). */
    private int areaZ;
    /** Gated area radius in blocks; 0 = no area. */
    private int areaRadius;
    /**
     * Compact snapshot of the vanilla portal frames captured at area creation,
     * format {@code x,y,z|facing|eye}. Used by the runtime to regenerate the
     * portal room between adventuring groups (broken frames come back, eyes
     * are stripped). Pure data — interpretation lives in the runtime layer.
     */
    private java.util.List<String> portalFrames = java.util.List.of();
    /**
     * Compact registry of naturally-generated loot containers inside the area,
     * format {@code x,y,z|blockType|lootTableKey}. On zone close each container
     * is re-armed with its vanilla loot table under a FRESH random seed —
     * every adventuring group finds different loot. Pure data; interpretation
     * lives in the runtime layer.
     */
    private java.util.List<String> containerLoot = java.util.List.of();

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
        this(id, ownerId, members, adventureId, instanceId, createdAt, persistent, name,
                EstateType.STANDARD, null, 0, 0, 0, 0);
    }

    public Estate(
            UUID id,
            UUID ownerId,
            Set<UUID> members,
            String adventureId,
            String instanceId,
            Instant createdAt,
            boolean persistent,
            String name,
            EstateType type,
            String areaWorld,
            int areaX,
            int areaY,
            int areaZ,
            int areaRadius
    ) {
        this.id = id;
        this.ownerId = ownerId;
        this.members = new HashSet<>(members);
        this.adventureId = adventureId;
        this.instanceId = instanceId;
        this.createdAt = createdAt;
        this.persistent = persistent;
        this.name = name;
        this.type = type != null ? type : EstateType.STANDARD;
        this.areaWorld = areaWorld;
        this.areaX = areaX;
        this.areaY = areaY;
        this.areaZ = areaZ;
        this.areaRadius = Math.max(0, areaRadius);
        // Membership is explicit: the owner is NOT auto-added, so an admin can
        // provision an estate without becoming part of the adventuring group.
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
    public EstateType type() { return type; }
    public String areaWorld() { return areaWorld; }
    public int areaX() { return areaX; }
    public int areaY() { return areaY; }
    public int areaZ() { return areaZ; }
    public int areaRadius() { return areaRadius; }

    public java.util.List<String> portalFrames() { return portalFrames; }

    /** Replaces the portal frame snapshot (immutable copy). Null clears it. */
    public void portalFrames(java.util.List<String> snapshot) {
        this.portalFrames = snapshot == null ? java.util.List.of() : java.util.List.copyOf(snapshot);
    }

    public java.util.List<String> containerLoot() { return containerLoot; }

    /** Replaces the loot container registry (immutable copy). Null clears it. */
    public void containerLoot(java.util.List<String> registry) {
        this.containerLoot = registry == null ? java.util.List.of() : java.util.List.copyOf(registry);
    }

    // ── Mutators ──────────────────────────────────────────────────────────────

    public void ownerId(UUID ownerId) { this.ownerId = ownerId; }
    public void persistent(boolean persistent) { this.persistent = persistent; }
    public void name(String name) { this.name = name; }
    public void type(EstateType type) { this.type = type != null ? type : EstateType.STANDARD; }
    /** Links this Estate to a running instance, or clears it (null) when the instance ends. */
    public void instanceId(String instanceId) { this.instanceId = instanceId; }

    /**
     * Assigns the gated physical area of this estate. A null world or non-positive
     * radius clears the area.
     */
    public void area(String worldName, int x, int y, int z, int radius) {
        if (worldName == null || radius <= 0) {
            this.areaWorld = null;
            this.areaX = 0;
            this.areaY = 0;
            this.areaZ = 0;
            this.areaRadius = 0;
            return;
        }
        this.areaWorld = worldName;
        this.areaX = x;
        this.areaY = y;
        this.areaZ = z;
        this.areaRadius = radius;
    }

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
    /** True if this Estate owns a gated physical area. */
    public boolean hasArea() { return areaWorld != null && areaRadius > 0; }

    /** True if the given block coordinates lie inside this estate's circular area. */
    public boolean contains(String worldName, int x, int z) {
        if (!hasArea() || !areaWorld.equals(worldName)) return false;
        int dx = x - areaX;
        int dz = z - areaZ;
        return (long) dx * dx + (long) dz * dz <= (long) areaRadius * areaRadius;
    }
}
