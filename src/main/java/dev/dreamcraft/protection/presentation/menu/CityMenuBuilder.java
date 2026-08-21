package dev.dreamcraft.protection.presentation.menu;

import dev.dreamcraft.protection.domain.model.CityPolicy;
import dev.dreamcraft.protection.presentation.MenuAction;
import dev.dreamcraft.protection.presentation.MenuDefinition;
import dev.dreamcraft.protection.presentation.MenuItem;
import dev.dreamcraft.protection.presentation.viewmodel.CityViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the {@link MenuDefinition} for a City overview menu from a {@link CityViewModel}.
 *
 * <p>Pure data construction — no Bukkit types. Lore encodes active/blocked states:
 * green (&a) for available actions, dark gray (&8) for inactive, red (&c) for destructive.
 */
public final class CityMenuBuilder {

    public static final String MENU_ID = "city_overview";

    private CityMenuBuilder() {}

    public static MenuDefinition build(CityViewModel vm) {
        List<MenuItem> items = new ArrayList<>();

        // Slot 4 — City overview display
        List<String> overviewLore = new ArrayList<>();
        overviewLore.add("&7Gobernador: &f" + vm.governorName());
        overviewLore.add("&7Miembros: &f" + vm.memberCount());
        overviewLore.add("&7Wards: &f" + vm.wardCount());
        overviewLore.add("&7Tesoro: &a" + vm.treasury());
        overviewLore.add("&7City Score: &f" + vm.cityScore());
        if (vm.levelStatus() != null) {
            overviewLore.add("&7Nivel: &b" + vm.levelStatus().levelName());
        }
        overviewLore.add("&7Rol: &f" + roleLabel(vm));
        items.add(MenuItem.display(4, "icon.city.overview",
                "&6&lCiudad: " + vm.name(), overviewLore));

        // Slot 6 — City level progression (computed, not purchased)
        if (vm.levelStatus() != null) {
            var lvl = vm.levelStatus();
            List<String> levelLore = new ArrayList<>();
            levelLore.add("&7Nivel actual: &b" + lvl.levelName());
            levelLore.add("");
            levelLore.add("&7Progreso:");
            levelLore.add("&8- &7Wards anexados: &f" + lvl.wards());
            levelLore.add("&8- &7Habitantes: &f" + lvl.members());
            levelLore.add("&8- &7Riqueza (score): &f" + lvl.wealth());
            if (lvl.maxed()) {
                levelLore.add("");
                levelLore.add("&6&l¡Nivel máximo alcanzado!");
            } else {
                levelLore.add("");
                levelLore.add("&7Siguiente nivel: &b" + lvl.nextLevelName());
                levelLore.add("&8- " + (lvl.needWards() == 0 ? "&a✔" : "&fFaltan " + lvl.needWards() + " wards"));
                levelLore.add("&8- " + (lvl.needMembers() == 0 ? "&a✔" : "&fFaltan " + lvl.needMembers() + " habitantes"));
                levelLore.add("&8- " + (lvl.needWealth() == 0 ? "&a✔" : "&fFaltan " + lvl.needWealth() + " de riqueza"));
                levelLore.add("");
                levelLore.add(lvl.nextReady()
                        ? "&aEl nivel se actualiza solo — ¡ya calificás!"
                        : "&7El nivel sube solo al cumplir los requisitos");
            }
            items.add(MenuItem.display(6, "icon.ward.active", "&b&lNivel de Ciudad", levelLore));
        }

        // Slot 10 — Invite resident
        if (vm.canManageResidents()) {
            items.add(MenuItem.button(10, "icon.members", "&a&lInvitar Residente",
                    List.of("&7Clic para invitar un jugador", "&aDisponible"),
                    MenuAction.of("city.invite")));
        } else {
            items.add(MenuItem.display(10, "icon.members", "&8&lInvitar Residente",
                    List.of("&8Requiere rol Council o superior")));
        }

        // Slot 12 — Kick resident
        if (vm.canManageResidents()) {
            items.add(MenuItem.button(12, "icon.members", "&c&lExpulsar Residente",
                    List.of("&7Clic para expulsar un miembro"),
                    MenuAction.of("city.kick")));
        } else {
            items.add(MenuItem.display(12, "icon.members", "&8&lExpulsar Residente",
                    List.of("&8Requiere rol Council o superior")));
        }

        // Slot 14 — Roles
        if (vm.canSetRoles()) {
            items.add(MenuItem.button(14, "icon.members", "&a&lAsignar Rol",
                    List.of("&7Clic para cambiar el rol de un miembro"),
                    MenuAction.of("city.roles")));
        } else {
            items.add(MenuItem.display(14, "icon.members", "&8&lAsignar Rol",
                    List.of("&8Solo el Gobernador puede asignar roles")));
        }

        // Slot 16 — Treasury deposit
        if (vm.canManageTreasury()) {
            items.add(MenuItem.button(16, "icon.upkeep", "&a&lTesoro",
                    List.of("&7Saldo: &a" + vm.treasury(), "&7Clic para gestionar el tesoro"),
                    MenuAction.of("city.bank")));
        } else {
            items.add(MenuItem.display(16, "icon.upkeep", "&8&lTesoro",
                    List.of("&8Requiere rol Council o superior")));
        }

        // Slot 19 — Policy
        List<String> policyLore = new ArrayList<>();
        policyLore.add("&7Políticas activas:");
        for (CityPolicy p : CityPolicy.values()) {
            policyLore.add((vm.policies().contains(p) ? "&a" : "&8") + " - " + p.name());
        }
        if (vm.canSetPolicy()) {
            policyLore.add("&aClic para alternar OPEN_RECRUITMENT");
            items.add(MenuItem.button(19, "icon.city.overview", "&a&lPolíticas", policyLore,
                    MenuAction.of("city.policy", "OPEN_RECRUITMENT")));
        } else {
            policyLore.add("&8Solo el Gobernador puede cambiar políticas");
            items.add(MenuItem.display(19, "icon.city.overview", "&8&lPolíticas", policyLore));
        }

        // Slot 21 — Transfer governorship
        if (vm.canTransferGovernor()) {
            items.add(MenuItem.button(21, "icon.members", "&a&lTransferir Gobernanza",
                    List.of("&7Clic para transferir la gobernaduría"),
                    MenuAction.of("city.transfer")));
        } else {
            items.add(MenuItem.display(21, "icon.members", "&8&lTransferir Gobernanza",
                    List.of("&8Solo el Gobernador")));
        }

        // Slot 23 — Delete city
        if (vm.canDelete()) {
            items.add(MenuItem.button(23, "icon.ward.inactive", "&c&lEliminar Ciudad",
                    List.of("&cClic para eliminar la ciudad", "&cAcción irreversible"),
                    MenuAction.of("city.delete")));
        } else {
            items.add(MenuItem.display(23, "icon.ward.inactive", "&8&lEliminar Ciudad",
                    List.of("&8Solo el Gobernador")));
        }

        // Slot 22 — Close
        items.add(MenuItem.button(22, "icon.back", "&c&lCerrar",
                List.of("&7Cerrar menú"),
                MenuAction.of("menu.close")));

        return new MenuDefinition(MENU_ID, "&8Ciudad &f" + vm.name(), 27, items);
    }

    private static String roleLabel(CityViewModel vm) {
        if (vm.isGovernor()) return "Gobernador";
        if (vm.isCouncil()) return "Council";
        return "Ciudadano";
    }
}
