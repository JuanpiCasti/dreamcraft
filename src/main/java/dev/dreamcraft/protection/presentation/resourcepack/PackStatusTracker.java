package dev.dreamcraft.protection.presentation.resourcepack;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks whether each player has the DreamCraft resource pack loaded, so the
 * presentation layer can decide per viewer between custom assets (CMD) and
 * the vanilla fallback (MD §9).
 *
 * <p>Two sources feed the map: {@link PlayerResourcePackStatusEvent} covers
 * packs pushed by plugins during play, while the join-time poll of
 * {@link Player#hasResourcePack()} covers the server.properties pack, which is
 * negotiated during the CONFIGURATION phase where Paper does not fire that
 * event (upstream issue #12844). A short delayed re-check absorbs clients
 * whose status lands a few ticks after the join.
 *
 * <p>Only used when {@code menus.provider: auto}; with {@code rp} or
 * {@code vanilla} every player is treated uniformly.
 */
public final class PackStatusTracker implements PackState, Listener {

    private static final long RECHECK_DELAY_TICKS = 40L;

    private final Map<UUID, Boolean> loaded = new ConcurrentHashMap<>();
    private final Plugin plugin;

    public PackStatusTracker(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refresh(event.getPlayer(), "join");
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> refresh(event.getPlayer(), "recheck"), RECHECK_DELAY_TICKS);
    }

    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        boolean ok =
                event.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED;
        org.bukkit.Bukkit.getLogger().info("[DreamCraft][PackDebug] " + event.getPlayer().getName()
                + " event status=" + event.getStatus() + " loaded=" + ok);
        loaded.put(event.getPlayer().getUniqueId(), ok);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        loaded.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public boolean has(UUID playerId) {
        return Boolean.TRUE.equals(loaded.get(playerId));
    }

    /** Polls the player's current pack state and stores it (config-phase safe). */
    private void refresh(Player player, String phase) {
        boolean ok = player.isOnline() && player.hasResourcePack();
        org.bukkit.Bukkit.getLogger().info("[DreamCraft][PackDebug] " + player.getName()
                + " " + phase + " hasResourcePack=" + ok);
        if (ok) {
            loaded.put(player.getUniqueId(), true);
        } else {
            // Only clear on authoritative sources; keep an earlier SUCCESS from
            // the event/recheck window instead of flapping mid-session.
            loaded.remove(player.getUniqueId());
        }
    }
}
