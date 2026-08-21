package dev.dreamcraft.protection;

import dev.dreamcraft.protection.config.DurationParser;
import dev.dreamcraft.protection.model.TierDefinition;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for tier configuration parsing and DurationParser.
 */
class TierAndConfigTest {

    // ── DurationParser ────────────────────────────────────────────────────────

    @Test
    void parsesHours() {
        assertEquals(Duration.ofHours(24), DurationParser.parse("24h"));
    }

    @Test
    void parsesDays() {
        assertEquals(Duration.ofDays(7), DurationParser.parse("7d"));
    }

    @Test
    void parsesMinutes() {
        assertEquals(Duration.ofMinutes(30), DurationParser.parse("30m"));
    }

    @Test
    void parsesMilliseconds() {
        assertEquals(Duration.ofMillis(500), DurationParser.parse("500ms"));
    }

    @Test
    void parsesIso8601Fallback() {
        assertEquals(Duration.ofHours(2), DurationParser.parse("PT2H"));
    }

    // ── TierDefinition ────────────────────────────────────────────────────────

    @Test
    void basicTierHasCorrectValues() {
        TierDefinition basic = new TierDefinition("basic", 12, 12, 4);
        assertEquals("basic", basic.key());
        assertEquals(12, basic.radius());
        assertEquals(12, basic.buildRadius());
        assertEquals(4, basic.maxMembers());
    }

    @Test
    void domainTierHasCorrectValues() {
        TierDefinition domain = new TierDefinition("domain", 48, 32, 30);
        assertEquals(48, domain.radius());
        assertEquals(32, domain.buildRadius());
        assertEquals(30, domain.maxMembers());
    }

    @Test
    void configuredTierRadiusIsCorrectForDomainViaMap() {
        Map<String, TierDefinition> tiers = Map.of(
                "domain", new TierDefinition("domain", 48, 32, 30)
        );
        TierDefinition domain = tiers.get("domain");
        assertNotNull(domain);
        assertEquals(48, domain.radius());
    }
}
