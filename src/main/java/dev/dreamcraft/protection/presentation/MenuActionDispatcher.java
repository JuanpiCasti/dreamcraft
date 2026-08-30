package dev.dreamcraft.protection.presentation;

import dev.dreamcraft.protection.command.CommandMessages;

import dev.dreamcraft.protection.domain.model.*;
import dev.dreamcraft.protection.domain.service.CityService;
import dev.dreamcraft.protection.domain.service.EstateService;
import dev.dreamcraft.protection.domain.service.WardHealth;
import dev.dreamcraft.protection.domain.service.WardService;
import dev.dreamcraft.protection.integration.worldguard.WorldGuardAdapter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
    /** Optional: opens an estate/group menu by id (admin zones GUI book button). */
    private java.util.function.BiConsumer<Player, UUID> estateMenuOpener = null;
    /** Optional: manages private End instances for END-type estates. */
    private dev.dreamcraft.protection.service.EndInstanceService endInstanceService = null;
    /** Optional: single dissolution contract for the ward disband button. */
    private dev.dreamcraft.protection.service.WardDissolutionService wardDissolutionService = null;
    /** Optional: asset contract — resolves menu sounds (presentation-assets.yml). */
    private volatile dev.dreamcraft.protection.presentation.resourcepack.PresentationAssetRegistry presentationAssets;
    /** Optional: opens the normal Ward menu (admin detail GUI «Abrir menú»). */
    private java.util.function.BiConsumer<Player, Ward> wardMenuOpener = null;
    /** Optional: opens a City menu by aggregate (admin detail GUI «Abrir menú»). */
    private java.util.function.BiConsumer<Player, City> cityMenuOpener = null;
    /** Optional: computed city levels shown in the admin city overview/detail. */
    private dev.dreamcraft.protection.service.CityLevelService cityLevelService = null;
    /** Configured physical core material for the health check (null → unknown). */
    private volatile org.bukkit.Material wardCoreMaterial = null;
    /** Provider used to open the admin overview/detail GUIs (wired at boot). */
    private MenuProvider adminMenuProvider = null;

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

    /** Registers the callback that opens an estate/group menu by estate id. */
    public void setEstateMenuOpener(java.util.function.BiConsumer<Player, UUID> opener) {
        this.estateMenuOpener = opener;
    }

    /** Registers the persistent city treasury vault store. */
    public void setCityTreasuryStore(dev.dreamcraft.protection.persistence.CityTreasuryStore store) {
        this.treasuryStore = store;
    }

    /** Registers the End instance service for END-type estate actions. */
    public void setEndInstanceService(dev.dreamcraft.protection.service.EndInstanceService service) {
        this.endInstanceService = service;
    }

    /** Registers the shared Ward dissolution contract (menu disband route). */
    public void setWardDissolutionService(
            dev.dreamcraft.protection.service.WardDissolutionService service) {
        this.wardDissolutionService = service;
    }

    /** Registers the asset registry used to resolve menu sounds. */
    public void setPresentationAssets(
            dev.dreamcraft.protection.presentation.resourcepack.PresentationAssetRegistry assets) {
        this.presentationAssets = assets;
    }

    /** Registers the opener used by the ward admin GUI to open a normal Ward menu. */
    public void setWardMenuOpener(java.util.function.BiConsumer<Player, Ward> opener) {
        this.wardMenuOpener = opener;
    }

    /** Registers the opener used by the city admin GUI to open a City menu. */
    public void setCityMenuOpener(java.util.function.BiConsumer<Player, City> opener) {
        this.cityMenuOpener = opener;
    }

    /** Registers the optional city level service (nivel line in the admin GUI). */
    public void setCityLevelService(dev.dreamcraft.protection.service.CityLevelService service) {
        this.cityLevelService = service;
    }

    /** Configures the physical core material used by the orphan health check. */
    public void setWardCoreMaterial(org.bukkit.Material material) {
        this.wardCoreMaterial = material;
    }

    /** Registers the menu provider used to open the admin overview/detail GUIs. */
    public void setAdminMenuProvider(MenuProvider provider) {
        this.adminMenuProvider = provider;
    }

    // ── Player-picker contract (see PlayerPickMenuBuilder) ────────────────────

    /** Pending action tokens carried inside {@code pick.*} payloads. */
    private static final String ACT_WARD_TRANSFER = "ward.transfer";
    private static final String ACT_CITY_INVITE = "city.invite";
    private static final String ACT_CITY_KICK = "city.kick";
    private static final String ACT_CITY_ROLES = "city.roles";
    private static final String ACT_CITY_TRANSFER = "city.transfer";
    private static final String ACT_ESTATE_INVITE = "estate.invite";
    private static final String ACT_ESTATE_TRANSFER = "estate.transfer";

    /** Origin-menu tokens for the picker's Volver button. */
    private static final String ORIGIN_WARD = "ward";
    private static final String ORIGIN_CITY = "city";
    private static final String ORIGIN_ESTATE = "estate";

    @Override
    public void accept(MenuContext ctx, MenuAction action) {
        Player player = Bukkit.getPlayer(ctx.viewerId());
        if (player == null) return;

        String id = action.actionId();
        try {
            switch (id) {
                case "menu.close", "cerrar" -> handleClose(player);
                case "perfil" -> handlePerfil(player);
                case "city.open" -> handleCityOpen(player, ctx);
                case "ward.open" -> handleWardOpen(player);
                case "ward.upgrade" -> handleWardUpgrade(player, ctx);
                case "ward.toggle_permission" -> handleWardTogglePermission(player, ctx, action);
                case "ward.permissions" -> openWardPermissions(player, ctx);
                case "ward.permissions.back" -> handleWardPermissionsBack(player, action);
                case "ward.annex_city" -> handleWardAnnexCity(player, ctx);
                case "ward.disband" -> handleWardDisband(player, ctx);
                case "ward.upkeep_vault" -> handleOpenUpkeepVault(player, ctx);
                case "ward.transfer" -> openWardPick(player, ctx);
                case "city.invite" -> openCityPick(player, ctx, ACT_CITY_INVITE);
                case "city.kick" -> openCityPick(player, ctx, ACT_CITY_KICK);
                case "city.roles" -> openCityPick(player, ctx, ACT_CITY_ROLES);
                case "city.transfer" -> openCityPick(player, ctx, ACT_CITY_TRANSFER);
                case "city.bank" -> handleOpenCityTreasury(player, ctx);
                case "city.policy" -> handleCityPolicy(player, ctx, action);
                case "city.policies" -> openCityPolicies(player, ctx);
                case "city.policies.back" -> handleCityPoliciesBack(player, action);
                case "city.delete" -> handleCityDelete(player, ctx);
                case "estate.invite" -> openEstatePick(player, ctx, ACT_ESTATE_INVITE);
                case "estate.transfer" -> openEstatePick(player, ctx, ACT_ESTATE_TRANSFER);
                // ── Player/role pickers (stateless payloads) ──────────────────
                case "pick.player" -> handlePickPlayers(player, action);
                case "pick.target" -> handlePickExecute(player, action);
                case "pick.role" -> handlePickRoles(player, action);
                case "pick.role.set" -> handlePickRoleSet(player, action);
                case "pick.back" -> handlePickBack(player, action);
                case "estate.join" -> handleEstateJoin(player, ctx);
                case "estate.leave" -> handleEstateLeave(player, ctx);
                case "estate.start" -> handleEstateStart(player, ctx);
                case "estate.disband" -> handleEstateDisband(player, ctx);
                case "estateadmin.tp" -> handleAdminZoneTp(player, action);
                case "estateadmin.menu" -> handleAdminZoneMenu(player, action);
                case "wardadmin.page" -> {
                    if (requireWardAdmin(player)) {
                        int[] pf = parsePageFilter(action.payload());
                        openWardAdminOverview(player, pf[0], pf[1] == 1);
                    }
                }
                case "wardadmin.detail" -> {
                    if (requireWardAdmin(player)) openWardAdminDetail(player, action.payload());
                }
                case "wardadmin.tp" -> {
                    if (requireWardAdmin(player)) handleWardAdminTp(player, action);
                }
                case "wardadmin.dissolve" -> {
                    if (requireWardAdmin(player)) handleWardAdminDissolve(player, action);
                }
                case "wardadmin.openmenu" -> {
                    if (requireWardAdmin(player)) handleWardAdminOpenMenu(player, action);
                }
                case "cityadmin.page" -> {
                    if (requireCityAdmin(player)) openCityAdminOverview(player, parsePage(action.payload()));
                }
                case "cityadmin.detail" -> {
                    if (requireCityAdmin(player)) openCityAdminDetail(player, action.payload());
                }
                case "cityadmin.delete" -> {
                    if (requireCityAdmin(player)) handleCityAdminDelete(player, action);
                }
                case "cityadmin.openmenu" -> {
                    if (requireCityAdmin(player)) handleCityAdminOpenMenu(player, action);
                }
                default -> feedback(player, dev.dreamcraft.protection.message.Messages.apply(
                        msg("menu.unknown-action", "Acción no reconocida: {action}"),
                        "action", id), NamedTextColor.RED);
            }
        } catch (RuntimeException e) {
            player.sendMessage(Component.text("[DreamCraft] ", NamedTextColor.DARK_PURPLE)
                    .append(Component.text(e.getMessage() != null ? e.getMessage()
                            : msg("menu.internal-error", "Error interno"), NamedTextColor.RED)));
            playError(player);
        }
    }

    /** Catalog lookup with inline fallback (see {@link dev.dreamcraft.protection.message.Messages}). */
    private static String msg(String key, String fallback, Object... placeholders) {
        return dev.dreamcraft.protection.message.Messages.get().tr(key, fallback, placeholders);
    }

    // ── Cross-menu navigation (interconexión de menús de jugador) ─────────────

    /**
     * Opens the City menu of the Matriz shown in the ward status (slot 16):
     * resolves the ward's own city — the one rendered in the icon — instead of
     * the viewer's membership, so the opened menu always matches the label.
     */
    private void handleCityOpen(Player player, MenuContext ctx) {
        Ward ward = resolveWard(player, ctx);
        if (ward == null) return;
        City city = ward.hasCityMembership()
                ? cityService.findById(ward.cityId()).orElse(null)
                : null;
        if (city == null) {
            feedback(player, msg("menu.city.open-no-city",
                    "Este Núcleo ya no pertenece a una Matriz."), NamedTextColor.RED);
            playError(player);
            return;
        }
        var opener = cityMenuOpener;
        if (opener == null) {
            feedback(player, msg("menu.cityadmin.menu-unavailable",
                    "El menú de la Matriz no está disponible."), NamedTextColor.RED);
            playError(player);
            return;
        }
        scheduleOpener(player, () -> opener.accept(player, city));
    }

    /**
     * Opens the viewer's own Ward status menu («Volver al Núcleo» in the City
     * menu): location lookup first, then the owner's first ward — mirrors
     * {@code WardCommand.resolveWard} without the UUID-argument branch. The
     * CityViewModel carries no viewer-ward flag, so the button renders
     * unconditionally and this handler degrades gracefully when the viewer
     * owns no ward.
     */
    private void handleWardOpen(Player player) {
        Ward ward = wardService
                .findAtLocation(player.getWorld().getName(),
                        player.getLocation().getBlockX(),
                        player.getLocation().getBlockZ())
                .or(() -> wardService.findByOwner(player.getUniqueId()).stream().findFirst())
                .orElse(null);
        if (ward == null) {
            feedback(player, msg("menu.ward.none-found",
                    "No tienes ningún Núcleo."), NamedTextColor.RED);
            playError(player);
            return;
        }
        var opener = wardMenuOpener;
        if (opener == null) {
            feedback(player, msg("menu.wardadmin.menu-unavailable",
                    "El menú del Núcleo no está disponible."), NamedTextColor.RED);
            playError(player);
            return;
        }
        scheduleOpener(player, () -> opener.accept(player, ward));
    }

    // ── Ward actions ──────────────────────────────────────────────────────────

    /**
     * Opens the Ward upkeep vault: a free-use chest inventory. Nothing is
     * consumed while it is open — the close listener settles the contents
     * (accepted materials → units, everything else returned).
     */
    private void handleOpenUpkeepVault(Player player, MenuContext ctx) {
        if (upkeepService == null) {
            feedback(player, msg("menu.ward.deposits-unavailable", "Depósitos no disponibles."), NamedTextColor.RED);
            playError(player);
            return;
        }
        Ward ward = resolveWard(player, ctx);
        if (ward == null) return;
        if (!upkeepService.canDeposit(player, ward)) {
            feedback(player, msg("menu.ward.cannot-deposit", "No puedes depositar upkeep en este Ward."), NamedTextColor.RED);
            playError(player);
            return;
        }
        // Open next tick — opening inventories during a click event causes ghost items
        UUID wardId = ward.id();
        String wardName = ward.name();
        Bukkit.getScheduler().runTask(plugin(), () -> {
            Player online = Bukkit.getPlayer(player.getUniqueId());
            if (online == null) return;
            Component title = composeVaultTitle(online, "ward_upkeep_vault", 27, "&8⚔ Bóveda de Upkeep · " + wardName);
            var holder = dev.dreamcraft.protection.ui.WardUpkeepVaultHolder.create(wardId, title);
            online.openInventory(holder.getInventory());
            playSuccess(online);
        });
    }

    /** Helper composing HUD background titles for 27-slot vaults (upkeep & treasury). */
    private Component composeVaultTitle(Player player, String menuId, int size, String legacyTitle) {
        if (adminMenuProvider instanceof VanillaMenuProvider vmp) {
            var def = new MenuDefinition(menuId, legacyTitle, size, java.util.List.of());
            return vmp.composeTitle(def, player.getUniqueId());
        }
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(legacyTitle);
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
            feedback(player, msg("menu.ward.deposits-unavailable", "Depósitos no disponibles."), NamedTextColor.RED);
            playError(player);
            return;
        }
        Ward ward = resolveWard(player, ctx);
        if (ward == null) return;
        if (!upkeepService.canDeposit(player, ward)) {
            feedback(player, msg("menu.ward.cannot-deposit", "No puedes depositar upkeep en este Ward."), NamedTextColor.RED);
            playError(player);
            return;
        }
        if (!upkeepService.isAccepted(material)) {
            feedback(player, msg("menu.ward.item-not-accepted",
                    "Ítem no válido para upkeep. Mira los aceptados en el menú."), NamedTextColor.RED);
            playError(player);
            return;
        }
        var receipt = upkeepService.deposit(ward, player, material, amount);
        consume.run();
        player.sendMessage(CommandMessages.WARD_PREFIX
                .append(Component.text(msg("menu.ward.deposited", "Depositaste "), NamedTextColor.GREEN))
                .append(Component.text(receipt.amount() + "x "
                        + upkeepService.displayName(receipt.material()), NamedTextColor.WHITE))
                .append(Component.text(dev.dreamcraft.protection.message.Messages.apply(
                        msg("menu.ward.deposited-units", " → +{units} unidades. Balance: {balance}"),
                        "units", receipt.unitsCredited(), "balance", receipt.newBalance()), NamedTextColor.GREEN)));
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
            feedback(player, msg("menu.ward.owner-only-upgrade", "Solo el owner puede mejorar el Ward."), NamedTextColor.RED);
            playError(player);
            return;
        }
        if (upgradeService == null) {
            // Fallback: no cost service wired — behave as before
            int radiusAfter = wardService.computeRadiusAfter(ward, 100);
            var conflictOpt = wardService.findForeignConflict(ward, radiusAfter);
            if (conflictOpt.isPresent()) {
                Ward other = conflictOpt.get();
                feedback(player, dev.dreamcraft.protection.message.Messages.apply(
                        msg("menu.ward.conflict-radius",
                                "No puedes mejorar: el radio nuevo ({radius}) alcanzaría la Ward '{other}' de {owner}."),
                        "radius", radiusAfter, "other", other.name(), "owner", ownerName(other)), NamedTextColor.RED);
                playError(player);
                return;
            }
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
            feedback(player, dev.dreamcraft.protection.message.Messages.apply(
                    msg("menu.ward.max-tier", "El Ward ya está en el tier máximo ({tier})."),
                    "tier", ward.tier()), NamedTextColor.YELLOW);
            playError(player);
            return;
        }
        var quote = quoteOpt.get();

        // 2. Refuse the upgrade when the new radius would reach a foreign Ward
        var conflictOpt = wardService.findForeignConflict(ward, quote.radiusAfter());
        if (conflictOpt.isPresent()) {
            Ward other = conflictOpt.get();
            feedback(player, dev.dreamcraft.protection.message.Messages.apply(
                    msg("menu.ward.conflict-radius-tier",
                            "No puedes mejorar al tier {tier}: el radio nuevo ({radius}) alcanzaría la Ward '{other}' de {owner}."),
                    "tier", quote.targetTierKey(), "radius", quote.radiusAfter(),
                    "other", other.name(), "owner", ownerName(other)), NamedTextColor.RED);
            playError(player);
            return;
        }

        // 2. Verify the player can pay the item cost
        var missing = upgradeService.missingItems(player, quote);
        if (!missing.isEmpty()) {
            player.sendMessage(CommandMessages.WARD_PREFIX.append(Component.text(
                    dev.dreamcraft.protection.message.Messages.apply(
                            msg("menu.ward.missing-items", "Te faltan ítems para mejorar al tier {tier}:"),
                            "tier", quote.targetTierKey()), NamedTextColor.RED)));
            missing.forEach(player::sendMessage);
            playError(player);
            return;
        }

        // 3. Charge, apply score gain and sync the region
        upgradeService.charge(player, quote);
        wardService.addBaseScore(ward, quote.scoreGain());
        worldGuardAdapter.resizeRegion(ward, -64, 320);

        player.sendMessage(CommandMessages.WARD_PREFIX
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
            feedback(player, msg("menu.ward.owner-only-permissions", "Solo el owner puede cambiar permisos."), NamedTextColor.RED);
            return;
        }
        WardPermission perm;
        try {
            perm = WardPermission.valueOf(action.payload());
        } catch (IllegalArgumentException e) {
            feedback(player, dev.dreamcraft.protection.message.Messages.apply(
                    msg("menu.ward.invalid-permission", "Permiso inválido: {perm}"),
                    "perm", action.payload()), NamedTextColor.RED);
            return;
        }
        if (ward.hasPermission(perm)) {
            ward.revokePermission(perm);
            feedback(player, dev.dreamcraft.protection.message.Messages.apply(
                    msg("menu.ward.permission-revoked", "Permiso {perm} revocado."),
                    "perm", perm.name()), NamedTextColor.YELLOW);
        } else {
            ward.grantPermission(perm);
            feedback(player, dev.dreamcraft.protection.message.Messages.apply(
                    msg("menu.ward.permission-granted", "Permiso {perm} concedido."),
                    "perm", perm.name()), NamedTextColor.GREEN);
        }
        if (perm == WardPermission.PUBLIC_CONTAINERS) {
            worldGuardAdapter.setPublicContainerAccess(ward, ward.hasPermission(perm));
        }
        wardService.assignWorldGuardRegion(ward, ward.worldGuardRegionId()); // persist
        playSuccess(player);
        // Reopen the permissions sub-menu so the viewer can keep toggling flags.
        reopenWardPermissions(player, ward.id());
    }

    private void handleWardAnnexCity(Player player, MenuContext ctx) {
        Ward ward = resolveWard(player, ctx);
        if (ward == null) return;
        if (!ward.ownerId().equals(player.getUniqueId())) {
            feedback(player, msg("menu.ward.owner-only-annex", "Solo el owner puede anexar el Ward."), NamedTextColor.RED);
            return;
        }
        if (ward.hasCityMembership()) {
            feedback(player, msg("menu.ward.already-in-city", "El Ward ya pertenece a una ciudad."), NamedTextColor.YELLOW);
            return;
        }
        // Find the city the player is a member of
        var optCity = cityService.findByMember(player.getUniqueId());
        if (optCity.isEmpty()) {
            feedback(player, msg("menu.ward.not-in-city", "No eres miembro de ninguna ciudad."), NamedTextColor.RED);
            return;
        }
        City city = optCity.get();
        wardService.setCityMembership(ward, city.id());
        // Project domain membership onto the WG region (single reconciliation path)
        dev.dreamcraft.protection.service.WardAccessSync.project(ward, cityService, worldGuardAdapter);
        feedback(player, dev.dreamcraft.protection.message.Messages.apply(
                msg("menu.ward.annexed", "Ward anexado a la ciudad {city}."),
                "city", city.name()), NamedTextColor.GREEN);
        playSuccess(player);
        reopenWardMenu(player, ward);
    }

    private void handleWardDisband(Player player, MenuContext ctx) {
        Ward ward = resolveWard(player, ctx);
        if (ward == null) return;
        if (!ward.ownerId().equals(player.getUniqueId())) {
            feedback(player, msg("menu.ward.owner-only-disband", "Solo el owner puede disolver el Ward."), NamedTextColor.RED);
            return;
        }
        // Single dissolution contract: region + repository + physical core +
        // tagged founder item back to the owner (inventory or drop at feet).
        boolean refunded;
        boolean coreBlockMissing = false;
        if (wardDissolutionService != null) {
            var result = wardDissolutionService.dissolve(ward, player, true);
            refunded = result.refunded();
            coreBlockMissing = !result.coreBlockRemoved();
        } else {
            refunded = legacyDissolve(ward);
        }
        player.closeInventory();
        feedback(player, msg("menu.ward.disbanded", "Ward disuelto.")
                + (refunded ? msg("menu.ward.disbanded-refund",
                        " Tu Núcleo volvió a tu inventario.") : "")
                + (!refunded && coreBlockMissing ? msg("menu.ward.disbanded-no-core-block",
                        " (sin bloque físico: nada devuelto)") : ""), NamedTextColor.GREEN);
        playSuccess(player);
    }

    /** Pre-contract fallback (service not wired): region + repository teardown only. */
    private boolean legacyDissolve(Ward ward) {
        worldGuardAdapter.removeRegion(ward);
        wardService.delete(ward);
        return false;
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
            feedback(player, msg("menu.city.treasury-unavailable", "El tesoro no está disponible."), NamedTextColor.RED);
            playError(player);
            return;
        }
        City city = resolveCity(player, ctx);
        if (city == null) return;
        CityRole role = city.roleOf(player.getUniqueId());
        boolean council = city.isGovernor(player.getUniqueId()) || role == CityRole.COUNCIL;
        if (!council) {
            feedback(player, msg("menu.city.treasury-role-required", "Requerís rol Council o superior para el tesoro."), NamedTextColor.RED);
            playError(player);
            return;
        }
        UUID cityId = city.id();
        String cityName = city.name();
        int value = treasuryStore.computeValue(treasuryStore.get(cityId));
        Bukkit.getScheduler().runTask(plugin(), () -> {
            Player online = Bukkit.getPlayer(player.getUniqueId());
            if (online == null) return;
            Component title = composeVaultTitle(online, "city_treasury_vault", 27, "&8★ Tesoro de " + cityName);
            var holder = dev.dreamcraft.protection.ui.CityTreasuryVaultHolder.create(cityId,
                    title,
                    treasuryStore.get(cityId));
            online.openInventory(holder.getInventory());
            online.sendMessage(CommandMessages.CITY_PREFIX
                    .append(Component.text(msg("menu.city.treasury-value-prefix", "Valor actual del tesoro: "), NamedTextColor.GRAY))
                    .append(Component.text(value + msg("menu.city.treasury-value-suffix", " unidades de riqueza"), NamedTextColor.YELLOW)));
        });
    }

    private void handleCityPolicy(Player player, MenuContext ctx, MenuAction action) {
        City city = resolveCity(player, ctx);
        if (city == null) return;
        if (!city.isGovernor(player.getUniqueId())) {
            feedback(player, msg("menu.city.owner-only-policy", "Solo el Gobernador puede cambiar políticas."), NamedTextColor.RED);
            return;
        }
        CityPolicy policy;
        try {
            policy = CityPolicy.valueOf(action.payload());
        } catch (IllegalArgumentException e) {
            feedback(player, dev.dreamcraft.protection.message.Messages.apply(
                    msg("menu.city.invalid-policy", "Política inválida: {policy}"),
                    "policy", action.payload()), NamedTextColor.RED);
            return;
        }
        boolean newState = !city.hasPolicy(policy);
        cityService.setPolicy(city, policy, newState);
        feedback(player, dev.dreamcraft.protection.message.Messages.apply(
                newState ? msg("menu.city.policy-on", "Política {policy} activada.")
                         : msg("menu.city.policy-off", "Política {policy} desactivada."),
                "policy", policy.name()), NamedTextColor.GREEN);
        playSuccess(player);
        // Reopen the policies sub-menu so the viewer can keep toggling flags.
        reopenCityPolicies(player, city.id());
    }

    private void handleCityDelete(Player player, MenuContext ctx) {
        City city = resolveCity(player, ctx);
        if (city == null) return;
        if (!city.isGovernor(player.getUniqueId())) {
            feedback(player, msg("menu.city.owner-only-delete", "Solo el Gobernador puede eliminar la ciudad."), NamedTextColor.RED);
            return;
        }
        // Disassociate all wards from this city, then re-project: their region
        // member lists collapse to empty (city-granted access fully revoked)
        for (Ward ward : wardService.findByCity(city.id())) {
            wardService.setCityMembership(ward, null);
            dev.dreamcraft.protection.service.WardAccessSync.project(ward, cityService, worldGuardAdapter);
        }
        cityService.delete(city);
        player.closeInventory();
        feedback(player, dev.dreamcraft.protection.message.Messages.apply(
                msg("menu.city.deleted", "Ciudad {city} eliminada."),
                "city", city.name()), NamedTextColor.GREEN);
        playSuccess(player);
    }

    // ── Estate actions ────────────────────────────────────────────────────────

    private void handleEstateJoin(Player player, MenuContext ctx) {
        Estate estate = resolveEstate(player, ctx);
        if (estate == null) return;
        if (estate.persistent()) {
            feedback(player, msg("menu.estate.admin-zone",
                    "Esta zona la administra el servidor: creá tu grupo con /{cmd.estate} discover."), NamedTextColor.RED);
            playError(player);
            return;
        }
        if (estate.isOwner(player.getUniqueId())) {
            feedback(player, msg("menu.estate.already-owner", "Ya eres el owner de esta instancia."), NamedTextColor.YELLOW);
            return;
        }
        if (estate.isMember(player.getUniqueId())) {
            feedback(player, msg("menu.estate.already-member", "Ya eres miembro de la instancia."), NamedTextColor.YELLOW);
            return;
        }
        estateService.addMember(estate, player.getUniqueId());
        worldGuardAdapter.syncEstateMembers(estate);
        feedback(player, dev.dreamcraft.protection.message.Messages.apply(
                msg("menu.estate.joined", "Te uniste al Estate {estate}."),
                "estate", estate.name()), NamedTextColor.GREEN);
        playSuccess(player);
    }

    private void handleEstateLeave(Player player, MenuContext ctx) {
        Estate estate = resolveEstate(player, ctx);
        if (estate == null) return;
        if (estate.isOwner(player.getUniqueId())) {
            feedback(player, msg("menu.estate.owner-cannot-leave", "El owner no puede salir; transfiere o disuelve el Estate."), NamedTextColor.RED);
            return;
        }
        if (!estate.isMember(player.getUniqueId())) {
            feedback(player, msg("menu.estate.not-member", "No eres miembro de la instancia."), NamedTextColor.RED);
            return;
        }
        estateService.removeMember(estate, player.getUniqueId());
        worldGuardAdapter.syncEstateMembers(estate);
        player.closeInventory();
        feedback(player, dev.dreamcraft.protection.message.Messages.apply(
                msg("menu.estate.left", "Saliste de la instancia {estate}."),
                "estate", estate.name()), NamedTextColor.GREEN);
        playSuccess(player);
    }

    private void handleEstateStart(Player player, MenuContext ctx) {
        Estate estate = resolveEstate(player, ctx);
        if (estate == null) return;
        if (!estate.isOwner(player.getUniqueId())) {
            feedback(player, msg("menu.estate.owner-only-start", "Solo el owner puede iniciar la instancia."), NamedTextColor.RED);
            return;
        }
        String instanceId = "inst-" + estate.id().toString().substring(0, 8);
        boolean started = estateService.startInstance(estate, instanceId);
        if (!started) {
            feedback(player,                 msg("menu.estate.instance-active", "La instancia ya tiene un mundo activo."), NamedTextColor.YELLOW);
            return;
        }
        // END-type estates pre-open their private End world + dragon right away
        String extra = "";
        if (endInstanceService != null && endInstanceService.preopen(estate)) {
            extra = msg("menu.estate.instance-ready", " El End privado está listo con la dragona.");
        }
        feedback(player, dev.dreamcraft.protection.message.Messages.apply(
                msg("menu.estate.started", "Instancia de {estate} iniciada."),
                "estate", estate.name()) + extra, NamedTextColor.GREEN);
        playSuccess(player);
    }

    private void handleEstateDisband(Player player, MenuContext ctx) {
        Estate estate = resolveEstate(player, ctx);
        if (estate == null) return;
        boolean admin = player.hasPermission("dreamcraft.protection.admin");
        if (!admin && !estate.isOwner(player.getUniqueId())) {
            feedback(player, msg("menu.estate.owner-only-disband", "Solo el owner puede disolver la instancia."), NamedTextColor.RED);
            return;
        }
        if (estate.persistent() && !admin) {
            // Admin zones outlive groups: they are managed via /{cmd.estate} admin
            feedback(player, msg("menu.estate.admin-zone",
                    "Esta zona la administra el servidor: creá tu grupo con /{cmd.estate} discover."), NamedTextColor.RED);
            playError(player);
            return;
        }
        if (endInstanceService != null && estate.type().usesEndInstance()) {
            endInstanceService.resetInstance(estate);
        }
        // The estate is gone: its pending zone edits must not outlive it
        if (endInstanceService != null) endInstanceService.clearZoneEdits(estate.id());
        worldGuardAdapter.removeEstateAreaRegion(estate);
        estateService.delete(estate);
        player.closeInventory();
        feedback(player, dev.dreamcraft.protection.message.Messages.apply(
                msg("menu.estate.disbanded", "Instancia {estate} disuelta."),
                "estate", estate.name()), NamedTextColor.GREEN);
        playSuccess(player);
    }

    // ── Player/role pickers (menus ejecutan la acción real; nada de hints) ────

    /*
     * v1 limit: the selector lists ONLINE players only. The underlying services
     * accept offline UUIDs for kick/transfer-style actions (e.g.
     * CityService.removeMember / transferGovernorship), so an offline variant
     * could be layered later without touching this grammar — chat/conversation
     * input is intentionally out of scope for this iteration.
     */

    /** Opens the online-player picker to choose the new Ward owner. */
    private void openWardPick(Player player, MenuContext ctx) {
        Ward ward = resolveWard(player, ctx);
        if (ward == null) return;
        if (!ward.ownerId().equals(player.getUniqueId())) {
            feedback(player, msg("menu.ward.owner-only-transfer",
                    "Solo el owner puede transferir el Ward."), NamedTextColor.RED);
            playError(player);
            return;
        }
        openPlayerPickLater(player, ACT_WARD_TRANSFER, ORIGIN_WARD, ward.id(), 0);
    }

    /** Opens the online-player picker for a pending city action. */
    private void openCityPick(Player player, MenuContext ctx, String action) {
        City city = resolveCity(player, ctx);
        if (city == null) return;
        if (!validateCityAction(player, city, action)) return;
        openPlayerPickLater(player, action, ORIGIN_CITY, city.id(), 0);
    }

    /** Opens the online-player picker for a pending estate action. */
    private void openEstatePick(Player player, MenuContext ctx, String action) {
        Estate estate = resolveEstate(player, ctx);
        if (estate == null) return;
        if (!estate.isOwner(player.getUniqueId())) {
            boolean invite = ACT_ESTATE_INVITE.equals(action);
            feedback(player, msg(invite ? "menu.estate.owner-only-invite" : "menu.estate.owner-only-transfer",
                    invite ? "Solo el owner puede invitar miembros." : "Solo el owner puede transferir el Estate."),
                    NamedTextColor.RED);
            playError(player);
            return;
        }
        openPlayerPickLater(player, action, ORIGIN_ESTATE, estate.id(), 0);
    }

    /** Payload navigation: rebuild the candidate grid for another page. */
    private void handlePickPlayers(Player player, MenuAction action) {
        String[] parts = splitPayload(action.payload());
        UUID entityId = parts.length > 2 ? parseUuid(parts[2]) : null;
        String act = parts.length > 0 ? parts[0] : "";
        String origin = parts.length > 1 ? parts[1] : "";
        int page = parts.length > 3 ? parseIntOrDefault(parts[3], 0) : 0;
        if (entityId == null) {
            contextLost(player);
            return;
        }
        if (!validateAuthority(player, act, origin, entityId)) return;
        openPlayerPickLater(player, act, origin, entityId, page);
    }

    /**
     * Executes the pending action against the picked player. Authority and every
     * domain rule are re-checked server-side here even though the originating
     * button was already gated in the view model.
     */
    private void handlePickExecute(Player player, MenuAction action) {
        String[] parts = splitPayload(action.payload());
        UUID entityId = parts.length > 2 ? parseUuid(parts[2]) : null;
        UUID targetId = parts.length > 3 ? parseUuid(parts[3]) : null;
        String act = parts.length > 0 ? parts[0] : "";
        String origin = parts.length > 1 ? parts[1] : "";
        if (entityId == null || targetId == null) {
            contextLost(player);
            return;
        }
        if (!validateAuthority(player, act, origin, entityId)) return;
        switch (act) {
            case ACT_WARD_TRANSFER -> executeWardTransfer(player, entityId, targetId);
            case ACT_CITY_INVITE -> executeCityInvite(player, entityId, targetId, origin);
            case ACT_CITY_KICK -> executeCityKick(player, entityId, targetId, origin);
            case ACT_CITY_TRANSFER -> executeCityTransfer(player, entityId, targetId, origin);
            case ACT_ESTATE_INVITE -> executeEstateInvite(player, entityId, targetId, origin);
            case ACT_ESTATE_TRANSFER -> executeEstateTransfer(player, entityId, targetId, origin);
            default -> contextLost(player);
        }
    }

    /** Second step of city.roles: pick the role for an already-chosen member. */
    private void handlePickRoles(Player player, MenuAction action) {
        String[] parts = splitPayload(action.payload());
        UUID targetId = parts.length > 0 ? parseUuid(parts[0]) : null;
        UUID cityId = parts.length > 1 ? parseUuid(parts[1]) : null;
        if (targetId == null || cityId == null) {
            contextLost(player);
            return;
        }
        City city = cityService.findById(cityId).orElse(null);
        if (city == null) {
            contextLost(player);
            return;
        }
        if (!city.isGovernor(player.getUniqueId())) {
            feedback(player, msg("menu.city.owner-only-roles",
                    "Solo el Gobernador puede asignar roles."), NamedTextColor.RED);
            playError(player);
            return;
        }
        var def = dev.dreamcraft.protection.presentation.menu.PlayerPickMenuBuilder
                .buildRoles(targetId, CommandMessages.resolveName(targetId), city.id());
        menuProviderOpenLater(player, def);
    }

    /** Applies the chosen role (GOVERNOR transfers the governorship). */
    private void handlePickRoleSet(Player player, MenuAction action) {
        String[] parts = splitPayload(action.payload());
        UUID cityId = parts.length > 2 ? parseUuid(parts[2]) : null;
        UUID targetId = parts.length > 1 ? parseUuid(parts[1]) : null;
        CityRole role;
        try {
            role = CityRole.valueOf(parts[0]);
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            role = null;
        }
        if (cityId == null || targetId == null || role == null) {
            contextLost(player);
            return;
        }
        City city = cityService.findById(cityId).orElse(null);
        if (city == null) {
            contextLost(player);
            return;
        }
        if (!city.isGovernor(player.getUniqueId())) {
            feedback(player, msg("menu.city.owner-only-roles",
                    "Solo el Gobernador puede asignar roles."), NamedTextColor.RED);
            playError(player);
            return;
        }
        if (role == CityRole.GOVERNOR) {
            if (targetId.equals(player.getUniqueId())) {
                feedback(player, msg("menu.city.self-transfer",
                        "No puedes transferirte la gobernanza a ti mismo."), NamedTextColor.RED);
                playError(player);
                return;
            }
            if (cityService.transferGovernorship(city, targetId)) {
                feedback(player, msg("menu.city.transferred", "Gobernanza transferida a {player}.",
                        "player", CommandMessages.resolveName(targetId)), NamedTextColor.GREEN);
                playSuccess(player);
                reopenOriginMenu(player, ORIGIN_CITY, city.id());
            } else {
                feedback(player, msg("menu.city.transfer-not-member",
                        "{player} debe ser miembro de la ciudad.",
                        "player", CommandMessages.resolveName(targetId)), NamedTextColor.RED);
                playError(player);
            }
            return;
        }
        if (cityService.setRole(city, targetId, role)) {
            feedback(player, msg("menu.city.role-set", "Rol de {player} establecido a {role}.",
                    "player", CommandMessages.resolveName(targetId), "role", role.name()), NamedTextColor.GREEN);
            playSuccess(player);
            reopenOriginMenu(player, ORIGIN_CITY, city.id());
        } else {
            feedback(player, msg("menu.city.role-not-member", "{player} no es miembro de la ciudad.",
                    "player", CommandMessages.resolveName(targetId)), NamedTextColor.YELLOW);
            playError(player);
        }
    }

    /** Volver/Cancelar: back to the origin menu (close when unreachable). */
    private void handlePickBack(Player player, MenuAction action) {
        String[] parts = splitPayload(action.payload());
        UUID entityId = parts.length > 1 ? parseUuid(parts[1]) : null;
        String origin = parts.length > 0 ? parts[0] : "";
        if (entityId == null || !reopenOriginMenu(player, origin, entityId)) {
            player.closeInventory();
        }
    }

    // ── Picker executions (validaciones espejo de los comandos) ───────────────

    /** Mirrors ProtectionCommand.handleTransfer: owner-only, online target, no self. */
    private void executeWardTransfer(Player player, UUID wardId, UUID targetId) {
        Ward ward = wardService.findById(wardId).orElse(null);
        if (ward == null) {
            contextLost(player);
            return;
        }
        if (!ward.ownerId().equals(player.getUniqueId())) {
            feedback(player, msg("menu.ward.owner-only-transfer",
                    "Solo el owner puede transferir el Ward."), NamedTextColor.RED);
            playError(player);
            return;
        }
        if (targetId.equals(player.getUniqueId())) {
            feedback(player, msg("menu.ward.self-transfer",
                    "No puedes transferirte el Ward a ti mismo."), NamedTextColor.RED);
            playError(player);
            return;
        }
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            feedback(player, msg("menu.pick.offline", "Ese jugador ya no está en línea."), NamedTextColor.RED);
            playError(player);
            return;
        }
        wardService.transferOwnership(ward, targetId, OwnerType.PLAYER);
        worldGuardAdapter.syncOwner(ward);
        feedback(player, msg("menu.ward.transferred", "Ownership transferido a {player}.",
                "player", target.getName()), NamedTextColor.GREEN);
        target.sendMessage(CommandMessages.prefixed("protection",
                msg("menu.ward.transferred-target", "{player} te transfirió su Ward {ward}.",
                        "player", player.getName(), "ward", ward.name()), NamedTextColor.GREEN));
        playSuccess(player);
        player.closeInventory();
    }

    /** Mirrors CityCommand.handleInvite: Council+, online target, WG re-projection. */
    private void executeCityInvite(Player player, UUID cityId, UUID targetId, String origin) {
        City city = cityService.findById(cityId).orElse(null);
        if (city == null) {
            contextLost(player);
            return;
        }
        if (!isResidentManager(city, player)) {
            feedback(player, msg("menu.city.residents-role-required",
                    "Requerís rol Council o superior para gestionar residentes."), NamedTextColor.RED);
            playError(player);
            return;
        }
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            feedback(player, msg("menu.pick.offline", "Ese jugador ya no está en línea."), NamedTextColor.RED);
            playError(player);
            return;
        }
        if (cityService.addMember(city, target.getUniqueId())) {
            // New resident gains access to every annexed ward's region
            dev.dreamcraft.protection.service.WardAccessSync.projectAll(
                    wardService.findByCity(city.id()), cityService, worldGuardAdapter);
            feedback(player, msg("menu.city.invited", "{player} invitado a la ciudad.",
                    "player", target.getName()), NamedTextColor.GREEN);
            target.sendMessage(CommandMessages.CITY_PREFIX.append(Component.text(
                    msg("menu.city.invited-target", "Fuiste invitado a la ciudad {city}.", "city", city.name()),
                    NamedTextColor.GREEN)));
            playSuccess(player);
            reopenOriginMenu(player, origin, city.id());
        } else {
            feedback(player, msg("menu.city.already-member", "{player} ya es miembro de la ciudad.",
                    "player", target.getName()), NamedTextColor.YELLOW);
            playError(player);
        }
    }

    /** Mirrors CityCommand.handleKick: Council+, never the governor, WG re-projection. */
    private void executeCityKick(Player player, UUID cityId, UUID targetId, String origin) {
        City city = cityService.findById(cityId).orElse(null);
        if (city == null) {
            contextLost(player);
            return;
        }
        if (!isResidentManager(city, player)) {
            feedback(player, msg("menu.city.residents-role-required",
                    "Requerís rol Council o superior para gestionar residentes."), NamedTextColor.RED);
            playError(player);
            return;
        }
        if (city.isGovernor(targetId)) {
            feedback(player, msg("menu.city.cannot-kick-governor",
                    "No puedes expulsar al Gobernador."), NamedTextColor.RED);
            playError(player);
            return;
        }
        if (cityService.removeMember(city, targetId)) {
            // Re-project: the ex-member loses access to every annexed ward's region
            dev.dreamcraft.protection.service.WardAccessSync.projectAll(
                    wardService.findByCity(city.id()), cityService, worldGuardAdapter);
            feedback(player, msg("menu.city.kicked", "Miembro expulsado de la ciudad."), NamedTextColor.GREEN);
            playSuccess(player);
            reopenOriginMenu(player, origin, city.id());
        } else {
            feedback(player, msg("menu.city.kick-not-member",
                    "Ese jugador no es miembro de la ciudad."), NamedTextColor.YELLOW);
            playError(player);
        }
    }

    /** Mirrors CityCommand.handleTransfer: governor-only, target must be member. */
    private void executeCityTransfer(Player player, UUID cityId, UUID targetId, String origin) {
        City city = cityService.findById(cityId).orElse(null);
        if (city == null) {
            contextLost(player);
            return;
        }
        if (!city.isGovernor(player.getUniqueId())) {
            feedback(player, msg("menu.city.owner-only-transfer",
                    "Solo el Gobernador puede transferir la gobernanza."), NamedTextColor.RED);
            playError(player);
            return;
        }
        if (targetId.equals(player.getUniqueId())) {
            feedback(player, msg("menu.city.self-transfer",
                    "No puedes transferirte la gobernanza a ti mismo."), NamedTextColor.RED);
            playError(player);
            return;
        }
        if (cityService.transferGovernorship(city, targetId)) {
            feedback(player, msg("menu.city.transferred", "Gobernanza transferida a {player}.",
                    "player", CommandMessages.resolveName(targetId)), NamedTextColor.GREEN);
            playSuccess(player);
            reopenOriginMenu(player, origin, city.id());
        } else {
            feedback(player, msg("menu.city.transfer-not-member",
                    "{player} debe ser miembro de la ciudad.",
                    "player", CommandMessages.resolveName(targetId)), NamedTextColor.RED);
            playError(player);
        }
    }

    /** Mirrors EstateCommand.handleInvite: owner-only, online target, WG member sync. */
    private void executeEstateInvite(Player player, UUID estateId, UUID targetId, String origin) {
        Estate estate = estateService.findById(estateId).orElse(null);
        if (estate == null) {
            contextLost(player);
            return;
        }
        if (!estate.isOwner(player.getUniqueId())) {
            feedback(player, msg("menu.estate.owner-only-invite",
                    "Solo el owner puede invitar miembros."), NamedTextColor.RED);
            playError(player);
            return;
        }
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            feedback(player, msg("menu.pick.offline", "Ese jugador ya no está en línea."), NamedTextColor.RED);
            playError(player);
            return;
        }
        if (estateService.addMember(estate, target.getUniqueId())) {
            worldGuardAdapter.syncEstateMembers(estate);
            feedback(player, msg("menu.estate.invited", "{player} invitado a la instancia.",
                    "player", target.getName()), NamedTextColor.GREEN);
            target.sendMessage(CommandMessages.ESTATE_PREFIX.append(Component.text(
                    msg("menu.estate.invited-target", "Fuiste invitado a la instancia {estate}.",
                            "estate", estate.name()), NamedTextColor.GREEN)));
            playSuccess(player);
            reopenOriginMenu(player, origin, estate.id());
        } else {
            feedback(player, msg("menu.estate.already-member-invite",
                    "{player} ya es miembro de la instancia.", "player", target.getName()),
                    NamedTextColor.YELLOW);
            playError(player);
        }
    }

    /** Mirrors EstateCommand.handleTransfer: owner-only, target must be member. */
    private void executeEstateTransfer(Player player, UUID estateId, UUID targetId, String origin) {
        Estate estate = estateService.findById(estateId).orElse(null);
        if (estate == null) {
            contextLost(player);
            return;
        }
        if (!estate.isOwner(player.getUniqueId())) {
            feedback(player, msg("menu.estate.owner-only-transfer",
                    "Solo el owner puede transferir el Estate."), NamedTextColor.RED);
            playError(player);
            return;
        }
        if (targetId.equals(player.getUniqueId())) {
            feedback(player, msg("menu.estate.self-transfer",
                    "No puedes transferirte el Estate a ti mismo."), NamedTextColor.RED);
            playError(player);
            return;
        }
        if (estateService.transferOwnership(estate, targetId)) {
            feedback(player, msg("menu.estate.transferred",
                    "Liderazgo de la instancia transferido a {player}.",
                    "player", CommandMessages.resolveName(targetId)), NamedTextColor.GREEN);
            playSuccess(player);
            reopenOriginMenu(player, origin, estate.id());
        } else {
            feedback(player, msg("menu.estate.transfer-not-member",
                    "{player} debe ser miembro de la instancia.",
                    "player", CommandMessages.resolveName(targetId)), NamedTextColor.RED);
            playError(player);
        }
    }

    // ── Picker plumbing ───────────────────────────────────────────────────────

    /**
     * Server-side authority gate for a pending picker action, re-resolving the
     * aggregate fresh from its repository — the view model flags that hid the
     * button are presentation-only and are never trusted here.
     */
    private boolean validateAuthority(Player player, String action, String origin, UUID entityId) {
        return switch (origin) {
            case ORIGIN_WARD -> {
                Ward ward = wardService.findById(entityId).orElse(null);
                if (ward == null) {
                    contextLost(player);
                    yield false;
                }
                if (!ward.ownerId().equals(player.getUniqueId())) {
                    feedback(player, msg("menu.ward.owner-only-transfer",
                            "Solo el owner puede transferir el Ward."), NamedTextColor.RED);
                    playError(player);
                    yield false;
                }
                yield true;
            }
            case ORIGIN_CITY -> {
                City city = cityService.findById(entityId).orElse(null);
                if (city == null) {
                    contextLost(player);
                    yield false;
                }
                yield validateCityAction(player, city, action);
            }
            case ORIGIN_ESTATE -> {
                Estate estate = estateService.findById(entityId).orElse(null);
                if (estate == null) {
                    contextLost(player);
                    yield false;
                }
                if (!estate.isOwner(player.getUniqueId())) {
                    boolean invite = ACT_ESTATE_INVITE.equals(action);
                    feedback(player, msg(invite ? "menu.estate.owner-only-invite" : "menu.estate.owner-only-transfer",
                            invite ? "Solo el owner puede invitar miembros." : "Solo el owner puede transferir el Estate."),
                            NamedTextColor.RED);
                    playError(player);
                    yield false;
                }
                yield true;
            }
            default -> {
                contextLost(player);
                yield false;
            }
        };
    }

    /** Per-action authority for city picks (espejo de CityCommand). */
    private boolean validateCityAction(Player player, City city, String action) {
        return switch (action) {
            case ACT_CITY_INVITE, ACT_CITY_KICK -> {
                if (!isResidentManager(city, player)) {
                    feedback(player, msg("menu.city.residents-role-required",
                            "Requerís rol Council o superior para gestionar residentes."), NamedTextColor.RED);
                    playError(player);
                    yield false;
                }
                yield true;
            }
            case ACT_CITY_ROLES -> {
                if (!city.isGovernor(player.getUniqueId())) {
                    feedback(player, msg("menu.city.owner-only-roles",
                            "Solo el Gobernador puede asignar roles."), NamedTextColor.RED);
                    playError(player);
                    yield false;
                }
                yield true;
            }
            case ACT_CITY_TRANSFER -> {
                if (!city.isGovernor(player.getUniqueId())) {
                    feedback(player, msg("menu.city.owner-only-transfer",
                            "Solo el Gobernador puede transferir la gobernanza."), NamedTextColor.RED);
                    playError(player);
                    yield false;
                }
                yield true;
            }
            default -> false;
        };
    }

    /** Espejo de CityCommand.canManageResidents. */
    private boolean isResidentManager(City city, Player player) {
        return city.isGovernor(player.getUniqueId())
                || city.roleOf(player.getUniqueId()) == CityRole.COUNCIL;
    }

    /** Builds the paginated candidate grid and opens it next tick. */
    private void openPlayerPickLater(Player player, String action, String origin, UUID entityId, int page) {
        var candidates = pickCandidates(player, action, entityId);
        if (candidates.isEmpty()) {
            feedback(player, msg("menu.pick.empty",
                    "No hay jugadores disponibles para esta acción."), NamedTextColor.RED);
            playError(player);
            return;
        }
        var def = dev.dreamcraft.protection.presentation.menu.PlayerPickMenuBuilder
                .buildPlayers(action, origin, entityId, page, candidates);
        menuProviderOpenLater(player, def);
    }

    /**
     * Candidates per pending action (always ONLINE players, viewer excluded):
     * invites list non-members; kick lists members (never the governor);
     * roles/transfer list members. Empty result → the picker is not opened.
     */
    private List<dev.dreamcraft.protection.presentation.menu.PlayerPickMenuBuilder.Candidate>
            pickCandidates(Player viewer, String action, UUID entityId) {
        var out = new ArrayList<dev.dreamcraft.protection.presentation.menu.PlayerPickMenuBuilder.Candidate>();
        switch (action) {
            case ACT_WARD_TRANSFER -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.getUniqueId().equals(viewer.getUniqueId())) out.add(candidateOf(p));
                }
            }
            case ACT_CITY_INVITE -> {
                City city = cityService.findById(entityId).orElse(null);
                if (city == null) return out;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    UUID id = p.getUniqueId();
                    if (id.equals(viewer.getUniqueId()) || city.isMember(id)) continue;
                    out.add(candidateOf(p));
                }
            }
            case ACT_CITY_KICK -> {
                City city = cityService.findById(entityId).orElse(null);
                if (city == null) return out;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    UUID id = p.getUniqueId();
                    if (id.equals(viewer.getUniqueId()) || city.isGovernor(id)) continue;
                    if (!city.isMember(id)) continue;
                    out.add(candidateOf(p));
                }
            }
            case ACT_CITY_ROLES, ACT_CITY_TRANSFER -> {
                City city = cityService.findById(entityId).orElse(null);
                if (city == null) return out;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    UUID id = p.getUniqueId();
                    if (id.equals(viewer.getUniqueId()) || !city.isMember(id)) continue;
                    out.add(candidateOf(p));
                }
            }
            case ACT_ESTATE_INVITE -> {
                Estate estate = estateService.findById(entityId).orElse(null);
                if (estate == null) return out;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    UUID id = p.getUniqueId();
                    if (id.equals(viewer.getUniqueId()) || id.equals(estate.ownerId())
                            || estate.isMember(id)) continue;
                    out.add(candidateOf(p));
                }
            }
            case ACT_ESTATE_TRANSFER -> {
                Estate estate = estateService.findById(entityId).orElse(null);
                if (estate == null) return out;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    UUID id = p.getUniqueId();
                    if (id.equals(viewer.getUniqueId()) || !estate.isMember(id)) continue;
                    out.add(candidateOf(p));
                }
            }
            default -> { /* unknown pending action: empty grid */ }
        }
        out.sort(java.util.Comparator.comparing(c -> c.name().toLowerCase(java.util.Locale.ROOT)));
        return out;
    }

    private static dev.dreamcraft.protection.presentation.menu.PlayerPickMenuBuilder.Candidate
            candidateOf(Player player) {
        return new dev.dreamcraft.protection.presentation.menu.PlayerPickMenuBuilder.Candidate(
                player.getUniqueId(), player.getName());
    }

    /** Reopens the origin menu (fresh view model) next tick; false when unreachable. */
    private boolean reopenOriginMenu(Player player, String origin, UUID entityId) {
        switch (origin) {
            case ORIGIN_CITY -> {
                City city = cityService.findById(entityId).orElse(null);
                var opener = cityMenuOpener;
                if (city == null || opener == null) return false;
                scheduleOpener(player, () -> opener.accept(player, city));
                return true;
            }
            case ORIGIN_ESTATE -> {
                var opener = estateMenuOpener;
                if (opener == null) return false;
                scheduleOpener(player, () -> opener.accept(player, entityId));
                return true;
            }
            case ORIGIN_WARD -> {
                Ward ward = wardService.findById(entityId).orElse(null);
                var opener = wardMenuOpener;
                if (ward == null || opener == null) return false;
                scheduleOpener(player, () -> opener.accept(player, ward));
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** Opens a rebuilt menu next tick (opening inside a click event ghosts items). */
    private void scheduleOpener(Player player, Runnable open) {
        Bukkit.getScheduler().runTask(plugin(), () -> {
            Player online = Bukkit.getPlayer(player.getUniqueId());
            if (online != null) open.run();
        });
    }

    private static String[] splitPayload(String payload) {
        return payload == null || payload.isBlank() ? new String[0] : payload.split(":");
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int parseIntOrDefault(String raw, int def) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private void contextLost(Player player) {
        feedback(player, msg("menu.pick.context-lost",
                "Contexto del selector perdido."), NamedTextColor.RED);
        playError(player);
    }

    // ── Ward/City admin GUIs (stateless payload navigation) ──────────────────

    /** Entries per admin page: 2×2 quarter blocks use rows 0-3 → 2 bands × 4 columns. */
    private static final int ADMIN_PAGE_SIZE = 8;

    /** Top-left slot of the {@code index}-th 2×2 admin entry block (rows 0-3, col pairs 0-2-4-6). */
    private static int adminBlockAnchor(int index) {
        return (index / 4) * 18 + (index % 4) * 2;
    }

    /** Inline permission gate for every wardadmin.* payload action. */
    private boolean requireWardAdmin(Player player) {
        if (player.hasPermission("dreamcraft.ward.admin")) return true;
        feedback(player, msg("common.no-permission", "No tienes permiso."), NamedTextColor.RED);
        playError(player);
        return false;
    }

    /** Inline permission gate for every cityadmin.* payload action. */
    private boolean requireCityAdmin(Player player) {
        if (player.hasPermission("dreamcraft.city.admin")) return true;
        feedback(player, msg("common.no-permission", "No tienes permiso."), NamedTextColor.RED);
        playError(player);
        return false;
    }

    /** Payload «page:filter» → {page, filter}; tolerant to missing parts. */
    private int[] parsePageFilter(String payload) {
        int[] out = {0, 0};
        if (payload == null || payload.isBlank()) return out;
        String[] parts = payload.split(":");
        try {
            if (parts.length > 0) out[0] = Math.max(0, Integer.parseInt(parts[0]));
            if (parts.length > 1) out[1] = "1".equals(parts[1]) ? 1 : 0;
        } catch (NumberFormatException ignored) {}
        return out;
    }

    /** Payload «page» → page (≥ 0). */
    private int parsePage(String payload) {
        return parsePageFilter(payload)[0];
    }

    /** Payload «<uuid>:<page>:<f>» → {wardId, page, filter}; null when malformed. */
    private Object[] parseIdPageFilter(Player player, String payload, String lostKey) {
        if (payload == null || payload.isBlank()) {
            feedback(player, msg(lostKey, "Contexto de admin perdido."), NamedTextColor.RED);
            playError(player);
            return null;
        }
        String[] parts = payload.split(":");
        int[] pf = parsePageFilter((parts.length > 1 ? parts[1] : "") + ":" + (parts.length > 2 ? parts[2] : ""));
        try {
            return new Object[]{UUID.fromString(parts[0]), pf[0], pf[1]};
        } catch (IllegalArgumentException e) {
            feedback(player, msg(lostKey, "Contexto de admin perdido."), NamedTextColor.RED);
            playError(player);
            return null;
        }
    }

    /**
     * Structural health of a Ward — pure in-memory observation: never loads
     * chunks or worlds. MISSING on either side → orphan; CHUNK_UNLOADED /
     * WG_INACTIVE are unknowns, not absence.
     */
    private WardHealth.HealthReport healthOf(Ward ward) {
        org.bukkit.World world = Bukkit.getWorld(ward.worldName());
        WardHealth.CoreState core;
        if (world == null || !world.isChunkLoaded(ward.centerX() >> 4, ward.centerZ() >> 4)) {
            core = WardHealth.CoreState.CHUNK_UNLOADED;
        } else {
            org.bukkit.Material material = wardCoreMaterial;
            org.bukkit.block.Block block = world.getBlockAt(
                    ward.centerX(), ward.centerY(), ward.centerZ());
            core = material == null ? WardHealth.CoreState.CHUNK_UNLOADED
                    : block.getType() == material ? WardHealth.CoreState.PRESENT
                    : WardHealth.CoreState.MISSING;
        }
        WardHealth.RegionState region = !worldGuardAdapter.isAvailable()
                ? WardHealth.RegionState.WG_INACTIVE
                : worldGuardAdapter.regionExists(ward)
                ? WardHealth.RegionState.PRESENT
                : WardHealth.RegionState.MISSING;
        return WardHealth.classify(core, region);
    }

    /** True when the ward is not confirmed-healthy (orphan OR unknown states). */
    private boolean isSuspect(WardHealth.HealthReport report) {
        return report.orphan()
                || report.coreState() == WardHealth.CoreState.CHUNK_UNLOADED
                || report.regionState() == WardHealth.RegionState.WG_INACTIVE;
    }

    /**
     * Protection-state rule shared with the sync menu: true when the ward's
     * upkeep no longer covers protection (GRACE/EXPIRED or no calculator).
     * Wired from the boot sequence via the Ward menu façade's view-model builder.
     */
    private java.util.function.Predicate<Ward> unprotectedWards = ward -> false;

    /** Wires the protection-state rule (see {@link #unprotectedWards}). */
    public void setUnprotectedWards(java.util.function.Predicate<Ward> predicate) {
        this.unprotectedWards = predicate == null ? ward -> false : predicate;
    }

    /** Admin icon for a ward row: inactive quarters when orphaned OR unprotected. */
    private String wardAdminIcon(Ward ward, WardHealth.HealthReport health) {
        boolean inactive = health.orphan() || unprotectedWards.test(ward);
        return inactive ? "icon.ward.inactive" : "icon.ward.active";
    }

    /**
     * Admin overview of EVERY registered Ward — orphaned nuclei first, then
     * alphabetical. Stateless pagination: page/filter travel inside payloads.
     * Layout decision: 45 ítems (slots 0-44), «Anterior» en 45, toggle
     * «solo sospechosos» en 49, «Siguiente» en 53 cuando hay más páginas y
     * «Cerrar» ocupa el slot restante (53 sin paginación, 51 con ella).
     */
    public void openWardAdminOverview(Player player, int page, boolean suspectsOnly) {
        record Row(Ward ward, WardHealth.HealthReport health) {}
        List<Row> rows = new ArrayList<>();
        for (Ward ward : wardService.findAll()) {
            var health = healthOf(ward);
            if (suspectsOnly && !isSuspect(health)) continue;
            rows.add(new Row(ward, health));
        }
        rows.sort(java.util.Comparator
                .comparing((Row r) -> r.health().orphan()).reversed()
                .thenComparing(r -> r.ward().name().toLowerCase(java.util.Locale.ROOT)));

        int pages = Math.max(1, (rows.size() + ADMIN_PAGE_SIZE - 1) / ADMIN_PAGE_SIZE);
        int current = Math.min(Math.max(0, page), pages - 1);
        int from = current * ADMIN_PAGE_SIZE;
        int to = Math.min(from + ADMIN_PAGE_SIZE, rows.size());

        List<MenuItem> items = new ArrayList<>();
        for (int i = from; i < to; i++) {
            Row row = rows.get(i);
            Ward ward = row.ward();
            boolean orphan = row.health().orphan();
            List<String> lore = wardAdminLore(ward, row.health());
            // Orphan/unprotected/suspect entries reuse the inactive quarter art
            // (gray crossed shield); the pack has no dedicated orphan tiles.
            String icon = wardAdminIcon(ward, row.health());
            String name = (orphan ? "&c⚠ &f" : "&b&l") + ward.name();
            items.addAll(MenuItem.block2x2Button(54, adminBlockAnchor(i - from), icon, name, lore,
                    MenuAction.of("wardadmin.detail", ward.id() + ":" + current + ":" + (suspectsOnly ? 1 : 0))));
        }
        String f = suspectsOnly ? "1" : "0";
        if (current > 0) {
            items.add(MenuItem.button(45, "menu.back", "&e« Anterior",
                    List.of("&7Página " + current),
                    MenuAction.of("wardadmin.page", (current - 1) + ":" + f)));
        }
        items.add(MenuItem.button(49, suspectsOnly ? "icon.toggle.on" : "icon.toggle.off",
                suspectsOnly ? "&c⚠ Solo sospechosos: &aON" : "&⚠ Solo sospechosos: &7OFF",
                List.of("&7Alterna entre todos los Núcleos y",
                        "&csolo huérfanos / estado desconocido"),
                MenuAction.of("wardadmin.page", current + ":" + (suspectsOnly ? "0" : "1"))));
        boolean hasNext = to < rows.size();
        if (hasNext) {
            items.add(MenuItem.button(53, "menu.back", "&eSiguiente »",
                    List.of("&7Página " + (current + 2) + " de " + pages),
                    MenuAction.of("wardadmin.page", (current + 1) + ":" + f)));
            items.add(MenuItem.button(51, "menu.close", "&c&lCerrar",
                    List.of("&7Cerrar menú"), MenuAction.of("menu.close")));
        } else {
            items.add(MenuItem.button(53, "menu.close", "&c&lCerrar",
                    List.of("&7Cerrar menú"), MenuAction.of("menu.close")));
        }

        String title = CommandMessages.tr("menu.title.ward-admin", "&8{name.ward} · Panel admin")
                + " §8(" + (current + 1) + "/" + pages + ")"
                + (suspectsOnly ? " §8· ⚠" : "");
        var def = new MenuDefinition("ward_admin_overview", title, 54, items);
        menuProviderOpenLater(player, def);
    }

    /** Lore shared by the overview button and the detail header of a ward. */
    private List<String> wardAdminLore(Ward ward, WardHealth.HealthReport health) {
        List<String> lore = new ArrayList<>();
        lore.add("&7Owner: &f" + CommandMessages.resolveName(ward.ownerId()));
        lore.add("&7Fase: &b" + ward.tier() + " &7· Radio: &f" + ward.radius()
                + " &7· Score: &f" + ward.baseScore());
        lore.add("&7Mundo: &f" + ward.worldName() + " &7@ &f"
                + ward.centerX() + ", " + ward.centerY() + ", " + ward.centerZ());
        lore.add("&7Upkeep: &f" + ward.upkeepBalance() + " u");
        if (ward.hasCityMembership()) {
            lore.add("&7Ciudad: &f" + cityService.findById(ward.cityId())
                    .map(City::name).orElse("?"));
        } else {
            lore.add("&7Ciudad: &7ninguna");
        }
        switch (health.regionState()) {
            case PRESENT -> lore.add("&7Región WG: &apresente");
            case MISSING -> lore.add("&cRegión WG: &4ausente");
            case WG_INACTIVE -> lore.add("&eRegión WG: &7inactiva");
        }
        switch (health.coreState()) {
            case PRESENT -> lore.add("&7Bloque físico: &apresente");
            case MISSING -> lore.add("&cBloque físico: &4ausente");
            case CHUNK_UNLOADED -> lore.add("&eBloque físico: &7chunk sin cargar");
        }
        if (health.orphan()) lore.add(msg("menu.wardadmin.orphan-warning",
                "&c⚠ HUÉRFANO: revisar o disolver"));
        return lore;
    }

    /**
     * Detail of one ward: open its normal menu, teleport to the core or force
     * a dissolution (clear warning in lore; closes the GUI afterwards).
     */
    private void openWardAdminDetail(Player player, String payload) {
        Object[] parsed = parseIdPageFilter(player, payload, "menu.ward.context-lost");
        if (parsed == null) return;
        Ward ward = wardService.findById((UUID) parsed[0]).orElse(null);
        if (ward == null) {
            feedback(player, msg("menu.ward.context-lost", "Contexto de Ward perdido."), NamedTextColor.RED);
            playError(player);
            return;
        }
        int page = (Integer) parsed[1];
        int filter = (Integer) parsed[2];
        var health = healthOf(ward);

        List<MenuItem> items = new ArrayList<>();
        List<String> headerLore = wardAdminLore(ward, health);
        if (ward.belowTierBlocks() > 0) {
            headerLore.add(dev.dreamcraft.protection.message.Messages.apply(
                    msg("menu.wardadmin.surcharge", "&6Sobrecosto: &f{blocks} bloque(s) bajo fase"),
                    "blocks", ward.belowTierBlocks()));
        }
        headerLore.add("");
        headerLore.add("&7ID: &8" + ward.id());
        items.add(MenuItem.display(4, wardAdminIcon(ward, health),
                (health.orphan() ? "&c⚠ " : "&b&l") + ward.name(), headerLore));

        // 2×2 quarter blocks: open · TP · dissolve; back 45 · close 53.
        items.addAll(MenuItem.block2x2Button(54, 10, "icon.ward.active", "&a&lAbrir menú del Núcleo",
                List.of("&7Abre el panel normal del Núcleo",
                        "&7(inspección completa con acciones de owner)."),
                MenuAction.of("wardadmin.openmenu", ward.id().toString())));

        items.addAll(MenuItem.block2x2Button(54, 15, "icon.estate.zone-tp", "&e&lTP al centro",
                List.of("&7Teletransporta al núcleo:",
                        "&f" + ward.worldName() + " @ " + ward.centerX()
                                + ", " + ward.centerY() + ", " + ward.centerZ()),
                MenuAction.of("wardadmin.tp", ward.id().toString())));

        items.addAll(MenuItem.block2x2Button(54, 28, "icon.ward.inactive", "&4&lDISOLVER NÚCLEO",
                List.of("&cElimina la región WG, el registro y",
                        "&cel bloque físico del Núcleo.",
                        "&cEl owner NO recibe el núcleo de vuelta.",
                        "",
                        "&4⚠ Acción irreversible — clic para ejecutar"),
                MenuAction.of("wardadmin.dissolve", ward.id().toString())));

        items.add(MenuItem.button(45, "menu.back", "&e« Volver",
                List.of("&7Vuelve a la lista (página " + (page + 1) + ")"),
                MenuAction.of("wardadmin.page", page + ":" + filter)));

        items.add(MenuItem.button(53, "menu.close", "&c&lCerrar",
                List.of("&7Cerrar menú"), MenuAction.of("menu.close")));

        var def = new MenuDefinition("ward_admin_detail",
                CommandMessages.tr("menu.title.ward-admin-detail", "&8Admin · Núcleo")
                        .replace("{name}", ward.name()), 54, items);
        menuProviderOpenLater(player, def);
    }

    /** estateadmin.tp mirrored: scheduled close + teleport to the ward center. */
    private void handleWardAdminTp(Player player, MenuAction action) {
        UUID wardId;
        try {
            wardId = UUID.fromString(action.payload());
        } catch (IllegalArgumentException e) {
            feedback(player, msg("menu.ward.context-lost", "Contexto de Ward perdido."), NamedTextColor.RED);
            playError(player);
            return;
        }
        Ward ward = wardService.findById(wardId).orElse(null);
        if (ward == null) {
            feedback(player, msg("menu.ward.context-lost", "Contexto de Ward perdido."), NamedTextColor.RED);
            playError(player);
            return;
        }
        org.bukkit.World world = Bukkit.getWorld(ward.worldName());
        if (world == null) {
            feedback(player, dev.dreamcraft.protection.message.Messages.apply(
                    msg("menu.estate.world-not-loaded", "El mundo {world} no está cargado."),
                    "world", ward.worldName()), NamedTextColor.RED);
            playError(player);
            return;
        }
        org.bukkit.Location target = new org.bukkit.Location(world,
                ward.centerX() + 0.5, ward.centerY() + 1.0, ward.centerZ() + 0.5);
        // Explicit TP request by an admin — loading this chunk is intentional.
        world.getChunkAt(target).load();
        Bukkit.getScheduler().runTask(plugin(), () -> {
            Player online = Bukkit.getPlayer(player.getUniqueId());
            if (online == null) return;
            online.closeInventory();
            online.teleport(target);
            feedback(online, dev.dreamcraft.protection.message.Messages.apply(
                    msg("menu.wardadmin.tp-done", "✦ Teletransporte al centro de {name}."),
                    "name", ward.name()), NamedTextColor.AQUA);
            playSuccess(online);
        });
    }

    /** Forced dissolution from the GUI: system path (no founder refund). */
    private void handleWardAdminDissolve(Player player, MenuAction action) {
        UUID wardId;
        try {
            wardId = UUID.fromString(action.payload());
        } catch (IllegalArgumentException e) {
            feedback(player, msg("menu.ward.context-lost", "Contexto de Ward perdido."), NamedTextColor.RED);
            playError(player);
            return;
        }
        Ward ward = wardService.findById(wardId).orElse(null);
        if (ward == null) {
            feedback(player, msg("menu.ward.context-lost", "Contexto de Ward perdido."), NamedTextColor.RED);
            playError(player);
            return;
        }
        var result = wardDissolutionService != null
                ? wardDissolutionService.dissolve(ward, player, false)
                : new dev.dreamcraft.protection.service.WardDissolutionService.Result(
                        legacyDissolve(ward), false);
        player.closeInventory();
        feedback(player, dev.dreamcraft.protection.message.Messages.apply(
                msg("menu.wardadmin.dissolved", "Núcleo {name} disuelto."),
                "name", ward.name())
                + (result.coreBlockRemoved() ? ""
                        : msg("menu.wardadmin.no-core-block", " (sin bloque físico)")),
                NamedTextColor.GREEN);
        playSuccess(player);
    }

    /** Opens the ward's normal menu through the wired opener (next tick). */
    private void handleWardAdminOpenMenu(Player player, MenuAction action) {
        Ward found;
        try {
            found = wardService.findById(UUID.fromString(action.payload())).orElse(null);
        } catch (IllegalArgumentException e) {
            found = null;
        }
        if (found == null) {
            feedback(player, msg("menu.ward.context-lost", "Contexto de Ward perdido."), NamedTextColor.RED);
            playError(player);
            return;
        }
        final Ward ward = found;
        var opener = wardMenuOpener;
        if (opener == null) {
            feedback(player, msg("menu.wardadmin.menu-unavailable",
                    "El menú del Núcleo no está disponible."), NamedTextColor.RED);
            playError(player);
            return;
        }
        Bukkit.getScheduler().runTask(plugin(), () -> {
            Player online = Bukkit.getPlayer(player.getUniqueId());
            if (online != null) opener.accept(online, ward);
        });
    }

    /**
     * Admin overview of EVERY city — alphabetical, same stateless pagination
     * as the ward GUI (sin toggle de filtro: las ciudades no tienen salud).
     */
    public void openCityAdminOverview(Player player, int page) {
        List<City> cities = cityService.findAll().stream()
                .sorted(java.util.Comparator.comparing(c -> c.name().toLowerCase(java.util.Locale.ROOT)))
                .toList();
        int pages = Math.max(1, (cities.size() + ADMIN_PAGE_SIZE - 1) / ADMIN_PAGE_SIZE);
        int current = Math.min(Math.max(0, page), pages - 1);
        int from = current * ADMIN_PAGE_SIZE;
        int to = Math.min(from + ADMIN_PAGE_SIZE, cities.size());

        List<MenuItem> items = new ArrayList<>();
        for (int i = from; i < to; i++) {
            City city = cities.get(i);
            List<String> lore = cityAdminLore(city);
            items.addAll(MenuItem.block2x2Button(54, adminBlockAnchor(i - from), "icon.city.overview",
                    "&6&l" + city.name(), lore,
                    MenuAction.of("cityadmin.detail", city.id() + ":" + current)));
        }
        if (current > 0) {
            items.add(MenuItem.button(45, "menu.back", "&e« Anterior",
                    List.of("&7Página " + current),
                    MenuAction.of("cityadmin.page", String.valueOf(current - 1))));
        }
        boolean hasNext = to < cities.size();
        if (hasNext) {
            items.add(MenuItem.button(53, "menu.back", "&eSiguiente »",
                    List.of("&7Página " + (current + 2) + " de " + pages),
                    MenuAction.of("cityadmin.page", String.valueOf(current + 1))));
            items.add(MenuItem.button(51, "menu.close", "&c&lCerrar",
                    List.of("&7Cerrar menú"), MenuAction.of("menu.close")));
        } else {
            items.add(MenuItem.button(53, "menu.close", "&c&lCerrar",
                    List.of("&7Cerrar menú"), MenuAction.of("menu.close")));
        }

        String title = CommandMessages.tr("menu.title.city-admin", "&8{name.city} · Panel admin")
                + " §8(" + (current + 1) + "/" + pages + ")";
        var def = new MenuDefinition("city_admin_overview", title, 54, items);
        menuProviderOpenLater(player, def);
    }

    /** Lore shared by the city overview button and the detail header. */
    private List<String> cityAdminLore(City city) {
        List<String> lore = new ArrayList<>();
        lore.add("&7Gobernador: &f" + CommandMessages.resolveName(city.governorId()));
        lore.add("&7Miembros: &f" + city.members().size());
        if (treasuryStore != null) {
            lore.add("&7Tesoro: &f" + treasuryStore.computeValue(treasuryStore.get(city.id())) + " u");
        } else {
            lore.add("&7Tesoro: &f" + city.treasury() + " u");
        }
        lore.add("&7Núcleos anexados: &f" + wardService.findByCity(city.id()).size());
        if (cityLevelService != null) {
            var lvl = cityLevelService.statusOf(city);
            lore.add("&7Nivel: &b" + lvl.levelName()
                    + (lvl.maxed() ? " &8(máximo)" : " &8→ " + lvl.nextLevelName()));
        }
        return lore;
    }

    /** Detail of one city: open its menu or force-delete it (sin TP). */
    private void openCityAdminDetail(Player player, String payload) {
        Object[] parsed = parseIdPageFilter(player, payload, "menu.city.context-lost");
        if (parsed == null) return;
        City city = cityService.findById((UUID) parsed[0]).orElse(null);
        if (city == null) {
            feedback(player, msg("menu.city.context-lost", "Contexto de Ciudad perdido."), NamedTextColor.RED);
            playError(player);
            return;
        }
        int page = (Integer) parsed[1];

        List<MenuItem> items = new ArrayList<>();
        List<String> headerLore = cityAdminLore(city);
        headerLore.add("");
        headerLore.add("&7ID: &8" + city.id());
        items.add(MenuItem.display(4, "icon.city.admin", "&6&l" + city.name(), headerLore));

        // 2×2 quarter blocks: open city menu · force delete; back 45 · close 53.
        items.addAll(MenuItem.block2x2Button(54, 10, "icon.city.overview", "&a&lAbrir menú de ciudad",
                List.of("&7Abre el panel normal de la Matriz."),
                MenuAction.of("cityadmin.openmenu", city.id().toString())));

        items.addAll(MenuItem.block2x2Button(54, 15, "icon.ward.inactive", "&4&lELIMINAR CIUDAD",
                List.of("&cDesanexa TODOS sus Núcleos (pierden el",
                        "&cacceso de los residentes en sus regiones),",
                        "&celimina el registro de la Matriz.",
                        "",
                        "&4⚠ Acción irreversible — clic para ejecutar"),
                MenuAction.of("cityadmin.delete", city.id().toString())));

        items.add(MenuItem.button(45, "menu.back", "&e« Volver",
                List.of("&7Vuelve a la lista (página " + (page + 1) + ")"),
                MenuAction.of("cityadmin.page", String.valueOf(page))));

        items.add(MenuItem.button(53, "menu.close", "&c&lCerrar",
                List.of("&7Cerrar menú"), MenuAction.of("menu.close")));

        var def = new MenuDefinition("city_admin_detail",
                CommandMessages.tr("menu.title.city-admin-detail", "&8Admin · Ciudad")
                        .replace("{name}", city.name()), 54, items);
        menuProviderOpenLater(player, def);
    }

    /** Opens the city's normal menu through the wired opener (next tick). */
    private void handleCityAdminOpenMenu(Player player, MenuAction action) {
        City found;
        try {
            found = cityService.findById(UUID.fromString(action.payload())).orElse(null);
        } catch (IllegalArgumentException e) {
            found = null;
        }
        if (found == null) {
            feedback(player, msg("menu.city.context-lost", "Contexto de Ciudad perdido."), NamedTextColor.RED);
            playError(player);
            return;
        }
        final City city = found;
        var opener = cityMenuOpener;
        if (opener == null) {
            feedback(player, msg("menu.cityadmin.menu-unavailable",
                    "El menú de la Matriz no está disponible."), NamedTextColor.RED);
            playError(player);
            return;
        }
        Bukkit.getScheduler().runTask(plugin(), () -> {
            Player online = Bukkit.getPlayer(player.getUniqueId());
            if (online != null) opener.accept(online, city);
        });
    }

    /** Admin variant of the governor delete flow: disannex + project + delete. */
    private void handleCityAdminDelete(Player player, MenuAction action) {
        City city;
        try {
            city = cityService.findById(UUID.fromString(action.payload())).orElse(null);
        } catch (IllegalArgumentException e) {
            city = null;
        }
        if (city == null) {
            feedback(player, msg("menu.city.context-lost", "Contexto de Ciudad perdido."), NamedTextColor.RED);
            playError(player);
            return;
        }
        deleteCityAsAdmin(player, city);
    }

    /** Shared teardown: disassociate wards, collapse region members, delete. */
    private void deleteCityAsAdmin(Player player, City city) {
        for (Ward ward : wardService.findByCity(city.id())) {
            wardService.setCityMembership(ward, null);
            dev.dreamcraft.protection.service.WardAccessSync.project(ward, cityService, worldGuardAdapter);
        }
        cityService.delete(city);
        player.closeInventory();
        feedback(player, dev.dreamcraft.protection.message.Messages.apply(
                msg("menu.city.deleted", "Ciudad {city} eliminada."),
                "city", city.name()), NamedTextColor.GREEN);
        playSuccess(player);
    }

    /** Opens a built admin GUI next tick (opening inside a click event causes ghost items). */
    private void menuProviderOpenLater(Player player, MenuDefinition def) {
        Bukkit.getScheduler().runTask(plugin(), () -> {
            Player online = Bukkit.getPlayer(player.getUniqueId());
            if (online == null || adminMenuProvider == null) return;
            MenuContext ctx = new MenuContext(online.getUniqueId(), online.getName(), java.util.Map.of());
            adminMenuProvider.open(def, ctx);
        });
    }

    /** Opens a built sub-menu next tick carrying entity context (wardId/cityId). */
    private void openMenuLater(Player player, MenuDefinition def, java.util.Map<String, Object> data) {
        Bukkit.getScheduler().runTask(plugin(), () -> {
            Player online = Bukkit.getPlayer(player.getUniqueId());
            if (online == null || adminMenuProvider == null) return;
            MenuContext ctx = new MenuContext(online.getUniqueId(), online.getName(), data);
            adminMenuProvider.open(def, ctx);
        });
    }

    // ── Governance sub-menus (políticas / permisos) ───────────────────────────

    /** Opens the Matriz policies sub-menu (each policy togglable individually). */
    private void openCityPolicies(Player player, MenuContext ctx) {
        City city = resolveCity(player, ctx);
        if (city == null) return;
        openCityPoliciesFor(player, city);
    }

    /** Re-opens the policies sub-menu after a toggle. */
    private void reopenCityPolicies(Player player, UUID cityId) {
        City city = cityService.findById(cityId).orElse(null);
        if (city == null) return;
        openCityPoliciesFor(player, city);
    }

    private void openCityPoliciesFor(Player player, City city) {
        var def = dev.dreamcraft.protection.presentation.menu.PolicyMenuBuilder
                .buildCityPolicies(city, player.getUniqueId());
        openMenuLater(player, def, java.util.Map.of("cityId", city.id()));
    }

    /** «Volver»: returns to the Matriz overview menu. */
    private void handleCityPoliciesBack(Player player, MenuAction action) {
        String[] parts = splitPayload(action.payload());
        UUID cityId = parts.length > 0 ? parseUuid(parts[0]) : null;
        if (cityId != null) reopenOriginMenu(player, ORIGIN_CITY, cityId);
    }

    /** Opens the Núcleo permissions sub-menu (each perm togglable individually). */
    private void openWardPermissions(Player player, MenuContext ctx) {
        Ward ward = resolveWard(player, ctx);
        if (ward == null) return;
        openWardPermissionsFor(player, ward);
    }

    /** Re-opens the permissions sub-menu after a toggle. */
    private void reopenWardPermissions(Player player, UUID wardId) {
        Ward ward = wardService.findById(wardId).orElse(null);
        if (ward == null) return;
        openWardPermissionsFor(player, ward);
    }

    private void openWardPermissionsFor(Player player, Ward ward) {
        var def = dev.dreamcraft.protection.presentation.menu.PolicyMenuBuilder
                .buildWardPermissions(ward, player.getUniqueId());
        openMenuLater(player, def, java.util.Map.of("wardId", ward.id()));
    }

    /** «Volver»: returns to the Núcleo/sync menu. */
    private void handleWardPermissionsBack(Player player, MenuAction action) {
        String[] parts = splitPayload(action.payload());
        UUID wardId = parts.length > 0 ? parseUuid(parts[0]) : null;
        if (wardId != null) reopenOriginMenu(player, ORIGIN_WARD, wardId);
    }



    /**
     * Admin-only: teleports the viewer to the anchored area of the adventure
     * zone carried in the action payload (from the admin zones GUI).
     */
    private void handleAdminZoneTp(Player player, MenuAction action) {
        if (!player.hasPermission("dreamcraft.protection.admin")) {
            feedback(player, msg("common.no-permission", "No tienes permiso."), NamedTextColor.RED);
            playError(player);
            return;
        }
        UUID zoneId;
        try {
            zoneId = UUID.fromString(action.payload());
        } catch (IllegalArgumentException e) {
            feedback(player, msg("menu.estate.context-lost", "Contexto de Estate perdido."), NamedTextColor.RED);
            playError(player);
            return;
        }
        Estate estate = estateService.findById(zoneId).orElse(null);
        if (estate == null || !estate.hasArea()) {
            feedback(player, msg("menu.estate.context-lost", "Contexto de instancia perdido."), NamedTextColor.RED);
            playError(player);
            return;
        }
        org.bukkit.World world = Bukkit.getWorld(estate.areaWorld());
        if (world == null) {
            feedback(player, dev.dreamcraft.protection.message.Messages.apply(
                    msg("menu.estate.world-not-loaded", "El mundo {world} no está cargado."),
                    "world", estate.areaWorld()), NamedTextColor.RED);
            playError(player);
            return;
        }
        Bukkit.getScheduler().runTask(plugin(), () -> {
            Player online = Bukkit.getPlayer(player.getUniqueId());
            if (online == null) return;
            online.closeInventory();
            online.teleport(new org.bukkit.Location(world,
                    estate.areaX() + 0.5, estate.areaY() + 1.0, estate.areaZ() + 0.5));
            playSuccess(online);
        });
    }

    /**
     * Opens the estate/group menu of the zone carried in the action payload
     * (book button of the admin zones GUI): join the group or manage it.
     */
    private void handleAdminZoneMenu(Player player, MenuAction action) {
        UUID zoneId;
        try {
            zoneId = UUID.fromString(action.payload());
        } catch (IllegalArgumentException e) {
            feedback(player, msg("menu.estate.context-lost", "Contexto de instancia perdido."), NamedTextColor.RED);
            playError(player);
            return;
        }
        Estate estate = estateService.findById(zoneId).orElse(null);
        if (estate == null) {
            feedback(player, msg("menu.estate.context-lost", "Contexto de instancia perdido."), NamedTextColor.RED);
            playError(player);
            return;
        }
        var opener = estateMenuOpener;
        if (opener == null) {
            feedback(player, msg("menu.estate.menu-unavailable", "El menú no está disponible."), NamedTextColor.RED);
            playError(player);
            return;
        }
        Bukkit.getScheduler().runTask(plugin(), () -> {
            Player online = Bukkit.getPlayer(player.getUniqueId());
            if (online != null) opener.accept(online, estate.id());
        });
    }

    /** Display name of a Ward's owner for messages (online, offline or fallback). */
    private String ownerName(Ward ward) {
        String name = Bukkit.getOfflinePlayer(ward.ownerId()).getName();
        return name != null ? name : "desconocido";
    }

    private void handleClose(Player player) {
        player.closeInventory();
    }

    /** Placeholder until the player profile screen ships: brand feedback only. */
    private void handlePerfil(Player player) {
        player.playSound(player.getLocation(),
                resolveSound("menu.success", Sound.ENTITY_EXPERIENCE_ORB_PICKUP), 0.7f, 1.4f);
        player.sendMessage(Component.text("[DreamCraft] ", NamedTextColor.DARK_PURPLE)
                .append(Component.text(
                        msg("menu.perfil.soon", "Tu perfil estará disponible pronto."),
                        NamedTextColor.GRAY)));
    }

    private Ward resolveWard(Player player, MenuContext ctx) {
        UUID wardId = ctx.get("wardId");
        if (wardId == null) {
            feedback(player, msg("menu.ward.context-lost", "Contexto de Ward perdido."), NamedTextColor.RED);
            return null;
        }
        return wardService.findById(wardId).orElse(null);
    }

    private City resolveCity(Player player, MenuContext ctx) {
        UUID cityId = ctx.get("cityId");
        if (cityId == null) {
            feedback(player, msg("menu.city.context-lost", "Contexto de Ciudad perdido."), NamedTextColor.RED);
            return null;
        }
        return cityService.findById(cityId).orElse(null);
    }

    private Estate resolveEstate(Player player, MenuContext ctx) {
        UUID estateId = ctx.get("estateId");
        if (estateId == null) {
            feedback(player, msg("menu.estate.context-lost", "Contexto de instancia perdido."), NamedTextColor.RED);
            return null;
        }
        return estateService.findById(estateId).orElse(null);
    }

    private void feedback(Player player, String message, NamedTextColor color) {
        player.sendActionBar(Component.text(message, color));
    }

    private void playSuccess(Player player) {
        player.playSound(player.getLocation(),
                resolveSound("menu.click", Sound.UI_BUTTON_CLICK), 0.7f, 1.2f);
    }

    private void playError(Player player) {
        player.playSound(player.getLocation(),
                resolveSound("menu.error", Sound.BLOCK_NOTE_BLOCK_BASS), 0.7f, 0.8f);
    }

    /** Resolves a sound asset key via presentation-assets.yml; falls back to vanilla. */
    private Sound resolveSound(String assetKey, Sound fallback) {
        var registry = presentationAssets;
        String raw = registry == null ? null : registry.sound(assetKey);
        if (raw == null || raw.isBlank()) return fallback;
        String normalized = raw.toLowerCase(java.util.Locale.ROOT).replace('_', '.');
        if (!normalized.contains(".")) normalized = "minecraft." + normalized;
        org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.fromString(normalized);
        if (key != null) {
            Sound viaRegistry = org.bukkit.Registry.SOUNDS.get(key);
            if (viaRegistry != null) return viaRegistry;
        }
        return fallback;
    }
}
