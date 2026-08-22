package dev.dreamcraft.protection.command;

import dev.dreamcraft.protection.config.CommandNames;

import dev.dreamcraft.protection.domain.model.City;
import dev.dreamcraft.protection.domain.model.OwnerType;
import dev.dreamcraft.protection.domain.model.Ward;
import dev.dreamcraft.protection.domain.model.WardPermission;
import dev.dreamcraft.protection.domain.service.CityService;
import dev.dreamcraft.protection.domain.service.WardService;
import dev.dreamcraft.protection.integration.worldguard.WorldGuardAdapter;
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
 * Handles all /ward (alias: /w) subcommands.
 *
 * <p>Delegates all business logic to {@link WardService} and opens menus through
 * the shared {@link WardMenuFacade} — the exact same menu as /protection claim.
 */
public final class WardCommand implements CommandExecutor, TabCompleter {

    private static final String USE_PERM = "dreamcraft.ward.use";
    private static final String ADMIN_PERM = "dreamcraft.ward.admin";
    /** VIP/menu permission: admins (ward.admin) or holders of this node may open /ward menu. */
    private static final String MENU_PERM = "dreamcraft.ward.menu";
    /**
     * Lore «El Despertar»: enlace dimensional remoto (VIP/staff). Holders may
     * feed the nucleus and raise phases from anywhere; everyone else must stand
     * physically next to their DreamCraft block.
     */
    public static final String REMOTE_PERM = "dreamcraft.ward.remote";

    private final WardService wardService;
    private final CityService cityService;
    private final WorldGuardAdapter worldGuardAdapter;
    private final dev.dreamcraft.protection.ui.WardItems wardItems;
    /** Shared menu façade — same menu as /protection claim. */
    private final WardMenuFacade menuFacade;
    /** Optional: item-based upkeep deposits. */
    private final dev.dreamcraft.protection.service.WardUpkeepService upkeepService;
    /** Per-server subcommand aliases/enabled flags. */
    private final dev.dreamcraft.protection.config.CommandOptions options;
    /** Single source of truth for dispatch + tab completion. */
    private final CommandRegistry registry;

    public WardCommand(WardService wardService,
                       CityService cityService,
                       WorldGuardAdapter worldGuardAdapter,
                       dev.dreamcraft.protection.ui.WardItems wardItems,
                       WardMenuFacade menuFacade,
                       dev.dreamcraft.protection.service.WardUpkeepService upkeepService) {
        this(wardService, cityService, worldGuardAdapter, wardItems, menuFacade, upkeepService,
                dev.dreamcraft.protection.config.CommandOptions.empty());
    }

    public WardCommand(WardService wardService,
                       CityService cityService,
                       WorldGuardAdapter worldGuardAdapter,
                       dev.dreamcraft.protection.ui.WardItems wardItems,
                       WardMenuFacade menuFacade,
                       dev.dreamcraft.protection.service.WardUpkeepService upkeepService,
                       dev.dreamcraft.protection.config.CommandOptions options) {
        this.wardService = wardService;
        this.cityService = cityService;
        this.worldGuardAdapter = worldGuardAdapter;
        this.wardItems = wardItems;
        this.menuFacade = menuFacade;
        this.upkeepService = upkeepService;
        this.options = options;
        this.registry = buildRegistry();
    }

    /**
     * Builds the subcommand table: canonical names here, aliases merged from
     * config.yml (commands.ward.subcommands.&lt;name&gt;.aliases).
     *
     * <p>Lore «El Despertar y la Sincronicidad»: this root is surfaced as
     * {@code /sync} via the server's commands.yml; subcommands use the lore
     * vocabulary through config aliases (despertar, renombrar, permisos…).
     */
    private CommandRegistry buildRegistry() {
        return new CommandRegistry("ward")
                .register(SubcommandSpec.of("create", (p, a) -> handleCreate(p))
                        .withAliases(options.aliases("ward", "create")))
                .register(SubcommandSpec.of("rename", this::handleRename)
                        .withAliases(options.aliases("ward", "rename")))
                .register(SubcommandSpec.of("info", this::handleInfo)
                        .withAliases(options.aliases("ward", "info")))
                .register(SubcommandSpec.of("delete", this::handleDelete)
                        .withAliases(options.aliases("ward", "delete")))
                .register(SubcommandSpec.of("score", this::handleScore)
                        .withAliases(options.aliases("ward", "score")))
                .register(SubcommandSpec.of("upkeep", this::handleUpkeep)
                        .withAliases(options.aliases("ward", "upkeep")))
                .register(SubcommandSpec.of("sintonizar", this::handleSintonize)
                        .withAliases(options.aliases("ward", "sintonizar")))
                .register(SubcommandSpec.of("expulsar", this::handleExpulsar)
                        .withAliases(options.aliases("ward", "expulsar")))
                .register(SubcommandSpec.of("transfer", this::handleTransfer)
                        .withAliases(options.aliases("ward", "transfer")))
                .register(SubcommandSpec.of("permissions", this::handlePermissions)
                        .withAliases(options.aliases("ward", "permissions")))
                .register(SubcommandSpec.of("city", this::handleCity)
                        .withAliases(options.aliases("ward", "city")))
                .register(SubcommandSpec.of("menu", this::handleMenu)
                        .withAliases(options.aliases("ward", "menu")))
                .register(SubcommandSpec.of("tp", this::handleTp)
                        .withAliases(options.aliases("ward", "tp")))
                .register(SubcommandSpec.admin("abrir", this::handleAdminOpen)
                        .withAliases(options.aliases("ward", "abrir")))
                .register(SubcommandSpec.admin("give", (p, a) -> handleGive(p))
                        .withAliases(options.aliases("ward", "give")));
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

    // ── Subcommand handlers ────────────────────────────────────────────────────

    private boolean handleCreate(Player player) {
        Ward ward = wardService.createWard(
                player.getUniqueId(),
                OwnerType.PLAYER,
                null,
                player.getWorld().getName(),
                player.getLocation().getBlockX(),
                player.getLocation().getBlockY(),
                player.getLocation().getBlockZ(),
                null // unique friendly name generated by the domain service
        );
        // Create the WorldGuard region and link it back
        String regionId = worldGuardAdapter.createRegion(ward, player.getWorld().getName(), -64, 320);
        if (regionId != null) {
            wardService.assignWorldGuardRegion(ward, regionId);
        }
        ok(player, WARD_PREFIX, "Núcleo §f" + ward.name() + "§a despertado (fase " + ward.tier() + ", radio " + ward.radius() + ").");
        title(player, "âœ¦ Territorio Despierto âœ¦", ward.name(), NamedTextColor.AQUA);
        return true;
    }

    /** /ward give — admin only: hands the special ward block item to the caller. */
    private boolean handleGive(Player player) {
        if (!player.hasPermission(ADMIN_PERM)) {
            error(player, WARD_PREFIX, "No tienes permiso para este comando.");
            return true;
        }
        player.getInventory().addItem(wardItems.createWardItem());
        ok(player, WARD_PREFIX, "Núcleo de Sincronía entregado. Colocalo para despertar tu territorio.");
        return true;
    }

    // ── Lore «El Despertar»: presencia física vs enlace remoto ────────────────

    /** True when this player holds the dimensional remote link (VIP/staff). */
    private boolean hasRemoteLink(Player player) {
        return player.hasPermission(REMOTE_PERM) || player.hasPermission(ADMIN_PERM);
    }

    /**
     * Lore gate: feeding the nucleus and raising phases require standing next
     * to the DreamCraft block — unless the viewer holds the remote link.
     */
    private boolean ensurePresence(Player player, Ward ward) {
        if (hasRemoteLink(player)) return true;
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
                "Acércate a tu Núcleo; el enlace remoto es exclusivo VIP."));
        return false;
    }

    /**
     * /ward sintonizar — opens the territory's frequency to public builders:
     * grants PUBLIC_BUILD + PUBLIC_CONTAINERS and syncs WorldGuard.
     */
    private boolean handleSintonize(Player player, String[] args) {
        Ward ward = resolveWard(player, args);
        if (ward == null) return true;
        if (!ward.ownerId().equals(player.getUniqueId()) && !player.hasPermission(ADMIN_PERM)) {
            error(player, WARD_PREFIX, "Solo el owner puede sintonizar constructores.");
            return true;
        }
        ward.grantPermission(WardPermission.PUBLIC_BUILD);
        ward.grantPermission(WardPermission.PUBLIC_CONTAINERS);
        persistAndSyncPermissions(ward);
        ok(player, WARD_PREFIX, tr("sync.sintonized",
                "Frecuencia compartida: los constructores ya pueden edificar y usar contenedores."));
        return true;
    }

    /** /ward expulsar — closes the shared frequency (revokes the public flags). */
    private boolean handleExpulsar(Player player, String[] args) {
        Ward ward = resolveWard(player, args);
        if (ward == null) return true;
        if (!ward.ownerId().equals(player.getUniqueId()) && !player.hasPermission(ADMIN_PERM)) {
            error(player, WARD_PREFIX, "Solo el owner puede cerrar la frecuencia.");
            return true;
        }
        ward.revokePermission(WardPermission.PUBLIC_BUILD);
        ward.revokePermission(WardPermission.PUBLIC_CONTAINERS);
        persistAndSyncPermissions(ward);
        warn(player, WARD_PREFIX, tr("sync.desintonized",
                "Frecuencia cerrada: revocado el acceso público de construcción y contenedores."));
        return true;
    }

    /** Persists permission changes and mirrors the container flag to WG. */
    private void persistAndSyncPermissions(Ward ward) {
        wardService.assignWorldGuardRegion(ward, ward.worldGuardRegionId());
        syncContainerFlag(ward, WardPermission.PUBLIC_CONTAINERS);
    }

    /**
     * /ward alimentar [cantidad] — remote energy sync (lore: enlace dimensional
     * VIP). Consumes up to {@code cantidad} items straight from the inventory,
     * best-value accepted materials first, and credits them to the nucleus.
     * Without arguments shows the current resonance status.
     */
    private boolean handleAlimentar(Player player, String[] args) {
        Ward ward = resolveWard(player, args);
        if (ward == null) return true;
        if (!hasRemoteLink(player)) {
            error(player, WARD_PREFIX, tr("common.physical-required",
                    "Debes interactuar físicamente con tu Bloque de DreamCraft para esto."));
            info(player, WARD_PREFIX, tr("common.physical-hint",
                    "Acércate a tu Núcleo; el enlace remoto es exclusivo VIP."));
            return true;
        }
        if (upkeepService == null) {
            error(player, WARD_PREFIX, "Depósitos no disponibles.");
            return true;
        }
        if (!upkeepService.canDeposit(player, ward)) {
            error(player, WARD_PREFIX, "No puedes depositar energÃ­a en este NÃºcleo.");
            return true;
        }
        if (args.length < 2 || !args[1].matches("\\d+")) {
            info(player, WARD_PREFIX, "Upkeep: " + ward.upkeepBalance()
                    + " | Uso: " + CommandNames.cmd("ward", "alimentar <cantidad>"));
            info(player, WARD_PREFIX, "Aceptados: " + acceptedMaterialsList());
            return true;
        }
        int requested = Integer.parseInt(args[1]);
        if (requested <= 0) {
            error(player, WARD_PREFIX, tr("common.invalid-amount", "Cantidad inválida: {amount}",
                    "amount", args[1]));
            return true;
        }

        // Greedy: highest-value materials first so fewer slots are consumed
        var ordered = upkeepService.acceptedMaterials().entrySet().stream()
                .sorted(java.util.Map.Entry.<org.bukkit.Material, Integer>comparingByValue().reversed())
                .toList();
        int consumed = 0;
        int remaining = requested;
        for (var entry : ordered) {
            if (remaining <= 0) break;
            org.bukkit.Material material = entry.getKey();
            int carried = countMaterial(player, material);
            if (carried <= 0) continue;
            int use = Math.min(carried, remaining);
            upkeepService.consumeFromInventory(player, material, use);
            upkeepService.deposit(ward, player, material, use);
            consumed += use;
            remaining -= use;
        }
        if (consumed == 0) {
            warn(player, WARD_PREFIX, "No llevas materiales aceptados. Aceptados: "
                    + acceptedMaterialsList());
            return true;
        }
        playSyncFeedback(player);
        ok(player, WARD_PREFIX, "✦ Sincronizaste §f" + consumed + "§a ítem(s) a distancia → Balance: §f"
                + ward.upkeepBalance() + "§a unidades.");
        return true;
    }

    /** Soft crystal chime for successful remote synchronization. */
    private void playSyncFeedback(Player player) {
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.4f);
    }

    /**
     * /ward letargo &lt;id|jugador&gt; — staff: drains all synced energy,
     * forcing the territory into its dormant state immediately.
     */
    private boolean handleLetargo(Player player, String[] args) {
        if (!player.hasPermission(ADMIN_PERM)) {
            error(player, WARD_PREFIX, "No tienes permiso para este comando.");
            return true;
        }
        Ward ward = null;
        if (args.length >= 2) {
            try {
                ward = wardService.findById(UUID.fromString(args[1])).orElse(null);
            } catch (IllegalArgumentException ignored) {}
            if (ward == null) {
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target != null) {
                    var owned = wardService.findByOwner(target.getUniqueId());
                    if (!owned.isEmpty()) ward = owned.iterator().next();
                }
            }
        }
        if (ward == null) {
            error(player, WARD_PREFIX, tr("sync.nucleus-not-found",
                    "Núcleo no encontrado. Usa <id> o el nombre de un jugador online."));
            return true;
        }
        int drained = ward.upkeepBalance();
        if (drained > 0) {
            wardService.deductUpkeep(ward, drained);
        }
        warn(player, WARD_PREFIX, "Núcleo §f" + ward.name() + "§e en letargo: energía drenada ("
                + drained + " u). El territorio queda pausado hasta re-sincronizar.");
        return true;
    }

    /**
     * /ward tp — immediate resonance jump to the nucleus. Owner or remote-link
     * holders only (lore: exclusive to VIP/staff).
     */
    private boolean handleTp(Player player, String[] args) {
        Ward ward = resolveWard(player, args);
        if (ward == null) return true;
        boolean owner = ward.ownerId().equals(player.getUniqueId());
        if (!owner && !hasRemoteLink(player)) {
            error(player, WARD_PREFIX, tr("common.no-permission", "No tienes permiso para usar este comando."));
            return true;
        }
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(ward.worldName());
        if (world == null) {
            error(player, WARD_PREFIX, "El mundo del núcleo no está cargado.");
            return true;
        }
        org.bukkit.Location target = new org.bukkit.Location(world,
                ward.centerX() + 0.5, ward.centerY() + 1.0, ward.centerZ() + 0.5);
        world.getChunkAt(target).load();
        player.teleport(target);
        actionbar(player, tr("sync.tp-done", "✦ Sintonización completa: llegada al núcleo."),
                NamedTextColor.AQUA);
        return true;
    }

    /**
     * /ward abrir &lt;id|jugador&gt; — staff inspection: opens any nucleus menu
     * remotely for review or adjustment.
     */
    private boolean handleAdminOpen(Player player, String[] args) {
        if (!player.hasPermission(ADMIN_PERM)) {
            error(player, WARD_PREFIX, "No tienes permiso para este comando.");
            return true;
        }
        Ward ward = null;
        if (args.length >= 2) {
            try {
                ward = wardService.findById(UUID.fromString(args[1])).orElse(null);
            } catch (IllegalArgumentException ignored) {}
            if (ward == null) {
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target != null) {
                    var owned = wardService.findByOwner(target.getUniqueId());
                    if (!owned.isEmpty()) ward = owned.iterator().next();
                }
            }
        }
        if (ward == null) {
            error(player, WARD_PREFIX, tr("sync.nucleus-not-found",
                    "Núcleo no encontrado. Usa <id> o el nombre de un jugador online."));
            return true;
        }
        openWardMenu(player, ward);
        return true;
    }

    /**
     * /ward rename <nombre...> — renames the resolved Ward (owner or admin only).
     * Accepts multi-word names; uniqueness is enforced by the domain service.
     */
    private boolean handleRename(Player player, String[] args) {
        if (args.length < 2) {
            error(player, WARD_PREFIX, CommandNames.cmd("ward", "rename <nombre>"));
            return true;
        }
        Ward ward = resolveWard(player, args);
        if (ward == null) return true;
        if (!ward.ownerId().equals(player.getUniqueId()) && !player.hasPermission(ADMIN_PERM)) {
            error(player, WARD_PREFIX, "Solo el owner puede renombrar el Ward.");
            return true;
        }
        String newName = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).trim();
        String oldName = ward.name();
        try {
            wardService.renameWard(ward, newName);
        } catch (IllegalArgumentException e) {
            error(player, WARD_PREFIX, e.getMessage());
            return true;
        }
        ok(player, WARD_PREFIX, "Núcleo §f" + oldName + "§a renombrado a §f" + ward.name() + "§a.");
        title(player, "NÃºcleo Renombrado", ward.name(), NamedTextColor.AQUA);
        return true;
    }

    private boolean handleInfo(Player player, String[] args) {
        Ward ward = resolveWard(player, args);
        if (ward == null) return true;
        info(player, WARD_PREFIX, "Nombre: §f" + ward.name());
        info(player, WARD_PREFIX, "ID: " + ward.id());
        info(player, WARD_PREFIX, "Owner: " + CommandMessages.resolveName(ward.ownerId()));
        info(player, WARD_PREFIX, "Tier: " + ward.tier() + " | Score: " + ward.baseScore());
        info(player, WARD_PREFIX, "Radio: " + ward.radius() + " | Upkeep: " + ward.upkeepBalance());
        info(player, WARD_PREFIX, "Centro: " + ward.centerX() + ", " + ward.centerY() + ", " + ward.centerZ());
        if (ward.hasCityMembership()) {
            cityService.findById(ward.cityId()).ifPresent(c ->
                    info(player, WARD_PREFIX, "Ciudad: " + c.name()));
        }
        return true;
    }

    private boolean handleDelete(Player player, String[] args) {
        Ward ward = resolveWard(player, args);
        if (ward == null) return true;
        if (!ward.ownerId().equals(player.getUniqueId()) && !player.hasPermission(ADMIN_PERM)) {
            error(player, WARD_PREFIX, "Solo el owner puede eliminar el NÃºcleo.");
            return true;
        }
        worldGuardAdapter.removeRegion(ward);
        wardService.delete(ward);
        ok(player, WARD_PREFIX, "NÃºcleo eliminado.");
        return true;
    }

    private boolean handleScore(Player player, String[] args) {
        Ward ward = resolveWard(player, args);
        if (ward == null) return true;
        if (args.length >= 3 && "add".equalsIgnoreCase(args[1])) {
            if (!ward.ownerId().equals(player.getUniqueId()) && !player.hasPermission(ADMIN_PERM)) {
                error(player, WARD_PREFIX, "Solo el owner puede modificar el score.");
                return true;
            }
            // Lore: raising a phase requires the physical nucleus (or remote link)
            if (!ensurePresence(player, ward)) return true;
            try {
                int delta = Integer.parseInt(args[2]);
                // Refuse growth that would reach a foreign Ward
                if (delta > 0) {
                    var conflictOpt = wardService.findForeignConflict(
                            ward, wardService.computeRadiusAfter(ward, delta));
                    if (conflictOpt.isPresent()) {
                        Ward other = conflictOpt.get();
                        error(player, WARD_PREFIX, "No puedes aumentar el score: el radio nuevo (§f"
                                + wardService.computeRadiusAfter(ward, delta)
                                + "§c) alcanzaría el Núcleo §f" + other.name()
                                + "§c de " + ownerName(other) + ".");
                        return true;
                    }
                }
                wardService.addBaseScore(ward, delta);
                worldGuardAdapter.resizeRegion(ward, -64, 320);
                ok(player, WARD_PREFIX, "Score actualizado: " + ward.baseScore() + " (tier " + ward.tier() + ").");
            } catch (NumberFormatException e) {
                error(player, WARD_PREFIX, "Cantidad inválida: " + args[2]);
            }
            return true;
        }
        info(player, WARD_PREFIX, "Score: " + ward.baseScore() + " | Tier: " + ward.tier());
        return true;
    }

    /**
     * /ward upkeep                          — shows balance and next charge
     * /ward upkeep deposit <material> <n>   — consumes n items, credits units
     * /ward upkeep credit <n>               — (admin/debug) credits raw units
     */
    private boolean handleUpkeep(Player player, String[] args) {
        Ward ward = resolveWard(player, args);
        if (ward == null) return true;

        if (args.length >= 2 && "deposit".equalsIgnoreCase(args[1])) {
            if (upkeepService == null) {
                error(player, WARD_PREFIX, "Depósitos no disponibles.");
                return true;
            }
            // Lore: feeding the nucleus requires the physical block (or remote link)
            if (!ensurePresence(player, ward)) return true;
            if (args.length < 4) {
                error(player, WARD_PREFIX, CommandNames.cmd("ward", "upkeep deposit <material> <cantidad>"));
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
            upkeepService.consumeFromInventory(player, material, amount);
            var receipt = upkeepService.deposit(ward, player, material, amount);
            ok(player, WARD_PREFIX, "Depositaste " + receipt.amount() + "x §f"
                    + upkeepService.displayName(receipt.material()) + "§a → +"
                    + receipt.unitsCredited() + " unidades. Balance: §f" + receipt.newBalance());
            return true;
        }

        if (args.length >= 2 && "credit".equalsIgnoreCase(args[1])) {
            if (!player.hasPermission(ADMIN_PERM)) {
                error(player, WARD_PREFIX, "Solo admins puede acreditar unidades directas.");
                return true;
            }
            try {
                int units = Integer.parseInt(args[2]);
                wardService.depositUpkeep(ward, units);
                ok(player, WARD_PREFIX, "Acreditadas " + units + " unidades. Balance: " + ward.upkeepBalance());
            } catch (NumberFormatException e) {
                error(player, WARD_PREFIX, "Cantidad inválida: " + args[2]);
            }
            return true;
        }

        info(player, WARD_PREFIX, "Upkeep: " + ward.upkeepBalance() + " | Próximo cobro: " + ward.nextUpkeepAt());
        if (upkeepService != null) {
            info(player, WARD_PREFIX, "Depositar ítems: " + CommandNames.cmd("ward", "upkeep deposit <material> <n>"));
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

    private boolean handleTransfer(Player player, String[] args) {
        if (args.length < 2) {
            error(player, WARD_PREFIX, CommandNames.cmd("ward", "transfer <jugador>"));
            return true;
        }
        Ward ward = resolveWard(player, args);
        if (ward == null) return true;
        if (!ward.ownerId().equals(player.getUniqueId())) {
            error(player, WARD_PREFIX, "Solo el owner puede transferir el NÃºcleo.");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            error(player, WARD_PREFIX, "Jugador " + args[1] + " no encontrado o no está en línea.");
            return true;
        }
        wardService.transferOwnership(ward, target.getUniqueId(), OwnerType.PLAYER);
        worldGuardAdapter.syncOwner(ward);
        ok(player, WARD_PREFIX, "NÃºcleo transferido a " + target.getName() + ".");
        return true;
    }

    private boolean handlePermissions(Player player, String[] args) {
        Ward ward = resolveWard(player, args);
        if (ward == null) return true;
        if (args.length >= 3) {
            if (!ward.ownerId().equals(player.getUniqueId())) {
                error(player, WARD_PREFIX, "Solo el owner puede cambiar permisos.");
                return true;
            }
            WardPermission perm;
            try {
                perm = WardPermission.valueOf(args[1].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                error(player, WARD_PREFIX, "Permiso inválido: " + args[1]);
                return true;
            }
            if ("grant".equalsIgnoreCase(args[2])) {
                ward.grantPermission(perm);
                wardService.assignWorldGuardRegion(ward, ward.worldGuardRegionId());
                syncContainerFlag(ward, perm);
                ok(player, WARD_PREFIX, "Permiso " + perm.name() + " concedido.");
            } else if ("revoke".equalsIgnoreCase(args[2])) {
                ward.revokePermission(perm);
                wardService.assignWorldGuardRegion(ward, ward.worldGuardRegionId());
                syncContainerFlag(ward, perm);
                ok(player, WARD_PREFIX, "Permiso " + perm.name() + " revocado.");
            } else {
                error(player, WARD_PREFIX, CommandNames.cmd("ward", "permissions <perm> <grant|revoke>"));
            }
            return true;
        }
        info(player, WARD_PREFIX, "Permisos: " + ward.permissions());
        return true;
    }

    private boolean handleCity(Player player, String[] args) {
        Ward ward = resolveWard(player, args);
        if (ward == null) return true;
        if (args.length < 2) {
            if (ward.hasCityMembership()) {
                cityService.findById(ward.cityId()).ifPresent(c ->
                        info(player, WARD_PREFIX, "Ciudad: " + c.name()));
            } else {
                info(player, WARD_PREFIX, "Este NÃºcleo no pertenece a ninguna Matriz.");
            }
            return true;
        }
        if ("annex".equalsIgnoreCase(args[1])) {
            if (!ward.ownerId().equals(player.getUniqueId())) {
                error(player, WARD_PREFIX, "Solo el owner puede federar el NÃºcleo.");
                return true;
            }
            var optCity = cityService.findByMember(player.getUniqueId());
            if (optCity.isEmpty()) {
                error(player, WARD_PREFIX, "No eres miembro de ninguna ciudad.");
                return true;
            }
            City city = optCity.get();
            wardService.setCityMembership(ward, city.id());
            dev.dreamcraft.protection.service.WardAccessSync.project(ward, cityService, worldGuardAdapter);
            ok(player, WARD_PREFIX, "NÃºcleo federado a la Matriz " + city.name() + ".");
            return true;
        }
        if ("leave".equalsIgnoreCase(args[1])) {
            if (!ward.ownerId().equals(player.getUniqueId())) {
                error(player, WARD_PREFIX, "Solo el owner puede desvincular el NÃºcleo.");
                return true;
            }
            // Clear membership first so the projection collapses the region's
            // member list (city-granted access fully revoked)
            wardService.setCityMembership(ward, null);
            dev.dreamcraft.protection.service.WardAccessSync.project(ward, cityService, worldGuardAdapter);
            ok(player, WARD_PREFIX, "NÃºcleo desvinculado de la Matriz.");
            return true;
        }
        error(player, WARD_PREFIX, CommandNames.cmd("ward", "city [annex|leave]"));
        return true;
    }

    private boolean handleMenu(Player player, String[] args) {
        if (!canOpenWardMenu(player)) {
            error(player, WARD_PREFIX, "El menú del Núcleo está reservado a admins y VIPs.");
            return true;
        }
        Ward ward = resolveWard(player, args);
        if (ward == null) return true;
        openWardMenu(player, ward);
        return true;
    }

    /**
     * Menu access gate shared with the ward block listener:
     * admins ({@code dreamcraft.ward.admin}) or VIPs ({@code dreamcraft.ward.menu}).
     */
    public boolean canOpenWardMenu(Player player) {
        return player.hasPermission(ADMIN_PERM) || player.hasPermission(MENU_PERM);
    }

    // ── Menu opening ───────────────────────────────────────────────────────────

    public void openWardMenu(Player player, Ward ward) {
        menuFacade.open(player, ward);
    }

    // ── Ward resolution ────────────────────────────────────────────────────────

    /**
     * Resolves the ward from args: tries UUID first, then player's location, then owner's first ward.
     */
    private Ward resolveWard(Player player, String[] args) {
        // If a sub-command is given as args[0], the ward ID may be in args[1] for some commands
        for (int i = 1; i < args.length; i++) {
            try {
                UUID id = UUID.fromString(args[i]);
                return wardService.findById(id).orElse(null);
            } catch (IllegalArgumentException ignored) {}
        }
        // Try location-based lookup
        var atLocation = wardService.findAtLocation(
                player.getWorld().getName(),
                player.getLocation().getBlockX(),
                player.getLocation().getBlockZ());
        if (atLocation.isPresent()) return atLocation.get();
        // Try owner's first ward
        var byOwner = wardService.findByOwner(player.getUniqueId());
        if (!byOwner.isEmpty()) return byOwner.iterator().next();
        error(player, WARD_PREFIX, "No se encontró ningún Núcleo. Usa " + CommandNames.cmd("ward", "create") + " primero.");
        return null;
    }

    /** Flips the WG chest-access flag when the container permission changes. */
    private void syncContainerFlag(Ward ward, WardPermission perm) {
        if (perm == WardPermission.PUBLIC_CONTAINERS) {
            worldGuardAdapter.setPublicContainerAccess(ward, ward.hasPermission(perm));
        }
    }

    // ── Help ──────────────────────────────────────────────────────────────────

    private void sendHelp(Player player) {
        helpBlock(player, "help.ward");
    }

    // ── Tab completion ─────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            filter(registry.completionTokens(args[0]), args[0]).forEach(completions::add);
            return completions;
        }
        if (args.length == 2) {
            SubcommandSpec spec = registry.resolve(args[0]);
            String sub = (spec != null ? spec.name() : args[0]).toLowerCase(Locale.ROOT);
            switch (sub) {
                case "transfer" -> filter(onlinePlayers(args[1]), args[1]).forEach(completions::add);
                case "permissions" -> {
                    for (WardPermission p : WardPermission.values()) completions.add(p.name().toLowerCase());
                    filter(completions, args[1]);
                }
                case "score" -> filter(List.of("add"), args[1]).forEach(completions::add);
                case "upkeep" -> {
                    List<String> opts = new ArrayList<>(List.of("deposit"));
                    if (sender.hasPermission(ADMIN_PERM)) opts.add("credit");
                    filter(opts, args[1]).forEach(completions::add);
                }
                case "city" -> filter(List.of("annex", "leave"), args[1]).forEach(completions::add);
                default -> wardIdsOf(sender).stream().filter(id -> id.startsWith(args[1])).forEach(completions::add);
            }
            return completions;
        }
        if (args.length == 3) {
            SubcommandSpec spec = registry.resolve(args[0]);
            String sub = (spec != null ? spec.name() : args[0]).toLowerCase(Locale.ROOT);
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

    /** Display name of a Ward's owner for messages (online, offline or fallback). */
    private String ownerName(Ward ward) {
        String name = Bukkit.getOfflinePlayer(ward.ownerId()).getName();
        return name != null ? name : "desconocido";
    }

    private List<String> onlinePlayers(String prefix) {
        List<String> names = new ArrayList<>();
        Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT)))
                .forEach(names::add);
        return names;
    }

    private List<String> wardIdsOf(CommandSender sender) {
        if (!(sender instanceof Player player)) return List.of();
        return wardService.findByOwner(player.getUniqueId()).stream()
                .map(w -> w.id().toString())
                .toList();
    }

    private List<String> filter(List<String> options, String prefix) {
        return options.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT)))
                .toList();
    }
}
