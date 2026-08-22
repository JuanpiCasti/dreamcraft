package dev.dreamcraft.protection.message;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the message catalog (Fase 3): {placeholder} substitution and the
 * server-override → embedded-default → fallback resolution order.
 */
class MessagesTest {

    @Test
    void applySubstitutesNamedPlaceholders() {
        assertEquals("Hola Steve, tienes 3 Wards.",
                Messages.apply("Hola {player}, tienes {count} Wards.", "player", "Steve", "count", 3));
        // Odd pairs and missing placeholders are left untouched
        assertEquals("{x}", Messages.apply("{x}", "only-key"));
    }

    @Test
    void trFallsBackToLiteralWhenKeyMissing() {
        Messages m = new Messages(new YamlConfiguration());
        assertEquals("Texto por defecto", m.tr("no.existe", "Texto por defecto", "a", 1));
    }

    @Test
    void trResolvesCatalogValuesAndPlaceholders() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("common.unknown-subcommand", "&cSubcomando desconocido: {sub}");
        Messages m = new Messages(cfg);

        String out = m.tr("common.unknown-subcommand", "fallback", "sub", "fundir");
        assertTrue(out.contains("Subcomando desconocido: fundir"));
        assertTrue(out.startsWith("&c"), "legacy codes survive for later parsing");
    }

    @Test
    void listsResolveFromCatalog() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("help.ward.lines", java.util.List.of("&f/ward create", "&f/ward menu"));
        Messages m = new Messages(cfg);
        assertEquals(2, m.list("help.ward.lines").size());
        assertTrue(m.list("missing.list").isEmpty());

        // Server override file wins over the merged defaults passed in
        Messages overridden = new Messages(cfg);
        assertEquals("&f/ward create", overridden.list("help.ward.lines").get(0));
    }
}
