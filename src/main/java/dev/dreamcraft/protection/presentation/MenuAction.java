package dev.dreamcraft.protection.presentation;

/**
 * Immutable descriptor for a single action a player can trigger from a menu.
 *
 * <p>Actions carry only presentation-level intent (the action type and optional
 * payload string). The domain service handles the actual business logic when
 * the action is dispatched.
 *
 * <p>Actions must NEVER recalculate score, tier, radius, upkeep, ownership
 * or permissions — those are computed by the domain and passed in via {@link MenuContext}.
 */
public record MenuAction(
        /** Stable identifier for the action type, e.g. "ward.deposit", "city.invite". */
        String actionId,
        /**
         * Optional payload string for parameterised actions
         * (e.g. a player name to invite, a tier key to upgrade to).
         */
        String payload
) {
    /** Creates an action with no payload. */
    public static MenuAction of(String actionId) {
        return new MenuAction(actionId, null);
    }

    /** Creates an action with a payload. */
    public static MenuAction of(String actionId, String payload) {
        return new MenuAction(actionId, payload);
    }
}
