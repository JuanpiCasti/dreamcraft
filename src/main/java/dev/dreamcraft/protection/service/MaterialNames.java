package dev.dreamcraft.protection.service;

import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Shared Spanish display names for common materials.
 * Falls back to a prettified enum name for unknown materials.
 */
public final class MaterialNames {

    private static final Map<String, String> ES = build();

    private MaterialNames() {}

    public static String forMaterial(Material material) {
        String es = ES.get(material.name());
        if (es != null) return es;
        String raw = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    private static Map<String, String> build() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("DIAMOND", "Diamante");
        m.put("EMERALD", "Esmeralda");
        m.put("IRON_INGOT", "Lingote de Hierro");
        m.put("GOLD_INGOT", "Lingote de Oro");
        m.put("COPPER_INGOT", "Lingote de Cobre");
        m.put("NETHERITE_INGOT", "Lingote de Netherite");
        m.put("REDSTONE", "Redstone");
        m.put("LAPIS_LAZULI", "Lapislázuli");
        m.put("COAL", "Carbón");
        m.put("OBSIDIAN", "Obsidiana");
        m.put("AMETHYST_SHARD", "Fragmento de Amatista");
        m.put("QUARTZ", "Cuarzo");
        m.put("GOLD_BLOCK", "Bloque de Oro");
        m.put("IRON_BLOCK", "Bloque de Hierro");
        m.put("DIAMOND_BLOCK", "Bloque de Diamante");
        m.put("EMERALD_BLOCK", "Bloque de Esmeralda");
        m.put("NETHERITE_BLOCK", "Bloque de Netherite");
        m.put("COPPER_BLOCK", "Bloque de Cobre");
        m.put("COAL_BLOCK", "Bloque de Carbón");
        m.put("REDSTONE_BLOCK", "Bloque de Redstone");
        m.put("LAPIS_BLOCK", "Bloque de Lapislázuli");
        m.put("GOLDEN_APPLE", "Manzana Dorada");
        m.put("ENCHANTED_GOLDEN_APPLE", "Manzana Dorada Encantada");
        return m;
    }
}
