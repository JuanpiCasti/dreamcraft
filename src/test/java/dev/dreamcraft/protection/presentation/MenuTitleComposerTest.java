package dev.dreamcraft.protection.presentation;

import dev.dreamcraft.protection.presentation.resourcepack.MenuTitleComposer;
import dev.dreamcraft.protection.presentation.resourcepack.PackState;
import dev.dreamcraft.protection.presentation.resourcepack.PresentationAssetRegistry;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the adaptive menu title composition: legacy plain text without
 * the pack, background glyph ({@code menu.bg.<size>}) with it.
 */
class MenuTitleComposerTest {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    private static final String TITLE = "&8Núcleo &fTest";
    private static final UUID VIEWER = UUID.randomUUID();

    private static final PackState WITH_PACK = id -> true;
    private static final PackState WITHOUT_PACK = id -> false;

    private static PresentationAssetRegistry guiRegistry(boolean withBg9) {
        return guiRegistry(withBg9, false);
    }

    private static PresentationAssetRegistry guiRegistry(boolean withBg9, boolean withOffset) {
        String bg9 = withBg9 ? "  menu.bg.9: { glyph: \"\\uE100\", font: dc.gui }\n" : "";
        String offset = withOffset ? """
                  menu.offset.-4: { glyph: "\\uEC04", font: dc.gui }
                  menu.offset.-1: { glyph: "\\uEC01", font: dc.gui }
                """ : "";
        YamlConfiguration cfg = new YamlConfiguration();
        try {
            cfg.loadFromString("""
                    options:
                      enabled: true
                    icons:
                      menu.close:
                        material: PAPER
                        cmd: 41404
                    fonts:
                      dc.gui: "dreamcraft:gui"
                    symbols:
                      menu.bg.54: { glyph: "\\uE105", font: dc.gui }
                    """ + bg9 + offset);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return PresentationAssetRegistry.fromConfiguration(cfg);
    }

    private static MenuDefinition definition(int size) {
        return new MenuDefinition("test_menu", TITLE, size, List.of());
    }

    @Test
    void withoutPackComposesLegacyTitle() {
        Component result = MenuTitleComposer.compose(guiRegistry(true), WITHOUT_PACK,
                definition(54), VIEWER);
        assertEquals(TITLE, LEGACY.serialize(result));
    }

    @Test
    void withPackAndKnownSizeRendersBackgroundGlyph() {
        Component result = MenuTitleComposer.compose(guiRegistry(false), WITH_PACK,
                definition(54), VIEWER);

        assertEquals(TextDecoration.State.FALSE,
                result.decoration(TextDecoration.ITALIC));
        assertEquals(NamedTextColor.WHITE, result.color());
        assertEquals(Key.key("dreamcraft:gui"), result.font());
        assertEquals("\uE105", ((net.kyori.adventure.text.TextComponent) result).content());
    }

    @Test
    void withPackAndOffsetAnchorsGlyphToLeftEdge() {
        // x=8 label origin − 2×(−4) − 1×(−1) private spaces = background at x=-1 (covers vanilla edge)
        Component result = MenuTitleComposer.compose(guiRegistry(false, true), WITH_PACK,
                definition(54), VIEWER);

        assertEquals(4, result.children().size());
        for (int i = 0; i < 4; i++) {
            assertEquals(Key.key("dreamcraft:gui"), result.children().get(i).font());
            assertEquals(TextDecoration.State.FALSE,
                    result.children().get(i).decoration(TextDecoration.ITALIC));
        }
        assertEquals("\uEC04", ((net.kyori.adventure.text.TextComponent)
                result.children().get(0)).content());
        assertEquals("\uEC04", ((net.kyori.adventure.text.TextComponent)
                result.children().get(1)).content());
        assertEquals("\uEC01", ((net.kyori.adventure.text.TextComponent)
                result.children().get(2)).content());
        assertEquals("\uE105", ((net.kyori.adventure.text.TextComponent)
                result.children().get(3)).content());
    }

    @Test
    void withPackButUnknownSizeFallsBackToLegacy() {
        // registry lacks menu.bg.9 while the menu requests size 9
        Component result = MenuTitleComposer.compose(guiRegistry(false), WITH_PACK,
                definition(9), VIEWER);
        assertEquals(TITLE, LEGACY.serialize(result));
    }

    @Test
    void cityAndEstateMenusUseTheirThemedGlyphs() {
        // registry with only the themed 54 glyphs: city → matriz (\uE115),
        // estate → nexo (\uE125), anything else → synt (\uE105)
        YamlConfiguration cfg = new YamlConfiguration();
        try {
            cfg.loadFromString("""
                    options:
                      enabled: true
                    icons:
                      menu.close:
                        material: PAPER
                        cmd: 41404
                    fonts:
                      dc.gui: "dreamcraft:gui"
                    symbols:
                      menu.bg.54: { glyph: "\\uE105", font: dc.gui }
                      menu.bg.matriz.54: { glyph: "\\uE115", font: dc.gui }
                      menu.bg.nexo.54: { glyph: "\\uE125", font: dc.gui }
                    """);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        var registry = PresentationAssetRegistry.fromConfiguration(cfg);

        Component city = MenuTitleComposer.compose(registry, WITH_PACK,
                new MenuDefinition("city_overview", TITLE, 54, List.of()), VIEWER);
        assertEquals("\uE115", ((net.kyori.adventure.text.TextComponent) city).content());

        Component estate = MenuTitleComposer.compose(registry, WITH_PACK,
                new MenuDefinition("estate_lobby", TITLE, 54, List.of()), VIEWER);
        assertEquals("\uE125", ((net.kyori.adventure.text.TextComponent) estate).content());

        Component ward = MenuTitleComposer.compose(registry, WITH_PACK,
                new MenuDefinition("ward_status", TITLE, 54, List.of()), VIEWER);
        assertEquals("\uE105", ((net.kyori.adventure.text.TextComponent) ward).content());

        // Admin variants inherit the domain theme
        Component cityAdmin = MenuTitleComposer.compose(registry, WITH_PACK,
                new MenuDefinition("city_admin_overview", TITLE, 54, List.of()), VIEWER);
        assertEquals("\uE115", ((net.kyori.adventure.text.TextComponent) cityAdmin).content());
    }

    @Test
    void nullRegistryOrDisabledDegradesToLegacy() {
        assertEquals(TITLE, LEGACY.serialize(
                MenuTitleComposer.compose(null, WITH_PACK, definition(54), VIEWER)));

        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("options.enabled", false);
        var disabled = PresentationAssetRegistry.fromConfiguration(cfg);
        assertEquals(TITLE, LEGACY.serialize(
                MenuTitleComposer.compose(disabled, WITH_PACK, definition(54), VIEWER)));
    }
}
