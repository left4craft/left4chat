package me.sisko.left4chat.integration;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.entity.Player;

/**
 * Permission, group and prefix lookups.
 *
 * <p>The old plugin went through Vault for all of this, which meant Vault's
 * {@code Permission} and {@code Chat} services on top of LuckPerms -- two extra
 * layers over the plugin that already had the answer. Vault is still needed on
 * the server for Essentials and friends; Left4Chat no longer needs it.
 */
public final class Permissions {

    private final LuckPerms luckPerms;

    public Permissions(LuckPerms luckPerms) {
        this.luckPerms = luckPerms;
    }

    /**
     * The player's primary group, lowercased.
     *
     * @param player the player
     * @return the group name, or {@code "default"} if LuckPerms has not loaded them
     */
    public String primaryGroup(Player player) {
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        return user == null ? "default" : user.getPrimaryGroup().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * The display name of the player's primary group.
     *
     * @param player the player
     * @return the player's rank
     */
    public String rank(Player player) {
        String groupName = primaryGroup(player);
        Group group = luckPerms.getGroupManager().getGroup(groupName);
        return group == null ? "Guest" : group.getFriendlyName();
    }

    /**
     * The player's chat prefix as ampersand markup.
     *
     * @param player the player
     * @return the prefix, or an empty string if they have none
     */
    public String prefix(Player player) {
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            return "";
        }
        String prefix = user.getCachedData()
                .getMetaData(queryOptions(user))
                .getPrefix();
        return prefix == null ? "" : prefix;
    }

    /**
     * Whether a player -- possibly on another server -- holds a permission.
     *
     * <p>The old {@code /list} asked whether the user had {@code left4craft.staff}
     * as a directly assigned node, so staff who inherited it from a group were
     * listed as ordinary players. This resolves it the way a permission check
     * should, through the inheritance tree.
     *
     * @param uuid       the player
     * @param permission the permission
     * @return a future resolving to whether they hold it
     */
    public CompletableFuture<Boolean> hasPermission(UUID uuid, String permission) {
        return luckPerms.getUserManager().loadUser(uuid).thenApply(user ->
                user.getCachedData()
                        .getPermissionData(queryOptions(user))
                        .checkPermission(permission)
                        .asBoolean());
    }

    /**
     * Grants a permission for as long as the plugin says so -- used for the
     * sleep-exemption an AFK player gets.
     *
     * @param player     the player
     * @param permission the permission
     * @return a future completing once the change is saved
     */
    public CompletableFuture<Void> grant(Player player, String permission) {
        return modify(player.getUniqueId(), user ->
                user.data().add(Node.builder(permission).build()));
    }

    /**
     * Revokes a permission previously granted with {@link #grant}.
     *
     * @param player     the player
     * @param permission the permission
     * @return a future completing once the change is saved
     */
    public CompletableFuture<Void> revoke(Player player, String permission) {
        return modify(player.getUniqueId(), user ->
                user.data().remove(Node.builder(permission).build()));
    }

    /**
     * Adds a player to a group, unless they are already in it.
     *
     * @param player the player
     * @param group  the group name
     * @return a future resolving to whether anything changed
     */
    public CompletableFuture<Boolean> addToGroup(Player player, String group) {
        return luckPerms.getUserManager().modifyUser(player.getUniqueId(), user -> {
            boolean already = user.getNodes(NodeType.INHERITANCE).stream()
                    .anyMatch(node -> node.getGroupName().equalsIgnoreCase(group));
            if (!already) {
                user.data().add(net.luckperms.api.node.types.InheritanceNode.builder(group).build());
            }
        }).thenApply(ignored -> true);
    }

    private CompletableFuture<Void> modify(UUID uuid, java.util.function.Consumer<User> action) {
        return luckPerms.getUserManager().modifyUser(uuid, action);
    }

    private QueryOptions queryOptions(User user) {
        return luckPerms.getContextManager().getQueryOptions(user)
                .orElseGet(luckPerms.getContextManager()::getStaticQueryOptions);
    }
}
