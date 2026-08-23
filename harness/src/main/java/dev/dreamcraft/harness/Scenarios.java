package dev.dreamcraft.harness;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.FormattedCommandAlias;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Scenario catalog v1.
 *
 * <p>ASSERTED scenarios encode behaviour we have already agreed on; PROBE
 * scenarios only record observed behaviour so undefined areas surface in the
 * report as gap candidates instead of silently passing.
 *
 * <p>Expectations anchored on stable phrases from messages.yml. When the
 * vocabulary changes, update the expectation here — that friction is
 * intentional (text is part of the contract).
 */
final class Scenarios {

    private Scenarios() {}

    static final String UUID_INEXISTENTE = "00000000-0000-0000-0000-00000000abcd";

    static List<Scenario> catalog(HarnessPlugin plugin, CapturingSenderFactory senders) {
        List<Scenario> list = new ArrayList<>();

        // ── Arranque ──────────────────────────────────────────────────────────
        list.add(Scenario.asserted("plugin-enabled", "arranque", () -> {
            Plugin dcp = Bukkit.getPluginManager().getPlugin("DreamCraftProtection");
            if (dcp == null) {
                return Outcome.fail("DreamCraftProtection presente y habilitado",
                        "ausente", "El plugin bajo test no cargó: revisar errores de arranque del server");
            }
            return dcp.isEnabled()
                    ? Outcome.pass()
                    : Outcome.fail("habilitado", "deshabilitado",
                    "Cargó pero falló el onEnable: ver logs del server");
        }));

        // ── Registro de comandos ──────────────────────────────────────────────
        String[][] canonicas = {
                {"ward", "dreamcraft.ward.use"},
                {"city", "dreamcraft.city.use"},
                {"estate", "dreamcraft.estate.use"},
                {"protection", "dreamcraft.protection.use"},
        };
        for (String[] root : canonicas) {
            String name = root[0], perm = root[1];
            list.add(Scenario.asserted("raiz-canonica-" + name, "comandos", () -> {
                PluginCommand cmd = Bukkit.getPluginCommand(name);
                if (cmd == null) {
                    return Outcome.fail("/" + name + " registrado con permiso " + perm,
                            "no registrado", "plugin.yml no declara el comando o el registro falló");
                }
                return perm.equals(cmd.getPermission())
                        ? Outcome.pass()
                        : Outcome.fail("permiso " + perm, "permiso " + cmd.getPermission(),
                        "El permiso del comando cambió en plugin.yml");
            }));
        }

        // Regression: commands.yml aliases used to clobber /sync etc. after boot
        String[][] versionadas = {{"sync", "ward"}, {"nexo", "estate"}, {"matriz", "city"}, {"proteccion", "protection"}};
        for (String[] pair : versionadas) {
            String display = pair[0], canonico = pair[1];
            list.add(Scenario.asserted("versionada-" + display + "-propiedad", "comandos", () -> {
                PluginCommand cmd = Bukkit.getPluginCommand(display);
                PluginCommand canonCmd = Bukkit.getPluginCommand(canonico);
                if (cmd == null) {
                    return Outcome.fail("/" + display + " es un PluginCommand de DreamCraftProtection",
                            "no registrada", "command-names en config.yml o DynamicCommands falló");
                }
                CommandMap map = Bukkit.getCommandMap();
                Object known = map instanceof org.bukkit.command.SimpleCommandMap simple
                        ? simple.getKnownCommands().get(display) : null;
                if (known instanceof FormattedCommandAlias) {
                    return Outcome.fail("el nombre /" + display + " pertenece al plugin",
                            "un FormattedCommandAlias ocupa el nombre (commands.yml)",
                            "Un alias de commands.yml pisó el registro versionado tras el boot "
                                    + "— el guard de ServerLoadEvent no reasumió la propiedad");
                }
                boolean mismoExecutor = cmd.getExecutor() != null && canonCmd != null
                        && cmd.getExecutor().getClass().getName()
                        .equals(canonCmd.getExecutor().getClass().getName());
                return mismoExecutor
                        ? Outcome.pass()
                        : Outcome.fail("executor compartido con /" + canonico,
                        "executor=" + (cmd.getExecutor() == null ? "null" : cmd.getExecutor().getClass().getName()),
                        "DynamicCommands no copió el executor canónico");
            }));
        }

        // Regression: level-1 tab completion of the renamed roots
        list.add(Scenario.asserted("tab-completer-sync-nivel1", "comandos", () ->
                tabLevel1Contains(Bukkit.getPluginCommand("sync"), Persona.jugadorBasico(), senders)));
        list.add(Scenario.asserted("tab-completer-ward-nivel1", "comandos", () ->
                tabLevel1Contains(Bukkit.getPluginCommand("ward"), Persona.jugadorBasico(), senders)));

        // Regression: «admin» only surfaces for permission holders of the renamed roots
        list.add(Scenario.asserted("tab-completer-sync-admin-por-permiso", "comandos", () ->
                tabLevel1AdminVisibility(Bukkit.getPluginCommand("sync"), Persona.adminJugador(), senders)));
        list.add(Scenario.asserted("tab-completer-matriz-admin-por-permiso", "comandos", () ->
                tabLevel1AdminVisibility(Bukkit.getPluginCommand("matriz"), Persona.adminJugador(), senders)));

        // ── Dispatch + permisos ───────────────────────────────────────────────
        list.add(Scenario.asserted("consola-rechaza-comando-jugador", "dispatch", () -> {
            String out = dispatch(senders, Persona.console(), "sync");
            return containsAny(out, new String[]{"jugadores"},
                    "/sync desde consola debería responder 'solo puede ser usado por jugadores'",
                    "WardCommand no está guardando sender instanceof Player antes de castear");
        }));
        list.add(Scenario.asserted("sin-permiso-bloquea-dispatch", "dispatch", () -> {
            String out = dispatch(senders, Persona.visitante(), "sinc info");
            return out.isBlank()
                    ? Outcome.fail("mensaje de falta de permiso", "(ninguna respuesta capturada)",
                    "El gate de plugin.yml respondió por otro canal o el mensaje usa spigot()")
                    : Outcome.pass(); // cualquier texto sirve: lo que importa es que NO ejecutó lógica real
        }));

        // ── Config desplegada ─────────────────────────────────────────────────
        list.add(Scenario.asserted("config-overrides-desplegadas", "configuración", () -> {
            File messagesOverride = new File(
                    new File(plugin.getDataFolder().getParentFile(), "DreamCraftProtection"),
                    "messages.yml");
            if (!messagesOverride.isFile()) {
                return Outcome.fail("plugins/DreamCraftProtection/messages.yml existe",
                        "no existe", "El sidecar config-sync no copió plugin-configs → data/plugins");
            }
            try {
                String text = Files.readString(messagesOverride.toPath());
                return text.contains("admin-zone")
                        ? Outcome.pass()
                        : Outcome.fail("clave estate.admin-zone en overrides",
                        "la clave no está", "messages.yml se regeneró sin las claves nuevas");
            } catch (Exception e) {
                return Outcome.fail("leer messages.yml", e.getClass().getSimpleName(), e.getMessage());
            }
        }));

        // ── Consola / RCON (contrato v2: ops admin sin ubicación) ─────────────
        list.add(Scenario.asserted("consola-proteccion-reload", "dispatch", () -> {
            String out = dispatch(senders, Persona.console(), "proteccion reload");
            if (out.contains("jugadores")) {
                return Outcome.fail("ejecutable desde consola por admins", out,
                        "ProtectionCommand sigue bloqueando senders no-Player antes del whitelist");
            }
            return containsAny(out, new String[]{"recargada"},
                    "confirmación de recarga", "handleReload cambió su frase de confirmación");
        }));
        list.add(Scenario.asserted("consola-proteccion-integraciones", "dispatch", () -> {
            String out = dispatch(senders, Persona.console(), "proteccion integrations");
            if (out.contains("jugadores")) {
                return Outcome.fail("ejecutable desde consola por admins", out,
                        "handleIntegrations no acepta CommandSender");
            }
            return containsAny(out, new String[]{"Integration Registry"},
                    "listado de integraciones", "La salida de handleIntegraciones cambió de formato");
        }));
        list.add(Scenario.asserted("nexo-disband-id-inexistente", "dispatch", () -> {
            String out = dispatch(senders, Persona.console(), "nexo disband " + UUID_INEXISTENTE);
            if (out.contains("jugadores")) {
                return Outcome.fail("consola admin puede disolver por id", out,
                        "EstateCommand sigue exigiendo Player para disband");
            }
            long hits = out.lines().filter(l -> l.contains("encontrada")).count();
            return hits == 1
                    ? Outcome.pass()
                    : Outcome.fail("exactamente un error 'Instancia no encontrada'",
                    hits + " ocurrencias: " + out,
                    "La ruta de consola duplica mensajes o cambió el texto de resolución");
        }));

        // ── Receta del Núcleo (crafting configurable) ─────────────────────────
        // Contrato: existe una ShapedRecipe cuyo resultado lleva el PDC tag del
        // Núcleo (namespace derivado del nombre del plugin) — lo crafteado funda
        // Ward exactamente igual que lo reclamado/entregado. El tema por defecto
        // es 8 diamantes + estrella del Nether central (config ward.recipe).
        list.add(Scenario.asserted("receta-nucleo-registrada-taggeada", "crafting", () -> {
            org.bukkit.NamespacedKey tagKey =
                    new org.bukkit.NamespacedKey("dreamcraftprotection", "ward-beacon");
            org.bukkit.inventory.ShapedRecipe found = null;
            java.util.Iterator<org.bukkit.inventory.Recipe> it = Bukkit.recipeIterator();
            while (it.hasNext()) {
                org.bukkit.inventory.Recipe r = it.next();
                if (!(r instanceof org.bukkit.inventory.ShapedRecipe shaped)) continue;
                var meta = shaped.getResult().getItemMeta();
                if (meta == null) continue;
                String tag = meta.getPersistentDataContainer()
                        .get(tagKey, org.bukkit.persistence.PersistentDataType.STRING);
                if (tag != null) {
                    found = shaped;
                    break;
                }
            }
            if (found == null) {
                return Outcome.fail(
                        "ShapedRecipe con resultado taggeado (PDC dreamcraftprotection:ward-beacon)",
                        "ninguna receta produce el Núcleo",
                        "onEnable no registró la receta o el resultado no lleva el tag");
            }

            // El material del resultado debe coincidir con ward.material desplegado
            File cfgFile = new File(new File(plugin.getDataFolder().getParentFile(),
                    "DreamCraftProtection"), "config.yml");
            if (cfgFile.isFile()) {
                try {
                    var cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(cfgFile);
                    Material expected = Material.matchMaterial(cfg.getString("ward.material", "BEACON"));
                    if (expected != null && found.getResult().getType() != expected) {
                        return Outcome.fail("resultado de la receta = " + expected,
                                found.getResult().getType().name(),
                                "el resultado no usa el material configurado en ward.material");
                    }
                } catch (Exception e) {
                    return Outcome.fail("leer config.yml desplegada", e.toString(), null);
                }
            }

            // Tema acordado v1: 8 diamantes + 1 estrella del Nether central
            Map<Material, Integer> counts = new java.util.EnumMap<>(Material.class);
            for (org.bukkit.inventory.RecipeChoice choice : found.getChoiceMap().values()) {
                if (choice instanceof org.bukkit.inventory.RecipeChoice.MaterialChoice mc) {
                    for (Material m : mc.getChoices()) counts.merge(m, 1, Integer::sum);
                }
            }
            boolean tema = counts.getOrDefault(Material.DIAMOND, 0) == 8
                    && counts.getOrDefault(Material.NETHER_STAR, 0) == 1;
            return tema
                    ? Outcome.pass()
                    : Outcome.fail("tema 8 diamantes + estrella del Nether",
                    counts.toString(),
                    "cambió la receta por defecto de ward.recipe (fricción intencional: actualizar la expectativa)");
        }));

        // ── PROBES (registro de comportamiento, no aserciones) ────────────────
        list.add(Scenario.probe("luckperms-listgroups", "observado", () ->
                Outcome.probe(dispatch(senders, Persona.console(), "lp listgroups"))));
        list.add(Scenario.probe("estado-persistencia-inicial", "persistencia", () -> {
            StringBuilder sb = new StringBuilder();
            File dataDir = new File(plugin.getDataFolder().getParentFile(), "DreamCraftProtection");
            for (String f : new String[]{"wards.yml", "cities.yml", "estates.yml", "claims.yml"}) {
                File file = new File(dataDir, f);
                long size = file.isFile() ? file.length() : -1;
                sb.append(f).append("=").append(size < 0 ? "ausente" : size + "b").append("; ");
            }
            return Outcome.probe(sb.toString());
        }));
        list.add(Scenario.probe("city-delete-sin-ciudad", "observado", () ->
                Outcome.probe(dispatch(senders, Persona.jugadorBasico(), "matriz delete"))));

        return list;
    }

    private static Outcome tabLevel1Contains(PluginCommand cmd, Persona persona,
                                             CapturingSenderFactory senders) {
        if (cmd == null) {
            return Outcome.fail("comando registrado para su completer", "no registrado", null);
        }
        TabCompleter completer = cmd.getTabCompleter();
        if (completer == null) {
            return Outcome.fail("tabCompleter cableado en /" + cmd.getName(),
                    "null", "setTabCompleter no se llamó durante el onEnable");
        }
        try {
            List<String> tokens = completer.onTabComplete(
                    senders.forPersona(persona).sender(), cmd, cmd.getName(), new String[]{""});
            boolean ok = tokens != null && tokens.contains("create") && tokens.contains("info");
            return ok
                    ? Outcome.pass()
                    : Outcome.fail("nivel 1 contiene create/info",
                    tokens == null ? "null (→ Bukkit sugiere jugadores)" : String.join(",", tokens),
                    "Completer devolvió lista vacía o nula: cae al fallback de nombres de jugador");
        } catch (Exception e) {
            return Outcome.fail("invocar completer", e.toString(), "Excepción dentro del onTabComplete");
        }
    }

    /**
     * Level-1 tab completion surfaces the hidden «admin» subcommand ONLY to
     * holders of its admin permission: present for the admin persona, absent
     * for a regular player.
     */
    private static Outcome tabLevel1AdminVisibility(PluginCommand cmd, Persona admin,
                                                    CapturingSenderFactory senders) {
        if (cmd == null) {
            return Outcome.fail("comando registrado para su completer", "no registrado", null);
        }
        TabCompleter completer = cmd.getTabCompleter();
        if (completer == null) {
            return Outcome.fail("tabCompleter cableado en /" + cmd.getName(),
                    "null", "setTabCompleter no se llamó durante el onEnable");
        }
        try {
            List<String> adminTokens = completer.onTabComplete(
                    senders.forPersona(admin).sender(), cmd, cmd.getName(), new String[]{""});
            if (adminTokens == null || !adminTokens.contains("admin")) {
                return Outcome.fail("'admin' visible para el admin",
                        adminTokens == null ? "null (→ Bukkit sugiere jugadores)" : String.join(",", adminTokens),
                        "SubcommandSpec.admin oculta demasiado: ni un admin ve la entrada");
            }
            List<String> playerTokens = completer.onTabComplete(
                    senders.forPersona(Persona.jugadorBasico()).sender(), cmd, cmd.getName(), new String[]{""});
            boolean oculto = playerTokens == null || !playerTokens.contains("admin");
            return oculto
                    ? Outcome.pass()
                    : Outcome.fail("'admin' oculto para jugadores básicos",
                    String.join(",", playerTokens),
                    "El completer no filtra SubcommandSpec.admin por permiso");
        } catch (Exception e) {
            return Outcome.fail("invocar completer", e.toString(), "Excepción dentro del onTabComplete");
        }
    }

    /**
     * Dispatches {@code line} as the persona.
     *
     * <p>Paper 26 routes Bukkit#dispatchCommand through Brigadier and requires
     * every sender to be convertible to a vanilla CommandSourceStack
     * ({@code VanillaCommandWrapper.getListener}), which synthetic senders are
     * not. Our own commands are invoked directly via
     * {@link PluginCommand#execute} — the same permission gate and executor
     * the real pipeline reaches. Third-party/vanilla commands fall back to a
     * real console dispatch whose output lands in the server log.
     */
    private static String dispatch(CapturingSenderFactory senders, Persona persona, String line) {
        CapturingSenderFactory.CapturedSender cs = senders.forPersona(persona);
        String[] parts = line.trim().split("\s+");
        PluginCommand cmd = Bukkit.getPluginCommand(parts[0]);
        if (cmd == null) {
            boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line);
            return "(comando externo '" + parts[0] + "' despachado como consola real; ok=" + ok
                    + ", salida en el log del server)";
        }
        String[] args = parts.length > 1
                ? java.util.Arrays.copyOfRange(parts, 1, parts.length)
                : new String[0];
        cmd.execute(cs.sender(), parts[0], args);
        return cs.output();
    }

    private static Outcome containsAny(String actual, String[] options,
                                       String expectedDesc, String hint) {
        for (String option : options) {
            if (actual.contains(option)) return Outcome.pass();
        }
        return Outcome.fail(expectedDesc, actual.isBlank() ? "(vacío)" : actual, hint);
    }
}
