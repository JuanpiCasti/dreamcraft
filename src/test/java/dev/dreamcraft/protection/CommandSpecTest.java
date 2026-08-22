package dev.dreamcraft.protection;

import dev.dreamcraft.protection.command.CommandRegistry;
import dev.dreamcraft.protection.command.SubcommandSpec;
import dev.dreamcraft.protection.config.CommandOptions;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the config-driven subcommand framework (Fase 1):
 * dispatch resolution by canonical name and configured alias, enabled flags,
 * alias-aware tab completion and admin-only token filtering.
 */
class CommandSpecTest {

    private CommandRegistry registry() {
        return new CommandRegistry("ward")
                .register(SubcommandSpec.of("create", (p, a) -> true))
                .register(SubcommandSpec.of("menu", (p, a) -> true))
                .register(SubcommandSpec.admin("give", (p, a) -> true));
    }

    @Test
    void resolvesCanonicalNamesCaseInsensitively() {
        CommandRegistry r = registry();
        assertEquals("create", r.resolve("CREATE").name());
        assertEquals("menu", r.resolve("Menu").name());
        assertNull(r.resolve("noexiste"));
        assertNull(r.resolve(null));
    }

    @Test
    void resolvesConfiguredAliases() {
        CommandOptions options = optionsFrom("""
                commands:
                  ward:
                    subcommands:
                      create:
                        aliases: [fundar, Fundar2]
                """);
        CommandRegistry r = new CommandRegistry("ward")
                .register(SubcommandSpec.of("create", (p, a) -> true)
                        .withAliases(options.aliases("ward", "create")))
                .register(SubcommandSpec.of("menu", (p, a) -> true));

        SubcommandSpec spec = r.resolve("fundar");
        assertNotNull(spec);
        assertEquals("create", spec.name());
        // Alias lookup is case-insensitive
        assertEquals("create", r.resolve("FUNDAR").name());
        assertEquals("create", r.resolve("Fundar2").name());
        // Unknown tokens still fail
        assertNull(r.resolve("crear"));
    }

    @Test
    void disabledSubcommandsAreFlaggedByOptions() {
        CommandOptions options = optionsFrom("""
                commands:
                  ward:
                    subcommands:
                      menu:
                        enabled: false
                """);
        assertFalse(options.isEnabled("ward", "menu"));
        assertTrue(options.isEnabled("ward", "create"));
        // Roots/subcommands without config entries default to enabled
        assertTrue(options.isEnabled("city", "create"));
        assertTrue(options.isEnabled("ward", "give"));
    }

    @Test
    void completionTokensIncludeAliasesAndFilterByPrefix() {
        CommandOptions options = optionsFrom("""
                commands:
                  ward:
                    subcommands:
                      create:
                        aliases: [fundar]
                """);
        CommandRegistry r = new CommandRegistry("ward")
                .register(SubcommandSpec.of("create", (p, a) -> true).withAliases(options.aliases("ward", "create")))
                .register(SubcommandSpec.of("rename", (p, a) -> true))
                .register(SubcommandSpec.admin("give", (p, a) -> true));

        List<String> all = r.completionTokens("");
        assertTrue(all.containsAll(List.of("create", "fundar", "rename", "give")));
        assertTrue(all.indexOf("create") < all.indexOf("fundar"), "canonical name first");

        List<String> f = r.completionTokens("fu");
        assertEquals(List.of("fundar"), f);
    }

    @Test
    void completionTokensRespectAdminPredicate() {
        CommandRegistry r = registry();
        List<String> playerView = r.completionTokens("", spec -> !spec.isAdminOnly());
        assertTrue(playerView.contains("create"));
        assertFalse(playerView.contains("give"));

        List<String> adminView = r.completionTokens("", spec -> true);
        assertTrue(adminView.contains("give"));
    }

    @Test
    void adminSpecsReportTheirClass() {
        assertTrue(SubcommandSpec.admin("give", (p, a) -> true).isAdminOnly());
        assertFalse(SubcommandSpec.of("create", (p, a) -> true).isAdminOnly());
    }

    private static CommandOptions optionsFrom(String yaml) {
        YamlConfiguration cfg = new YamlConfiguration();
        try {
            cfg.loadFromString(yaml);
        } catch (org.bukkit.configuration.InvalidConfigurationException e) {
            throw new IllegalStateException(e);
        }
        return CommandOptions.load(cfg);
    }
}
