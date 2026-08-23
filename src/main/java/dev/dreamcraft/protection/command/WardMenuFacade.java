package dev.dreamcraft.protection.command;

import dev.dreamcraft.protection.domain.model.City;
import dev.dreamcraft.protection.domain.model.Ward;
import dev.dreamcraft.protection.domain.port.WardTierProvider;
import dev.dreamcraft.protection.domain.service.CityService;
import dev.dreamcraft.protection.presentation.MenuContext;
import dev.dreamcraft.protection.presentation.MenuProvider;
import dev.dreamcraft.protection.presentation.menu.WardMenuBuilder;
import dev.dreamcraft.protection.presentation.viewmodel.WardUpgradePreview;
import dev.dreamcraft.protection.presentation.viewmodel.WardViewModel;
import dev.dreamcraft.protection.presentation.viewmodel.WardViewModelBuilder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shared façade that renders and opens the Ward status menu.
 *
 * <p>Both {@code /ward menu} and {@code /protection claim} open the exact same
 * menu through this façade, guaranteeing one mechanic behind both commands.
 * Also used by the {@link dev.dreamcraft.protection.presentation.MenuActionDispatcher}
 * to refresh the menu after deposits/upgrades.
 */
public final class WardMenuFacade {

    private final WardViewModelBuilder viewModelBuilder;
    private final MenuProvider menuProvider;
    private final dev.dreamcraft.protection.service.WardUpgradeService upgradeService;

    public WardMenuFacade(WardTierProvider tierProvider,
                          CityService cityService,
                          dev.dreamcraft.protection.service.WardUpgradeService upgradeService,
                          MenuProvider menuProvider,
                          List<String> upkeepMaterialLines) {
        this(tierProvider, cityService, upgradeService, menuProvider, upkeepMaterialLines, null);
    }

    /**
     * @param upkeepCalculator optional balance → protection-time projector; null disables
     *                         the "Protección:" lore lines and vault-open summary
     */
    public WardMenuFacade(WardTierProvider tierProvider,
                          CityService cityService,
                          dev.dreamcraft.protection.service.WardUpgradeService upgradeService,
                          MenuProvider menuProvider,
                          List<String> upkeepMaterialLines,
                          dev.dreamcraft.protection.service.UpkeepProjectionCalculator upkeepCalculator) {
        this(tierProvider, cityService, upgradeService, menuProvider, upkeepMaterialLines,
                upkeepCalculator, 0);
    }

    /**
     * @param upkeepCalculator        optional balance → protection-time projector; null disables
     *                                the "Protección:" lore lines and vault-open summary
     * @param belowTierSurchargeUnits recurring surcharge per gated block below tier
     *                                (mirrors {@code WardUpkeepTickTask}); 0 → base rate only
     */
    public WardMenuFacade(WardTierProvider tierProvider,
                          CityService cityService,
                          dev.dreamcraft.protection.service.WardUpgradeService upgradeService,
                          MenuProvider menuProvider,
                          List<String> upkeepMaterialLines,
                          dev.dreamcraft.protection.service.UpkeepProjectionCalculator upkeepCalculator,
                          int belowTierSurchargeUnits) {
        this.upgradeService = upgradeService;
        this.menuProvider = menuProvider;
        this.viewModelBuilder = new WardViewModelBuilder(
                tierProvider,
                CommandMessages::resolveName,
                id -> cityService.findById(id).map(City::name).orElse(null),
                this::buildUpgradePreview,
                upkeepMaterialLines,
                upkeepCalculator,
                belowTierSurchargeUnits);
    }

    /** Opens the Ward menu for the viewer with freshly computed state. */
    public void open(Player player, Ward ward) {
        WardViewModel vm = viewModelBuilder.build(ward, player.getUniqueId());
        var def = WardMenuBuilder.build(vm);
        MenuContext ctx = new MenuContext(player.getUniqueId(), player.getName(),
                Map.of("wardId", ward.id()));
        menuProvider.open(def, ctx);
    }

    /** Computes the upgrade preview shown in the menu (cost lines + affordability). */
    private WardUpgradePreview buildUpgradePreview(Ward ward, UUID viewerId) {
        if (upgradeService == null) return WardUpgradePreview.unavailable();
        var quoteOpt = upgradeService.quoteNext(ward);
        if (quoteOpt.isEmpty()) return WardUpgradePreview.unavailable();
        var quote = quoteOpt.get();
        Player viewer = Bukkit.getPlayer(viewerId);
        boolean canAfford = viewer != null && upgradeService.canAfford(viewer, quote);
        var costLines = quote.costs().stream()
                .map(c -> new WardUpgradePreview.CostLine(
                        c.amount(),
                        upgradeService.displayName(c.material()),
                        viewer != null && upgradeService.countItem(viewer, c.material()) >= c.amount()))
                .toList();
        return new WardUpgradePreview(true, canAfford, quote.targetTierKey(),
                quote.scoreGain(), quote.radiusAfter(), quote.upkeepPerInterval(), costLines,
                quote.crossingTier());
    }
}
