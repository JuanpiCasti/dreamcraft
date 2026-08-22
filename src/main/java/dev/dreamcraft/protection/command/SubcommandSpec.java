package dev.dreamcraft.protection.command;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Declarative description of one subcommand: canonical name, optional aliases
 * (merged from config.yml at boot), permission class and handler.
 *
 * <p>Each domain command builds its {@link CommandRegistry} once at construction;
 * dispatch, tab completion and help all read from that single table — eliminating
 * the previous duplication between the switch dispatch and the tab-complete list.
 */
public final class SubcommandSpec {

    /** Handler signature: receives the player and the full raw args (args[0] = token used). */
    @FunctionalInterface
    public interface Handler {
        boolean run(Player player, String[] args);
    }

    private final String name;
    private final List<String> configAliases;
    private final boolean adminOnly;
    private final Handler handler;

    private SubcommandSpec(String name, List<String> configAliases, boolean adminOnly, Handler handler) {
        this.name = name;
        this.configAliases = List.copyOf(configAliases);
        this.adminOnly = adminOnly;
        this.handler = handler;
    }

    /** Player-facing subcommand. */
    public static SubcommandSpec of(String name, Handler handler) {
        return new SubcommandSpec(name, List.of(), false, handler);
    }

    /** Admin-only subcommand (hidden from non-admin tab completion). */
    public static SubcommandSpec admin(String name, Handler handler) {
        return new SubcommandSpec(name, List.of(), true, handler);
    }

    /** Returns a copy with extra aliases merged from config. */
    public SubcommandSpec withAliases(List<String> aliases) {
        return new SubcommandSpec(name, aliases, adminOnly, handler);
    }

    public String name() {
        return name;
    }

    public List<String> configAliases() {
        return configAliases;
    }

    public boolean isAdminOnly() {
        return adminOnly;
    }

    /** All tokens that resolve to this subcommand: canonical name first, then configured aliases. */
    public List<String> tokens() {
        List<String> tokens = new ArrayList<>();
        tokens.add(name);
        tokens.addAll(configAliases);
        return tokens;
    }

    public boolean matches(String lowerToken) {
        if (name.equalsIgnoreCase(lowerToken)) return true;
        for (String alias : configAliases) {
            if (alias.equalsIgnoreCase(lowerToken)) return true;
        }
        return false;
    }

    public boolean execute(Player player, String[] args) {
        return handler.run(player, args);
    }
}
