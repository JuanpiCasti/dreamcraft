package dev.dreamcraft.protection.model;

public record ClaimBounds(int minX, int maxX, int minZ, int maxZ) {
    public boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public boolean intersects(ClaimBounds other) {
        return minX <= other.maxX && maxX >= other.minX && minZ <= other.maxZ && maxZ >= other.minZ;
    }
}
