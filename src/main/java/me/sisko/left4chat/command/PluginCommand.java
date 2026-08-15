package me.sisko.left4chat.command;

import me.sisko.left4chat.Left4Chat;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

/**
 * Base for Left4Chat's commands.
 *
 * <p>All twelve commands moved from {@code plugin.yml} plus
 * {@link org.bukkit.command.CommandExecutor} to Paper's Brigadier registrar.
 * That is what {@code paper-plugin.yml} requires, and it gets the commands into
 * the client's command tree so unknown arguments are rejected before they reach
 * the server.
 */
abstract class PluginCommand implements BasicCommand {

    protected final Left4Chat plugin;

    PluginCommand(Left4Chat plugin) {
        this.plugin = plugin;
    }

    /**
     * Runs work off the main thread. Anything touching Redis or Postgres goes
     * through here.
     *
     * @param runnable the work
     */
    protected void async(Runnable runnable) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable);
    }

    /**
     * Runs work on the main thread. Anything touching a live player goes
     * through here.
     *
     * @param runnable the work
     */
    protected void sync(Runnable runnable) {
        plugin.getServer().getScheduler().runTask(plugin, runnable);
    }

    /**
     * Joins the remaining arguments back into a message.
     *
     * @param args the arguments
     * @param from the index to start at
     * @return the joined text
     */
    protected static String join(String[] args, int from) {
        return String.join(" ", java.util.Arrays.copyOfRange(args, from, args.length));
    }

    protected static void error(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.RED));
    }

    /**
     * Sends the "console only" note the original logged rather than replied
     * with.
     *
     * @param source who ran the command
     */
    protected void consoleOnly(CommandSourceStack source) {
        error(source.getSender(), "That command can only be run from the console.");
    }
}
