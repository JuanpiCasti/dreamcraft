package dev.dreamcraft.protection.ui;

import dev.dreamcraft.protection.config.ProtectionConfig;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Factory/identifier for the special block item that creates a Ward when placed.
 * The placed block becomes the center of the Ward's protection radius.
 */
public final class WardItems {
    private final NamespacedKey key;
    private final ProtectionConfig config;

    public WardItems(JavaPlugin plugin, ProtectionConfig config) {
        this.key = new NamespacedKey(plugin, "ward-beacon");
        this.config = config;
    }

    public ItemStack createWardItem() {
        Material material = config.wardMaterial() == null ? Material.BEACON : config.wardMaterial();
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("Baliza de Ward");
        meta.setLore(java.util.List.of(
                "Colocala para fundar un Ward",
                "La protección se extiende en radio alrededor de este bloque",
                "ID: " + config.wardItemId()
        ));
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, config.wardItemId());
        if (config.wardCustomModelData() > 0) {
            meta.setCustomModelData(config.wardCustomModelData());
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isWardItem(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        String value = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return config.wardItemId() != null && config.wardItemId().equals(value);
    }
}
