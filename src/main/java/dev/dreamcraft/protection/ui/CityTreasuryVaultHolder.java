package dev.dreamcraft.protection.ui;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * InventoryHolder identifying a City treasury vault.
 *
 * <p>Unlike the Ward upkeep vault, items placed here physically persist —
 * the governor (or council) can come back and withdraw them. The vault's
 * content value feeds the city's dynamic wealth score.
 */
public final class CityTreasuryVaultHolder implements InventoryHolder {

    private final UUID cityId;
    private Inventory inventory;

    private CityTreasuryVaultHolder(UUID cityId) {
        this.cityId = cityId;
    }

    /** Creates the holder together with its backing 27-slot chest inventory. */
    public static CityTreasuryVaultHolder create(UUID cityId, net.kyori.adventure.text.Component title) {
        return create(cityId, title, null);
    }

    /**
     * Creates the vault seeded with the persisted contents — without this,
     * every reopen would show an empty chest even though the close listener
     * saved everything correctly.
     */
    public static CityTreasuryVaultHolder create(UUID cityId, net.kyori.adventure.text.Component title,
                                                 org.bukkit.inventory.ItemStack[] contents) {
        CityTreasuryVaultHolder holder = new CityTreasuryVaultHolder(cityId);
        holder.inventory = Bukkit.createInventory(holder, 27, title);
        if (contents != null && contents.length == 27) {
            holder.inventory.setContents(contents);
        }
        return holder;
    }

    public UUID cityId() {
        return cityId;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
