package dev.dreamcraft.protection.domain.service;

import dev.dreamcraft.protection.domain.model.*;
import dev.dreamcraft.protection.domain.port.WardRepository;
import dev.dreamcraft.protection.domain.port.WardTierProvider;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Domain service for Ward lifecycle management.
 *
 * <p>This service owns all business rules for Wards:
 * score computation, tier resolution, radius calculation, upkeep deduction,
 * and domain permission management.
 *
 * <p>It does <b>not</b> call WorldGuard directly; integration adapters handle that.
 * It does <b>not</b> depend on Bukkit APIs.
 */
public final class WardService {

    private final WardRepository wardRepository;
    private final WardTierProvider tierProvider;
    private final Duration upkeepInterval;

    public WardService(WardRepository wardRepository, WardTierProvider tierProvider, Duration upkeepInterval) {
        this.wardRepository = wardRepository;
        this.tierProvider = tierProvider;
        this.upkeepInterval = upkeepInterval;
    }

    /**
     * Creates a new Ward with the default tier and a computed initial radius.
     *
     * @param ownerId   UUID of the owning player
     * @param ownerType PLAYER or CITY
     * @param cityId    optional city this ward belongs to
     * @param worldName the Minecraft world name
     * @param cx        center X block coordinate
     * @param cy        center Y block coordinate
     * @param cz        center Z block coordinate
     * @return the persisted Ward
     */
    public Ward createWard(
            UUID ownerId,
            OwnerType ownerType,
            UUID cityId,
            String worldName,
            int cx, int cy, int cz
    ) {
        return createWard(ownerId, ownerType, cityId, worldName, cx, cy, cz, null);
    }

    /**
     * Creates a new Ward with an explicit display name. When {@code name} is null
     * or blank a unique friendly name is generated (never a raw UUID fragment).
     */
    public Ward createWard(
            UUID ownerId,
            OwnerType ownerType,
            UUID cityId,
            String worldName,
            int cx, int cy, int cz,
            String name
    ) {
        int baseScore = 0;
        WardTier tier = tierProvider.resolveForScore(baseScore);
        int radius = tier.computeRadius(baseScore);
        Instant now = Instant.now();

        Ward ward = new Ward(
                UUID.randomUUID(),
                resolveUniqueName(name),
                worldName,
                ownerId,
                ownerType,
                cityId,
                baseScore,
                tier.key(),
                radius,
                0,
                now,
                now,
                now.plus(upkeepInterval),
                cx, cy, cz,
                null, // worldGuardRegionId set by integration layer after region creation
                EnumSet.of(WardPermission.PUBLIC_STATUS_VIEW)
        );
        wardRepository.save(ward);
        return ward;
    }

    /**
     * Adds baseScore delta to the Ward and recalculates tier and radius.
     * Persists the updated Ward.
     */
    public void addBaseScore(Ward ward, int delta) {
        int newScore = Math.max(0, ward.baseScore() + delta);
        ward.baseScore(newScore);
        WardTier tier = tierProvider.resolveForScore(newScore);
        ward.tier(tier.key());
        ward.radius(tier.computeRadius(newScore));
        wardRepository.save(ward);
    }

    /**
     * Deposits upkeep units and resets the next upkeep timestamp if the Ward was overdue.
     */
    public void depositUpkeep(Ward ward, int units) {
        ward.upkeepBalance(ward.upkeepBalance() + units);
        Instant now = Instant.now();
        if (now.isAfter(ward.nextUpkeepAt())) {
            ward.nextUpkeepAt(now.plus(upkeepInterval));
        }
        wardRepository.save(ward);
    }

    /**
     * Deducts one interval's worth of upkeep. Called by the upkeep tick task.
     * Returns true if the deduction succeeded (sufficient balance), false if depleted.
     */
    public boolean deductUpkeep(Ward ward, int costPerInterval) {
        if (ward.upkeepBalance() < costPerInterval) {
            ward.upkeepBalance(0);
            wardRepository.save(ward);
            return false;
        }
        ward.upkeepBalance(ward.upkeepBalance() - costPerInterval);
        ward.lastUpkeepAt(Instant.now());
        ward.nextUpkeepAt(ward.lastUpkeepAt().plus(upkeepInterval));
        wardRepository.save(ward);
        return true;
    }

    /**
     * Associates a Ward to a City. Pass null to disassociate.
     */
    public void setCityMembership(Ward ward, UUID cityId) {
        ward.cityId(cityId);
        wardRepository.save(ward);
    }

    /**
     * Transfers ownership of a Ward to a new owner.
     */
    public void transferOwnership(Ward ward, UUID newOwnerId, OwnerType newOwnerType) {
        ward.ownerId(newOwnerId);
        ward.ownerType(newOwnerType);
        wardRepository.save(ward);
    }

    /**
     * Sets the WorldGuard region ID reference (called by the integration layer after
     * region creation). This is the only place the domain accepts an integration artifact.
     */
    public void assignWorldGuardRegion(Ward ward, String regionId) {
        ward.worldGuardRegionId(regionId);
        wardRepository.save(ward);
    }

    public void delete(Ward ward) {
        wardRepository.delete(ward.id());
    }

    public Optional<Ward> findById(UUID id) {
        return wardRepository.findById(id);
    }

    public Optional<Ward> findAtLocation(String worldName, int x, int z) {
        return wardRepository.findAtLocation(worldName, x, z);
    }

    public Optional<Ward> findByCenter(String worldName, int x, int y, int z) {
        return wardRepository.findByCenter(worldName, x, y, z);
    }

    public Collection<Ward> findByOwner(UUID ownerId) {
        return wardRepository.findByOwnerId(ownerId);
    }

    public Collection<Ward> findByCity(UUID cityId) {
        return wardRepository.findByCityId(cityId);
    }

    public Collection<Ward> findAll() {
        return wardRepository.findAll();
    }

    /** Resolves a display name: uses the given one if unique, otherwise generates a friendly unique name. */
    private String resolveUniqueName(String requested) {
        if (requested != null && !requested.isBlank()) {
            String trimmed = requested.trim();
            boolean taken = wardRepository.findAll().stream()
                    .anyMatch(w -> w.name().equalsIgnoreCase(trimmed));
            return taken ? dev.dreamcraft.protection.util.NameGenerator.unique(this::nameTaken) : trimmed;
        }
        return dev.dreamcraft.protection.util.NameGenerator.unique(this::nameTaken);
    }

    private boolean nameTaken(String candidate) {
        return wardRepository.findAll().stream()
                .anyMatch(w -> w.name().equalsIgnoreCase(candidate));
    }
}
