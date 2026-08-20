package dev.dreamcraft.protection.integration.luckperms;

import dev.dreamcraft.protection.integration.registry.CapabilityRegistry;
import dev.dreamcraft.protection.integration.registry.IntegrationKey;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.query.QueryOptions;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * LuckPerms 5.x implementation of {@link LuckPermsAdapter}.
 *
 * <p>Uses the LuckPerms API (stable since LP 5.0).
 * Users are loaded synchronously via the user manager.
 *
 * <p><b>API surface used:</b>
 * <ul>
 *   <li>{@code LuckPermsProvider.get()} — gets the LP API instance</li>
 *   <li>{@code LuckPerms#getUserManager()} — user operations</li>
 *   <li>{@code User#getCachedData().getPermissionData().checkPermission(node)}</li>
 *   <li>{@code User#getPrimaryGroup()}</li>
 * </ul>
 */
public final class LuckPermsAdapterImpl implements LuckPermsAdapter {

    private final CapabilityRegistry registry;
    private final Logger logger;

    public LuckPermsAdapterImpl(CapabilityRegistry registry, Logger logger) {
        this.registry = registry;
        this.logger = logger;
    }

    @Override
    public boolean isAvailable() {
        return registry.isAvailable(IntegrationKey.LUCK_PERMS);
    }

    @Override
    public boolean hasPermission(UUID playerId, String permission) {
        if (!isAvailable()) return false;
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getUserManager().getUser(playerId);
            if (user == null) return false;
            return user.getCachedData()
                    .getPermissionData(QueryOptions.nonContextual())
                    .checkPermission(permission)
                    .asBoolean();
        } catch (Exception e) {
            logger.warning("[LuckPerms] hasPermission failed for " + playerId + ": " + e.getMessage());
            return false;
        }
    }

    @Override
    public void grantPermission(UUID playerId, String permission) {
        if (!isAvailable()) return;
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getUserManager().getUser(playerId);
            if (user == null) return;
            user.data().add(Node.builder(permission).build());
            lp.getUserManager().saveUser(user);
        } catch (Exception e) {
            logger.warning("[LuckPerms] grantPermission failed for " + playerId + ": " + e.getMessage());
        }
    }

    @Override
    public void revokePermission(UUID playerId, String permission) {
        if (!isAvailable()) return;
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getUserManager().getUser(playerId);
            if (user == null) return;
            user.data().remove(Node.builder(permission).build());
            lp.getUserManager().saveUser(user);
        } catch (Exception e) {
            logger.warning("[LuckPerms] revokePermission failed for " + playerId + ": " + e.getMessage());
        }
    }

    @Override
    public String primaryGroup(UUID playerId) {
        if (!isAvailable()) return null;
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getUserManager().getUser(playerId);
            return user != null ? user.getPrimaryGroup() : null;
        } catch (Exception e) {
            logger.warning("[LuckPerms] primaryGroup failed for " + playerId + ": " + e.getMessage());
            return null;
        }
    }
}
