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
    private dev.dreamcraft.protection.persistence.NucleusClaimStore nucleusClaimStore;

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
    /** Single dissolution contract for every route that removes a Ward. */
    private dev.dreamcraft.protection.service.WardDissolutionService wardDissolutionService;

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
    public dev.dreamcraft.protection.service.WardDissolutionService wardDissolutionService() { return wardDissolutionService; }
    public VanillaMenuProvider menuProvider()        { return menuProvider; }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // Command names must be installed BEFORE the message catalog loads:
        // prefixes/headers/titles resolve {name.*}/{cmd.*} at load time.
        dev.dreamcraft.protection.config.CommandNames.install(getConfig());
        dev.dreamcraft.protection.message.Messages.load(this);

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
        // Zone edit journal: records adventurer modifications inside adventure
        // areas and restores them when the zone closes (chunk regeneration)
        dev.dreamcraft.protection.service.EstateZoneJournal zoneJournal =
                new dev.dreamcraft.protection.service.EstateZoneJournal(getDataFolder(), getLogger());
        endInstanceService.setZoneJournal(zoneJournal);

        // 4. Domain persistence
        bootDomainPersistence();

        // 4b. Container protection migration — regions created before chest-access
        //     was enforced get their flag state re-applied on every boot.
        migrateContainerFlags();

        // 5. Presentation — provider selector + asset contract + pack tracking
        dev.dreamcraft.protection.config.PresentationOptions presentationOptions =
                dev.dreamcraft.protection.config.PresentationOptions.load(getConfig());
        dev.dreamcraft.protection.presentation.resourcepack.PresentationAssetRegistry assetRegistry =
                dev.dreamcraft.protection.presentation.resourcepack.PresentationAssetRegistry.load(this);
        menuProvider = new VanillaMenuProvider();
        switch (presentationOptions.assetMode()) {
            case RP -> {
                // Mandatory pack: every viewer is assumed to render CMD assets.
                menuProvider.setAssetRegistry(assetRegistry);
                menuProvider.setPackTracker(
                        (dev.dreamcraft.protection.presentation.resourcepack.PackState) id -> true);
            }
            case AUTO -> {
                menuProvider.setAssetRegistry(assetRegistry);
                var tracker = new dev.dreamcraft.protection.presentation.resourcepack.PackStatusTracker();
                getServer().getPluginManager().registerEvents(tracker, this);
                menuProvider.setPackTracker(tracker);
            }
            case VANILLA -> { /* pure legacy rendering — golden rule MD §23 */ }
        }

        // 6. Register Bukkit listeners (menu provider only)
        var pm = getServer().getPluginManager();
        pm.registerEvents(menuProvider, this);

        // 7. Unified Ward mechanic — shared services, menu façade and commands
        WardTierProvider tierProvider = new ConfigWardTierProvider(getConfig());
        dev.dreamcraft.protection.service.WardUpgradeService upgradeService =
                new dev.dreamcraft.protection.service.WardUpgradeService(tierProvider, protectionConfig);
        dev.dreamcraft.protection.service.WardUpkeepService upkeepService =
                new dev.dreamcraft.protection.service.WardUpkeepService(protectionConfig, wardService);
        // Single dissolution contract: WG region + repository + physical core +
        // tagged founder item refund (owner) — shared by all four removal routes.
        wardDissolutionService = new dev.dreamcraft.protection.service.WardDissolutionService(
                wardService, worldGuardAdapter, wardItems(),
                protectionConfig.wardMaterial(), this::saveDomainData);
        dev.dreamcraft.protection.service.CityLevelService cityLevelService =
                new dev.dreamcraft.protection.service.CityLevelService(wardService,
                        protectionConfig.cityLevels(),
                        cityTreasuryStore());

        java.util.List<String> upkeepLines = new java.util.ArrayList<>();
        upkeepService.acceptedMaterials().forEach((mat, units) ->
                upkeepLines.add(upkeepService.displayName(mat) + " ×" + units + " u"));
        // Balance → protection-time projector for menu lore + vault-open summary
        java.util.LinkedHashMap<String, Integer> upkeepUnitsByName = new java.util.LinkedHashMap<>();
        upkeepService.acceptedMaterials().forEach((mat, units) ->
                upkeepUnitsByName.put(dev.dreamcraft.protection.service.UpkeepProjectionCalculator
                        .normalizeMaterialName(mat.name()), units));
        dev.dreamcraft.protection.service.UpkeepProjectionCalculator upkeepProjection =
                new dev.dreamcraft.protection.service.UpkeepProjectionCalculator(
                        protectionConfig.upkeepInterval(),
                        protectionConfig.warningThreshold(),
                        protectionConfig.expiringThreshold(),
                        upkeepUnitsByName,
                        name -> {
                            org.bukkit.Material mat = org.bukkit.Material.matchMaterial(name);
                            return mat == null ? name
                                    : dev.dreamcraft.protection.service.MaterialNames.forMaterial(mat);
                        });
        dev.dreamcraft.protection.command.WardMenuFacade wardMenuFacade =
                new dev.dreamcraft.protection.command.WardMenuFacade(
                        tierProvider, cityService, upgradeService, menuProvider, upkeepLines,
                        upkeepProjection, protectionConfig.belowTierSurchargeUnits());

        // 7a. Wire the action + deposit dispatchers to the vanilla menu provider
        MenuActionDispatcher dispatcher = new MenuActionDispatcher(
                wardService, cityService, estateService, worldGuardAdapter, upgradeService, upkeepService);
        dispatcher.setWardMenuReopener(wardMenuFacade::open);
        menuProvider.setActionHandler(dispatcher);
        menuProvider.setDepositHandler(dispatcher);
        dispatcher.setCityTreasuryStore(cityTreasuryStore());
        dispatcher.setEndInstanceService(endInstanceService());
        dispatcher.setWardDissolutionService(wardDissolutionService);
        // Menu sounds resolve through the presentation-assets.yml contract
        dispatcher.setPresentationAssets(assetRegistry);

        // 7b. Commands: /protection delegates to the same Ward mechanic as /ward.
        //     Subcommand names/aliases come from config.yml (commands.*.subcommands).
        dev.dreamcraft.protection.config.CommandOptions commandOptions =
                dev.dreamcraft.protection.config.CommandOptions.load(getConfig());
        PluginCommand command = getCommand("protection");
        if (command != null) {
            ProtectionCommand executor = new ProtectionCommand(
                    wardService, cityService, worldGuardAdapter, wardItems(),
                    upgradeService, upkeepService, wardMenuFacade, this::reloadPluginConfig,
                    commandOptions);
            executor.setStatusSources(capabilityRegistry, assetRegistry, presentationOptions.assetMode());
            executor.setDissolutionService(wardDissolutionService);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        WardCommand wardExecutor = new WardCommand(
                wardService, cityService, worldGuardAdapter, wardItems(), wardMenuFacade, upkeepService,
                wardDissolutionService, nucleusClaimStore(), commandOptions);
        PluginCommand wardCmd = getCommand("ward");
        if (wardCmd != null) {
            wardCmd.setExecutor(wardExecutor);
            wardCmd.setTabCompleter(wardExecutor);
        }
        PluginCommand cityCmd = getCommand("city");
        dev.dreamcraft.protection.command.CityCommand cityExecutor =
                new dev.dreamcraft.protection.command.CityCommand(cityService, wardService, menuProvider,
                        cityLevelService, worldGuardAdapter, commandOptions);
        if (cityCmd != null) {
            cityCmd.setExecutor(cityExecutor);
            cityCmd.setTabCompleter(cityExecutor);
        }
        PluginCommand estateCmd = getCommand("estate");
        if (estateCmd != null) {
            EstateCommand estateExecutor = new EstateCommand(estateService, menuProvider,
                    worldGuardAdapter, endInstanceService(), commandOptions);
            estateCmd.setExecutor(estateExecutor);
            estateCmd.setTabCompleter(estateExecutor);
            // Admin zones GUI book button → opens that group's own menu
            dispatcher.setEstateMenuOpener(estateExecutor::openEstateMenuById);
        }

        // 7b-ter. Admin GUIs (wards/cities): stateless payload navigation — the
        // dispatcher renders and opens them; commands just trigger page 0.
        dispatcher.setAdminMenuProvider(menuProvider);
        dispatcher.setWardMenuOpener(wardExecutor::openWardMenu);
        dispatcher.setCityMenuOpener(cityExecutor::openCityMenu);
        dispatcher.setCityLevelService(cityLevelService);
        dispatcher.setWardCoreMaterial(protectionConfig.wardMaterial());
        wardExecutor.setAdminMenuOpener(player -> dispatcher.openWardAdminOverview(player, 0, false));
        cityExecutor.setAdminMenuOpener(player -> dispatcher.openCityAdminOverview(player, 0));

        // 7b-bis. Versioned roots (/sync, /nexo, /matriz…) registered as real
        // commands sharing the canonical executors — Bukkit's commands.yml
        // aliases don't forward tab completions; this does.
        java.util.Map<String, PluginCommand> canonicalRoots = new java.util.HashMap<>();
        if (command != null) canonicalRoots.put("protection", command);
        if (wardCmd != null) canonicalRoots.put("ward", wardCmd);
        if (cityCmd != null) canonicalRoots.put("city", cityCmd);
        if (estateCmd != null) canonicalRoots.put("estate", estateCmd);
        dev.dreamcraft.protection.command.DynamicCommands.registerVersionedRoots(
                this, getLogger(), canonicalRoots);
        // commands.yml aliases load AFTER onEnable and clobber the bare names;
        // the load guard restores our instances once aliases are in.
        dev.dreamcraft.protection.command.DynamicCommands.registerLoadGuard(this, getLogger());

        // 7c. Tier-gated block surcharge — placement counting, break relief and
        //     founding/descent backfill scans (built first so its seeder can be
        //     shared by every founding route below)
        dev.dreamcraft.protection.listener.WardBlockGateListener gateListener =
                new dev.dreamcraft.protection.listener.WardBlockGateListener(
                        wardService, tierProvider, protectionConfig, wardItems(), this::saveDomainData);
        pm.registerEvents(gateListener, this);

        // Tier-transition hooks from the domain: a real ascent wipes the counter
        // there and we just notify the owner; a descent keeps whatever was stored
        // until this authoritative world scan replaces it with the true count.
        wardService.setTierAlignedCallback((ward, previousBlocks) -> {
            org.bukkit.entity.Player owner = org.bukkit.Bukkit.getPlayer(ward.ownerId());
            if (owner != null) {
                owner.sendMessage(dev.dreamcraft.protection.command.CommandMessages.prefixed("ward",
                        "Fase alineada: sobrecosto retirado (" + previousBlocks
                                + " bloque(s) ahora cubiertos por tu nueva fase).",
                        net.kyori.adventure.text.format.NamedTextColor.GREEN));
            }
        });
        wardService.setTierDescendedCallback(gateListener::seedExistingBelowTierBlocks);
        // Founding routes seed the same way: both share this single instance.
        wardExecutor.setFoundingSeeder(gateListener::seedExistingBelowTierBlocks);

        // 7c-bis. Unified Ward mechanic — placing the ward item founds a Ward centered on it;
        //     breaking it dissolves through the shared contract (vanilla drop suppressed)
        pm.registerEvents(new WardItemListener(
                wardItems(),
                wardService,
                worldGuardAdapter,
                wardExecutor::openWardMenu,
                this::saveDomainData,
                wardDissolutionService,
                gateListener::seedExistingBelowTierBlocks
        ), this);

        // 7d. Ward upkeep tick + region entry action bar
        new dev.dreamcraft.protection.service.WardUpkeepTickTask(
                wardService, tierProvider, wardRepository,
                protectionConfig.upkeepInterval(),
                protectionConfig.belowTierSurchargeUnits(), this).register();
        pm.registerEvents(new dev.dreamcraft.protection.listener.WardRegionListener(wardService), this);

        // 7d-bis. Container transfer gate — hoppers/droppers can't cross Ward
        //         boundaries (WG alone does not stop hopper draining).
        pm.registerEvents(new dev.dreamcraft.protection.listener.WardContainerProtectionListener(wardService), this);

        // 7e. Ward upkeep vault — settles contents when the vault inventory closes
        pm.registerEvents(new dev.dreamcraft.protection.listener.WardUpkeepVaultListener(
                wardService, upkeepService, this::saveDomainData, upkeepProjection,
                ward -> tierProvider.findByKey(ward.tier())
                        .map(dev.dreamcraft.protection.domain.model.WardTier::upkeepPerInterval)
                        .orElse(1),
                protectionConfig.belowTierSurchargeUnits()), this);

        // 7f. City treasury vault — persists contents when the vault inventory closes
        pm.registerEvents(new dev.dreamcraft.protection.listener.CityTreasuryVaultListener(
                cityTreasuryStore(), this::flushDomainData), this);

        // 8. Estate adventure areas — gated portals + private End instances
        pm.registerEvents(new dev.dreamcraft.protection.listener.EstatePortalListener(
                estateService, endInstanceService(), worldGuardAdapter,
                endInstanceConfig.areaBandBelow(), endInstanceConfig.areaBandAbove()), this);
        // 8b. Structure preservation + zone journaling (regeneration on close)
        pm.registerEvents(new dev.dreamcraft.protection.listener.EstateStructureListener(
                estateService, endInstanceConfig.protectStructure(),
                endInstanceConfig.regenerateZone(), zoneJournal,
                endInstanceConfig.areaBandBelow(), endInstanceConfig.areaBandAbove()), this);
        // 8c. Instance world events — exit-portal generation when the dragon falls
        pm.registerEvents(endInstanceService(), this);

        // 9. Configurable core recipe (ward.recipe) — result is the TAGGED item,
        //    so crafted cores are indistinguishable from claimed/given ones.
        registerWardRecipe();

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
        dev.dreamcraft.protection.config.CommandNames.install(getConfig());
        this.protectionConfig = ProtectionConfig.load(getConfig());
        validateConfig(protectionConfig);
        this.endInstanceConfig = dev.dreamcraft.protection.config.EndInstanceConfig.load(getConfig());
        // Stealth banding for adventure areas (End/Trial Chamber): keep surface
        // players outside stronghold/chamber zones both in WG and discovery.
        if (worldGuardAdapter != null) {
            worldGuardAdapter.setEstateAreaBand(
                    endInstanceConfig.areaBandBelow(), endInstanceConfig.areaBandAbove());
        }
        if (endInstanceService != null) {
            endInstanceService.applyConfig(endInstanceConfig);
        }
        this.wardItems = new WardItems(this, protectionConfig);
        this.treasuryStore = new dev.dreamcraft.protection.persistence.CityTreasuryStore(
                new File(getDataFolder(), "treasuries.yml"),
                protectionConfig.wardUpkeepMaterials());
        this.treasuryStore.loadAll();
        // One-time free nucleus per player UUID ("/ward reclamar")
        this.nucleusClaimStore = new dev.dreamcraft.protection.persistence.NucleusClaimStore(
                new File(getDataFolder(), "nucleus-claims.yml"));
        this.nucleusClaimStore.loadAll();
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

    /**
     * Re-applies the chest-access flag to every Ward region: deny by default,
     * allow when PUBLIC_CONTAINERS is granted. Fixes regions created before
     * container protection existed (they had no flags at all).
     */
    private void migrateContainerFlags() {
        if (!worldGuardAdapter.isAvailable() || wardService == null) return;
        int applied = 0;
        for (dev.dreamcraft.protection.domain.model.Ward ward : wardService.findAll()) {
            if (ward.worldGuardRegionId() == null) continue;
            worldGuardAdapter.setPublicContainerAccess(ward, ward.hasPermission(
                    dev.dreamcraft.protection.domain.model.WardPermission.PUBLIC_CONTAINERS));
            applied++;
        }
        if (applied > 0) {
            getLogger().info("[DreamCraft] chest-access sincronizado en " + applied + " región(es) de Ward.");
        }
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
        dev.dreamcraft.protection.message.Messages.load(this);
        registerWardRecipe();
        getLogger().info("[DreamCraftProtection] Recarga completada.");
    }

    /**
     * Registers the shaped core recipe from {@code ward.recipe} (config.yml).
     * The result is {@link WardItems#createWardItem()} — the PDC-tagged item —
     * so anything crafted founds a Ward exactly like a claimed/given one.
     * removeRecipe-then-addRecipe keeps this idempotent across /protection recargar.
     */
    private void registerWardRecipe() {
        dev.dreamcraft.protection.config.WardRecipe recipe = protectionConfig.wardRecipe();
        if (!recipe.enabled()) {
            getLogger().info("[Config] ward.recipe disabled: no se registra receta del Núcleo.");
            return;
        }
        String invalid = recipe.validationError();
        if (invalid != null) {
            getLogger().warning("[Config] ward.recipe inválido (" + invalid + "): receta no registrada.");
            return;
        }
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(this, "ward_recipe");
        getServer().removeRecipe(key);
        org.bukkit.inventory.ShapedRecipe shaped =
                new org.bukkit.inventory.ShapedRecipe(key, wardItems.createWardItem());
        shaped.shape(recipe.bukkitShape());
        recipe.ingredients().forEach(shaped::setIngredient);
        getServer().addRecipe(shaped);
        getLogger().info("[DreamCraft] Receta del Núcleo registrada: "
                + String.join(" / ", recipe.shape()) + ".");
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
    private dev.dreamcraft.protection.persistence.NucleusClaimStore nucleusClaimStore() { return nucleusClaimStore; }
}
