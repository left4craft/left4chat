package me.sisko.left4chat.ui;

import java.util.EnumSet;
import java.util.Set;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import me.sisko.left4chat.Left4Chat;
import me.sisko.left4chat.Left4ChatConfig;
import me.sisko.left4chat.text.AmpersandColors;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

/**
 * The {@code /game} menu.
 *
 * <p>Two fixes over the old version. It built one static {@link Inventory} at
 * startup and opened <em>that same instance</em> for every player, so two people
 * with the menu open were looking into the same container. And it identified the
 * menu by comparing {@code InventoryView#getTitle()} to a hardcoded string,
 * which any other plugin could collide with; this uses an
 * {@link InventoryHolder} marker instead, which cannot be spoofed.
 *
 * <p>The servers themselves moved from a hardcoded if-else chain over
 * {@link org.bukkit.Material} to the {@code selector} block in config.yml.
 */
public final class ServerSelector implements Listener {

    /** Marks an inventory as ours. */
    private record Holder(Left4ChatConfig.Selector selector) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            throw new UnsupportedOperationException();
        }
    }

    private static final Set<ItemFlag> HIDE_EVERYTHING = EnumSet.of(
            ItemFlag.HIDE_ATTRIBUTES,
            ItemFlag.HIDE_DESTROYS,
            ItemFlag.HIDE_ENCHANTS,
            ItemFlag.HIDE_PLACED_ON,
            ItemFlag.HIDE_UNBREAKABLE);

    private final Left4Chat plugin;

    public ServerSelector(Left4Chat plugin) {
        this.plugin = plugin;
    }

    /**
     * Opens the menu for a player. Must run on the main thread.
     *
     * @param player the player
     */
    public void open(Player player) {
        Left4ChatConfig.Selector selector = plugin.config().selector();

        Inventory inventory = plugin.getServer().createInventory(
                new Holder(selector), 9, AmpersandColors.format(selector.title()));

        for (Left4ChatConfig.Selector.Entry entry : selector.entries()) {
            ItemStack icon = ItemStack.of(entry.material());
            icon.editMeta(meta -> {
                meta.displayName(AmpersandColors.format(entry.name())
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                meta.addItemFlags(HIDE_EVERYTHING.toArray(ItemFlag[]::new));
            });
            inventory.setItem(entry.slot(), icon);
        }

        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder(Left4ChatConfig.Selector selector))) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        plugin.afk().markActive(player);

        int slot = event.getRawSlot();
        player.closeInventory();

        selector.entries().stream()
                .filter(entry -> entry.slot() == slot)
                .filter(entry -> !entry.server().isEmpty())
                .findFirst()
                .ifPresent(entry -> connect(player, entry.server()));
    }

    /**
     * Asks the proxy to move a player to another server.
     *
     * @param player the player
     * @param server the target server, as the proxy knows it
     */
    public void connect(Player player, String server) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(server);
        // Velocity implements the same channel BungeeCord did.
        player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
    }
}
