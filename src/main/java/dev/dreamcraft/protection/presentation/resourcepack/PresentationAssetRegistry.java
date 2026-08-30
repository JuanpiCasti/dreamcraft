package dev.dreamcraft.protection.presentation.resourcepack;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Vanilla CustomModelData implementation of {@link ResourcePackProvider},
 * backed by {@code presentation-assets.yml} — the contract file between this
 * plugin and the future DreamCraft resource pack worktree.
 *
 * <p>Resolution order (mirrors {@link dev.dreamcraft.protection.message.Messages}):
 * <ol>
 *   <li>{@code <dataFolder>/presentation-assets.yml} — server override</li>
 *   <li>embedded {@code presentation-assets.yml} — plugin default contract</li>
 *   <li>legacy built-in icon map / vanilla materials</li>
 * </ol>
 *
 * <p>The registry never decides gameplay: it only maps semantic keys
 * ({@code ward.icon}, {@code city.score}, …) to visual data. A missing entry,
 * a missing pack or a missing provider always degrades to plain vanilla —
 * MD §23 golden rule.
 */
public final class PresentationAssetRegistry implements ResourcePackProvider {

    private static final String FALLBACK_MATERIAL = "PAPER";

    private final boolean enabled;
    private final Map<String, IconAsset> icons = new HashMap<>();
    private final Map<String, String> sounds = new HashMap<>();
    private final Map<String, String> fonts = new HashMap<>();
    private final Map<String, String> symbols = new HashMap<>();
    /** Symbols resolved to renderable glyphs: {codepoint, Adventure font}. */
    private final Map<String, GlyphRef> symbolRefs = new HashMap<>();
    private final Map<String, String> particles = new HashMap<>();

    private PresentationAssetRegistry(boolean enabled) {
        this.enabled = enabled;
    }

    public static PresentationAssetRegistry empty() {
        return new PresentationAssetRegistry(false);
    }

    /** Loads the embedded contract and overlays the server copy when present. */
    public static PresentationAssetRegistry load(JavaPlugin plugin) {
        YamlConfiguration merged = new YamlConfiguration();
        try (InputStream in = plugin.getResource("presentation-assets.yml")) {
            if (in != null) {
                merged.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Assets] presentation-assets.yml embebido ilegible: " + e.getMessage());
        }
        File file = new File(plugin.getDataFolder(), "presentation-assets.yml");
        if (file.exists()) {
            try {
                YamlConfiguration custom = YamlConfiguration.loadConfiguration(file);
                for (String path : custom.getKeys(true)) {
                    if (custom.isString(path)) merged.set(path, custom.getString(path));
                    else if (custom.isList(path)) merged.set(path, custom.getStringList(path));
                    else if (custom.isConfigurationSection(path)) merged.set(path, custom.get(path));
                }
                plugin.getLogger().info("[Assets] overrides aplicados desde presentation-assets.yml.");
            } catch (Exception e) {
                plugin.getLogger().warning("[Assets] presentation-assets.yml del servidor inválido: " + e.getMessage());
            }
        }
        PresentationAssetRegistry registry = fromConfiguration(merged);
        return registry;
    }

    /** Testable entry point: builds a registry directly from a merged configuration. */
    public static PresentationAssetRegistry fromConfiguration(FileConfiguration cfg) {
        boolean enabled = cfg.getBoolean("options.enabled", true);
        PresentationAssetRegistry registry = new PresentationAssetRegistry(enabled);
        registry.parse(cfg);
        return registry;
    }

    /**
     * Parses the contract. Note: Bukkit's YamlConfiguration treats '.' as a
     * path separator, so {@code ward.icon:} nests to {@code icons.ward.icon}.
     * Parsing therefore walks arbitrary depth and uses the relative path below
     * each top-level section as the semantic asset key.
     */
    private void parse(FileConfiguration cfg) {
        ConfigurationSection iconSection = cfg.getConfigurationSection("icons");
        if (iconSection != null) {
            for (String path : iconSection.getKeys(true)) {
                if (!iconSection.isConfigurationSection(path)) continue;
                ConfigurationSection entry = iconSection.getConfigurationSection(path);
                if (entry == null || !entry.contains("material")) continue;
                Material material = Material.matchMaterial(entry.getString("material", FALLBACK_MATERIAL));
                if (material == null) continue;
                int cmd = entry.getInt("cmd", 0);
                Material fallback = matchOrNull(entry.getString("fallback"));
                icons.put(path, new IconAsset(material, cmd, fallback,
                        entry.getBoolean("hide-name", false)));
            }
        }
        fillSimple(cfg, "sounds", sounds);
        fillSimple(cfg, "fonts", fonts);
        fillFieldLeaves(cfg, "symbols", symbols, "glyph");
        parseSymbolRefs(cfg);
        fillSimple(cfg, "particles", particles);
    }

    /**
     * Second symbol pass: entries shaped {@code {glyph: c, font: key}} become
     * renderable {@link GlyphRef}s with the font key resolved through the
     * {@code fonts} section (e.g. {@code dc.gui → dreamcraft:gui}); an
     * unresolved font key is kept verbatim so the contract stays the single
     * source of truth.
     */
    private void parseSymbolRefs(FileConfiguration cfg) {
        ConfigurationSection section = cfg.getConfigurationSection("symbols");
        if (section == null) return;
        for (String path : section.getKeys(true)) {
            if (!section.isConfigurationSection(path)) continue;
            String glyph = blankToNull(section.getString(path + ".glyph"));
            if (glyph == null) continue;
            String rawFont = blankToNull(section.getString(path + ".font"));
            String resolvedFont = rawFont == null ? null : fonts.getOrDefault(rawFont, rawFont);
            if (resolvedFont == null) continue;
            symbolRefs.put(path, new GlyphRef(glyph, resolvedFont));
        }
    }

    private static String blankToNull(String raw) {
        return raw == null || raw.isBlank() ? null : raw;
    }

    private static Material matchOrNull(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return Material.matchMaterial(raw);
    }

    /** Sections whose entries are plain strings or {vanilla: value} maps. */
    private void fillSimple(FileConfiguration cfg, String sectionName, Map<String, String> target) {
        ConfigurationSection section = cfg.getConfigurationSection(sectionName);
        if (section == null) return;
        String prefix = sectionName + ".";
        for (String path : section.getKeys(true)) {
            if (section.isConfigurationSection(path)) continue;
            String value = leafValue(section, path, "vanilla");
            if (value != null) {
                target.put(relativize(path, prefix, "vanilla"), value);
            }
        }
    }

    /** Symbol sections use {glyph: char, font: id} (or the bare string). */
    private void fillFieldLeaves(FileConfiguration cfg, String sectionName, Map<String, String> target, String field) {
        ConfigurationSection section = cfg.getConfigurationSection(sectionName);
        if (section == null) return;
        String prefix = sectionName + ".";
        for (String path : section.getKeys(true)) {
            if (section.isConfigurationSection(path)) continue;
            if (isWrapperSibling(section, path, field)) continue;
            String value = leafValue(section, path, field);
            if (value != null) {
                target.put(relativize(path, prefix, field), value);
            }
        }
    }

    /**
     * True for metadata leaves of a wrapped entry ({@code {glyph: c, font: f}}
     * stores both {@code .glyph} and {@code .font}); only the {@code <field>}
     * leaf names the asset. Bare-string entries never qualify — their parent
     * has no {@code <field>} probe.
     */
    private static boolean isWrapperSibling(ConfigurationSection section, String path, String field) {
        int dot = path.lastIndexOf('.');
        if (dot <= 0 || path.endsWith("." + field)) return false;
        String parent = path.substring(0, dot);
        return section.isConfigurationSection(parent)
                && leafValue(section, parent + "." + field, field) != null;
    }

    private static String leafValue(ConfigurationSection section, String path, String wrapperKey) {
        Object raw = section.get(path);
        if (raw instanceof String s && !s.isBlank()) return s;
        // {vanilla: x} / {glyph: c} style: the actual value sits one level deeper
        String wrapped = section.getString(path + "." + wrapperKey);
        return wrapped != null && !wrapped.isBlank() ? wrapped : null;
    }

    /** Strips the section prefix and the optional wrapper segment ({vanilla:}/{glyph:}). */
    private static String relativize(String path, String prefix, String wrapperKey) {
        String key = path.startsWith(prefix) ? path.substring(prefix.length()) : path;
        String wrapperSuffix = "." + wrapperKey;
        if (key.endsWith(wrapperSuffix)) {
            key = key.substring(0, key.length() - wrapperSuffix.length());
        }
        return key;
    }

    // ── ResourcePackProvider ──────────────────────────────────────────────────

    @Override public String providerName() { return "vanilla-cmd"; }

    @Override public boolean isAvailable() { return enabled && !icons.isEmpty(); }

    @Override
    public Optional<PresentationIcon> icon(String assetKey) {
        IconAsset entry = icons.get(assetKey);
        if (entry == null) return Optional.empty();
        boolean cmdUsable = enabled && entry.cmd() > 0;
        if (!cmdUsable) {
            return Optional.of(PresentationIcon.vanilla(
                    entry.fallback() != null ? entry.fallback() : entry.material()));
        }
        return Optional.of(new PresentationIcon(entry.material(), entry.cmd(), null));
    }

    @Override public String sound(String assetKey) { return sounds.get(assetKey); }
    @Override public String font(String assetKey) { return fonts.get(assetKey); }
    @Override public String symbol(String assetKey) { return symbols.get(assetKey); }
    @Override public String particle(String assetKey) { return particles.get(assetKey); }

    /** Renderable glyph ({@link GlyphRef}) for a symbol key, if defined. */
    public Optional<GlyphRef> symbolRef(String assetKey) {
        return Optional.ofNullable(symbolRefs.get(assetKey));
    }

    /** Raw contract entry for an icon key (status output/tests). */
    public Optional<IconAsset> iconAsset(String assetKey) {
        return Optional.ofNullable(icons.get(assetKey));
    }

    /** Number of registered icon entries (status output/tests). */
    public int iconCount() { return icons.size(); }

    /**
     * Resolves the target Bukkit Material for a key based on viewer resource pack state.
     */
    public Material resolveMaterial(String assetKey, boolean viewerHasResourcePack) {
        IconAsset entry = icons.get(assetKey);
        if (entry == null) return null;
        if (viewerHasResourcePack && entry.cmd() > 0) {
            return entry.material();
        }
        return entry.fallback() != null ? entry.fallback() : entry.material();
    }

    /**
     * Applies the asset to a rendered stack when appropriate:
     * CMD when the viewer loaded the resource pack; otherwise swaps in the
     * configured vanilla fallback material (MD §9). No-op when the key has no
     * entry or nothing differs — legacy menus keep working untouched.
     */
    public void applyTo(String assetKey, ItemStack stack, ItemMeta meta, boolean viewerHasResourcePack) {
        if (!enabled || meta == null || stack == null || assetKey == null) return;
        IconAsset entry = icons.get(assetKey);
        if (entry == null) return;
        if (viewerHasResourcePack && entry.cmd() > 0) {
            if (!entry.material().equals(stack.getType())) {
                stack.setType(entry.material());
            }
            meta.setCustomModelData(entry.cmd());
            // The CMD texture paints its own label — hide the vanilla item name
            if (entry.hideName()) meta.displayName(net.kyori.adventure.text.Component.space());
            return;
        }
        Material fallback = entry.fallback() != null ? entry.fallback() : entry.material();
        if (!fallback.equals(stack.getType())) {
            stack.setType(fallback);
        }
        meta.setCustomModelData(null);
    }
}
