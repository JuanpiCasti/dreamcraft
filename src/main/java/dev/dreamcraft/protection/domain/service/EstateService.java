package dev.dreamcraft.protection.domain.service;

import dev.dreamcraft.protection.domain.model.Estate;
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
        Estate estate = new Estate(
                UUID.randomUUID(),
                ownerId,
                new HashSet<>(),
                adventureId,
                instanceId,
                Instant.now(),
                persistent,
                name
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
}
