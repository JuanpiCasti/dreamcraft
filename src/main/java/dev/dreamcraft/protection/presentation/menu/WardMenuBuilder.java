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
                                ? "&7Ciudad: &f" + vm.cityName()
                                : "&7Sin ciudad"
                )));

        // Slot 10 — Upkeep deposit
        List<String> upkeepLore = new ArrayList<>();
        upkeepLore.add("&7Balance actual: &f" + vm.upkeepBalance() + " unidades");
        if (!vm.upkeepMaterials().isEmpty()) {
            upkeepLore.add("");
            upkeepLore.add("&7Ítems aceptados &8(unidades por ítem):");
            for (String line : vm.upkeepMaterials()) {
                upkeepLore.add("&8- &f" + line);
            }
        }
        upkeepLore.add("");
        upkeepLore.add(vm.canDeposit()
                ? "&7Toma el ítem en mano y haz clic aquí"
                : "&cNo puedes depositar en este Ward");
        items.add(MenuItem.depositSlot(10, "icon.upkeep",
                "&a&lDepositar Upkeep", upkeepLore));

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
                if (!preview.costs().isEmpty()) {
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
            items.add(MenuItem.button(12, "icon.ward.active", "&a&lMejorar Ward", scoreLore,
                    MenuAction.of("ward.upgrade")));
        } else {
            scoreLore.add("&8Tier máximo alcanzado");
            items.add(MenuItem.display(12, "icon.ward.inactive", "&8&lMejorar Ward", scoreLore));
        }

        // Slot 14 — Permissions toggle
        List<String> permLore = new ArrayList<>();
        permLore.add("&7Permisos públicos:");
        vm.permissions().forEach(p -> permLore.add("&7- &f" + p.name()));
        if (vm.canSetPermissions()) {
            permLore.add("&aClic para alternar PUBLIC_BUILD");
            items.add(MenuItem.button(14, "icon.members", "&a&lPermisos", permLore,
                    MenuAction.of("ward.toggle_permission", "PUBLIC_BUILD")));
        } else {
            permLore.add("&8Solo el owner puede cambiar permisos");
            items.add(MenuItem.display(14, "icon.members", "&8&lPermisos", permLore));
        }

        // Slot 16 — City membership
        if (vm.hasCityMembership()) {
            items.add(MenuItem.display(16, "icon.city.overview", "&a&lCiudad: " + vm.cityName(),
                    List.of("&7Ward anexado a la ciudad", "&7" + vm.cityName())));
        } else if (vm.canAnnexToCity()) {
            items.add(MenuItem.button(16, "icon.city.overview", "&a&lAnexar a Ciudad",
                    List.of("&7Clic para anexar a tu ciudad"),
                    MenuAction.of("ward.annex_city")));
        } else {
            items.add(MenuItem.display(16, "icon.city.overview", "&8&lAnexar a Ciudad",
                    List.of("&8Necesitas ser owner y no tener ciudad")));
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
            items.add(MenuItem.button(21, "icon.ward.inactive", "&c&lDisolver Ward",
                    List.of("&cClic para eliminar este Ward", "&cEsta acción es irreversible"),
                    MenuAction.of("ward.disband")));
        } else {
            items.add(MenuItem.display(21, "icon.ward.inactive", "&8&lDisolver Ward",
                    List.of("&8Solo el owner puede disolver")));
        }

        // Slot 22 — Close
        items.add(MenuItem.button(22, "icon.back", "&c&lCerrar",
                List.of("&7Cerrar menú"),
                MenuAction.of("menu.close")));

        return new MenuDefinition(MENU_ID, "&8Ward &f" + vm.name(), 27, items);
    }
}
