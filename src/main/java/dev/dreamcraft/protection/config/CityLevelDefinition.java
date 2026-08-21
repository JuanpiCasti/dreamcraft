package dev.dreamcraft.protection.config;

/**
 * Configured City level threshold.
 *
 * <p>City levels are computed, never purchased: a city holds the highest level
 * whose three minimums are all satisfied.
 *
 * @param key         stable level key (e.g. "aldea")
 * @param displayName friendly name shown to players
 * @param minWards    annexed wards required
 * @param minMembers  inhabitants (members) required
 * @param minWealth   wealth required (sum of annexed wards' baseScore)
 */
public record CityLevelDefinition(
        String key,
        String displayName,
        int minWards,
        int minMembers,
        int minWealth
) {
    /** The implicit fallback level when nothing else qualifies. */
    public boolean isStarter() {
        return minWards <= 0 && minMembers <= 0 && minWealth <= 0;
    }
}
