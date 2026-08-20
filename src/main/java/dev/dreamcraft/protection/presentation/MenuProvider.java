package dev.dreamcraft.protection.presentation;

/**
 * Abstraction for opening and refreshing menus.
 *
 * <p>Implementations may back this with vanilla Bukkit inventories, DeluxeMenus,
 * Oraxen GUIs, or any other provider. The domain and service layer never depend
 * on a concrete implementation — they construct a {@link MenuDefinition} and pass
 * it to the provider.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>Service layer builds a {@link MenuContext} from domain data.</li>
 *   <li>Service layer calls {@code MenuProvider#open(definition, context)}.</li>
 *   <li>Provider renders the definition and opens the inventory for the player.</li>
 *   <li>Player interacts → provider dispatches {@link MenuAction} back to the service layer.</li>
 * </ol>
 *
 * <p>To replace the provider (e.g. migrate from vanilla to DeluxeMenus), implement
 * this interface and swap the registered instance — no domain code changes required.
 */
public interface MenuProvider {

    /**
     * Opens a menu for the player described in {@code context}.
     *
     * @param definition the menu layout
     * @param context    viewer identity and pre-computed view data
     */
    void open(MenuDefinition definition, MenuContext context);

    /**
     * Refreshes an already-open menu for the player (updates display items without
     * closing/reopening the inventory). No-op if the player does not have this menu open.
     *
     * @param menuId  the stable menu ID from {@link MenuDefinition#menuId()}
     * @param context viewer identity and freshly-computed view data
     */
    void refresh(String menuId, MenuContext context);

    /**
     * Closes the open menu for the player, if any.
     */
    void close(java.util.UUID viewerId);

    /**
     * Returns true if this provider can render the given menuId.
     */
    boolean supports(String menuId);

    /**
     * Name of the provider for logging/diagnostics (e.g. "vanilla", "deluxe", "oraxen").
     */
    String providerName();
}
