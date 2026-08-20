package dev.dreamcraft.protection.ui;

import dev.dreamcraft.protection.model.ProtectionClaim;
import dev.dreamcraft.protection.service.ClaimManager;
import dev.dreamcraft.protection.service.UpkeepCalculator;
import dev.dreamcraft.protection.service.UpkeepSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Vanilla inventory UI for the protection wardrobe.
 *
 * <p>Layout (27 slots, 3 rows of 9):
 * <pre>
 *  [ I ][ . ][ . ][ . ][ U ][ . ][ . ][ . ][ M ]
 *  [ . ][ . ][ . ][ . ][ D ][ . ][ . ][ . ][ . ]
 *  [ . ][ . ][ . ][ . ][ . ][ . ][ . ][ . ][ . ]
 * </pre>
 *  I = info/status (slot 0)
 *  U = upkeep status display (slot 4)
 *  M = members (slot 8)
 *  D = deposit slot — the ONLY writable slot (slot 13)
 *
 * <p>Every slot except D is non-extractable. Visual items return no ItemStack to the
 * cursor. The deposit slot accepts only the configured upkeep resource material;
 * on close the deposit is consumed and converted to upkeep units.
 *
 * <p>The title encodes the claim UUID so we can identify which claim a given open
 * inventory belongs to when handling click events.
 */
public final class ProtectionMenu implements Listener {

    /** Slots that are visual-only and must never yield items to the player. */
    private static final Set<Integer> LOCKED_SLOTS = Set.of(0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 10, 11, 12, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26);
    /** The only slot the player may place items into (upkeep deposit). */
    private static final int DEPOSIT_SLOT = 13;

    private static final String TITLE_PREFIX = "§5Armario";
    private static final int INVENTORY_SIZE = 27;

    private final UpkeepCalculator upkeepCalculator;
    private final ClaimManager claimManager;
    private final Material depositMaterial;
    private final int unitsPerItem;

    public ProtectionMenu(UpkeepCalculator upkeepCalculator, ClaimManager claimManager,
                          Material depositMaterial, int unitsPerItem) {
        this.upkeepCalculator = upkeepCalculator;
        this.claimManager = claimManager;
        this.depositMaterial = depositMaterial;
        this.unitsPerItem = unitsPerItem;
    }

    // ── Opening ──────────────────────────────────────────────────────────────

    public void open(Player player, ProtectionClaim claim) {
        // Encode claim UUID in title so click events can identify it
        Inventory inventory = Bukkit.createInventory(null, INVENTORY_SIZE,
                TITLE_PREFIX + " §8[" + claim.id() + "]");
        refresh(inventory, claim);
        player.openInventory(inventory);
    }

    /**
     * Fills the visual slots of an already-open inventory with fresh data.
     * The deposit slot is left intact (or cleared if no item has been placed yet).
     */
    public void refresh(Inventory inventory, ProtectionClaim claim) {
        UpkeepSnapshot snapshot = upkeepCalculator.calculate(claim);
        inventory.setItem(0, infoItem(claim, snapshot));
        inventory.setItem(4, upkeepItem(claim, snapshot));
        inventory.setItem(8, memberItem(claim));
        // Deposit slot has a hint item only when empty
        if (inventory.getItem(DEPOSIT_SLOT) == null) {
            inventory.setItem(DEPOSIT_SLOT, depositHintItem());
        }
        // Fill remaining slots with glass panes to prevent accidental clicks
        ItemStack filler = filler();
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            if (!LOCKED_SLOTS.contains(i) && i != DEPOSIT_SLOT) {
                continue;
            }
            if (LOCKED_SLOTS.contains(i) && inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }

    // ── Event handling ───────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!isProtectionMenu(event.getView().getTitle())) return;

        int rawSlot = event.getRawSlot();
        boolean clickedTopInventory = rawSlot >= 0 && rawSlot < INVENTORY_SIZE;

        // Collect-to-cursor (double-click) anywhere in the view — always block
        // so the client can't vacuum items out of the top inventory.
        if (event.getClick() == ClickType.DOUBLE_CLICK) {
            event.setCancelled(true);
            return;
        }

        // Shift-click from bottom inventory: would move items into the top inventory.
        // Block entirely so nothing auto-fills the locked slots.
        if (!clickedTopInventory && event.getClick().isShiftClick()) {
            event.setCancelled(true);
            return;
        }

        // Normal click in the bottom inventory (player moving items around in their
        // own hotbar/inventory while the menu is open) — allow vanilla behaviour.
        if (!clickedTopInventory) {
            return;
        }

        // From here: click is inside the top (menu) inventory.
        // Cancel by default to block extracting visual items.
        event.setCancelled(true);

        UUID claimId = extractClaimId(event.getView().getTitle());
        if (claimId == null) return;
        ProtectionClaim claim = claimManager.claimIndex().byId(claimId).orElse(null);
        if (claim == null) return;

        // Deposit slot: the only slot that processes an incoming deposit
        if (rawSlot == DEPOSIT_SLOT) {
            handleDeposit(event, player, claim);
        }
        // All other top-inventory slots: remain cancelled
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!isProtectionMenu(event.getView().getTitle())) return;

        // If any dragged slot touches the top inventory (not deposit), cancel entirely
        for (int slot : event.getRawSlots()) {
            if (slot < INVENTORY_SIZE && slot != DEPOSIT_SLOT) {
                event.setCancelled(true);
                return;
            }
        }
        // Drag purely into the deposit slot: treat same as a click deposit
        if (event.getRawSlots().contains(DEPOSIT_SLOT)) {
            event.setCancelled(true);
            // We can't easily convert a drag to a deposit here without complex item math;
            // instruct player to click-deposit instead.
            // The deposit hint guides them to use normal click.
        }
    }

    // ── Deposit logic ────────────────────────────────────────────────────────

    private void handleDeposit(InventoryClickEvent event, Player player, ProtectionClaim claim) {
        ItemStack cursor = event.getCursor();

        // Only accept items the player is actively carrying on their cursor.
        // The slot always contains a visual hint item — never treat the slot's
        // own content as a real deposit, to avoid crediting units without
        // consuming anything from the player's inventory.
        if (cursor == null || cursor.getType().isAir()) {
            // Nothing on cursor: player clicked the hint — show guidance
            player.sendMessage("§e[Protección] Toma §f" + depositMaterial.name().toLowerCase() +
                    " §een mano y luego haz click aquí para depositar.");
            return;
        }

        if (cursor.getType() != depositMaterial) {
            player.sendMessage("§c[Protección] Solo puedes depositar §f" + depositMaterial.name().toLowerCase() + "§c.");
            return;
        }

        // Cursor holds the correct resource — consume it and credit units
        int quantity = cursor.getAmount();
        int units = quantity * unitsPerItem;
        claimManager.depositUpkeep(claim, units);
        // Clear cursor — items are consumed (converted to units)
        event.getView().setCursor(null);
        player.sendMessage("§a[Protección] Depositaste " + quantity + " §f" +
                depositMaterial.name().toLowerCase() + " §a→ §f" + units + " §aunidades de mantenimiento.");
        refresh(event.getInventory(), claim);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private boolean isProtectionMenu(String title) {
        return title != null && title.startsWith(TITLE_PREFIX + " §8[");
    }

    private UUID extractClaimId(String title) {
        try {
            int start = title.indexOf('[') + 1;
            int end = title.indexOf(']');
            if (start <= 0 || end <= start) return null;
            return UUID.fromString(title.substring(start, end));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ── Display items ────────────────────────────────────────────────────────

    private ItemStack infoItem(ProtectionClaim claim, UpkeepSnapshot snapshot) {
        ItemStack item = new ItemStack(Material.SHIELD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§fEstado de Protección");
        List<String> lore = new ArrayList<>();
        lore.add("§7Estado: " + stateColor(claim) + claim.status().name());
        lore.add("§7Tier: §b" + claim.tier());
        lore.add("§7Radio: §f" + claim.radius() + " bloques");
        lore.add("§7Área: §f" + (claim.radius() * 2 + 1) + " x " + (claim.radius() * 2 + 1));
        lore.add("§7Bloques: §f" + claim.stats().totalBlocks());
        lore.add("§7Tiempo restante: §e" + formatDuration(snapshot.timeRemaining()));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack upkeepItem(ProtectionClaim claim, UpkeepSnapshot snapshot) {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§fMantenimiento");
        List<String> lore = new ArrayList<>();
        lore.add("§7Costo diario: §e" + snapshot.dailyCost() + " unidades");
        lore.add("§7Almacenado: §a" + snapshot.storedUnits() + " unidades");
        lore.add(" ");
        lore.add("§7Deposita §f" + depositMaterial.name().toLowerCase() + " §7en el slot central.");
        lore.add("§71 §f" + depositMaterial.name().toLowerCase() + " §7= §e" + unitsPerItem + " unidades");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack memberItem(ProtectionClaim claim) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§fMiembros");
        List<String> lore = new ArrayList<>();
        lore.add("§7Owner: §e" + claim.ownerUuid());
        lore.add("§7Miembros: §f" + claim.members().size());
        lore.add(" ");
        lore.add("§7Usa §f/protection members §7para gestionar.");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack depositHintItem() {
        // Use a neutral material (never the deposit material) so this visual hint
        // cannot be accidentally treated as a real deposit by the click handler.
        ItemStack item = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§aSlot de Depósito");
        meta.setLore(List.of(
                "§7Toma §f" + depositMaterial.name().toLowerCase() + " §7en mano",
                "§7y haz click aquí para depositar",
                "§71 ítem = §e" + unitsPerItem + " unidades"
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§7");
        item.setItemMeta(meta);
        return item;
    }

    private String stateColor(ProtectionClaim claim) {
        return switch (claim.status()) {
            case ACTIVE -> "§a";
            case WARNING -> "§e";
            case EXPIRING -> "§c";
            case NO_RESOURCES, UNPROTECTED, DECAYING -> "§4";
            case DESTROYED -> "§8";
        };
    }

    private String formatDuration(java.time.Duration d) {
        if (d.isNegative() || d.isZero()) return "§c0h";
        long hours = d.toHours();
        long minutes = d.toMinutesPart();
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }
}
