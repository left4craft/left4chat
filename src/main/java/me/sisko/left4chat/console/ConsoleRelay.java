package me.sisko.left4chat.console;

import java.util.logging.Level;

import me.sisko.left4chat.Left4Chat;
import me.sisko.left4chat.Left4ChatConfig;

/**
 * Runs console commands published to this server's Redis channel.
 *
 * <p>This is what the separate Left4Craft plugin used to do, folded in here.
 * Left4Chat already held a Redis connection and a subscriber thread; Left4Craft
 * opened a second one of each to listen on a single channel and call
 * {@code Bukkit.dispatchCommand}.
 *
 * <p>It is what makes the rest of the network's cross-server commands actually
 * happen: Left4Chat's own captcha kicks and spambot bans, Left4Hub's cosmetics
 * grants, and anything the Discord bot sends all publish here and rely on the
 * receiving server executing them.
 *
 * <h2>This channel is remote command execution</h2>
 *
 * <p>Anything able to publish to Redis can run any console command on this
 * server. That was true of Left4Craft too -- what is new is that every command
 * is now logged before it runs, so there is a record of what was executed and
 * when. Redis must stay unreachable from outside the host network.
 */
public final class ConsoleRelay {

    /** Published by the panel on a restart; the server understands "stop". */
    private static final String RESTART_ALIAS = "restart";
    private static final String STOP = "stop";

    private final Left4Chat plugin;

    public ConsoleRelay(Left4Chat plugin) {
        this.plugin = plugin;
    }

    /**
     * The channel this server listens on.
     *
     * @param config the plugin config
     * @return the channel name
     */
    public static String channel(Left4ChatConfig config) {
        return config.keys().consoleChannel(config.serverName());
    }

    /**
     * Handles one relayed command. Runs on the Redis subscriber thread.
     *
     * @param message the command, without a leading slash
     */
    public void accept(String message) {
        String command = message.strip();
        if (command.isEmpty()) {
            return;
        }

        // The panel publishes "restart"; the server only knows "stop".
        if (command.equalsIgnoreCase(RESTART_ALIAS)) {
            command = STOP;
        }

        plugin.getLogger().info("Console relay: " + command);

        boolean shuttingDown = command.equalsIgnoreCase(STOP);
        String toRun = command;

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (shuttingDown) {
                // Move everyone to the fallback server before the server goes
                // away, so they land on hub rather than being disconnected.
                // Bukkit.dispatchCommand does not fire ServerCommandEvent, so
                // the listener that normally handles this will not see it.
                plugin.evacuate();
            }

            try {
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), toRun);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Relayed command failed: " + toRun, e);
            }
        });
    }

}
