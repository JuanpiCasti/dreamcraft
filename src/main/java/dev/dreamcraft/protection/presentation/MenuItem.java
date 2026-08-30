package dev.dreamcraft.protection.presentation;

import java.util.ArrayList;
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

    // ── 2×2 action blocks ─────────────────────────────────────────────────────

    /** Slot offsets of a 2×2 block anchored at its top-left corner: {n, n+1, n+9, n+10}. */
    private static final int[] BLOCK_2X2_OFFSETS = {0, 1, 9, 10};

    /** Quarter suffixes matching the pack's tile art, in the same slot order as {@link #BLOCK_2X2_OFFSETS}. */
    private static final String[] QUARTER_SUFFIXES = {".tl", ".tr", ".bl", ".br"};

    /**
     * Creates a 2×2 extended button occupying slots {@code n, n+1, n+9, n+10}.
     * The pack renders one big button split into four quarter tiles: slot {@code n}
     * carries {@code iconKey + ".tl"}, {@code n+1} → {@code .tr}, {@code n+9} →
     * {@code .bl}, {@code n+10} → {@code .br}. All four share name/lore/action, so
     * clicking any of the four slots triggers the action once (click routing
     * resolves the item per raw slot — there is no per-action dedup to conflict with).
     * Without the pack every quarter falls back to plain PAPER.
     *
     * @param containerSize inventory size (27/54); the whole block must fit inside
     * @param topLeftSlot   anchor slot {@code n}; must satisfy {@code n % 9 <= 7}
     *                      so the block never crosses a row boundary
     * @throws IllegalArgumentException when the anchor breaks the row grid or the
     *         block overflows the container
     */
    public static List<MenuItem> block2x2Button(int containerSize, int topLeftSlot, String iconKey,
                                                String displayName, List<String> lore, MenuAction action) {
        requireValidBlock(containerSize, topLeftSlot);
        List<MenuItem> block = new ArrayList<>(4);
        for (int i = 0; i < BLOCK_2X2_OFFSETS.length; i++) {
            block.add(button(topLeftSlot + BLOCK_2X2_OFFSETS[i],
                    iconKey + QUARTER_SUFFIXES[i], displayName, lore, action));
        }
        return block;
    }

    /** Same quarter tiling as {@link #block2x2Button} but display-only (no action). */
    public static List<MenuItem> block2x2Display(int containerSize, int topLeftSlot, String iconKey,
                                                 String displayName, List<String> lore) {
        requireValidBlock(containerSize, topLeftSlot);
        List<MenuItem> block = new ArrayList<>(4);
        for (int i = 0; i < BLOCK_2X2_OFFSETS.length; i++) {
            block.add(display(topLeftSlot + BLOCK_2X2_OFFSETS[i],
                    iconKey + QUARTER_SUFFIXES[i], displayName, lore));
        }
        return block;
    }

    private static void requireValidBlock(int containerSize, int topLeftSlot) {
        if (topLeftSlot < 0 || topLeftSlot % 9 > 7) {
            throw new IllegalArgumentException(
                    "2×2 block anchor " + topLeftSlot + " crosses a row boundary");
        }
        if (topLeftSlot + BLOCK_2X2_OFFSETS[BLOCK_2X2_OFFSETS.length - 1] >= containerSize) {
            throw new IllegalArgumentException("2×2 block at " + topLeftSlot
                    + " overflows container size " + containerSize);
        }
    }

    // ── 3×3 action blocks ─────────────────────────────────────────────────────

    /** Slot offsets of a 3×3 block anchored at its top-left corner: {0,1,2, 9,10,11, 18,19,20}. */
    private static final int[] BLOCK_3X3_OFFSETS = {
            0, 1, 2,
            9, 10, 11,
            18, 19, 20
    };

    /**
     * Creates a 3×3 display block occupying 9 slots {n, n+1, n+2, n+9, n+10, n+11, n+18, n+19, n+20}.
     * All 9 slots share the same display name and lore.
     */
    public static List<MenuItem> block3x3Display(int containerSize, int topLeftSlot, String iconKey,
                                                 String displayName, List<String> lore) {
        if (topLeftSlot < 0 || topLeftSlot % 9 > 6) {
            throw new IllegalArgumentException(
                    "3×3 block anchor " + topLeftSlot + " crosses a row boundary");
        }
        if (topLeftSlot + BLOCK_3X3_OFFSETS[BLOCK_3X3_OFFSETS.length - 1] >= containerSize) {
            throw new IllegalArgumentException("3×3 block at " + topLeftSlot
                    + " overflows container size " + containerSize);
        }
        List<MenuItem> block = new ArrayList<>(9);
        for (int offset : BLOCK_3X3_OFFSETS) {
            block.add(display(topLeftSlot + offset, iconKey, displayName, lore));
        }
        return block;
    }

    /**
     * Creates a 3×3 interactive button occupying 9 slots.
     * Clicking any slot fires the same {@link MenuAction}.
     */
    public static List<MenuItem> block3x3Button(int containerSize, int topLeftSlot, String iconKey,
                                                String displayName, List<String> lore, MenuAction action) {
        if (topLeftSlot < 0 || topLeftSlot % 9 > 6) {
            throw new IllegalArgumentException(
                    "3×3 block anchor " + topLeftSlot + " crosses a row boundary");
        }
        if (topLeftSlot + BLOCK_3X3_OFFSETS[BLOCK_3X3_OFFSETS.length - 1] >= containerSize) {
            throw new IllegalArgumentException("3×3 block at " + topLeftSlot
                    + " overflows container size " + containerSize);
        }
        List<MenuItem> block = new ArrayList<>(9);
        for (int offset : BLOCK_3X3_OFFSETS) {
            block.add(button(topLeftSlot + offset, iconKey, displayName, lore, action));
        }
        return block;
    }

    // ── 3×2 action blocks (3 slots horizontales, 2 verticales) ───────────────

    /** Slot offsets of a 3×2 block anchored at top-left: {0,1,2, 9,10,11}. */
    private static final int[] BLOCK_3X2_OFFSETS = {
            0, 1, 2,
            9, 10, 11
    };

    /**
     * Creates a 3×2 display block occupying 6 slots {n, n+1, n+2, n+9, n+10, n+11}.
     * 3 slots horizontally, 2 slots vertically.
     */
    public static List<MenuItem> block3x2Display(int containerSize, int topLeftSlot, String iconKey,
                                                 String displayName, List<String> lore) {
        if (topLeftSlot < 0 || topLeftSlot % 9 > 6) {
            throw new IllegalArgumentException(
                    "3×2 block anchor " + topLeftSlot + " crosses a row boundary");
        }
        if (topLeftSlot + BLOCK_3X2_OFFSETS[BLOCK_3X2_OFFSETS.length - 1] >= containerSize) {
            throw new IllegalArgumentException("3×2 block at " + topLeftSlot
                    + " overflows container size " + containerSize);
        }
        List<MenuItem> block = new ArrayList<>(6);
        for (int offset : BLOCK_3X2_OFFSETS) {
            block.add(display(topLeftSlot + offset, iconKey, displayName, lore));
        }
        return block;
    }

    /**
     * Creates a 3×2 interactive button occupying 6 slots.
     * Clicking any slot fires the same {@link MenuAction}.
     */
    public static List<MenuItem> block3x2Button(int containerSize, int topLeftSlot, String iconKey,
                                                String displayName, List<String> lore, MenuAction action) {
        if (topLeftSlot < 0 || topLeftSlot % 9 > 6) {
            throw new IllegalArgumentException(
                    "3×2 block anchor " + topLeftSlot + " crosses a row boundary");
        }
        if (topLeftSlot + BLOCK_3X2_OFFSETS[BLOCK_3X2_OFFSETS.length - 1] >= containerSize) {
            throw new IllegalArgumentException("3×2 block at " + topLeftSlot
                    + " overflows container size " + containerSize);
        }
        List<MenuItem> block = new ArrayList<>(6);
        for (int offset : BLOCK_3X2_OFFSETS) {
            block.add(button(topLeftSlot + offset, iconKey, displayName, lore, action));
        }
        return block;
    }

    public Optional<MenuAction> getAction() {
        return Optional.ofNullable(action);
    }
}
