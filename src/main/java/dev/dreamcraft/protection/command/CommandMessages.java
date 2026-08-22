package dev.dreamcraft.protection.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/**
 * Shared Adventure-based message helpers for command handlers.
 *
 * <p>Prefixes and shared texts are loaded from the message catalog
 * ({@code messages.yml}) so each server can rebrand them without recompiling —
 * see {@link dev.dreamcraft.protection.message.Messages}.
 */
public final class CommandMessages {

    private static final LegacyComponentSerializer LEGACY_AMPERSAND =
            LegacyComponentSerializer.legacyAmpersand();

    static Component PREFIX = Component.text("[DreamCraft] ", NamedTextColor.DARK_PURPLE);
    static Component WARD_PREFIX = Component.text("[Ward] ", NamedTextColor.DARK_AQUA);
    static Component CITY_PREFIX = Component.text("[Ciudad] ", NamedTextColor.AQUA);
    static Component ESTATE_PREFIX = Component.text("[Estate] ", NamedTextColor.LIGHT_PURPLE);

    private CommandMessages() {}

    /**
     * Reloads the prefixes from the message catalog.
     * Values use legacy & codes; blank/missing keys keep the built-in default.
     */
    public static void reloadPrefixes(FileConfiguration messages) {
        PREFIX = parse(messages, "prefixes.global", "[DreamCraft] ", NamedTextColor.DARK_PURPLE);
        WARD_PREFIX = parse(messages, "prefixes.ward", "[Ward] ", NamedTextColor.DARK_AQUA);
        CITY_PREFIX = parse(messages, "prefixes.city", "[Ciudad] ", NamedTextColor.AQUA);
        ESTATE_PREFIX = parse(messages, "prefixes.estate", "[Estate] ", NamedTextColor.LIGHT_PURPLE);
    }

    private static Component parse(FileConfiguration cfg, String key, String fallbackText, NamedTextColor color) {
        String raw = cfg == null ? null : cfg.getString(key);
        if (raw != null && !raw.isBlank()) {
            return LEGACY_AMPERSAND.deserialize(raw).append(Component.space());
        }
        return Component.text(fallbackText, color);
    }

    /** Catalog lookup with inline fallback: custom yml → embedded default → the given literal. */
    public static String tr(String key, String fallback, Object... placeholders) {
        return dev.dreamcraft.protection.message.Messages.get().tr(key, fallback, placeholders);
    }

    /**
     * Parses a message that may contain legacy color codes in either flavor
     * (& or §), so server overrides can restyle any text safely.
     */
    private static Component parseLegacy(String message) {
        return LEGACY_AMPERSAND.deserialize(message.replace('§', '&'));
    }

    /** Renders a help block from the catalog: header, subtitle, blank line, lines[]. */
    public static void helpBlock(Player player, String baseKey) {
        var m = dev.dreamcraft.protection.message.Messages.get();
        String header = m.tr(baseKey + ".header", "");
        if (!header.isBlank()) player.sendMessage(parseLegacy(header));
        String subtitle = m.tr(baseKey + ".subtitle", "");
        if (!subtitle.isBlank()) player.sendMessage(parseLegacy(subtitle));
        player.sendMessage(Component.space());
        for (String line : m.list(baseKey + ".lines")) {
            if (!line.isBlank()) player.sendMessage(parseLegacy(line));
        }
    }

    /** Renders an extra admin sub-block of a help block (header + lines). */
    public static void helpAdminSection(Player player, String adminBaseKey) {
        var m = dev.dreamcraft.protection.message.Messages.get();
        player.sendMessage(Component.space());
        String header = m.tr(adminBaseKey + ".header", "");
        if (!header.isBlank()) player.sendMessage(parseLegacy(header));
        for (String line : m.list(adminBaseKey + ".lines")) {
            if (!line.isBlank()) player.sendMessage(parseLegacy(line));
        }
    }

    static void error(Player player, Component prefix, String message) {
        player.sendMessage(prefix.append(parseLegacy(message)));
    }

    static void warn(Player player, Component prefix, String message) {
        player.sendMessage(prefix.append(parseLegacy(message).colorIfAbsent(NamedTextColor.YELLOW)));
    }

    static void ok(Player player, Component prefix, String message) {
        player.sendMessage(prefix.append(parseLegacy(message).colorIfAbsent(NamedTextColor.GREEN)));
    }

    static void info(Player player, Component prefix, String message) {
        player.sendMessage(prefix.append(parseLegacy(message).colorIfAbsent(NamedTextColor.GRAY)));
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
