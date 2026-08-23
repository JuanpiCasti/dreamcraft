package dev.dreamcraft.protection.presentation.menu;

import dev.dreamcraft.protection.domain.model.CityRole;
import dev.dreamcraft.protection.presentation.MenuAction;
import dev.dreamcraft.protection.presentation.MenuDefinition;
import dev.dreamcraft.protection.presentation.MenuItem;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds the {@link MenuDefinition}s of the reusable online-player picker and
 * the second-step city role picker.
 *
 * <p>Pure data construction — no Bukkit types. Heads use the dynamic icon key
 * {@code icon.player.<uuid>}, which {@code VanillaMenuProvider} renders as a
 * PLAYER_HEAD with SkullMeta owned by that player.
 *
 * <p><b>Payload grammar</b> (stateless navigation — everything travels in the
 * payload, like the admin GUIs):
 * <pre>
 *   pick.player   :{accion}:{origen}:{entidadId}:{pagina}   open/turn page
 *   pick.target   :{accion}:{origen}:{entidadId}:{targetUuid}
 *   pick.role     :{playerUuid}:{cityId}
 *   pick.role.set :{rol}:{playerUuid}:{cityId}
 *   pick.back     :{origen}:{entidadId}
 * </pre>
 * where {@code accion} ∈ ward.transfer · city.invite · city.kick · city.roles ·
 * city.transfer · estate.invite · estate.transfer and {@code origen} ∈ ward ·
 * city · estate (the menu to return to).
 *
 * <p><b>v1 limit:</b> the picker lists ONLINE players only. The underlying
 * services would accept offline UUIDs for kick/transfer-style actions; chat or
 * conversation input for offline targets is intentionally out of scope here.
 */
public final class PlayerPickMenuBuilder {

    public static final String MENU_ID_PLAYERS = "player_pick";
    public static final String MENU_ID_ROLES = "role_pick";

    /** 45 heads per page (slots 0–44), nav row at 45/49/53 — admin overview style. */
    private static final int PAGE_SIZE = 45;

    private PlayerPickMenuBuilder() {}

    /** One selectable online player (display data only). */
    public record Candidate(UUID playerId, String name) {}

    /**
     * Paginated head grid of the candidates for a pending action.
     * Slot 45 «Anterior», slot 49 «Volver» (back to the origin menu) and slot
     * 53 «Siguiente» / «Cerrar».
     */
    public static MenuDefinition buildPlayers(String pendingAction, String origin,
                                              UUID entityId, int page,
                                              List<Candidate> candidates) {
        int pages = Math.max(1, (candidates.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.min(Math.max(0, page), pages - 1);
        int from = current * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, candidates.size());

        List<MenuItem> items = new ArrayList<>();
        String context = pendingAction + ":" + origin + ":" + entityId;
        for (int i = from; i < to; i++) {
            Candidate candidate = candidates.get(i);
            items.add(MenuItem.button(i - from, "icon.player." + candidate.playerId(),
                    "&f" + candidate.name(),
                    List.of("&7Clic para seleccionar"),
                    MenuAction.of("pick.target",
                            context + ":" + candidate.playerId())));
        }
        if (current > 0) {
            items.add(MenuItem.button(45, "icon.back", "&e« Anterior",
                    List.of("&7Página " + current),
                    MenuAction.of("pick.player", context + ":" + (current - 1))));
        }
        items.add(MenuItem.button(49, "icon.back", "&e« Volver",
                List.of("&7Cancela y vuelve al menú anterior"),
                MenuAction.of("pick.back", origin + ":" + entityId)));
        if (to < candidates.size()) {
            items.add(MenuItem.button(53, "icon.back", "&eSiguiente »",
                    List.of("&7Página " + (current + 2) + " de " + pages),
                    MenuAction.of("pick.player", context + ":" + (current + 1))));
        } else {
            items.add(MenuItem.button(53, "icon.back", "&c&lCerrar",
                    List.of("&7Cerrar menú"), MenuAction.of("menu.close")));
        }

        String title = dev.dreamcraft.protection.message.Messages.get()
                .tr("menu.title.pick-player", "&8Seleccionar jugador")
                + " §8(" + (current + 1) + "/" + pages + ")";
        return new MenuDefinition(MENU_ID_PLAYERS, title, 54, items);
    }

    /**
     * Second step of {@code city.roles}: pick one of the real {@link CityRole}
     * values for an already-picked member. Clicking GOVERNOR transfers the
     * governorship (same contract as {@code /city roles}).
     */
    public static MenuDefinition buildRoles(UUID targetId, String targetName, UUID cityId) {
        List<MenuItem> items = new ArrayList<>();
        CityRole[] roles = CityRole.values();
        for (int i = 0; i < roles.length; i++) {
            CityRole role = roles[i];
            int slot = 10 + i * 2;
            items.add(MenuItem.button(slot, "icon.members",
                    "&6&l" + roleLabel(role), roleLore(role),
                    MenuAction.of("pick.role.set",
                            role.name() + ":" + targetId + ":" + cityId)));
        }
        items.add(MenuItem.button(22, "icon.back", "&c&lCerrar",
                List.of("&7Cerrar menú"), MenuAction.of("menu.close")));

        String title = dev.dreamcraft.protection.message.Messages.get()
                .tr("menu.title.pick-role", "&8Rol para &f{name}", "name", targetName);
        return new MenuDefinition(MENU_ID_ROLES, title, 27, items);
    }

    private static String roleLabel(CityRole role) {
        return switch (role) {
            case GOVERNOR -> dev.dreamcraft.protection.message.Messages.get()
                    .tr("menu.role.GOVERNOR", "Gobernador");
            case COUNCIL -> dev.dreamcraft.protection.message.Messages.get()
                    .tr("menu.role.COUNCIL", "Council");
            case CITIZEN -> dev.dreamcraft.protection.message.Messages.get()
                    .tr("menu.role.CITIZEN", "Ciudadano");
            case ALLY -> dev.dreamcraft.protection.message.Messages.get()
                    .tr("menu.role.ALLY", "Aliado");
        };
    }

    private static List<String> roleLore(CityRole role) {
        return switch (role) {
            case GOVERNOR -> List.of(
                    "&7Transfiere la Gobernanza a este miembro.",
                    "&cEl Gobernador actual pasa a Council.",
                    "&aClic para asignar");
            case COUNCIL -> List.of(
                    "&7Gestiona residentes y el tesoro.",
                    "&aClic para asignar");
            case CITIZEN -> List.of(
                    "&7Rol base de habitante.",
                    "&aClic para asignar");
            case ALLY -> List.of(
                    "&7Aliado de la ciudad.",
                    "&aClic para asignar");
        };
    }
}
