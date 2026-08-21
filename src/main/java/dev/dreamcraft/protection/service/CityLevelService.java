package dev.dreamcraft.protection.service;

import dev.dreamcraft.protection.config.CityLevelDefinition;
import dev.dreamcraft.protection.config.ProtectionConfig;
import dev.dreamcraft.protection.domain.model.City;
import dev.dreamcraft.protection.domain.model.Ward;
import dev.dreamcraft.protection.domain.service.WardService;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Computes a City's level from live progress — never from direct credit deposits.
 *
 * <p>Progress inputs:
 * <ul>
 *   <li><b>wards</b> — wards annexed to the city</li>
 *   <li><b>members</b> — inhabitants of the city</li>
 *   <li><b>wealth</b> — dynamic score: the treasury vault's item value plus the
 *       baseScore of annexed wards that are "in good standing" (positive upkeep
 *       balance). Both parts move up and down on their own: deposits/withdrawals
 *       change the treasury value, and the upkeep tick drains ward balances.</li>
 * </ul>
 *
 * <p>The resolved level is the highest configured level whose three minimums
 * are all satisfied. Levels are read from {@code city-levels.levels}.
 */
public final class CityLevelService {

    /** Immutable snapshot of a city's progression towards levels. */
    public record CityLevelStatus(
            String levelKey,
            String levelName,
            int wards,
            int members,
            int wealth,
            boolean maxed,
            String nextLevelName,
            int needWards,
            int needMembers,
            int needWealth
    ) {
        /** True when every requirement of the next level is already met. */
        public boolean nextReady() {
            return !maxed
                    && wards >= needWards && members >= needMembers && wealth >= needWealth;
        }
    }

    private final WardService wardService;
    private final List<CityLevelDefinition> levels; // sorted ascending by requirements
    /** Optional: persistent city treasury vaults contributing item value to wealth. */
    private final dev.dreamcraft.protection.persistence.CityTreasuryStore treasuryStore;

    public CityLevelService(WardService wardService, ProtectionConfig config) {
        this(wardService, config.cityLevels(), null);
    }

    /** Test-friendly constructor taking the raw level list (no treasury store). */
    public CityLevelService(WardService wardService, List<CityLevelDefinition> configuredLevels) {
        this(wardService, configuredLevels, null);
    }

    public CityLevelService(WardService wardService,
                            List<CityLevelDefinition> configuredLevels,
                            dev.dreamcraft.protection.persistence.CityTreasuryStore treasuryStore) {
        this.wardService = wardService;
        this.treasuryStore = treasuryStore;
        this.levels = configuredLevels == null ? List.of() : configuredLevels.stream()
                .sorted(Comparator.comparingInt(CityLevelDefinition::minWealth)
                        .thenComparingInt(CityLevelDefinition::minWards)
                        .thenComparingInt(CityLevelDefinition::minMembers))
                .toList();
    }

    /** @return the current level status for the given city. Never null. */
    public CityLevelStatus statusOf(City city) {
        List<Ward> annexed = List.copyOf(wardService.findByCity(city.id()));
        int wards = annexed.size();
        int members = city.members().size();

        // Dynamic wealth: healthy wards + treasury vault content value
        int wardWealth = annexed.stream()
                .filter(w -> w.upkeepBalance() > 0) // "en buen estado" — upkeep paid
                .mapToInt(Ward::baseScore)
                .sum();
        int treasuryValue = treasuryStore != null
                ? treasuryStore.computeValue(treasuryStore.get(city.id()))
                : 0;
        int wealth = wardWealth + treasuryValue;

        CityLevelDefinition current = null;
        for (CityLevelDefinition lvl : levels) {
            if (wards >= lvl.minWards() && members >= lvl.minMembers() && wealth >= lvl.minWealth()) {
                current = lvl;
            }
        }
        // Fallback when no config: everything is level 0
        String key = current != null ? current.key() : "aldea";
        String name = current != null ? current.displayName() : capitalize(key);

        final CityLevelDefinition reached = current;
        Optional<CityLevelDefinition> next = levels.stream()
                .filter(l -> reached == null || l.minWealth() > reached.minWealth()
                        || l.minWards() > reached.minWards()
                        || l.minMembers() > reached.minMembers())
                .findFirst();

        if (next.isEmpty()) {
            return new CityLevelStatus(key, name, wards, members, wealth,
                    true, null, 0, 0, 0);
        }
        CityLevelDefinition n = next.get();
        return new CityLevelStatus(key, name, wards, members, wealth,
                false, n.displayName(),
                Math.max(0, n.minWards() - wards),
                Math.max(0, n.minMembers() - members),
                Math.max(0, n.minWealth() - wealth));
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
