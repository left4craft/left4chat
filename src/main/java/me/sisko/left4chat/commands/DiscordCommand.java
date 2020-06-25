package me.sisko.left4chat.commands;

import java.util.Iterator;

import me.sisko.left4chat.sql.AsyncUserSave;
import me.sisko.left4chat.sql.AsyncUserUpdate;
import me.sisko.left4chat.util.Main;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.json.JSONObject;

import redis.clients.jedis.Jedis;

public class DiscordCommand implements CommandExecutor {
    public boolean onCommand(CommandSender sender, Command cmd, String str, String[] args) {
        if (sender instanceof Player) {
            Player p = (Player) sender;
            if (args.length == 0) {
                p.sendMessage(ChatColor.translateAlternateColorCodes((char) '&',
                        "&3[Discord&rSync&3] Join here &l\u00bb &b&ndiscord.left4craft.org"));
            } else {
                String jsonStr = Main.getCodes();
                if (jsonStr == null) {
                    p.sendMessage(ChatColor.RED + "Failed to connect to the discord bot.");
                    return true;
                } else if (jsonStr.equals("")) {
                    jsonStr = "{}";
                }

                String code = args[0];
                JSONObject codes = new JSONObject(jsonStr);

                Iterator<String> discordIds = codes.keys();
                while (discordIds.hasNext()) {
                    String id = discordIds.next();

                    // iterating through all the discord id's, look for a matching code
                    if (codes.getJSONObject(id).getString("code").equals(code)) {
                        
                        // if code is found, remove it and push change to redis
                        codes.remove(id);

                        Jedis j = new Jedis(Main.plugin.getConfig().getString("redisip"));
                        j.auth(Main.plugin.getConfig().getString("redispass"));
                        j.set("discord.synccodes", codes.toString());
                        j.close();

                        // do all the database things
                        new AsyncUserSave().setup(Main.getSQL(), p.getUniqueId().toString(), p.getName(), id)
                                .runTaskAsynchronously((Plugin) Main.plugin);
                        new AsyncUserUpdate().setup(Main.getSQL(), p).runTaskLaterAsynchronously((Plugin) Main.plugin,
                                40L);
                        p.sendMessage(ChatColor.GREEN + "Successfully synced to discord account " + id);
                        return true;
                    }
                }

                p.sendMessage(ChatColor.RED + "Invalid code.");
                return true;
                // String[] parts = tempJson.replace(" ", "").split(",");
                // HashMap<String, String> codes = new HashMap<String, String>();
                // try {
                // for (int i = 0; i < parts.length; ++i) {
                // parts[i] = parts[i].replace("\"", "");
                // parts[i] = parts[i].replace("{", "");
                // parts[i] = parts[i].replace("}", "");
                // String[] subparts = parts[i].split(":");
                // codes.put(subparts[0], subparts[1]);
                // }
                // }
                // catch (ArrayIndexOutOfBoundsException e) {
                // p.sendMessage(ChatColor.RED + "Invalid Code");
                // return true;
                // }
                // String discordID = (String)codes.get(args[0]);
                // if (discordID != null) {
                // discordID = discordID.split("~")[0];
                // codes.remove(args[0]);
                // Jedis j = new Jedis(Main.plugin.getConfig().getString("redisip"));
                // j.auth(Main.plugin.getConfig().getString("redispass"));
                // j.set("discord.synccodes", new JSONObject(codes).toJSONString());
                // new AsyncUserSave().setup(Main.getSQL(), p.getUniqueId().toString(),
                // p.getName(), discordID).runTaskAsynchronously((Plugin)Main.plugin);
                // new AsyncUserUpdate().setup(Main.getSQL(),
                // p).runTaskLaterAsynchronously((Plugin)Main.plugin, 40L);
                // p.sendMessage(ChatColor.GREEN + "Successfully synced to discord account " +
                // discordID);
                // j.close();
                // } else {
                // p.sendMessage(ChatColor.RED + "Invalid code.");
                // }
            }
        } else {
            Main.plugin.getLogger().info("You can't use that command from console!");
        }
        return true;
    }
}
