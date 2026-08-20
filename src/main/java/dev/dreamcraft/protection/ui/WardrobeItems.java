package dev.dreamcraft.protection.ui;

import dev.dreamcraft.protection.config.ProtectionConfig;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class WardrobeItems {
    private final NamespacedKey key;
    private final ProtectionConfig config;

    public WardrobeItems(JavaPlugin plugin, ProtectionConfig config) {
        this.key = new NamespacedKey(plugin, "protection-wardrobe");
        this.config = config;
    }

    public ItemStack createWardrobeItem() {
        Material material = config.wardrobeMaterial() == null ? Material.LODESTONE : config.wardrobeMaterial();
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("Armario de Base");
        meta.setLore(java.util.List.of(
                "Colocalo para crear una proteccion",
                "Funciona sin Resource Pack",
                "ID: " + config.resourceItemId()
        ));
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, config.resourceItemId());
        if (config.customModelData() > 0) {
            meta.setCustomModelData(config.customModelData());
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isWardrobeItem(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        String value = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return config.resourceItemId().equals(value);
    }
}
