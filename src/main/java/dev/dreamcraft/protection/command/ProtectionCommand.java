package dev.dreamcraft.protection.command;

import dev.dreamcraft.protection.config.ProtectionConfig;
import dev.dreamcraft.protection.model.ProtectionClaim;
import dev.dreamcraft.protection.model.TierDefinition;
import dev.dreamcraft.protection.service.ClaimManager;
import dev.dreamcraft.protection.service.UpkeepManager;
import dev.dreamcraft.protection.ui.ProtectionMenu;
import dev.dreamcraft.protection.ui.WardrobeItems;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles all /protection (aliases: /prot, /claim) subcommands.
 *
 * <p>Player subcommands (perm: dreamcraft.protection.use):
 * <ul>
 *   <li>/protection            — show help
 *   <li>/protection claim      — open the wardrobe menu for the current claim
 *   <li>/protection status     — text status of current claim
 *   <li>/protection upkeep     — upkeep details of current claim
 *   <li>/protection members    — list members
 *   <li>/protection members add &lt;player&gt;    — add a member
 *   <li>/protection members remove &lt;player&gt; — remove a member
 *   <li>/protection transfer &lt;player&gt;       — transfer ownership
 *   <li>/protection abandon    — abandon and delete current claim
 * </ul>
 *
 * <p>Admin subcommands (perm: dreamcraft.protection.admin):
 * <ul>
 *   <li>/protection give          — give a wardrobe item
 *   <li>/protection reload        — reload configuration
 *   <li>/protection recalculate   — recalculate upkeep state
 * </ul>
 */
public final class ProtectionCommand implements CommandExecutor, TabCompleter {

    private static final String USE_PERM  = "dreamcraft.protection.use";
    private static final String ADMIN_PERM = "dreamcraft.protection.admin";

    private final ClaimManager claimManager;
    private final WardrobeItems wardrobeItems;
    private final ProtectionMenu protectionMenu;
    private final Runnable reloadAction;
    private final UpkeepManager upkeepManager;
    private final ProtectionConfig config;

    public ProtectionCommand(ClaimManager claimManager, WardrobeItems wardrobeItems,
                             ProtectionMenu protectionMenu, Runnable reloadAction,
                             UpkeepManager upkeepManager, ProtectionConfig config) {
        this.claimManager = claimManager;
        this.wardrobeItems = wardrobeItems;
        this.protectionMenu = protectionMenu;
        this.reloadAction = reloadAction;
        this.upkeepManager = upkeepManager;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cEste comando solo puede ser usado por jugadores.");
            return true;
        }
        if (!player.hasPermission(USE_PERM)) {
            player.sendMessage("§cNo tienes permiso para usar este comando.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "claim"       -> handleClaim(player);
            case "status"      -> handleStatus(player);
            case "upkeep"      -> handleUpkeep(player);
            case "members"     -> handleMembers(player, args);
            case "transfer"    -> handleTransfer(player, args);
            case "abandon"     -> handleAbandon(player);
            case "give"        -> handleGive(player);
            case "reload"      -> handleReload(player);
            case "recalculate", "rebuildstats" -> handleRecalculate(player);
            default            -> {
                player.sendMessage("§c[Protección] Subcomando desconocido: §f" + sub);
                sendHelp(player);
                yield true;
            }
        };
    }

    // ── Help ──────────────────────────────────────────────────────────────────

    private void sendHelp(Player player) {
        player.sendMessage("§5§lDreamCraft Protection");
        player.sendMessage("§7Gestiona las protecciones de tu base.");
        player.sendMessage(" ");
        player.sendMessage("§f/protection claim       §7— Abrir el menú de tu claim");
        player.sendMessage("§f/protection status      §7— Consultar el estado del claim");
        player.sendMessage("§f/protection upkeep      §7— Consultar el mantenimiento");
        player.sendMessage("§f/protection members     §7— Ver/gestionar miembros");
        player.sendMessage("§f/protection transfer <jugador>  §7— Transferir ownership");
        player.sendMessage("§f/protection abandon     §7— Abandonar/eliminar el claim");
        if (player.hasPermission(ADMIN_PERM)) {
            player.sendMessage(" ");
            player.sendMessage("§c§lAdmin:");
            player.sendMessage("§f/protection give        §7— Obtener ítem Wardrobe");
            player.sendMessage("§f/protection reload      §7— Recargar configuración");
            player.sendMessage("§f/protection recalculate §7— Recalcular upkeep del claim");
        }
    }

    // ── Player subcommands ────────────────────────────────────────────────────

    /**
     * /protection claim — open the wardrobe menu for the claim the player is standing in.
     */
    private boolean handleClaim(Player player) {
        Optional<ProtectionClaim> opt = claimManager.findByLocation(player.getLocation());
        if (opt.isEmpty()) {
            player.sendMessage("§c[Protección] No hay ningún claim en esta posición.");
            return true;
        }
        protectionMenu.open(player, opt.get());
        return true;
    }

    /**
     * /protection status — text summary of the current claim.
     */
    private boolean handleStatus(Player player) {
        Optional<ProtectionClaim> opt = claimManager.findByLocation(player.getLocation());
        if (opt.isEmpty()) {
            player.sendMessage("§c[Protección] No hay ningún claim en esta posición.");
            return true;
        }
        ProtectionClaim claim = opt.get();
        player.sendMessage("§5§lEstado del Claim");
        player.sendMessage("§7ID: §f" + claim.id());
        player.sendMessage("§7Owner: §e" + claim.ownerUuid());
        player.sendMessage("§7Estado: §f" + claim.status().name());
        player.sendMessage("§7Tier: §b" + claim.tier());
        player.sendMessage("§7Radio: §f" + claim.radius() + " bloques");
        player.sendMessage("§7Wardrobe: §f" + claim.wardrobeX() + "," + claim.wardrobeY() + "," + claim.wardrobeZ());
        player.sendMessage("§7Miembros: §f" + claim.members().size());
        return true;
    }

    /**
     * /protection upkeep — upkeep details of the current claim.
     */
    private boolean handleUpkeep(Player player) {
        Optional<ProtectionClaim> opt = claimManager.findByLocation(player.getLocation());
        if (opt.isEmpty()) {
            player.sendMessage("§c[Protección] No hay ningún claim en esta posición.");
            return true;
        }
        ProtectionClaim claim = opt.get();
        // We need the calculator — grab it from the manager via the upkeepManager
        // Actually we don't hold a direct calculator ref — use UpkeepManager which has it
        upkeepManager.recalculateState(claim); // ensure state is fresh
        player.sendMessage("§5§lMantenimiento del Claim");
        player.sendMessage("§7Unidades almacenadas: §a" + claim.upkeepStorage().get("maintenance"));
        player.sendMessage("§7Próximo cobro: §e" + claim.nextUpkeepAt());
        player.sendMessage("§7Estado: §f" + claim.status().name());
        return true;
    }

    /**
     * /protection members [add|remove|list] [player]
     */
    private boolean handleMembers(Player player, String[] args) {
        if (args.length < 2) {
            // Show usage
            player.sendMessage("§5§lGestión de Miembros");
            player.sendMessage("§f/protection members list            §7— Ver miembros");
            player.sendMessage("§f/protection members add <jugador>   §7— Agregar miembro");
            player.sendMessage("§f/protection members remove <jugador>§7— Quitar miembro");
            player.sendMessage("§7Ejemplo: §f/protection members add Steve");
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "list"   -> handleMembersList(player);
            case "add"    -> handleMembersAdd(player, args);
            case "remove" -> handleMembersRemove(player, args);
            default       -> {
                player.sendMessage("§c[Protección] Acción desconocida: §f" + action);
                player.sendMessage("§7Usa: §f/protection members [list|add|remove]");
                yield true;
            }
        };
    }

    private boolean handleMembersList(Player player) {
        Optional<ProtectionClaim> opt = claimManager.findByLocation(player.getLocation());
        if (opt.isEmpty()) {
            player.sendMessage("§c[Protección] No hay ningún claim en esta posición.");
            return true;
        }
        ProtectionClaim claim = opt.get();
        player.sendMessage("§5§lMiembros del Claim");
        player.sendMessage("§7Owner: §e" + claim.ownerUuid());
        if (claim.members().isEmpty()) {
            player.sendMessage("§7No hay miembros adicionales.");
        } else {
            claim.members().forEach(uuid -> player.sendMessage("§7- §f" + uuid));
        }
        TierDefinition tier = config.tiers().get(claim.tier());
        int maxMembers = tier != null ? tier.maxMembers() : config.defaultMaxMembers();
        player.sendMessage("§7Slots: §f" + claim.members().size() + "§7/§f" + maxMembers);
        return true;
    }

    private boolean handleMembersAdd(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUso: §f/protection members add <jugador>");
            player.sendMessage("§7Ejemplo: §f/protection members add Steve");
            return true;
        }
        Optional<ProtectionClaim> opt = claimManager.findByLocation(player.getLocation());
        if (opt.isEmpty()) {
            player.sendMessage("§c[Protección] No hay ningún claim en esta posición.");
            return true;
        }
        ProtectionClaim claim = opt.get();
        if (!claim.ownerUuid().equals(player.getUniqueId())) {
            player.sendMessage("§c[Protección] Solo el owner puede agregar miembros.");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            player.sendMessage("§c[Protección] Jugador §f" + args[2] + " §cno encontrado o no está en línea.");
            return true;
        }
        if (target.getUniqueId().equals(claim.ownerUuid())) {
            player.sendMessage("§c[Protección] El owner ya tiene acceso completo.");
            return true;
        }
        if (claim.members().contains(target.getUniqueId())) {
            player.sendMessage("§e[Protección] " + target.getName() + " ya es miembro.");
            return true;
        }
        boolean added = claimManager.addMember(claim, target.getUniqueId());
        if (!added) {
            TierDefinition tier = config.tiers().get(claim.tier());
            int maxMembers = tier != null ? tier.maxMembers() : config.defaultMaxMembers();
            player.sendMessage("§c[Protección] Límite de miembros alcanzado (§f" + maxMembers + "§c).");
        } else {
            player.sendMessage("§a[Protección] §f" + target.getName() + " §aagregado al claim.");
            target.sendMessage("§a[Protección] Fuiste agregado al claim de §f" + player.getName() + "§a.");
        }
        return true;
    }

    private boolean handleMembersRemove(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUso: §f/protection members remove <jugador>");
            return true;
        }
        Optional<ProtectionClaim> opt = claimManager.findByLocation(player.getLocation());
        if (opt.isEmpty()) {
            player.sendMessage("§c[Protección] No hay ningún claim en esta posición.");
            return true;
        }
        ProtectionClaim claim = opt.get();
        if (!claim.ownerUuid().equals(player.getUniqueId())) {
            player.sendMessage("§c[Protección] Solo el owner puede quitar miembros.");
            return true;
        }
        UUID targetUuid;
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target != null) {
            targetUuid = target.getUniqueId();
        } else {
            try {
                targetUuid = UUID.fromString(args[2]);
            } catch (IllegalArgumentException e) {
                player.sendMessage("§c[Protección] Jugador §f" + args[2] + " §cno encontrado.");
                return true;
            }
        }
        claimManager.removeMember(claim, targetUuid);
        player.sendMessage("§a[Protección] Miembro eliminado del claim.");
        return true;
    }

    /**
     * /protection transfer <player> — transfers ownership if config allows.
     */
    private boolean handleTransfer(Player player, String[] args) {
        if (!config.ownerTransfer()) {
            player.sendMessage("§c[Protección] La transferencia de ownership está deshabilitada.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("§cUso: §f/protection transfer <jugador>");
            player.sendMessage("§7Ejemplo: §f/protection transfer Steve");
            return true;
        }
        Optional<ProtectionClaim> opt = claimManager.findByLocation(player.getLocation());
        if (opt.isEmpty()) {
            player.sendMessage("§c[Protección] No hay ningún claim en esta posición.");
            return true;
        }
        ProtectionClaim claim = opt.get();
        if (!claim.ownerUuid().equals(player.getUniqueId())) {
            player.sendMessage("§c[Protección] Solo el owner puede transferir el claim.");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage("§c[Protección] Jugador §f" + args[1] + " §cno encontrado o no está en línea.");
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage("§c[Protección] No puedes transferirte el claim a ti mismo.");
            return true;
        }
        claimManager.transferOwner(claim, target.getUniqueId());
        player.sendMessage("§a[Protección] Ownership transferido a §f" + target.getName() + "§a.");
        target.sendMessage("§a[Protección] §f" + player.getName() + " §ate transfirió su claim.");
        return true;
    }

    /**
     * /protection abandon — removes the claim the player is standing in.
     * Only the owner can abandon. The wardrobe block remains; the protection is lifted.
     */
    private boolean handleAbandon(Player player) {
        Optional<ProtectionClaim> opt = claimManager.findByLocation(player.getLocation());
        if (opt.isEmpty()) {
            player.sendMessage("§c[Protección] No hay ningún claim en esta posición.");
            return true;
        }
        ProtectionClaim claim = opt.get();
        if (!claim.ownerUuid().equals(player.getUniqueId())) {
            player.sendMessage("§c[Protección] Solo el owner puede abandonar el claim.");
            return true;
        }
        claimManager.removeClaim(claim);
        try {
            claimManager.save();
        } catch (IOException e) {
            player.sendMessage("§e[Protección] Claim eliminado pero no se pudo guardar: " + e.getMessage());
            return true;
        }
        player.sendMessage("§a[Protección] Claim eliminado. El área ya no está protegida.");
        return true;
    }

    // ── Admin subcommands ─────────────────────────────────────────────────────

    /**
     * /protection give — gives the executing admin a Wardrobe item.
     */
    private boolean handleGive(Player player) {
        if (!player.hasPermission(ADMIN_PERM)) {
            player.sendMessage("§c[Protección] No tienes permiso para este comando.");
            return true;
        }
        player.getInventory().addItem(wardrobeItems.createWardrobeItem());
        player.sendMessage("§a[Protección] Armario entregado.");
        return true;
    }

    private boolean handleReload(Player player) {
        if (!player.hasPermission(ADMIN_PERM)) {
            player.sendMessage("§c[Protección] No tienes permiso para este comando.");
            return true;
        }
        reloadAction.run();
        player.sendMessage("§a[Protección] Configuración recargada.");
        return true;
    }

    private boolean handleRecalculate(Player player) {
        if (!player.hasPermission(ADMIN_PERM)) {
            player.sendMessage("§c[Protección] No tienes permiso para este comando.");
            return true;
        }
        Optional<ProtectionClaim> opt = claimManager.findByLocation(player.getLocation());
        if (opt.isEmpty()) {
            player.sendMessage("§c[Protección] No hay ningún claim en esta posición.");
            return true;
        }
        upkeepManager.recalculateState(opt.get());
        player.sendMessage("§a[Protección] Claim recalculado.");
        return true;
    }

    // ── Tab completion ────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) return List.of();

        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("claim", "status", "upkeep", "members", "transfer", "abandon"));
            if (player.hasPermission(ADMIN_PERM)) {
                subs.addAll(List.of("give", "reload", "recalculate"));
            }
            String input = args[0].toLowerCase(Locale.ROOT);
            subs.stream().filter(s -> s.startsWith(input)).forEach(completions::add);
            return completions;
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if ("members".equals(sub)) {
                return List.of("list", "add", "remove");
            }
            if ("transfer".equals(sub)) {
                return onlinePlayers(args[1]);
            }
        }
        if (args.length == 3) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String action = args[1].toLowerCase(Locale.ROOT);
            if ("members".equals(sub) && ("add".equals(action) || "remove".equals(action))) {
                return onlinePlayers(args[2]);
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
}
