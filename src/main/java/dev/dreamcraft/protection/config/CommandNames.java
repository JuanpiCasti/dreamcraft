package dev.dreamcraft.protection.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Versioned root-command names shown to players.
 *
 * <p><b>Distribution-neutral by design:</b> with no configuration the plugin
 * uses its canonical roots (/ward, /city, /estate, /protection) everywhere.
 * Each server rebrands by adding to its OWN config.yml:
 *
 * <pre>
 * command-names:
 *   ward: sync      # must match the alias defined in commands.yml
 *   city: matriz
 *   estate: nexo
 * </pre>
 *
 * <p>{@link dev.dreamcraft.protection.message.Messages} substitutes the
 * {@code {cmd.ward}}-style placeholders automatically; code uses
 * {@link #cmd(String, String)} for inline strings. Unconfigured domains fall
 * back to their canonical name.
 */
public final class CommandNames {

    /** Identity defaults — a bare deployment behaves exactly like vanilla naming. */
    private static volatile Map<String, String> roots = Map.of(
            "ward", "ward",
            "city", "city",
            "estate", "estate",
            "protection", "protection"
    );

    private CommandNames() {}

    /** Loads overrides from the {@code command-names} section (missing keys keep defaults). */
    public static void install(FileConfiguration cfg) {
        ConfigurationSection section = cfg == null ? null : cfg.getConfigurationSection("command-names");
        if (section == null) return;
        Map<String, String> merged = new HashMap<>(roots);
        for (String key : section.getKeys(false)) {
            String value = section.getString(key);
            if (value != null && !value.isBlank()) {
                merged.put(key.toLowerCase(Locale.ROOT), value);
            }
        }
        roots = Map.copyOf(merged);
    }

    /** Display root for a logical domain ("ward" → "sync"). */
    public static String root(String domain) {
        return roots.getOrDefault(
                domain == null ? "" : domain.toLowerCase(Locale.ROOT),
                domain == null ? "" : domain);
    }

    /** Immutable view of every domain → display-root mapping. */
    public static Map<String, String> all() {
        return roots;
    }

    /**
     * Renders a full player-facing command string:
     * {@code cmd("ward", "rename <nombre>")} → {@code "/sync rename <nombre>"}.
     */
    public static String cmd(String domain, String argsAndSubcommand) {
        String base = "/" + root(domain);
        return argsAndSubcommand == null || argsAndSubcommand.isBlank()
                ? base
                : base + " " + argsAndSubcommand;
    }
}
