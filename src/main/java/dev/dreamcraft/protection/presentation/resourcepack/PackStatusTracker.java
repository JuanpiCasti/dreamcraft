package dev.dreamcraft.protection.presentation.resourcepack;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks whether each player has the DreamCraft resource pack loaded, so the
 * presentation layer can decide per viewer between custom assets (CMD) and
 * the vanilla fallback (MD §9).
 *
 * <p>Only used when {@code menus.provider: auto}; with {@code rp} or
 * {@code vanilla} every player is treated uniformly.
 */
public final class PackStatusTracker implements PackState, Listener {

    private final Map<UUID, Boolean> loaded = new ConcurrentHashMap<>();

    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        loaded.put(event.getPlayer().getUniqueId(),
                event.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        loaded.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public boolean has(UUID playerId) {
        return Boolean.TRUE.equals(loaded.get(playerId));
    }
}
