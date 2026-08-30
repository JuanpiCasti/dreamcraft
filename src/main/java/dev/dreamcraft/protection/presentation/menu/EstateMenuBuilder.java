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

        // ── Layout estate_lobby v2 (54 slots = 6 filas de 9) ──
        // Fila 0: 8 perfil.
        // Filas 1-2: Invitar 2×2 {10,11,19,20} · Resumen 2×2 {13,14,22,23}
        // centrado · Unirse 2×2 {16,17,25,26}.
        // Fila 3: separador (filler).
        // Filas 4-5: Salir 37 · Volver 46 · Iniciar 2×2 {40,41,49,50} ·
        // Transferir 43 · Disolver 52 · Cerrar 53.

        // Fila 3 — línea separadora horizontal (menu.line, barra fina del pack)
        for (int s = 27; s <= 35; s++) {
            items.add(MenuItem.display(s, "menu.line", " ", List.of()));
        }

        // Slots 12/13/14/21/22/23 (3×2) — main overview display (3 horizontales, 2 verticales, centrado)
        items.addAll(MenuItem.block3x2Display(54, 12, "icon.estate.overview",
                "&d&lGrupo: " + vm.name(),
                List.of(
                        "&7Owner: &f" + vm.ownerName(),
                        "&7Miembros: &f" + vm.memberCount(),
                        "&7Tipo: &f" + vm.typeLabel(),
                        vm.isAdventureLinked() ? "&7Aventura: &f" + vm.adventureId() : "&7Sin aventura",
                        vm.hasArea() ? "&aÁrea de aventura activa" : "&7Sin área definida",
                        vm.persistent() ? "&7Persistente: &aSí" : "&7Persistente: &cNo",
                        "&7Creado: &f" + vm.createdAt()
                )));

        // Slots 9/10/18/19 (2×2) — Invite (1 slot a la izquierda, simétrico a Matriz)
        if (vm.canInvite()) {
            items.addAll(MenuItem.block2x2Button(54, 9, "menu.invite", "&a&lInvitar",
                    List.of("&7Clic para invitar un jugador"),
                    MenuAction.of("estate.invite")));
        } else {
            items.addAll(MenuItem.block2x2Display(54, 9, "menu.invite", "&8&lInvitar",
                    List.of("&8Solo el owner puede invitar")));
        }

        // Slots 16/17/25/26 (2×2) — Join
        if (vm.canJoin()) {
            items.addAll(MenuItem.block2x2Button(54, 16, "estate.join", "&a&lUnirse",
                    List.of("&7Clic para unirte a esta instancia"),
                    MenuAction.of("estate.join")));
        } else {
            items.addAll(MenuItem.block2x2Display(54, 16, "estate.join", "&8&lUnirse",
                    List.of("&8Ya eres miembro")));
        }

        // Slots 39/40/41/48/49/50 (3×2) — Start instance (3 slots horizontales, 2 verticales)
        if (vm.canStart()) {
            items.addAll(MenuItem.block3x2Button(54, 39, "estate.dragon", "&a&lIniciar",
                    List.of("&7Clic para iniciar la instancia", "&aDisponible"),
                    MenuAction.of("estate.start")));
        } else {
            items.addAll(MenuItem.block3x2Display(54, 39, "estate.dragon", "&8&lIniciar",
                    List.of("&8Solo el owner puede iniciar")));
        }

        // ── Fila 5: Botones de acción 1×1 ──

        // Slot 36 — Cerrar menú
        items.add(MenuItem.button(36, "menu.close", "&e« Cerrar",
                List.of("&7Cerrar menú"),
                MenuAction.of("cerrar")));

        // Slot 37 — Identidad de jugador (Perfil)
        items.add(MenuItem.button(37, "menu.profile", "&6Perfil",
                List.of("&7Tu identidad y datos de jugador"),
                MenuAction.of("perfil")));

        // Slot 38 — Abandonar / Salir del grupo (icono puerta con flecha)
        if (vm.canLeave()) {
            items.add(MenuItem.button(38, "estate.leave", "&c« Abandonar grupo",
                    List.of("&7Abandonar el grupo actual"),
                    MenuAction.of("estate.leave")));
        } else {
            items.add(MenuItem.display(38, "estate.leave", "&8« Abandonar grupo",
                    List.of("&8No eres miembro")));
        }

        // Slot 43 — Transfer (columna derecha)
        if (vm.canTransfer()) {
            items.add(MenuItem.button(43, "estate.transfer", "&a&lTransferir",
                    List.of("&7Clic para transferir ownership"),
                    MenuAction.of("estate.transfer")));
        } else {
            items.add(MenuItem.display(43, "estate.transfer", "&8&lTransferir",
                    List.of("&8Solo el owner puede transferir")));
        }

        // Slot 44 — Disband (columna derecha, TNT)
        if (vm.canDisband()) {
            items.add(MenuItem.button(44, "estate.disband", "&c&lDisolver",
                    List.of("&cClic para disolver la instancia", "&cAcción irreversible"),
                    MenuAction.of("estate.disband")));
        } else {
            items.add(MenuItem.display(44, "estate.disband", "&8&lDisolver",
                    List.of("&8Solo el owner puede disolver")));
        }

        items.replaceAll(MenuItem::asCatcher);
        return new MenuDefinition(MENU_ID_LOBBY, dev.dreamcraft.protection.message.Messages.get()
                .tr("menu.title.estate-lobby", "&8{name.estate} &f{name}", "name", vm.name()), 54, items);
    }

    private static MenuDefinition buildInstance(EstateViewModel vm) {
        List<MenuItem> items = new ArrayList<>();

        // ── Layout estate_instance v2 (54 slots = 6 filas de 9) ──
        // Fila 0: 8 perfil.
        // Filas 1-2: Invitar 2×2 {10,11,19,20} · Resumen 2×2 {13,14,22,23}
        // centrado · Salir 2×2 {16,17,25,26}.
        // Fila 3: separador (filler).
        // Fila 4: Volver 39 · Cerrar Instancia 40 · Cerrar menú 41 (trío centrado).

        // Fila 3 — línea separadora horizontal (menu.line, barra fina del pack)
        for (int s = 27; s <= 35; s++) {
            items.add(MenuItem.display(s, "menu.line", " ", List.of()));
        }

        // Slots 12/13/14/21/22/23 (3×2) — main overview display (3 horizontales, 2 verticales, centrado)
        items.addAll(MenuItem.block3x2Display(54, 12, "icon.estate.overview",
                "&d&lInstancia: " + vm.name(),
                List.of(
                        "&7Owner: &f" + vm.ownerName(),
                        "&7Miembros: &f" + vm.memberCount(),
                        vm.adventureId() != null ? "&7Aventura: &f" + vm.adventureId() : "&7Sin aventura",
                        "&7Instancia: &f" + vm.instanceId()
                )));

        // Slots 9/10/18/19 (2×2) — Invite (in-instance, 1 slot a la izquierda)
        if (vm.canInvite()) {
            items.addAll(MenuItem.block2x2Button(54, 9, "menu.invite", "&a&lInvitar",
                    List.of("&7Clic para invitar a la instancia"),
                    MenuAction.of("estate.invite")));
        } else {
            items.addAll(MenuItem.block2x2Display(54, 9, "menu.invite", "&8&lInvitar",
                    List.of("&8Solo el owner")));
        }

        // Slots 16/17/25/26 (2×2) — Leave instance
        if (vm.canLeave()) {
            items.addAll(MenuItem.block2x2Button(54, 16, "estate.leave", "&e&lSalir",
                    List.of("&7Clic para salir de la instancia"),
                    MenuAction.of("estate.leave")));
        } else {
            items.addAll(MenuItem.block2x2Display(54, 16, "estate.leave", "&8&lSalir",
                    List.of("&8No eres miembro")));
        }

        // ── Fila 5: Botones de acción 1×1 ──

        // Slot 36 — Cerrar menú
        items.add(MenuItem.button(36, "menu.close", "&cCerrar",
                List.of("&7Cerrar menú"),
                MenuAction.of("cerrar")));

        // Slot 37 — Identidad de jugador (Perfil)
        items.add(MenuItem.button(37, "menu.profile", "&6Perfil",
                List.of("&7Tu identidad y datos de jugador"),
                MenuAction.of("perfil")));

        // Slot 39 — Back to the viewer's Ward status (flecha violeta de nexo)
        items.add(MenuItem.button(39, "menu.back.nexo", "&e« Volver al Núcleo",
                List.of("&7Abre el estado de tu Núcleo"),
                MenuAction.of("ward.open")));

        // Slot 40 — Disband instance
        if (vm.canDisband()) {
            items.add(MenuItem.button(40, "estate.disband", "&c&lCerrar Instancia",
                    List.of("&cClic para cerrar la instancia"),
                    MenuAction.of("estate.disband")));
        } else {
            items.add(MenuItem.display(40, "estate.disband", "&8&lCerrar Instancia",
                    List.of("&8Solo el owner")));
        }

        items.replaceAll(MenuItem::asCatcher);
        return new MenuDefinition(MENU_ID_INSTANCE, dev.dreamcraft.protection.message.Messages.get().tr("menu.title.estate-instance", "&8Instancia &f{name}", "name", vm.name()), 54, items);
    }
}
