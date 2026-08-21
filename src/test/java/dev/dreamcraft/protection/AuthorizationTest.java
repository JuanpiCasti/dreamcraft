package dev.dreamcraft.protection;

import dev.dreamcraft.protection.config.ProtectionConfig;
import dev.dreamcraft.protection.model.*;
import dev.dreamcraft.protection.service.ClaimManager;
import dev.dreamcraft.protection.service.ClaimIndex;
import dev.dreamcraft.protection.service.ProtectionChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for authorization (owner, member, outsider), member management, and tier limits.
 */
class AuthorizationTest {

    private ProtectionConfig config;
    private ClaimIndex claimIndex;
    private ProtectionChecker checker;
    private UUID ownerUuid;
    private UUID memberUuid;
    private UUID outsiderUuid;
    private ProtectionClaim claim;

    @BeforeEach
    void setUp() {
        config = UpkeepCalculatorTest.testConfig();
        claimIndex = new ClaimIndex();
        checker = new ProtectionChecker(config, claimIndex);

        ownerUuid = UUID.randomUUID();
        memberUuid = UUID.randomUUID();
        outsiderUuid = UUID.randomUUID();

        claim = makeClaim(ownerUuid, 0, 0, 16, "advanced", Set.of(memberUuid));
        claimIndex.add(claim);
    }

    // ── isAllowed ─────────────────────────────────────────────────────────────

    @Test
    void ownerIsAllowedAllActions() {
        for (ProtectionAction action : ProtectionAction.values()) {
            assertTrue(checker.isAllowed(ownerUuid, claim, action),
                    "Owner should be allowed: " + action);
        }
    }

    @Test
    void memberIsAllowedBuildAndInteract() {
        assertTrue(checker.isAllowed(memberUuid, claim, ProtectionAction.BUILD));
        assertTrue(checker.isAllowed(memberUuid, claim, ProtectionAction.BREAK));
        assertTrue(checker.isAllowed(memberUuid, claim, ProtectionAction.INTERACT));
    }

    @Test
    void memberCannotManageOrTransfer() {
        assertFalse(checker.isAllowed(memberUuid, claim, ProtectionAction.MANAGE_MEMBERS));
        assertFalse(checker.isAllowed(memberUuid, claim, ProtectionAction.TRANSFER));
        assertFalse(checker.isAllowed(memberUuid, claim, ProtectionAction.REMOVE_WARDROBE));
    }

    @Test
    void outsiderIsDeniedAll() {
        for (ProtectionAction action : ProtectionAction.values()) {
            assertFalse(checker.isAllowed(outsiderUuid, claim, action),
                    "Outsider should be denied: " + action);
        }
    }

    // ── ProtectionChecker.check result ────────────────────────────────────────

    @Test
    void ownerGetsAllowedResult() {
        // We simulate check() by passing a mock-like invocation path via direct check
        assertEquals(ProtectionCheckResult.PROTECTED_ALLOWED,
                checkerCheck(ownerUuid, 0, 0, ProtectionAction.BUILD));
    }

    @Test
    void outsiderGetsDeniedResult() {
        assertEquals(ProtectionCheckResult.PROTECTED_DENIED,
                checkerCheck(outsiderUuid, 0, 0, ProtectionAction.BUILD));
    }

    @Test
    void outsideClaimGetsNotProtected() {
        // Position well outside claim bounds (radius=16 → bounds -16..16)
        ProtectionCheckResult result = checker.checkNoPlayer("world", 200, 200, ProtectionAction.BUILD);
        assertEquals(ProtectionCheckResult.NOT_PROTECTED, result);
    }

    @Test
    void disabledProtectionAlwaysAllows() {
        ProtectionConfig disabled = new ProtectionConfig(
                false, 16, 16, false, null, 27,
                Duration.ofHours(24), Duration.ofHours(24), Duration.ofHours(6),
                Duration.ofHours(24), Duration.ofHours(48),
                null, 64, 8, true, Duration.ofDays(7),
                true, true, true, 41001, "dreamcraft:protection_wardrobe",
                null, null, 0, 100,
                Map.of(), Map.of(), Map.of(), Map.of()
        );
        ProtectionChecker disabledChecker = new ProtectionChecker(disabled, claimIndex);
        assertEquals(ProtectionCheckResult.PROTECTION_DISABLED,
                disabledChecker.checkNoPlayer("world", 0, 0, ProtectionAction.BUILD));
    }

    // ── Member management ─────────────────────────────────────────────────────

    @Test
    void addMemberWithinLimit() {
        ProtectionConfig cfg = makeConfigWithTier("advanced", 16, 16, 8);
        ClaimIndex idx = new ClaimIndex();
        ProtectionClaim c = makeClaim(ownerUuid, 0, 0, 16, "advanced", new HashSet<>());
        idx.add(c);
        ClaimManager mgr = new ClaimManager(cfg, idx, null, null);

        UUID newMember = UUID.randomUUID();
        assertTrue(mgr.addMember(c, newMember));
        assertTrue(c.members().contains(newMember));
    }

    @Test
    void addMemberExceedsLimit() {
        ProtectionConfig cfg = makeConfigWithTier("advanced", 16, 16, 2);
        ClaimIndex idx = new ClaimIndex();
        Set<UUID> existing = new HashSet<>(Set.of(UUID.randomUUID(), UUID.randomUUID()));
        ProtectionClaim c = makeClaim(ownerUuid, 0, 0, 16, "advanced", existing);
        idx.add(c);
        ClaimManager mgr = new ClaimManager(cfg, idx, null, null);

        assertFalse(mgr.addMember(c, UUID.randomUUID()));
    }

    @Test
    void removeMemberRemovesFromSet() {
        ProtectionConfig cfg = makeConfigWithTier("advanced", 16, 16, 8);
        ClaimIndex idx = new ClaimIndex();
        Set<UUID> existing = new HashSet<>(Set.of(memberUuid));
        ProtectionClaim c = makeClaim(ownerUuid, 0, 0, 16, "advanced", existing);
        idx.add(c);
        ClaimManager mgr = new ClaimManager(cfg, idx, null, null);

        mgr.removeMember(c, memberUuid);
        assertFalse(c.members().contains(memberUuid));
    }

    @Test
    void transferOwnerChangesOwner() {
        ProtectionConfig cfg = makeConfigWithTier("advanced", 16, 16, 8);
        ClaimIndex idx = new ClaimIndex();
        ProtectionClaim c = makeClaim(ownerUuid, 0, 0, 16, "advanced", new HashSet<>());
        ClaimManager mgr = new ClaimManager(cfg, idx, null, null);

        UUID newOwner = UUID.randomUUID();
        assertTrue(mgr.transferOwner(c, newOwner));
        assertEquals(newOwner, c.ownerUuid());
    }

    @Test
    void transferOwnerDisabledByConfig() {
        ProtectionConfig cfg = new ProtectionConfig(
                true, 16, 16, false, null, 27,
                Duration.ofHours(24), Duration.ofHours(24), Duration.ofHours(6),
                Duration.ofHours(24), Duration.ofHours(48),
                null, 64, 8, false, Duration.ofDays(7), // ownerTransfer=false
                true, true, true, 41001, "dreamcraft:protection_wardrobe",
                null, null, 0, 100,
                Map.of("advanced", new TierDefinition("advanced", 16, 16, 8)), Map.of(), Map.of(), Map.of()
        );
        ClaimManager mgr = new ClaimManager(cfg, new ClaimIndex(), null, null);
        ProtectionClaim c = makeClaim(ownerUuid, 0, 0, 16, "advanced", new HashSet<>());
        assertFalse(mgr.transferOwner(c, UUID.randomUUID()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ProtectionCheckResult checkerCheck(UUID uuid, int x, int z, ProtectionAction action) {
        // We need a Player-like object — we use direct isAllowed check via claim
        Optional<ProtectionClaim> found = claimIndex.findClaim("world", x, z);
        if (found.isEmpty()) return ProtectionCheckResult.NOT_PROTECTED;
        if (!config.enabled()) return ProtectionCheckResult.PROTECTION_DISABLED;
        if (!found.get().status().isProtectionActive()) return ProtectionCheckResult.NOT_PROTECTED;
        return checker.isAllowed(uuid, found.get(), action)
                ? ProtectionCheckResult.PROTECTED_ALLOWED
                : ProtectionCheckResult.PROTECTED_DENIED;
    }

    private ProtectionClaim makeClaim(UUID owner, int x, int z, int radius, String tier, Set<UUID> members) {
        Instant now = Instant.now();
        return new ProtectionClaim(
                UUID.randomUUID(), "Test Claim", "world", owner,
                x, 64, z, radius, radius,
                ProtectionState.ACTIVE, tier,
                now, now, now, now,
                members, new HashMap<>(),
                new ClaimStats(), new UpkeepStorage(),
                x, 64, z
        );
    }

    private ProtectionConfig makeConfigWithTier(String tierKey, int radius, int buildRadius, int maxMembers) {
        Map<String, TierDefinition> tiers = new HashMap<>();
        tiers.put(tierKey, new TierDefinition(tierKey, radius, buildRadius, maxMembers));
        return new ProtectionConfig(
                true, 16, 16, false, null, 27,
                Duration.ofHours(24), Duration.ofHours(24), Duration.ofHours(6),
                Duration.ofHours(24), Duration.ofHours(48),
                null, 64, maxMembers, true, Duration.ofDays(7),
                true, true, true, 41001, "dreamcraft:protection_wardrobe",
                null, null, 0, 100,
                tiers,
                Map.of("basic", 1, "reinforced", 3, "advanced", 10, "special", 15, "light", 1),
                Map.of(),
                Map.of()
        );
    }
}
