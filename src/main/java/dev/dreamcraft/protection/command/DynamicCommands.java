package dev.dreamcraft.protection.command;

import dev.dreamcraft.protection.config.CommandNames;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Registers the versioned root names (command-names:) as first-class plugin
 * commands sharing the canonical executor + tab completer.
 *
 * <p>Why not rely on Bukkit's {@code commands.yml} aliases alone: a
 * {@code FormattedCommandAlias} ({@code sync: ward $1-}) does not forward tab
 * completions, so /sync showed no suggestions. Registering the display names
 * as real commands fixes suggestions, /help entries and permission gating
 * while keeping the canonical roots (/ward…) fully functional. A commands.yml
 * alias pointing at the same name is simply overridden in the command map.
 */
public final class DynamicCommands {

    private static final String FALLBACK_PREFIX = "dreamcraft";

    private DynamicCommands() {}

    /**
     * @param canonical canonical root name → its wired PluginCommand
     *                  (executor and tab completer already set)
     */
    public static void registerVersionedRoots(Plugin plugin,
                                              Logger logger,
                                              Map<String, PluginCommand> canonical) {
        CommandMap map = Bukkit.getCommandMap();
        Constructor<PluginCommand> constructor;
        try {
            constructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            constructor.setAccessible(true);
        } catch (NoSuchMethodException e) {
            logger.warning("[DreamCraft] No se pudo preparar el registro de comandos versionados: "
                    + e.getMessage());
            return;
        }

        for (Map.Entry<String, String> entry : CommandNames.all().entrySet()) {
            PluginCommand source = canonical.get(entry.getKey());
            if (source == null) continue; // root disabled/absent in this deployment
            String display = entry.getValue().toLowerCase(java.util.Locale.ROOT);
            if (display.equalsIgnoreCase(source.getName())) continue; // same name — nothing to add

            try {
                PluginCommand versioned = constructor.newInstance(display, plugin);
                versioned.setDescription(source.getDescription());
                versioned.setUsage(source.getUsage());
                versioned.setPermission(source.getPermission());
                versioned.setExecutor(source.getExecutor());
                versioned.setTabCompleter(source.getTabCompleter());
                map.register(FALLBACK_PREFIX, versioned);
                logger.info("[DreamCraft] Comando versionado registrado: /" + display
                        + " → mecánica /" + source.getName());
            } catch (Exception e) {
                logger.warning("[DreamCraft] Falló el registro de /" + display + ": " + e.getMessage());
            }
        }
    }
}
