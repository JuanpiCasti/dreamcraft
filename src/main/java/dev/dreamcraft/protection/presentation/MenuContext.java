package dev.dreamcraft.protection.presentation;

import java.util.Map;
import java.util.UUID;

/**
 * Context passed to a menu when it is opened or an action is triggered.
 *
 * <p>Carries the viewer identity and all data needed to render the menu
 * without reaching back into the domain. ViewModels are pre-computed by
 * the service layer before the menu is opened.
 *
 * <p>The domain never depends on this class. It flows in the direction:
 * <pre>Domain → Services → (build context) → Presentation</pre>
 */
public record MenuContext(
        UUID viewerId,
        String viewerName,
        /** Arbitrary typed data keyed by string — populated by service layer. */
        Map<String, Object> data
) {
    /** Retrieves a typed value from the context data, or null. */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) data.get(key);
    }

    public boolean has(String key) {
        return data.containsKey(key);
    }
}
