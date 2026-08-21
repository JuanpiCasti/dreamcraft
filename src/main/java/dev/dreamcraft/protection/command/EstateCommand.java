package dev.dreamcraft.protection.command;

import dev.dreamcraft.protection.domain.model.Estate;
import dev.dreamcraft.protection.domain.service.EstateService;
import dev.dreamcraft.protection.presentation.MenuContext;
import dev.dreamcraft.protection.presentation.MenuProvider;
import dev.dreamcraft.protection.presentation.menu.EstateMenuBuilder;
import dev.dreamcraft.protection.presentation.viewmodel.EstateViewModel;
import dev.dreamcraft.protection.presentation.viewmodel.EstateViewModelBuilder;
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

import static dev.dreamcraft.protection.command.CommandMessages.*;

/**
 * Handles all /estate (alias: /grupo) subcommands.
 *
 * <p>Delegates all business logic to {@link EstateService} and opens menus via
 * {@link MenuProvider} using pre-computed {@link EstateViewModel}s.
 */
public final class EstateCommand implements CommandExecutor, TabCompleter {

    private static final String USE_PERM = "dreamcraft.estate.use";

    private final EstateService estateService;
    private final MenuProvider menuProvider;
    private final EstateViewModelBuilder viewModelBuilder;

    public EstateCommand(EstateService estateService, MenuProvider menuProvider) {
        this.estateService = estateService;
        this.menuProvider = menuProvider;
        this.viewModelBuilder = new EstateViewModelBuilder(this::resolveName);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cEste comando solo puede ser usado por jugadores.");
            return true;
        }
        if (!player.hasPermission(USE_PERM)) {
            error(player, ESTATE_PREFIX, "No tienes permiso para usar este comando.");
            return true;
        }
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }
        try {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "create" -> handleCreate(player, args);
                case "discover" -> handleDiscover(player, args);
                case "admin" -> handleAdmin(player, args);
                case "invite" -> handleInvite(player, args);
                case "join" -> handleJoin(player, args);
                case "leave" -> handleLeave(player, args);
                case "disband" -> handleDisband(player, args);
                case "start" -> handleStart(player, args);
                case "transfer" -> handleTransfer(player, args);
                case "info" -> handleInfo(player, args);
                case "menu" -> handleMenu(player, args);
                default -> {
                    error(player, ESTATE_PREFIX, "Subcomando desconocido: " + args[0]);
                    sendHelp(player);
                    yield true;
                }
            };
        } catch (RuntimeException e) {
            if (!handleDomainException(player, ESTATE_PREFIX, e)) {
                error(player, ESTATE_PREFIX, "Error: " + e.getMessage());
            }
            return true;
        }
    }

    // ── Subcommand handlers ────────────────────────────────────────────────────

    private boolean handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            error(player, ESTATE_PREFIX, "Uso: /estate create <id>");
            return true;
        }
        String name = args[1];
        Estate estate = estateService.createEstate(player.getUniqueId(), name, null, null, false);
        ok(player, ESTATE_PREFIX, "Estate '" + estate.name() + "' creado.");
        return true;
    }

    private boolean handleDiscover(Player player, String[] args) {
        if (args.length < 2) {
            error(player, ESTATE_PREFIX, "Uso: /estate discover <tipo>");
            return true;
        }
        String adventureType = args[1];
        // Discover creates an adventure-linked, non-persistent estate
        String adventureId = "adv-" + adventureType.toLowerCase(Locale.ROOT);
        Estate estate = estateService.createEstate(
                player.getUniqueId(), "Aventura: " + adventureType, adventureId, null, false);
        ok(player, ESTATE_PREFIX, "Estate de aventura '" + estate.name() + "' descubierto.");
        openEstateMenu(player, estate);
        return true;
    }

    private boolean handleAdmin(Player player, String[] args) {
        if (!player.hasPermission("dreamcraft.protection.admin")) {
            error(player, ESTATE_PREFIX, "No tienes permiso para este comando.");
            return true;
        }
        if (args.length < 4 || !"create".equalsIgnoreCase(args[1])) {
            error(player, ESTATE_PREFIX, "Uso: /estate admin create <id> <tipo> [radio]");
            return true;
        }
        String id = args[2];
        String type = args[3];
        String adventureId = "admin-" + type.toLowerCase(Locale.ROOT);
        Estate estate = estateService.createEstate(
                player.getUniqueId(), id, adventureId, null, true);
        ok(player, ESTATE_PREFIX, "Estate admin '" + estate.name() + "' creado (persistente).");
        return true;
    }

    private boolean handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            error(player, ESTATE_PREFIX, "Uso: /estate invite <jugador>");
            return true;
        }
        Estate estate = resolveEstate(player, args);
        if (estate == null) return true;
        if (!estate.isOwner(player.getUniqueId())) {
            error(player, ESTATE_PREFIX, "Solo el owner puede invitar miembros.");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            error(player, ESTATE_PREFIX, "Jugador " + args[1] + " no encontrado o no está en línea.");
            return true;
        }
        boolean added = estateService.addMember(estate, target.getUniqueId());
        if (added) {
            ok(player, ESTATE_PREFIX, target.getName() + " invitado al Estate.");
            target.sendMessage(ESTATE_PREFIX.append(
                    Component.text("Fuiste invitado al Estate " + estate.name() + ".", NamedTextColor.GREEN)));
        } else {
            warn(player, ESTATE_PREFIX, target.getName() + " ya es miembro del Estate.");
        }
        return true;
    }

    private boolean handleJoin(Player player, String[] args) {
        if (args.length < 2) {
            error(player, ESTATE_PREFIX, "Uso: /estate join <id>");
            return true;
        }
        UUID estateId;
        try {
            estateId = UUID.fromString(args[1]);
        } catch (IllegalArgumentException e) {
            error(player, ESTATE_PREFIX, "Estate ID inválido: " + args[1]);
            return true;
        }
        Estate estate = estateService.findById(estateId).orElse(null);
        if (estate == null) {
            error(player, ESTATE_PREFIX, "Estate no encontrado.");
            return true;
        }
        if (estate.isMember(player.getUniqueId())) {
            warn(player, ESTATE_PREFIX, "Ya eres miembro del Estate.");
            return true;
        }
        estateService.addMember(estate, player.getUniqueId());
        ok(player, ESTATE_PREFIX, "Te uniste al Estate " + estate.name() + ".");
        return true;
    }

    private boolean handleLeave(Player player, String[] args) {
        Estate estate = resolveEstate(player, args);
        if (estate == null) return true;
        if (!estate.isMember(player.getUniqueId())) {
            error(player, ESTATE_PREFIX, "No eres miembro del Estate.");
            return true;
        }
        if (estate.isOwner(player.getUniqueId())) {
            error(player, ESTATE_PREFIX, "El owner no puede salir. Usa /estate disband o /estate transfer.");
            return true;
        }
        estateService.removeMember(estate, player.getUniqueId());
        ok(player, ESTATE_PREFIX, "Saliste del Estate " + estate.name() + ".");
        return true;
    }

    private boolean handleDisband(Player player, String[] args) {
        Estate estate = resolveEstate(player, args);
        if (estate == null) return true;
        if (!estate.isOwner(player.getUniqueId())) {
            error(player, ESTATE_PREFIX, "Solo el owner puede disolver el Estate.");
            return true;
        }
        estateService.delete(estate);
        ok(player, ESTATE_PREFIX, "Estate " + estate.name() + " disuelto.");
        return true;
    }

    private boolean handleStart(Player player, String[] args) {
        Estate estate = resolveEstate(player, args);
        if (estate == null) return true;
        if (!estate.isOwner(player.getUniqueId())) {
            error(player, ESTATE_PREFIX, "Solo el owner puede iniciar la instancia.");
            return true;
        }
        String instanceId = "inst-" + estate.id().toString().substring(0, 8);
        boolean started = estateService.startInstance(estate, instanceId);
        if (started) {
            ok(player, ESTATE_PREFIX, "Instancia " + instanceId + " iniciada.");
            title(player, "Instancia Iniciada", estate.name(), NamedTextColor.LIGHT_PURPLE);
        } else {
            warn(player, ESTATE_PREFIX, "El Estate ya tiene una instancia activa.");
        }
        return true;
    }

    private boolean handleTransfer(Player player, String[] args) {
        if (args.length < 2) {
            error(player, ESTATE_PREFIX, "Uso: /estate transfer <jugador>");
            return true;
        }
        Estate estate = resolveEstate(player, args);
        if (estate == null) return true;
        if (!estate.isOwner(player.getUniqueId())) {
            error(player, ESTATE_PREFIX, "Solo el owner puede transferir el Estate.");
            return true;
        }
        UUID targetId;
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target != null) {
            targetId = target.getUniqueId();
        } else {
            try {
                targetId = UUID.fromString(args[1]);
            } catch (IllegalArgumentException e) {
                error(player, ESTATE_PREFIX, "Jugador " + args[1] + " no encontrado.");
                return true;
            }
        }
        boolean transferred = estateService.transferOwnership(estate, targetId);
        if (transferred) {
            ok(player, ESTATE_PREFIX, "Estate transferido a " + args[1] + ".");
        } else {
            error(player, ESTATE_PREFIX, args[1] + " debe ser miembro del Estate.");
        }
        return true;
    }

    private boolean handleInfo(Player player, String[] args) {
        Estate estate = resolveEstate(player, args);
        if (estate == null) return true;
        info(player, ESTATE_PREFIX, "Nombre: " + estate.name());
        info(player, ESTATE_PREFIX, "Owner: " + resolveName(estate.ownerId()));
        info(player, ESTATE_PREFIX, "Miembros: " + estate.members().size());
        info(player, ESTATE_PREFIX, "Aventura: " + (estate.adventureId() != null ? estate.adventureId() : "N/A"));
        info(player, ESTATE_PREFIX, "Instancia: " + (estate.instanceId() != null ? estate.instanceId() : "Inactiva"));
        info(player, ESTATE_PREFIX, "Persistente: " + (estate.persistent() ? "Sí" : "No"));
        return true;
    }

    private boolean handleMenu(Player player, String[] args) {
        Estate estate = resolveEstate(player, args);
        if (estate == null) return true;
        openEstateMenu(player, estate);
        return true;
    }

    // ── Menu opening ───────────────────────────────────────────────────────────

    public void openEstateMenu(Player player, Estate estate) {
        EstateViewModel vm = viewModelBuilder.build(estate, player.getUniqueId());
        var def = EstateMenuBuilder.build(vm);
        MenuContext ctx = new MenuContext(player.getUniqueId(), player.getName(),
                Map.of("estateId", estate.id()));
        menuProvider.open(def, ctx);
    }

    // ── Estate resolution ──────────────────────────────────────────────────────

    private Estate resolveEstate(Player player, String[] args) {
        for (int i = 1; i < args.length; i++) {
            try {
                UUID id = UUID.fromString(args[i]);
                Estate e = estateService.findById(id).orElse(null);
                if (e != null) return e;
            } catch (IllegalArgumentException ignored) {}
        }
        var byOwner = estateService.findByOwner(player.getUniqueId());
        if (!byOwner.isEmpty()) return byOwner.iterator().next();
        var byMember = estateService.findByMember(player.getUniqueId());
        if (!byMember.isEmpty()) return byMember.iterator().next();
        error(player, ESTATE_PREFIX, "No se encontró ningún Estate. Usa /estate create <id> primero.");
        return null;
    }

    private String resolveName(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        return p != null ? p.getName() : uuid.toString().substring(0, 8);
    }

    // ── Help ──────────────────────────────────────────────────────────────────

    private void sendHelp(Player player) {
        player.sendMessage("§d§lDreamCraft Estate");
        player.sendMessage("§7Gestiona tu grupo de aventura.");
        player.sendMessage(" ");
        player.sendMessage("§f/estate create <id>          §7— Crear Estate");
        player.sendMessage("§f/estate discover <tipo>      §7— Descubrir Estate de aventura");
        player.sendMessage("§f/estate admin create <id> <tipo> §7— Crear Estate admin (persistente)");
        player.sendMessage("§f/estate invite <jugador>     §7— Invitar miembro");
        player.sendMessage("§f/estate join <id>           §7— Unirse a un Estate");
        player.sendMessage("§f/estate leave               §7— Salir del Estate");
        player.sendMessage("§f/estate start               §7— Iniciar instancia");
        player.sendMessage("§f/estate transfer <jugador>   §7— Transferir ownership");
        player.sendMessage("§f/estate info                §7— Ver información");
        player.sendMessage("§f/estate menu                §7— Abrir menú del Estate");
        player.sendMessage("§f/estate disband             §7— Disolver Estate");
    }

    // ── Tab completion ─────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = List.of("create", "discover", "admin", "invite", "join", "leave", "disband", "start", "transfer", "info", "menu");
            filter(subs, args[0]).forEach(completions::add);
            return completions;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            switch (sub) {
                case "invite", "transfer" -> filter(onlinePlayers(args[1]), args[1]).forEach(completions::add);
                case "join" -> filter(estateIds(), args[1]).forEach(completions::add);
                case "admin" -> filter(List.of("create"), args[1]).forEach(completions::add);
                default -> estateIdsOf(sender).stream().filter(id -> id.startsWith(args[1])).forEach(completions::add);
            }
            return completions;
        }
        if (args.length == 3 && "admin".equalsIgnoreCase(sub)) {
            filter(List.of("<id>"), args[2]).forEach(completions::add);
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

    private List<String> estateIds() {
        return estateService.findAll().stream().map(e -> e.id().toString()).toList();
    }

    private List<String> estateIdsOf(CommandSender sender) {
        if (!(sender instanceof Player player)) return List.of();
        return estateService.findByOwner(player.getUniqueId()).stream()
                .map(e -> e.id().toString())
                .toList();
    }

    private List<String> filter(List<String> options, String prefix) {
        return options.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT)))
                .toList();
    }
}
