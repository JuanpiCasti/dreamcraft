package dev.dreamcraft.protection.ui;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * InventoryHolder identifying a Ward upkeep vault.
 *
 * <p>The vault is a plain chest inventory where the depositor freely places
 * items; nothing is consumed while it is open. When the inventory closes,
 * {@code dev.dreamcraft.protection.listener.WardUpkeepVaultListener} converts
 * accepted materials into upkeep units and hands back everything else.
 *
 * <p>Using a custom holder (instead of title matching) keeps this inventory
 * invisible to {@code VanillaMenuProvider}'s click interception, so vanilla
 * item movement works inside it.
 */
public final class WardUpkeepVaultHolder implements InventoryHolder {

    private final UUID wardId;
    private Inventory inventory;

    public WardUpkeepVaultHolder(UUID wardId) {
        this.wardId = wardId;
    }

    /** Creates the holder together with its backing 27-slot chest inventory. */
    public static WardUpkeepVaultHolder create(UUID wardId, net.kyori.adventure.text.Component title) {
        WardUpkeepVaultHolder holder = new WardUpkeepVaultHolder(wardId);
        holder.bind(Bukkit.createInventory(holder, 27, title));
        return holder;
    }

    public UUID wardId() {
        return wardId;
    }

    private void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
