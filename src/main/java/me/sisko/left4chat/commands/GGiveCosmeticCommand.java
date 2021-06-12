package me.sisko.left4chat.commands;

import me.sisko.left4chat.util.Main;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import redis.clients.jedis.Jedis;

public class GGiveCosmeticCommand
implements CommandExecutor {
    private final String[] rarities = {"normal", "mythical", "legendary"};

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String str, String[] args) {
        if (sender instanceof Player) {
            ((Player)sender).sendMessage(ChatColor.RED + "Insufficient Permission");
        } else if (args.length < 2) {
            Main.plugin.getLogger().info("Usage: /ggivecosmetic <name> <amount> [tier]");
        } else {
            for(Player p : Bukkit.getOnlinePlayers()) {
                if(p.getName().equalsIgnoreCase(args[0])) {
                    boolean plural = Integer.parseInt(args[1]) > 1;
                    if(args.length == 2) {
                        p.sendMessage(ChatColor.GREEN + args[1] + " cosmetic " + (plural ? "coins have" : "coin has") + " been added to your account.");
                    } else {
                        p.sendMessage(ChatColor.GREEN + args[1] + " " + rarities[Integer.parseInt(args[2])] + " " + (plural ? "keys have" : "key has") + " been added to your account.");
                    }
                }
            }


            Jedis j = new Jedis(Main.plugin.getConfig().getString("redisip"), Main.plugin.getConfig().getInt("redisport"));
            j.auth(Main.plugin.getConfig().getString("redispass"));
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

