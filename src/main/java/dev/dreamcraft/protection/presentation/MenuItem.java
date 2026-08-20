package dev.dreamcraft.protection.presentation;

import java.util.List;
import java.util.Optional;

/**
 * Immutable descriptor for a single item slot in a menu.
 *
 * <p>A MenuItem contains only display data and an optional action — never
 * Bukkit {@code ItemStack}, Oraxen model references, or resource-pack asset IDs.
 * The presentation layer implementation converts these descriptors into
 * concrete items appropriate for the active provider (vanilla, Oraxen, etc.).
 *
 * <p>The {@code iconKey} is a stable logical identifier that the presentation
 * layer maps to a material or model. For example {@code "icon.ward.active"} maps
 * to {@code SHIELD} in vanilla or to a custom model if Oraxen/resource-pack is active.
 */
public record MenuItem(
        /** Slot index in the menu (0-based). */
        int slot,
        /**
         * Logical icon key — resolved by the presentation layer to a concrete icon.
         * Format: {@code "icon.<domain>.<state>"}, e.g. {@code "icon.ward.active"}.
         */
        String iconKey,
        /** Display name (may contain legacy color codes or MiniMessage tags). */
        String displayName,
        /** Lore lines. */
        List<String> lore,
        /** Action triggered when this item is clicked. Null for display-only items. */
        MenuAction action,
        /** Whether this slot accepts player items (e.g. deposit slots). */
        boolean acceptsDeposit
) {
    /** Creates a read-only display item. */
    public static MenuItem display(int slot, String iconKey, String displayName, List<String> lore) {
        return new MenuItem(slot, iconKey, displayName, lore, null, false);
    }

    /** Creates a clickable action item. */
    public static MenuItem button(int slot, String iconKey, String displayName, List<String> lore, MenuAction action) {
        return new MenuItem(slot, iconKey, displayName, lore, action, false);
    }

    /** Creates a deposit slot. */
    public static MenuItem depositSlot(int slot, String iconKey, String displayName, List<String> lore) {
        return new MenuItem(slot, iconKey, displayName, lore, null, true);
    }

    public Optional<MenuAction> getAction() {
        return Optional.ofNullable(action);
    }
}
