package dev.dreamcraft.protection.domain.service;

import dev.dreamcraft.protection.domain.model.City;
import dev.dreamcraft.protection.domain.model.CityPolicy;
import dev.dreamcraft.protection.domain.model.CityRole;
import dev.dreamcraft.protection.domain.port.CityRepository;

import java.time.Instant;
import java.util.*;

/**
 * Domain service for City lifecycle management.
 *
 * <p>Manages governor, member roster, roles, treasury, City Score, and policies.
 * Does not create LuckPerms groups. Does not depend on Bukkit APIs.
 */
public final class CityService {

    private final CityRepository cityRepository;

    public CityService(CityRepository cityRepository) {
        this.cityRepository = cityRepository;
    }

    /**
     * Creates a new City with the given governor.
     * The governor is automatically added as GOVERNOR member.
     */
    public City createCity(UUID governorId, String name) {
        if (cityRepository.findByName(name).isPresent()) {
            throw new IllegalArgumentException("A city with the name '" + name + "' already exists.");
        }
        Map<UUID, CityRole> members = new HashMap<>();
        members.put(governorId, CityRole.GOVERNOR);
        City city = new City(
                UUID.randomUUID(),
                governorId,
                members,
                0L,
                0,
                Instant.now(),
                name,
                EnumSet.of(CityPolicy.PUBLIC_LISTING)
        );
        cityRepository.save(city);
        return city;
    }

    /**
     * Adds a member to the city with the CITIZEN role.
     * Returns false if the player is already a member.
     */
    public boolean addMember(City city, UUID playerId) {
        if (city.isMember(playerId)) return false;
        city.addMember(playerId, CityRole.CITIZEN);
        cityRepository.save(city);
        return true;
    }

    /**
     * Changes the role of an existing member. The governor role cannot be granted this way;
     * use {@link #transferGovernorship} instead.
     */
    public boolean setRole(City city, UUID playerId, CityRole role) {
        if (role == CityRole.GOVERNOR) return false; // use transferGovernorship
        if (!city.isMember(playerId)) return false;
        city.addMember(playerId, role); // addMember replaces if present
        cityRepository.save(city);
        return true;
    }

    /**
     * Removes a member. The governor cannot be removed; use {@link #transferGovernorship} first.
     */
    public boolean removeMember(City city, UUID playerId) {
        boolean removed = city.removeMember(playerId);
        if (removed) cityRepository.save(city);
        return removed;
    }

    /**
     * Transfers governorship from current governor to an existing member.
     */
    public boolean transferGovernorship(City city, UUID newGovernorId) {
        if (!city.isMember(newGovernorId)) return false;
        UUID oldGovernorId = city.governorId();
        city.governorId(newGovernorId);
        city.addMember(newGovernorId, CityRole.GOVERNOR);
        city.addMember(oldGovernorId, CityRole.COUNCIL); // demote old governor to council
        cityRepository.save(city);
        return true;
    }

    public void depositTreasury(City city, long amount) {
        city.depositTreasury(amount);
        cityRepository.save(city);
    }

    public boolean withdrawTreasury(City city, long amount) {
        boolean ok = city.withdrawTreasury(amount);
        if (ok) cityRepository.save(city);
        return ok;
    }

    public void addCityScore(City city, int delta) {
        city.addCityScore(delta);
        cityRepository.save(city);
    }

    public void setPolicy(City city, CityPolicy policy, boolean enabled) {
        if (enabled) city.enablePolicy(policy);
        else city.disablePolicy(policy);
        cityRepository.save(city);
    }

    public void delete(City city) {
        cityRepository.delete(city.id());
    }

    public Optional<City> findById(UUID id) { return cityRepository.findById(id); }
    public Optional<City> findByName(String name) { return cityRepository.findByName(name); }
    public Optional<City> findByMember(UUID memberId) { return cityRepository.findByMember(memberId); }
    public Collection<City> findAll() { return cityRepository.findAll(); }
}
