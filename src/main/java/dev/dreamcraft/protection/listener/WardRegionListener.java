package dev.dreamcraft.protection.listener;

import dev.dreamcraft.protection.domain.model.Ward;
import dev.dreamcraft.protection.domain.service.WardService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shows an action bar when a player enters a Ward zone: the Ward's name and
 * the location of its center block ("la ubicación del ward").
 *
 * <p>Own Ward → aqua with coordinates. Someone else's Ward → gold with the
 * owner's name. Leaving a Ward clears the bar naturally (no forced message).
 *
 * <p>Performance: only evaluated when the player crosses a block boundary,
 * and the resolved Ward is cached per player so repeated moves inside the
 * same zone don't re-query.
 */
public final class WardRegionListener implements Listener {

    private final WardService wardService;
    /** Player UUID → last Ward UUID they were inside (absent = outside). */
    private final Map<UUID, UUID> lastWardByPlayer = new ConcurrentHashMap<>();

    public WardRegionListener(WardService wardService) {
        this.wardService = wardService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return; // same block — nothing to do
        }
        evaluate(event.getPlayer(), to);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        lastWardByPlayer.remove(event.getPlayer().getUniqueId());
        evaluate(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        evaluate(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        lastWardByPlayer.remove(event.getPlayer().getUniqueId());
    }

    private void evaluate(Player player, Location location) {
        Optional<Ward> found = wardService.findAtLocation(
                location.getWorld().getName(), location.getBlockX(), location.getBlockZ());

        UUID playerId = player.getUniqueId();
        if (found.isEmpty()) {
            lastWardByPlayer.remove(playerId);
            return;
        }
        Ward ward = found.get();
        if (ward.id().equals(lastWardByPlayer.get(playerId))) {
            return; // already announced this zone
        }
        lastWardByPlayer.put(playerId, ward.id());

        boolean own = ward.ownerId().equals(playerId);
        String coords = ward.centerX() + ", " + ward.centerY() + ", " + ward.centerZ();
        Component bar;
        if (own) {
            bar = Component.text("⚔ ", NamedTextColor.DARK_AQUA)
                    .append(Component.text(ward.name(), NamedTextColor.AQUA))
                    .append(Component.text("  §8|  ", NamedTextColor.DARK_GRAY))
                    .append(Component.text("Centro: " + coords, NamedTextColor.GRAY));
        } else {
            String ownerName = resolveOwnerName(ward);
            bar = Component.text("⚔ ", NamedTextColor.GOLD)
                    .append(Component.text(ward.name(), NamedTextColor.YELLOW))
                    .append(Component.text(" de " + ownerName, NamedTextColor.GOLD))
                    .append(Component.text("  §8|  ", NamedTextColor.DARK_GRAY))
                    .append(Component.text("Centro: " + coords, NamedTextColor.GRAY));
        }
        player.sendActionBar(bar);
    }

    private String resolveOwnerName(Ward ward) {
        Player online = Bukkit.getPlayer(ward.ownerId());
        if (online != null) return online.getName();
        String offline = Bukkit.getOfflinePlayer(ward.ownerId()).getName();
        return offline != null ? offline : "Desconocido";
    }
}
