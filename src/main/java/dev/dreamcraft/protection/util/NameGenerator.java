package dev.dreamcraft.protection.util;

import java.util.Random;
import java.util.function.Predicate;

/**
 * Generates human-friendly display names for claims, wards and other named
 * entities, so players never see raw UUID fragments like "11214d3a".
 *
 * <p>Pure Java — safe to use from the domain layer.
 */
public final class NameGenerator {

    private static final String[] NOUNS = {
            "Atalaya", "Bastión", "Refugio", "Alcázar", "Fortín", "Santuario",
            "Mirador", "Baluarte", "Torre", "Casona", "Cetro", "Umbral",
            "Ciénega", "Enclave", "Hogar", "Rincón", "Valle", "Cumbre"
    };

    private static final String[] EPITHETS = {
            "del Alba", "de Cristal", "Esmeralda", "del Crepúsculo", "Aurora",
            "de Plata", "del Roble", "Eterna", "del Viento", "de Jade",
            "del Fénix", "Sombrío", "de la Luna", "del Mar", "Dorada",
            "de las Sombras", "del Trueno", "Serena"
    };

    private static final String[] ROMAN = {
            "", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"
    };

    private static final Random RANDOM = new Random();

    private NameGenerator() {}

    /** @return a random flavor name, e.g. "Atalaya del Alba". */
    public static String random() {
        return NOUNS[RANDOM.nextInt(NOUNS.length)] + " " + EPITHETS[RANDOM.nextInt(EPITHETS.length)];
    }

    /**
     * Generates a random name that passes {@code isTaken} uniqueness check.
     * Retries with fresh random names, then falls back to Roman numeral suffixes.
     *
     * @param isTaken predicate returning true when a candidate name is already in use
     * @return a unique display name (never null)
     */
    public static String unique(Predicate<String> isTaken) {
        for (int i = 0; i < 25; i++) {
            String candidate = random();
            if (!isTaken.test(candidate)) {
                return candidate;
            }
        }
        String base = random();
        for (int i = 1; i < ROMAN.length; i++) {
            String candidate = base + ROMAN[i];
            if (!isTaken.test(candidate)) {
                return candidate;
            }
        }
        return base + " " + System.currentTimeMillis() % 1000;
    }
}
