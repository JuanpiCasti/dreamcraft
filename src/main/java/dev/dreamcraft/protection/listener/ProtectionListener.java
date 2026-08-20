package dev.dreamcraft.protection.listener;

import dev.dreamcraft.protection.model.ProtectionAction;
import dev.dreamcraft.protection.model.ProtectionCheckResult;
import dev.dreamcraft.protection.model.ProtectionClaim;
import dev.dreamcraft.protection.service.ClaimManager;
import dev.dreamcraft.protection.service.ProtectionChecker;
import dev.dreamcraft.protection.service.UpkeepManager;
import dev.dreamcraft.protection.ui.ProtectionMenu;
import dev.dreamcraft.protection.ui.WardrobeItems;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Optional;

/**
 * Core event listener for per-player block and interaction protection.
 *
 * <p>Behaviour contract:
 * <ul>
 *   <li>Outside any claim → vanilla behaviour (events pass through).
 *   <li>Inside a claim + player is authorised → vanilla behaviour.
 *   <li>Inside a claim + player is NOT authorised → event cancelled, feedback message.
 * </ul>
 *
 * <p>Indirect modification (fire spread, fluid flow, entity damage to blocks) is
 * handled here for the simple player-caused cases. Physics events (explosions, pistons,
 * redstone, hoppers) are delegated to their own listeners.
 */
public final class ProtectionListener implements Listener {
    private final WardrobeItems wardrobeItems;
    private final ClaimManager claimManager;
    private final ProtectionChecker protectionChecker;
    private final ProtectionMenu protectionMenu;
    private final UpkeepManager upkeepManager;

    public ProtectionListener(WardrobeItems wardrobeItems, ClaimManager claimManager,
                               ProtectionChecker protectionChecker, ProtectionMenu protectionMenu,
                               UpkeepManager upkeepManager) {
        this.wardrobeItems = wardrobeItems;
        this.claimManager = claimManager;
        this.protectionChecker = protectionChecker;
        this.protectionMenu = protectionMenu;
        this.upkeepManager = upkeepManager;
    }

    // ── Block place ───────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();

        if (wardrobeItems.isWardrobeItem(event.getItemInHand())) {
            // "basic" tier for new claims; config determines its radius/maxMembers
            Optional<ProtectionClaim> created = claimManager.createClaim(player, block, "basic");
            if (created.isEmpty()) {
                event.setCancelled(true);
                player.sendMessage("§c[Protección] No se puede crear el claim aquí (área ocupada).");
                return;
            }
            // Persist immediately so state survives a crash right after placement
            try {
                claimManager.save();
            } catch (Exception ignored) { /* non-fatal */ }
            player.sendMessage("§a[Protección] Claim creado con tier §b" + created.get().tier() +
                    "§a. Abre el armario para depositar recursos.");
            return;
        }

        ProtectionCheckResult result = protectionChecker.check(
                player, block.getWorld().getName(), block.getX(), block.getZ(), ProtectionAction.BUILD);
        if (result == ProtectionCheckResult.PROTECTED_DENIED) {
            event.setCancelled(true);
            player.sendMessage("§c[Protección] No puedes construir en esta área.");
            return;
        }
        if (result == ProtectionCheckResult.PROTECTED_ALLOWED) {
            claimManager.findByLocation(block.getLocation()).ifPresent(claim -> {
                claimManager.trackPlace(claim, block.getType());
                upkeepManager.recalculateState(claim);
            });
        }
    }

    // ── Block break ───────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        // Special case: breaking the wardrobe block removes the claim (owner only)
        Optional<ProtectionClaim> wardrobeClaim = claimManager.findByWardrobe(block);
        if (wardrobeClaim.isPresent()) {
            ProtectionClaim claim = wardrobeClaim.get();
            if (!claim.ownerUuid().equals(player.getUniqueId())) {
                event.setCancelled(true);
                player.sendMessage("§c[Protección] Solo el owner puede romper el armario.");
                return;
            }
            claimManager.removeClaim(claim);
            try {
                claimManager.save();
            } catch (Exception ignored) { /* non-fatal */ }
            player.sendMessage("§a[Protección] Claim eliminado. Área liberada.");
            return;
        }

        ProtectionCheckResult result = protectionChecker.check(
                player, block.getWorld().getName(), block.getX(), block.getZ(), ProtectionAction.BREAK);
        if (result == ProtectionCheckResult.PROTECTED_DENIED) {
            event.setCancelled(true);
            player.sendMessage("§c[Protección] No puedes romper bloques en esta área.");
            return;
        }
        if (result == ProtectionCheckResult.PROTECTED_ALLOWED) {
            claimManager.findByLocation(block.getLocation()).ifPresent(claim -> {
                claimManager.trackBreak(claim, block.getType());
                upkeepManager.recalculateState(claim);
            });
        }
    }

    // ── Player interact ───────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only handle main-hand right-clicks on blocks
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
            return;
        }
        Block block = event.getClickedBlock();

        // Wardrobe block → open menu
        Optional<ProtectionClaim> wardrobeClaim = claimManager.findByWardrobe(block);
        if (wardrobeClaim.isPresent()) {
            event.setCancelled(true);
            ProtectionClaim claim = wardrobeClaim.get();
            if (!protectionChecker.isAllowed(event.getPlayer().getUniqueId(), claim, ProtectionAction.INTERACT)) {
                event.getPlayer().sendMessage("§c[Protección] No tienes acceso a este armario.");
                return;
            }
            protectionMenu.open(event.getPlayer(), claim);
            return;
        }

        ProtectionCheckResult result = protectionChecker.check(
                event.getPlayer(), block.getWorld().getName(), block.getX(), block.getZ(),
                ProtectionAction.INTERACT);
        if (result == ProtectionCheckResult.PROTECTED_DENIED) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c[Protección] No tienes acceso a esta área.");
        }
        if (result == ProtectionCheckResult.PROTECTED_ALLOWED) {
            claimManager.findByLocation(block.getLocation()).ifPresent(claimManager::recordActivity);
        }
    }

    // ── Bucket interactions ───────────────────────────────────────────────────

    /** Prevents placing liquid (lava/water buckets) inside a foreign claim. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Block block = event.getBlock();
        ProtectionCheckResult result = protectionChecker.check(
                event.getPlayer(), block.getWorld().getName(), block.getX(), block.getZ(),
                ProtectionAction.BUILD);
        if (result == ProtectionCheckResult.PROTECTED_DENIED) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c[Protección] No puedes colocar líquidos en esta área.");
        }
    }

    /** Prevents filling buckets from inside a foreign claim. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Block block = event.getBlock();
        ProtectionCheckResult result = protectionChecker.check(
                event.getPlayer(), block.getWorld().getName(), block.getX(), block.getZ(),
                ProtectionAction.BREAK);
        if (result == ProtectionCheckResult.PROTECTED_DENIED) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c[Protección] No puedes tomar bloques de esta área.");
        }
    }

    // ── Fire spread ───────────────────────────────────────────────────────────

    /** Prevents fire from spreading into or within a claimed area. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        // BlockSpreadEvent covers fire spreading to adjacent blocks
        Block dest = event.getBlock();
        ProtectionCheckResult result = protectionChecker.checkNoPlayer(
                dest.getWorld().getName(), dest.getX(), dest.getZ(), ProtectionAction.BUILD);
        if (result == ProtectionCheckResult.PROTECTED_DENIED) {
            event.setCancelled(true);
        }
    }

    /** Prevents blocks inside a claim from burning. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        Block block = event.getBlock();
        ProtectionCheckResult result = protectionChecker.checkNoPlayer(
                block.getWorld().getName(), block.getX(), block.getZ(), ProtectionAction.BUILD);
        if (result == ProtectionCheckResult.PROTECTED_DENIED) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevents fire ignition inside a claim by non-owners.
     * Player-caused ignition (flint and steel) is also covered by onPlayerInteract.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        Block block = event.getBlock();
        if (event.getPlayer() != null) {
            ProtectionCheckResult result = protectionChecker.check(
                    event.getPlayer(), block.getWorld().getName(), block.getX(), block.getZ(),
                    ProtectionAction.BUILD);
            if (result == ProtectionCheckResult.PROTECTED_DENIED) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§c[Protección] No puedes encender fuego en esta área.");
            }
        } else {
            // Non-player ignition (lava, lightning, etc.)
            ProtectionCheckResult result = protectionChecker.checkNoPlayer(
                    block.getWorld().getName(), block.getX(), block.getZ(), ProtectionAction.BUILD);
            if (result == ProtectionCheckResult.PROTECTED_DENIED) {
                event.setCancelled(true);
            }
        }
    }

    // ── Fluid flow ────────────────────────────────────────────────────────────

    /**
     * Prevents fluid from flowing into a protected area from outside.
     * Only cancels when the destination is protected; source-protected flow
     * within the same claim is allowed (members' own water/lava).
     *
     * <p>Decision: we only protect the destination. Fluid that starts inside a
     * protected claim and flows outside is vanilla behaviour — the destination
     * outside the claim is unclaimed and the member is allowed.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        Block dest = event.getToBlock();
        ProtectionCheckResult result = protectionChecker.checkNoPlayer(
                dest.getWorld().getName(), dest.getX(), dest.getZ(), ProtectionAction.BUILD);
        if (result == ProtectionCheckResult.PROTECTED_DENIED) {
            event.setCancelled(true);
        }
    }

    // ── Entity damage to blocks ───────────────────────────────────────────────

    /**
     * Prevents players from using projectiles or melee to destroy blocks inside a claim.
     * Only handles EntityDamageByEntity where the damager is a player;
     * mob-related block damage is handled in PhysicsProtectionListener (explosions).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        // Only relevant when the damaged entity is an item frame, painting, or armorstand
        // (block entities that can be "broken" by players). Regular block breaking goes
        // through BlockBreakEvent. We protect decoration entities here.
        org.bukkit.entity.Entity entity = event.getEntity();
        if (!(entity instanceof org.bukkit.entity.ItemFrame)
                && !(entity instanceof org.bukkit.entity.Painting)
                && !(entity instanceof org.bukkit.entity.ArmorStand)) {
            return;
        }
        ProtectionCheckResult result = protectionChecker.check(
                player, entity.getWorld().getName(),
                entity.getLocation().getBlockX(), entity.getLocation().getBlockZ(),
                ProtectionAction.BREAK);
        if (result == ProtectionCheckResult.PROTECTED_DENIED) {
            event.setCancelled(true);
            player.sendMessage("§c[Protección] No puedes destruir entidades en esta área.");
        }
    }
}
