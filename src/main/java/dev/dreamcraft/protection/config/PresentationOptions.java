package dev.dreamcraft.protection.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;

/**
 * Presentation-layer selection loaded from config.yml:
 *
 * <pre>
 * menus:
 *   provider: auto      # auto | vanilla | rp
 *   custom-title: true  # glyph background titles from the resource pack
 * </pre>
 *
 * <ul>
 *   <li><b>vanilla</b> — pure Bukkit inventories, never applies CMD.</li>
 *   <li><b>rp</b> — always applies CustomModelData from presentation-assets.yml
 *       (servers that ship the pack as mandatory).</li>
 *   <li><b>auto</b> — CMD only for players whose resource pack loaded;
 *       everyone else gets the configured vanilla fallback (MD §9).</li>
 * </ul>
 */
public record PresentationOptions(Mode assetMode, boolean customTitle) {

    public enum Mode { VANILLA, RP, AUTO }

    public static PresentationOptions load(FileConfiguration cfg) {
        String raw = cfg.getString("menus.provider", "auto");
        Mode mode = switch (raw == null ? "auto" : raw.toLowerCase(Locale.ROOT)) {
            case "vanilla" -> Mode.VANILLA;
            case "rp", "resource-pack", "resourcepack" -> Mode.RP;
            default -> Mode.AUTO;
        };
        boolean customTitle = cfg.getBoolean("menus.custom-title", true);
        return new PresentationOptions(mode, customTitle);
    }
}
