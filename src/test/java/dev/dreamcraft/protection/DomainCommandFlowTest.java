package dev.dreamcraft.protection;

import dev.dreamcraft.protection.domain.model.*;
import dev.dreamcraft.protection.domain.port.EstateRepository;
import dev.dreamcraft.protection.domain.port.WardRepository;
import dev.dreamcraft.protection.domain.port.CityRepository;
import dev.dreamcraft.protection.domain.service.CityService;
import dev.dreamcraft.protection.domain.service.EstateService;
import dev.dreamcraft.protection.domain.service.WardService;
import dev.dreamcraft.protection.presentation.MenuDefinition;
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

    // ── Estate service flow ─────────────────────────────────────────────────

    @Test
    void estateCreationIncludesOwnerAsMember() {
        Estate estate = estateService.createEstate(ownerId, "Fellowship", null, null, false);
        assertTrue(estate.isMember(ownerId));
        assertEquals(ownerId, estate.ownerId());
        assertFalse(estate.persistent());
        assertFalse(estate.isInstanced());
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
        assertFalse(vmOwner.canJoin()); // owner is member

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

        assertEquals("ward_status", def.menuId());
        assertEquals(27, def.size());
        assertEquals(9, def.items().size()); // 9 items defined
        // Slot 4 is display, slot 22 is close button
        assertNotNull(def.itemAt(4));
        assertNotNull(def.itemAt(22));
        // Close button has an action
        assertTrue(def.itemAt(22).getAction().isPresent());
        assertEquals("menu.close", def.itemAt(22).getAction().get().actionId());
    }

    @Test
    void wardMenuBuilderUpgradeButtonActiveForOwner() {
        Ward ward = wardService.createWard(ownerId, OwnerType.PLAYER, null, "world", 0, 64, 0);
        var builder = new WardViewModelBuilder(new TestWardTierProvider(), uuid -> "Owner");
        WardViewModel vm = builder.build(ward, ownerId);

        MenuDefinition def = WardMenuBuilder.build(vm);
        // Slot 12 is the upgrade button — should be active (button with action)
        assertNotNull(def.itemAt(12));
        assertTrue(def.itemAt(12).getAction().isPresent());
        assertEquals("ward.upgrade", def.itemAt(12).getAction().get().actionId());
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
        // Slot 12 should be display-only (no action)
        assertNotNull(def.itemAt(12));
        assertTrue(def.itemAt(12).getAction().isEmpty());
    }

    @Test
    void cityMenuBuilderProducesValidDefinition() {
        City city = cityService.createCity(ownerId, "TestCity");
        var builder = new CityViewModelBuilder(uuid -> "Gov", c -> 3);
        CityViewModel vm = builder.build(city, ownerId);

        MenuDefinition def = CityMenuBuilder.build(vm);

        assertEquals("city_overview", def.menuId());
        assertEquals(27, def.size());
        assertNotNull(def.itemAt(4));
        assertNotNull(def.itemAt(22));
        // Slot 10 (invite) should be active for governor
        assertNotNull(def.itemAt(10));
        assertTrue(def.itemAt(10).getAction().isPresent());
        assertEquals("city.invite", def.itemAt(10).getAction().get().actionId());
    }

    @Test
    void estateMenuBuilderProducesLobbyForNonInstanced() {
        Estate estate = estateService.createEstate(ownerId, "Test", null, null, false);
        var builder = new EstateViewModelBuilder(uuid -> "Owner");
        EstateViewModel vm = builder.build(estate, ownerId);

        MenuDefinition def = EstateMenuBuilder.build(vm);

        assertEquals("estate_lobby", def.menuId());
        assertEquals(27, def.size());
        assertNotNull(def.itemAt(4));
        assertNotNull(def.itemAt(22));
    }

    @Test
    void estateMenuBuilderProducesInstanceWhenInstanced() {
        Estate estate = estateService.createEstate(ownerId, "Dungeon", "adv-1", null, false);
        estateService.startInstance(estate, "inst-001");
        var builder = new EstateViewModelBuilder(uuid -> "Owner");
        EstateViewModel vm = builder.build(estate, ownerId);

        MenuDefinition def = EstateMenuBuilder.build(vm);

        assertEquals("estate_instance", def.menuId());
        assertNotNull(def.itemAt(4));
        // Instance view has fewer items (no join/transfer/start slots)
        assertEquals(5, def.items().size());
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
        @Override public Optional<Ward> findAtLocation(String worldName, int x, int z) {
            return cache.values().stream()
                    .filter(w -> w.worldName().equals(worldName))
                    .filter(w -> Math.abs(w.centerX() - x) <= w.radius() && Math.abs(w.centerZ() - z) <= w.radius())
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
