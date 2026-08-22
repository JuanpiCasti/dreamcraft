package dev.dreamcraft.protection.command;

import dev.dreamcraft.protection.domain.model.OwnerType;
import dev.dreamcraft.protection.domain.model.Ward;
import dev.dreamcraft.protection.domain.model.WardPermission;
import dev.dreamcraft.protection.domain.service.CityService;
import dev.dreamcraft.protection.domain.service.WardService;
import dev.dreamcraft.protection.integration.worldguard.WorldGuardAdapter;
import dev.dreamcraft.protection.service.WardUpkeepService;
import dev.dreamcraft.protection.service.WardUpgradeService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static dev.dreamcraft.protection.command.CommandMessages.*;

/**
 * Handles all /protection (aliases: /prot, /claim) subcommands.
 *
 * <p><b>Unified mechanic:</b> since the Ward unification, this command is a
 * full delegate of the Ward system — same domain services, same item-based
 * upkeep and the exact same menu as {@code /ward menu} (via {@link WardMenuFacade}).
 * Both commands coexist for player convenience; there is no separate claim system.
 *
 * <p>Player subcommands (perm: dreamcraft.protection.use):
 * <ul>
 *   <li>/protection claim | menu — open the Ward menu (VIPs/governors/admins)</li>
 *   <li>/protection status       — text status of the current Ward</li>
 *   <li>/protection rename &lt;nombre&gt; — rename the current Ward</li>
 *   <li>/protection upkeep [deposit &lt;material&gt; &lt;n&gt;] — upkeep details/deposit</li>
 *   <li>/protection permissions [perm grant|revoke] — public permission flags</li>
 *   <li>/protection upgrade      — upgrade the Ward tier (item cost)</li>
 *   <li>/protection transfer &lt;jugador&gt; — transfer ownership</li>
 *   <li>/protection members      — membership info (wards use City membership)</li>
 *   <li>/protection dissolve | abandon — disband the current Ward</li>
 * </ul>
 *
 * <p>Admin subcommands (perm: dreamcraft.protection.admin):
 * <ul>
 *   <li>/protection give        — receive a Ward Beacon item</li>
 *   <li>/protection reload      — reload configuration</li>
 *   <li>/protection recalculate — re-sync the WorldGuard region size</li>
 * </ul>
 */
public final class ProtectionCommand implements CommandExecutor, TabCompleter {

    private static final String USE_PERM   = "dreamcraft.protection.use";
    private static final String ADMIN_PERM = "dreamcraft.protection.admin";
    /** VIP menu permission (governors also pass). */
    private static final String MENU_PERM  = "dreamcraft.protection.menu";

    private static final Component WARD_PREFIX = Component.text("[Protección] ", NamedTextColor.DARK_AQUA);

    private final WardService wardService;
    private final CityService cityService;
    private final WorldGuardAdapter worldGuardAdapter;
    private final dev.dreamcraft.protection.ui.WardItems wardItems;
    private final WardUpgradeService upgradeService;
    private final WardUpkeepService upkeepService;
    private final WardMenuFacade menuFacade;
    private final Runnable reloadAction;
    /** Per-server subcommand aliases/enabled flags. */
    private final dev.dreamcraft.protection.config.CommandOptions options;
    /** Single source of truth for dispatch + tab completion. */
    private final CommandRegistry registry;
    /** Optional: integration + presentation status reporting (/protection integrations). */
    private dev.dreamcraft.protection.integration.registry.CapabilityRegistry capabilityRegistry;
    private dev.dreamcraft.protection.presentation.resourcepack.PresentationAssetRegistry assetRegistry;
    private dev.dreamcraft.protection.config.PresentationOptions.Mode assetMode =
            dev.dreamcraft.protection.config.PresentationOptions.Mode.AUTO;

    public ProtectionCommand(WardService wardService,
                             CityService cityService,
                             WorldGuardAdapter worldGuardAdapter,
                             dev.dreamcraft.protection.ui.WardItems wardItems,
                             WardUpgradeService upgradeService,
                             WardUpkeepService upkeepService,
                             WardMenuFacade menuFacade,
                             Runnable reloadAction) {
        this(wardService, cityService, worldGuardAdapter, wardItems, upgradeService, upkeepService,
                menuFacade, reloadAction, dev.dreamcraft.protection.config.CommandOptions.empty());
    }

    public ProtectionCommand(WardService wardService,
                             CityService cityService,
                             WorldGuardAdapter worldGuardAdapter,
                             dev.dreamcraft.protection.ui.WardItems wardItems,
                             WardUpgradeService upgradeService,
                             WardUpkeepService upkeepService,
                             WardMenuFacade menuFacade,
                             Runnable reloadAction,
                             dev.dreamcraft.protection.config.CommandOptions options) {
        this.wardService = wardService;
        this.cityService = cityService;
        this.worldGuardAdapter = worldGuardAdapter;
        this.wardItems = wardItems;
        this.upgradeService = upgradeService;
        this.upkeepService = upkeepService;
        this.menuFacade = menuFacade;
        this.reloadAction = reloadAction;
        this.options = options;
        this.registry = buildRegistry();
    }

    /** Installs the status sources for /protection integrations. */
    public void setStatusSources(dev.dreamcraft.protection.integration.registry.CapabilityRegistry capabilities,
                                 dev.dreamcraft.protection.presentation.resourcepack.PresentationAssetRegistry assets,
                                 dev.dreamcraft.protection.config.PresentationOptions.Mode mode) {
        this.capabilityRegistry = capabilities;
        this.assetRegistry = assets;
        this.assetMode = mode;
    }

    /**
     * Builds the subcommand table: canonical names here, aliases merged from
     * config.yml (commands.protection.subcommands.&lt;name&gt;.aliases). Several
     * canonical tokens may share one handler (e.g. claim/menu).
     */
    private CommandRegistry buildRegistry() {
        return new CommandRegistry("protection")
                .register(SubcommandSpec.of("claim", (p, a) -> handleClaim(p))
                        .withAliases(options.aliases("protection", "claim")))
                .register(SubcommandSpec.of("menu", (p, a) -> handleClaim(p))
                        .withAliases(options.aliases("protection", "menu")))
                .register(SubcommandSpec.of("status", (p, a) -> handleStatus(p))
                        .withAliases(options.aliases("protection", "status")))
                .register(SubcommandSpec.of("info", (p, a) -> handleStatus(p))
                        .withAliases(options.aliases("protection", "info")))
                .register(SubcommandSpec.of("rename", this::handleRename)
                        .withAliases(options.aliases("protection", "rename")))
                .register(SubcommandSpec.of("upkeep", this::handleUpkeep)
                        .withAliases(options.aliases("protection", "upkeep")))
                .register(SubcommandSpec.of("permissions", this::handlePermissions)
                        .withAliases(options.aliases("protection", "permissions")))
                .register(SubcommandSpec.of("upgrade", (p, a) -> handleUpgrade(p))
                        .withAliases(options.aliases("protection", "upgrade")))
                .register(SubcommandSpec.of("transfer", this::handleTransfer)
                        .withAliases(options.aliases("protection", "transfer")))
                .register(SubcommandSpec.of("members", (p, a) -> handleMembers(p))
                        .withAliases(options.aliases("protection", "members")))
                .register(SubcommandSpec.of("abandon", (p, a) -> handleAbandon(p))
                        .withAliases(options.aliases("protection", "abandon")))
                .register(SubcommandSpec.of("dissolve", (p, a) -> handleAbandon(p))
                        .withAliases(options.aliases("protection", "dissolve")))
                .register(SubcommandSpec.of("delete", (p, a) -> handleAbandon(p))
                        .withAliases(options.aliases("protection", "delete")))
                .register(SubcommandSpec.admin("give", (p, a) -> handleGive(p))
                        .withAliases(options.aliases("protection", "give")))
                .register(SubcommandSpec.admin("reload", (p, a) -> handleReload(p))
                        .withAliases(options.aliases("protection", "reload")))
                .register(SubcommandSpec.admin("recalculate", (p, a) -> handleRecalculate(p))
                        .withAliases(options.aliases("protection", "recalculate")))
                .register(SubcommandSpec.admin("integrations", (p, a) -> handleIntegrations(p))
                        .withAliases(options.aliases("protection", "integrations")));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(tr("common.players-only", "§cEste comando solo puede ser usado por jugadores."));
            return true;
        }
        if (!player.hasPermission(USE_PERM)) {
            error(player, WARD_PREFIX, tr("common.no-permission", "No tienes permiso para usar este comando."));
            return true;
        }
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }
        SubcommandSpec spec = registry.resolve(args[0]);
        if (spec == null || !options.isEnabled(registry.root(), spec.name())) {
            error(player, WARD_PREFIX, tr("common.unknown-subcommand", "Subcomando desconocido: {sub}", "sub", args[0]));
            sendHelp(player);
            return true;
        }
        try {
            return spec.execute(player, args);
        } catch (RuntimeException e) {
            if (!handleDomainException(player, WARD_PREFIX, e)) {
                error(player, WARD_PREFIX, tr("common.error", "Error: {message}", "message", e.getMessage()));
            }
            return true;
        }
    }

    // ── Help ──────────────────────────────────────────────────────────────────

    private void sendHelp(Player player) {
        helpBlock(player, "help.protection");
        if (player.hasPermission(ADMIN_PERM)) {
            helpAdminSection(player, "help.protection.admin");
        }
    }

    // ── Player subcommands ────────────────────────────────────────────────────

    /** /protection claim — opens the shared Ward menu for the Ward at the player's position. */
    private boolean handleClaim(Player player) {
        if (!canOpenProtectionMenu(player)) {
            error(player, WARD_PREFIX, "El menú por comando está reservado a VIPs y Gobernadores.");
            info(player, WARD_PREFIX, "Abrí el menú con clic derecho en la baliza del Ward.");
            return true;
        }
        Ward ward = resolveWard(player);
        if (ward == null) return true;
        menuFacade.open(player, ward);
        return true;
    }

    /**
     * Menu gate: admins, VIPs (either menu node) or city governors.
     */
    public boolean canOpenProtectionMenu(Player player) {
        if (player.hasPermission(ADMIN_PERM) || player.hasPermission(MENU_PERM)
                || player.hasPermission("dreamcraft.ward.admin")
                || player.hasPermission("dreamcraft.ward.menu")) {
            return true;
        }
        return cityService.findByMember(player.getUniqueId())
                .map(city -> city.isGovernor(player.getUniqueId()))
                .orElse(false);
    }

    /** /protection status — text summary of the current Ward. */
    private boolean handleStatus(Player player) {
        Ward ward = resolveWard(player);
        if (ward == null) return true;
        info(player, WARD_PREFIX, "Nombre: §f" + ward.name());
        info(player, WARD_PREFIX, "ID: " + ward.id());
        info(player, WARD_PREFIX, "Owner: " + resolveName(ward.ownerId()));
        info(player, WARD_PREFIX, "Tier: " + ward.tier() + " | Score: " + ward.baseScore());
        info(player, WARD_PREFIX, "Radio: " + ward.radius() + " bloques");
        info(player, WARD_PREFIX, "Centro: " + ward.centerX() + ", " + ward.centerY() + ", " + ward.centerZ());
        info(player, WARD_PREFIX, "Upkeep: " + ward.upkeepBalance() + " unidades");
        if (ward.hasCityMembership()) {
            cityService.findById(ward.cityId()).ifPresent(c ->
                    info(player, WARD_PREFIX, "Ciudad: " + c.name()));
        }
        return true;
    }

    /** /protection rename <nombre...> — renames the current Ward (owner/admin only). */
    private boolean handleRename(Player player, String[] args) {
        if (args.length < 2) {
            error(player, WARD_PREFIX, "Uso: /protection rename <nombre>");
            return true;
        }
        Ward ward = resolveWard(player);
        if (ward == null) return true;
        if (!ward.ownerId().equals(player.getUniqueId()) && !player.hasPermission(ADMIN_PERM)) {
            error(player, WARD_PREFIX, "Solo el owner puede renombrar el Ward.");
            return true;
        }
        String newName = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).trim();
        String oldName = ward.name();
        wardService.renameWard(ward, newName); // throws IllegalArgumentException → handled upstream
        ok(player, WARD_PREFIX, "Ward §f" + oldName + "§a renombrado a §f" + ward.name() + "§a.");
        title(player, "Ward Renombrado", ward.name(), NamedTextColor.AQUA);
        return true;
    }

    /** /protection upkeep [deposit <material> <n>] — mirrors /ward upkeep. */
    private boolean handleUpkeep(Player player, String[] args) {
        Ward ward = resolveWard(player);
        if (ward == null) return true;

        if (args.length >= 2 && "deposit".equalsIgnoreCase(args[1])) {
            if (upkeepService == null) {
                error(player, WARD_PREFIX, "Depósitos no disponibles.");
                return true;
            }
            // Lore: feeding the nucleus requires the physical block (or remote link)
            if (!ensurePresence(player, ward)) return true;
            if (args.length < 4) {
                error(player, WARD_PREFIX, "Uso: /protection upkeep deposit <material> <cantidad>");
                info(player, WARD_PREFIX, "Aceptados: " + acceptedMaterialsList());
                return true;
            }
            org.bukkit.Material material = upkeepService.matchAccepted(args[2]).orElse(null);
            if (material == null) {
                error(player, WARD_PREFIX, "Material no aceptado: " + args[2]);
                info(player, WARD_PREFIX, "Aceptados: " + acceptedMaterialsList());
                return true;
            }
            int amount;
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                error(player, WARD_PREFIX, "Cantidad inválida: " + args[3]);
                return true;
            }
            if (amount <= 0) {
                error(player, WARD_PREFIX, "La cantidad debe ser mayor a 0.");
                return true;
            }
            int carried = countMaterial(player, material);
            if (carried < amount) {
                error(player, WARD_PREFIX, "Solo tenés " + carried + "x "
                        + upkeepService.displayName(material) + ".");
                return true;
            }
            if (!upkeepService.canDeposit(player, ward)) {
                error(player, WARD_PREFIX, "No puedes depositar upkeep en este Ward.");
                return true;
            }
            upkeepService.consumeFromInventory(player, material, amount);
            var receipt = upkeepService.deposit(ward, player, material, amount);
            ok(player, WARD_PREFIX, "Depositaste " + receipt.amount() + "x §f"
                    + upkeepService.displayName(receipt.material()) + "§a → +"
                    + receipt.unitsCredited() + " unidades. Balance: §f" + receipt.newBalance());
            return true;
        }

        info(player, WARD_PREFIX, "Upkeep: " + ward.upkeepBalance() + " unidades | Próximo cobro: " + ward.nextUpkeepAt());
        if (upkeepService != null) {
            info(player, WARD_PREFIX, "Depositar: /protection upkeep deposit <material> <n>");
            info(player, WARD_PREFIX, "Aceptados: " + acceptedMaterialsList());
        }
        return true;
    }

    private String acceptedMaterialsList() {
        StringBuilder sb = new StringBuilder();
        upkeepService.acceptedMaterials().forEach((mat, units) -> {
            if (sb.length() > 0) sb.append("§7, ");
            sb.append("§f").append(upkeepService.displayName(mat)).append(" §7(×").append(units).append(")");
        });
        return sb.toString();
    }

    private int countMaterial(Player player, org.bukkit.Material material) {
        int total = 0;
        for (org.bukkit.inventory.ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() == material) total += item.getAmount();
        }
        return total;
    }

    /** /protection permissions [perm grant|revoke] — Ward public permission flags. */
    private boolean handlePermissions(Player player, String[] args) {
        Ward ward = resolveWard(player);
        if (ward == null) return true;
        if (args.length >= 3) {
            if (!ward.ownerId().equals(player.getUniqueId()) && !player.hasPermission(ADMIN_PERM)) {
                error(player, WARD_PREFIX, "Solo el owner puede cambiar permisos.");
                return true;
            }
            WardPermission perm;
            try {
                perm = WardPermission.valueOf(args[1].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                error(player, WARD_PREFIX, "Permiso inválido: " + args[1]);
                info(player, WARD_PREFIX, "Válidos: PUBLIC_BUILD, PUBLIC_BREAK, PUBLIC_CONTAINERS, "
                        + "PUBLIC_UPKEEP_DEPOSIT, PUBLIC_STATUS_VIEW");
                return true;
            }
            switch (args[2].toLowerCase(Locale.ROOT)) {
                case "grant" -> {
                    ward.grantPermission(perm);
                    persistPermissions(ward);
                    syncContainerFlag(ward, perm);
                    ok(player, WARD_PREFIX, "Permiso §f" + perm.name() + "§a concedido al público.");
                }
                case "revoke" -> {
                    ward.revokePermission(perm);
                    persistPermissions(ward);
                    syncContainerFlag(ward, perm);
                    warn(player, WARD_PREFIX, "Permiso §f" + perm.name() + "§e revocado.");
                }
                default -> error(player, WARD_PREFIX, "Uso: /protection permissions <perm> <grant|revoke>");
            }
            return true;
        }
        info(player, WARD_PREFIX, "Permisos públicos de §f" + ward.name() + "§7:");
        for (WardPermission perm : WardPermission.values()) {
            player.sendMessage("§7- §f" + perm.name() + "§7: "
                    + (ward.hasPermission(perm) ? "§aactivado" : "§cdesactivado"));
        }
        info(player, WARD_PREFIX, "Usá /protection permissions <perm> <grant|revoke> para cambiarlos.");
        return true;
    }

    private void persistPermissions(Ward ward) {
        wardService.assignWorldGuardRegion(ward, ward.worldGuardRegionId()); // persists via repository
    }

    /** Flips the WG chest-access flag when the container permission changes. */
    private void syncContainerFlag(Ward ward, WardPermission perm) {
        if (perm == WardPermission.PUBLIC_CONTAINERS) {
            worldGuardAdapter.setPublicContainerAccess(ward, ward.hasPermission(perm));
        }
    }

    /** /protection upgrade — moves the Ward to the next tier, charging item costs. */
    private boolean handleUpgrade(Player player) {
        Ward ward = resolveWard(player);
        if (ward == null) return true;
        if (!ward.ownerId().equals(player.getUniqueId()) && !player.hasPermission(ADMIN_PERM)) {
            error(player, WARD_PREFIX, "Solo el owner puede mejorar el Ward.");
            return true;
        }
        // Lore «El Despertar»: raising a phase requires the physical nucleus
        if (!ensurePresence(player, ward)) return true;
        var quoteOpt = upgradeService.quoteNext(ward);
        if (quoteOpt.isEmpty()) {
            warn(player, WARD_PREFIX, "El Ward ya está en el tier máximo (§b" + ward.tier() + "§e).");
            return true;
        }
        var quote = quoteOpt.get();

        // Refuse the upgrade when the new radius would reach a foreign Ward
        var conflictOpt = wardService.findForeignConflict(ward, quote.radiusAfter());
        if (conflictOpt.isPresent()) {
            Ward other = conflictOpt.get();
            error(player, WARD_PREFIX, "No puedes mejorar al tier §b" + quote.targetTierKey()
                    + "§c: el radio nuevo (§f" + quote.radiusAfter() + "§c) alcanzaría la Ward §f"
                    + other.name() + "§c de " + ownerName(other) + ".");
            return true;
        }

        var missing = upgradeService.missingItems(player, quote);
        if (!missing.isEmpty()) {
            error(player, WARD_PREFIX, "Te faltan ítems para mejorar al tier §b" + quote.targetTierKey() + "§c:");
            missing.forEach(player::sendMessage);
            return true;
        }
        upgradeService.charge(player, quote);
        wardService.addBaseScore(ward, quote.scoreGain());
        worldGuardAdapter.resizeRegion(ward, -64, 320);
        ok(player, WARD_PREFIX, "Ward mejorado a tier §b" + ward.tier()
                + "§a (radio §f" + ward.radius() + "§a, upkeep §f" + quote.upkeepPerInterval()
                + "§a u/intervalo). Ítems descontados.");
        return true;
    }

    /** /protection transfer <jugador>. */
    private boolean handleTransfer(Player player, String[] args) {
        if (args.length < 2) {
            error(player, WARD_PREFIX, "Uso: /protection transfer <jugador>");
            return true;
        }
        Ward ward = resolveWard(player);
        if (ward == null) return true;
        if (!ward.ownerId().equals(player.getUniqueId())) {
            error(player, WARD_PREFIX, "Solo el owner puede transferir el Ward.");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            error(player, WARD_PREFIX, "Jugador §f" + args[1] + "§c no encontrado o no está en línea.");
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            error(player, WARD_PREFIX, "No puedes transferirte el Ward a ti mismo.");
            return true;
        }
        wardService.transferOwnership(ward, target.getUniqueId(), OwnerType.PLAYER);
        worldGuardAdapter.syncOwner(ward);
        ok(player, WARD_PREFIX, "Ownership transferido a §f" + target.getName() + "§a.");
        target.sendMessage("§a[Protección] §f" + player.getName() + "§a te transfirió su Ward §f"
                + ward.name() + "§a.");
        return true;
    }

    /** /protection members — wards don't have member lists; they use City membership. */
    private boolean handleMembers(Player player) {
        Ward ward = resolveWard(player);
        if (ward == null) return true;
        info(player, WARD_PREFIX, "Los Wards no manejan listas de miembros propias:");
        info(player, WARD_PREFIX, "el acceso se gestiona con permisos públicos y por Ciudad.");
        if (ward.hasCityMembership()) {
            cityService.findById(ward.cityId()).ifPresent(c ->
                    info(player, WARD_PREFIX, "Este Ward pertenece a §f" + c.name()
                            + "§7 — sus habitantes tienen acceso."));
        } else {
            info(player, WARD_PREFIX, "Anexalo a tu ciudad con §f/ward city annex§7 para dar acceso a sus habitantes.");
        }
        info(player, WARD_PREFIX, "Permisos públicos: §f/protection permissions");
        return true;
    }

    /** /protection abandon — dissolves the current Ward (owner/admin only). */
    private boolean handleAbandon(Player player) {
        Ward ward = resolveWard(player);
        if (ward == null) return true;
        if (!ward.ownerId().equals(player.getUniqueId()) && !player.hasPermission(ADMIN_PERM)) {
            error(player, WARD_PREFIX, "Solo el owner puede disolver el Ward.");
            return true;
        }
        worldGuardAdapter.removeRegion(ward);
        wardService.delete(ward);
        ok(player, WARD_PREFIX, "Ward §f" + ward.name() + "§a disuelto. El área ya no está protegida.");
        return true;
    }

    // ── Admin subcommands ─────────────────────────────────────────────────────

    /** /protection give — hands the executing admin a Ward Beacon item. */
    private boolean handleGive(Player player) {
        if (!player.hasPermission(ADMIN_PERM)) {
            error(player, WARD_PREFIX, "No tienes permiso para este comando.");
            return true;
        }
        player.getInventory().addItem(wardItems.createWardItem());
        ok(player, WARD_PREFIX, "Baliza de Ward entregada. Colocala para fundar un Ward.");
        return true;
    }

    private boolean handleReload(Player player) {
        if (!player.hasPermission(ADMIN_PERM)) {
            error(player, WARD_PREFIX, "No tienes permiso para este comando.");
            return true;
        }
        reloadAction.run();
        ok(player, WARD_PREFIX, "Configuración recargada.");
        return true;
    }

    /** /protection recalculate — re-syncs the WG region to the Ward's stored radius. */
    private boolean handleRecalculate(Player player) {
        if (!player.hasPermission(ADMIN_PERM)) {
            error(player, WARD_PREFIX, "No tienes permiso para este comando.");
            return true;
        }
        Ward ward = resolveWard(player);
        if (ward == null) return true;
        worldGuardAdapter.resizeRegion(ward, -64, 320);
        ok(player, WARD_PREFIX, "Región re-sincronizada (radio §f" + ward.radius() + "§a).");
        return true;
    }

    /**
     * /protection integrations — MD §19: reports infrastructure AND presentation
     * integrations with their capabilities (perm: dreamcraft.integrations.status).
     */
    private boolean handleIntegrations(Player player) {
        if (!player.hasPermission("dreamcraft.integrations.status")) {
            error(player, WARD_PREFIX, tr("common.no-permission-action", "No tienes permiso para este comando."));
            return true;
        }
        info(player, WARD_PREFIX, "Integration Registry (infraestructura):");
        if (capabilityRegistry != null) {
            for (var entry : capabilityRegistry.allStatuses().entrySet()) {
                var status = entry.getValue();
                String mark = status.available() ? "&a✓" : (status.present() ? "&e!" : "&c✗");
                StringBuilder line = new StringBuilder(mark + " &f" + entry.getKey().name());
                if (status.detectedVersion() != null) line.append(" &7v").append(status.detectedVersion());
                if (status.unavailableReason() != null) line.append(" &8— ").append(status.unavailableReason());
                info(player, WARD_PREFIX, line.toString());
            }
        }
        info(player, WARD_PREFIX, "Presentación:");
        info(player, WARD_PREFIX, "- modo de assets: &f" + assetMode.name().toLowerCase(Locale.ROOT));
        if (assetRegistry != null && !assetRegistry.providerName().isBlank()) {
            info(player, WARD_PREFIX, "- proveedor de assets: &f" + assetRegistry.providerName()
                    + (assetRegistry.isAvailable() ? " &a(disponible)" : " &c(sin entradas)"));
            info(player, WARD_PREFIX, "- iconos en contrato: &f" + assetRegistry.iconCount());
            info(player, WARD_PREFIX, "- capabilities: custom-models=&f"
                    + (assetRegistry.isAvailable() ? "sí" : "no")
                    + "&7, custom-sounds=&f" + (assetRegistry.sound("menu.click") != null ? "sí" : "no"));
        }
        if (capabilityRegistry != null) {
            info(player, WARD_PREFIX, "- Oraxen detectado: &f"
                    + (capabilityRegistry.isAvailable(dev.dreamcraft.protection.integration.registry.IntegrationKey.ORAXEN) ? "sí" : "no")
                    + "&7 | DeluxeMenus detectado: &f"
                    + (capabilityRegistry.isAvailable(dev.dreamcraft.protection.integration.registry.IntegrationKey.DELUXE_MENUS) ? "sí" : "no"));
        }
        return true;
    }

    // ── Ward resolution ───────────────────────────────────────────────────────

    /**
     * Lore gate: feeding the nucleus and raising phases require standing next
     * to the DreamCraft block — unless the viewer holds the remote link
     * ({@code dreamcraft.ward.remote}, admins bypass).
     */
    private boolean ensurePresence(Player player, Ward ward) {
        if (player.hasPermission(WardCommand.REMOTE_PERM) || player.hasPermission(ADMIN_PERM)) return true;
        if (player.getWorld().getName().equals(ward.worldName())) {
            double r = ward.radius() + 2;
            double dx = player.getLocation().getX() - (ward.centerX() + 0.5);
            double dz = player.getLocation().getZ() - (ward.centerZ() + 0.5);
            double dy = player.getLocation().getY() - (ward.centerY() + 0.5);
            if (dx * dx + dz * dz <= r * r && Math.abs(dy) <= r) return true;
        }
        error(player, WARD_PREFIX, tr("common.physical-required",
                "Debes interactuar físicamente con tu Bloque de DreamCraft para esto."));
        info(player, WARD_PREFIX, tr("common.physical-hint",
                "Acércate a tu Núcleo o consigue el enlace remoto VIP con /sync tp."));
        return false;
    }

    /** Resolves the ward: explicit UUID arg → ward at location → owner's first ward. */
    private Ward resolveWard(Player player) {
        var atLocation = wardService.findAtLocation(
                player.getWorld().getName(),
                player.getLocation().getBlockX(),
                player.getLocation().getBlockZ());
        if (atLocation.isPresent()) return atLocation.get();
        var byOwner = wardService.findByOwner(player.getUniqueId());
        if (!byOwner.isEmpty()) return byOwner.iterator().next();
        error(player, WARD_PREFIX, "No hay ningún Ward en esta posición. Fundá uno con §f/ward create§c.");
        return null;
    }

    // ── Tab completion ────────────────────────────────────────────────────────

    /** Display name of a Ward's owner for messages (online, offline or fallback). */
    private String ownerName(Ward ward) {
        String name = Bukkit.getOfflinePlayer(ward.ownerId()).getName();
        return name != null ? name : "desconocido";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) return List.of();

        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            // Admin-only tokens stay hidden unless the sender has the admin node
            filter(registry.completionTokens(args[0], spec ->
                    !spec.isAdminOnly() || player.hasPermission(ADMIN_PERM)), args[0]).forEach(completions::add);
            return completions;
        }
        SubcommandSpec resolved = registry.resolve(args[0]);
        String sub = (resolved != null ? resolved.name() : args[0]).toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            switch (sub) {
                case "transfer" -> filter(onlinePlayers(args[1]), args[1]).forEach(completions::add);
                case "permissions" -> {
                    for (WardPermission p : WardPermission.values()) completions.add(p.name().toLowerCase(Locale.ROOT));
                    filter(completions, args[1]).forEach(completions::add);
                }
                case "upkeep" -> filter(List.of("deposit"), args[1]).forEach(completions::add);
                default -> { }
            }
            return completions;
        }
        if (args.length == 3) {
            if ("upkeep".equals(sub) && "deposit".equalsIgnoreCase(args[1]) && upkeepService != null) {
                upkeepService.acceptedMaterials().keySet().stream()
                        .map(Enum::name)
                        .map(String::toLowerCase)
                        .filter(m -> m.startsWith(args[2].toLowerCase(Locale.ROOT)))
                        .forEach(completions::add);
                return completions;
            }
            if ("permissions".equals(sub)) {
                filter(List.of("grant", "revoke"), args[2]).forEach(completions::add);
                return completions;
            }
        }
        return List.of();
    }

    private List<String> onlinePlayers(String prefix) {
        List<String> names = new ArrayList<>();
        Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT)))
                .forEach(names::add);
        return names;
    }

    private List<String> filter(List<String> options, String prefix) {
        return options.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT)))
                .toList();
    }
}
