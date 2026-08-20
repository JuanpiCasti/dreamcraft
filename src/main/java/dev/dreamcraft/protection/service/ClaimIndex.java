package dev.dreamcraft.protection.service;

import dev.dreamcraft.protection.model.ProtectionClaim;

import java.util.*;

public final class ClaimIndex {
    // Grid cell size in blocks. Each claim is indexed into every cell it overlaps.
    private static final int GRID_SIZE = 16;

    private final Map<UUID, ProtectionClaim> claimsById = new HashMap<>();
    private final Map<String, Map<Long, Set<UUID>>> worldGrid = new HashMap<>();

    public void add(ProtectionClaim claim) {
        claimsById.put(claim.id(), claim);
        index(claim);
    }

    public void remove(UUID claimId) {
        ProtectionClaim claim = claimsById.remove(claimId);
        if (claim != null) {
            deindex(claim);
        }
    }

    public Collection<ProtectionClaim> allClaims() {
        return Collections.unmodifiableCollection(claimsById.values());
    }

    public Optional<ProtectionClaim> byId(UUID id) {
        return Optional.ofNullable(claimsById.get(id));
    }

    /**
     * Finds the first claim whose protectionBounds contains (x, z) in the given world.
     *
     * <p>Strategy: look up the single grid cell that contains (x, z). Every claim that
     * overlaps that cell was indexed into it during {@link #add}, so checking only that
     * cell's candidate set is sufficient and correct — no O(n) global scan needed.
     */
    public Optional<ProtectionClaim> findClaim(String world, int x, int z) {
        Map<Long, Set<UUID>> grid = worldGrid.get(world);
        if (grid == null) {
            return Optional.empty();
        }
        // The cell that contains point (x, z)
        long key = cellKey(floorDiv(x, GRID_SIZE), floorDiv(z, GRID_SIZE));
        Set<UUID> candidates = grid.get(key);
        if (candidates == null) {
            return Optional.empty();
        }
        for (UUID claimId : candidates) {
            ProtectionClaim claim = claimsById.get(claimId);
            if (claim != null && claim.protectionBounds().contains(x, z)) {
                return Optional.of(claim);
            }
        }
        return Optional.empty();
    }

    public boolean overlaps(ProtectionClaim candidate) {
        for (ProtectionClaim claim : nearbyClaims(
                candidate.world(),
                candidate.protectionBounds().minX(),
                candidate.protectionBounds().maxX(),
                candidate.protectionBounds().minZ(),
                candidate.protectionBounds().maxZ())) {
            if (!claim.id().equals(candidate.id()) && claim.protectionBounds().intersects(candidate.protectionBounds())) {
                return true;
            }
        }
        return false;
    }

    public Collection<ProtectionClaim> nearbyClaims(String world, int minX, int maxX, int minZ, int maxZ) {
        Map<Long, Set<UUID>> grid = worldGrid.getOrDefault(world, Map.of());
        Set<ProtectionClaim> results = new HashSet<>();
        for (int cellX = floorDiv(minX, GRID_SIZE); cellX <= floorDiv(maxX, GRID_SIZE); cellX++) {
            for (int cellZ = floorDiv(minZ, GRID_SIZE); cellZ <= floorDiv(maxZ, GRID_SIZE); cellZ++) {
                Set<UUID> ids = grid.get(cellKey(cellX, cellZ));
                if (ids == null) {
                    continue;
                }
                for (UUID id : ids) {
                    ProtectionClaim claim = claimsById.get(id);
                    if (claim != null) {
                        results.add(claim);
                    }
                }
            }
        }
        return results;
    }

    /**
     * Index a claim into every grid cell its protectionBounds overlaps.
     * This ensures that findClaim(world, x, z) can always find the claim
     * by looking up only the cell containing (x, z).
     */
    private void index(ProtectionClaim claim) {
        Map<Long, Set<UUID>> grid = worldGrid.computeIfAbsent(claim.world(), key -> new HashMap<>());
        for (int cellX = floorDiv(claim.protectionBounds().minX(), GRID_SIZE);
             cellX <= floorDiv(claim.protectionBounds().maxX(), GRID_SIZE); cellX++) {
            for (int cellZ = floorDiv(claim.protectionBounds().minZ(), GRID_SIZE);
                 cellZ <= floorDiv(claim.protectionBounds().maxZ(), GRID_SIZE); cellZ++) {
                grid.computeIfAbsent(cellKey(cellX, cellZ), ignored -> new HashSet<>()).add(claim.id());
            }
        }
    }

    private void deindex(ProtectionClaim claim) {
        Map<Long, Set<UUID>> grid = worldGrid.get(claim.world());
        if (grid == null) {
            return;
        }
        for (int cellX = floorDiv(claim.protectionBounds().minX(), GRID_SIZE);
             cellX <= floorDiv(claim.protectionBounds().maxX(), GRID_SIZE); cellX++) {
            for (int cellZ = floorDiv(claim.protectionBounds().minZ(), GRID_SIZE);
                 cellZ <= floorDiv(claim.protectionBounds().maxZ(), GRID_SIZE); cellZ++) {
                Set<UUID> ids = grid.get(cellKey(cellX, cellZ));
                if (ids == null) {
                    continue;
                }
                ids.remove(claim.id());
                if (ids.isEmpty()) {
                    grid.remove(cellKey(cellX, cellZ));
                }
            }
        }
        if (grid.isEmpty()) {
            worldGrid.remove(claim.world());
        }
    }

    private long cellKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private int floorDiv(int value, int gridSize) {
        return Math.floorDiv(value, gridSize);
    }
}
