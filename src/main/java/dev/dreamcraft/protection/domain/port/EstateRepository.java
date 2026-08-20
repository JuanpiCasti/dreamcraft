package dev.dreamcraft.protection.domain.port;

import dev.dreamcraft.protection.domain.model.Estate;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Output port: persistence contract for Estate aggregates.
 */
public interface EstateRepository {

    Optional<Estate> findById(UUID id);

    Collection<Estate> findByOwnerId(UUID ownerId);

    /** Find all Estates a player is a member of. */
    Collection<Estate> findByMember(UUID memberId);

    /** Find Estates linked to a specific adventure. */
    Collection<Estate> findByAdventureId(String adventureId);

    Collection<Estate> findAll();

    void save(Estate estate);

    void delete(UUID id);

    /** Remove all non-persistent Estates (cleanup on shutdown or restart). */
    void deleteAllTransient();
}
