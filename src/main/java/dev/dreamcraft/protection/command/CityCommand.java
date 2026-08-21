package dev.dreamcraft.protection.command;

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

    public CityCommand(CityService cityService, WardService wardService, MenuProvider menuProvider) {
        this(cityService, wardService, menuProvider, null);
    }

    public CityCommand(CityService cityService, WardService wardService, MenuProvider menuProvider,
                       dev.dreamcraft.protection.service.CityLevelService levelService) {
        this.cityService = cityService;
        this.wardService = wardService;
        this.menuProvider = menuProvider;
        this.levelService = levelService;
        this.viewModelBuilder = new CityViewModelBuilder(this::resolveName, this::wardCountOf,
                levelService != null ? levelService::statusOf : null);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cEste comando solo puede ser usado por jugadores.");
            return true;
        }
        if (!player.hasPermission(USE_PERM)) {
            error(player, CITY_PREFIX, "No tienes permiso para usar este comando.");
            return true;
        }
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }
        try {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "create" -> handleCreate(player, args);
                case "annex" -> handleAnnex(player, args);
                case "invite" -> handleInvite(player, args);
                case "kick" -> handleKick(player, args);
                case "roles" -> handleRoles(player, args);
                case "bank" -> handleBank(player, args);
                case "policy" -> handlePolicy(player, args);
                case "menu" -> handleMenu(player);
                case "info" -> handleInfo(player, args);
                case "transfer" -> handleTransfer(player, args);
                case "delete" -> handleDelete(player, args);
                default -> {
                    error(player, CITY_PREFIX, "Subcomando desconocido: " + args[0]);
                    sendHelp(player);
                    yield true;
                }
            };
        } catch (RuntimeException e) {
            if (!handleDomainException(player, CITY_PREFIX, e)) {
                error(player, CITY_PREFIX, "Error: " + e.getMessage());
            }
            return true;
        }
    }

    // ── Subcommand handlers ────────────────────────────────────────────────────

    private boolean handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            error(player, CITY_PREFIX, "Uso: /city create <nombre>");
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
            error(player, CITY_PREFIX, "Uso: /city annex <wardId>");
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
        ok(player, CITY_PREFIX, "Ward anexado a la ciudad " + city.name() + ".");
        return true;
    }

    private boolean handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            error(player, CITY_PREFIX, "Uso: /city invite <jugador>");
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
            error(player, CITY_PREFIX, "Uso: /city kick <jugador>");
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
            ok(player, CITY_PREFIX, "Miembro expulsado de la ciudad.");
        } else {
            warn(player, CITY_PREFIX, "Ese jugador no es miembro de la ciudad.");
        }
        return true;
    }

    private boolean handleRoles(Player player, String[] args) {
        if (args.length < 3) {
            error(player, CITY_PREFIX, "Uso: /city roles <jugador> <rol>");
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
            error(player, CITY_PREFIX, "Uso: /city bank <deposit|withdraw> <monto> §8(admin)");
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
            error(player, CITY_PREFIX, "Uso: /city bank <deposit|withdraw> <monto>");
        }
        return true;
    }

    private boolean handlePolicy(Player player, String[] args) {
        if (args.length < 3) {
            error(player, CITY_PREFIX, "Uso: /city policy set <politica> <on|off>");
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
            error(player, CITY_PREFIX, "Uso: /city policy set <politica> <on|off>");
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
            error(player, CITY_PREFIX, "Uso: /city transfer <jugador>");
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
        for (Ward ward : wardService.findByCity(city.id())) {
            wardService.setCityMembership(ward, null);
        }
        cityService.delete(city);
        ok(player, CITY_PREFIX, "Ciudad " + city.name() + " eliminada.");
        return true;
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
            error(player, CITY_PREFIX, "No eres miembro de ninguna ciudad. Usa /city create <nombre>.");
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
        player.sendMessage("§6§lDreamCraft Ciudad");
        player.sendMessage("§7Gestiona tu ciudad.");
        player.sendMessage(" ");
        player.sendMessage("§f/city create <nombre>     §7— Crear ciudad");
        player.sendMessage("§f/city info [nombre]      §7— Ver información");
        player.sendMessage("§f/city menu                §7— Abrir menú de ciudad");
        player.sendMessage("§f/city annex <wardId>      §7— Anexar un Ward");
        player.sendMessage("§f/city invite <jugador>    §7— Invitar residente");
        player.sendMessage("§f/city kick <jugador>      §7— Expulsar residente");
        player.sendMessage("§f/city roles <jugador> <rol> §7— Asignar rol");
        player.sendMessage("§f/city bank <deposit|withdraw> <monto> §7— Ajuste admin de créditos");
        player.sendMessage("§f/city policy set <politica> <on|off> §7— Cambiar política");
        player.sendMessage("§f/city transfer <jugador>  §7— Transferir gobernanza");
        player.sendMessage("§f/city delete              §7— Eliminar ciudad");
        player.sendMessage("§7Roles: GOVERNOR, COUNCIL, CITIZEN, ALLY");
    }

    // ── Tab completion ─────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = List.of("create", "annex", "invite", "kick", "roles", "bank", "policy", "menu", "info", "transfer", "delete");
            filter(subs, args[0]).forEach(completions::add);
            return completions;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
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
