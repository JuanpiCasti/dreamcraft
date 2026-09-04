package dev.dreamcraft.protection.presentation.menu;

import dev.dreamcraft.protection.presentation.MenuAction;
import dev.dreamcraft.protection.presentation.MenuDefinition;
import dev.dreamcraft.protection.presentation.MenuItem;
import dev.dreamcraft.protection.presentation.viewmodel.WardViewModel;
import dev.dreamcraft.protection.service.UpkeepProjectionCalculator;

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

        // ── Layout ward_status v2 (54 slots = 6 filas de 9) ──
        // Fila 0-1: Estado/Sync 2×2 {4,5,13,14} (sube 1 slot desde {13,14,22,23}).
        // Filas 1-2: Bóveda Upkeep 2×2 {9,10,18,19} · Estado 2×2 {4,5,13,14}
        // centrado · Elevar Fase 2×2 {16,17,25,26}. El upkeep quedó 1 slot a la
        // izquierda (desde {10,11,19,20}): deja 2 slots de distancia con el sync.
        // Fila 3: separador (filler).
        // Fila 4 (anteúltima): Identidad 36 · Permisos 37 · Transferir 39 ·
        // Matriz 2×2 {40,41,49,50} · Apagar 43 · Cerrar 44. Todos los iconos
        // 1×1 viven en la anteúltima fila para que no invadan el borde inferior
        // del panel; el icono de identidad del jugador vive en (36).

        // Slot 4 — Ward status display
        // Icon reflects real protection, not city membership: active while the
        // upkeep projection still covers upcoming charges; inactive only on null
        // projection or GRACIA/EXPIRADO (AVISO/POR_VENCER keep paying → active).
        var statusProjection = vm.upkeepProjection();
        boolean unprotected = statusProjection == null
                || statusProjection.state() == UpkeepProjectionCalculator.State.GRACIA
                || statusProjection.state() == UpkeepProjectionCalculator.State.EXPIRADO;
        String wardIcon = unprotected ? "icon.ward.inactive" : "icon.ward.active";
        // MenuId dinámico: la variante de fondo horneada (ward_status = cristal
        // activo, ward_inactive = cristal apagado) sigue el estado real de
        // protección. Cada apertura reconstruye el def y vuelve a guardar su
        // menuId en VanillaMenuProvider.openMenus, así que el refresco no se
        // ve afectado por el cambio de id según estado.
        String menuId = unprotected ? "ward_inactive" : "ward_status";
        // Slots 3/4/5/12/13/14/21/22/23 (3×3) — Ward status display (9 slots del
        // cristal central del núcleo, coincidiendo con el layout 3×3 horneado).
        items.addAll(MenuItem.block3x3Display(54, 3, wardIcon,
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

        // Slot 36 — Cerrar menú (flecha a la izquierda, lado izquierdo de fila 5)
        items.add(MenuItem.button(36, "menu.close", "&e« Cerrar",
                List.of("&7Cerrar menú"),
                MenuAction.of("cerrar")));

        // Slot 37 — Viewer identity (perfil en fila 5)
        items.add(MenuItem.button(37, "menu.profile", "&6Perfil — Identidad",
                List.of("&7Tu identidad y datos de jugador"),
                MenuAction.of("perfil")));

        // Fila 3 — línea separadora horizontal (menu.line, barra fina del pack)
        for (int s = 27; s <= 35; s++) {
            items.add(MenuItem.display(s, "menu.line", " ", List.of()));
        }

        // Slots 9/10/18/19 (2×2) — Upkeep vault opener (1 slot a la izquierda del
        // layout anterior, 2 slots de distancia del sync).
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
            items.addAll(MenuItem.block2x2Button(54, 9, "icon.upkeep", "&a&lBóveda de Upkeep", upkeepLore,
                    MenuAction.of("ward.upkeep_vault")));
        } else {
            upkeepLore.add("&cNo puedes depositar en este Núcleo");
            items.addAll(MenuItem.block2x2Display(54, 9, "icon.upkeep", "&8&lBóveda de Upkeep", upkeepLore));
        }

        // Slots 16/17/25/26 (2×2) — Score / upgrade
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
            items.addAll(MenuItem.block2x2Button(54, 16, "icon.ward.tier", "&a&lElevar Fase", scoreLore,
                    MenuAction.of("ward.upgrade")));
        } else {
            scoreLore.add("&8Tier máximo alcanzado");
            items.addAll(MenuItem.block2x2Display(54, 16, "icon.ward.tier", "&8&lElevar Fase", scoreLore));
        }

        // Slot 38 — Permissions (fila 5, icono papel)
        List<String> permLore = new ArrayList<>();
        permLore.add("&7Permisos públicos:");
        vm.permissions().forEach(p -> permLore.add("&7- &f" + p.name()));
        boolean containersPublic = vm.permissions().stream()
                .anyMatch(p -> p.name().equals("PUBLIC_CONTAINERS"));
        permLore.add("");
        permLore.add(containersPublic
                ? "&7Contenedores: &aabiertos al público"
                : "&7Contenedores: &csolo miembros");
        permLore.add("&aClic para gestionar cada permiso");
        if (vm.canSetPermissions()) {
            items.add(MenuItem.button(38, "ward.permissions", "&a&lPermisos", permLore,
                    MenuAction.of("ward.permissions")));
        } else {
            permLore.add("&8Solo el owner puede cambiar permisos");
            items.add(MenuItem.display(38, "ward.permissions", "&8&lPermisos", permLore));
        }

        // Slot 39 — Transfer ownership (fila 5, icono casco dorado)
        if (vm.canTransfer()) {
            items.add(MenuItem.button(39, "ward.transfer", "&a&lTransferir",
                    List.of("&7Clic para transferir ownership"),
                    MenuAction.of("ward.transfer")));
        } else {
            items.add(MenuItem.display(39, "ward.transfer", "&8&lTransferir",
                    List.of("&8Solo el owner puede transferir")));
        }

        // Slots 40/41/49/50 (2×2) — City membership
        if (vm.hasCityMembership()) {
            items.addAll(MenuItem.block2x2Button(54, 40, "icon.city.overview",
                    "&a&lMatriz: " + vm.cityName(),
                    List.of("&7Núcleo federado a la Matriz", "&7" + vm.cityName(),
                            "&aClic para gestionar la Matriz"),
                    MenuAction.of("city.open")));
        } else if (vm.canAnnexToCity()) {
            items.addAll(MenuItem.block2x2Button(54, 40, "icon.city.overview", "&a&lFederar a Matriz",
                    List.of("&7Clic para federar a tu Matriz"),
                    MenuAction.of("ward.annex_city")));
        } else {
            items.addAll(MenuItem.block2x2Display(54, 40, "icon.city.overview", "&8&lFederar a Matriz",
                    List.of("&8Necesitas ser owner y no tener Matriz")));
        }

        // Slot 42 — Invitar/Gestionar miembros (fila 5, icono 1x1 azul persona +)
        List<String> memberLore = new ArrayList<>();
        memberLore.add("&7Agrega amigos a tu zona protegida.");
        memberLore.add("&7Miembros actuales: &f" + vm.members().size());
        memberLore.add("&aPodrán construir y abrir cofres.");
        if (vm.canInvite()) {
            memberLore.add("");
            memberLore.add("&aClic para gestionar miembros");
            items.add(MenuItem.button(42, "menu.invite", "&a&lInvitar Jugador", memberLore,
                    MenuAction.of("ward.invite")));
        } else {
            memberLore.add("");
            memberLore.add("&8Solo el owner puede invitar miembros");
            items.add(MenuItem.display(42, "menu.invite", "&8&lInvitar Jugador", memberLore));
        }

        // Slot 43 — Disband / delete (fila 5, TNT)
        if (vm.canDisband()) {
            items.add(MenuItem.button(43, "ward.disband", "§c§lApagar Núcleo",
                    List.of("&cClic para eliminar este Núcleo", "&cEsta acción es irreversible"),
                    MenuAction.of("ward.disband")));
        } else {
            items.add(MenuItem.display(43, "ward.disband", "&8§lApagar Núcleo",
                    List.of("&8Solo el owner puede disolver")));
        }

        items.replaceAll(MenuItem::asCatcher);
        return new MenuDefinition(menuId, dev.dreamcraft.protection.message.Messages.get()
                .tr("menu.title.ward", "&8Núcleo &f{name}", "name", vm.name()), 54, items);
    }
}
