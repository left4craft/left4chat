package me.sisko.left4chat;

import java.util.HashMap;
import me.sisko.left4chat.AsyncUserSave;
import me.sisko.left4chat.AsyncUserUpdate;
import me.sisko.left4chat.Main;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.json.simple.JSONObject;
import redis.clients.jedis.Jedis;

public class DiscordCommand
implements CommandExecutor {
    public boolean onCommand(CommandSender sender, Command cmd, String str, String[] args) {
        if (sender instanceof Player) {
            Player p = (Player)sender;
            if (args.length == 0) {
                p.sendMessage(ChatColor.translateAlternateColorCodes((char)'&', "&3[Discord&rSync&3] Join here &l\u00bb &b&ndiscord.left4craft.org"));
            } else {
                String tempJson = Main.getCodes();
                if (tempJson == null) {
                    p.sendMessage(ChatColor.RED + "Failed to connect to the discord bot.");
                    return true;
                }
                Main.plugin.getLogger().info(tempJson);
                String[] parts = tempJson.replace(" ", "").split(",");
                HashMap<String, String> codes = new HashMap<String, String>();
                try {
                    for (int i = 0; i < parts.length; ++i) {
                        parts[i] = parts[i].replace("\"", "");
                        parts[i] = parts[i].replace("{", "");
                        parts[i] = parts[i].replace("}", "");
                        String[] subparts = parts[i].split(":");
                        codes.put(subparts[0], subparts[1]);
                    }
                }
                catch (ArrayIndexOutOfBoundsException e) {
                    p.sendMessage( r.RED + "Invalid Code");
                    return true;
                }
                String discordID = (String)codes.get(args[0]);
                if (discordID != null) {
                    discordID = discordID.split("~")[0];
                    codes.remove(args[0]);
                    Jedis j = new Jedis();
                    j.set("discord.synccodes", new JSONObject(codes).toJSONString());
                    new AsyncUserSave().setup(Main.getSQL(), p.getUniqueId().toString(), p.getName(), discordID).runTaskAsynchronously((Plugin)Main.plugin);
                    new AsyncUserUpdate().setup(Main.getSQL(), p).runTaskLaterAsynchronously((Plugin)Main.plugin, 40L);
                    p.sendMessage(ChatColor.GREEN + "Successfully synced to discord account " + discordID);
                    j.close();
                } else {
                    p.sendMessage(ChatColor.RED + "Invalid code.");
                }
            }
        } else {
            Main.plugin.getLogger().info("You can't use that command from console!");
        }
        return true;
    }
}

