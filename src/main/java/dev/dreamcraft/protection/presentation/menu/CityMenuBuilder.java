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

        // ── Layout city_overview v3 (54 slots = 6 filas de 9) ──
        // Filas 0-2: Estado de Matriz 3×3 centrado {3,4,5, 12,13,14, 21,22,23}.
        // Filas 1-2: Invitar 2×2 {9,10, 18,19} (1 slot a la izq) · Roles 2×2 {16,17, 25,26} (donde estaba la bóveda).
        // Fila 3: Separadores 27..29 y 33..35 a los lados del Tesoro.
        // Filas 3-5: Tesoro 3×3 centrado {30,31,32, 39,40,41, 48,49,50}.
        // Fila 5: Todos los botones 1×1: Perfil 45 · Políticas 46 · Expulsar 47 · [Tesoro 48..50] · Transferir 51 · Eliminar 52 · Cerrar 53.

        // Slots 3/4/5/12/13/14/21/22/23 (3×3) — City overview central display
        List<String> overviewLore = new ArrayList<>();
        overviewLore.add("&7Gobernador: &f" + vm.governorName());
        overviewLore.add("&7Miembros: &f" + vm.memberCount());
        overviewLore.add("&7Núcleos: &f" + vm.wardCount());
        overviewLore.add("&7Tesoro: &a" + vm.treasury());
        overviewLore.add("&7City Score: &f" + vm.cityScore());
        if (vm.levelStatus() != null) {
            var lvl = vm.levelStatus();
            overviewLore.add("&7Nivel: &b" + lvl.levelName());
            overviewLore.add("");
            overviewLore.add("&7Progreso de Nivel:");
            overviewLore.add("&8- &7Núcleos federados: &f" + lvl.wards());
            overviewLore.add("&8- &7Habitantes: &f" + lvl.members());
            overviewLore.add("&8- &7Riqueza (score): &f" + lvl.wealth());
            if (lvl.maxed()) {
                overviewLore.add("");
                overviewLore.add("&6&l¡Nivel máximo alcanzado!");
            } else {
                overviewLore.add("");
                overviewLore.add("&7Siguiente nivel: &b" + lvl.nextLevelName());
                overviewLore.add("&8- " + (lvl.needWards() == 0 ? "&a✔" : "&fFaltan " + lvl.needWards() + " núcleos"));
                overviewLore.add("&8- " + (lvl.needMembers() == 0 ? "&a✔" : "&fFaltan " + lvl.needMembers() + " habitantes"));
                overviewLore.add("&8- " + (lvl.needWealth() == 0 ? "&a✔" : "&fFaltan " + lvl.needWealth() + " de riqueza"));
                overviewLore.add("");
                overviewLore.add(lvl.nextReady()
                        ? "&aEl nivel se actualiza solo — ¡ya calificás!"
                        : "&7El nivel sube solo al cumplir los requisitos");
            }
        }
        overviewLore.add("&7Rol: &f" + roleLabel(vm));
        items.addAll(MenuItem.block3x3Display(54, 3, "icon.city.overview",
                "&6&lMatriz: " + vm.name(), overviewLore));

        // Fila 3 — líneas separadoras horizontales a los lados del Tesoro 3×3
        for (int s : new int[]{27, 28, 29, 33, 34, 35}) {
            items.add(MenuItem.display(s, "menu.line", " ", List.of()));
        }

        // Slots 9/10/18/19 (2×2) — Invite resident (1 slot a la izquierda)
        if (vm.canManageResidents()) {
            items.addAll(MenuItem.block2x2Button(54, 9, "menu.invite", "&a&lInvitar Residente",
                    List.of("&7Clic para invitar un jugador", "&aDisponible"),
                    MenuAction.of("city.invite")));
        } else {
            items.addAll(MenuItem.block2x2Display(54, 9, "menu.invite", "&8&lInvitar Residente",
                    List.of("&8Requiere rol Council o superior")));
        }

        // Slots 16/17/25/26 (2×2) — Roles (ubicado donde antes estaba la bóveda)
        if (vm.canSetRoles()) {
            items.addAll(MenuItem.block2x2Button(54, 16, "menu.roles", "&a&lAsignar Rol",
                    List.of("&7Clic para cambiar el rol de un miembro"),
                    MenuAction.of("city.roles")));
        } else {
            items.addAll(MenuItem.block2x2Display(54, 16, "menu.roles", "&8&lAsignar Rol",
                    List.of("&8Solo el Gobernador puede asignar roles")));
        }

        // Slots 30/31/32/39/40/41/48/49/50 (3×3) — Treasury vault (Tesoro abajo 3×3)
        List<String> treasuryLore = List.of(
                "&7Saldo (créditos): &a" + vm.treasury(),
                "",
                "&7Bóveda física: los ítems quedan",
                "&7guardados y podés retirarlos cuando",
                "&7quieras. Su valor suma riqueza",
                "&7para el nivel de la Matriz.",
                "&aClic para abrir la bóveda");
        if (vm.canManageTreasury()) {
            items.addAll(MenuItem.block3x3Button(54, 30, "city.treasury", "&6&lTesoro de la Matriz",
                    treasuryLore, MenuAction.of("city.bank")));
        } else {
            items.addAll(MenuItem.block3x3Display(54, 30, "city.treasury", "&8&lTesoro",
                    List.of("&8Requiere rol Council o superior")));
        }

        // ── Botones 1×1 en Fila 5 ──

        // Slot 36 — Cerrar menú (flecha a la izquierda)
        items.add(MenuItem.button(36, "menu.back", "&e« Cerrar",
                List.of("&7Cerrar menú"),
                MenuAction.of("cerrar")));

        // Slot 37 — Identidad de jugador (Perfil)
        items.add(MenuItem.button(37, "menu.profile", "&6Perfil",
                List.of("&7Tu identidad y datos de jugador"),
                MenuAction.of("perfil")));

        // Slot 38 — Políticas (icono papel)
        List<String> policyLore = new ArrayList<>();
        policyLore.add("&7Políticas activas:");
        for (CityPolicy p : CityPolicy.values()) {
            policyLore.add((vm.policies().contains(p) ? "&a" : "&8") + " - " + p.name());
        }
        policyLore.add("&aClic para gestionar cada política");
        if (vm.canSetPolicy()) {
            items.add(MenuItem.button(38, "ward.permissions", "&a&lPolíticas", policyLore,
                    MenuAction.of("city.policies")));
        } else {
            policyLore.add("&8Solo el Gobernador puede cambiar políticas");
            items.add(MenuItem.display(38, "ward.permissions", "&8&lPolíticas", policyLore));
        }

        // Slot 42 — Expulsar Residente
        if (vm.canManageResidents()) {
            items.add(MenuItem.button(42, "menu.kick", "&c&lExpulsar Residente",
                    List.of("&7Clic para expulsar un miembro"),
                    MenuAction.of("city.kick")));
        } else {
            items.add(MenuItem.display(42, "menu.kick", "&8&lExpulsar Residente",
                    List.of("&8Requiere rol Council o superior")));
        }

        // Slot 43 — Transferir Gobernanza
        if (vm.canTransferGovernor()) {
            items.add(MenuItem.button(43, "menu.roles", "&a&lTransferir Gobernanza",
                    List.of("&7Clic para transferir la gobernaduría"),
                    MenuAction.of("city.transfer")));
        } else {
            items.add(MenuItem.display(43, "menu.roles", "&8&lTransferir Gobernanza",
                    List.of("&8Solo el Gobernador")));
        }

        // Slot 44 — Eliminar Matriz (escudo inactivo/apagado)
        if (vm.canDelete()) {
            items.add(MenuItem.button(44, "icon.ward.inactive", "&c&lEliminar Matriz",
                    List.of("&cClic para eliminar la Matriz", "&cAcción irreversible"),
                    MenuAction.of("city.delete")));
        } else {
            items.add(MenuItem.display(44, "icon.ward.inactive", "&8&lEliminar Matriz",
                    List.of("&8Solo el Gobernador")));
        }

        // Los visuales van horneados en el glifo de fondo (menu.bg.<menuId>);
        // los ítems quedan como capturadores invisibles de click (menu.catcher).
        items.replaceAll(it -> new MenuItem(it.slot(), "menu.catcher", it.displayName(), it.lore(), it.action(), it.acceptsDeposit()));
        return new MenuDefinition(MENU_ID, dev.dreamcraft.protection.message.Messages.get()
                .tr("menu.title.city", "&8Matriz &f{name}", "name", vm.name()), 54, items);
    }

    private static String roleLabel(CityViewModel vm) {
        if (vm.isGovernor()) return "Gobernador";
        if (vm.isCouncil()) return "Council";
        return "Ciudadano";
    }
}
