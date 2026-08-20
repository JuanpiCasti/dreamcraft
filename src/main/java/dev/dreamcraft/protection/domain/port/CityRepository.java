package dev.dreamcraft.protection.domain.port;

import dev.dreamcraft.protection.domain.model.City;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Output port: persistence contract for City aggregates.
 */
public interface CityRepository {

    Optional<City> findById(UUID id);

    Optional<City> findByName(String name);

    Optional<City> findByGovernor(UUID governorId);

    /** Check if a player is a member of any City. */
    Optional<City> findByMember(UUID memberId);

    Collection<City> findAll();

    void save(City city);

    void delete(UUID id);

    void saveAll(Collection<City> cities);
}
