package dev.dreamcraft.protection.presentation.menu;

import dev.dreamcraft.protection.presentation.MenuAction;
import dev.dreamcraft.protection.presentation.MenuDefinition;
import dev.dreamcraft.protection.presentation.MenuItem;
import dev.dreamcraft.protection.presentation.viewmodel.WardViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the {@link MenuDefinition} for a Ward status menu from a {@link WardViewModel}.
 *
 * <p>Pure data construction — no Bukkit types. The {@link dev.dreamcraft.protection.presentation.VanillaMenuProvider}
 * renders this definition into a concrete inventory.
 *
 * <p>Lore uses legacy {@code &}-codes (resolved by the provider via Adventure):
 * <ul>
 *   <li>{@code &7} gray for informational lines</li>
 *   <li>{@code &a} green for available actions</li>
 *   <li>{@code &c} red for blocked/unavailable actions</li>
 *   <li>{@code &8} dark gray for inactive/disabled states</li>
 * </ul>
 */
public final class WardMenuBuilder {

    public static final String MENU_ID = "ward_status";

    private WardMenuBuilder() {}

    public static MenuDefinition build(WardViewModel vm) {
        List<MenuItem> items = new ArrayList<>();

        // Slot 4 — Ward status display
        String wardIcon = vm.hasCityMembership() ? "icon.ward.active" : "icon.ward.inactive";
        items.add(MenuItem.display(4, wardIcon,
                "&b&l" + vm.name(),
                List.of(
                        "&7Owner: &f" + vm.ownerName(),
                        "&7Tier: &b" + vm.tier(),
                        "&7Score: &f" + vm.baseScore(),
                        "&7Radio: &f" + vm.radius() + " bloques",
                        "&7Upkeep: &f" + vm.upkeepBalance(),
                        "&7Centro: &f" + vm.centerX() + ", " + vm.centerY() + ", " + vm.centerZ(),
                        vm.hasCityMembership()
                                ? "&7Matriz: &f" + vm.cityName()
                                : "&7Sin Matriz"
                )));

        // Slot 10 — Upkeep vault opener
        // Lore (≤ 10 lines): protection time by state, consumption, below-tier
        // surcharge (when active), balance, ALL material → time equivalences
        // (two per line to stay compact), action hint.
        List<String> upkeepLore = new ArrayList<>();
        var projection = vm.upkeepProjection();
        if (projection != null) {
            String stateColor = projection.state().legacyColor();
            upkeepLore.add(stateColor + "&lProtección: &f" + projection.timeRemainingText()
                    + " &7(" + projection.state().displayName() + ")");
            upkeepLore.add("&7Consumo: &f" + projection.unitsPerInterval() + " u/intervalo");
            if (vm.belowTierBlocks() > 0 && vm.belowTierSurchargePerInterval() > 0) {
                upkeepLore.add("&cSobrecosto: &f+" + vm.belowTierSurchargePerInterval()
                        + " u/intervalo &7(" + vm.belowTierBlocks()
                        + " bloque(s) fuera de fase)");
            }
            upkeepLore.add("&7Balance: &f" + projection.balanceUnits() + " u disponibles");
            var equivalences = projection.equivalences();
            if (!equivalences.isEmpty()) {
                upkeepLore.add("");
                for (int i = 0; i < equivalences.size(); i += 2) {
                    StringBuilder line = new StringBuilder();
                    for (int j = i; j < Math.min(i + 2, equivalences.size()); j++) {
                        var eq = equivalences.get(j);
                        if (j > i) line.append(" &8· ");
                        else line.append("&8· ");
                        line.append("&f1×").append(eq.label())
                                .append(" &7≈ &b").append(eq.timeBoughtText());
                    }
                    upkeepLore.add(line.toString());
                }
            }
        } else {
            // Calculator not wired — degrade gracefully to the plain balance line
            upkeepLore.add("&7Balance actual: &f" + vm.upkeepBalance() + " unidades");
        }
        upkeepLore.add("");
        if (vm.canDeposit()) {
            upkeepLore.add("&aClic para abrir la bóveda y colocar ítems");
        } else {
            upkeepLore.add("&cNo puedes depositar en este Núcleo");
        }
        if (vm.canDeposit()) {
            items.add(MenuItem.button(10, "icon.upkeep", "&a&lBóveda de Upkeep", upkeepLore,
                    MenuAction.of("ward.upkeep_vault")));
        } else {
            items.add(MenuItem.display(10, "icon.upkeep", "&8&lBóveda de Upkeep", upkeepLore));
        }

        // Slot 12 — Score / upgrade
        List<String> scoreLore = new ArrayList<>();
        scoreLore.add("&7Score base: &f" + vm.baseScore());
        scoreLore.add("&7Tier actual: &b" + vm.tier());
        var preview = vm.upgradePreview();
        if (vm.canUpgrade()) {
            if (preview.available()) {
                scoreLore.add("");
                scoreLore.add("&7Mejora al tier &b" + preview.targetTier() + "&7:");
                scoreLore.add("&8- &7Radio de protección: &f" + preview.radiusAfter() + " bloques");
                scoreLore.add("&8- &7Upkeep: &f" + preview.upkeepPerInterval() + " unidades/intervalo");
                scoreLore.add("&8- &7Score: &f+" + preview.scoreGain());
                if (!preview.crossingTier()) {
                    scoreLore.add("");
                    scoreLore.add("&7Costo: &fgratis (crecimiento)");
                } else if (!preview.costs().isEmpty()) {
                    scoreLore.add("");
                    scoreLore.add("&7Costo (se descuenta al mejorar):");
                    for (var cost : preview.costs()) {
                        scoreLore.add((cost.affordable() ? "&a✔ " : "&c✖ ")
                                + "&f" + cost.amount() + "x " + cost.materialDisplay());
                    }
                }
                scoreLore.add("");
                if (preview.canAfford()) {
                    scoreLore.add("&aClic para mejorar");
                } else {
                    scoreLore.add("&cTe faltan ítems marcados con ✖");
                }
            } else {
                scoreLore.add("");
                scoreLore.add("&7Mejora disponible al siguiente tier");
                scoreLore.add("&aClic para mejorar");
            }
            items.add(MenuItem.button(12, "icon.ward.active", "&a&lElevar Fase", scoreLore,
                    MenuAction.of("ward.upgrade")));
        } else {
            scoreLore.add("&8Tier máximo alcanzado");
            items.add(MenuItem.display(12, "icon.ward.inactive", "&8&lElevar Fase", scoreLore));
        }

        // Slot 14 — Permissions toggle
        List<String> permLore = new ArrayList<>();
        permLore.add("&7Permisos públicos:");
        vm.permissions().forEach(p -> permLore.add("&7- &f" + p.name()));
        boolean containersPublic = vm.permissions().stream()
                .anyMatch(p -> p.name().equals("PUBLIC_CONTAINERS"));
        permLore.add("");
        permLore.add(containersPublic
                ? "&7Contenedores: &aabiertos al público"
                : "&7Contenedores: &csolo miembros");
        if (vm.canSetPermissions()) {
            permLore.add("&aClic para alternar PUBLIC_CONTAINERS");
            items.add(MenuItem.button(14, "icon.members", "&a&lPermisos", permLore,
                    MenuAction.of("ward.toggle_permission", "PUBLIC_CONTAINERS")));
        } else {
            permLore.add("&8Solo el owner puede cambiar permisos");
            items.add(MenuItem.display(14, "icon.members", "&8&lPermisos", permLore));
        }

        // Slot 16 — City membership
        if (vm.hasCityMembership()) {
            items.add(MenuItem.display(16, "icon.city.overview", "&a&lMatriz: " + vm.cityName(),
                    List.of("&7Núcleo federado a la Matriz", "&7" + vm.cityName())));
        } else if (vm.canAnnexToCity()) {
            items.add(MenuItem.button(16, "icon.city.overview", "&a&lFederar a Matriz",
                    List.of("&7Clic para federar a tu Matriz"),
                    MenuAction.of("ward.annex_city")));
        } else {
            items.add(MenuItem.display(16, "icon.city.overview", "&8&lFederar a Matriz",
                    List.of("&8Necesitas ser owner y no tener Matriz")));
        }

        // Slot 19 — Transfer ownership
        if (vm.canTransfer()) {
            items.add(MenuItem.button(19, "icon.members", "&a&lTransferir",
                    List.of("&7Clic para transferir ownership"),
                    MenuAction.of("ward.transfer")));
        } else {
            items.add(MenuItem.display(19, "icon.members", "&8&lTransferir",
                    List.of("&8Solo el owner puede transferir")));
        }

        // Slot 21 — Disband / delete
        if (vm.canDisband()) {
            items.add(MenuItem.button(21, "icon.ward.inactive", "§c§lApagar Núcleo",
                    List.of("&cClic para eliminar este Núcleo", "&cEsta acción es irreversible"),
                    MenuAction.of("ward.disband")));
        } else {
            items.add(MenuItem.display(21, "icon.ward.inactive", "&8§lApagar Núcleo",
                    List.of("&8Solo el owner puede disolver")));
        }

        // Slot 22 — Close
        items.add(MenuItem.button(22, "icon.back", "&c&lCerrar",
                List.of("&7Cerrar menú"),
                MenuAction.of("menu.close")));

        return new MenuDefinition(MENU_ID, dev.dreamcraft.protection.message.Messages.get()
                .tr("menu.title.ward", "&8Núcleo &f{name}", "name", vm.name()), 27, items);
    }
}
