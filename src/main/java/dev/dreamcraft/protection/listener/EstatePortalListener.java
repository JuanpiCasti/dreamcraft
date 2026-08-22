package dev.dreamcraft.protection.listener;

import dev.dreamcraft.protection.command.CommandMessages;

import dev.dreamcraft.protection.config.CommandNames;

import dev.dreamcraft.protection.domain.model.Estate;
import dev.dreamcraft.protection.domain.service.EstateService;
import dev.dreamcraft.protection.integration.worldguard.WorldGuardAdapter;
import dev.dreamcraft.protection.service.EndInstanceService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.EndPortalFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gates Estate adventure areas and wires their portals to private instances.
 *
 * <p>Behavior by estate type:
 * <ul>
 *   <li><b>END</b> — inserting Eyes of Ender into frames inside the estate area,
 *       and stepping through the resulting portal, requires membership of that
 *       estate. Members are redirected to the estate's private End instance
 *       instead of the shared {@code world_the_end}.</li>
 *   <li><b>TRIAL_CHAMBER</b> — interacting with vaults and trial spawners inside
 *       the estate area requires membership.</li>
 * </ul>
 *
 * <p>Session lifecycle (enter/leave/relog) is delegated to {@link EndInstanceService}.
 */
public final class EstatePortalListener implements Listener {

    private final EstateService estateService;
    private final EndInstanceService instanceService;
    /** Optional: syncs newly created party estates to WorldGuard area regions. */
    private final WorldGuardAdapter worldGuardAdapter;
    /** playerId → last zone estate they were inside (absent = outside). */
    private final Map<UUID, UUID> lastZoneByPlayer = new ConcurrentHashMap<>();
    /**
     * Vertical stealth band: a location only counts as "inside the zone" when
     * it sits within anchorY - bandBelow … anchorY + bandAbove. Surface players
     * passing above a stronghold/chamber therefore never trigger discovery,
     * gating messages or automatic party creation.
     */
    private final int bandBelow;
    private final int bandAbove;

    public EstatePortalListener(EstateService estateService,
                                EndInstanceService instanceService,
                                WorldGuardAdapter worldGuardAdapter) {
        this(estateService, instanceService, worldGuardAdapter, 16, 48);
    }

    public EstatePortalListener(EstateService estateService,
                                EndInstanceService instanceService,
                                WorldGuardAdapter worldGuardAdapter,
                                int areaBandBelow,
                                int areaBandAbove) {
        this.estateService = estateService;
        this.instanceService = instanceService;
        this.worldGuardAdapter = worldGuardAdapter;
        this.bandBelow = Math.max(0, areaBandBelow);
        this.bandAbove = Math.max(4, areaBandAbove);
    }

    // ── Eye insertion + trial vault gating ────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        Material type = clicked.getType();
        boolean isFrame = type == Material.END_PORTAL_FRAME;
        boolean isTrialBlock = type == Material.VAULT || type == Material.TRIAL_SPAWNER;
        if (!isFrame && !isTrialBlock) return;

        Player player = event.getPlayer();
        List<Estate> zones = adventureAreasAt(clicked.getLocation()).stream()
                .filter(e -> withinVerticalBand(clicked.getLocation(), e))
                .toList();
        if (zones.isEmpty()) return;

        Estate zone = zones.get(0);
        if (zones.stream().noneMatch(e -> isMember(e, player.getUniqueId()))) {
            event.setCancelled(true);
            player.sendMessage(CommandMessages.ESTATE_PREFIX
                    .append(Component.text("Esta zona pertenece al estate ", NamedTextColor.RED))
                    .append(Component.text(zone.name(), NamedTextColor.AQUA))
                    .append(Component.text(". Creá tu grupo con ", NamedTextColor.RED))
                    .append(Component.text(CommandNames.cmd("estate", "discover " + zone.type().key()), NamedTextColor.YELLOW))
                    .append(Component.text(" o pedí una invitación.", NamedTextColor.RED)));
            return;
        }

        // Member inserted an eye — announce once the portal completes (next tick,
        // after vanilla processes the placement).
        if (isFrame && isHoldingEye(event.getItem())) {
            Location frameLoc = clicked.getLocation();
            UUID estateId = zone.id();
            Bukkit.getScheduler().runTaskLater(plugin(), () -> {
                if (countFilledFrames(frameLoc, 16) >= 12) {
                    broadcastToEstate(estateId,
                            CommandMessages.ESTATE_PREFIX
                                    .append(Component.text("El portal del End está abierto. ¡Adelante!",
                                            NamedTextColor.GREEN)));
                }
            }, 1L);
        }
    }

    // ── Portal redirection ────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (event.getCause() != org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.END_PORTAL) return;
        Player player = event.getPlayer();
        World fromWorld = event.getFrom().getWorld();
        if (fromWorld == null) return;

        // Case A: leaving the instance world through the exit portal → back to the estate area.
        if (instanceService.isInstanceWorldName(fromWorld.getName())) {
            Estate estate = instanceService.estateForWorldName(fromWorld.getName()).orElse(null);
            Location exit = estate != null
                    ? instanceService.exitLocationFor(estate)
                    : fallbackExit();
            event.setTo(exit);
            player.sendMessage(CommandMessages.prefixed("estate", "Volviste del End.", NamedTextColor.GREEN));
            return;
        }

        // Case B: entering through an END-type estate portal → private instance.
        // Several parties may share the zone; each member travels to their own
        // estate's world, preferring one that is already active (mid-fight).
        List<Estate> candidates = adventureAreasAt(event.getFrom()).stream()
                .filter(e -> e.type().usesEndInstance())
                .filter(e -> withinVerticalBand(event.getFrom(), e))
                .toList();
        if (candidates.isEmpty()) return;

        // Deterministic pick: resume own active fight > own party world already
        // loaded > any own party (world created fresh) > admin zone LAST — so a
        // shared area never routes members into the server-managed world by accident.
        UUID playerId = player.getUniqueId();
        List<Estate> mine = candidates.stream()
                .filter(e -> isMember(e, playerId))
                .toList();
        Estate estate = instanceService.estateOfPlayer(playerId)
                .filter(mine::contains)
                .orElseGet(() -> mine.stream()
                        .filter(e -> !e.persistent())
                        .filter(e -> Bukkit.getWorld(instanceService.worldNameFor(e)) != null)
                        .findFirst()
                        .orElseGet(() -> mine.stream()
                                .filter(e -> !e.persistent())
                                .findFirst()
                                .orElseGet(() -> mine.stream()
                                        .filter(Estate::persistent)
                                        .findFirst()
                                        .orElse(null))));

        if (estate == null) {
            event.setCancelled(true);
            player.sendMessage(CommandMessages.ESTATE_PREFIX
                    .append(Component.text("Solo los miembros de un estate de esta zona pueden cruzar. "
                            + "Creá el tuyo con ", NamedTextColor.RED))
                    .append(Component.text(CommandNames.cmd("estate", "discover end"), NamedTextColor.YELLOW))
                    .append(Component.text(".", NamedTextColor.RED)));
            return;
        }
        if (!instanceService.isEnabled()) {
            return; // instancing disabled → fall through to vanilla behavior
        }

        World instance = instanceService.getOrCreateWorld(estate);
        if (instance == null) {
            event.setCancelled(true);
            player.sendMessage(CommandMessages.prefixed("estate", "No se pudo abrir la instancia. Avisa a un admin.",
                    NamedTextColor.RED));
            return;
        }
        event.setTo(instanceService.entryLocation(instance));
        // Session registration happens next tick, once the player actually arrived.
        Bukkit.getScheduler().runTask(plugin(), () -> {
            Player arrived = Bukkit.getPlayer(player.getUniqueId());
            if (arrived != null && arrived.getWorld().getName().equals(instance.getName())) {
                instanceService.handleEnter(arrived, estate);
            }
        });
    }

    // ── Session lifecycle hooks ───────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        // Any way out of the instance world (exit portal, /spawn, death respawn…)
        if (instanceService.isInstanceWorldName(event.getFrom().getName())) {
            instanceService.handleLeave(event.getPlayer());
        }
        evaluateZoneEntry(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return; // same block — nothing to do
        }
        evaluateZoneEntry(event.getPlayer(), to);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        lastZoneByPlayer.remove(player.getUniqueId());
        if (instanceService.isInstanceWorldName(player.getWorld().getName())) {
            instanceService.handleLeave(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        instanceService.handleJoin(event.getPlayer());
        evaluateZoneEntry(event.getPlayer(), event.getPlayer().getLocation());
    }

    /**
     * Zone-entry discovery: the first time a player steps into an END /
     * TRIAL_CHAMBER zone without belonging to any of its estates, an
     * on-screen prompt tells them to create their own group with
     * {@code /{cmd.estate} discover} — no party is auto-created.
     * Already-recognized members are simply remembered.
     */
    private void evaluateZoneEntry(Player player, Location location) {
        World world = location.getWorld();
        if (world == null) return;
        List<Estate> zones = estateService.findInstancedAreasAt(
                        world.getName(), location.getBlockX(), location.getBlockZ()).stream()
                .filter(e -> withinVerticalBand(location, e))
                .toList();
        UUID playerId = player.getUniqueId();

        if (zones.isEmpty()) {
            lastZoneByPlayer.remove(playerId);
            return;
        }
        Estate zone = zones.get(0);
        if (zone.id().equals(lastZoneByPlayer.get(playerId))) {
            return; // already notified this zone
        }
        lastZoneByPlayer.put(playerId, zone.id());

        if (zones.stream().anyMatch(e -> isMember(e, playerId))) {
            return; // known member — nothing to prompt
        }

        // Same keys/placeholders as the edit gate (EstateStructureListener):
        // entering the zone and bumping into it show the exact same cartel.
        CommandMessages.adventureZoneNearby(player, zone.type());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** All END/TRIAL_CHAMBER estates whose gated area contains this location. */
    private List<Estate> adventureAreasAt(Location location) {
        World world = location.getWorld();
        if (world == null) return List.of();
        return new java.util.ArrayList<>(estateService.findInstancedAreasAt(
                world.getName(), location.getBlockX(), location.getBlockZ()));
    }

    /** Stealth band check: |y - anchorY| within the configured vertical band. */
    private boolean withinVerticalBand(Location location, Estate estate) {
        double dy = location.getY() - estate.areaY();
        return dy >= -bandBelow && dy <= bandAbove;
    }

    private static boolean isMember(Estate estate, java.util.UUID playerId) {
        return estate.isOwner(playerId) || estate.isMember(playerId);
    }

    private static boolean isHoldingEye(ItemStack item) {
        return item != null && item.getType() == Material.ENDER_EYE;
    }

    /** Counts END_PORTAL_FRAME blocks with an eye within the given radius of the location. */
    private static int countFilledFrames(Location center, int radius) {
        int count = 0;
        World world = center.getWorld();
        if (world == null) return 0;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                int ty = center.getBlockY() + y;
                if (ty < world.getMinHeight() || ty > world.getMaxHeight() - 1) continue;
                for (int z = -radius; z <= radius; z++) {
                    Block block = world.getBlockAt(center.getBlockX() + x, ty, center.getBlockZ() + z);
                    if (block.getType() == Material.END_PORTAL_FRAME
                            && block.getBlockData() instanceof EndPortalFrame frame
                            && frame.hasEye()) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private void broadcastToEstate(UUID estateId, Component message) {
        Estate estate = estateService.findById(estateId).orElse(null);
        if (estate == null) return;
        for (UUID memberId : estate.members()) {
            Player online = Bukkit.getPlayer(memberId);
            if (online != null) online.sendMessage(message);
        }
    }

    private org.bukkit.Location fallbackExit() {
        return Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    private org.bukkit.plugin.Plugin plugin() {
        return Bukkit.getPluginManager().getPlugin("DreamCraftProtection");
    }
}
