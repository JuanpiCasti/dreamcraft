package dev.dreamcraft.protection.presentation;

import dev.dreamcraft.protection.presentation.resourcepack.PresentationAssetRegistry;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the presentation asset contract (Fase 4): parsing of
 * presentation-assets.yml and the vanilla fallback chain (MD §9/§23).
 */
class PresentationAssetsTest {

    private static YamlConfiguration yaml(String content) {
        YamlConfiguration cfg = new YamlConfiguration();
        try {
            cfg.loadFromString(content);
        } catch (org.bukkit.configuration.InvalidConfigurationException e) {
            throw new IllegalStateException(e);
        }
        return cfg;
    }

    private static final String CONTRACT = """
            options:
              enabled: true
            icons:
              ward.icon:
                material: SHIELD
                cmd: 41101
                fallback: BEACON
              city.members:
                material: PLAYER_HEAD
            sounds:
              menu.click: ui.button.click
              menu.error:
                vanilla: block.note_block.bass
            fonts:
              dc.hud: "dreamcraft:hud"
            symbols:
              bar.full: "\\uE000"
            particles:
              ward.active: ENCHANT
            """;

    @Test
    void iconWithCmdResolvesCustomModelForPackViewers() {
        var registry = PresentationAssetRegistry.fromConfiguration(yaml(CONTRACT));
        assertTrue(registry.isAvailable());

        var icon = registry.icon("ward.icon").orElseThrow();
        assertEquals(Material.SHIELD, icon.material());
        assertEquals(41101, icon.customModelData());
        assertNull(icon.providerItemId());
    }

    @Test
    void plainIconWithoutCmdStaysVanilla() {
        var registry = PresentationAssetRegistry.fromConfiguration(yaml(CONTRACT));
        var icon = registry.icon("city.members").orElseThrow();
        assertEquals(Material.PLAYER_HEAD, icon.material());
        assertFalse(icon.hasCustomModel());
    }

    @Test
    void unknownKeysFallBackEmpty() {
        var registry = PresentationAssetRegistry.fromConfiguration(yaml(CONTRACT));
        assertTrue(registry.icon("estate.dragon").isEmpty());
        assertNull(registry.sound("menu.success"));
        assertNull(registry.font("nope"));
        assertNull(registry.symbol("nope"));
        assertNull(registry.particle("nope"));
    }

    @Test
    void soundsFontsSymbolsAndParticlesParse() {
        var registry = PresentationAssetRegistry.fromConfiguration(yaml(CONTRACT));
        assertEquals("ui.button.click", registry.sound("menu.click"));
        // Both plain-string and {vanilla:} forms are accepted
        assertEquals("block.note_block.bass", registry.sound("menu.error"));
        assertEquals("dreamcraft:hud", registry.font("dc.hud"));
        assertEquals("\uE000", registry.symbol("bar.full"));
        assertEquals("ENCHANT", registry.particle("ward.active"));
    }

    @Test
    void disabledRegistryDegradesToVanilla() {
        YamlConfiguration cfg = yaml(CONTRACT);
        cfg.set("options.enabled", false);
        var registry = PresentationAssetRegistry.fromConfiguration(cfg);

        assertFalse(registry.isAvailable());
        // Golden rule MD §23: without the pack/provider everything stays vanilla
        var icon = registry.icon("ward.icon").orElseThrow();
        assertEquals(Material.BEACON, icon.material());
        assertFalse(icon.hasCustomModel());
    }

    @Test
    void emptyRegistryIsInert() {
        var registry = PresentationAssetRegistry.fromConfiguration(new YamlConfiguration());
        assertFalse(registry.isAvailable());
        assertTrue(registry.icon("ward.icon").isEmpty());
        assertEquals(0, registry.iconCount());
    }
}
