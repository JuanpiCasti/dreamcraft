package dev.dreamcraft.protection.service;

import java.time.Duration;
import java.util.Map;

public record UpkeepSnapshot(
        int dailyCost,
        int storedUnits,
        Duration timeRemaining,
        Map<String, Integer> categoryRequirements
) {
}
