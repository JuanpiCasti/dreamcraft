package dev.dreamcraft.protection.domain.service;

import dev.dreamcraft.protection.domain.model.Estate;
import dev.dreamcraft.protection.domain.model.EstateType;
import dev.dreamcraft.protection.domain.port.EstateRepository;

import java.time.Instant;
import java.util.*;

/**
 * Domain service for Estate lifecycle management.
 *
 * <p>Estates are temporary or persistent groups for adventure coordination.
 * Independent of City/Ward. No territorial claims, no LuckPerms groups.
 * Does not depend on Bukkit APIs.
 */
public final class EstateService {

    private final EstateRepository estateRepository;

    public EstateService(EstateRepository estateRepository) {
        this.estateRepository = estateRepository;
    }

    /**
     * Creates a new Estate.
     *
     * @param ownerId     UUID of the owner
     * @param name        display name for the estate
     * @param adventureId optional adventure ID (null if not adventure-linked)
     * @param instanceId  optional instance ID (null if not instanced)
     * @param persistent  if false, the estate can be cleaned up after the server restarts
     */
    public Estate createEstate(
            UUID ownerId,
            String name,
            String adventureId,
            String instanceId,
            boolean persistent
    ) {
        return createEstate(ownerId, name, adventureId, instanceId, persistent,
                EstateType.STANDARD, null, 0, 0, 0, 0);
    }

    /**
     * Creates a new Estate with an explicit adventure type and optional gated area.
     *
     * @param type       adventure kind (STANDARD / END / TRIAL_CHAMBER)
     * @param areaWorld  world of the gated area (null = no area)
     * @param areaX      area center X
     * @param areaY      area center Y
     * @param areaZ      area center Z
     * @param areaRadius area radius in blocks (0 = no area)
     */
    public Estate createEstate(
            UUID ownerId,
            String name,
            String adventureId,
            String instanceId,
            boolean persistent,
            EstateType type,
            String areaWorld,
            int areaX,
            int areaY,
            int areaZ,
            int areaRadius
    ) {
        Estate estate = new Estate(
                UUID.randomUUID(),
                ownerId,
                new HashSet<>(),
                adventureId,
                instanceId,
                Instant.now(),
                persistent,
                name,
                type,
                areaWorld,
                areaX,
                areaY,
                areaZ,
                areaRadius
        );
        estateRepository.save(estate);
        return estate;
    }

    public boolean addMember(Estate estate, UUID playerId) {
        if (estate.isMember(playerId)) return false;
        estate.addMember(playerId);
        estateRepository.save(estate);
        return true;
    }

    public boolean removeMember(Estate estate, UUID playerId) {
        boolean removed = estate.removeMember(playerId);
        if (removed) estateRepository.save(estate);
        return removed;
    }

    /**
     * Transfers ownership of the estate to an existing member.
     */
    public boolean transferOwnership(Estate estate, UUID newOwnerId) {
        if (!estate.isMember(newOwnerId)) return false;
        estate.ownerId(newOwnerId);
        estate.addMember(newOwnerId); // ensure owner is in member set
        estateRepository.save(estate);
        return true;
    }

    public void makePersistent(Estate estate, boolean persistent) {
        estate.persistent(persistent);
        estateRepository.save(estate);
    }

    /** Changes the adventure type of the estate (e.g. STANDARD → END). */
    public void setType(Estate estate, EstateType type) {
        estate.type(type);
        estateRepository.save(estate);
    }

    /**
     * Assigns or moves the gated physical area of the estate.
     * A null world or non-positive radius clears the area.
     */
    public void setArea(Estate estate, String worldName, int x, int y, int z, int radius) {
        estate.area(worldName, x, y, z, radius);
        estateRepository.save(estate);
    }

    /**
     * Starts a new instance for the estate, linking it to an instance ID.
     * Only the owner can start an instance. Returns false if already instanced.
     *
     * @param instanceId the running instance identifier (must not be null)
     */
    public boolean startInstance(Estate estate, String instanceId) {
        if (estate.isInstanced()) return false;
        if (instanceId == null) return false;
        estate.instanceId(instanceId);
        estateRepository.save(estate);
        return true;
    }

    /**
     * Ends the current instance, clearing the instance link.
     * Returns false if the estate was not instanced.
     */
    public boolean endInstance(Estate estate) {
        if (!estate.isInstanced()) return false;
        estate.instanceId(null);
        estateRepository.save(estate);
        return true;
    }

    public void delete(Estate estate) {
        estateRepository.delete(estate.id());
    }

    public void cleanupTransient() {
        estateRepository.deleteAllTransient();
    }

    public Optional<Estate> findById(UUID id) { return estateRepository.findById(id); }
    public Collection<Estate> findByOwner(UUID ownerId) { return estateRepository.findByOwnerId(ownerId); }
    public Collection<Estate> findByMember(UUID memberId) { return estateRepository.findByMember(memberId); }
    public Collection<Estate> findByAdventure(String adventureId) { return estateRepository.findByAdventureId(adventureId); }
    public Collection<Estate> findAll() { return estateRepository.findAll(); }

    /** All estates of the given adventure type. */
    public Collection<Estate> findByType(EstateType type) {
        return estateRepository.findAll().stream()
                .filter(e -> e.type() == type)
                .toList();
    }

    /**
     * Finds the estate whose gated area contains the given block coordinates.
     * When several areas overlap, the smallest (most specific) wins.
     */
    public Optional<Estate> findAreaAt(String worldName, int x, int z) {
        return estateRepository.findAll().stream()
                .filter(e -> e.contains(worldName, x, z))
                .min(Comparator.comparingInt(Estate::areaRadius));
    }

    /**
     * All instanced-adventure estates (END / TRIAL_CHAMBER) whose gated area
     * contains the given block coordinates. Several parties may share one zone:
     * each has its own estate and, for END, its own private instance world.
     */
    public Collection<Estate> findInstancedAreasAt(String worldName, int x, int z) {
        return estateRepository.findAll().stream()
                .filter(e -> e.type().isInstancedAdventure())
                .filter(e -> e.contains(worldName, x, z))
                .toList();
    }

    /**
     * The admin-created zone template for an adventure type: an estate of that
     * type which owns a gated area. Party estates copy their area from it.
     * Admin zones are always persistent — player parties (persistent = false)
     * never act as templates.
     */
    public Optional<Estate> findZoneTemplate(EstateType type) {
        return findByType(type).stream()
                .filter(Estate::persistent)
                .filter(Estate::hasArea)
                .findFirst();
    }

    /**
     * Creates a personal party estate for a player: they become the owner
     * (leader) and can invite their group. When a zone template exists, the
     * party inherits its gated area so portal gating recognizes its members.
     *
     * @param ownerId   the player who discovered/joined — becomes leader
     * @param ownerName display name for the estate title
     * @param type      adventure kind (END / TRIAL_CHAMBER / STANDARD)
     * @param zone      the zone template to inherit the area from (nullable)
     */
    public Estate createPartyEstate(UUID ownerId, String ownerName, EstateType type, Estate zone) {
        String label = type == EstateType.END ? "Estancia" : type.displayName();
        String name = label + " de " + ownerName;
        if (zone != null && zone.hasArea()) {
            Estate party = createEstate(ownerId, name, "adv-" + type.key(), null, false, type,
                    zone.areaWorld(), zone.areaX(), zone.areaY(), zone.areaZ(), zone.areaRadius());
            // Inherit the regeneration snapshots so every party gets the same
            // portal repair and loot re-roll guarantees as the zone template
            if (!zone.portalFrames().isEmpty() || !zone.containerLoot().isEmpty()) {
                party.portalFrames(zone.portalFrames());
                party.containerLoot(zone.containerLoot());
                estateRepository.save(party);
            }
            return party;
        }
        return createEstate(ownerId, name, "adv-" + type.key(), null, false, type,
                null, 0, 0, 0, 0);
    }

    /** Persists any direct mutation done outside the service (e.g. frame snapshots). */
    public void save(Estate estate) {
        estateRepository.save(estate);
    }
}
