package dev.dreamcraft.protection.domain.service;

import dev.dreamcraft.protection.domain.model.*;
import dev.dreamcraft.protection.domain.port.WardRepository;
import dev.dreamcraft.protection.domain.port.WardTierProvider;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

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
    /** Hard ceiling for a single Ward's radius (ward.max-radius); MAX_VALUE = uncapped. */
    private final int maxRadius;

    /**
     * Optional presentation hook fired when a real tier ascent wipes the
     * below-tier surcharge counter. Receives (ward, previousCounterValue).
     * No-op by default; the domain never depends on Bukkit for this.
     */
    private BiConsumer<Ward, Integer> tierAlignedCallback = (ward, previous) -> { };

    /**
     * Optional hook fired when a tier transition descends (only reachable via
     * admin score edits). The counter is intentionally NOT touched here — the
     * authoritative value requires a world re-scan — so listeners can trigger
     * {@code seedExistingBelowTierBlocks}. No-op by default.
     */
    private Consumer<Ward> tierDescendedCallback = ward -> { };

    public WardService(WardRepository wardRepository, WardTierProvider tierProvider, Duration upkeepInterval) {
        this(wardRepository, tierProvider, upkeepInterval, Integer.MAX_VALUE);
    }

    public WardService(WardRepository wardRepository, WardTierProvider tierProvider,
                       Duration upkeepInterval, int maxRadius) {
        this.wardRepository = wardRepository;
        this.tierProvider = tierProvider;
        this.upkeepInterval = upkeepInterval;
        this.maxRadius = Math.max(1, maxRadius);
    }

    /** Clamps any computed radius to the configured {@code ward.max-radius} ceiling. */
    private int clampRadius(int radius) {
        return Math.min(radius, maxRadius);
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
        int radius = clampRadius(tier.computeRadius(baseScore));
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
     *
     * <p>Below-tier surcharge policy on tier transitions (this is the single
     * choke point for EVERY tier change: /ward upgrade, menu upgrades and
     * admin score edits):
     * <ul>
     *   <li><b>Ascent</b> (new tier min-base-score strictly greater): the
     *       {@code belowTierBlocks} counter is wiped to 0 before saving — a
     *       higher phase now covers the previously under-ranked blocks, so
     *       charging their surcharge forever would be unfair. The optional
     *       {@code tierAlignedCallback} fires afterwards with (ward,
     *       previousCount) so the presentation layer can notify the owner.</li>
     *   <li><b>Descent</b> (admin-only via score remove/set): the counter is
     *     intentionally NOT modified here — the authoritative value depends on
     *     which gated blocks actually remain inside the (possibly shrunk) area.
     *     A world re-scan is expected instead, wired through
     *     {@link #setTierDescendedCallback(Consumer)}.</li>
     *   <li><b>Intra-tier</b>: the counter is untouched.</li>
     * </ul>
     */
    public void addBaseScore(Ward ward, int delta) {
        int newScore = Math.max(0, ward.baseScore() + delta);
        String oldTierKey = ward.tier();
        ward.baseScore(newScore);
        WardTier tier = tierProvider.resolveForScore(newScore);
        ward.tier(tier.key());
        ward.radius(clampRadius(tier.computeRadius(newScore)));

        int previousBelowTier = ward.belowTierBlocks();
        boolean ascended = false;
        boolean descended = false;
        Optional<WardTier> oldTier = tierProvider.findByKey(oldTierKey);
        if (oldTier.isPresent()) {
            int oldMin = oldTier.get().minBaseScore();
            int newMin = tier.minBaseScore();
            if (newMin > oldMin) {
                ascended = true;
                ward.belowTierBlocks(0);
            } else if (newMin < oldMin) {
                descended = true; // counter untouched: covered by re-scan hook
            }
        }

        wardRepository.save(ward);

        if (ascended) {
            tierAlignedCallback.accept(ward, previousBelowTier);
        } else if (descended) {
            tierDescendedCallback.accept(ward);
        }
    }

    /**
     * Radius the Ward would have after gaining {@code delta} base score,
     * without mutating or persisting anything. Used to validate upgrades
     * before they are applied.
     */
    public int computeRadiusAfter(Ward ward, int delta) {
        int newScore = Math.max(0, ward.baseScore() + delta);
        return clampRadius(tierProvider.resolveForScore(newScore).computeRadius(newScore));
    }

    /**
     * First FOREIGN Ward (different owner) whose center would fall inside this
     * Ward's area if its radius grew to {@code newRadius}. Wards owned by the
     * same owner are ignored (self-stacking is harmless). Use this before any
     * operation that grows the Ward's radius to refuse the change with a
     * meaningful message instead of silently swallowing a neighbor.
     */
    public Optional<Ward> findForeignConflict(Ward ward, int newRadius) {
        return wardRepository.findConflicting(
                        ward.worldName(), ward.centerX(), ward.centerZ(), newRadius, ward.id())
                .filter(other -> !other.ownerId().equals(ward.ownerId()));
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
     * Renames a Ward manually. Keeps uniqueness across all Wards.
     *
     * @throws IllegalArgumentException when blank, longer than 32 chars, or already taken
     */
    public void renameWard(Ward ward, String newName) {
        String trimmed = newName == null ? "" : newName.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("El nombre no puede estar vacío.");
        }
        if (trimmed.length() > 32) {
            throw new IllegalArgumentException("El nombre no puede superar 32 caracteres.");
        }
        boolean taken = wardRepository.findAll().stream()
                .anyMatch(w -> !w.id().equals(ward.id()) && w.name().equalsIgnoreCase(trimmed));
        if (taken) {
            throw new IllegalArgumentException("Ya existe un Ward llamado " + trimmed + ".");
        }
        ward.name(trimmed);
        wardRepository.save(ward);
    }

    /**
     * Sets the below-tier gated block counter and persists the Ward.
     * Used by the placement gate (increment) and its break counterpart
     * (decrement). Clamped at ≥ 0.
     */
    public void setBelowTierBlocks(Ward ward, int count) {
        ward.belowTierBlocks(count);
        wardRepository.save(ward);
    }

    /**
     * Registers the presentation-side notice fired when a tier ascent aligns
     * the Ward's phase and wipes its below-tier surcharge counter.
     * Receives (ward, previousCounterValue); null restores the no-op default.
     */
    public void setTierAlignedCallback(BiConsumer<Ward, Integer> callback) {
        this.tierAlignedCallback = callback != null ? callback : (ward, previous) -> { };
    }

    /**
     * Registers the hook fired when a tier transition descends. The plugin
     * wires this to a world re-scan that replaces {@code belowTierBlocks} with
     * the real current count (the domain itself stays Bukkit-free).
     * Null restores the no-op default.
     */
    public void setTierDescendedCallback(Consumer<Ward> callback) {
        this.tierDescendedCallback = callback != null ? callback : ward -> { };
    }

    /**
     * Associates a Ward to a City. Pass null to disassociate.
     */
    public void setCityMembership(Ward ward, UUID cityId) {
        ward.cityId(cityId);
        wardRepository.save(ward);
    }

    /**
     * Adds a direct trusted member to a Ward.
     * Returns true if added, false if already member or owner.
     */
    public boolean addMember(Ward ward, UUID playerId) {
        if (ward.addMember(playerId)) {
            wardRepository.save(ward);
            return true;
        }
        return false;
    }

    /**
     * Removes a direct member from a Ward.
     * Returns true if removed, false if not found.
     */
    public boolean removeMember(Ward ward, UUID playerId) {
        if (ward.removeMember(playerId)) {
            wardRepository.save(ward);
            return true;
        }
        return false;
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
