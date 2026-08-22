package dev.dreamcraft.protection.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Per-server command customization loaded from config.yml:
 *
 * <pre>
 * commands:
 *   ward:
 *     subcommands:
 *       create:
 *         aliases: [fundar]
 *       menu:
 *         enabled: false
 * </pre>
 *
 * <p>Aliases extend tab completion and dispatch without recompiling; a disabled
 * subcommand resolves to "unknown" so servers can hide features they don't use.
 */
public record CommandOptions(Map<String, Map<String, SubEntry>> roots) {

    public record SubEntry(List<String> aliases, boolean enabled) {
    }

    public static CommandOptions empty() {
        return new CommandOptions(Map.of());
    }

    public static CommandOptions load(FileConfiguration config) {
        ConfigurationSection commands = config.getConfigurationSection("commands");
        if (commands == null) return empty();
        Map<String, Map<String, SubEntry>> roots = new HashMap<>();
        for (String rootKey : commands.getKeys(false)) {
            ConfigurationSection subs = commands.getConfigurationSection(rootKey + ".subcommands");
            if (subs == null) continue;
            Map<String, SubEntry> entries = new HashMap<>();
            for (String subKey : subs.getKeys(false)) {
                ConfigurationSection entry = subs.getConfigurationSection(subKey);
                List<String> aliases = entry == null ? List.of() : entry.getStringList("aliases");
                boolean enabled = entry == null || entry.getBoolean("enabled", true);
                entries.put(subKey.toLowerCase(Locale.ROOT), new SubEntry(List.copyOf(aliases), enabled));
            }
            roots.put(rootKey.toLowerCase(Locale.ROOT), Map.copyOf(entries));
        }
        return new CommandOptions(Map.copyOf(roots));
    }

    public List<String> aliases(String root, String subcommand) {
        Map<String, SubEntry> subs = roots.get(root.toLowerCase(Locale.ROOT));
        if (subs == null) return List.of();
        SubEntry entry = subs.get(subcommand.toLowerCase(Locale.ROOT));
        return entry == null ? List.of() : entry.aliases();
    }

    public boolean isEnabled(String root, String subcommand) {
        Map<String, SubEntry> subs = roots.get(root.toLowerCase(Locale.ROOT));
        if (subs == null) return true;
        SubEntry entry = subs.get(subcommand.toLowerCase(Locale.ROOT));
        return entry == null || entry.enabled();
    }
}
