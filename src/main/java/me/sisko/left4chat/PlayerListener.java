package me.sisko.left4chat;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;

/**
 * Join and quit handling, activity tracking, and moving players off the server
 * when it stops.
 *
 * <p>Join and quit messages are suppressed here because the proxy announces them
 * network-wide instead -- one message, not one per backend.
 */
public final class PlayerListener implements Listener {

    private final Left4Chat plugin;

    PlayerListener(Left4Chat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        event.joinMessage(null);
        plugin.afk().markActive(event.getPlayer());
        plugin.syncDiscordProfile(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        event.quitMessage(null);
        plugin.afk().forget(event.getPlayer());
    }

    /**
     * Activity tracking.
     *
     * <p>Only fires on a change of block, not on every look packet. The old
     * listener ran on the raw {@link PlayerMoveEvent} and did a full Redis
     * round trip inside it.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) {
            return;
        }
        plugin.afk().markActive(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        plugin.afk().markActive(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        plugin.afk().markActive(event.getPlayer());

        if (event.getMessage().equalsIgnoreCase("/stop")) {
            plugin.evacuate();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onConsoleCommand(ServerCommandEvent event) {
        if (event.getCommand().equalsIgnoreCase("stop")) {
            plugin.evacuate();
        }
    }

}
