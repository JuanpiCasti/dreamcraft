package dev.dreamcraft.protection.domain.port;

import dev.dreamcraft.protection.domain.model.Ward;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Output port: persistence contract for Ward aggregates.
 * Implementations live in the persistence layer, not the domain.
 */
public interface WardRepository {

    Optional<Ward> findById(UUID id);

    /** Find Wards by owner (player or city). */
    Collection<Ward> findByOwnerId(UUID ownerId);

    /** Find all Wards belonging to a City. */
    Collection<Ward> findByCityId(UUID cityId);

    /** Find the Ward whose center is in the given world at or near (x, z) within radius. */
    Optional<Ward> findAtLocation(String worldName, int x, int z);

    /**
     * Find the first Ward (excluding {@code excludeId}) whose center lies within
     * the square of half-width {@code radius} around (x, z) in the given world.
     * Used to detect conflicts before growing a Ward's area.
     */
    Optional<Ward> findConflicting(String worldName, int x, int z, int radius, UUID excludeId);

    /** Find the Ward whose special center block sits exactly at the given position. */
    Optional<Ward> findByCenter(String worldName, int x, int y, int z);

    Collection<Ward> findAll();

    void save(Ward ward);

    void delete(UUID id);

    void saveAll(Collection<Ward> wards);
}
