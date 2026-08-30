package dev.dreamcraft.protection.presentation.resourcepack;

import dev.dreamcraft.protection.presentation.MenuDefinition;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.UUID;

/**
 * Composes adaptive menu titles: when the viewer has the resource pack, the
 * title becomes a single HD background glyph from the {@code dc.gui} font that
 * covers the whole vanilla container (one codepoint per inventory size,
 * {@code menu.bg.<size>}); without the pack it degrades to the plain legacy
 * title (MD §23 golden rule).
 *
 * <p>Pure static composition over {@link PresentationAssetRegistry} +
 * {@link PackState} — no Bukkit server required, fully unit-testable.
 */
public final class MenuTitleComposer {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    private MenuTitleComposer() {}

    public static Component compose(PresentationAssetRegistry registry, PackState packState,
                                    MenuDefinition definition, UUID viewerId) {
        Component legacy = LEGACY.deserialize(definition.title());
        if (registry == null || !registry.isAvailable()
                || packState == null || !packState.has(viewerId)) {
            return legacy;
        }
        // 1) glifo horneado por menú (panel + botones pintados); 2) tema/tamaño;
        // 3) legacy.
        var ref = registry.symbolRef("menu.bg." + definition.menuId());
        if (ref.isEmpty()) ref = registry.symbolRef(bgKey(definition));
        return ref
                .filter(r -> Key.parseable(r.fontKey()))
                .map(r -> glyphTitle(registry, r))
                .orElse(legacy);
    }

    /**
     * Themed background glyph key: the panel color follows the menu domain —
     * city menus use the blue "matriz" frame, estate menus the violet "nexo"
     * frame, everything else (ward/sync + admin) the steel "synt" frame
     * ({@code menu.bg.<size>}). Admin variants inherit their domain prefix.
     */
    private static String bgKey(MenuDefinition definition) {
        String menuId = definition.menuId();
        if (menuId.startsWith("city")) return "menu.bg.matriz." + definition.size();
        if (menuId.startsWith("estate")) return "menu.bg.nexo." + definition.size();
        return "menu.bg." + definition.size();
    }

    /**
     * Container labels are drawn left-aligned at x=8; the background glyph is
     * exactly container-width (176px), so two −4 private-space glyphs pull it
     * back to x=0. Without the offset glyph in the contract the bare glyph is
     * returned (graceful 8px misalignment instead of a broken title).
     */
    private static Component glyphTitle(PresentationAssetRegistry registry, GlyphRef ref) {
        // WHITE neutraliza el tinte del color de titulo del contenedor
        // (#404040): sin color explicito el glifo del fondo se dibuja x0.25.
        Component glyph = Component.text(ref.glyph())
                .font(Key.key(ref.fontKey()))
                .color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false);
        GlyphRef offset4 = registry.symbolRef("menu.offset.-4").orElse(null);
        if (offset4 == null || !Key.parseable(offset4.fontKey())) {
            return glyph;
        }
        Component back = Component.empty().decoration(TextDecoration.ITALIC, false);
        for (int i = 0; i < 2; i++) {
            back = back.append(Component.text(offset4.glyph())
                    .font(Key.key(offset4.fontKey()))
                    .color(NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false));
        }
        // -1px extra (-9px total) para estirar el fondo sobre la línea blanca del marco original
        GlyphRef offset1 = registry.symbolRef("menu.offset.-1").orElse(null);
        if (offset1 != null && Key.parseable(offset1.fontKey())) {
            back = back.append(Component.text(offset1.glyph())
                    .font(Key.key(offset1.fontKey()))
                    .color(NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false));
        }
        return back.append(glyph);
    }
}
