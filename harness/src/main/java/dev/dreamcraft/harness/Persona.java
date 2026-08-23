package dev.dreamcraft.harness;

import org.bukkit.permissions.Permission;

import java.util.Set;

/**
 * A test user profile: display name, op flag and an explicit permission set.
 *
 * <p>Permissions are answered EXACTLY by membership ({@code *} grants all) so
 * scenarios stay deterministic regardless of LuckPerms state. Note that this
 * deliberately bypasses plugin.yml {@code default:} values — personas declare
 * their effective permissions explicitly.
 */
public record Persona(String name, boolean op, Set<String> permissions) {

    /** Grants everything (used for the console-like superuser). */
    public boolean grants(String permission) {
        return op || permissions.contains("*") || permissions.contains(permission);
    }

    public boolean grants(Permission permission) {
        return grants(permission.getName());
    }

    // ── Presets ───────────────────────────────────────────────────────────────

    public static Persona console() {
        return new Persona("Consola", true, Set.of("*"));
    }

    /** In-game administrator: every protection-related permission. */
    public static Persona adminJugador() {
        return new Persona("TestAdmin", false, Set.of(
                "dreamcraft.protection.use", "dreamcraft.protection.admin",
                "dreamcraft.protection.menu",
                "dreamcraft.ward.use", "dreamcraft.ward.admin",
                "dreamcraft.ward.menu", "dreamcraft.ward.remote",
                "dreamcraft.city.use", "dreamcraft.city.admin",
                "dreamcraft.estate.use",
                "dreamcraft.integrations.status"));
    }

    /** Regular player with the default-granted permissions only. */
    public static Persona jugadorBasico() {
        return new Persona("TestPlayer", false, Set.of(
                "dreamcraft.protection.use", "dreamcraft.ward.use",
                "dreamcraft.city.use", "dreamcraft.estate.use"));
    }

    /** VIP-flavoured player: menu access + remote ward actions. */
    public static Persona jugadorVip() {
        return new Persona("TestVip", false, Set.of(
                "dreamcraft.protection.use", "dreamcraft.protection.menu",
                "dreamcraft.ward.use", "dreamcraft.ward.menu", "dreamcraft.ward.remote",
                "dreamcraft.city.use", "dreamcraft.estate.use"));
    }

    /** Fresh visitor: no permissions at all. */
    public static Persona visitante() {
        return new Persona("TestAnon", false, Set.of());
    }
}
