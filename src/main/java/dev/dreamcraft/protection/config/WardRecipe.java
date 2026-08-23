package dev.dreamcraft.protection.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Configurable shaped recipe that crafts the tagged Ward core item
 * ({@code ward.recipe} in config.yml).
 *
 * <p>Default thematic shape: 8 diamonds around a nether star:
 * <pre>
 *   D D D
 *   D N D
 *   D D D
 * </pre>
 *
 * <p>{@code enabled: false} keeps the recipe unregistered — the core then only
 * enters the economy via the one-time free claim, admin {@code give} or drops.
 */
public record WardRecipe(boolean enabled, List<String> shape, Map<Character, Material> ingredients) {

    public static final WardRecipe DEFAULT = new WardRecipe(
            true,
            List.of("DDD", "DND", "DDD"),
            Map.of('D', Material.DIAMOND, 'N', Material.NETHER_STAR));

    /** Loads {@code ward.recipe}; falls back to the thematic defaults per missing piece. */
    public static WardRecipe load(ConfigurationSection ward) {
        ConfigurationSection section = ward == null ? null : ward.getConfigurationSection("recipe");
        if (section == null) return DEFAULT;

        boolean enabled = section.getBoolean("enabled", true);
        List<String> shape = section.getStringList("shape");
        if (shape.isEmpty()) shape = DEFAULT.shape();

        Map<Character, Material> ingredients = new LinkedHashMap<>();
        ConfigurationSection ingSection = section.getConfigurationSection("ingredients");
        if (ingSection != null) {
            for (String key : ingSection.getKeys(false)) {
                if (key.length() != 1) continue;
                Material material = Material.matchMaterial(String.valueOf(ingSection.get(key)));
                if (material != null) {
                    ingredients.put(Character.toUpperCase(key.charAt(0)), material);
                }
            }
        }
        if (ingredients.isEmpty()) ingredients.putAll(DEFAULT.ingredients());

        return new WardRecipe(enabled, List.copyOf(shape), java.util.Collections.unmodifiableMap(ingredients));
    }

    /**
     * @return null when Bukkit would accept this shape/ingredients pair,
     *         otherwise a human-readable reason (logged at boot instead of crashing).
     */
    public String validationError() {
        if (shape.isEmpty() || shape.size() > 3) return "la shape debe tener entre 1 y 3 filas";
        int width = shape.get(0).length();
        if (width < 1 || width > 3) return "cada fila debe tener entre 1 y 3 símbolos";
        Set<Character> used = new HashSet<>();
        for (String row : shape) {
            if (row.length() != width) return "todas las filas deben tener el mismo ancho";
            for (char c : row.toCharArray()) {
                if (c == ' ') continue;
                char upper = Character.toUpperCase(c);
                if (!ingredients.containsKey(upper)) return "falta el material para el símbolo '" + c + "'";
                used.add(upper);
            }
        }
        for (Character symbol : ingredients.keySet()) {
            if (!used.contains(symbol)) return "el símbolo '" + symbol + "' no aparece en la shape";
        }
        return null;
    }

    /** Shape rows normalized to uppercase symbols (Bukkit requirement). */
    public String[] bukkitShape() {
        return shape.stream()
                .map(row -> row.toUpperCase(Locale.ROOT))
                .toArray(String[]::new);
    }
}
