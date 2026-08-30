package dev.dreamcraft.protection.presentation.resourcepack;

/**
 * A font glyph from {@code presentation-assets.yml} resolved for rendering:
 * the codepoint plus its Adventure font ({@code namespace:key}).
 *
 * <p>Used by glyph-based composition — e.g. full-container background glyphs
 * drawn through the inventory title.
 */
public record GlyphRef(String glyph, String fontKey) {
}
