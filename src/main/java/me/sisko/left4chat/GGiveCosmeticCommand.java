package me.sisko.left4chat;

import me.sisko.left4chat.Main;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import redis.clients.jedis.Jedis;

public class GGiveCosmeticCommand
implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String str, String[] args) {
        if (sender instanceof Player) {
            ((Player)sender).sendMessage(ChatColor.RED + "Insufficient Permission");
        } else if (args.length < 2) {
            Main.plugin.getLogger().info("Usage: /ggivecosmetic <name> <amount> [tier]");
        } else {
            Jedis j = new Jedis();
            if (args.length == 2) {
                j.publish("minecraft.console.hub.in", "givecosmetic " + args[0] + " " + args[1]);
            } else {
                j.publish("minecraft.console.hub.in", "givecosmetic " + args[0] + " " + args[1] + " " + args[2]);
            }
            j.close();
        }
        return true;
    }
}

