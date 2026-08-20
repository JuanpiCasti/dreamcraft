package dev.dreamcraft.protection.integration.packetevents;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerActionBar;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetTitleSubtitle;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetTitleText;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetTitleTimes;
import dev.dreamcraft.protection.integration.registry.CapabilityRegistry;
import dev.dreamcraft.protection.integration.registry.IntegrationKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * PacketEvents 2.x implementation of {@link PacketEventsAdapter}.
 *
 * <p>Uses PacketEvents' stable wrapper API (2.13.0).
 * Falls back to vanilla Bukkit title/actionbar when PE is unavailable.
 *
 * <p><b>Verified API surface (packetevents-spigot 2.13.0):</b>
 * <ul>
 *   <li>{@code WrapperPlayServerSetTitleText}</li>
 *   <li>{@code WrapperPlayServerSetTitleSubtitle} (not SetSubtitleText)</li>
 *   <li>{@code WrapperPlayServerSetTitleTimes}</li>
 *   <li>{@code WrapperPlayServerActionBar}</li>
 * </ul>
 */
public final class PacketEventsAdapterImpl implements PacketEventsAdapter {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    private final CapabilityRegistry registry;
    private final Logger logger;

    public PacketEventsAdapterImpl(CapabilityRegistry registry, Logger logger) {
        this.registry = registry;
        this.logger = logger;
    }

    @Override
    public boolean isAvailable() {
        return registry.isAvailable(IntegrationKey.PACKET_EVENTS);
    }

    @Override
    public void sendTitle(UUID playerId, String title, String subtitle,
                          int fadeInTicks, int stayTicks, int fadeOutTicks) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return;

        if (isAvailable()) {
            try {
                User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
                if (user != null) {
                    Component titleComp = LEGACY.deserialize(title);
                    Component subtitleComp = (subtitle != null)
                            ? LEGACY.deserialize(subtitle)
                            : Component.empty();
                    user.sendPacket(new WrapperPlayServerSetTitleTimes(fadeInTicks, stayTicks, fadeOutTicks));
                    user.sendPacket(new WrapperPlayServerSetTitleText(titleComp));
                    user.sendPacket(new WrapperPlayServerSetTitleSubtitle(subtitleComp));
                    return;
                }
            } catch (Exception e) {
                logger.warning("[PacketEvents] sendTitle failed for " + playerId + ": " + e.getMessage());
            }
        }
        fallbackTitle(player, title, subtitle, fadeInTicks, stayTicks, fadeOutTicks);
    }

    @Override
    public void sendActionBar(UUID playerId, String message) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return;

        if (isAvailable()) {
            try {
                User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
                if (user != null) {
                    user.sendPacket(new WrapperPlayServerActionBar(LEGACY.deserialize(message)));
                    return;
                }
            } catch (Exception e) {
                logger.warning("[PacketEvents] sendActionBar failed for " + playerId + ": " + e.getMessage());
            }
        }
        player.sendActionBar(LEGACY.deserialize(message));
    }

    // ── Fallback ──────────────────────────────────────────────────────────────

    private void fallbackTitle(Player player, String title, String subtitle,
                                int fadeIn, int stay, int fadeOut) {
        player.showTitle(net.kyori.adventure.title.Title.title(
                LEGACY.deserialize(title),
                subtitle != null ? LEGACY.deserialize(subtitle) : Component.empty(),
                net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ofMillis(fadeIn * 50L),
                        java.time.Duration.ofMillis(stay * 50L),
                        java.time.Duration.ofMillis(fadeOut * 50L)
                )
        ));
    }
}
