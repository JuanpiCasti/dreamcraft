package dev.dreamcraft.protection.message;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
        return apply(raw, placeholders);
    }

    /** String list (e.g. help blocks); empty list when absent. */
    public List<String> list(String key) {
        return merged.getStringList(key);
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
