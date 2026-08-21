package dev.dreamcraft.protection.listener;

import dev.dreamcraft.protection.config.ProtectionConfig;
import dev.dreamcraft.protection.domain.model.Ward;
import dev.dreamcraft.protection.domain.model.WardTier;
import dev.dreamcraft.protection.domain.port.WardTierProvider;
import dev.dreamcraft.protection.domain.service.WardService;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Gates "advanced" blocks (enchanting table, brewing stand, beacon, …) behind
 * Ward tiers: inside a Ward whose tier is below the configured minimum for the
 * material, placement is denied until the Ward ranks up.
 *
 * <p>Configuration: {@code ward.tier-gated-blocks} maps material → minimum
 * ward-tier key. Tier ordering is derived from {@code min-base-score}.
 * Admins ({@code dreamcraft.ward.admin}) bypass the gate.
 */
public final class WardBlockGateListener implements Listener {

    private final WardService wardService;
    private final WardTierProvider tierProvider;
    private final Map<Material, String> gatedBlocks;

    public WardBlockGateListener(WardService wardService,
                                 WardTierProvider tierProvider,
                                 ProtectionConfig config) {
        this.wardService = wardService;
        this.tierProvider = tierProvider;
        this.gatedBlocks = config.wardTierGatedBlocks();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Material placed = event.getBlockPlaced().getType();
        String requiredTierKey = gatedBlocks.get(placed);
        if (requiredTierKey == null) return;

        Block block = event.getBlockPlaced();
        Optional<Ward> found = wardService.findAtLocation(
                block.getWorld().getName(), block.getX(), block.getZ());
        if (found.isEmpty()) return; // outside any Ward — not gated by this listener

        Player player = event.getPlayer();
        if (player.hasPermission("dreamcraft.ward.admin")) return;

        Ward ward = found.get();
        int wardRank = rankOf(ward.tier());
        int requiredRank = rankOf(requiredTierKey);
        if (wardRank < 0 || requiredRank < 0) return; // unknown tier keys → don't block
        if (wardRank >= requiredRank) return;

        String requiredName = tierProvider.findByKey(requiredTierKey)
                .map(t -> capitalize(t.key()))
                .orElse(capitalize(requiredTierKey));
        event.setCancelled(true);
        player.sendMessage("§c[Ward] §f" + pretty(placed) + "§c requiere un Ward rango §b"
                + requiredName + "§c. Mejorá tu Ward desde la baliza (§f/ward menu§c).");
    }

    /** 0-based position of the tier when ordered by min-base-score; -1 if unknown. */
    private int rankOf(String tierKey) {
        List<WardTier> ordered = tierProvider.allTiers().values().stream()
                .sorted(Comparator.comparingInt(WardTier::minBaseScore))
                .toList();
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).key().equalsIgnoreCase(tierKey)) return i;
        }
        return -1;
    }

    private String pretty(Material material) {
        return capitalize(material.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' '));
    }

    private String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
