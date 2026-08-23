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
 * <p>Upgrade model ("esquema B"): each upgrade adds {@code ward.score-per-upgrade}
 * base score to the Ward; the domain tier provider resolves tier + radius from
 * the new score, so the radius grows with every upgrade. Only the upgrade that
 * <b>crosses</b> the next tier's minimum base score charges the item cost defined
 * for that TARGET tier in {@code ward-upgrade-costs} — intermediate upgrades are free.
 */
public final class WardUpgradeService {

    /** A quoted upgrade: target tier, resulting stats and its item cost. */
    public record UpgradeQuote(
            String targetTierKey,
            int scoreGain,
            int radiusAfter,
            int upkeepPerInterval,
            List<WardUpgradeCost> costs,
            /** true when this upgrade reaches the target tier's min score and pays {@link #costs}. */
            boolean crossingTier
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
     * <p>Cost rule ("esquema B"): the quoted cost is non-empty only when the
     * resulting score reaches the target tier's minimum base score, i.e. when
     * this upgrade crosses into the next tier. Intermediate upgrades are free.
     *
     * @return empty when the Ward is already at the highest configured tier
     */
    public Optional<UpgradeQuote> quoteNext(Ward ward) {
        Optional<WardTier> nextOpt = nextTierOf(ward);
        if (nextOpt.isEmpty()) return Optional.empty();

        WardTier next = nextOpt.get();
        int newScore = ward.baseScore() + scorePerUpgrade;
        boolean crossingTier = newScore >= next.minBaseScore();
        return Optional.of(new UpgradeQuote(
                next.key(),
                scorePerUpgrade,
                next.computeRadius(newScore),
                next.upkeepPerInterval(),
                costsForCrossing(newScore, next.minBaseScore(), costsFor(next.key())),
                crossingTier
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

    /**
     * Returns the target tier's costs only when {@code newScore} reaches
     * {@code nextMinBaseScore} (the upgrade crosses into the tier); otherwise
     * the intermediate upgrade is free. Pure helper, unit-testable without Bukkit.
     */
    public static List<WardUpgradeCost> costsForCrossing(int newScore, int nextMinBaseScore,
                                                         List<WardUpgradeCost> costs) {
        return newScore >= nextMinBaseScore ? costs : List.of();
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

    /** Spanish display name for common materials; falls back to a prettified enum name. */
    public String displayName(Material material) {
        return MaterialNames.forMaterial(material);
    }
}
