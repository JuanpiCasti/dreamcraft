package dev.dreamcraft.protection.persistence;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * YAML-backed storage for City treasury vaults.
 *
 * <p>Each city has one persistent chest inventory ({@code treasuries.yml}).
 * Items placed inside physically stay there — the governor can withdraw them
 * at any time — and their configured unit value feeds the city's dynamic
 * wealth score.
 */
public final class CityTreasuryStore {

    public static final int VAULT_SIZE = 27;

    private final File file;
    /** Unit value per material (same table as ward upkeep deposits). */
    private final Map<Material, Integer> unitValues;
    private final Map<UUID, ItemStack[]> cache = new ConcurrentHashMap<>();

    public CityTreasuryStore(File file, Map<Material, Integer> unitValues) {
        this.file = file;
        this.unitValues = unitValues;
    }

    // ── Load / flush ──────────────────────────────────────────────────────────

    public void loadAll() {
        cache.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("treasuries");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            try {
                UUID cityId = UUID.fromString(key);
                List<?> raw = root.getList(key + ".items");
                ItemStack[] items = new ItemStack[VAULT_SIZE];
                if (raw != null) {
                    for (int i = 0; i < Math.min(raw.size(), VAULT_SIZE); i++) {
                        Object o = raw.get(i);
                        if (o instanceof ItemStack stack && !stack.getType().isAir()) {
                            items[i] = stack;
                        }
                    }
                }
                cache.put(cityId, items);
            } catch (IllegalArgumentException e) {
                System.err.println("[DreamCraft] Invalid treasury id: " + key);
            }
        }
    }

    public void flush() throws IOException {
        file.getParentFile().mkdirs();
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("treasuries");
        for (Map.Entry<UUID, ItemStack[]> entry : cache.entrySet()) {
            root.set(entry.getKey().toString(), Map.of("items", Arrays.asList(entry.getValue())));
        }
        yaml.save(file);
    }

    // ── Access ────────────────────────────────────────────────────────────────

    /** @return a defensive copy of the stored vault contents (always length {@value #VAULT_SIZE}). */
    public ItemStack[] get(UUID cityId) {
        ItemStack[] stored = cache.get(cityId);
        ItemStack[] copy = new ItemStack[VAULT_SIZE];
        if (stored != null) {
            System.arraycopy(stored, 0, copy, 0, VAULT_SIZE);
        }
        return copy;
    }

    public void set(UUID cityId, ItemStack[] items) {
        ItemStack[] copy = new ItemStack[VAULT_SIZE];
        if (items != null) {
            System.arraycopy(items, 0, copy, 0, Math.min(items.length, VAULT_SIZE));
        }
        cache.put(cityId, copy);
    }

    public void delete(UUID cityId) {
        cache.remove(cityId);
    }

    /**
     * @return total wealth units of the given stacks: only materials present in
     *         the upkeep unit-value table count; anything else is worth 0.
     */
    public int computeValue(ItemStack[] items) {
        if (items == null) return 0;
        int total = 0;
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) continue;
            Integer units = unitValues.get(item.getType());
            if (units != null) total += units * item.getAmount();
        }
        return total;
    }
}
