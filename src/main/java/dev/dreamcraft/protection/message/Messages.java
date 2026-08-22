package dev.dreamcraft.protection.message;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Central message catalog (single locale: es).
 *
 * <p>Resolution order per key:
 * <ol>
 *   <li>{@code <dataFolder>/messages.yml} — server override (copied by config-sync,
 *       so every server in the fleet can rebrand texts without recompiling)</li>
 *   <li>embedded {@code messages.yml} — plugin default</li>
 *   <li>code fallback — the literal passed at the call site</li>
 * </ol>
 *
 * <p>Values support legacy {@code &} color codes and {@code {placeholder}} substitution.
 */
public final class Messages {

    private static volatile Messages INSTANCE = new Messages(new YamlConfiguration());

    private final FileConfiguration merged;

    /** Public for tests and advanced wiring; prefer {@link #load(JavaPlugin)}. */
    public Messages(FileConfiguration merged) {
        this.merged = merged;
    }

    public static Messages get() {
        return INSTANCE;
    }

    /** Translates a key with {@code {placeholder}} args; falls back to the given literal. */
    public String tr(String key, String fallback, Object... placeholders) {
        String raw = merged.getString(key);
        if (raw == null || raw.isBlank()) raw = fallback;
        return applyVersionedCommands(apply(raw, placeholders));
    }

    /**
     * Substitutes the versioned root-command tokens ({@code {cmd.ward}},
     * {@code {cmd.city}}, …) so catalog texts always show this server's
     * player-facing command names (lore coherence).
     */
    public static String applyVersionedCommands(String template) {
        if (template == null || !template.contains("{cmd.")) return template;
        return template
                .replace("{cmd.ward}", dev.dreamcraft.protection.config.CommandNames.root("ward"))
                .replace("{cmd.city}", dev.dreamcraft.protection.config.CommandNames.root("city"))
                .replace("{cmd.estate}", dev.dreamcraft.protection.config.CommandNames.root("estate"))
                .replace("{cmd.protection}", dev.dreamcraft.protection.config.CommandNames.root("protection"));
    }

    /**
     * String list (e.g. help blocks); empty list when absent.
     * Each line gets the versioned root-command substitution applied
     * ({@code {cmd.ward}} → raíz visible del servidor).
     */
    public List<String> list(String key) {
        List<String> raw = merged.getStringList(key);
        List<String> out = new ArrayList<>(raw.size());
        for (String line : raw) {
            out.add(applyVersionedCommands(line));
        }
        return out;
    }

    /** Replaces {@code {name}} pairs in a template. */
    public static String apply(String template, Object... kv) {
        if (kv == null || kv.length % 2 != 0) return template;
        String out = template;
        for (int i = 0; i < kv.length; i += 2) {
            out = out.replace("{" + kv[i] + "}", String.valueOf(kv[i + 1]));
        }
        return out;
    }

    /**
     * Loads the embedded defaults, overlays the server copy and installs the instance.
     * Called from onEnable and from /protection reload.
     */
    public static void load(JavaPlugin plugin) {
        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in != null) {
                defaults.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Messages] messages.yml embebido ilegible: " + e.getMessage());
        }
        File file = new File(plugin.getDataFolder(), "messages.yml");
        FileConfiguration merged = defaults;
        if (file.exists()) {
            try {
                YamlConfiguration custom = YamlConfiguration.loadConfiguration(file);
                int applied = 0;
                for (String path : custom.getKeys(true)) {
                    if (custom.isString(path)) {
                        merged.set(path, custom.getString(path));
                        applied++;
                    } else if (custom.isList(path)) {
                        merged.set(path, custom.getStringList(path));
                        applied++;
                    }
                }
                if (applied > 0) {
                    plugin.getLogger().info("[Messages] " + applied + " override(s) aplicados desde messages.yml.");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[Messages] messages.yml del servidor inválido: " + e.getMessage());
            }
        }
        INSTANCE = new Messages(merged);
        dev.dreamcraft.protection.command.CommandMessages.reloadPrefixes(merged);
    }
}
