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
import dev.dreamcraft.protection.listener.WardItemListener;
import dev.dreamcraft.protection.persistence.ConfigWardTierProvider;
import dev.dreamcraft.protection.persistence.YamlCityRepository;
import dev.dreamcraft.protection.persistence.YamlEstateRepository;
import dev.dreamcraft.protection.persistence.YamlWardRepository;
import dev.dreamcraft.protection.presentation.MenuActionDispatcher;
import dev.dreamcraft.protection.presentation.VanillaMenuProvider;
import dev.dreamcraft.protection.ui.WardItems;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Duration;

public final class DreamCraftProtectionPlugin extends JavaPlugin {

    // ── Configuration ─────────────────────────────────────────────────────────
    private ProtectionConfig protectionConfig;
    private WardItems wardItems;
    private dev.dreamcraft.protection.persistence.CityTreasuryStore treasuryStore;

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

    // ── Estate adventure instances (End / Trial Chamber) ─────────────────────
    private dev.dreamcraft.protection.config.EndInstanceConfig endInstanceConfig;
    private dev.dreamcraft.protection.service.EndInstanceService endInstanceService;

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
    public dev.dreamcraft.protection.service.EndInstanceService endInstanceService() { return endInstanceService; }
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

        // 3. Boot configuration + items
        bootServices();

        // 3b. Estate adventure instancing (End / Trial Chamber) — created once,
        //     survives /protection reload via the lazy EstateService supplier.
        endInstanceService = new dev.dreamcraft.protection.service.EndInstanceService(
                this, this::estateService, endInstanceConfig, essentialsAdapter);

        // 4. Domain persistence
        bootDomainPersistence();

        // 5. Presentation
        menuProvider = new VanillaMenuProvider();

        // 6. Register Bukkit listeners (menu provider only)
        var pm = getServer().getPluginManager();
        pm.registerEvents(menuProvider, this);

        // 7. Unified Ward mechanic — shared services, menu façade and commands
        WardTierProvider tierProvider = new ConfigWardTierProvider(getConfig());
        dev.dreamcraft.protection.service.WardUpgradeService upgradeService =
                new dev.dreamcraft.protection.service.WardUpgradeService(tierProvider, protectionConfig);
        dev.dreamcraft.protection.service.WardUpkeepService upkeepService =
                new dev.dreamcraft.protection.service.WardUpkeepService(protectionConfig, wardService);
        dev.dreamcraft.protection.service.CityLevelService cityLevelService =
                new dev.dreamcraft.protection.service.CityLevelService(wardService,
                        protectionConfig.cityLevels(),
                        cityTreasuryStore());

        java.util.List<String> upkeepLines = new java.util.ArrayList<>();
        upkeepService.acceptedMaterials().forEach((mat, units) ->
                upkeepLines.add(upkeepService.displayName(mat) + " ×" + units + " u"));
        dev.dreamcraft.protection.command.WardMenuFacade wardMenuFacade =
                new dev.dreamcraft.protection.command.WardMenuFacade(
                        tierProvider, cityService, upgradeService, menuProvider, upkeepLines);

        // 7a. Wire the action + deposit dispatchers to the vanilla menu provider
        MenuActionDispatcher dispatcher = new MenuActionDispatcher(
                wardService, cityService, estateService, worldGuardAdapter, upgradeService, upkeepService);
        dispatcher.setWardMenuReopener(wardMenuFacade::open);
        menuProvider.setActionHandler(dispatcher);
        menuProvider.setDepositHandler(dispatcher);
        dispatcher.setCityTreasuryStore(cityTreasuryStore());
        dispatcher.setEndInstanceService(endInstanceService());

        // 7b. Commands: /protection delegates to the same Ward mechanic as /ward
        PluginCommand command = getCommand("protection");
        if (command != null) {
            ProtectionCommand executor = new ProtectionCommand(
                    wardService, cityService, worldGuardAdapter, wardItems(),
                    upgradeService, upkeepService, wardMenuFacade, this::reloadPluginConfig);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        WardCommand wardExecutor = new WardCommand(
                wardService, cityService, worldGuardAdapter, wardItems(), wardMenuFacade, upkeepService);
        PluginCommand wardCmd = getCommand("ward");
        if (wardCmd != null) {
            wardCmd.setExecutor(wardExecutor);
            wardCmd.setTabCompleter(wardExecutor);
        }
        PluginCommand cityCmd = getCommand("city");
        if (cityCmd != null) {
            CityCommand cityExecutor = new CityCommand(cityService, wardService, menuProvider, cityLevelService);
            cityCmd.setExecutor(cityExecutor);
            cityCmd.setTabCompleter(cityExecutor);
        }
        PluginCommand estateCmd = getCommand("estate");
        if (estateCmd != null) {
            EstateCommand estateExecutor = new EstateCommand(estateService, menuProvider,
                    worldGuardAdapter, endInstanceService());
            estateCmd.setExecutor(estateExecutor);
            estateCmd.setTabCompleter(estateExecutor);
        }

        // 7c. Ward block listener — placing the ward item founds a Ward centered on it
        pm.registerEvents(new WardItemListener(
                wardItems(),
                wardService,
                worldGuardAdapter,
                wardExecutor::openWardMenu,
                wardExecutor::canOpenWardMenu,
                this::saveDomainData
        ), this);

        // 7d. Ward upkeep tick + tier-gated blocks + region entry action bar
        new dev.dreamcraft.protection.service.WardUpkeepTickTask(
                wardService, tierProvider, wardRepository,
                protectionConfig.upkeepInterval(), this).register();
        pm.registerEvents(new dev.dreamcraft.protection.listener.WardBlockGateListener(
                wardService, tierProvider, protectionConfig), this);
        pm.registerEvents(new dev.dreamcraft.protection.listener.WardRegionListener(wardService), this);

        // 7e. Ward upkeep vault — settles contents when the vault inventory closes
        pm.registerEvents(new dev.dreamcraft.protection.listener.WardUpkeepVaultListener(
                wardService, upkeepService, this::saveDomainData), this);

        // 7f. City treasury vault — persists contents when the vault inventory closes
        pm.registerEvents(new dev.dreamcraft.protection.listener.CityTreasuryVaultListener(
                cityTreasuryStore(), this::flushDomainData), this);

        // 8. Estate adventure areas — gated portals + private End instances
        pm.registerEvents(new dev.dreamcraft.protection.listener.EstatePortalListener(
                estateService, endInstanceService(), worldGuardAdapter), this);

        getLogger().info("[DreamCraft] Domain layer active — Ward/City/Estate ready.");
    }

    @Override
    public void onDisable() {
        // Unload estate instance worlds without saving (folders are cleaned lazily)
        if (endInstanceService != null) endInstanceService.shutdown();
        // Save domain data
        flushDomainData();
    }

    // ── Boot helpers ──────────────────────────────────────────────────────────

    private void bootServices() {
        reloadConfig();
        this.protectionConfig = ProtectionConfig.load(getConfig());
        validateConfig(protectionConfig);
        this.endInstanceConfig = dev.dreamcraft.protection.config.EndInstanceConfig.load(getConfig());
        if (endInstanceService != null) {
            endInstanceService.applyConfig(endInstanceConfig);
        }
        this.wardItems = new WardItems(this, protectionConfig);
        this.treasuryStore = new dev.dreamcraft.protection.persistence.CityTreasuryStore(
                new File(getDataFolder(), "treasuries.yml"),
                protectionConfig.wardUpkeepMaterials());
        this.treasuryStore.loadAll();
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
            if (treasuryStore   != null) treasuryStore.flush();
        } catch (IOException e) {
            getLogger().severe("[DreamCraft] Failed to save domain data: " + e.getMessage());
        }
    }

    /** Public save hook for listeners (e.g. ward block placement). */
    public void saveDomainData() {
        flushDomainData();
    }

    private void reloadPluginConfig() {
        bootServices();
        bootDomainPersistence();
        getLogger().info("[DreamCraftProtection] Recarga completada.");
    }

    // ── Config validation ─────────────────────────────────────────────────────

    private void validateConfig(ProtectionConfig cfg) {
        if (cfg.upkeepInterval().isZero() || cfg.upkeepInterval().isNegative())
            getLogger().warning("[Config] upkeep.interval debe ser > 0.");
        if (cfg.wardMaterial() == null)
            getLogger().warning("[Config] protection.ward-material inválido.");
        if (cfg.wardUpkeepMaterials().isEmpty())
            getLogger().warning("[Config] ward.upkeep-materials vacío: nadie podrá depositar upkeep.");
        if (cfg.cityLevels().isEmpty())
            getLogger().warning("[Config] city-levels.levels vacío: las ciudades no tendrán niveles.");
        getLogger().info("[Config] Configuración cargada. Upkeep materials: "
                + cfg.wardUpkeepMaterials().size()
                + " | Niveles de ciudad: " + cfg.cityLevels().size() + ".");
    }

    private WardItems wardItems() { return wardItems; }
    private dev.dreamcraft.protection.persistence.CityTreasuryStore cityTreasuryStore() { return treasuryStore; }
}
