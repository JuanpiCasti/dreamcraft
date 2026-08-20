package dev.dreamcraft.protection.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class ClaimStats {
    private int totalBlocks;
    private final Map<String, Integer> materialCounts = new HashMap<>();
    private final Map<String, Integer> categoryCounts = new HashMap<>();

    public void increment(String materialKey, String categoryKey) {
        totalBlocks++;
        materialCounts.merge(materialKey, 1, Integer::sum);
        categoryCounts.merge(categoryKey, 1, Integer::sum);
    }

    public void decrement(String materialKey, String categoryKey) {
        if (totalBlocks > 0) {
            totalBlocks--;
        }
        decrementMap(materialCounts, materialKey);
        decrementMap(categoryCounts, categoryKey);
    }

    private void decrementMap(Map<String, Integer> map, String key) {
        Integer current = map.get(key);
        if (current == null) {
            return;
        }
        if (current <= 1) {
            map.remove(key);
            return;
        }
        map.put(key, current - 1);
    }

    public int totalBlocks() {
        return totalBlocks;
    }

    public Map<String, Integer> materialCounts() {
        return Collections.unmodifiableMap(materialCounts);
    }

    public Map<String, Integer> categoryCounts() {
        return Collections.unmodifiableMap(categoryCounts);
    }

    public void restore(Map<String, Integer> materials, Map<String, Integer> categories, int totalBlocks) {
        this.materialCounts.clear();
        this.materialCounts.putAll(materials);
        this.categoryCounts.clear();
        this.categoryCounts.putAll(categories);
        this.totalBlocks = totalBlocks;
    }
}
