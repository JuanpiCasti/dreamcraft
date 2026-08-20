package dev.dreamcraft.protection.service;

import dev.dreamcraft.protection.config.ProtectionConfig;
import dev.dreamcraft.protection.model.ClaimStats;
import dev.dreamcraft.protection.model.ProtectionClaim;
import dev.dreamcraft.protection.model.ProtectionState;
import dev.dreamcraft.protection.model.TierDefinition;
import dev.dreamcraft.protection.model.UpkeepStorage;
import dev.dreamcraft.protection.persistence.ClaimRepository;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.Locale;

public final class ClaimManager {
    private final ProtectionConfig config;
    private final ClaimIndex claimIndex;
    private final ClaimRepository repository;
    private final BuildingCostService buildingCostService;

    public ClaimManager(ProtectionConfig config, ClaimIndex claimIndex, ClaimRepository repository, BuildingCostService buildingCostService) {
        this.config = config;
        this.claimIndex = claimIndex;
        this.repository = repository;
        this.buildingCostService = buildingCostService;
    }

    public void load() {
        for (ProtectionClaim claim : repository.loadAll()) {
            claimIndex.add(claim);
        }
    }

    public void save() throws IOException {
        repository.saveAll(claimIndex.allClaims());
    }

    /**
     * Creates a new claim centred on wardrobeBlock using the given tier.
     * Falls back to "basic" tier if tierKey is unknown.
     * Returns empty if:
     * - overlap is disabled and the area overlaps an existing claim, or
     * - the wardrobe block itself sits inside an existing active claim.
     */
    public Optional<ProtectionClaim> createClaim(Player owner, Block wardrobeBlock, String tierKey) {
        // Wardrobe placement inside an existing claim is always forbidden
        Optional<ProtectionClaim> existing = claimIndex.findClaim(
                wardrobeBlock.getWorld().getName(), wardrobeBlock.getX(), wardrobeBlock.getZ());
        if (existing.isPresent()) {
            return Optional.empty();
        }

        // Resolve tier — default to "basic" for new claims
        TierDefinition tier = config.tiers().getOrDefault(
                tierKey.toLowerCase(Locale.ROOT),
                config.tiers().getOrDefault("basic",
                        new TierDefinition("basic", config.defaultRadius(), config.defaultBuildRadius(), config.defaultMaxMembers())));

        Instant now = Instant.now();
        ProtectionClaim claim = new ProtectionClaim(
                UUID.randomUUID(),
                wardrobeBlock.getWorld().getName(),
                owner.getUniqueId(),
                wardrobeBlock.getX(),
                wardrobeBlock.getY(),
                wardrobeBlock.getZ(),
                tier.radius(),
                tier.buildRadius(),
                ProtectionState.ACTIVE,
                tier.key(),
                now,
                now,
                now.plus(config.upkeepInterval()),
                now,
                new HashSet<>(),
                new HashMap<>(),
                new ClaimStats(),
                new UpkeepStorage(),
                wardrobeBlock.getX(),
                wardrobeBlock.getY(),
                wardrobeBlock.getZ()
        );
        if (!config.allowOverlap() && claimIndex.overlaps(claim)) {
            return Optional.empty();
        }
        claimIndex.add(claim);
        return Optional.of(claim);
    }

    public Optional<ProtectionClaim> findByLocation(Location location) {
        return claimIndex.findClaim(location.getWorld().getName(), location.getBlockX(), location.getBlockZ());
    }

    public Collection<ProtectionClaim> allClaims() {
        return claimIndex.allClaims();
    }

    public ClaimIndex claimIndex() {
        return claimIndex;
    }

    /**
     * Finds the claim whose wardrobe is at the given block position.
     * Uses the spatial index: first narrows candidates to the single cell
     * at the wardrobe position, then checks exact coordinates.
     */
    public Optional<ProtectionClaim> findByWardrobe(Block block) {
        // Wardrobe location equals claim center, so findClaim covers it directly.
        // If multiple claims somehow share the same cell we verify the exact wardrobe coords.
        String world = block.getWorld().getName();
        int x = block.getX(), y = block.getY(), z = block.getZ();
        return claimIndex.allClaims().stream()
                .filter(claim -> claim.world().equals(world) && claim.isWardrobe(x, y, z))
                .findFirst();
    }

    public boolean removeClaim(ProtectionClaim claim) {
        claimIndex.remove(claim.id());
        return true;
    }

    /**
     * Adds a member if the tier's max-members limit has not been reached.
     * Returns false if the claim is full.
     */
    public boolean addMember(ProtectionClaim claim, UUID uuid) {
        TierDefinition tier = config.tiers().get(claim.tier());
        int maxMembers = tier != null ? tier.maxMembers() : config.defaultMaxMembers();
        if (claim.members().size() >= maxMembers) {
            return false;
        }
        claim.members().add(uuid);
        return true;
    }

    public void removeMember(ProtectionClaim claim, UUID uuid) {
        claim.members().remove(uuid);
    }

    /** Transfers ownership. Respects the ownerTransfer config flag. */
    public boolean transferOwner(ProtectionClaim claim, UUID newOwner) {
        if (!config.ownerTransfer()) {
            return false;
        }
        claim.ownerUuid(newOwner);
        claim.members().remove(newOwner);
        return true;
    }

    public void trackPlace(ProtectionClaim claim, Material material) {
        claim.stats().increment(material.name(), buildingCostService.categoryOf(material));
        claim.lastActivityAt(Instant.now());
    }

    public void trackBreak(ProtectionClaim claim, Material material) {
        claim.stats().decrement(material.name(), buildingCostService.categoryOf(material));
        claim.lastActivityAt(Instant.now());
    }

    public void recordActivity(ProtectionClaim claim) {
        claim.lastActivityAt(Instant.now());
    }

    public void depositUpkeep(ProtectionClaim claim, int units) {
        claim.upkeepStorage().deposit("maintenance", units);
        claim.lastActivityAt(Instant.now());
    }
}
