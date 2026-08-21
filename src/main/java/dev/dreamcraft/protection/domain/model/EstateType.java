package dev.dreamcraft.protection.domain.model;

import java.util.Locale;

/**
 * Adventure type of an Estate.
 *
 * <p>{@link #END} and {@link #TRIAL_CHAMBER} are "instanced adventures": they own
 * a physical area (portal / structure zone) and gate it to estate members only.
 * END estates additionally get a private End dimension instance per group with
 * automatic dragon respawn and world reset when everyone leaves.
 */
public enum EstateType {

    /** Plain social/adventure group — no world interaction. */
    STANDARD,
    /** End portal adventure: gated entrance area + private End instance. */
    END,
    /** Trial chamber adventure: gated vault/spawner area (no instancing). */
    TRIAL_CHAMBER;

    /** Stable lowercase key used in commands and persistence ("end", "trial_chamber"). */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** True if this type owns a gated physical area and instanced mechanics. */
    public boolean isInstancedAdventure() {
        return this == END || this == TRIAL_CHAMBER;
    }

    /** True if entering the area's portal creates a private End dimension. */
    public boolean usesEndInstance() {
        return this == END;
    }

    /**
     * Parses a user-facing type name. Accepted aliases:
     * end, trial_chamber, trialchamber, trial. Unknown values map to STANDARD.
     */
    public static EstateType parse(String raw) {
        if (raw == null) return STANDARD;
        String k = raw.trim().toLowerCase(Locale.ROOT).replace("-", "_");
        return switch (k) {
            case "end", "the_end", "ender" -> END;
            case "trial_chamber", "trialchamber", "trial" -> TRIAL_CHAMBER;
            default -> STANDARD;
        };
    }

    /** Spanish display label for menus/messages. */
    public String displayName() {
        return switch (this) {
            case STANDARD -> "Estándar";
            case END -> "End";
            case TRIAL_CHAMBER -> "Cámara de Pruebas";
        };
    }
}
