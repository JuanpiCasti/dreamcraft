package dev.dreamcraft.protection.command;

import dev.dreamcraft.protection.domain.model.OwnerType;
import dev.dreamcraft.protection.domain.model.Ward;
import dev.dreamcraft.protection.domain.model.WardPermission;
import dev.dreamcraft.protection.domain.port.WardTierProvider;
import dev.dreamcraft.protection.domain.service.CityService;
import dev.dreamcraft.protection.domain.service.WardService;
import dev.dreamcraft.protection.integration.worldguard.WorldGuardAdapter;
import dev.dreamcraft.protection.presentation.MenuContext;
import dev.dreamcraft.protection.presentation.MenuProvider;
import dev.dreamcraft.protection.presentation.menu.WardMenuBuilder;
import dev.dreamcraft.protection.presentation.viewmodel.WardViewModel;
import dev.dreamcraft.protection.presentation.viewmodel.WardViewModelBuilder;
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
import java.util.Map;
import java.util.UUID;

import static dev.dreamcraft.protection.command.CommandMessages.*;

/**
 * Handles all /ward (alias: /w) subcommands.
 *
 * <p>Delegates all business logic to {@link WardService} and opens menus via
 * {@link MenuProvider} using pre-computed {@link WardViewModel}s.
 */
public final class WardCommand implements CommandExecutor, TabCompleter {

    private static final String USE_PERM = "dreamcraft.ward.use";
    private static final String ADMIN_PERM = "dreamcraft.ward.admin";

    private final WardService wardService;
    private final CityService cityService;
    private final WorldGuardAdapter worldGuardAdapter;
    private final WardTierProvider tierProvider;
    private final MenuProvider menuProvider;
    private final WardViewModelBuilder viewModelBuilder;

    public WardCommand(WardService wardService,
                       CityService cityService,
                       WorldGuardAdapter worldGuardAdapter,
                       WardTierProvider tierProvider,
                       MenuProvider menuProvider) {
        this.wardService = wardService;
        this.cityService = cityService;
        this.worldGuardAdapter = worldGuardAdapter;
        this.tierProvider = tierProvider;
        this.menuProvider = menuProvider;
        this.viewModelBuilder = new WardViewModelBuilder(tierProvider, this::resolveName);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cEste comando solo puede ser usado por jugadores.");
            return true;
        }
        if (!player.hasPermission(USE_PERM)) {
            error(player, WARD_PREFIX, "No tienes permiso para usar este comando.");
            return true;
        }
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }
        try {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "create" -> handleCreate(player);
                case "info" -> handleInfo(player, args);
                case "delete" -> handleDelete(player, args);
                case "score" -> handleScore(player, args);
                case "upkeep" -> handleUpkeep(player, args);
                case "transfer" -> handleTransfer(player, args);
                case "permissions" -> handlePermissions(player, args);
                case "city" -> handleCity(player, args);
                case "menu" -> handleMenu(player, args);
                default -> {
                    error(player, WARD_PREFIX, "Subcomando desconocido: " + args[0]);
                    sendHelp(player);
                    yield true;
                }
            };
        } catch (RuntimeException e) {
            if (!handleDomainException(player, WARD_PREFIX, e)) {
                error(player, WARD_PREFIX, "Error: " + e.getMessage());
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
                player.getLocation().getBlockZ()
        );
        // Create the WorldGuard region and link it back
        String regionId = worldGuardAdapter.createRegion(ward, player.getWorld().getName(), -64, 320);
        if (regionId != null) {
            wardService.assignWorldGuardRegion(ward, regionId);
        }
        ok(player, WARD_PREFIX, "Ward creado (tier " + ward.tier() + ", radio " + ward.radius() + ").");
        title(player, "Ward Creado", ward.tier(), NamedTextColor.AQUA);
        return true;
    }

    private boolean handleInfo(Player player, String[] args) {
        Ward ward = resolveWard(player, args);
        if (ward == null) return true;
        info(player, WARD_PREFIX, "ID: " + ward.id());
        info(player, WARD_PREFIX, "Owner: " + resolveName(ward.ownerId()));
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
            error(player, WARD_PREFIX, "Solo el owner puede eliminar el Ward.");
            return true;
        }
        worldGuardAdapter.removeRegion(ward);
        wardService.delete(ward);
        ok(player, WARD_PREFIX, "Ward eliminado.");
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
            try {
                int delta = Integer.parseInt(args[2]);
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

    private boolean handleUpkeep(Player player, String[] args) {
        Ward ward = resolveWard(player, args);
        if (ward == null) return true;
        if (args.length >= 3 && "deposit".equalsIgnoreCase(args[1])) {
            try {
                int units = Integer.parseInt(args[2]);
                wardService.depositUpkeep(ward, units);
                ok(player, WARD_PREFIX, "Depositadas " + units + " unidades. Balance: " + ward.upkeepBalance());
            } catch (NumberFormatException e) {
                error(player, WARD_PREFIX, "Cantidad inválida: " + args[2]);
            }
            return true;
        }
        info(player, WARD_PREFIX, "Upkeep: " + ward.upkeepBalance() + " | Próximo cobro: " + ward.nextUpkeepAt());
        return true;
    }

    private boolean handleTransfer(Player player, String[] args) {
        if (args.length < 2) {
            error(player, WARD_PREFIX, "Uso: /ward transfer <jugador>");
            return true;
        }
        Ward ward = resolveWard(player, args);
        if (ward == null) return true;
        if (!ward.ownerId().equals(player.getUniqueId())) {
            error(player, WARD_PREFIX, "Solo el owner puede transferir el Ward.");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            error(player, WARD_PREFIX, "Jugador " + args[1] + " no encontrado o no está en línea.");
            return true;
        }
        wardService.transferOwnership(ward, target.getUniqueId(), OwnerType.PLAYER);
        worldGuardAdapter.syncOwner(ward);
        ok(player, WARD_PREFIX, "Ward transferido a " + target.getName() + ".");
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
                ok(player, WARD_PREFIX, "Permiso " + perm.name() + " concedido.");
            } else if ("revoke".equalsIgnoreCase(args[2])) {
                ward.revokePermission(perm);
                wardService.assignWorldGuardRegion(ward, ward.worldGuardRegionId());
                ok(player, WARD_PREFIX, "Permiso " + perm.name() + " revocado.");
            } else {
                error(player, WARD_PREFIX, "Uso: /ward permissions <perm> <grant|revoke>");
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
                info(player, WARD_PREFIX, "Este Ward no pertenece a ninguna ciudad.");
            }
            return true;
        }
        if ("annex".equalsIgnoreCase(args[1])) {
            if (!ward.ownerId().equals(player.getUniqueId())) {
                error(player, WARD_PREFIX, "Solo el owner puede anexar el Ward.");
                return true;
            }
            var optCity = cityService.findByMember(player.getUniqueId());
            if (optCity.isEmpty()) {
                error(player, WARD_PREFIX, "No eres miembro de ninguna ciudad.");
                return true;
            }
            City city = optCity.get();
            wardService.setCityMembership(ward, city.id());
            syncCityMembership(ward, city);
            ok(player, WARD_PREFIX, "Ward anexado a la ciudad " + city.name() + ".");
            return true;
        }
        if ("leave".equalsIgnoreCase(args[1])) {
            if (!ward.ownerId().equals(player.getUniqueId())) {
                error(player, WARD_PREFIX, "Solo el owner puede desvincular el Ward.");
                return true;
            }
            wardService.setCityMembership(ward, null);
            ok(player, WARD_PREFIX, "Ward desvinculado de la ciudad.");
            return true;
        }
        error(player, WARD_PREFIX, "Uso: /ward city [annex|leave]");
        return true;
    }

    private boolean handleMenu(Player player, String[] args) {
        Ward ward = resolveWard(player, args);
        if (ward == null) return true;
        openWardMenu(player, ward);
        return true;
    }

    // ── Menu opening ───────────────────────────────────────────────────────────

    public void openWardMenu(Player player, Ward ward) {
        WardViewModel vm = viewModelBuilder.build(ward, player.getUniqueId());
        var def = WardMenuBuilder.build(vm);
        MenuContext ctx = new MenuContext(player.getUniqueId(), player.getName(),
                Map.of("wardId", ward.id()));
        menuProvider.open(def, ctx);
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
        error(player, WARD_PREFIX, "No se encontró ningún Ward. Usa /ward create primero.");
        return null;
    }

    private void syncCityMembership(Ward ward, dev.dreamcraft.protection.domain.model.City city) {
        if (!worldGuardAdapter.isAvailable()) return;
        for (UUID memberId : city.members().keySet()) {
            worldGuardAdapter.addMember(ward, memberId);
        }
    }

    private String resolveName(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        return p != null ? p.getName() : uuid.toString().substring(0, 8);
    }

    // ── Help ──────────────────────────────────────────────────────────────────

    private void sendHelp(Player player) {
        player.sendMessage("§b§lDreamCraft Ward");
        player.sendMessage("§7Gestiona tu territorio Ward.");
        player.sendMessage(" ");
        player.sendMessage("§f/ward create              §7— Crear Ward en tu posición");
        player.sendMessage("§f/ward info [id]          §7— Ver información del Ward");
        player.sendMessage("§f/ward menu [id]          §7— Abrir menú del Ward");
        player.sendMessage("§f/ward score [add <n>]    §7— Ver/añadir score");
        player.sendMessage("§f/ward upkeep [deposit <n>] §7— Ver/depositar upkeep");
        player.sendMessage("§f/ward transfer <jugador> §7— Transferir ownership");
        player.sendMessage("§f/ward permissions [perm grant|revoke] §7— Gestionar permisos");
        player.sendMessage("§f/ward city [annex|leave] §7— Anexar/desvincular de ciudad");
        player.sendMessage("§f/ward delete [id]       §7— Eliminar Ward");
    }

    // ── Tab completion ─────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = List.of("create", "info", "delete", "score", "upkeep", "transfer", "permissions", "city", "menu");
            filter(subs, args[0]).forEach(completions::add);
            return completions;
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "transfer" -> filter(onlinePlayers(args[1]), args[1]).forEach(completions::add);
                case "permissions" -> {
                    for (WardPermission p : WardPermission.values()) completions.add(p.name().toLowerCase());
                    filter(completions, args[1]);
                }
                case "score" -> filter(List.of("add"), args[1]).forEach(completions::add);
                case "upkeep" -> filter(List.of("deposit"), args[1]).forEach(completions::add);
                case "city" -> filter(List.of("annex", "leave"), args[1]).forEach(completions::add);
                default -> wardIdsOf(sender).stream().filter(id -> id.startsWith(args[1])).forEach(completions::add);
            }
            return completions;
        }
        if (args.length == 3 && "permissions".equalsIgnoreCase(args[0])) {
            filter(List.of("grant", "revoke"), args[2]).forEach(completions::add);
            return completions;
        }
        return List.of();
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
