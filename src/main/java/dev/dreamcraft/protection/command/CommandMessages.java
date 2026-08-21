package dev.dreamcraft.protection.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

/**
 * Shared Adventure-based message helpers for command handlers.
 *
 * <p>Uses Paper's Component API (actionbar, titles) instead of legacy §-codes
 * to keep a consistent presentation voice across all domain commands.
 */
final class CommandMessages {

    static final Component PREFIX = Component.text("[DreamCraft] ", NamedTextColor.DARK_PURPLE);
    static final Component WARD_PREFIX = Component.text("[Ward] ", NamedTextColor.DARK_AQUA);
    static final Component CITY_PREFIX = Component.text("[Ciudad] ", NamedTextColor.AQUA);
    static final Component ESTATE_PREFIX = Component.text("[Estate] ", NamedTextColor.LIGHT_PURPLE);

    private CommandMessages() {}

    static void error(Player player, Component prefix, String message) {
        player.sendMessage(prefix.append(Component.text(message, NamedTextColor.RED)));
    }

    static void warn(Player player, Component prefix, String message) {
        player.sendMessage(prefix.append(Component.text(message, NamedTextColor.YELLOW)));
    }

    static void ok(Player player, Component prefix, String message) {
        player.sendMessage(prefix.append(Component.text(message, NamedTextColor.GREEN)));
    }

    static void info(Player player, Component prefix, String message) {
        player.sendMessage(prefix.append(Component.text(message, NamedTextColor.GRAY)));
    }

    /** Discrete actionbar feedback (non-intrusive). */
    static void actionbar(Player player, String message, NamedTextColor color) {
        player.sendActionBar(Component.text(message, color));
    }

    /** Brief title + subtitle for key actions. */
    static void title(Player player, String title, String subtitle, NamedTextColor color) {
        player.showTitle(net.kyori.adventure.title.Title.title(
                Component.text(title, color, TextDecoration.BOLD),
                Component.text(subtitle, NamedTextColor.GRAY),
                net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ZERO,
                        java.time.Duration.ofSeconds(2),
                        java.time.Duration.ofMillis(500))));
    }

    /** Friendly handling of IllegalArgumentException from domain services. */
    static boolean handleDomainException(Player player, Component prefix, RuntimeException e) {
        if (e instanceof IllegalArgumentException) {
            error(player, prefix, e.getMessage());
            return true;
        }
        return false;
    }

    /**
     * Resolves a player display name without ever exposing raw UUID fragments.
     * Online name → last known name (offline) → "Desconocido".
     */
    static String resolveName(java.util.UUID uuid) {
        org.bukkit.entity.Player online = org.bukkit.Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        String offline = org.bukkit.Bukkit.getOfflinePlayer(uuid).getName();
        return offline != null ? offline : "Desconocido";
    }
}
