package dev.dreamcraft.protection.command;

import dev.dreamcraft.protection.config.CommandNames;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.FormattedCommandAlias;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
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
 * while keeping the canonical roots (/ward…) fully functional.
 *
 * <p>Timing matters: {@code MinecraftServer} loads commands.yml aliases via
 * {@code SimpleCommandMap.registerServerAliases()} AFTER plugins enable, and
 * that call unconditionally {@code put()}s each alias into the known-commands
 * map — clobbering whatever we registered during {@code onEnable}. The alias
 * then owns /display and tab completion falls back to player-name suggestions.
 * {@link VersionedRootGuardListener} therefore re-asserts ownership on
 * {@link ServerLoadEvent}, restoring our prefixed instance under the bare name.
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
                if (map instanceof SimpleCommandMap simple) {
                    Command previous = simple.getKnownCommands().get(display);
                    if (previous instanceof FormattedCommandAlias
                            && previous.getLabel().equalsIgnoreCase(display)) {
                        previous.unregister(simple);
                        simple.getKnownCommands().remove(display);
                        logger.info("[DreamCraft] Alias de commands.yml '/" + display
                                + "' desalojado para el registro versionado");
                    }
                }
                PluginCommand versioned = constructor.newInstance(display, plugin);
                versioned.setDescription(source.getDescription());
                versioned.setUsage(source.getUsage());
                versioned.setPermission(source.getPermission());
                versioned.setExecutor(source.getExecutor());
                versioned.setTabCompleter(source.getTabCompleter());
                if (!map.register(FALLBACK_PREFIX, versioned)) {
                    logger.warning("[DreamCraft] El registro de /" + display
                            + " fue rechazado por el command map");
                    continue;
                }
                logger.info("[DreamCraft] Comando versionado registrado: /" + display
                        + " → mecánica /" + source.getName());
            } catch (Exception e) {
                logger.warning("[DreamCraft] Falló el registro de /" + display + ": " + e.getMessage());
            }
        }
    }

    /** Re-asserts versioned roots after commands.yml aliases load; see class javadoc. */
    public static void reassertVersionedRoots(Logger logger) {
        CommandMap map = Bukkit.getCommandMap();
        if (!(map instanceof SimpleCommandMap simple)) return;
        Map<String, Command> known = simple.getKnownCommands();
        for (Map.Entry<String, String> entry : CommandNames.all().entrySet()) {
            String display = entry.getValue().toLowerCase(java.util.Locale.ROOT);
            Command ours = known.get(FALLBACK_PREFIX + ":" + display);
            if (!(ours instanceof PluginCommand)) continue;
            Command current = known.get(display);
            if (current == ours) continue;
            if (!(current instanceof FormattedCommandAlias)) continue; // someone else's command
            current.unregister(simple);
            known.put(display, ours);
            logger.info("[DreamCraft] /" + display
                    + " reasumido tras la carga de aliases de commands.yml");
        }
    }

    /** Registers the {@link ServerLoadEvent} hook that calls {@link #reassertVersionedRoots}. */
    public static void registerLoadGuard(Plugin plugin, Logger logger) {
        Bukkit.getPluginManager().registerEvents(new VersionedRootGuardListener(logger), plugin);
    }

    static final class VersionedRootGuardListener implements Listener {
        private final Logger logger;

        VersionedRootGuardListener(Logger logger) {
            this.logger = logger;
        }

        @EventHandler(priority = EventPriority.NORMAL)
        public void onServerLoad(ServerLoadEvent event) {
            if (event.getType() == ServerLoadEvent.LoadType.STARTUP) {
                reassertVersionedRoots(logger);
            }
        }
    }
}
