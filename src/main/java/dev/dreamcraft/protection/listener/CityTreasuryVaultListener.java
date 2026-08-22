package dev.dreamcraft.protection.listener;

import dev.dreamcraft.protection.command.CommandMessages;

import dev.dreamcraft.protection.persistence.CityTreasuryStore;
import dev.dreamcraft.protection.ui.CityTreasuryVaultHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

/**
 * Persists a City treasury vault when its inventory closes and reports the
 * vault's current wealth contribution.
 */
public final class CityTreasuryVaultListener implements Listener {

    private final CityTreasuryStore store;
    private final Runnable saveAction;

    public CityTreasuryVaultListener(CityTreasuryStore store, Runnable saveAction) {
        this.store = store;
        this.saveAction = saveAction;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVaultClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof CityTreasuryVaultHolder holder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        store.set(holder.cityId(), event.getInventory().getContents());
        try {
            store.flush();
        } catch (Exception e) {
            player.sendMessage(CommandMessages.prefixed("city", "No se pudo guardar el tesoro: "
                    + e.getMessage(), NamedTextColor.RED));
            return;
        }
        int value = store.computeValue(event.getInventory().getContents());
        player.sendMessage(CommandMessages.CITY_PREFIX
                .append(Component.text("Tesoro guardado — valor: ", NamedTextColor.GRAY))
                .append(Component.text(value + " unidades de riqueza", NamedTextColor.YELLOW)));
        saveAction.run();
    }
}
