package dev.dreamcraft.protection.service;

import dev.dreamcraft.protection.config.ProtectionConfig;
import dev.dreamcraft.protection.model.ProtectionClaim;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public final class UpkeepCalculator {
    private final ProtectionConfig config;

    public UpkeepCalculator(ProtectionConfig config) {
        this.config = config;
    }

    public UpkeepSnapshot calculate(ProtectionClaim claim) {
        Map<String, Integer> requirements = new HashMap<>();
        int dailyCost = 0;
        for (Map.Entry<String, Integer> entry : claim.stats().categoryCounts().entrySet()) {
            int cost = config.categoryBaseCosts().getOrDefault(entry.getKey(), 1) * entry.getValue();
            requirements.put(entry.getKey(), cost);
            dailyCost += cost;
        }
        dailyCost = Math.max(1, dailyCost);
        int storedUnits = claim.upkeepStorage().get("maintenance");
        long intervalSeconds = Math.max(1, config.upkeepInterval().toSeconds());
        long remainingSeconds = (long) storedUnits * intervalSeconds / dailyCost;
        return new UpkeepSnapshot(dailyCost, storedUnits, Duration.ofSeconds(remainingSeconds), requirements);
    }
}
