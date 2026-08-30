package dev.dreamcraft.protection.presentation.menu;

import dev.dreamcraft.protection.domain.model.City;
import dev.dreamcraft.protection.domain.model.CityPolicy;
import dev.dreamcraft.protection.domain.model.Ward;
import dev.dreamcraft.protection.domain.model.WardPermission;
import dev.dreamcraft.protection.presentation.MenuAction;
import dev.dreamcraft.protection.presentation.MenuDefinition;
import dev.dreamcraft.protection.presentation.MenuItem;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds the {@link MenuDefinition}s of the two governance sub-menus: one listing
 * the active {@link CityPolicy}s of a Matriz and one the active {@link WardPermission}s
 * of a Núcleo. Each option is a single button that toggles that specific flag, so the
 * viewer can switch them one by one (replacing the old single-toggle buttons).
 *
 * <p>Pure data construction — no Bukkit types. The city menu opens from
 * {@code city.policies} (Matriz overview) and the ward one from
 * {@code ward.permissions} (Núcleo/sync status). Toggles reuse the existing
 * {@code city.policy} / {@code ward.toggle_permission} dispatcher actions with the
 * option name as payload, and reopen this sub-menu afterwards so the viewer can keep
 * flipping flags. «Volver» returns to the parent menu via the origin re-opener.
 */
public final class PolicyMenuBuilder {

    public static final String MENU_ID_CITY_POLICIES = "city_policies";
    public static final String MENU_ID_WARD_PERMISSIONS = "ward_permissions";

    private PolicyMenuBuilder() {}

    /** One centered slot per toggleable option in a 27-slot menu. */
    private static final int[] SLOTS = {11, 13, 15, 17, 19};

    /** Builds the Matriz policies sub-menu (one toggle per {@link CityPolicy}). */
    public static MenuDefinition buildCityPolicies(City city, UUID viewerId) {
        boolean governor = city.isGovernor(viewerId);
        List<MenuItem> items = new ArrayList<>();
        CityPolicy[] policies = CityPolicy.values();
        for (int i = 0; i < policies.length; i++) {
            CityPolicy policy = policies[i];
            boolean active = city.hasPolicy(policy);
            List<String> lore = policyLore(policy, active, governor);
            if (governor) {
                items.add(MenuItem.button(SLOTS[i],
                        active ? "icon.city.overview" : "icon.ward.inactive",
                        (active ? "&a&l" : "&8&l") + policyLabel(policy), lore,
                        MenuAction.of("city.policy", policy.name())));
            } else {
                items.add(MenuItem.display(SLOTS[i],
                        active ? "icon.city.overview" : "icon.ward.inactive",
                        (active ? "&a&l" : "&8&l") + policyLabel(policy), lore));
            }
        }
        items.add(MenuItem.button(22, "icon.back", "&e« Volver",
                List.of("&7Vuelve a la Matriz"),
                MenuAction.of("city.policies.back", city.id().toString())));
        String title = dev.dreamcraft.protection.message.Messages.get()
                .tr("menu.title.city-policies", "&8Políticas de la Matriz");
        return new MenuDefinition(MENU_ID_CITY_POLICIES, title, 27, items);
    }

    /** Builds the Núcleo permissions sub-menu (one toggle per {@link WardPermission}). */
    public static MenuDefinition buildWardPermissions(Ward ward, UUID viewerId) {
        boolean owner = ward.ownerId().equals(viewerId);
        List<MenuItem> items = new ArrayList<>();
        WardPermission[] perms = WardPermission.values();
        for (int i = 0; i < perms.length; i++) {
            WardPermission perm = perms[i];
            boolean active = ward.hasPermission(perm);
            List<String> lore = permissionLore(perm, active, owner);
            if (owner) {
                items.add(MenuItem.button(SLOTS[i],
                        active ? "icon.ward.active" : "icon.ward.inactive",
                        (active ? "&a&l" : "&8&l") + permissionLabel(perm), lore,
                        MenuAction.of("ward.toggle_permission", perm.name())));
            } else {
                items.add(MenuItem.display(SLOTS[i],
                        active ? "icon.ward.active" : "icon.ward.inactive",
                        (active ? "&a&l" : "&8&l") + permissionLabel(perm), lore));
            }
        }
        items.add(MenuItem.button(22, "icon.back", "&e« Volver",
                List.of("&7Vuelve al Núcleo"),
                MenuAction.of("ward.permissions.back", ward.id().toString())));
        String title = dev.dreamcraft.protection.message.Messages.get()
                .tr("menu.title.ward-permissions", "&8Permisos del Núcleo");
        return new MenuDefinition(MENU_ID_WARD_PERMISSIONS, title, 27, items);
    }

    private static String policyLabel(CityPolicy policy) {
        return switch (policy) {
            case AUTO_ASSOCIATE_WARDS -> "Asociación automática de Núcleos";
            case OPEN_RECRUITMENT -> "Reclutamiento abierto";
            case FREE_WARD_CREATION -> "Creación libre de Núcleos";
            case COUNCIL_TREASURY_APPROVAL -> "Tesoro con aprobación de Council";
            case PUBLIC_LISTING -> "Matriz en listado público";
        };
    }

    private static List<String> policyLore(CityPolicy policy, boolean active, boolean governor) {
        String desc = switch (policy) {
            case AUTO_ASSOCIATE_WARDS -> "Los Núcleos de los miembros se asocian solos al crearse cerca.";
            case OPEN_RECRUITMENT -> "Los miembros invitan sin aprobación del Gobernador.";
            case FREE_WARD_CREATION -> "Los ciudadanos crean Núcleos sin aprobación del Gobernador.";
            case COUNCIL_TREASURY_APPROVAL -> "Los retiros del tesoro requieren voto de Council.";
            case PUBLIC_LISTING -> "La Matriz figura en los listados públicos.";
        };
        List<String> lore = new ArrayList<>();
        lore.add("&7" + desc);
        lore.add("");
        lore.add(active ? "&aEstado: ACTIVA" : "&8Estado: INACTIVA");
        if (governor) {
            lore.add(active ? "&aClic para desactivar" : "&aClic para activar");
        } else {
            lore.add("&8Solo el Gobernador puede cambiarlas");
        }
        return lore;
    }

    private static String permissionLabel(WardPermission perm) {
        return switch (perm) {
            case PUBLIC_BUILD -> "Construcción pública";
            case PUBLIC_BREAK -> "Rotura pública";
            case PUBLIC_CONTAINERS -> "Contenedores públicos";
            case PUBLIC_UPKEEP_DEPOSIT -> "Depósito de upkeep público";
            case PUBLIC_STATUS_VIEW -> "Ver estado público";
        };
    }

    private static List<String> permissionLore(WardPermission perm, boolean active, boolean owner) {
        String desc = switch (perm) {
            case PUBLIC_BUILD -> "Permite construir a los no-miembros dentro del Núcleo.";
            case PUBLIC_BREAK -> "Permite romper bloques a los no-miembros dentro del Núcleo.";
            case PUBLIC_CONTAINERS -> "Abre contenedores a los no-miembros (espejo del flag chest-access).";
            case PUBLIC_UPKEEP_DEPOSIT -> "Permite depositar upkeep a los no-miembros.";
            case PUBLIC_STATUS_VIEW -> "Permite ver el estado del Núcleo a los no-miembros.";
        };
        List<String> lore = new ArrayList<>();
        lore.add("&7" + desc);
        lore.add("");
        lore.add(active ? "&aEstado: CONCEDIDO" : "&8Estado: DENEGADO");
        if (owner) {
            lore.add(active ? "&aClic para revocar" : "&aClic para conceder");
        } else {
            lore.add("&8Solo el owner puede cambiarlos");
        }
        return lore;
    }
}
