package dev.dreamcraft.protection.presentation;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marks inventories as DreamCraft menus and carries their id — replaces the
 * old hidden-suffix-in-title trick, so players only ever see a clean name.
 */
public final class MenuHolder implements InventoryHolder {

    private final String menuId;
    private Inventory inventory;

    public MenuHolder(String menuId) {
        this.menuId = menuId;
    }

    public String menuId() {
        return menuId;
    }

    void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
