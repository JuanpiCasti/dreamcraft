package dev.dreamcraft.protection.integration.packetevents;

import java.util.UUID;

/**
 * Integration adapter contract for PacketEvents.
 *
 * <p>PacketEvents is used ONLY when a UI or protocol feature genuinely requires
 * packet-level access. DreamCraft does not use it for domain logic.
 *
 * <p>Current uses:
 * <ul>
 *   <li>Sending title/subtitle packets with custom formatting for Ward events.</li>
 *   <li>Sending action bar messages with resource-pack font support (fallback to plain text).</li>
 * </ul>
 *
 * <p>When PacketEvents is unavailable, all methods fall back to vanilla Bukkit APIs.
 */
public interface PacketEventsAdapter {

    /**
     * Sends a title/subtitle to a player.
     *
     * @param playerId  target player UUID
     * @param title     main title text (may contain legacy color codes)
     * @param subtitle  subtitle text (may be null)
     * @param fadeInTicks  fade-in duration in ticks
     * @param stayTicks    stay duration in ticks
     * @param fadeOutTicks fade-out duration in ticks
     */
    void sendTitle(UUID playerId, String title, String subtitle,
                   int fadeInTicks, int stayTicks, int fadeOutTicks);

    /**
     * Sends an action bar message to a player.
     */
    void sendActionBar(UUID playerId, String message);

    boolean isAvailable();
}
