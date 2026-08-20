package dev.dreamcraft.protection.presentation;

import java.util.List;

/**
 * Immutable descriptor for a complete menu layout.
 *
 * <p>A MenuDefinition describes the menu structure — size, title, and all items —
 * without any reference to Bukkit {@code Inventory}, {@code ItemStack}, Oraxen, or
 * the resource pack. It is the pure data contract between the service layer and the
 * presentation provider.
 *
 * <p>The {@link MenuProvider} implementation converts this definition into a
 * rendered, openable inventory appropriate for the current presentation backend.
 */
public record MenuDefinition(
        /** Unique stable menu identifier (e.g. "ward_status", "city_overview"). */
        String menuId,
        /** Display title (may contain legacy color codes or MiniMessage tags). */
        String title,
        /** Number of inventory slots. Must be a multiple of 9 between 9 and 54. */
        int size,
        /** All items to render in this menu. */
        List<MenuItem> items
) {
    /**
     * Returns the item at the given slot, or null if none is defined.
     */
    public MenuItem itemAt(int slot) {
        return items.stream()
                .filter(i -> i.slot() == slot)
                .findFirst()
                .orElse(null);
    }
}
