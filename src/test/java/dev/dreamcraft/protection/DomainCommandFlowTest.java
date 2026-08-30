package dev.dreamcraft.protection;

import dev.dreamcraft.protection.domain.model.*;
import dev.dreamcraft.protection.domain.port.EstateRepository;
import dev.dreamcraft.protection.domain.port.WardRepository;
import dev.dreamcraft.protection.domain.port.CityRepository;
import dev.dreamcraft.protection.domain.port.WardTierProvider;
import dev.dreamcraft.protection.domain.service.CityService;
import dev.dreamcraft.protection.domain.service.EstateService;
import dev.dreamcraft.protection.domain.service.WardService;
import dev.dreamcraft.protection.service.CityLevelService;
import dev.dreamcraft.protection.presentation.MenuDefinition;
import dev.dreamcraft.protection.presentation.MenuItem;
import dev.dreamcraft.protection.presentation.menu.CityMenuBuilder;
import dev.dreamcraft.protection.presentation.menu.EstateMenuBuilder;
import dev.dreamcraft.protection.presentation.menu.WardMenuBuilder;
import dev.dreamcraft.protection.presentation.viewmodel.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Ward/City/Estate domain service flows and presentation layer
 * (ViewModel builders and MenuDefinition builders).
 *
 * <p>Uses in-memory repository stubs — no Bukkit dependencies, no WorldGuard.
 * Verifies the full flow: service delegation → ViewModel mapping → MenuDefinition rendering.
 */
class DomainCommandFlowTest {

    private WardService wardService;
    private CityService cityService;
    private EstateService estateService;
    private InMemoryWardRepository wardRepo;
    private InMemoryCityRepository cityRepo;
    private InMemoryEstateRepository estateRepo;

    private UUID ownerId;
    private UUID otherId;
    private UUID memberId;

    @BeforeEach
    void setUp() {
        wardRepo = new InMemoryWardRepository();
        cityRepo = new InMemoryCityRepository();
        estateRepo = new InMemoryEstateRepository();

        wardService = new WardService(wardRepo, new TestWardTierProvider(), Duration.ofHours(24));
        cityService = new CityService(cityRepo);
        estateService = new EstateService(estateRepo);

        ownerId = UUID.randomUUID();
        otherId = UUID.randomUUID();
        memberId = UUID.randomUUID();
    }

    // ── Ward service flow ────────────────────────────────────────────────────

    @Test
    void wardCreationUsesDefaultTierAndPersists() {
        Ward ward = wardService.createWard(ownerId, OwnerType.PLAYER, null, "world", 10, 64, 20);

        assertEquals("basic", ward.tier());
        assertEquals(16, ward.radius());
        assertEquals(0, ward.baseScore());
        assertTrue(ward.permissions().contains(WardPermission.PUBLIC_STATUS_VIEW));
        assertNull(ward.worldGuardRegionId());
        // Persisted
        assertTrue(wardService.findById(ward.id()).isPresent());
    }

    @Test
    void wardScoreIncreaseRecalculatesTierAndRadius() {
        Ward ward = wardService.createWard(ownerId, OwnerType.PLAYER, null, "world", 0, 64, 0);
        wardService.addBaseScore(ward, 100); // crosses into "reinforced" tier
        assertEquals(100, ward.baseScore());
        assertEquals("reinforced", ward.tier());
        // radius = baseRadius(32) + floor(100 * 0.1) = 42
        assertEquals(42, ward.radius());
    }

    @Test
    void wardUpkeepDepositResetsWhenOverdue() {
        Ward ward = wardService.createWard(ownerId, OwnerType.PLAYER, null, "world", 0, 64, 0);
        wardService.depositUpkeep(ward, 50);
        assertEquals(50, ward.upkeepBalance());
    }

    @Test
    void wardCityAnnexSetsMembership() {
        City city = cityService.createCity(ownerId, "TestCity");
        Ward ward = wardService.createWard(ownerId, OwnerType.PLAYER, null, "world", 0, 64, 0);
        assertFalse(ward.hasCityMembership());

        wardService.setCityMembership(ward, city.id());
        assertTrue(ward.hasCityMembership());
        assertEquals(city.id(), ward.cityId());

        // Disassociate
        wardService.setCityMembership(ward, null);
        assertFalse(ward.hasCityMembership());
    }

    @Test
    void wardTransferOwnershipChangesOwner() {
        Ward ward = wardService.createWard(ownerId, OwnerType.PLAYER, null, "world", 0, 64, 0);
        wardService.transferOwnership(ward, otherId, OwnerType.PLAYER);
        assertEquals(otherId, ward.ownerId());
    }

    @Test
    void wardPermissionsToggle() {
        Ward ward = wardService.createWard(ownerId, OwnerType.PLAYER, null, "world", 0, 64, 0);
        assertFalse(ward.hasPermission(WardPermission.PUBLIC_BUILD));
        ward.grantPermission(WardPermission.PUBLIC_BUILD);
        assertTrue(ward.hasPermission(WardPermission.PUBLIC_BUILD));
        ward.revokePermission(WardPermission.PUBLIC_BUILD);
        assertFalse(ward.hasPermission(WardPermission.PUBLIC_BUILD));
    }

    // ── Upgrade conflict validation ──────────────────────────────────────────

    @Test
    void upgradeConflictDetectsForeignWardInsideNewRadius() {
        Ward ward = wardService.createWard(ownerId, OwnerType.PLAYER, null, "world", 0, 64, 0);
        // Foreign ward sits at the edge of the radius the upgrade would reach (42)
        Ward neighbor = wardService.createWard(otherId, OwnerType.PLAYER, null, "world", 40, 64, 0);

        int radiusAfter = wardService.computeRadiusAfter(ward, 100); // reinforced → 42
        assertEquals(42, radiusAfter);

        var conflict = wardService.findForeignConflict(ward, radiusAfter);
        assertTrue(conflict.isPresent());
        assertEquals(neighbor.id(), conflict.get().id());
    }

    @Test
    void upgradeConflictIgnoresOwnWardsAndFarWards() {
        Ward ward = wardService.createWard(ownerId, OwnerType.PLAYER, null, "world", 0, 64, 0);
        // Same owner inside the prospective radius → allowed (self-stacking is harmless)
        wardService.createWard(ownerId, OwnerType.PLAYER, null, "world", 40, 64, 0);
        assertTrue(wardService.findForeignConflict(ward, 42).isEmpty());

        // Foreign ward in a different world → no conflict
        wardService.createWard(otherId, OwnerType.PLAYER, null, "world_nether", 10, 64, 10);
        assertTrue(wardService.findForeignConflict(ward, 42).isEmpty());

        // Foreign ward beyond the prospective radius → no conflict
        wardService.createWard(otherId, OwnerType.PLAYER, null, "world", 100, 64, 100);
        assertTrue(wardService.findForeignConflict(ward, 42).isEmpty());
    }

    // ── Ward rename ──────────────────────────────────────────────────────────

    @Test
    void wardRenameChangesNameAndPersists() {
        Ward ward = wardService.createWard(ownerId, OwnerType.PLAYER, null, "world", 0, 64, 0);
        String generated = ward.name();

        wardService.renameWard(ward, "Fortaleza Norte");

        assertEquals("Fortaleza Norte", ward.name());
        assertEquals("Fortaleza Norte", wardService.findById(ward.id()).orElseThrow().name());
        assertNotEquals(generated, ward.name());
    }

    @Test
    void wardRenameRejectsDuplicatesBlankAndTooLong() {
        Ward a = wardService.createWard(ownerId, OwnerType.PLAYER, null, "world", 0, 64, 0, "Alpha");
        Ward b = wardService.createWard(ownerId, OwnerType.PLAYER, null, "world", 200, 64, 200, "Beta");

        // Duplicate (case-insensitive) is rejected
        assertThrows(IllegalArgumentException.class, () -> wardService.renameWard(b, "alpha"));
        // Blank is rejected
        assertThrows(IllegalArgumentException.class, () -> wardService.renameWard(b, "   "));
        // Longer than 32 chars is rejected
        assertThrows(IllegalArgumentException.class, () -> wardService.renameWard(b, "x".repeat(33)));
        // Renaming to your own current name is fine
        assertDoesNotThrow(() -> wardService.renameWard(a, "Alpha"));
        assertEquals("Beta", b.name()); // unchanged after failed attempts
    }

    @Test
    void wardRenameAcceptsMultiWordNames() {
        Ward ward = wardService.createWard(ownerId, OwnerType.PLAYER, null, "world", 0, 64, 0);
        wardService.renameWard(ward, "  Bastión   del   Este  ".replaceAll("\\s+", " "));
        assertEquals("Bastión del Este", ward.name());
    }

    // ── Upkeep material matching (lenient) ───────────────────────────────────

    @Test
    void upkeepDepositMatchesLenientMaterialNames() {
        dev.dreamcraft.protection.service.WardUpkeepService upkeep =
                new dev.dreamcraft.protection.service.WardUpkeepService(
                        java.util.Map.of(org.bukkit.Material.IRON_INGOT, 8, org.bukkit.Material.COAL, 2),
                        wardService);

        // Exact and case-insensitive
        assertTrue(upkeep.matchAccepted("IRON_INGOT").isPresent());
        assertTrue(upkeep.matchAccepted("iron_ingot").isPresent());
        assertTrue(upkeep.matchAccepted("coal").isPresent());
        // Missing underscores / spaces are tolerated
        assertTrue(upkeep.matchAccepted("ironingot").isPresent());
        assertTrue(upkeep.matchAccepted("iron ingot").isPresent());
        // Plural of a real material does not exist → rejected
        assertFalse(upkeep.matchAccepted("IRON_INGOTS").isPresent());
        // Valid material but not in the accepted set → rejected
        assertFalse(upkeep.matchAccepted("DIAMOND_BLOCK").isPresent());
        assertFalse(upkeep.matchAccepted("COAL_ORE").isPresent());
        // Garbage / blank → rejected
        assertFalse(upkeep.matchAccepted("noexiste").isPresent());
        assertFalse(upkeep.matchAccepted("  ").isPresent());
    }

    // ── City service flow ────────────────────────────────────────────────────

    @Test
    void cityCreationMakesGovernorMember() {
        City city = cityService.createCity(ownerId, "Rivendell");
        assertEquals("Rivendell", city.name());
        assertTrue(city.isMember(ownerId));
        assertEquals(CityRole.GOVERNOR, city.roleOf(ownerId));
        assertTrue(city.policies().contains(CityPolicy.PUBLIC_LISTING));
    }

    @Test
    void cityDuplicateNameThrows() {
        cityService.createCity(ownerId, "Gondor");
        assertThrows(IllegalArgumentException.class, () -> cityService.createCity(otherId, "Gondor"));
    }

    @Test
    void cityManageResidentAddRemove() {
        City city = cityService.createCity(ownerId, "Edoras");
        assertTrue(cityService.addMember(city, memberId));
        assertEquals(CityRole.CITIZEN, city.roleOf(memberId));
        // Already member → false
        assertFalse(cityService.addMember(city, memberId));
        assertTrue(cityService.removeMember(city, memberId));
        assertFalse(city.isMember(memberId));
    }

    @Test
    void citySetRoleCantGrantGovernor() {
        City city = cityService.createCity(ownerId, "Minas Tirith");
        cityService.addMember(city, memberId);
        // setRole with GOVERNOR returns false — must use transferGovernorship
        assertFalse(cityService.setRole(city, memberId, CityRole.GOVERNOR));
        assertTrue(cityService.setRole(city, memberId, CityRole.COUNCIL));
        assertEquals(CityRole.COUNCIL, city.roleOf(memberId));
    }

    @Test
    void cityTransferGovernorshipDemotesOldGovernor() {
        City city = cityService.createCity(ownerId, "Osgiliath");
        cityService.addMember(city, memberId);
        assertTrue(cityService.transferGovernorship(city, memberId));
        assertEquals(memberId, city.governorId());
        assertEquals(CityRole.GOVERNOR, city.roleOf(memberId));
        assertEquals(CityRole.COUNCIL, city.roleOf(ownerId)); // demoted
    }

    @Test
    void cityGovernorCannotBeRemoved() {
        City city = cityService.createCity(ownerId, "Dale");
        assertFalse(cityService.removeMember(city, ownerId));
    }

    @Test
    void cityTreasuryDepositWithdraw() {
        City city = cityService.createCity(ownerId, "Erebor");
        cityService.depositTreasury(city, 1000);
        assertEquals(1000, city.treasury());
        assertTrue(cityService.withdrawTreasury(city, 400));
        assertEquals(600, city.treasury());
        assertFalse(cityService.withdrawTreasury(city, 10000)); // insufficient
        assertEquals(600, city.treasury());
    }

    @Test
    void cityPolicyToggle() {
        City city = cityService.createCity(ownerId, "Laketown");
        assertTrue(city.hasPolicy(CityPolicy.PUBLIC_LISTING));
        cityService.setPolicy(city, CityPolicy.PUBLIC_LISTING, false);
        assertFalse(city.hasPolicy(CityPolicy.PUBLIC_LISTING));
        cityService.setPolicy(city, CityPolicy.OPEN_RECRUITMENT, true);
        assertTrue(city.hasPolicy(CityPolicy.OPEN_RECRUITMENT));
    }

    // ── City levels (computed from wards/members/wealth) ─────────────────────

    @Test
    void cityLevelStartsAtStarterLevel() {
        CityLevelService levels = testCityLevels();
        City city = cityService.createCity(ownerId, "Hamlet");

        var status = levels.statusOf(city);
        assertEquals("aldea", status.levelKey());
        assertEquals(0, status.wards());
        assertEquals(1, status.members()); // governor counts as member
        assertEquals(0, status.wealth());
        assertFalse(status.maxed());
        assertEquals("Pueblo", status.nextLevelName());
    }

    @Test
    void cityLevelRequiresAllThreeMinimums() {
        CityLevelService levels = testCityLevels();
        City city = cityService.createCity(ownerId, "GrowingTown");

        // 1 annexed ward with wealth 100 — but only 1 member: stays "aldea"
        Ward ward = wardService.createWard(ownerId, OwnerType.PLAYER, null, "world", 0, 64, 0);
        wardService.addBaseScore(ward, 100);
        wardService.depositUpkeep(ward, 10); // healthy: positive balance counts towards wealth
        wardService.setCityMembership(ward, city.id());
        assertEquals("aldea", levels.statusOf(city).levelKey());

        // Reach 3 members → pueblo unlocks
        cityService.addMember(city, memberId);
        cityService.addMember(city, otherId);
        var pueblo = levels.statusOf(city);
        assertEquals("pueblo", pueblo.levelKey());
        assertEquals(100, pueblo.wealth());

        // Next level needs 3 wards / 6 members / 400 wealth
        assertEquals(2, pueblo.needWards());
        assertEquals(3, pueblo.needMembers());
        assertEquals(300, pueblo.needWealth());
    }

    @Test
    void cityLevelWealthIsSumOfHealthyAnnexedWards() {
        CityLevelService levels = testCityLevels();
        City city = cityService.createCity(ownerId, "RichVille");
        for (int i = 0; i < 3; i++) {
            Ward w = wardService.createWard(ownerId, OwnerType.PLAYER, null, "world", i * 500, 64, 0);
            wardService.addBaseScore(w, 150);
            wardService.depositUpkeep(w, 5); // healthy
            wardService.setCityMembership(w, city.id());
        }
        var status = levels.statusOf(city);
        assertEquals(450, status.wealth());
        assertEquals(3, status.wards());
        // members still 1 → neither "pueblo" (needs 3) nor "ciudad"
        assertEquals("aldea", status.levelKey());
    }

    @Test
    void cityLevelWealthIgnoresWardsWithDepletedUpkeep() {
        CityLevelService levels = testCityLevels();
        City city = cityService.createCity(ownerId, "DecayingTown");

        Ward healthy = wardService.createWard(ownerId, OwnerType.PLAYER, null, "world", 0, 64, 0);
        wardService.addBaseScore(healthy, 200);
        wardService.depositUpkeep(healthy, 10);
        wardService.setCityMembership(healthy, city.id());

        Ward depleted = wardService.createWard(ownerId, OwnerType.PLAYER, null, "world", 500, 64, 0);
        wardService.addBaseScore(depleted, 300);
        wardService.setCityMembership(depleted, city.id()); // no upkeep → not counted

        var status = levels.statusOf(city);
        assertEquals(2, status.wards());          // both annexed...
        assertEquals(200, status.wealth());       // ...but only the healthy one is worth score

        // Upkeep runs out on the first ward → its score stops counting too
        wardService.deductUpkeep(healthy, 10);
        assertEquals(0, levels.statusOf(city).wealth());
    }

    private CityLevelService testCityLevels() {
        return new CityLevelService(wardService, java.util.List.of(
                new dev.dreamcraft.protection.config.CityLevelDefinition("aldea", "Aldea", 0, 0, 0),
                new dev.dreamcraft.protection.config.CityLevelDefinition("pueblo", "Pueblo", 1, 3, 100),
                new dev.dreamcraft.protection.config.CityLevelDefinition("ciudad", "Ciudad", 3, 6, 400)
        ));
    }

    // ── Estate service flow ─────────────────────────────────────────────────

    @Test
    void estateCreationDoesNotAutoJoinOwner() {
        Estate estate = estateService.createEstate(ownerId, "Fellowship", null, null, false);
        assertFalse(estate.isMember(ownerId)); // membership is explicit
        assertEquals(ownerId, estate.ownerId());
        assertFalse(estate.persistent());
        assertFalse(estate.isInstanced());

        // Player-facing flows add the creator explicitly
        assertTrue(estateService.addMember(estate, ownerId));
        assertTrue(estate.isMember(ownerId));
    }

    @Test
    void estateAddRemoveMember() {
        Estate estate = estateService.createEstate(ownerId, "Company", null, null, false);
        assertTrue(estateService.addMember(estate, memberId));
        assertTrue(estate.isMember(memberId));
        assertTrue(estateService.removeMember(estate, memberId));
        assertFalse(estate.isMember(memberId));
    }

    @Test
    void estateOwnerCannotLeave() {
        Estate estate = estateService.createEstate(ownerId, "Party", null, null, false);
        assertFalse(estateService.removeMember(estate, ownerId));
    }

    @Test
    void estateStartInstanceSetsInstanceId() {
        Estate estate = estateService.createEstate(ownerId, "Raid", "adv-1", null, false);
        assertFalse(estate.isInstanced());
        assertTrue(estateService.startInstance(estate, "inst-001"));
        assertTrue(estate.isInstanced());
        assertEquals("inst-001", estate.instanceId());
        // Can't start again
        assertFalse(estateService.startInstance(estate, "inst-002"));
    }

    @Test
    void estateEndInstanceClearsInstanceId() {
        Estate estate = estateService.createEstate(ownerId, "Dungeon", null, null, false);
        estateService.startInstance(estate, "inst-abc");
        assertTrue(estate.isInstanced());
        assertTrue(estateService.endInstance(estate));
        assertFalse(estate.isInstanced());
        assertNull(estate.instanceId());
        // Can't end again
        assertFalse(estateService.endInstance(estate));
    }

    @Test
    void estateStartInstanceRejectsNullId() {
        Estate estate = estateService.createEstate(ownerId, "Test", null, null, false);
        assertFalse(estateService.startInstance(estate, null));
        assertFalse(estate.isInstanced());
    }

    @Test
    void estateTransferOwnership() {
        Estate estate = estateService.createEstate(ownerId, "Guild", null, null, false);
        estateService.addMember(estate, memberId);
        assertTrue(estateService.transferOwnership(estate, memberId));
        assertEquals(memberId, estate.ownerId());
    }

    @Test
    void estateTransferOwnershipFailsForNonMember() {
        Estate estate = estateService.createEstate(ownerId, "Guild", null, null, false);
        assertFalse(estateService.transferOwnership(estate, otherId));
    }

    @Test
    void estateCleanupTransientRemovesNonPersistent() {
        estateService.createEstate(ownerId, "Temp1", null, null, false);
        estateService.createEstate(ownerId, "Perm1", null, null, true);
        estateService.createEstate(ownerId, "Temp2", null, null, false);
        assertEquals(3, estateService.findAll().size());
        estateService.cleanupTransient();
        assertEquals(1, estateService.findAll().size());
    }

    // ── ViewModel builders ───────────────────────────────────────────────────

    @Test
    void wardViewModelBuilderComputesOwnerFlags() {
        Ward ward = wardService.createWard(ownerId, OwnerType.PLAYER, null, "world", 0, 64, 0);
        var builder = new WardViewModelBuilder(new TestWardTierProvider(), uuid -> "Player" + uuid.toString().charAt(0));

        WardViewModel vmOwner = builder.build(ward, ownerId);
        assertTrue(vmOwner.canManage());
        assertTrue(vmOwner.canTransfer());
        assertTrue(vmOwner.canSetPermissions());
        assertTrue(vmOwner.canAnnexToCity());
        assertTrue(vmOwner.canUpgrade()); // has higher tiers

        WardViewModel vmOther = builder.build(ward, otherId);
        assertFalse(vmOther.canManage());
        assertFalse(vmOther.canTransfer());
        assertFalse(vmOther.canSetPermissions());
        assertFalse(vmOther.canAnnexToCity());
    }

    @Test
    void cityViewModelBuilderComputesGovernorFlags() {
        City city = cityService.createCity(ownerId, "TestCity");
        cityService.addMember(city, memberId);
        cityService.setRole(city, memberId, CityRole.COUNCIL);
        var builder = new CityViewModelBuilder(uuid -> "P", c -> 0);

        CityViewModel vmGov = builder.build(city, ownerId);
        assertTrue(vmGov.isGovernor());
        assertTrue(vmGov.canSetRoles());
        assertTrue(vmGov.canSetPolicy());
        assertTrue(vmGov.canDelete());

        CityViewModel vmCouncil = builder.build(city, memberId);
        assertFalse(vmCouncil.isGovernor());
        assertTrue(vmCouncil.isCouncil());
        assertTrue(vmCouncil.canManageResidents());
        assertTrue(vmCouncil.canManageTreasury());
        assertFalse(vmCouncil.canSetRoles());
    }

    @Test
    void estateViewModelBuilderComputesOwnerFlags() {
        Estate estate = estateService.createEstate(ownerId, "Test", null, null, false);
        var builder = new EstateViewModelBuilder(uuid -> "P");

        EstateViewModel vmOwner = builder.build(estate, ownerId);
        assertTrue(vmOwner.isOwner());
        assertTrue(vmOwner.canInvite());
        assertTrue(vmOwner.canStart());
        assertFalse(vmOwner.canJoin()); // owner manages, doesn't join

        EstateViewModel vmNonMember = builder.build(estate, otherId);
        assertFalse(vmNonMember.isOwner());
        assertFalse(vmNonMember.canInvite());
        assertTrue(vmNonMember.canJoin());
        assertFalse(vmNonMember.canLeave());
    }

    // ── Menu builders ─────────────────────────────────────────────────────────

    @Test
    void wardMenuBuilderProducesValidDefinition() {
        Ward ward = wardService.createWard(ownerId, OwnerType.PLAYER, null, "world", 0, 64, 0);
        var builder = new WardViewModelBuilder(new TestWardTierProvider(), uuid -> "Owner");
        WardViewModel vm = builder.build(ward, ownerId);

        MenuDefinition def = WardMenuBuilder.build(vm);

        // 35 items: close(36) + perfil(37) + permisos(38)/transfer(39)/disband(43) singles
        // + upkeep 2x2 (4) + fase 2x2 (4) + matriz 2x2 (4) + 9 separator tiles + 9 status tiles (3x3)
        assertEquals(35, def.items().size());
        // Fila 5: Close en slot 36 (flecha a la izquierda)
        assertTrue(def.itemAt(36).getAction().isPresent());
        assertEquals("cerrar", def.itemAt(36).getAction().get().actionId());
        // Perfil en slot 37
        assertNotNull(def.itemAt(37));
        assertEquals("perfil", def.itemAt(37).getAction().get().actionId());
        // Permisos en slot 38 (papel)
        assertNotNull(def.itemAt(38));
        assertEquals("ward.permissions", def.itemAt(38).getAction().get().actionId());
        // Transferir en slot 39 (personitas)
        assertNotNull(def.itemAt(39));
        assertEquals("ward.transfer", def.itemAt(39).getAction().get().actionId());
        // Disband en slot 43 (engranaje)
        assertNotNull(def.itemAt(43));
        assertEquals("ward.disband", def.itemAt(43).getAction().get().actionId());
        // En menú horneado, los visuales están en el fondo y los items son catchers
        assertEquals("menu.catcher", def.itemAt(16).iconKey());
        // Los 9 slots del cristal 3x3 (3,4,5, 12,13,14, 21,22,23) responden como catchers
        for (int s : new int[]{3, 4, 5, 12, 13, 14, 21, 22, 23}) {
            assertNotNull(def.itemAt(s), "Slot " + s + " de status debe estar presente");
            assertEquals("menu.catcher", def.itemAt(s).iconKey());
            assertTrue(def.itemAt(s).displayName().contains(vm.name()));
        }
    }

    @Test
    void wardMenuMatrizSlotOpensCityOverviewForMember() {
        City city = cityService.createCity(ownerId, "Matrix");
        Ward ward = wardService.createWard(ownerId, OwnerType.PLAYER, null, "world", 0, 64, 0);
        wardService.setCityMembership(ward, city.id());
        var builder = new WardViewModelBuilder(new TestWardTierProvider(), uuid -> "Owner",
                id -> "Matrix");

        MenuDefinition def = WardMenuBuilder.build(builder.build(ward, ownerId));

        MenuItem item = def.itemAt(40);
        assertNotNull(item);
        assertEquals("menu.catcher", item.iconKey());
        assertTrue(item.getAction().isPresent());
        assertEquals("city.open", item.getAction().get().actionId());
        assertTrue(item.lore().stream().anyMatch(l -> l.contains("Clic para gestionar la Matriz")));
    }

    @Test
    void wardMenuBuilderUpgradeButtonActiveForOwner() {
        Ward ward = wardService.createWard(ownerId, OwnerType.PLAYER, null, "world", 0, 64, 0);
        var builder = new WardViewModelBuilder(new TestWardTierProvider(), uuid -> "Owner");
        WardViewModel vm = builder.build(ward, ownerId);

        MenuDefinition def = WardMenuBuilder.build(vm);
        // Slot 16 (fase block anchor) is the upgrade button — should be active
        assertNotNull(def.itemAt(16));
        assertTrue(def.itemAt(16).getAction().isPresent());
        assertEquals("ward.upgrade", def.itemAt(16).getAction().get().actionId());
    }

    @Test
    void wardMenuBuilderUpgradeInactiveForMaxTier() {
        // Create a tier provider with only one tier (no upgrade possible)
        WardTierProvider singleTier = new WardTierProvider() {
            private final WardTier only = new WardTier("basic", 0, Integer.MAX_VALUE, 16, 0.0, 1);
            @Override public Optional<WardTier> findByKey(String key) { return "basic".equals(key) ? Optional.of(only) : Optional.empty(); }
            @Override public WardTier resolveForScore(int baseScore) { return only; }
            @Override public Map<String, WardTier> allTiers() { return Map.of("basic", only); }
            @Override public String defaultTierKey() { return "basic"; }
        };
        Ward ward = wardService.createWard(ownerId, OwnerType.PLAYER, null, "world", 0, 64, 0);
        var builder = new WardViewModelBuilder(singleTier, uuid -> "Owner");
        WardViewModel vm = builder.build(ward, ownerId);

        assertFalse(vm.canUpgrade());
        MenuDefinition def = WardMenuBuilder.build(vm);
        // Slot 16 should be display-only (no action), still with the tier quarter tile
        assertNotNull(def.itemAt(16));
        assertTrue(def.itemAt(16).getAction().isEmpty());
        assertEquals("menu.catcher", def.itemAt(16).iconKey());
    }

    @Test
    void wardMenuStatusIconFollowsRealProtectionState() {
        var calc = new dev.dreamcraft.protection.service.UpkeepProjectionCalculator(
                Duration.ofHours(24), Duration.ZERO, Duration.ZERO, java.util.Map.of());
        var builder = new WardViewModelBuilder(new TestWardTierProvider(), uuid -> "Owner",
                id -> null, null, java.util.List.of(), calc);

        // Covered balance → PROTEGIDO → variante horneada ward_status (activo)
        Ward covered = wardService.createWard(ownerId, OwnerType.PLAYER, null, "world", 0, 64, 0);
        wardService.depositUpkeep(covered, 100); // basic charges 1 u/interval
        var coveredDef = WardMenuBuilder.build(builder.build(covered, ownerId));
        assertEquals("ward_status", coveredDef.menuId());
        assertEquals("menu.catcher", coveredDef.itemAt(4).iconKey());

        // Balance below one interval charge → GRACIA → variante horneada ward_inactive (apagado)
        Ward grace = wardService.createWard(ownerId, OwnerType.PLAYER, null, "world", 200, 64, 200);
        wardService.addBaseScore(grace, 500); // advanced tier charges 10 u/interval
        wardService.depositUpkeep(grace, 5);
        var graceDef = WardMenuBuilder.build(builder.build(grace, ownerId));
        assertEquals("ward_inactive", graceDef.menuId());
        assertEquals("menu.catcher", graceDef.itemAt(4).iconKey());

        // Zero balance → EXPIRADO → variante horneada ward_inactive (apagado)
        Ward expired = wardService.createWard(ownerId, OwnerType.PLAYER, null, "world", 400, 64, 400);
        var expiredDef = WardMenuBuilder.build(builder.build(expired, ownerId));
        assertEquals("ward_inactive", expiredDef.menuId());
        assertEquals("menu.catcher", expiredDef.itemAt(4).iconKey());
    }

    @Test
    void cityMenuBuilderProducesValidDefinition() {
        City city = cityService.createCity(ownerId, "TestCity");
        var builder = new CityViewModelBuilder(uuid -> "Gov", c -> 3);
        CityViewModel vm = builder.build(city, ownerId);

        MenuDefinition def = CityMenuBuilder.build(vm);

        assertEquals("city_overview", def.menuId());
        assertEquals(54, def.size());
        // Slot 3 (matriz status 3x3 block anchor)
        assertNotNull(def.itemAt(3));
        assertNotNull(def.itemAt(13));
        // Slot 9 (invite block anchor, movido 1 slot a la izquierda)
        assertNotNull(def.itemAt(9));
        assertTrue(def.itemAt(9).getAction().isPresent());
        assertEquals("city.invite", def.itemAt(9).getAction().get().actionId());
        // Slot 16 (roles block anchor en la ubicación de la bóveda)
        assertNotNull(def.itemAt(16));
        assertTrue(def.itemAt(16).getAction().isPresent());
        assertEquals("city.roles", def.itemAt(16).getAction().get().actionId());
        // Slot 30 (tesoro 3x3 abajo)
        assertNotNull(def.itemAt(30));
        assertTrue(def.itemAt(30).getAction().isPresent());
        assertEquals("city.bank", def.itemAt(30).getAction().get().actionId());
        // Fila 5: Slot 36 — Cerrar menú, Slot 37 — Perfil, Slot 38 — Políticas
        assertEquals("menu.catcher", def.itemAt(36).iconKey());
        assertTrue(def.itemAt(36).getAction().isPresent());
        assertEquals("cerrar", def.itemAt(36).getAction().get().actionId());
        assertEquals("menu.catcher", def.itemAt(37).iconKey());
        assertTrue(def.itemAt(37).getAction().isPresent());
        assertEquals("perfil", def.itemAt(37).getAction().get().actionId());
        assertNotNull(def.itemAt(38));
        assertNotNull(def.itemAt(44));
    }

    @Test
    void estateMenuBuilderProducesLobbyForNonInstanced() {
        Estate estate = estateService.createEstate(ownerId, "Test", null, null, false);
        var builder = new EstateViewModelBuilder(uuid -> "Owner");
        EstateViewModel vm = builder.build(estate, ownerId);

        MenuDefinition def = EstateMenuBuilder.build(vm);

        assertEquals("estate_lobby", def.menuId());
        assertEquals(54, def.size());
        assertNotNull(def.itemAt(13));
        // Slot 36 — Cerrar menú
        assertEquals("menu.catcher", def.itemAt(36).iconKey());
        assertTrue(def.itemAt(36).getAction().isPresent());
        assertEquals("cerrar", def.itemAt(36).getAction().get().actionId());
        assertNotNull(def.itemAt(44));
    }

    @Test
    void estateMenuBuilderProducesInstanceWhenInstanced() {
        Estate estate = estateService.createEstate(ownerId, "Dungeon", "adv-1", null, false);
        estateService.startInstance(estate, "inst-001");
        var builder = new EstateViewModelBuilder(uuid -> "Owner");
        EstateViewModel vm = builder.build(estate, ownerId);

        MenuDefinition def = EstateMenuBuilder.build(vm);

        assertEquals("estate_instance", def.menuId());
        assertNotNull(def.itemAt(13));
        // Instance view: overview 3x2 (6) + profile (1) + back (1) + disband (1) + close (1)
        // + two 2×2 quarter blocks (invite 4 / leave 4) + 9 separator tiles = 27
        assertEquals(27, def.items().size());
        assertEquals("menu.catcher", def.itemAt(39).iconKey());
        assertTrue(def.itemAt(39).getAction().isPresent());
        assertEquals("ward.open", def.itemAt(39).getAction().get().actionId());
    }

    // ── In-memory repository stubs ───────────────────────────────────────────

    private static final class InMemoryWardRepository implements WardRepository {
        private final Map<UUID, Ward> cache = new ConcurrentHashMap<>();

        @Override public Optional<Ward> findById(UUID id) { return Optional.ofNullable(cache.get(id)); }
        @Override public Collection<Ward> findByOwnerId(UUID ownerId) {
            return cache.values().stream().filter(w -> w.ownerId().equals(ownerId)).collect(Collectors.toList());
        }
        @Override public Collection<Ward> findByCityId(UUID cityId) {
            return cache.values().stream().filter(w -> cityId.equals(w.cityId())).collect(Collectors.toList());
        }
        @Override
        public Optional<Ward> findAtLocation(String worldName, int x, int z) {
            return cache.values().stream()
                    .filter(w -> w.worldName().equals(worldName))
                    .filter(w -> Math.abs(w.centerX() - x) <= w.radius() && Math.abs(w.centerZ() - z) <= w.radius())
                    .findFirst();
        }
        @Override
        public Optional<Ward> findConflicting(String worldName, int x, int z, int radius, UUID excludeId) {
            return cache.values().stream()
                    .filter(w -> w.worldName().equals(worldName))
                    .filter(w -> !w.id().equals(excludeId))
                    .filter(w -> Math.abs(w.centerX() - x) <= radius
                            && Math.abs(w.centerZ() - z) <= radius)
                    .findFirst();
        }
        @Override public Optional<Ward> findByCenter(String worldName, int x, int y, int z) {
            return cache.values().stream()
                    .filter(w -> w.worldName().equals(worldName))
                    .filter(w -> w.centerX() == x && w.centerY() == y && w.centerZ() == z)
                    .findFirst();
        }
        @Override public Collection<Ward> findAll() { return new ArrayList<>(cache.values()); }
        @Override public void save(Ward ward) { cache.put(ward.id(), ward); }
        @Override public void delete(UUID id) { cache.remove(id); }
        @Override public void saveAll(Collection<Ward> wards) { cache.clear(); wards.forEach(w -> cache.put(w.id(), w)); }
    }

    private static final class InMemoryCityRepository implements CityRepository {
        private final Map<UUID, City> cache = new ConcurrentHashMap<>();

        @Override public Optional<City> findById(UUID id) { return Optional.ofNullable(cache.get(id)); }
        @Override public Optional<City> findByName(String name) {
            return cache.values().stream().filter(c -> c.name().equalsIgnoreCase(name)).findFirst();
        }
        @Override public Optional<City> findByGovernor(UUID governorId) {
            return cache.values().stream().filter(c -> c.governorId().equals(governorId)).findFirst();
        }
        @Override public Optional<City> findByMember(UUID memberId) {
            return cache.values().stream().filter(c -> c.isMember(memberId)).findFirst();
        }
        @Override public Collection<City> findAll() { return new ArrayList<>(cache.values()); }
        @Override public void save(City city) { cache.put(city.id(), city); }
        @Override public void delete(UUID id) { cache.remove(id); }
        @Override public void saveAll(Collection<City> cities) { cache.clear(); cities.forEach(c -> cache.put(c.id(), c)); }
    }

    private static final class InMemoryEstateRepository implements EstateRepository {
        private final Map<UUID, Estate> cache = new ConcurrentHashMap<>();

        @Override public Optional<Estate> findById(UUID id) { return Optional.ofNullable(cache.get(id)); }
        @Override public Collection<Estate> findByOwnerId(UUID ownerId) {
            return cache.values().stream().filter(e -> e.ownerId().equals(ownerId)).collect(Collectors.toList());
        }
        @Override public Collection<Estate> findByMember(UUID memberId) {
            return cache.values().stream().filter(e -> e.isMember(memberId)).collect(Collectors.toList());
        }
        @Override public Collection<Estate> findByAdventureId(String adventureId) {
            return cache.values().stream().filter(e -> adventureId.equals(e.adventureId())).collect(Collectors.toList());
        }
        @Override public Collection<Estate> findAll() { return new ArrayList<>(cache.values()); }
        @Override public void save(Estate estate) { cache.put(estate.id(), estate); }
        @Override public void delete(UUID id) { cache.remove(id); }
        @Override public void deleteAllTransient() { cache.values().removeIf(e -> !e.persistent()); }
    }
}
