package dev.dreamcraft.protection.command;

import dev.dreamcraft.protection.domain.model.Estate;
import dev.dreamcraft.protection.domain.model.EstateType;
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
import java.util.Optional;
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
    /** Optional: syncs estate areas/membership to WorldGuard regions. */
    private final dev.dreamcraft.protection.integration.worldguard.WorldGuardAdapter worldGuardAdapter;
    /** Optional: manages private End instances for END-type estates. */
    private final dev.dreamcraft.protection.service.EndInstanceService instanceService;
    /** Per-server subcommand aliases/enabled flags. */
    private final dev.dreamcraft.protection.config.CommandOptions options;
    /** Single source of truth for dispatch + tab completion. */
    private final CommandRegistry registry;

    public EstateCommand(EstateService estateService, MenuProvider menuProvider) {
        this(estateService, menuProvider, null, null);
    }

    public EstateCommand(EstateService estateService,
                         MenuProvider menuProvider,
                         dev.dreamcraft.protection.integration.worldguard.WorldGuardAdapter worldGuardAdapter,
                         dev.dreamcraft.protection.service.EndInstanceService instanceService) {
        this(estateService, menuProvider, worldGuardAdapter, instanceService,
                dev.dreamcraft.protection.config.CommandOptions.empty());
    }

    public EstateCommand(EstateService estateService,
                         MenuProvider menuProvider,
                         dev.dreamcraft.protection.integration.worldguard.WorldGuardAdapter worldGuardAdapter,
                         dev.dreamcraft.protection.service.EndInstanceService instanceService,
                         dev.dreamcraft.protection.config.CommandOptions options) {
        this.estateService = estateService;
        this.menuProvider = menuProvider;
        this.worldGuardAdapter = worldGuardAdapter;
        this.instanceService = instanceService;
        this.options = options;
        this.viewModelBuilder = new EstateViewModelBuilder(this::resolveName);
        this.registry = buildRegistry();
    }

    /**
     * Builds the subcommand table: canonical names here, aliases merged from
     * config.yml (commands.estate.subcommands.&lt;name&gt;.aliases).
     */
    private CommandRegistry buildRegistry() {
        return new CommandRegistry("estate")
                .register(SubcommandSpec.of("create", this::handleCreate)
                        .withAliases(options.aliases("estate", "create")))
                .register(SubcommandSpec.of("discover", this::handleDiscover)
                        .withAliases(options.aliases("estate", "discover")))
                .register(SubcommandSpec.admin("admin", this::handleAdmin)
                        .withAliases(options.aliases("estate", "admin")))
                .register(SubcommandSpec.of("invite", this::handleInvite)
                        .withAliases(options.aliases("estate", "invite")))
                .register(SubcommandSpec.of("join", this::handleJoin)
                        .withAliases(options.aliases("estate", "join")))
                .register(SubcommandSpec.of("leave", (p, a) -> handleLeave(p, a))
                        .withAliases(options.aliases("estate", "leave")))
                .register(SubcommandSpec.of("disband", (p, a) -> handleDisband(p, a))
                        .withAliases(options.aliases("estate", "disband")))
                .register(SubcommandSpec.of("start", (p, a) -> handleStart(p, a))
                        .withAliases(options.aliases("estate", "start")))
                .register(SubcommandSpec.of("transfer", this::handleTransfer)
                        .withAliases(options.aliases("estate", "transfer")))
                .register(SubcommandSpec.of("info", this::handleInfo)
                        .withAliases(options.aliases("estate", "info")))
                .register(SubcommandSpec.of("menu", this::handleMenu)
                        .withAliases(options.aliases("estate", "menu")));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(tr("common.players-only", "§cEste comando solo puede ser usado por jugadores."));
            return true;
        }
        if (!player.hasPermission(USE_PERM)) {
            error(player, ESTATE_PREFIX, tr("common.no-permission", "No tienes permiso para usar este comando."));
            return true;
        }
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }
        SubcommandSpec spec = registry.resolve(args[0]);
        if (spec == null || !options.isEnabled(registry.root(), spec.name())) {
            error(player, ESTATE_PREFIX, tr("common.unknown-subcommand", "Subcomando desconocido: {sub}", "sub", args[0]));
            sendHelp(player);
            return true;
        }
        try {
            return spec.execute(player, args);
        } catch (RuntimeException e) {
            if (!handleDomainException(player, ESTATE_PREFIX, e)) {
                error(player, ESTATE_PREFIX, tr("common.error", "Error: {message}", "message", e.getMessage()));
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
        estateService.addMember(estate, player.getUniqueId());
        ok(player, ESTATE_PREFIX, "Estate '" + estate.name() + "' creado.");
        return true;
    }

    /**
     * Creates a personal party estate for the player: they become its leader
     * (owner) and can invite their group. For END / TRIAL_CHAMBER types, the
     * party inherits the gated area of the admin-created zone (if any) so the
     * portal/structure recognizes its members.
     */
    private boolean handleDiscover(Player player, String[] args) {
        if (args.length < 2) {
            error(player, ESTATE_PREFIX, "Uso: /estate discover <tipo> (end, trial_chamber)");
            return true;
        }
        EstateType type = EstateType.parse(args[1]);
        var zoneOpt = type.isInstancedAdventure()
                ? estateService.findZoneTemplate(type)
                : Optional.<Estate>empty();

        Estate estate = estateService.createPartyEstate(
                player.getUniqueId(), player.getName(), type, zoneOpt.orElse(null));
        estateService.addMember(estate, player.getUniqueId());
        syncEstateMembers(estate);

        ok(player, ESTATE_PREFIX, "Estate '" + estate.name() + "' creado. Sos su líder.");
        if (type.isInstancedAdventure()) {
            if (zoneOpt.isPresent()) {
                info(player, ESTATE_PREFIX, "Zona de aventura heredada. Colocá los ojos y cruzá el portal "
                        + "con tu grupo (/estate invite <jugador>).");
            } else {
                warn(player, ESTATE_PREFIX, "Todavía no hay zona de '" + type.displayName()
                        + "' creada por un admin; tu estate funcionará cuando exista.");
            }
        }
        openEstateMenu(player, estate);
        return true;
    }

    private boolean handleAdmin(Player player, String[] args) {
        if (!player.hasPermission("dreamcraft.protection.admin")) {
            error(player, ESTATE_PREFIX, "No tienes permiso para este comando.");
            return true;
        }
        if (args.length < 2) {
            error(player, ESTATE_PREFIX, "Uso: /estate admin create <id> <tipo> [radio|auto] | "
                    + "/estate admin area <id> [radio] | /estate admin reset <id>");
            return true;
        }
        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create" -> handleAdminCreate(player, args);
            case "area" -> handleAdminArea(player, args);
            case "reset" -> handleAdminReset(player, args);
            default -> {
                error(player, ESTATE_PREFIX, "Subcomando admin desconocido: " + args[1]);
                yield true;
            }
        };
    }

    /**
     * Creates a persistent typed estate and — when run by a player — anchors its
     * gated area at the player's position. Stand inside the portal/structure zone
     * before running it; the area protects the structure via WorldGuard and gates
     * it to estate members only.
     */
    private boolean handleAdminCreate(Player player, String[] args) {
        if (args.length < 4) {
            error(player, ESTATE_PREFIX, "Uso: /estate admin create <id> <tipo> [radio|auto]");
            return true;
        }
        String id = args[2];
        EstateType type = EstateType.parse(args[3]);

        // «auto» estimates the zone from the real structure (stronghold /
        // trial chamber) via vanilla structure location, anchoring the area at
        // its actual coordinates instead of the admin's position.
        boolean auto = args.length >= 5 && "auto".equalsIgnoreCase(args[4]);
        org.bukkit.Location anchor;
        int radius;
        if (auto) {
            anchor = locateStructure(player, type);
            if (anchor == null) {
                error(player, ESTATE_PREFIX, "No se encontró estructura cercana (búsqueda de 512 bloques). "
                        + "Colócate dentro de la estructura o usa un radio manual.");
                return true;
            }
            radius = args.length >= 6 ? parseRadius(args[5]) : Math.max(48, defaultAreaRadius());
        } else {
            anchor = player.getLocation();
            radius = args.length >= 5 ? parseRadius(args[4]) : defaultAreaRadius();
        }

        Estate estate = estateService.createEstate(
                player.getUniqueId(), id, "adv-" + type.key(), null, true, type,
                null, 0, 0, 0, 0);
        if (player.getWorld() != null && radius > 0) {
            applyArea(estate, anchor, radius);
            ok(player, ESTATE_PREFIX, "Estate admin '" + estate.name() + "' creado (persistente, tipo "
                    + type.displayName() + ", área r=" + radius
                    + (auto ? ", anclada a la estructura @ " + anchor.getBlockX() + "/"
                    + anchor.getBlockY() + "/" + anchor.getBlockZ() : "") + ").");
        } else {
            ok(player, ESTATE_PREFIX, "Estate admin '" + estate.name() + "' creado (persistente, tipo "
                    + type.displayName() + "). Define su área con /estate admin area " + estate.id());
        }
        return true;
    }

    /**
     * Estimates an adventure zone by locating the real vanilla structure:
     * END → stronghold, TRIAL_CHAMBER → trial chambers. Uses the modern
     * {@link org.bukkit.Registry#STRUCTURE} lookup (the legacy StructureType
     * constants predate trial chambers). Returns the located anchor or null.
     */
    private org.bukkit.Location locateStructure(Player player, EstateType type) {
        try {
            if (player.getWorld() == null) return null;
            org.bukkit.NamespacedKey key = switch (type.key()) {
                case "end" -> org.bukkit.NamespacedKey.minecraft("stronghold");
                case "trial_chamber" -> org.bukkit.NamespacedKey.minecraft("trial_chambers");
                default -> null;
            };
            if (key == null) return null;
            org.bukkit.generator.structure.Structure structure =
                    org.bukkit.Registry.STRUCTURE.get(key);
            if (structure == null) return null;
            var result = player.getWorld()
                    .locateNearestStructure(player.getLocation(), structure, 512, true);
            return result == null ? null : result.getLocation();
        } catch (Exception e) {
            return null;
        }
    }

    /** Moves/re-anchors the estate's gated area at the player's position. */
    private boolean handleAdminArea(Player player, String[] args) {
        if (args.length < 3) {
            error(player, ESTATE_PREFIX, "Uso: /estate admin area <id> [radio]");
            return true;
        }
        Estate estate = findEstateByIdOrName(args[2]);
        if (estate == null) {
            error(player, ESTATE_PREFIX, "Estate no encontrado: " + args[2]);
            return true;
        }
        int radius = args.length >= 4 ? parseRadius(args[3]) : Math.max(estate.areaRadius(), defaultAreaRadius());
        applyArea(estate, player.getLocation(), radius);
        ok(player, ESTATE_PREFIX, "Área del estate '" + estate.name()
                + "' fijada aquí (r=" + radius + ").");
        return true;
    }

    /** Forces the instance world reset (map restore + dragon respawn). */
    private boolean handleAdminReset(Player player, String[] args) {
        if (args.length < 3) {
            error(player, ESTATE_PREFIX, "Uso: /estate admin reset <id>");
            return true;
        }
        Estate estate = findEstateByIdOrName(args[2]);
        if (estate == null) {
            error(player, ESTATE_PREFIX, "Estate no encontrado: " + args[2]);
            return true;
        }
        if (instanceService == null || !estate.type().usesEndInstance()) {
            error(player, ESTATE_PREFIX, "Este estate no tiene instancia de End.");
            return true;
        }
        instanceService.resetInstance(estate);
        ok(player, ESTATE_PREFIX, "Instancia reiniciada: mapa restaurado y dragona lista.");
        return true;
    }

    private void applyArea(Estate estate, org.bukkit.Location location, int radius) {
        // Drop any previous WG region before re-anchoring
        if (worldGuardAdapter != null) worldGuardAdapter.removeEstateAreaRegion(estate);
        estateService.setArea(estate, location.getWorld().getName(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ(), radius);
        if (worldGuardAdapter != null && worldGuardAdapter.isAvailable()) {
            worldGuardAdapter.createEstateAreaRegion(estate, estate.areaWorld(),
                    estate.areaX(), estate.areaZ(), estate.areaRadius());
        }
        // Snapshot the vanilla portal frames so the room can regenerate
        // between groups (broken frames restored, eyes stripped) and discard
        // any stale edit journal from the previous anchor
        if (instanceService != null) {
            instanceService.clearZoneEdits(estate.id());
            instanceService.capturePortal(estate);
        }
    }

    private int parseRadius(String raw) {
        try {
            return Math.max(4, Integer.parseInt(raw));
        } catch (NumberFormatException e) {
            return defaultAreaRadius();
        }
    }

    private int defaultAreaRadius() { return 32; }

    private Estate findEstateByIdOrName(String raw) {
        try {
            UUID id = UUID.fromString(raw);
            var byId = estateService.findById(id);
            if (byId.isPresent()) return byId.get();
        } catch (IllegalArgumentException ignored) {}
        return estateService.findAll().stream()
                .filter(e -> e.name().equalsIgnoreCase(raw))
                .findFirst()
                .orElse(null);
    }

    /** Syncs the estate's WorldGuard area region with its current membership. */
    private void syncEstateMembers(Estate estate) {
        if (worldGuardAdapter != null) worldGuardAdapter.syncEstateMembers(estate);
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
            syncEstateMembers(estate);
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
        syncEstateMembers(estate);
        ok(player, ESTATE_PREFIX, "Te uniste al Estate " + estate.name() + ".");
        return true;
    }

    private boolean handleLeave(Player player, String[] args) {
        Estate estate = resolveEstate(player, args);
        if (estate == null) return true;
        if (estate.isOwner(player.getUniqueId())) {
            error(player, ESTATE_PREFIX, "El owner no puede salir. Usa /estate disband o /estate transfer.");
            return true;
        }
        if (!estate.isMember(player.getUniqueId())) {
            error(player, ESTATE_PREFIX, "No eres miembro del Estate.");
            return true;
        }
        estateService.removeMember(estate, player.getUniqueId());
        syncEstateMembers(estate);
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
        if (instanceService != null && estate.type().usesEndInstance()) {
            instanceService.resetInstance(estate);
        }
        if (worldGuardAdapter != null) worldGuardAdapter.removeEstateAreaRegion(estate);
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
            // END-type estates pre-open their private End world + dragon right away
            if (instanceService != null && instanceService.preopen(estate)) {
                ok(player, ESTATE_PREFIX, "Instancia de §f" + estate.name()
                        + "§a iniciada. El End privado está listo con la dragona.");
            } else {
                ok(player, ESTATE_PREFIX, "Instancia de §f" + estate.name() + "§a iniciada.");
            }
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
        info(player, ESTATE_PREFIX, "Tipo: " + estate.type().displayName());
        info(player, ESTATE_PREFIX, "Owner: " + resolveName(estate.ownerId()));
        info(player, ESTATE_PREFIX, "Miembros: " + estate.members().size());
        info(player, ESTATE_PREFIX, "Aventura: " + (estate.adventureId() != null ? estate.adventureId() : "N/A"));
        info(player, ESTATE_PREFIX, "Instancia: " + (estate.instanceId() != null ? estate.instanceId() : "Inactiva"));
        if (estate.hasArea()) {
            info(player, ESTATE_PREFIX, "Área: " + estate.areaWorld() + " @ "
                    + estate.areaX() + ", " + estate.areaY() + ", " + estate.areaZ()
                    + " (r=" + estate.areaRadius() + ")");
        } else {
            info(player, ESTATE_PREFIX, "Área: sin definir");
        }
        if (instanceService != null && estate.type().usesEndInstance()) {
            boolean active = Bukkit.getWorld(instanceService.worldNameFor(estate)) != null;
            info(player, ESTATE_PREFIX, "Mundo End: "
                    + instanceService.worldNameFor(estate) + (active ? " (activo)" : " (sin crear)"));
        }
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
        return CommandMessages.resolveName(uuid);
    }

    // ── Help ──────────────────────────────────────────────────────────────────

    private void sendHelp(Player player) {
        helpBlock(player, "help.estate");
    }

    // ── Tab completion ─────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            filter(registry.completionTokens(args[0]), args[0]).forEach(completions::add);
            return completions;
        }
        SubcommandSpec resolved = registry.resolve(args[0]);
        String sub = (resolved != null ? resolved.name() : args[0]).toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            switch (sub) {
                case "invite", "transfer" -> filter(onlinePlayers(args[1]), args[1]).forEach(completions::add);
                case "join" -> filter(estateIds(), args[1]).forEach(completions::add);
                case "discover" -> filter(List.of("end", "trial_chamber", "standard"), args[1]).forEach(completions::add);
                case "admin" -> filter(List.of("create", "area", "reset"), args[1]).forEach(completions::add);
                default -> estateIdsOf(sender).stream().filter(id -> id.startsWith(args[1])).forEach(completions::add);
            }
            return completions;
        }
        if (args.length == 3 && "admin".equalsIgnoreCase(sub)) {
            switch (args[1].toLowerCase(Locale.ROOT)) {
                case "create" -> filter(List.of("<id>"), args[2]).forEach(completions::add);
                case "area", "reset" -> estateNames().stream()
                        .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT)))
                        .forEach(completions::add);
                default -> {}
            }
            return completions;
        }
        if (args.length == 4 && "admin".equalsIgnoreCase(sub)
                && "create".equalsIgnoreCase(args[1])) {
            filter(List.of("end", "trial_chamber", "standard"), args[3]).forEach(completions::add);
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

    private List<String> estateNames() {
        return estateService.findAll().stream().map(Estate::name).toList();
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
