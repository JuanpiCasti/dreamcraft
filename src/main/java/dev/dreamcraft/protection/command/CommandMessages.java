package dev.dreamcraft.protection.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.command.CommandSender;
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

    public static Component PREFIX = Component.text("[DreamCraft] ", NamedTextColor.DARK_PURPLE);
    public static Component WARD_PREFIX = Component.text("[Ward] ", NamedTextColor.DARK_AQUA);
    public static Component CITY_PREFIX = Component.text("[Ciudad] ", NamedTextColor.AQUA);
    public static Component ESTATE_PREFIX = Component.text("[Estate] ", NamedTextColor.LIGHT_PURPLE);

    /**
     * Catalog-driven prefix + legacy-parsed body in one component — for
     * listeners/services outside the command package that build quick
     * feedback lines without a full tr() key.
     */
    public static Component prefixed(String domain, String legacyText, NamedTextColor fallbackColor) {
        Component prefix = switch (domain == null ? "" : domain) {
            case "ward" -> WARD_PREFIX;
            case "city" -> CITY_PREFIX;
            case "estate" -> ESTATE_PREFIX;
            default -> PREFIX;
        };
        return prefix.append(parseLegacy(legacyText).colorIfAbsent(fallbackColor));
    }

    private CommandMessages() {}

    /**
     * Reloads the prefixes from the message catalog.
     * Values use legacy & codes and support the versioned placeholders
     * ({code {name.ward}} → "sync", …); blank/missing keys keep a
     * CommandNames-derived default so bare config.yml rebrands too.
     */
    public static void reloadPrefixes(FileConfiguration messages) {
        PREFIX = parse(messages, "prefixes.global",
                "[DreamCraft] ", NamedTextColor.DARK_PURPLE);
        WARD_PREFIX = parse(messages, "prefixes.ward",
                "[" + dev.dreamcraft.protection.config.CommandNames.root("ward") + "] ", NamedTextColor.DARK_AQUA);
        CITY_PREFIX = parse(messages, "prefixes.city",
                "[" + dev.dreamcraft.protection.config.CommandNames.root("city") + "] ", NamedTextColor.AQUA);
        ESTATE_PREFIX = parse(messages, "prefixes.estate",
                "[" + dev.dreamcraft.protection.config.CommandNames.root("estate") + "] ", NamedTextColor.LIGHT_PURPLE);
    }

    private static Component parse(FileConfiguration cfg, String key, String fallbackText, NamedTextColor color) {
        String raw = cfg == null ? null : cfg.getString(key);
        if (raw != null && !raw.isBlank()) {
            return LEGACY_AMPERSAND.deserialize(
                    dev.dreamcraft.protection.message.Messages.applyVersionedCommands(raw))
                    .append(Component.space());
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

    /** Public parse for listeners sending catalog text to titles/actionbars. */
    public static Component legacy(String legacyText) {
        return parseLegacy(legacyText);
    }

    /**
     * Zone-nearby prompt shared by the area gate and the entry check — same
     * catalog keys and placeholders ({name.estate}/{cmd.estate} versioned;
     * {type}/{type.key} resolved from the adventure type) so both paths show
     * the exact same cartel.
     */
    public static void adventureZoneNearby(Player player,
                                           dev.dreamcraft.protection.domain.model.EstateType type) {
        String typeLabel = tr("adventure.type." + type.key(), type.displayName());
        String typeArg = tr("adventure.type-key." + type.key(), type.key());
        titleLegacy(player,
                tr("adventure.zone-nearby.title",
                        "&6&l{name.estate} &6&lal &f{type} &6&ldisponible",
                        "type", typeLabel),
                tr("adventure.zone-nearby.subtitle",
                        "&eUnete usando &f/{cmd.estate} discover {type.key}",
                        "type.key", typeArg));
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

    static void error(CommandSender sender, Component prefix, String message) {
        sender.sendMessage(prefix.append(parseLegacy(message)));
    }

    static void warn(CommandSender sender, Component prefix, String message) {
        sender.sendMessage(prefix.append(parseLegacy(message).colorIfAbsent(NamedTextColor.YELLOW)));
    }

    static void ok(CommandSender sender, Component prefix, String message) {
        sender.sendMessage(prefix.append(parseLegacy(message).colorIfAbsent(NamedTextColor.GREEN)));
    }

    static void info(CommandSender sender, Component prefix, String message) {
        sender.sendMessage(prefix.append(parseLegacy(message).colorIfAbsent(NamedTextColor.GRAY)));
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

    /**
     * Title + subtitle from catalog strings: supports legacy & codes and
     * {cmd.*}/{name.*} placeholders (resolved by the caller via tr()).
     */
    public static void titleLegacy(Player player, String legacyTitle, String legacySubtitle) {
        player.showTitle(net.kyori.adventure.title.Title.title(
                parseLegacy(legacyTitle),
                parseLegacy(legacySubtitle),
                net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ofMillis(300),
                        java.time.Duration.ofSeconds(3),
                        java.time.Duration.ofSeconds(1))));
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
