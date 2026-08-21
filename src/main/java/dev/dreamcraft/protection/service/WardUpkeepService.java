package dev.dreamcraft.protection.service;

import dev.dreamcraft.protection.config.ProtectionConfig;
import dev.dreamcraft.protection.domain.model.Ward;
import dev.dreamcraft.protection.domain.model.WardPermission;
import dev.dreamcraft.protection.domain.service.WardService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gameplay service for Ward upkeep deposits.
 *
 * <p>Accepts a configurable set of materials ({@code ward.upkeep-materials}),
 * each with its own value in upkeep units per item. Deposits consume the items
 * and credit the Ward's balance via {@link WardService#depositUpkeep}.
 *
 * <p>Deposit authority: the owner (or an admin) always may; anyone else needs
 * the {@link WardPermission#PUBLIC_UPKEEP_DEPOSIT} permission on the Ward.
 */
public final class WardUpkeepService {

    /** Result of a deposit attempt. */
    public enum DepositResult {
        OK,
        /** Material is not in ward.upkeep-materials. */
        REJECTED_MATERIAL,
        /** Viewer lacks permission to deposit on this Ward. */
        REJECTED_PERMISSION
    }

    public record DepositReceipt(Material material, int amount, int unitsCredited, int newBalance) {}

    private final Map<Material, Integer> materialsByUnitValue;
    private final WardService wardService;

    public WardUpkeepService(ProtectionConfig config, WardService wardService) {
        this.materialsByUnitValue = new LinkedHashMap<>(config.wardUpkeepMaterials());
        this.wardService = wardService;
    }

    /** @return units credited per single item of the given material, or empty if not accepted. */
    public java.util.Optional<Integer> unitsPerItem(Material material) {
        return java.util.Optional.ofNullable(materialsByUnitValue.get(material));
    }

    /** @return true when the material is a valid upkeep resource. */
    public boolean isAccepted(Material material) {
        return materialsByUnitValue.containsKey(material);
    }

    /** @return accepted materials mapped to their per-item unit value (insertion order = config order). */
    public Map<Material, Integer> acceptedMaterials() {
        return new LinkedHashMap<>(materialsByUnitValue);
    }

    /**
     * Credits the whole stack of the given material to the Ward.
     * Does NOT touch any inventory — consumption is the caller's responsibility
     * (the menu provider consumes the cursor only after this returns OK).
     */
    public DepositReceipt deposit(Ward ward, Player depositor, Material material, int amount) {
        if (!isAccepted(material)) {
            throw new IllegalArgumentException("Material de upkeep no aceptado: " + material.name());
        }
        int units = materialsByUnitValue.get(material) * Math.max(1, amount);
        wardService.depositUpkeep(ward, units);
        return new DepositReceipt(material, amount, units, ward.upkeepBalance());
    }

    /** @return true when the viewer may deposit on this Ward. */
    public boolean canDeposit(Player viewer, Ward ward) {
        if (ward.ownerId().equals(viewer.getUniqueId())) return true;
        if (viewer.hasPermission("dreamcraft.ward.admin")) return true;
        return ward.hasPermission(WardPermission.PUBLIC_UPKEEP_DEPOSIT);
    }

    /**
     * Consumes up to {@code amount} items of the given material from the player's
     * inventory (storage + offhand). Used by command-based deposits.
     *
     * @return how many items were actually removed
     */
    public int consumeFromInventory(Player player, Material material, int amount) {
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        int remaining = amount;
        ItemStack[] storage = inv.getStorageContents();
        for (int i = 0; i < storage.length && remaining > 0; i++) {
            ItemStack item = storage[i];
            if (item == null || item.getType() != material) continue;
            int take = Math.min(item.getAmount(), remaining);
            remaining -= take;
            if (take >= item.getAmount()) {
                storage[i] = null;
            } else {
                item.setAmount(item.getAmount() - take);
            }
        }
        inv.setStorageContents(storage);

        ItemStack offhand = inv.getItemInOffHand();
        if (remaining > 0 && offhand.getType() == material) {
            int take = Math.min(offhand.getAmount(), remaining);
            remaining -= take;
            if (take >= offhand.getAmount()) {
                inv.setItemInOffHand(null);
            } else {
                offhand.setAmount(offhand.getAmount() - take);
            }
        }
        return amount - remaining;
    }

    /** Spanish display name for a material. */
    public String displayName(Material material) {
        return MaterialNames.forMaterial(material);
    }
}
