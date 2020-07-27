package me.sisko.left4chat.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import org.json.JSONObject;

import me.sisko.left4chat.util.Main;
import redis.clients.jedis.Jedis;

public class AnnounceCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String str, String[] args) {
        if (!(sender instanceof Player)) {
            String msg = "";
            for (String arg : args) {
                msg = String.valueOf(msg) + arg + " ";
            }
            Jedis j = new Jedis(Main.plugin.getConfig().getString("redisip"));
			j.auth(Main.plugin.getConfig().getString("redispass"));

			HashMap<String, String> out = new HashMap<String, String>();
			out.put("type", "broadcast");
			out.put("message", ChatColor.stripColor(msg));

            j.publish("minecraft.chat.global.in", msg);
            j.publish("minecraft.chat.global.out", new JSONObject(out).toString());
            j.close();
        } else {
            sender.sendMessage(ChatColor.RED + "Insufficient Permission.");
        }
        return true;
    }
}

