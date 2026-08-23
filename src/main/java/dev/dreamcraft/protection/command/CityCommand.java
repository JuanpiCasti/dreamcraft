package dev.dreamcraft.protection.command;

import dev.dreamcraft.protection.config.CommandNames;

import dev.dreamcraft.protection.domain.model.City;
import dev.dreamcraft.protection.domain.model.CityPolicy;
import dev.dreamcraft.protection.domain.model.CityRole;
import dev.dreamcraft.protection.domain.model.Ward;
import dev.dreamcraft.protection.domain.service.CityService;
import dev.dreamcraft.protection.domain.service.WardService;
import dev.dreamcraft.protection.presentation.MenuContext;
import dev.dreamcraft.protection.presentation.MenuProvider;
import dev.dreamcraft.protection.presentation.menu.CityMenuBuilder;
import dev.dreamcraft.protection.presentation.viewmodel.CityViewModel;
import dev.dreamcraft.protection.presentation.viewmodel.CityViewModelBuilder;
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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static dev.dreamcraft.protection.command.CommandMessages.*;

/**
 * Handles all /city (alias: /ciudad) subcommands.
 *
 * <p>Delegates all business logic to {@link CityService} and opens menus via
 * {@link MenuProvider} using pre-computed {@link CityViewModel}s.
 */
public final class CityCommand implements CommandExecutor, TabCompleter {

    private static final String USE_PERM = "dreamcraft.city.use";
    private static final String ADMIN_PERM = "dreamcraft.city.admin";

    private final CityService cityService;
    private final WardService wardService;
    private final MenuProvider menuProvider;
    /** Optional: computed city levels (wards/members/wealth). */
    private final dev.dreamcraft.protection.service.CityLevelService levelService;
    private final CityViewModelBuilder viewModelBuilder;
    /** Optional: WG adapter for region member sync on annex/kick/delete. */
    private final dev.dreamcraft.protection.integration.worldguard.WorldGuardAdapter worldGuardAdapter;
    /** Per-server subcommand aliases/enabled flags. */
    private final dev.dreamcraft.protection.config.CommandOptions options;
    /** Single source of truth for dispatch + tab completion. */
    private final CommandRegistry registry;
    /** Admin cities GUI opener (/city admin menu); wired by the plugin to the dispatcher. */
    private java.util.function.Consumer<Player> adminMenuOpener = player -> { };

    /** Wires the admin cities GUI opener (stateless overview at page 0). */
    public void setAdminMenuOpener(java.util.function.Consumer<Player> opener) {
        this.adminMenuOpener = opener != null ? opener : player -> { };
    }

    public CityCommand(CityService cityService, WardService wardService, MenuProvider menuProvider) {
        this(cityService, wardService, menuProvider, null);
    }

    public CityCommand(CityService cityService, WardService wardService, MenuProvider menuProvider,
                       dev.dreamcraft.protection.service.CityLevelService levelService) {
        this(cityService, wardService, menuProvider, levelService, null,
                dev.dreamcraft.protection.config.CommandOptions.empty());
    }

    public CityCommand(CityService cityService, WardService wardService, MenuProvider menuProvider,
                       dev.dreamcraft.protection.service.CityLevelService levelService,
                       dev.dreamcraft.protection.integration.worldguard.WorldGuardAdapter worldGuardAdapter) {
        this(cityService, wardService, menuProvider, levelService, worldGuardAdapter,
                dev.dreamcraft.protection.config.CommandOptions.empty());
    }

    public CityCommand(CityService cityService, WardService wardService, MenuProvider menuProvider,
                       dev.dreamcraft.protection.service.CityLevelService levelService,
                       dev.dreamcraft.protection.integration.worldguard.WorldGuardAdapter worldGuardAdapter,
                       dev.dreamcraft.protection.config.CommandOptions options) {
        this.cityService = cityService;
        this.wardService = wardService;
        this.menuProvider = menuProvider;
        this.levelService = levelService;
        this.worldGuardAdapter = worldGuardAdapter;
        this.options = options;
        this.viewModelBuilder = new CityViewModelBuilder(this::resolveName, this::wardCountOf,
                levelService != null ? levelService::statusOf : null);
        this.registry = buildRegistry();
    }

    /**
     * Builds the subcommand table: canonical names here, aliases merged from
     * config.yml (commands.city.subcommands.&lt;name&gt;.aliases).
     */
    private CommandRegistry buildRegistry() {
        return new CommandRegistry("city")
                .register(SubcommandSpec.of("create", this::handleCreate)
                        .withAliases(options.aliases("city", "create")))
                .register(SubcommandSpec.of("annex", this::handleAnnex)
                        .withAliases(options.aliases("city", "annex")))
                .register(SubcommandSpec.of("invite", this::handleInvite)
                        .withAliases(options.aliases("city", "invite")))
                .register(SubcommandSpec.of("kick", this::handleKick)
                        .withAliases(options.aliases("city", "kick")))
                .register(SubcommandSpec.of("roles", this::handleRoles)
                        .withAliases(options.aliases("city", "roles")))
                .register(SubcommandSpec.admin("bank", this::handleBank)
                        .withAliases(options.aliases("city", "bank")))
                .register(SubcommandSpec.of("policy", this::handlePolicy)
                        .withAliases(options.aliases("city", "policy")))
                .register(SubcommandSpec.of("menu", (p, a) -> handleMenu(p))
                        .withAliases(options.aliases("city", "menu")))
                .register(SubcommandSpec.of("info", this::handleInfo)
                        .withAliases(options.aliases("city", "info")))
                .register(SubcommandSpec.of("transfer", this::handleTransfer)
                        .withAliases(options.aliases("city", "transfer")))
                .register(SubcommandSpec.of("delete", this::handleDelete)
                        .withAliases(options.aliases("city", "delete")))
                .register(SubcommandSpec.admin("admin", this::handleAdmin)
                        .withAliases(options.aliases("city", "admin")));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return handleConsole(sender, args);
        }
        if (!player.hasPermission(USE_PERM)) {
            error(player, CITY_PREFIX, tr("common.no-permission", "No tienes permiso para usar este comando."));
            return true;
        }
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }
        SubcommandSpec spec = registry.resolve(args[0]);
        if (spec == null || !options.isEnabled(registry.root(), spec.name())) {
            error(player, CITY_PREFIX, tr("common.unknown-subcommand", "Subcomando desconocido: {sub}", "sub", args[0]));
            sendHelp(player);
            return true;
        }
        try {
            return spec.execute(player, args);
        } catch (RuntimeException e) {
            if (!handleDomainException(player, CITY_PREFIX, e)) {
                error(player, CITY_PREFIX, tr("common.error", "Error: {message}", "message", e.getMessage()));
            }
            return true;
        }
    }

    // ── Subcommand handlers ────────────────────────────────────────────────────

    /**
     * Console/RCON surface (SSH-friendly): only {@code admin delete} is
     * reachable — a location-free forced city deletion by exact name.
     * Everything else keeps the players-only contract.
     */
    private boolean handleConsole(CommandSender sender, String[] args) {
        boolean whitelisted = args.length >= 2
                && args[0].equalsIgnoreCase("admin")
                && args[1].equalsIgnoreCase("delete");
        if (!whitelisted) {
            error(sender, CITY_PREFIX, tr("common.players-only", "§cEste comando solo puede ser usado por jugadores."));
            return true;
        }
        if (!sender.hasPermission(ADMIN_PERM)) {
            error(sender, CITY_PREFIX, tr("common.no-permission", "No tienes permiso para usar este comando."));
            return true;
        }
        String raw = args.length >= 3
                ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)).trim()
                : null;
        return adminDelete(sender, raw);
    }

    /** /city admin — staff surface: overview GUI and forced deletion. */
    private boolean handleAdmin(Player player, String[] args) {
        if (!player.hasPermission(ADMIN_PERM)) {
            error(player, CITY_PREFIX, tr("common.no-permission-action", "&cNo tienes permiso para este comando."));
            return true;
        }
        if (args.length < 2) {
            error(player, CITY_PREFIX, "Uso: " + CommandNames.cmd("city",
                    "admin menu &8|&f admin delete <nombre>"));
            return true;
        }
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "menu" -> {
                adminMenuOpener.accept(player);
                yield true;
            }
            case "delete" -> adminDelete(player, args.length >= 3
                    ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)).trim()
                    : null);
            default -> {
                error(player, CITY_PREFIX, "Subcomando admin desconocido: " + args[1]);
                yield true;
            }
        };
    }

    /** Resolves a city by EXACT name match (case-insensitive). */
    private City findCityByExactName(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return cityService.findAll().stream()
                .filter(c -> c.name().equalsIgnoreCase(raw))
                .findFirst()
                .orElse(null);
    }

    /**
     * Forced deletion shared by the player and console admin routes: mirrors
     * the governor delete flow (disannex wards → re-project regions → delete).
     */
    private boolean adminDelete(CommandSender feedback, String raw) {
        if (raw == null || raw.isBlank()) {
            error(feedback, CITY_PREFIX, CommandNames.cmd("city", "admin delete <nombre>"));
            return true;
        }
        City city = findCityByExactName(raw);
        if (city == null) {
            error(feedback, CITY_PREFIX, "Ciudad '" + raw + "' no encontrada.");
            return true;
        }
        deleteCityAndDisannex(city);
        ok(feedback, CITY_PREFIX, "Ciudad " + city.name() + " eliminada por admin.");
        return true;
    }
    private boolean handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            error(player, CITY_PREFIX, CommandNames.cmd("city", "create <nombre>"));
            return true;
        }
        String name = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        City city = cityService.createCity(player.getUniqueId(), name);
        ok(player, CITY_PREFIX, "Ciudad '" + city.name() + "' creada. Eres el Gobernador.");
        title(player, "Ciudad Creada", city.name(), NamedTextColor.GOLD);
        return true;
    }

    private boolean handleAnnex(Player player, String[] args) {
        if (args.length < 2) {
            error(player, CITY_PREFIX, CommandNames.cmd("city", "annex <idNucleo>"));
            return true;
        }
        UUID wardId;
        try {
            wardId = UUID.fromString(args[1]);
        } catch (IllegalArgumentException e) {
            error(player, CITY_PREFIX, "Ward ID inválido: " + args[1]);
            return true;
        }
        Ward ward = wardService.findById(wardId).orElse(null);
        if (ward == null) {
            error(player, CITY_PREFIX, "Ward no encontrado.");
            return true;
        }
        var optCity = cityService.findByMember(player.getUniqueId());
        if (optCity.isEmpty()) {
            error(player, CITY_PREFIX, "No eres miembro de ninguna ciudad.");
            return true;
        }
        City city = optCity.get();
        if (!city.isGovernor(player.getUniqueId()) && !city.isMember(player.getUniqueId())) {
            error(player, CITY_PREFIX, "No tienes permiso para anexar Wards a esta ciudad.");
            return true;
        }
        wardService.setCityMembership(ward, city.id());
        // Project domain membership: all city residents gain region access
        dev.dreamcraft.protection.service.WardAccessSync.project(ward, cityService, worldGuardAdapter);
        ok(player, CITY_PREFIX, "Ward anexado a la ciudad " + city.name() + ".");
        return true;
    }

    private boolean handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            error(player, CITY_PREFIX, CommandNames.cmd("city", "invite <jugador>"));
            return true;
        }
        City city = resolveCity(player);
        if (city == null) return true;
        if (!canManageResidents(city, player)) {
            error(player, CITY_PREFIX, "No tienes permiso para invitar miembros.");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            error(player, CITY_PREFIX, "Jugador " + args[1] + " no encontrado o no está en línea.");
            return true;
        }
        boolean added = cityService.addMember(city, target.getUniqueId());
        if (added) {
            // New resident gains access to every annexed ward's region
            dev.dreamcraft.protection.service.WardAccessSync.projectAll(
                    wardService.findByCity(city.id()), cityService, worldGuardAdapter);
            ok(player, CITY_PREFIX, target.getName() + " invitado a la ciudad.");
            target.sendMessage(CITY_PREFIX.append(
                    Component.text("Fuiste invitado a la ciudad " + city.name() + ".", NamedTextColor.GREEN)));
        } else {
            warn(player, CITY_PREFIX, target.getName() + " ya es miembro de la ciudad.");
        }
        return true;
    }

    private boolean handleKick(Player player, String[] args) {
        if (args.length < 2) {
            error(player, CITY_PREFIX, CommandNames.cmd("city", "kick <jugador>"));
            return true;
        }
        City city = resolveCity(player);
        if (city == null) return true;
        if (!canManageResidents(city, player)) {
            error(player, CITY_PREFIX, "No tienes permiso para expulsar miembros.");
            return true;
        }
        UUID targetId = resolvePlayerId(args[1]);
        if (targetId == null) {
            error(player, CITY_PREFIX, "Jugador " + args[1] + " no encontrado.");
            return true;
        }
        if (city.isGovernor(targetId)) {
            error(player, CITY_PREFIX, "No puedes expulsar al Gobernador.");
            return true;
        }
        boolean removed = cityService.removeMember(city, targetId);
        if (removed) {
            // Re-project: the ex-member loses access to every annexed ward's region
            dev.dreamcraft.protection.service.WardAccessSync.projectAll(
                    wardService.findByCity(city.id()), cityService, worldGuardAdapter);
            ok(player, CITY_PREFIX, "Miembro expulsado de la ciudad.");
        } else {
            warn(player, CITY_PREFIX, "Ese jugador no es miembro de la ciudad.");
        }
        return true;
    }

    private boolean handleRoles(Player player, String[] args) {
        if (args.length < 3) {
            error(player, CITY_PREFIX, CommandNames.cmd("city", "roles <jugador> <rol>"));
            return true;
        }
        City city = resolveCity(player);
        if (city == null) return true;
        if (!city.isGovernor(player.getUniqueId())) {
            error(player, CITY_PREFIX, "Solo el Gobernador puede asignar roles.");
            return true;
        }
        UUID targetId = resolvePlayerId(args[1]);
        if (targetId == null) {
            error(player, CITY_PREFIX, "Jugador " + args[1] + " no encontrado.");
            return true;
        }
        CityRole role;
        try {
            role = CityRole.valueOf(args[2].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            error(player, CITY_PREFIX, "Rol inválido: " + args[2]
                    + " (GOVERNOR, COUNCIL, CITIZEN, ALLY)");
            return true;
        }
        if (role == CityRole.GOVERNOR) {
            boolean transferred = cityService.transferGovernorship(city, targetId);
            if (transferred) {
                ok(player, CITY_PREFIX, "Gobernanza transferida a " + args[1] + ".");
            } else {
                error(player, CITY_PREFIX, "No se pudo transferir la gobernanza.");
            }
            return true;
        }
        boolean changed = cityService.setRole(city, targetId, role);
        if (changed) {
            ok(player, CITY_PREFIX, "Rol de " + args[1] + " establecido a " + role.name() + ".");
        } else {
            warn(player, CITY_PREFIX, args[1] + " no es miembro de la ciudad.");
        }
        return true;
    }

    /**
     * /city bank — admin-only raw credit adjustment (debug).
     * The real treasury is the physical vault: click "Tesoro" in the city menu
     * or use this command as an admin to correct balances manually.
     */
    private boolean handleBank(Player player, String[] args) {
        if (!player.hasPermission("dreamcraft.ward.admin")
                && !player.hasPermission("dreamcraft.protection.admin")) {
            error(player, CITY_PREFIX, "El tesoro se gestiona con ítems: abrilo desde el menú de la ciudad.");
            info(player, CITY_PREFIX, "Menú de ciudad → slot §fTesoro§7 (requiere rol Council o superior).");
            return true;
        }
        if (args.length < 3) {
            error(player, CITY_PREFIX, CommandNames.cmd("city", "bank <deposit|withdraw> <monto> §8(admin)"));
            return true;
        }
        City city = resolveCity(player);
        if (city == null) return true;
        CityRole role = city.roleOf(player.getUniqueId());
        if (role != CityRole.COUNCIL && !city.isGovernor(player.getUniqueId())) {
            error(player, CITY_PREFIX, "Solo Council o Gobernador pueden gestionar el tesoro.");
            return true;
        }
        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            error(player, CITY_PREFIX, "Monto inválido: " + args[2]);
            return true;
        }
        if (amount <= 0) {
            error(player, CITY_PREFIX, "El monto debe ser positivo.");
            return true;
        }
        if ("deposit".equalsIgnoreCase(args[1])) {
            cityService.depositTreasury(city, amount);
            ok(player, CITY_PREFIX, "Depositadas " + amount + " unidades. Tesoro: " + city.treasury());
        } else if ("withdraw".equalsIgnoreCase(args[1])) {
            boolean withdrawn = cityService.withdrawTreasury(city, amount);
            if (withdrawn) {
                ok(player, CITY_PREFIX, "Retiradas " + amount + " unidades. Tesoro: " + city.treasury());
            } else {
                error(player, CITY_PREFIX, "Fondos insuficientes. Tesoro actual: " + city.treasury());
            }
        } else {
            error(player, CITY_PREFIX, CommandNames.cmd("city", "bank <deposit|withdraw> <monto>"));
        }
        return true;
    }

    private boolean handlePolicy(Player player, String[] args) {
        if (args.length < 3) {
            error(player, CITY_PREFIX, CommandNames.cmd("city", "policy set <politica> <on|off>"));
            info(player, CITY_PREFIX, "Políticas: " + policyNames());
            return true;
        }
        City city = resolveCity(player);
        if (city == null) return true;
        if (!city.isGovernor(player.getUniqueId())) {
            error(player, CITY_PREFIX, "Solo el Gobernador puede cambiar políticas.");
            return true;
        }
        if (!"set".equalsIgnoreCase(args[1])) {
            error(player, CITY_PREFIX, CommandNames.cmd("city", "policy set <politica> <on|off>"));
            return true;
        }
        CityPolicy policy;
        try {
            policy = CityPolicy.valueOf(args[2].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            error(player, CITY_PREFIX, "Política inválida: " + args[2] + " — " + policyNames());
            return true;
        }
        boolean enable = args.length < 4 || "on".equalsIgnoreCase(args[3]) || "true".equalsIgnoreCase(args[3]);
        cityService.setPolicy(city, policy, enable);
        ok(player, CITY_PREFIX, "Política " + policy.name() + " " + (enable ? "activada" : "desactivada") + ".");
        return true;
    }

    private boolean handleMenu(Player player) {
        City city = resolveCity(player);
        if (city == null) return true;
        openCityMenu(player, city);
        return true;
    }

    private boolean handleInfo(Player player, String[] args) {
        City city;
        if (args.length >= 2) {
            String name = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            city = cityService.findByName(name).orElse(null);
            if (city == null) {
                error(player, CITY_PREFIX, "Ciudad '" + name + "' no encontrada.");
                return true;
            }
        } else {
            city = resolveCity(player);
            if (city == null) return true;
        }
        info(player, CITY_PREFIX, "Nombre: " + city.name());
        info(player, CITY_PREFIX, "Gobernador: " + resolveName(city.governorId()));
        info(player, CITY_PREFIX, "Miembros: " + city.members().size());
          info(player, CITY_PREFIX, "Tesoro: " + city.treasury() + " | Score: " + city.cityScore());
        info(player, CITY_PREFIX, "Wards: " + wardCountOf(city));
        if (levelService != null) {
            var lvl = levelService.statusOf(city);
            info(player, CITY_PREFIX, "Nivel: " + lvl.levelName()
                    + (lvl.maxed() ? " §8(máximo)" : " §8→ siguiente: " + lvl.nextLevelName()));
        }
        info(player, CITY_PREFIX, "Políticas: " + city.policies());
        return true;
    }

    private boolean handleTransfer(Player player, String[] args) {
        if (args.length < 2) {
            error(player, CITY_PREFIX, CommandNames.cmd("city", "transfer <jugador>"));
            return true;
        }
        City city = resolveCity(player);
        if (city == null) return true;
        if (!city.isGovernor(player.getUniqueId())) {
            error(player, CITY_PREFIX, "Solo el Gobernador puede transferir la gobernanza.");
            return true;
        }
        UUID targetId = resolvePlayerId(args[1]);
        if (targetId == null) {
            error(player, CITY_PREFIX, "Jugador " + args[1] + " no encontrado.");
            return true;
        }
        boolean transferred = cityService.transferGovernorship(city, targetId);
        if (transferred) {
            ok(player, CITY_PREFIX, "Gobernanza transferida a " + args[1] + ".");
        } else {
            error(player, CITY_PREFIX, args[1] + " debe ser miembro de la ciudad.");
        }
        return true;
    }

    private boolean handleDelete(Player player, String[] args) {
        City city = resolveCity(player);
        if (city == null) return true;
        if (!city.isGovernor(player.getUniqueId()) && !player.hasPermission(ADMIN_PERM)) {
            error(player, CITY_PREFIX, "Solo el Gobernador puede eliminar la ciudad.");
            return true;
        }
        deleteCityAndDisannex(city);
        ok(player, CITY_PREFIX, "Ciudad " + city.name() + " eliminada.");
        return true;
    }

    /**
     * Shared city teardown (governor delete + admin variant): disassociate all
     * wards from the city, then re-project — their region member lists collapse
     * to empty (city-granted access fully revoked) — and delete the aggregate.
     */
    private void deleteCityAndDisannex(City city) {
        for (Ward ward : wardService.findByCity(city.id())) {
            wardService.setCityMembership(ward, null);
            dev.dreamcraft.protection.service.WardAccessSync.project(ward, cityService, worldGuardAdapter);
        }
        cityService.delete(city);
    }

    // ── Menu opening ───────────────────────────────────────────────────────────

    public void openCityMenu(Player player, City city) {
        CityViewModel vm = viewModelBuilder.build(city, player.getUniqueId());
        var def = CityMenuBuilder.build(vm);
        MenuContext ctx = new MenuContext(player.getUniqueId(), player.getName(),
                Map.of("cityId", city.id()));
        menuProvider.open(def, ctx);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private City resolveCity(Player player) {
        var optCity = cityService.findByMember(player.getUniqueId());
        if (optCity.isEmpty()) {
            error(player, CITY_PREFIX, "No eres miembro de ninguna Matriz. Usa " + CommandNames.cmd("city", "create <nombre>") + ".");
            return null;
        }
        return optCity.get();
    }

    private boolean canManageResidents(City city, Player player) {
        CityRole role = city.roleOf(player.getUniqueId());
        return city.isGovernor(player.getUniqueId()) || role == CityRole.COUNCIL;
    }

    private UUID resolvePlayerId(String nameOrUuid) {
        Player p = Bukkit.getPlayerExact(nameOrUuid);
        if (p != null) return p.getUniqueId();
        try {
            return UUID.fromString(nameOrUuid);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String resolveName(UUID uuid) {
        return CommandMessages.resolveName(uuid);
    }

    private int wardCountOf(City city) {
        return wardService.findByCity(city.id()).size();
    }

    private String policyNames() {
        return java.util.Arrays.stream(CityPolicy.values())
                .map(Enum::name).collect(Collectors.joining(", "));
    }

    // ── Help ──────────────────────────────────────────────────────────────────

    private void sendHelp(Player player) {
        helpBlock(player, "help.city");
        if (player.hasPermission(ADMIN_PERM)) {
            helpAdminSection(player, "help.city.admin");
        }
    }

    // ── Tab completion ─────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        boolean admin = sender.hasPermission(ADMIN_PERM);
        if (args.length == 1) {
            // Admin-only subcommands stay hidden from non-admin senders
            filter(registry.completionTokens(args[0], spec -> !spec.isAdminOnly() || admin), args[0])
                    .forEach(completions::add);
            return completions;
        }
        SubcommandSpec resolved = registry.resolve(args[0]);
        String sub = (resolved != null ? resolved.name() : args[0]).toLowerCase(Locale.ROOT);
        if ("admin".equals(sub)) {
            if (!admin) return completions;
            if (args.length == 2) {
                filter(List.of("menu", "delete"), args[1]).forEach(completions::add);
            } else if (args.length == 3 && "delete".equalsIgnoreCase(args[1])) {
                filter(cityNames(), args[2]).forEach(completions::add);
            }
            return completions;
        }
        if (args.length == 2) {
            switch (sub) {
                case "invite", "kick", "transfer" -> filter(onlinePlayers(args[1]), args[1]).forEach(completions::add);
                case "roles" -> filter(onlinePlayers(args[1]), args[1]).forEach(completions::add);
                case "bank" -> filter(List.of("deposit", "withdraw"), args[1]).forEach(completions::add);
                case "policy" -> filter(List.of("set"), args[1]).forEach(completions::add);
                case "info" -> filter(cityNames(), args[1]).forEach(completions::add);
                default -> {}
            }
            return completions;
        }
        if (args.length == 3 && "roles".equalsIgnoreCase(sub)) {
            for (CityRole r : CityRole.values()) completions.add(r.name().toLowerCase());
            filter(completions, args[2]);
            return completions;
        }
        if (args.length == 3 && "policy".equalsIgnoreCase(sub)) {
            for (CityPolicy p : CityPolicy.values()) completions.add(p.name().toLowerCase());
            filter(completions, args[2]);
            return completions;
        }
        if (args.length == 4 && "policy".equalsIgnoreCase(sub)) {
            filter(List.of("on", "off"), args[3]).forEach(completions::add);
            return completions;
        }
        return List.of();
    }

    private List<String> onlinePlayers(String prefix) {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT)))
                .toList();
    }

    private List<String> cityNames() {
        return cityService.findAll().stream().map(City::name).toList();
    }

    private List<String> filter(List<String> options, String prefix) {
        return options.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT)))
                .toList();
    }
}
