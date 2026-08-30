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

    /** GUI contract: profile/close buttons + background glyphs (menu.bg.*). */
    private static final String MENU_CONTRACT = """
            options:
              enabled: true
            icons:
              menu.profile:
                material: PAPER
                cmd: 41403
                fallback: PLAYER_HEAD
                hide-name: true
              menu.close:
                material: PAPER
                cmd: 41404
                fallback: BARRIER
                hide-name: true
            fonts:
              dc.gui: "dreamcraft:gui"
            symbols:
              menu.bg.9:  { glyph: "\\uE100", font: dc.gui }
              menu.bg.18: { glyph: "\\uE101", font: dc.gui }
              menu.bg.27: { glyph: "\\uE102", font: dc.gui }
              menu.bg.36: { glyph: "\\uE103", font: dc.gui }
              menu.bg.45: { glyph: "\\uE104", font: dc.gui }
              menu.bg.54: { glyph: "\\uE105", font: dc.gui }
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

    @Test
    void guiButtonsParseWithCmdFallbackAndHideName() {
        var registry = PresentationAssetRegistry.fromConfiguration(yaml(MENU_CONTRACT));

        var profile = registry.iconAsset("menu.profile").orElseThrow();
        assertEquals(Material.PAPER, profile.material());
        assertEquals(41403, profile.cmd());
        assertTrue(profile.hideName());

        var close = registry.iconAsset("menu.close").orElseThrow();
        assertEquals(Material.PAPER, close.material());
        assertEquals(41404, close.cmd());
        assertTrue(close.hideName());
    }

    @Test
    void guiButtonFallbacksApplyWithoutPack() {
        // options.enabled: false forces the vanilla fallback branch of icon()
        YamlConfiguration cfg = yaml(MENU_CONTRACT);
        cfg.set("options.enabled", false);
        var registry = PresentationAssetRegistry.fromConfiguration(cfg);

        assertEquals(Material.PLAYER_HEAD, registry.icon("menu.profile").orElseThrow().material());
        assertEquals(Material.BARRIER, registry.icon("menu.close").orElseThrow().material());
    }

    @Test
    void guiFontAndBackgroundGlyphsResolve() {
        var registry = PresentationAssetRegistry.fromConfiguration(yaml(MENU_CONTRACT));

        assertEquals("dreamcraft:gui", registry.font("dc.gui"));
        var ref = registry.symbolRef("menu.bg.54").orElseThrow();
        assertEquals("\uE105", ref.glyph());
        assertEquals("dreamcraft:gui", ref.fontKey());
        // Wrapper siblings ({glyph:, font:}) never leak into the raw symbol map
        assertNull(registry.symbol("menu.bg.54.font"));
    }

    @Test
    void missingGlyphRefDegradesToEmpty() {
        var registry = PresentationAssetRegistry.fromConfiguration(yaml(CONTRACT));
        assertTrue(registry.symbolRef("menu.bg.54").isEmpty());
    }

    @Test
    void resolveMaterialDifferentiatesBetweenPackAndViewerWithoutPack() {
        var registry = PresentationAssetRegistry.fromConfiguration(yaml(CONTRACT));
        // With pack: SHIELD (since cmd > 0)
        assertEquals(Material.SHIELD, registry.resolveMaterial("ward.icon", true));
        // Without pack: fallback BEACON
        assertEquals(Material.BEACON, registry.resolveMaterial("ward.icon", false));
        // Without cmd: material PLAYER_HEAD for both
        assertEquals(Material.PLAYER_HEAD, registry.resolveMaterial("city.members", true));
        assertEquals(Material.PLAYER_HEAD, registry.resolveMaterial("city.members", false));
    }
}
