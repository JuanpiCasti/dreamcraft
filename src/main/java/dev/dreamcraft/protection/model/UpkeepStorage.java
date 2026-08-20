package dev.dreamcraft.protection.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class UpkeepStorage {
    private final Map<String, Integer> resources = new HashMap<>();

    public void deposit(String key, int amount) {
        if (amount <= 0) {
            return;
        }
        resources.merge(key, amount, Integer::sum);
    }

    public boolean withdraw(String key, int amount) {
        int current = resources.getOrDefault(key, 0);
        if (amount <= 0 || current < amount) {
            return false;
        }
        if (current == amount) {
            resources.remove(key);
        } else {
            resources.put(key, current - amount);
        }
        return true;
    }

    public int get(String key) {
        return resources.getOrDefault(key, 0);
    }

    public Map<String, Integer> snapshot() {
        return Collections.unmodifiableMap(resources);
    }
}
