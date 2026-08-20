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

    Collection<Ward> findAll();

    void save(Ward ward);

    void delete(UUID id);

    void saveAll(Collection<Ward> wards);
}
