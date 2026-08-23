package dev.dreamcraft.protection.listener;

import dev.dreamcraft.protection.command.CommandMessages;
import dev.dreamcraft.protection.config.CommandNames;
import dev.dreamcraft.protection.domain.model.OwnerType;
import dev.dreamcraft.protection.domain.model.Ward;
import dev.dreamcraft.protection.domain.service.WardService;
import dev.dreamcraft.protection.integration.worldguard.WorldGuardAdapter;
import dev.dreamcraft.protection.ui.WardItems;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Listener for the special Ward block.
 *
 * <p>Behaviour contract:
 * <ul>
 *   <li>Placing the Ward item founds a Ward whose protection radius is centered
 *       on the placed block.</li>
 *   <li>Breaking the center block dissolves the Ward through the shared
 *       {@link dev.dreamcraft.protection.service.WardDissolutionService}
 *       (owner gets the tagged core back — only when the physical block was
 *       actually removed; admin teardown returns nothing).
 *       The break event is cancelled so the vanilla generic drop never appears.</li>
 *   <li>Right-clicking the center block opens the Ward menu. Physical access
 *       rule: the viewer is the OWNER (always, default users included) or an
 *       admin ({@code dreamcraft.ward.admin}) or a VIP
 *       ({@code dreamcraft.ward.menu}). The remote {@code /ward menu} keeps its
 *       own VIP/admin-only gate — see {@code WardCommand.canOpenWardMenu}.</li>
 * </ul>
 */
public final class WardItemListener implements Listener {

    private static final String ADMIN_PERM = "dreamcraft.ward.admin";
    private static final String MENU_PERM = "dreamcraft.ward.menu";

    private final WardItems wardItems;
    private final WardService wardService;
    private final WorldGuardAdapter worldGuardAdapter;
    private final BiConsumer<Player, Ward> menuOpener;
    private final Runnable saveAction;
    /** Single dissolution contract — same teardown as /ward delete and the menu. */
    private final dev.dreamcraft.protection.service.WardDissolutionService dissolutionService;
    /**
     * Backfill hook run right after a new Ward is founded: counts pre-existing
     * below-tier gated blocks into the surcharge counter (no-op by default).
     */
    private final java.util.function.Consumer<Ward> foundingSeeder;

    public WardItemListener(WardItems wardItems,
                            WardService wardService,
                            WorldGuardAdapter worldGuardAdapter,
                            BiConsumer<Player, Ward> menuOpener,
                            Runnable saveAction) {
        this(wardItems, wardService, worldGuardAdapter, menuOpener, saveAction, null);
    }

    public WardItemListener(WardItems wardItems,
                            WardService wardService,
                            WorldGuardAdapter worldGuardAdapter,
                            BiConsumer<Player, Ward> menuOpener,
                            Runnable saveAction,
                            dev.dreamcraft.protection.service.WardDissolutionService dissolutionService) {
        this(wardItems, wardService, worldGuardAdapter, menuOpener, saveAction,
                dissolutionService, null);
    }

    public WardItemListener(WardItems wardItems,
                            WardService wardService,
                            WorldGuardAdapter worldGuardAdapter,
                            BiConsumer<Player, Ward> menuOpener,
                            Runnable saveAction,
                            dev.dreamcraft.protection.service.WardDissolutionService dissolutionService,
                            java.util.function.Consumer<Ward> foundingSeeder) {
        this.wardItems = wardItems;
        this.wardService = wardService;
        this.worldGuardAdapter = worldGuardAdapter;
        this.menuOpener = menuOpener;
        this.saveAction = saveAction;
        this.dissolutionService = dissolutionService;
        this.foundingSeeder = foundingSeeder != null ? foundingSeeder : ward -> { };
    }

    // ── Block place ───────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!wardItems.isWardItem(event.getItemInHand())) return;

        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        String world = block.getWorld().getName();

        // No founding a Ward inside another Ward's radius
        Optional<Ward> occupied = wardService.findAtLocation(world, block.getX(), block.getZ());
        if (occupied.isPresent()) {
            event.setCancelled(true);
            if (occupied.get().ownerId().equals(player.getUniqueId())) {
                player.sendMessage(CommandMessages.prefixed("ward",
                        "Tu propio Núcleo ya cubre esta área §7(/" + CommandNames.root("ward")
                                + " delete lo retira; click derecho en él abre su menú).",
                        NamedTextColor.RED));
            } else {
                player.sendMessage(CommandMessages.prefixed("ward",
                        "Esta área ya pertenece a otro Núcleo.", NamedTextColor.RED));
            }
            return;
        }

        Ward ward = wardService.createWard(
                player.getUniqueId(),
                OwnerType.PLAYER,
                null,
                world,
                block.getX(), block.getY(), block.getZ(),
                null // unique friendly name generated by the domain service
        );
        String regionId = worldGuardAdapter.createRegion(ward, world, -64, 320);
        if (regionId != null) {
            wardService.assignWorldGuardRegion(ward, regionId);
        }
        saveAction.run();
        // Backfill: gated blocks that predate this core enter the surcharge
        // counter right away (see WardBlockGateListener#seedExistingBelowTierBlocks).
        foundingSeeder.accept(ward);
        player.sendMessage(CommandMessages.prefixed("ward",
                "Núcleo §f" + ward.name() + "§a despertado (fase " + ward.tier()
                        + ", radio " + ward.radius() + ").",
                NamedTextColor.GREEN));
    }

    // ── Block break ───────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Optional<Ward> center = wardService.findByCenter(
                block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        if (center.isEmpty()) return;

        Player player = event.getPlayer();
        Ward ward = center.get();
        boolean owner = ward.ownerId().equals(player.getUniqueId());
        if (!owner && !player.hasPermission("dreamcraft.ward.admin")) {
            event.setCancelled(true);
            player.sendMessage("§c[Sincronía] Solo el owner puede retirar su Núcleo de Sincronía.");
            return;
        }
        // Cancel first: the vanilla material drop is replaced by the tagged
        // founder item the dissolution service hands back to owners.
        event.setCancelled(true);
        var result = dissolutionService != null
                ? dissolutionService.dissolve(ward, player, owner)
                : legacyDissolve(ward);
        if (dissolutionService == null) saveAction.run();
        player.sendMessage(CommandMessages.prefixed("ward",
                "Núcleo §f" + ward.name() + "§a desactivado. Área liberada."
                        + (result.refunded() ? " §7(Tu Núcleo volvió a tu inventario.)"
                                : owner && !result.coreBlockRemoved()
                                        ? " §7(sin bloque físico: nada devuelto)" : ""),
                NamedTextColor.GREEN));
    }

    /** Pre-contract fallback (service not wired): region + repository teardown only. */
    private dev.dreamcraft.protection.service.WardDissolutionService.Result legacyDissolve(Ward ward) {
        worldGuardAdapter.removeRegion(ward);
        wardService.delete(ward);
        return new dev.dreamcraft.protection.service.WardDissolutionService.Result(false, false);
    }

    // ── Right-click ───────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) return;

        Block block = event.getClickedBlock();
        Optional<Ward> center = wardService.findByCenter(
                block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        if (center.isEmpty()) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        Ward ward = center.get();
        // Physical access: the OWNER always opens their own core; admins and
        // VIPs may open anyone's. Remote /ward menu keeps its VIP/admin gate.
        boolean allowed = ward.ownerId().equals(player.getUniqueId())
                || player.hasPermission(ADMIN_PERM)
                || player.hasPermission(MENU_PERM);
        if (!allowed) {
            player.sendMessage(CommandMessages.prefixed("ward",
                    "El menú de este Núcleo está reservado a su dueño, admins y VIPs.",
                    NamedTextColor.RED));
            return;
        }
        menuOpener.accept(player, ward);
    }
}
