package dev.dreamcraft.protection.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Per-root-command table of {@link SubcommandSpec}s — the single source of truth
 * for dispatch, level-1 tab completion and help rendering.
 */
public final class CommandRegistry {

    private final String root;
    private final Map<String, SubcommandSpec> specs = new LinkedHashMap<>();

    public CommandRegistry(String root) {
        this.root = root;
    }

    public String root() {
        return root;
    }

    public CommandRegistry register(SubcommandSpec spec) {
        specs.put(spec.name().toLowerCase(Locale.ROOT), spec);
        return this;
    }

    /** Resolves a user token (canonical name or configured alias) to its spec; null when unknown or disabled. */
    public SubcommandSpec resolve(String token) {
        if (token == null) return null;
        String lower = token.toLowerCase(Locale.ROOT);
        for (SubcommandSpec spec : specs.values()) {
            if (spec.matches(lower)) return spec;
        }
        return null;
    }

    public List<SubcommandSpec> all() {
        return List.copyOf(specs.values());
    }

    /** All tokens (names + configured aliases), filtered by prefix — for tab completion. */
    public List<String> completionTokens(String prefix) {
        return completionTokens(prefix, spec -> true);
    }

    /** Same as {@link #completionTokens(String)} but skips specs rejected by the filter (e.g. admin gating). */
    public List<String> completionTokens(String prefix, Predicate<SubcommandSpec> include) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (SubcommandSpec spec : specs.values()) {
            if (!include.test(spec)) continue;
            for (String token : spec.tokens()) {
                if (token.toLowerCase(Locale.ROOT).startsWith(lower)) out.add(token);
            }
        }
        return out;
    }
}
