package dev.dreamcraft.protection.presentation;

import dev.dreamcraft.protection.domain.model.*;
import dev.dreamcraft.protection.domain.service.CityService;
import dev.dreamcraft.protection.domain.service.EstateService;
import dev.dreamcraft.protection.domain.service.WardService;
import dev.dreamcraft.protection.integration.worldguard.WorldGuardAdapter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Dispatches {@link MenuAction}s received from the menu provider to the
 * appropriate domain service, then re-opens or refreshes the menu with
 * updated state.
 *
 * <p>This is the presentation→service bridge: it reads the entity ID from
 * the {@link MenuContext} data map, loads the domain aggregate, validates
 * the viewer's authority, delegates to the service, syncs WorldGuard when
 * needed, and sends Adventure feedback (actionbar/sound).
 *
 * <p>WorldGuard sync is triggered on:
 * <ul>
 *   <li>Ward upgrade (resize region)</li>
 *   <li>Ward annex to city (inherit policies/memberships)</li>
 *   <li>Estate start/end instance (temporal access flags)</li>
 * </ul>
 */
public final class MenuActionDispatcher implements BiConsumer<MenuContext, MenuAction>,
        VanillaMenuProvider.DepositHandler {

    private final WardService wardService;
    private final CityService cityService;
    private final EstateService estateService;
    private final WorldGuardAdapter worldGuardAdapter;
    private final dev.dreamcraft.protection.service.WardUpgradeService upgradeService;
    /** Optional: item-based upkeep deposits for Wards. */
    private final dev.dreamcraft.protection.service.WardUpkeepService upkeepService;
    /** Optional: persistent city treasury vaults. */
    private dev.dreamcraft.protection.persistence.CityTreasuryStore treasuryStore = null;
    /** Optional: reopens the Ward menu with fresh data (scheduled 1 tick later). */
    private java.util.function.BiConsumer<Player, Ward> wardMenuReopener = null;
    /** Optional: manages private End instances for END-type estates. */
    private dev.dreamcraft.protection.service.EndInstanceService endInstanceService = null;

    public MenuActionDispatcher(WardService wardService,
                                CityService cityService,
                                EstateService estateService,
                                WorldGuardAdapter worldGuardAdapter) {
        this(wardService, cityService, estateService, worldGuardAdapter, null, null);
    }

    public MenuActionDispatcher(WardService wardService,
                                CityService cityService,
                                EstateService estateService,
                                WorldGuardAdapter worldGuardAdapter,
                                dev.dreamcraft.protection.service.WardUpgradeService upgradeService) {
        this(wardService, cityService, estateService, worldGuardAdapter, upgradeService, null);
    }

    public MenuActionDispatcher(WardService wardService,
                                CityService cityService,
                                EstateService estateService,
                                WorldGuardAdapter worldGuardAdapter,
                                dev.dreamcraft.protection.service.WardUpgradeService upgradeService,
                                dev.dreamcraft.protection.service.WardUpkeepService upkeepService) {
        this.wardService = wardService;
        this.cityService = cityService;
        this.estateService = estateService;
        this.worldGuardAdapter = worldGuardAdapter;
        this.upgradeService = upgradeService;
        this.upkeepService = upkeepService;
    }

    /** Registers the callback used to refresh the open Ward menu after state changes. */
    public void setWardMenuReopener(java.util.function.BiConsumer<Player, Ward> reopener) {
        this.wardMenuReopener = reopener;
    }

    /** Registers the persistent city treasury vault store. */
    public void setCityTreasuryStore(dev.dreamcraft.protection.persistence.CityTreasuryStore store) {
        this.treasuryStore = store;
    }

    /** Registers the End instance service for END-type estate actions. */
    public void setEndInstanceService(dev.dreamcraft.protection.service.EndInstanceService service) {
        this.endInstanceService = service;
    }

    @Override
    public void accept(MenuContext ctx, MenuAction action) {
        Player player = Bukkit.getPlayer(ctx.viewerId());
        if (player == null) return;

        String id = action.actionId();
        try {
            switch (id) {
                case "menu.close" -> handleClose(player);
                case "ward.upgrade" -> handleWardUpgrade(player, ctx);
                case "ward.toggle_permission" -> handleWardTogglePermission(player, ctx, action);
                case "ward.annex_city" -> handleWardAnnexCity(player, ctx);
                case "ward.disband" -> handleWardDisband(player, ctx);
                case "ward.upkeep_vault" -> handleOpenUpkeepVault(player, ctx);
                case "ward.transfer" -> feedback(player, "Usa /ward transfer <jugador>", NamedTextColor.YELLOW);
                case "city.invite" -> feedback(player, "Usa /city invite <jugador>", NamedTextColor.YELLOW);
                case "city.kick" -> feedback(player, "Usa /city kick <jugador>", NamedTextColor.YELLOW);
                case "city.roles" -> feedback(player, "Usa /city roles <jugador> <rol>", NamedTextColor.YELLOW);
                case "city.bank" -> handleOpenCityTreasury(player, ctx);
                case "city.policy" -> handleCityPolicy(player, ctx, action);
                case "city.delete" -> handleCityDelete(player, ctx);
                case "estate.invite" -> feedback(player, "Usa /estate invite <jugador>", NamedTextColor.YELLOW);
                case "estate.join" -> handleEstateJoin(player, ctx);
                case "estate.leave" -> handleEstateLeave(player, ctx);
                case "estate.start" -> handleEstateStart(player, ctx);
                case "estate.disband" -> handleEstateDisband(player, ctx);
                default -> feedback(player, "Acción no reconocida: " + id, NamedTextColor.RED);
            }
        } catch (RuntimeException e) {
            player.sendMessage(Component.text("[DreamCraft] ", NamedTextColor.DARK_PURPLE)
                    .append(Component.text(e.getMessage() != null ? e.getMessage() : "Error interno", NamedTextColor.RED)));
            playError(player);
        }
    }

    // ── Ward actions ──────────────────────────────────────────────────────────

    /**
     * Opens the Ward upkeep vault: a free-use chest inventory. Nothing is
     * consumed while it is open — the close listener settles the contents
     * (accepted materials → units, everything else returned).
     */
    private void handleOpenUpkeepVault(Player player, MenuContext ctx) {
        if (upkeepService == null) {
            feedback(player, "Depósitos no disponibles.", NamedTextColor.RED);
            playError(player);
            return;
        }
        Ward ward = resolveWard(player, ctx);
        if (ward == null) return;
        if (!upkeepService.canDeposit(player, ward)) {
            feedback(player, "No puedes depositar upkeep en este Ward.", NamedTextColor.RED);
            playError(player);
            return;
        }
        // Open next tick — opening inventories during a click event causes ghost items
        UUID wardId = ward.id();
        String wardName = ward.name();
        Bukkit.getScheduler().runTask(plugin(), () -> {
            Player online = Bukkit.getPlayer(player.getUniqueId());
            if (online == null) return;
            var holder = dev.dreamcraft.protection.ui.WardUpkeepVaultHolder.create(wardId,
                    net.kyori.adventure.text.Component.text("⚔ Bóveda de Upkeep — " + wardName,
                            NamedTextColor.DARK_AQUA));
            online.openInventory(holder.getInventory());
            playSuccess(online);
        });
    }

    /**
     * Deposit-slot handler: credits the offered stack as Ward upkeep units and
     * consumes the cursor items only on success.
     */
    @Override
    public void onDeposit(MenuContext ctx, org.bukkit.Material material, int amount, Runnable consume) {
        Player player = Bukkit.getPlayer(ctx.viewerId());
        if (player == null) return;
        if (upkeepService == null) {
            feedback(player, "Depósitos no disponibles.", NamedTextColor.RED);
            playError(player);
            return;
        }
        Ward ward = resolveWard(player, ctx);
        if (ward == null) return;
        if (!upkeepService.canDeposit(player, ward)) {
            feedback(player, "No puedes depositar upkeep en este Ward.", NamedTextColor.RED);
            playError(player);
            return;
        }
        if (!upkeepService.isAccepted(material)) {
            feedback(player, "Ítem no válido para upkeep. Mira los aceptados en el menú.",
                    NamedTextColor.RED);
            playError(player);
            return;
        }
        var receipt = upkeepService.deposit(ward, player, material, amount);
        consume.run();
        player.sendMessage(Component.text("[Ward] ", NamedTextColor.DARK_AQUA)
                .append(Component.text("Depositaste ", NamedTextColor.GREEN))
                .append(Component.text(receipt.amount() + "x "
                        + upkeepService.displayName(receipt.material()), NamedTextColor.WHITE))
                .append(Component.text(" → +" + receipt.unitsCredited()
                        + " unidades. Balance: " + receipt.newBalance(), NamedTextColor.GREEN)));
        playSuccess(player);
        reopenWardMenu(player, ward);
    }

    /** Reopens the Ward menu next tick so the viewer sees fresh balance/state. */
    private void reopenWardMenu(Player player, Ward ward) {
        if (wardMenuReopener == null) return;
        Bukkit.getScheduler().runTask(plugin(), () -> wardMenuReopener.accept(player, ward));
    }

    /** The dispatcher runs inside the plugin — resolve it lazily from any registered command. */
    private org.bukkit.plugin.Plugin plugin() {
        return Bukkit.getPluginManager().getPlugin("DreamCraftProtection");
    }

    private void handleWardUpgrade(Player player, MenuContext ctx) {
        Ward ward = resolveWard(player, ctx);
        if (ward == null) return;
        if (!ward.ownerId().equals(player.getUniqueId())) {
            feedback(player, "Solo el owner puede mejorar el Ward.", NamedTextColor.RED);
            playError(player);
            return;
        }
        if (upgradeService == null) {
            // Fallback: no cost service wired — behave as before
            wardService.addBaseScore(ward, 100);
            worldGuardAdapter.resizeRegion(ward, -64, 320);
            feedback(player, "Ward mejorado a " + ward.tier() + " (radio " + ward.radius() + ")", NamedTextColor.GREEN);
            playSuccess(player);
            reopenWardMenu(player, ward);
            return;
        }

        // 1. Quote the next tier
        var quoteOpt = upgradeService.quoteNext(ward);
        if (quoteOpt.isEmpty()) {
            feedback(player, "El Ward ya está en el tier máximo (" + ward.tier() + ").", NamedTextColor.YELLOW);
            playError(player);
            return;
        }
        var quote = quoteOpt.get();

        // 2. Verify the player can pay the item cost
        var missing = upgradeService.missingItems(player, quote);
        if (!missing.isEmpty()) {
            player.sendMessage(Component.text("[Ward] Te faltan ítems para mejorar al tier "
                    + quote.targetTierKey() + ":", NamedTextColor.RED));
            missing.forEach(player::sendMessage);
            playError(player);
            return;
        }

        // 3. Charge, apply score gain and sync the region
        upgradeService.charge(player, quote);
        wardService.addBaseScore(ward, quote.scoreGain());
        worldGuardAdapter.resizeRegion(ward, -64, 320);

        player.sendMessage(Component.text("[Ward] ", NamedTextColor.DARK_AQUA)
                .append(Component.text("Mejorado a ", NamedTextColor.GREEN))
                .append(Component.text(ward.tier(), NamedTextColor.AQUA))
                .append(Component.text(" — radio " + ward.radius()
                        + " bloques, upkeep " + quote.upkeepPerInterval()
                        + " unidades/intervalo. Ítems descontados.", NamedTextColor.GREEN)));
        playSuccess(player);
        reopenWardMenu(player, ward);
    }

    private void handleWardTogglePermission(Player player, MenuContext ctx, MenuAction action) {
        Ward ward = resolveWard(player, ctx);
        if (ward == null) return;
        if (!ward.ownerId().equals(player.getUniqueId())) {
            feedback(player, "Solo el owner puede cambiar permisos.", NamedTextColor.RED);
            return;
        }
        WardPermission perm;
        try {
            perm = WardPermission.valueOf(action.payload());
        } catch (IllegalArgumentException e) {
            feedback(player, "Permiso inválido: " + action.payload(), NamedTextColor.RED);
            return;
        }
        if (ward.hasPermission(perm)) {
            ward.revokePermission(perm);
            feedback(player, "Permiso " + perm.name() + " revocado.", NamedTextColor.YELLOW);
        } else {
            ward.grantPermission(perm);
            feedback(player, "Permiso " + perm.name() + " concedido.", NamedTextColor.GREEN);
        }
        wardService.assignWorldGuardRegion(ward, ward.worldGuardRegionId()); // persist
        playSuccess(player);
        reopenWardMenu(player, ward);
    }

    private void handleWardAnnexCity(Player player, MenuContext ctx) {
        Ward ward = resolveWard(player, ctx);
        if (ward == null) return;
        if (!ward.ownerId().equals(player.getUniqueId())) {
            feedback(player, "Solo el owner puede anexar el Ward.", NamedTextColor.RED);
            return;
        }
        if (ward.hasCityMembership()) {
            feedback(player, "El Ward ya pertenece a una ciudad.", NamedTextColor.YELLOW);
            return;
        }
        // Find the city the player is a member of
        var optCity = cityService.findByMember(player.getUniqueId());
        if (optCity.isEmpty()) {
            feedback(player, "No eres miembro de ninguna ciudad.", NamedTextColor.RED);
            return;
        }
        City city = optCity.get();
        wardService.setCityMembership(ward, city.id());
        // Sync WorldGuard: inherit city policies/memberships via region membership
        syncCityMembershipToRegion(ward, city);
        feedback(player, "Ward anexado a la ciudad " + city.name() + ".", NamedTextColor.GREEN);
        playSuccess(player);
        reopenWardMenu(player, ward);
    }

    private void handleWardDisband(Player player, MenuContext ctx) {
        Ward ward = resolveWard(player, ctx);
        if (ward == null) return;
        if (!ward.ownerId().equals(player.getUniqueId())) {
            feedback(player, "Solo el owner puede disolver el Ward.", NamedTextColor.RED);
            return;
        }
        worldGuardAdapter.removeRegion(ward);
        wardService.delete(ward);
        player.closeInventory();
        feedback(player, "Ward disuelto.", NamedTextColor.GREEN);
        playSuccess(player);
    }

    // ── City actions ──────────────────────────────────────────────────────────

    /**
     * Opens the City treasury vault: a persistent chest inventory backed by
     * {@link dev.dreamcraft.protection.persistence.CityTreasuryStore}. Items
     * stay inside and their value feeds the city's wealth score. Requires
     * Council role or higher.
     */
    private void handleOpenCityTreasury(Player player, MenuContext ctx) {
        if (treasuryStore == null) {
            feedback(player, "El tesoro no está disponible.", NamedTextColor.RED);
            playError(player);
            return;
        }
        City city = resolveCity(player, ctx);
        if (city == null) return;
        CityRole role = city.roleOf(player.getUniqueId());
        boolean council = city.isGovernor(player.getUniqueId()) || role == CityRole.COUNCIL;
        if (!council) {
            feedback(player, "Requerís rol Council o superior para el tesoro.", NamedTextColor.RED);
            playError(player);
            return;
        }
        UUID cityId = city.id();
        String cityName = city.name();
        int value = treasuryStore.computeValue(treasuryStore.get(cityId));
        Bukkit.getScheduler().runTask(plugin(), () -> {
            Player online = Bukkit.getPlayer(player.getUniqueId());
            if (online == null) return;
            var holder = dev.dreamcraft.protection.ui.CityTreasuryVaultHolder.create(cityId,
                    net.kyori.adventure.text.Component.text("★ Tesoro de " + cityName,
                            NamedTextColor.GOLD));
            online.openInventory(holder.getInventory());
            online.sendMessage(Component.text("[Ciudad] ", NamedTextColor.GOLD)
                    .append(Component.text("Valor actual del tesoro: ", NamedTextColor.GRAY))
                    .append(Component.text(value + " unidades de riqueza", NamedTextColor.YELLOW)));
        });
    }

    private void handleCityPolicy(Player player, MenuContext ctx, MenuAction action) {
        City city = resolveCity(player, ctx);
        if (city == null) return;
        if (!city.isGovernor(player.getUniqueId())) {
            feedback(player, "Solo el Gobernador puede cambiar políticas.", NamedTextColor.RED);
            return;
        }
        CityPolicy policy;
        try {
            policy = CityPolicy.valueOf(action.payload());
        } catch (IllegalArgumentException e) {
            feedback(player, "Política inválida: " + action.payload(), NamedTextColor.RED);
            return;
        }
        boolean newState = !city.hasPolicy(policy);
        cityService.setPolicy(city, policy, newState);
        feedback(player, "Política " + policy.name() + " " + (newState ? "activada" : "desactivada") + ".",
                NamedTextColor.GREEN);
        playSuccess(player);
    }

    private void handleCityDelete(Player player, MenuContext ctx) {
        City city = resolveCity(player, ctx);
        if (city == null) return;
        if (!city.isGovernor(player.getUniqueId())) {
            feedback(player, "Solo el Gobernador puede eliminar la ciudad.", NamedTextColor.RED);
            return;
        }
        // Disassociate all wards from this city
        for (Ward ward : wardService.findByCity(city.id())) {
            wardService.setCityMembership(ward, null);
        }
        cityService.delete(city);
        player.closeInventory();
        feedback(player, "Ciudad " + city.name() + " eliminada.", NamedTextColor.GREEN);
        playSuccess(player);
    }

    // ── Estate actions ────────────────────────────────────────────────────────

    private void handleEstateJoin(Player player, MenuContext ctx) {
        Estate estate = resolveEstate(player, ctx);
        if (estate == null) return;
        if (estate.isOwner(player.getUniqueId())) {
            feedback(player, "Ya eres el owner de este Estate.", NamedTextColor.YELLOW);
            return;
        }
        if (estate.isMember(player.getUniqueId())) {
            feedback(player, "Ya eres miembro del Estate.", NamedTextColor.YELLOW);
            return;
        }
        estateService.addMember(estate, player.getUniqueId());
        worldGuardAdapter.syncEstateMembers(estate);
        feedback(player, "Te uniste al Estate " + estate.name() + ".", NamedTextColor.GREEN);
        playSuccess(player);
    }

    private void handleEstateLeave(Player player, MenuContext ctx) {
        Estate estate = resolveEstate(player, ctx);
        if (estate == null) return;
        if (estate.isOwner(player.getUniqueId())) {
            feedback(player, "El owner no puede salir; transfiere o disuelve el Estate.", NamedTextColor.RED);
            return;
        }
        if (!estate.isMember(player.getUniqueId())) {
            feedback(player, "No eres miembro del Estate.", NamedTextColor.RED);
            return;
        }
        estateService.removeMember(estate, player.getUniqueId());
        worldGuardAdapter.syncEstateMembers(estate);
        player.closeInventory();
        feedback(player, "Saliste del Estate " + estate.name() + ".", NamedTextColor.GREEN);
        playSuccess(player);
    }

    private void handleEstateStart(Player player, MenuContext ctx) {
        Estate estate = resolveEstate(player, ctx);
        if (estate == null) return;
        if (!estate.isOwner(player.getUniqueId())) {
            feedback(player, "Solo el owner puede iniciar la instancia.", NamedTextColor.RED);
            return;
        }
        String instanceId = "inst-" + estate.id().toString().substring(0, 8);
        boolean started = estateService.startInstance(estate, instanceId);
        if (!started) {
            feedback(player, "El Estate ya tiene una instancia activa.", NamedTextColor.YELLOW);
            return;
        }
        // END-type estates pre-open their private End world + dragon right away
        String extra = "";
        if (endInstanceService != null && endInstanceService.preopen(estate)) {
            extra = " El End privado está listo con la dragona.";
        }
        feedback(player, "Instancia de " + estate.name() + " iniciada." + extra, NamedTextColor.GREEN);
        playSuccess(player);
    }

    private void handleEstateDisband(Player player, MenuContext ctx) {
        Estate estate = resolveEstate(player, ctx);
        if (estate == null) return;
        if (!estate.isOwner(player.getUniqueId())) {
            feedback(player, "Solo el owner puede disolver el Estate.", NamedTextColor.RED);
            return;
        }
        if (endInstanceService != null && estate.type().usesEndInstance()) {
            endInstanceService.resetInstance(estate);
        }
        worldGuardAdapter.removeEstateAreaRegion(estate);
        estateService.delete(estate);
        player.closeInventory();
        feedback(player, "Estate " + estate.name() + " disuelto.", NamedTextColor.GREEN);
        playSuccess(player);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void handleClose(Player player) {
        player.closeInventory();
    }

    private Ward resolveWard(Player player, MenuContext ctx) {
        UUID wardId = ctx.get("wardId");
        if (wardId == null) {
            feedback(player, "Contexto de Ward perdido.", NamedTextColor.RED);
            return null;
        }
        return wardService.findById(wardId).orElse(null);
    }

    private City resolveCity(Player player, MenuContext ctx) {
        UUID cityId = ctx.get("cityId");
        if (cityId == null) {
            feedback(player, "Contexto de Ciudad perdido.", NamedTextColor.RED);
            return null;
        }
        return cityService.findById(cityId).orElse(null);
    }

    private Estate resolveEstate(Player player, MenuContext ctx) {
        UUID estateId = ctx.get("estateId");
        if (estateId == null) {
            feedback(player, "Contexto de Estate perdido.", NamedTextColor.RED);
            return null;
        }
        return estateService.findById(estateId).orElse(null);
    }

    /**
     * Syncs city membership to the Ward's WorldGuard region by adding all city
     * members as WG region members (inheritance of permissions).
     */
    private void syncCityMembershipToRegion(Ward ward, City city) {
        if (!worldGuardAdapter.isAvailable()) return;
        for (UUID memberId : city.members().keySet()) {
            worldGuardAdapter.addMember(ward, memberId);
        }
    }

    private void feedback(Player player, String message, NamedTextColor color) {
        player.sendActionBar(Component.text(message, color));
    }

    private void playSuccess(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
    }

    private void playError(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f);
    }
}
