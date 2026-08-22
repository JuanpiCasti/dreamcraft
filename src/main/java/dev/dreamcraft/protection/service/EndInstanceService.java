package dev.dreamcraft.protection.service;

import dev.dreamcraft.protection.command.CommandMessages;

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
        // The dragon's phase AI only ticks while its arena chunks are loaded.
        // Players arrive at the platform (chunk ~6,3), far from the origin —
        // without force-loading, the arena unloads and the dragon freezes
        // mid-air, ignoring crystals and players alike.
        for (int cx = -2; cx <= 2; cx++) {
            for (int cz = -2; cz <= 2; cz++) {
                world.setChunkForceLoaded(cx, cz, true);
            }
        }
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
        player.sendMessage(CommandMessages.ESTATE_PREFIX
                .append(Component.text("Entraste al End de ", NamedTextColor.GREEN))
                .append(Component.text(estate.name(), NamedTextColor.AQUA))
                .append(Component.text(others.isEmpty()
                        ? ". Sos el primero en llegar."
                        : ". Ya están del otro lado: " + String.join(", ", others) + ".", NamedTextColor.GREEN)));

        Component arrival = CommandMessages.ESTATE_PREFIX
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
        // Lore: every retreat through the exit portal regenerates the overworld
        // portal room (frames restored, eyes stripped) for the next group.
        Estate estate = estates().findById(estateId).orElse(null);
        if (estate != null && estate.type().usesEndInstance()) {
            schedulePortalRefresh(estate);
        }
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
        player.sendMessage(CommandMessages.prefixed("estate", "Fuiste enviado fuera de una instancia de aventura.",
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
                rollbackZoneEdits(current);
                regeneratePortal(current);
                broadcastToSession(estateId, CommandMessages.prefixed("estate", "El portal de entrada se reinició.",
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

    /** Short-delay portal regeneration after any member retreats to the overworld. */
    private void schedulePortalRefresh(Estate estate) {
        cancelPendingPortalReset(estate.id());
        UUID estateId = estate.id();
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingPortalResets.remove(estateId);
            Estate current = estates().findById(estateId).orElse(null);
            if (current != null) {
                rollbackZoneEdits(current);
                regeneratePortal(current);
            }
        }, 40L); // 2s — enough for the exit teleport to settle
        pendingPortalResets.put(estateId, task);
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
                    online.sendMessage(CommandMessages.prefixed("estate", "La instancia se reinició; volviste al mundo principal.",
                            NamedTextColor.YELLOW));
                }
            }
        }

        regeneratePortal(estate);
        rollbackZoneEdits(estate);

        String name = worldNameFor(estate);
        World world = Bukkit.getWorld(name);
        if (world != null) {
            Bukkit.unloadWorld(world, false);
        }
        File folder = new File(Bukkit.getWorldContainer(), name);
        if (folder.exists()) deleteRecursively(folder);

        plugin.getLogger().info("[EndInstance] Instancia reiniciada: " + name
                + " — mapa restaurado y dragona lista para el próximo grupo.");
        broadcastToEstate(estate, CommandMessages.ESTATE_PREFIX
                .append(Component.text("El End de ", NamedTextColor.GREEN))
                .append(Component.text(estate.name(), NamedTextColor.AQUA))
                .append(Component.text(" se reinició: la dragona reapareció y el mapa fue restaurado.",
                        NamedTextColor.GREEN)));
    }

    /** Optional: edit journal — rolls zone blocks back to pristine on close. */
    private EstateZoneJournal zoneJournal;

    /** Registers the zone edit journal used for between-group regeneration. */
    public void setZoneJournal(EstateZoneJournal journal) {
        this.zoneJournal = journal;
    }

    /** Discards recorded edits without applying them (area re-anchored). */
    public void clearZoneEdits(java.util.UUID estateId) {
        if (zoneJournal != null) zoneJournal.clear(estateId);
    }

    /** Rolls back journaled edits so the next group finds pristine chunks. */
    private void rollbackZoneEdits(Estate estate) {
        if (zoneJournal != null && config.regenerateZone()) {
            zoneJournal.rollback(estate.id());
        }
    }

    /**
     * Captures the adventure zone's regenerable state inside the estate's area
     * (full circle ∩ vertical stealth band):
     * <ul>
     *   <li><b>portal frames</b> — "x,y,z|facing|eye" for exact repair;</li>
     *   <li><b>natural loot containers</b> — "x,y,z|type|lootTableKey" for
     *       re-arming each container with its vanilla loot table under a fresh
     *       random seed on zone close, so every group finds DIFFERENT loot.</li>
     * </ul>
     * Only containers whose LootTable is still set qualify: vanilla clears the
     * tag once filled, which is exactly how looted chests are detected.
     */
    public void capturePortal(Estate estate) {
        if (!config.enabled() || !estate.type().usesEndInstance() || !estate.hasArea()) return;
        World world = Bukkit.getWorld(estate.areaWorld());
        if (world == null) return;

        int r = Math.max(4, estate.areaRadius());
        long r2 = (long) r * r;
        int yMin = Math.max(world.getMinHeight(), estate.areaY() - config.areaBandBelow());
        int yMax = Math.min(world.getMaxHeight() - 1, estate.areaY() + config.areaBandAbove());

        List<String> frames = new ArrayList<>();
        List<String> containers = new ArrayList<>();
        for (int x = estate.areaX() - r; x <= estate.areaX() + r; x++) {
            for (int z = estate.areaZ() - r; z <= estate.areaZ() + r; z++) {
                long dx = x - estate.areaX();
                long dz = z - estate.areaZ();
                if (dx * dx + dz * dz > r2) continue;
                for (int y = yMin; y <= yMax; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == Material.END_PORTAL_FRAME
                            && block.getBlockData() instanceof EndPortalFrame frame) {
                        frames.add(x + "," + y + "," + z
                                + "|" + frame.getFacing().name()
                                + "|" + frame.hasEye());
                        continue;
                    }
                    Material type = block.getType();
                    if (type == Material.CHEST || type == Material.TRAPPED_CHEST
                            || type == Material.BARREL) {
                        var state = block.getState();
                        if (state instanceof org.bukkit.loot.Lootable lootable
                                && lootable.getLootTable() != null) {
                            containers.add(x + "," + y + "," + z
                                    + "|" + type.name()
                                    + "|" + lootable.getLootTable().getKey());
                        }
                    }
                }
            }
        }
        estate.portalFrames(frames);
        estate.containerLoot(containers);
        estates().save(estate);
        plugin.getLogger().info("[EndInstance] Snapshot del nexo '" + estate.name()
                + "' capturado: " + frames.size() + " frame(s), "
                + containers.size() + " contenedor(es) de loot.");
    }

    /**
     * Regenerates the overworld portal room between groups:
     * <ul>
     *   <li>restores every captured frame that was broken or removed;</li>
     *   <li>strips placed Eyes so the next group must re-arm the portal;</li>
     *   <li>closes any open end-portal blocks.</li>
     * </ul>
     * Estates without a snapshot keep the legacy eye-strip behavior.
     */
    public void regeneratePortal(Estate estate) {
        if (!estate.hasArea()) return;
        World world = Bukkit.getWorld(estate.areaWorld());
        if (world == null) return;

        int repaired = 0;
        int stripped = 0;

        // 1. Restore captured frames (broken frames come back)
        for (String entry : estate.portalFrames()) {
            String[] parts = entry.split("\\|");
            String[] pos = parts[0].split(",");
            try {
                int x = Integer.parseInt(pos[0]);
                int y = Integer.parseInt(pos[1]);
                int z = Integer.parseInt(pos[2]);
                Block block = world.getBlockAt(x, y, z);
                EndPortalFrame data = null;
                if (block.getBlockData() instanceof EndPortalFrame existing) {
                    data = existing;
                } else if (block.getType() != Material.END_PORTAL_FRAME) {
                    block.setType(Material.END_PORTAL_FRAME);
                    if (block.getBlockData() instanceof EndPortalFrame fresh) data = fresh;
                    repaired++;
                }
                if (data != null) {
                    boolean wantEye = Boolean.parseBoolean(parts.length > 2 ? parts[2] : "false");
                    if (parts.length > 1) {
                        try { data.setFacing(org.bukkit.block.BlockFace.valueOf(parts[1])); }
                        catch (IllegalArgumentException ignored) {}
                    }
                    if (data.hasEye()) { data.setEye(false); stripped++; }
                    if (wantEye && !data.hasEye()) data.setEye(true); // vanilla pristine state
                    block.setBlockData(data);
                }
            } catch (NumberFormatException ignored) {}
        }

        // 2. Legacy sweep: strip remaining eyes + close open portals in the scan area
        int r = Math.min(Math.max(4, config.frameScanRadius()), Math.max(4, estate.areaRadius()));
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
        // 3. Re-arm natural loot containers: same vanilla table, FRESH random
        //    seed — the next group finds different loot than the last one.
        int rerolled = 0;
        if (config.regenerateZone()) {
            for (String entry : estate.containerLoot()) {
                String[] parts = entry.split("\\|");
                if (parts.length < 3) continue;
                try {
                    String[] pos = parts[0].split(",");
                    Material type = Material.matchMaterial(parts[1]);
                    org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.fromString(parts[2]);
                    if (type == null || key == null) continue;
                    Block block = world.getBlockAt(
                            Integer.parseInt(pos[0]), Integer.parseInt(pos[1]), Integer.parseInt(pos[2]));
                    if (block.getType() != type) block.setType(type);
                    var state = block.getState();
                    if (!(state instanceof org.bukkit.loot.Lootable lootable)) continue;
                    org.bukkit.loot.LootTable table = Bukkit.getLootTable(key);
                    if (table == null) continue;
                    if (state instanceof org.bukkit.inventory.InventoryHolder holder) {
                        holder.getInventory().clear();
                    }
                    lootable.setLootTable(table);
                    lootable.setSeed(java.util.concurrent.ThreadLocalRandom.current().nextLong());
                    state.update();
                    rerolled++;
                } catch (Exception ignored) {
                    // Corrupt/legacy entry — skip, the rest still re-arms
                }
            }
        }
        if (repaired > 0 || stripped > 0 || rerolled > 0) {
            plugin.getLogger().info("[EndInstance] Nexo '" + estate.name()
                    + "' regenerado (" + repaired + " frames restaurados, "
                    + stripped + " limpiados, " + rerolled + " loots re-armados).");
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
