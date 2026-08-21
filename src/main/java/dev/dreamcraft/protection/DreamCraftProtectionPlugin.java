package dev.dreamcraft.protection;

import dev.dreamcraft.protection.command.CityCommand;
import dev.dreamcraft.protection.command.EstateCommand;
import dev.dreamcraft.protection.command.ProtectionCommand;
import dev.dreamcraft.protection.command.WardCommand;
import dev.dreamcraft.protection.config.ProtectionConfig;
import dev.dreamcraft.protection.domain.port.WardTierProvider;
import dev.dreamcraft.protection.domain.service.CityService;
import dev.dreamcraft.protection.domain.service.EstateService;
import dev.dreamcraft.protection.domain.service.WardService;
import dev.dreamcraft.protection.integration.chunky.ChunkyAdapter;
import dev.dreamcraft.protection.integration.chunky.ChunkyAdapterImpl;
import dev.dreamcraft.protection.integration.coreprotect.CoreProtectAdapter;
import dev.dreamcraft.protection.integration.coreprotect.CoreProtectAdapterImpl;
import dev.dreamcraft.protection.integration.essentialsx.EssentialsAdapter;
import dev.dreamcraft.protection.integration.essentialsx.EssentialsAdapterImpl;
import dev.dreamcraft.protection.integration.luckperms.LuckPermsAdapter;
import dev.dreamcraft.protection.integration.luckperms.LuckPermsAdapterImpl;
import dev.dreamcraft.protection.integration.packetevents.PacketEventsAdapter;
import dev.dreamcraft.protection.integration.packetevents.PacketEventsAdapterImpl;
import dev.dreamcraft.protection.integration.registry.CapabilityRegistry;
import dev.dreamcraft.protection.integration.worldedit.WorldEditAdapter;
import dev.dreamcraft.protection.integration.worldedit.WorldEditAdapterImpl;
import dev.dreamcraft.protection.integration.worldguard.WorldGuardAdapter;
import dev.dreamcraft.protection.integration.worldguard.WorldGuardAdapterImpl;
import dev.dreamcraft.protection.listener.HopperProtectionListener;
import dev.dreamcraft.protection.listener.PhysicsProtectionListener;
import dev.dreamcraft.protection.listener.ProtectionListener;
import dev.dreamcraft.protection.listener.RedstoneProtectionListener;
import dev.dreamcraft.protection.persistence.ConfigWardTierProvider;
import dev.dreamcraft.protection.persistence.ClaimRepository;
import dev.dreamcraft.protection.persistence.YamlCityRepository;
import dev.dreamcraft.protection.persistence.YamlEstateRepository;
import dev.dreamcraft.protection.persistence.YamlWardRepository;
import dev.dreamcraft.protection.presentation.MenuActionDispatcher;
import dev.dreamcraft.protection.presentation.VanillaMenuProvider;
import dev.dreamcraft.protection.service.*;
import dev.dreamcraft.protection.ui.ProtectionMenu;
import dev.dreamcraft.protection.ui.WardrobeItems;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Duration;

public final class DreamCraftProtectionPlugin extends JavaPlugin {

    // ── Existing protection system ────────────────────────────────────────────
    private ProtectionConfig protectionConfig;
    private ClaimManager claimManager;
    private ClaimIndex claimIndex;
    private BuildingCostService buildingCostService;
    private UpkeepCalculator upkeepCalculator;
    private UpkeepManager upkeepManager;
    private WardrobeItems wardrobeItems;
    private ProtectionChecker protectionChecker;
    private ProtectionMenu protectionMenu;

    // ── Integration layer ─────────────────────────────────────────────────────
    private CapabilityRegistry capabilityRegistry;
    private WorldGuardAdapter worldGuardAdapter;
    private LuckPermsAdapter luckPermsAdapter;
    private CoreProtectAdapter coreProtectAdapter;
    private EssentialsAdapter essentialsAdapter;
    private WorldEditAdapter worldEditAdapter;
    private ChunkyAdapter chunkyAdapter;
    private PacketEventsAdapter packetEventsAdapter;

    // ── Domain layer ──────────────────────────────────────────────────────────
    private YamlWardRepository wardRepository;
    private YamlCityRepository cityRepository;
    private YamlEstateRepository estateRepository;
    private WardService wardService;
    private CityService cityService;
    private EstateService estateService;

    // ── Presentation ──────────────────────────────────────────────────────────
    private VanillaMenuProvider menuProvider;

    // ── Public accessors (for commands / listeners) ───────────────────────────
    public CapabilityRegistry capabilityRegistry()   { return capabilityRegistry; }
    public WorldGuardAdapter  worldGuardAdapter()    { return worldGuardAdapter; }
    public LuckPermsAdapter   luckPermsAdapter()     { return luckPermsAdapter; }
    public CoreProtectAdapter coreProtectAdapter()   { return coreProtectAdapter; }
    public EssentialsAdapter  essentialsAdapter()    { return essentialsAdapter; }
    public WorldEditAdapter   worldEditAdapter()     { return worldEditAdapter; }
    public ChunkyAdapter      chunkyAdapter()        { return chunkyAdapter; }
    public PacketEventsAdapter packetEventsAdapter() { return packetEventsAdapter; }
    public WardService        wardService()          { return wardService; }
    public CityService        cityService()          { return cityService; }
    public EstateService      estateService()        { return estateService; }
    public VanillaMenuProvider menuProvider()        { return menuProvider; }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // 1. Integration Registry — detect all optional plugins
        capabilityRegistry = new CapabilityRegistry(getServer().getPluginManager(), getLogger());
        capabilityRegistry.detect();

        // 2. Integration adapters — each is a no-op when unavailable
        worldGuardAdapter  = new WorldGuardAdapterImpl(capabilityRegistry, getLogger());
        luckPermsAdapter   = new LuckPermsAdapterImpl(capabilityRegistry, getLogger());
        coreProtectAdapter = new CoreProtectAdapterImpl(capabilityRegistry, getLogger());
        essentialsAdapter  = new EssentialsAdapterImpl(capabilityRegistry, getLogger());
        worldEditAdapter   = new WorldEditAdapterImpl(capabilityRegistry, getLogger());
        chunkyAdapter      = new ChunkyAdapterImpl(capabilityRegistry, getLogger());
        packetEventsAdapter = new PacketEventsAdapterImpl(capabilityRegistry, getLogger());

        // 3. Boot existing protection services
        bootServices();

        // 4. Domain persistence
        bootDomainPersistence();

        // 5. Presentation
        menuProvider = new VanillaMenuProvider();

        // 6. Register Bukkit listeners
        var pm = getServer().getPluginManager();
        ProtectionChecker checker = protectionChecker();
        ProtectionMenu menu = protectionMenu();

        pm.registerEvents(new ProtectionListener(
                wardrobeItems(), claimManager, checker, menu, upkeepManager()), this);
        pm.registerEvents(menu, this);
        pm.registerEvents(new PhysicsProtectionListener(checker), this);
        pm.registerEvents(new RedstoneProtectionListener(checker), this);
        pm.registerEvents(new HopperProtectionListener(checker), this);
        pm.registerEvents(menuProvider, this);

        // 7. Upkeep tick
        new UpkeepTickTask(claimManager, upkeepManager(), upkeepCalculator, protectionConfig, this).register();

        // 8. Commands
        PluginCommand command = getCommand("protection");
        if (command != null) {
            ProtectionCommand executor = new ProtectionCommand(
                    claimManager, wardrobeItems(), menu, this::reloadPluginConfig,
                    upkeepManager(), protectionConfig);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        // 8a. Wire the menu action dispatcher to the vanilla menu provider
        menuProvider.setActionHandler(new MenuActionDispatcher(
                wardService, cityService, estateService, worldGuardAdapter));

        // 8b. Domain commands: /ward, /city, /estate
        WardTierProvider tierProvider = new ConfigWardTierProvider(getConfig());
        PluginCommand wardCmd = getCommand("ward");
        if (wardCmd != null) {
            WardCommand wardExecutor = new WardCommand(
                    wardService, cityService, worldGuardAdapter, tierProvider, menuProvider);
            wardCmd.setExecutor(wardExecutor);
            wardCmd.setTabCompleter(wardExecutor);
        }
        PluginCommand cityCmd = getCommand("city");
        if (cityCmd != null) {
            CityCommand cityExecutor = new CityCommand(cityService, wardService, menuProvider);
            cityCmd.setExecutor(cityExecutor);
            cityCmd.setTabCompleter(cityExecutor);
        }
        PluginCommand estateCmd = getCommand("estate");
        if (estateCmd != null) {
            EstateCommand estateExecutor = new EstateCommand(estateService, menuProvider);
            estateCmd.setExecutor(estateExecutor);
            estateCmd.setTabCompleter(estateExecutor);
        }

        getLogger().info("[DreamCraft] Domain layer active — Ward/City/Estate ready.");
    }

    @Override
    public void onDisable() {
        // Save existing claim data
        if (claimManager != null) {
            try { claimManager.save(); }
            catch (IOException e) { getLogger().severe("No se pudo guardar claims: " + e.getMessage()); }
        }
        // Save domain data
        flushDomainData();
    }

    // ── Boot helpers ──────────────────────────────────────────────────────────

    private void bootServices() {
        reloadConfig();
        this.protectionConfig = ProtectionConfig.load(getConfig());
        validateConfig(protectionConfig);
        this.claimIndex = new ClaimIndex();
        this.buildingCostService = new BuildingCostService(protectionConfig);
        this.upkeepCalculator = new UpkeepCalculator(protectionConfig);
        this.upkeepManager = new UpkeepManager(protectionConfig, upkeepCalculator);
        this.claimManager = new ClaimManager(
                protectionConfig,
                claimIndex,
                new ClaimRepository(new File(getDataFolder(), "claims.yml")),
                buildingCostService
        );
        this.wardrobeItems = new WardrobeItems(this, protectionConfig);
        this.protectionChecker = new ProtectionChecker(protectionConfig, claimManager.claimIndex());
        Material depositMat = protectionConfig.upkeepResourceMaterial() != null
                ? protectionConfig.upkeepResourceMaterial() : Material.DIAMOND;
        this.protectionMenu = new ProtectionMenu(
                upkeepCalculator, claimManager, depositMat, protectionConfig.upkeepUnitsPerItem());
        this.claimManager.load();
    }

    private void bootDomainPersistence() {
        File dataFolder = getDataFolder();
        wardRepository  = new YamlWardRepository(new File(dataFolder, "wards.yml"));
        cityRepository  = new YamlCityRepository(new File(dataFolder, "cities.yml"));
        estateRepository = new YamlEstateRepository(new File(dataFolder, "estates.yml"));

        wardRepository.loadAll();
        cityRepository.loadAll();
        estateRepository.loadAll();

        // Ward upkeep interval from config (reuse existing interval value)
        Duration upkeepInterval = protectionConfig.upkeepInterval();
        WardTierProvider tierProvider = new ConfigWardTierProvider(getConfig());

        wardService  = new WardService(wardRepository, tierProvider, upkeepInterval);
        cityService  = new CityService(cityRepository);
        estateService = new EstateService(estateRepository);

        getLogger().info("[DreamCraft] Loaded "
                + wardRepository.findAll().size() + " Ward(s), "
                + cityRepository.findAll().size() + " City(ies), "
                + estateRepository.findAll().size() + " Estate(s).");
    }

    private void flushDomainData() {
        try {
            if (wardRepository  != null) wardRepository.flush();
            if (cityRepository  != null) cityRepository.flush();
            if (estateRepository != null) estateRepository.flush();
        } catch (IOException e) {
            getLogger().severe("[DreamCraft] Failed to save domain data: " + e.getMessage());
        }
    }

    private void reloadPluginConfig() {
        try { claimManager.save(); }
        catch (IOException e) { getLogger().warning("No se pudo guardar antes del reload: " + e.getMessage()); }
        bootServices();
        bootDomainPersistence();
        getLogger().info("[DreamCraftProtection] Recarga completada.");
    }

    // ── Config validation (unchanged) ─────────────────────────────────────────

    private void validateConfig(ProtectionConfig cfg) {
        if (cfg.defaultRadius() <= 0)
            getLogger().warning("[Config] protection.default-radius debe ser > 0. Usando 16.");
        if (cfg.defaultBuildRadius() <= 0)
            getLogger().warning("[Config] protection.default-build-radius debe ser > 0. Usando 16.");
        if (cfg.inventorySize() % 9 != 0 || cfg.inventorySize() < 9 || cfg.inventorySize() > 54)
            getLogger().warning("[Config] protection.inventory-size inválido (" + cfg.inventorySize() + "). Debe ser múltiplo de 9 (9–54).");
        if (cfg.upkeepUnitsPerItem() <= 0)
            getLogger().warning("[Config] upkeep.units-per-item debe ser > 0. Usando 64.");
        if (cfg.upkeepInterval().isZero() || cfg.upkeepInterval().isNegative())
            getLogger().warning("[Config] upkeep.interval debe ser > 0.");
        if (cfg.warningThreshold().isNegative())
            getLogger().warning("[Config] upkeep.warning-threshold negativo.");
        if (cfg.expiringThreshold().isNegative())
            getLogger().warning("[Config] upkeep.expiring-threshold negativo.");
        if (cfg.gracePeriod().isNegative())
            getLogger().warning("[Config] upkeep.grace-period negativo.");
        if (cfg.destructionDelay().isNegative())
            getLogger().warning("[Config] upkeep.destruction-delay negativo.");
        if (cfg.upkeepResourceMaterial() == null)
            getLogger().warning("[Config] upkeep.resource-material inválido. Usando DIAMOND.");
        if (cfg.wardrobeMaterial() == null)
            getLogger().warning("[Config] protection.wardrobe-material inválido. Usando LODESTONE.");
        if (cfg.tiers().isEmpty())
            getLogger().warning("[Config] No se encontraron tiers definidos en la configuración.");
        cfg.tiers().forEach((key, tier) -> {
            if (tier.radius() <= 0)
                getLogger().warning("[Config] tier." + key + ".radius debe ser > 0.");
            if (tier.buildRadius() <= 0)
                getLogger().warning("[Config] tier." + key + ".build-radius debe ser > 0.");
            if (tier.maxMembers() < 0)
                getLogger().warning("[Config] tier." + key + ".max-members no puede ser negativo.");
        });
        cfg.categoryBaseCosts().forEach((cat, cost) -> {
            if (cost <= 0)
                getLogger().warning("[Config] building-cost.categories." + cat + ".base-cost debe ser > 0.");
        });
        getLogger().info("[Config] Configuración cargada. Tiers: " + cfg.tiers().keySet()
                + " | Protección " + (cfg.enabled() ? "habilitada" : "DESHABILITADA") + ".");
    }

    private WardrobeItems wardrobeItems()   { return wardrobeItems; }
    private ProtectionChecker protectionChecker() { return protectionChecker; }
    private ProtectionMenu protectionMenu() { return protectionMenu; }
    private UpkeepManager upkeepManager()   { return upkeepManager; }
}
