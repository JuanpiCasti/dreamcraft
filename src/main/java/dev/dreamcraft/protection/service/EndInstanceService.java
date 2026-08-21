package dev.dreamcraft.protection.service;

import dev.dreamcraft.protection.config.EndInstanceConfig;
import dev.dreamcraft.protection.domain.model.Estate;
import dev.dreamcraft.protection.domain.service.EstateService;
import dev.dreamcraft.protection.integration.essentialsx.EssentialsAdapter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.EndPortalFrame;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs private End dimension instances for END-type Estates.
 *
 * <p>Each estate of type {@link dev.dreamcraft.protection.domain.model.EstateType#END}
 * gets its own End world ({@code <prefix><estateId8>}) created on demand via the
 * Bukkit {@link WorldCreator} API — no Multiverse required. This keeps the shared
 * {@code world_the_end} untouched for the rest of the server.
 *
 * <p>Lifecycle per adventuring group:
 * <ol>
 *   <li>First member steps into the estate's overworld portal → redirected to the
 *       instance world; an obsidian entry platform is built and a fresh
 *       Ender Dragon is spawned if none is alive.</li>
 *   <li>The arriving player is told which estate members are already "on the other
 *       side"; after a grace period the overworld portal frames are stripped of eyes
 *       so the next group must re-insert them (membership-gated).</li>
 *   <li>When the last member leaves (exit portal, death respawn, quit), the world is
 *       unloaded and its folder deleted after a configurable delay — the map returns
 *       to its pre-boss state and the next group faces a freshly spawned dragon.</li>
 * </ol>
 */
public final class EndInstanceService {

    /** Vanilla-style entry platform: 5×5 obsidian centered at (100, 48, 0). */
    private static final int PLATFORM_X = 100;
    private static final int PLATFORM_Y = 48;
    private static final int PLATFORM_Z = 0;

    private final JavaPlugin plugin;
    /** Resolved lazily so the service survives /protection reload (domain re-boot). */
    private final java.util.function.Supplier<EstateService> estateServiceSupplier;
    private volatile EndInstanceConfig config;
    private final EssentialsAdapter essentialsAdapter;

    /** playerId → estateId for every player currently inside an instance world. */
    private final Map<UUID, UUID> estateByPlayer = new ConcurrentHashMap<>();
    /** estateId → players currently inside that estate's instance world. */
    private final Map<UUID, Set<UUID>> playersByEstate = new ConcurrentHashMap<>();
    /** estateId → scheduled full world reset task (cancelled if someone re-enters). */
    private final Map<UUID, BukkitTask> pendingWorldResets = new ConcurrentHashMap<>();
    /** estateId → scheduled overworld portal eye-strip task. */
    private final Map<UUID, BukkitTask> pendingPortalResets = new ConcurrentHashMap<>();

    public EndInstanceService(JavaPlugin plugin,
                              java.util.function.Supplier<EstateService> estateServiceSupplier,
                              EndInstanceConfig config,
                              EssentialsAdapter essentialsAdapter) {
        this.plugin = plugin;
        this.estateServiceSupplier = estateServiceSupplier;
        this.config = config;
        this.essentialsAdapter = essentialsAdapter;
    }

    /** Swaps the configuration (used on config reload without dropping sessions). */
    public void applyConfig(EndInstanceConfig newConfig) {
        this.config = newConfig;
    }

    private EstateService estates() {
        return estateServiceSupplier.get();
    }

    // ── World naming / resolution ─────────────────────────────────────────────

    /** True if estate adventure instancing is enabled in the config. */
    public boolean isEnabled() {
        return config.enabled();
    }

    public String worldNameFor(Estate estate) {
        return config.worldPrefix() + shortId(estate);
    }

    public boolean isInstanceWorldName(String worldName) {
        return worldName != null && worldName.startsWith(config.worldPrefix());
    }

    /** Resolves the estate that owns an instance world, if any. */
    public Optional<Estate> estateForWorldName(String worldName) {
        if (!isInstanceWorldName(worldName)) return Optional.empty();
        String suffix = worldName.substring(config.worldPrefix().length());
        return estates().findAll().stream()
                .filter(e -> shortId(e).equals(suffix))
                .findFirst();
    }

    private static String shortId(Estate estate) {
        return estate.id().toString().replace("-", "").substring(0, 8);
    }

    // ── World lifecycle ───────────────────────────────────────────────────────

    /**
     * Returns the estate's instance world, creating it (and its dragon) on first use.
     * A stale folder left by a previous session/reset is wiped before creation.
     */
    public World getOrCreateWorld(Estate estate) {
        String name = worldNameFor(estate);
        World existing = Bukkit.getWorld(name);
        if (existing != null) return existing;

        File stale = new File(Bukkit.getWorldContainer(), name);
        if (stale.exists()) deleteRecursively(stale);

        World world = Bukkit.createWorld(new WorldCreator(name)
                .environment(World.Environment.THE_END)
                .generateStructures(true));
        if (world == null) {
            plugin.getLogger().severe("[EndInstance] No se pudo crear el mundo " + name);
            return null;
        }
        plugin.getLogger().info("[EndInstance] Mundo instanciado: " + name + " (estate " + estate.name() + ")");
        ensureDragon(world);
        return world;
    }

    /** Entry point of the instance world: builds the obsidian platform and returns a safe spawn location. */
    public Location entryLocation(World world) {
        buildEntryPlatform(world);
        return new Location(world, PLATFORM_X + 0.5, PLATFORM_Y + 1.0, PLATFORM_Z + 0.5);
    }

    private void buildEntryPlatform(World world) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Block floor = world.getBlockAt(PLATFORM_X + dx, PLATFORM_Y, PLATFORM_Z + dz);
                if (floor.getType() != Material.OBSIDIAN) floor.setType(Material.OBSIDIAN);
                for (int dy = 1; dy <= 3; dy++) {
                    Block above = world.getBlockAt(PLATFORM_X + dx, PLATFORM_Y + dy, PLATFORM_Z + dz);
                    if (!above.getType().isAir()) above.setType(Material.AIR);
                }
            }
        }
    }

    /**
     * Spawns a fresh Ender Dragon at the arena center if none is alive in the world.
     */
    public void ensureDragon(World world) {
        world.getChunkAt(0, 0).load();
        if (!world.getEntitiesByClass(EnderDragon.class).isEmpty()) return;
        world.spawn(new Location(world, 0, 128, 0), EnderDragon.class);
        plugin.getLogger().info("[EndInstance] Dragona generada en " + world.getName());
    }

    // ── Session tracking ──────────────────────────────────────────────────────

    /** True if the player is currently inside any instance world tracked by this service. */
    public boolean isInInstance(UUID playerId) {
        return estateByPlayer.containsKey(playerId);
    }

    /** Estate whose instance the player is inside, or empty. */
    public Optional<Estate> estateOfPlayer(UUID playerId) {
        UUID estateId = estateByPlayer.get(playerId);
        return estateId == null ? Optional.empty() : estates().findById(estateId);
    }

    /**
     * Registers a player as arrived in the estate's instance. Announces who was
     * already "on the other side" and schedules the overworld portal reset.
     */
    public void handleEnter(Player player, Estate estate) {
        cancelPendingWorldReset(estate.id());

        Set<UUID> members = playersByEstate.computeIfAbsent(estate.id(), k -> ConcurrentHashMap.newKeySet());
        boolean firstArrival = members.isEmpty();
        members.add(player.getUniqueId());
        estateByPlayer.put(player.getUniqueId(), estate.id());

        List<String> others = new ArrayList<>();
        for (UUID memberId : members) {
            if (!memberId.equals(player.getUniqueId())) others.add(resolveName(memberId));
        }
        player.sendMessage(Component.text("[Estate] ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text("Entraste al End de ", NamedTextColor.GREEN))
                .append(Component.text(estate.name(), NamedTextColor.AQUA))
                .append(Component.text(others.isEmpty()
                        ? ". Sos el primero en llegar."
                        : ". Ya están del otro lado: " + String.join(", ", others) + ".", NamedTextColor.GREEN)));

        Component arrival = Component.text("[Estate] ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(player.getName(), NamedTextColor.YELLOW))
                .append(Component.text(" llegó al End.", NamedTextColor.GREEN));
        broadcastToSession(estate.id(), arrival, player.getUniqueId());

        if (firstArrival && config.resetPortalOnFirstEnter()) {
            schedulePortalReset(estate);
        }
    }

    /**
     * Unregisters a player from their instance session. When the last member
     * leaves, schedules the full world reset (map restore + dragon respawn).
     */
    public void handleLeave(Player player) {
        UUID estateId = estateByPlayer.remove(player.getUniqueId());
        if (estateId == null) return;
        Set<UUID> members = playersByEstate.get(estateId);
        if (members == null) return;
        members.remove(player.getUniqueId());
        if (members.isEmpty()) {
            scheduleWorldReset(estateId);
        }
    }

    /**
     * Re-login handling: players still positioned inside an instance world rejoin
     * their session if they are still estate members; otherwise they are sent out.
     */
    public void handleJoin(Player player) {
        if (!isInstanceWorldName(player.getWorld().getName())) return;
        Optional<Estate> estate = estateForWorldName(player.getWorld().getName());
        boolean authorized = estate.isPresent()
                && (estate.get().isMember(player.getUniqueId()) || estate.get().isOwner(player.getUniqueId()));
        if (authorized) {
            handleEnter(player, estate.get());
            return;
        }
        // Not a member anymore (or orphaned world): send them home.
        Estate target = estate.orElse(null);
        Location exit = target != null ? exitLocationFor(target) : fallbackExitLocation();
        player.teleport(exit);
        player.sendMessage(Component.text("[Estate] Fuiste enviado fuera de una instancia de aventura.",
                NamedTextColor.YELLOW));
    }

    // ── Reset scheduling ──────────────────────────────────────────────────────

    private void schedulePortalReset(Estate estate) {
        cancelPendingPortalReset(estate.id());
        UUID estateId = estate.id();
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingPortalResets.remove(estateId);
            Estate current = estates().findById(estateId).orElse(null);
            if (current != null && !playersByEstate.getOrDefault(estateId, Set.of()).isEmpty()) {
                stripOverworldPortal(current);
                broadcastToSession(estateId, Component.text("[Estate] El portal de entrada se reinició.",
                        NamedTextColor.GRAY), null);
            }
        }, config.portalResetDelaySeconds() * 20L);
        pendingPortalResets.put(estateId, task);
    }

    private void scheduleWorldReset(UUID estateId) {
        cancelPendingWorldReset(estateId);
        long delayTicks = Math.max(0, config.resetDelaySeconds()) * 20L;
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingWorldResets.remove(estateId);
            Estate estate = estates().findById(estateId).orElse(null);
            if (estate != null) resetInstance(estate);
        }, delayTicks);
        pendingWorldResets.put(estateId, task);
    }

    private void cancelPendingWorldReset(UUID estateId) {
        BukkitTask task = pendingWorldResets.remove(estateId);
        if (task != null) task.cancel();
    }

    private void cancelPendingPortalReset(UUID estateId) {
        BukkitTask task = pendingPortalResets.remove(estateId);
        if (task != null) task.cancel();
    }

    /**
     * Full instance reset: teleports out anyone left, strips the overworld portal
     * eyes (so the next group must rebuild it), unloads the world without saving
     * and deletes its folder — restoring the map to its pre-boss state. The next
     * group gets a freshly spawned dragon because the world is recreated.
     */
    public void resetInstance(Estate estate) {
        cancelPendingWorldReset(estate.id());
        cancelPendingPortalReset(estate.id());

        Set<UUID> members = playersByEstate.remove(estate.id());
        if (members != null) {
            for (UUID memberId : members) {
                estateByPlayer.remove(memberId);
                Player online = Bukkit.getPlayer(memberId);
                if (online != null && online.isOnline()
                        && online.getWorld().getName().equals(worldNameFor(estate))) {
                    online.teleport(exitLocationFor(estate));
                    online.sendMessage(Component.text("[Estate] La instancia se reinició; volviste al mundo principal.",
                            NamedTextColor.YELLOW));
                }
            }
        }

        stripOverworldPortal(estate);

        String name = worldNameFor(estate);
        World world = Bukkit.getWorld(name);
        if (world != null) {
            Bukkit.unloadWorld(world, false);
        }
        File folder = new File(Bukkit.getWorldContainer(), name);
        if (folder.exists()) deleteRecursively(folder);

        plugin.getLogger().info("[EndInstance] Instancia reiniciada: " + name
                + " — mapa restaurado y dragona lista para el próximo grupo.");
        broadcastToEstate(estate, Component.text("[Estate] ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text("El End de ", NamedTextColor.GREEN))
                .append(Component.text(estate.name(), NamedTextColor.AQUA))
                .append(Component.text(" se reinició: la dragona reapareció y el mapa fue restaurado.",
                        NamedTextColor.GREEN)));
    }

    /**
     * Removes every placed Eye of Ender from the portal frames inside the estate's
     * area and closes any open end-portal blocks, resetting the entrance for the
     * next group.
     */
    public void stripOverworldPortal(Estate estate) {
        if (!estate.hasArea()) return;
        World world = Bukkit.getWorld(estate.areaWorld());
        if (world == null) return;

        int r = Math.min(Math.max(4, config.frameScanRadius()), Math.max(4, estate.areaRadius()));
        int stripped = 0;
        for (int x = estate.areaX() - r; x <= estate.areaX() + r; x++) {
            for (int y = estate.areaY() - r; y <= estate.areaY() + r; y++) {
                if (y < world.getMinHeight() || y > world.getMaxHeight() - 1) continue;
                for (int z = estate.areaZ() - r; z <= estate.areaZ() + r; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == Material.END_PORTAL_FRAME) {
                        if (block.getBlockData() instanceof EndPortalFrame frame && frame.hasEye()) {
                            frame.setEye(false);
                            block.setBlockData(frame);
                            stripped++;
                        }
                    } else if (block.getType() == Material.END_PORTAL) {
                        block.setType(Material.AIR);
                        stripped++;
                    }
                }
            }
        }
        if (stripped > 0) {
            plugin.getLogger().info("[EndInstance] Portal del estate '" + estate.name()
                    + "' reiniciado (" + stripped + " bloques restaurados).");
        }
    }

    // ── Exit locations ────────────────────────────────────────────────────────

    /** Where players land when leaving the instance: estate area anchor → Essentials spawn → main world spawn. */
    public Location exitLocationFor(Estate estate) {
        if (estate.hasArea()) {
            World areaWorld = Bukkit.getWorld(estate.areaWorld());
            if (areaWorld != null) {
                return new Location(areaWorld,
                        estate.areaX() + 0.5, estate.areaY() + 1.0, estate.areaZ() + 0.5);
            }
        }
        return fallbackExitLocation();
    }

    private Location fallbackExitLocation() {
        if (essentialsAdapter != null && essentialsAdapter.isAvailable()) {
            EssentialsAdapter.LocationSnapshot spawn = essentialsAdapter.getSpawn();
            if (spawn != null) {
                World world = Bukkit.getWorld(spawn.worldName());
                if (world != null) {
                    return new Location(world, spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
                }
            }
        }
        return Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    // ── Misc ──────────────────────────────────────────────────────────────────

    /** Pre-creates the instance world + dragon (used by /estate start on END estates). */
    public boolean preopen(Estate estate) {
        if (!config.enabled() || !estate.type().usesEndInstance()) return false;
        World world = getOrCreateWorld(estate);
        return world != null;
    }

    /** Unloads every active instance world without saving (plugin disable). Folders are cleaned lazily. */
    public void shutdown() {
        for (BukkitTask task : pendingWorldResets.values()) task.cancel();
        for (BukkitTask task : pendingPortalResets.values()) task.cancel();
        pendingWorldResets.clear();
        pendingPortalResets.clear();
        estateByPlayer.clear();
        playersByEstate.clear();
        for (World world : new ArrayList<>(Bukkit.getWorlds())) {
            if (isInstanceWorldName(world.getName())) {
                Bukkit.unloadWorld(world, false);
            }
        }
    }

    private void broadcastToSession(UUID estateId, Component message, UUID exclude) {
        Set<UUID> members = playersByEstate.get(estateId);
        if (members == null) return;
        for (UUID memberId : members) {
            if (memberId.equals(exclude)) continue;
            Player online = Bukkit.getPlayer(memberId);
            if (online != null) online.sendMessage(message);
        }
    }

    private void broadcastToEstate(Estate estate, Component message) {
        for (UUID memberId : estate.members()) {
            Player online = Bukkit.getPlayer(memberId);
            if (online != null) online.sendMessage(message);
        }
    }

    private String resolveName(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        String offline = Bukkit.getOfflinePlayer(uuid).getName();
        return offline != null ? offline : "Jugador";
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursively(child);
        }
        if (!file.delete()) {
            file.deleteOnExit();
        }
    }
}
