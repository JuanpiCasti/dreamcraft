package dev.dreamcraft.protection.presentation.menu;

import dev.dreamcraft.protection.presentation.MenuAction;
import dev.dreamcraft.protection.presentation.MenuDefinition;
import dev.dreamcraft.protection.presentation.MenuItem;
import dev.dreamcraft.protection.presentation.viewmodel.EstateViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the {@link MenuDefinition} for an Estate lobby / instance view from an
 * {@link EstateViewModel}.
 *
 * <p>Pure data construction — no Bukkit types. Lore encodes active/blocked states:
 * green (&a) for available actions, dark gray (&8) for inactive, red (&c) for destructive.
 */
public final class EstateMenuBuilder {

    public static final String MENU_ID_LOBBY = "estate_lobby";
    public static final String MENU_ID_INSTANCE = "estate_instance";

    private EstateMenuBuilder() {}

    public static MenuDefinition build(EstateViewModel vm) {
        return vm.isInstanced() ? buildInstance(vm) : buildLobby(vm);
    }

    private static MenuDefinition buildLobby(EstateViewModel vm) {
        List<MenuItem> items = new ArrayList<>();

        // Slot 4 — Estate overview
        items.add(MenuItem.display(4, "icon.estate.overview",
                "&d&lEstate: " + vm.name(),
                List.of(
                        "&7Owner: &f" + vm.ownerName(),
                        "&7Miembros: &f" + vm.memberCount(),
                        vm.isAdventureLinked() ? "&7Aventura: &f" + vm.adventureId() : "&7Sin aventura",
                        vm.persistent() ? "&7Persistente: &aSí" : "&7Persistente: &cNo",
                        "&7Creado: &f" + vm.createdAt()
                )));

        // Slot 10 — Invite
        if (vm.canInvite()) {
            items.add(MenuItem.button(10, "icon.members", "&a&lInvitar",
                    List.of("&7Clic para invitar un jugador"),
                    MenuAction.of("estate.invite")));
        } else {
            items.add(MenuItem.display(10, "icon.members", "&8&lInvitar",
                    List.of("&8Solo el owner puede invitar")));
        }

        // Slot 12 — Join
        if (vm.canJoin()) {
            items.add(MenuItem.button(12, "icon.members", "&a&lUnirse",
                    List.of("&7Clic para unirte a este Estate"),
                    MenuAction.of("estate.join")));
        } else {
            items.add(MenuItem.display(12, "icon.members", "&8&lUnirse",
                    List.of("&8Ya eres miembro")));
        }

        // Slot 14 — Leave
        if (vm.canLeave()) {
            items.add(MenuItem.button(14, "icon.back", "&e&lSalir",
                    List.of("&7Clic para salir del Estate"),
                    MenuAction.of("estate.leave")));
        } else {
            items.add(MenuItem.display(14, "icon.back", "&8&lSalir",
                    List.of("&8No eres miembro")));
        }

        // Slot 16 — Start instance
        if (vm.canStart()) {
            items.add(MenuItem.button(16, "icon.estate.overview", "&a&lIniciar",
                    List.of("&7Clic para iniciar la instancia", "&aDisponible"),
                    MenuAction.of("estate.start")));
        } else {
            items.add(MenuItem.display(16, "icon.estate.overview", "&8&lIniciar",
                    List.of("&8Solo el owner puede iniciar")));
        }

        // Slot 19 — Transfer
        if (vm.canTransfer()) {
            items.add(MenuItem.button(19, "icon.members", "&a&lTransferir",
                    List.of("&7Clic para transferir ownership"),
                    MenuAction.of("estate.transfer")));
        } else {
            items.add(MenuItem.display(19, "icon.members", "&8&lTransferir",
                    List.of("&8Solo el owner puede transferir")));
        }

        // Slot 21 — Disband
        if (vm.canDisband()) {
            items.add(MenuItem.button(21, "icon.ward.inactive", "&c&lDisolver",
                    List.of("&cClic para disolver el Estate", "&cAcción irreversible"),
                    MenuAction.of("estate.disband")));
        } else {
            items.add(MenuItem.display(21, "icon.ward.inactive", "&8&lDisolver",
                    List.of("&8Solo el owner puede disolver")));
        }

        // Slot 22 — Close
        items.add(MenuItem.button(22, "icon.back", "&c&lCerrar",
                List.of("&7Cerrar menú"),
                MenuAction.of("menu.close")));

        return new MenuDefinition(MENU_ID_LOBBY, "&8Estate &f" + vm.name(), 27, items);
    }

    private static MenuDefinition buildInstance(EstateViewModel vm) {
        List<MenuItem> items = new ArrayList<>();

        // Slot 4 — Instance overview
        items.add(MenuItem.display(4, "icon.estate.overview",
                "&d&lInstancia: " + vm.name(),
                List.of(
                        "&7Owner: &f" + vm.ownerName(),
                        "&7Miembros: &f" + vm.memberCount(),
                        vm.adventureId() != null ? "&7Aventura: &f" + vm.adventureId() : "&7Sin aventura",
                        "&7Instancia: &f" + vm.instanceId()
                )));

        // Slot 10 — Invite (in-instance)
        if (vm.canInvite()) {
            items.add(MenuItem.button(10, "icon.members", "&a&lInvitar",
                    List.of("&7Clic para invitar a la instancia"),
                    MenuAction.of("estate.invite")));
        } else {
            items.add(MenuItem.display(10, "icon.members", "&8&lInvitar",
                    List.of("&8Solo el owner")));
        }

        // Slot 14 — Leave instance
        if (vm.canLeave()) {
            items.add(MenuItem.button(14, "icon.back", "&e&lSalir",
                    List.of("&7Clic para salir de la instancia"),
                    MenuAction.of("estate.leave")));
        } else {
            items.add(MenuItem.display(14, "icon.back", "&8&lSalir",
                    List.of("&8No eres miembro")));
        }

        // Slot 21 — Disband instance
        if (vm.canDisband()) {
            items.add(MenuItem.button(21, "icon.ward.inactive", "&c&lCerrar Instancia",
                    List.of("&cClic para cerrar la instancia"),
                    MenuAction.of("estate.disband")));
        } else {
            items.add(MenuItem.display(21, "icon.ward.inactive", "&8&lCerrar Instancia",
                    List.of("&8Solo el owner")));
        }

        // Slot 22 — Close menu
        items.add(MenuItem.button(22, "icon.back", "&c&lCerrar",
                List.of("&7Cerrar menú"),
                MenuAction.of("menu.close")));

        return new MenuDefinition(MENU_ID_INSTANCE, "&8Instancia &f" + vm.name(), 27, items);
    }
}
