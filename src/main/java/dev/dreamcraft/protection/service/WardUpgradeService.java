package dev.dreamcraft.protection.service;

import dev.dreamcraft.protection.config.ProtectionConfig;
import dev.dreamcraft.protection.config.WardUpgradeCost;
import dev.dreamcraft.protection.domain.model.Ward;
import dev.dreamcraft.protection.domain.model.WardTier;
import dev.dreamcraft.protection.domain.port.WardTierProvider;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Gameplay service for Ward upgrades: quotes the next tier, computes item costs,
 * verifies the player's inventory and charges the items.
 *
 * <p>Upgrade model: each upgrade adds {@code ward.score-per-upgrade} base score to
 * the Ward; the domain tier provider resolves tier + radius from the new score.
 * The item cost is defined per TARGET tier in {@code ward-upgrade-costs} — tiers
 * without an entry are free to reach.
 */
public final class WardUpgradeService {

    /** A quoted upgrade: target tier, resulting stats and its item cost. */
    public record UpgradeQuote(
            String targetTierKey,
            int scoreGain,
            int radiusAfter,
            int upkeepPerInterval,
            List<WardUpgradeCost> costs
    ) {}

    private final WardTierProvider tierProvider;
    private final int scorePerUpgrade;
    private final Map<String, List<WardUpgradeCost>> costsByTargetTier;

    public WardUpgradeService(WardTierProvider tierProvider, ProtectionConfig config) {
        this.tierProvider = tierProvider;
        this.scorePerUpgrade = config.wardScorePerUpgrade();
        this.costsByTargetTier = config.wardUpgradeCosts();
    }

    // ── Quoting ───────────────────────────────────────────────────────────────

    /**
     * Quotes the next upgrade for a Ward.
     *
     * @return empty when the Ward is already at the highest configured tier
     */
    public Optional<UpgradeQuote> quoteNext(Ward ward) {
        Optional<WardTier> nextOpt = nextTierOf(ward);
        if (nextOpt.isEmpty()) return Optional.empty();

        WardTier next = nextOpt.get();
        int newScore = ward.baseScore() + scorePerUpgrade;
        return Optional.of(new UpgradeQuote(
                next.key(),
                scorePerUpgrade,
                next.computeRadius(newScore),
                next.upkeepPerInterval(),
                costsFor(next.key())
        ));
    }

    /** Tiers ordered by min-base-score ascending; next = first strictly above current. */
    private Optional<WardTier> nextTierOf(Ward ward) {
        var currentOpt = tierProvider.findByKey(ward.tier());
        int currentMin = currentOpt.map(WardTier::minBaseScore).orElse(-1);
        return tierProvider.allTiers().values().stream()
                .sorted(Comparator.comparingInt(WardTier::minBaseScore))
                .filter(t -> t.minBaseScore() > currentMin)
                .findFirst();
    }

    private List<WardUpgradeCost> costsFor(String targetTierKey) {
        return costsByTargetTier.getOrDefault(targetTierKey.toLowerCase(Locale.ROOT), List.of());
    }

    // ── Inventory checks ──────────────────────────────────────────────────────

    /** @return how many items of the given material the player carries. */
    public int countItem(Player player, Material material) {
        return countItem(player.getInventory(), material);
    }

    /** @return true when the player carries every item required by the quote. */
    public boolean canAfford(Player player, UpgradeQuote quote) {
        for (WardUpgradeCost cost : quote.costs()) {
            if (countItem(player, cost.material()) < cost.amount()) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return lines describing missing items ("§c✖ 8x Diamante"); empty list = can afford.
     */
    public List<String> missingItems(Player player, UpgradeQuote quote) {
        List<String> missing = new ArrayList<>();
        for (WardUpgradeCost cost : quote.costs()) {
            int has = countItem(player.getInventory(), cost.material());
            if (has < cost.amount()) {
                missing.add("§c✖ §f" + (cost.amount() - has) + "x " + displayName(cost.material())
                        + " §7(tienes " + has + "/" + cost.amount() + ")");
            }
        }
        return missing;
    }

    /** Removes the quoted cost from the player's inventory. Call only after {@link #missingItems} is empty. */
    public void charge(Player player, UpgradeQuote quote) {
        for (WardUpgradeCost cost : quote.costs()) {
            removeItems(player.getInventory(), cost.material(), cost.amount());
        }
    }

    private int countItem(Inventory inventory, Material material) {
        int total = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (item != null && item.getType() == material) {
                total += item.getAmount();
            }
        }
        return total;
    }

    private void removeItems(Inventory inventory, Material material, int amount) {
        ItemStack[] contents = inventory.getStorageContents();
        for (int i = 0; i < contents.length && amount > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != material) continue;
            int take = Math.min(item.getAmount(), amount);
            amount -= take;
            if (take >= item.getAmount()) {
                contents[i] = null;
            } else {
                item.setAmount(item.getAmount() - take);
            }
        }
        inventory.setStorageContents(contents);
    }

    // ── Display helpers ───────────────────────────────────────────────────────

    private static final Map<String, String> MATERIAL_NAMES_ES = buildMaterialNames();

    private static Map<String, String> buildMaterialNames() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("DIAMOND", "Diamante");
        m.put("EMERALD", "Esmeralda");
        m.put("IRON_INGOT", "Lingote de Hierro");
        m.put("GOLD_INGOT", "Lingote de Oro");
        m.put("COPPER_INGOT", "Lingote de Cobre");
        m.put("NETHERITE_INGOT", "Lingote de Netherite");
        m.put("REDSTONE", "Redstone");
        m.put("LAPIS_LAZULI", "Lapislázuli");
        m.put("COAL", "Carbón");
        m.put("OBSIDIAN", "Obsidiana");
        m.put("AMETHYST_SHARD", "Fragmento de Amatista");
        m.put("QUARTZ", "Cuarzo");
        m.put("GOLD_BLOCK", "Bloque de Oro");
        m.put("IRON_BLOCK", "Bloque de Hierro");
        m.put("DIAMOND_BLOCK", "Bloque de Diamante");
        m.put("EMERALD_BLOCK", "Bloque de Esmeralda");
        return m;
    }

    /** Spanish display name for common materials; falls back to a prettified enum name. */
    public String displayName(Material material) {
        String es = MATERIAL_NAMES_ES.get(material.name());
        if (es != null) return es;
        String raw = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }
}
