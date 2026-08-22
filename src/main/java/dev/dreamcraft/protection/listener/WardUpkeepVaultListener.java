package dev.dreamcraft.protection.listener;

import dev.dreamcraft.protection.command.CommandMessages;

import dev.dreamcraft.protection.domain.model.Ward;
import dev.dreamcraft.protection.domain.service.WardService;
import dev.dreamcraft.protection.service.WardUpkeepService;
import dev.dreamcraft.protection.ui.WardUpkeepVaultHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Settles a Ward upkeep vault when its inventory closes.
 *
 * <p>While the vault is open the player can arrange items freely. On close:
 * <ul>
 *   <li>Accepted materials ({@code ward.upkeep-materials}) are consumed and
 *       converted into upkeep units credited to the Ward.</li>
 *   <li>Everything else is returned to the player's inventory (dropped at
 *       their feet when full) — nothing is ever destroyed.</li>
 * </ul>
 */
public final class WardUpkeepVaultListener implements Listener {

    private final WardService wardService;
    private final WardUpkeepService upkeepService;
    private final Runnable saveAction;

    public WardUpkeepVaultListener(WardService wardService,
                                   WardUpkeepService upkeepService,
                                   Runnable saveAction) {
        this.wardService = wardService;
        this.upkeepService = upkeepService;
        this.saveAction = saveAction;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVaultClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof WardUpkeepVaultHolder holder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        Ward ward = wardService.findById(holder.wardId()).orElse(null);
        ItemStack[] contents = event.getInventory().getContents();
        event.getInventory().clear();

        // Ward vanished while the vault was open — hand everything back untouched
        if (ward == null) {
            giveBack(player, List.of(contents));
            return;
        }

        int totalUnits = 0;
        Map<Material, Integer> consumed = new LinkedHashMap<>();
        List<ItemStack> rejected = new ArrayList<>();

        for (ItemStack item : contents) {
            if (item == null || item.getType().isAir()) continue;
            int unitsPer = upkeepService.unitsPerItem(item.getType()).orElse(0);
            if (unitsPer > 0) {
                consumed.merge(item.getType(), item.getAmount(), Integer::sum);
                totalUnits += unitsPer * item.getAmount();
            } else {
                rejected.add(item);
            }
        }

        if (totalUnits > 0) {
            wardService.depositUpkeep(ward, totalUnits);
            saveAction.run();
        }
        giveBack(player, rejected);

        player.sendMessage(CommandMessages.WARD_PREFIX
                .append(Component.text("Bóveda cerrada — ", NamedTextColor.GRAY)));
        if (!consumed.isEmpty()) {
            StringBuilder detail = new StringBuilder();
            consumed.forEach((mat, amount) -> {
                if (detail.length() > 0) detail.append("§7, ");
                detail.append("§f").append(amount).append("x ")
                        .append(upkeepService.displayName(mat));
            });
            player.sendMessage(CommandMessages.WARD_PREFIX
                    .append(Component.text("Depositado: " + detail, NamedTextColor.WHITE))
                    .append(Component.text(" → +" + totalUnits + " unidades", NamedTextColor.GREEN))
                    .append(Component.text(" (Balance: " + ward.upkeepBalance() + ")", NamedTextColor.GRAY)));
        } else {
            player.sendMessage(CommandMessages.prefixed("ward", "No dejaste ítems válidos de upkeep.",
                    NamedTextColor.YELLOW));
        }
        if (!rejected.isEmpty()) {
            player.sendMessage(CommandMessages.prefixed("ward", "Ítems no válidos devueltos a tu inventario.",
                    NamedTextColor.YELLOW));
        }
    }

    /** Returns items to the player's inventory, dropping any overflow at their feet. */
    private void giveBack(Player player, List<ItemStack> items) {
        if (items.isEmpty()) return;
        var leftover = player.getInventory().addItem(items.toArray(new ItemStack[0]));
        leftover.values().forEach(stack ->
                player.getWorld().dropItemNaturally(player.getLocation(), stack));
    }
}
