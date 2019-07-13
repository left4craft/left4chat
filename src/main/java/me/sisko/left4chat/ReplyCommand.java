package me.sisko.left4chat;

import java.util.HashMap;
import me.sisko.left4chat.Main;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import redis.clients.jedis.Jedis;

public class ReplyCommand
implements CommandExecutor {
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            Main.plugin.getLogger().info("You can't reply from console!");
            return true;
        }
        Player p = (Player)sender;
        if (!Main.plugin.getPerms().has(p, "left4chat.verified")) {
            p.sendMessage(ChatColor.RED + "You must verify with " + ChatColor.GOLD + "/verify" + ChatColor.RED + " to message other players!");
            return true;
        }
        if (args.length < 1) {
            p.sendMessage(ChatColor.RED + "Usage: /" + label + " <message>");
        } else {
            String name = p.getName();
            Jedis j = new Jedis();
            String tempJson = j.get("minecraft.chat.replies");
            String[] parts = tempJson.replace(" ", "").split(",");
            HashMap<String, String> replies = new HashMap<String, String>();
            try {
                for (int i = 0; i < parts.length; ++i) {
                    parts[i] = parts[i].replace("\"", "");
                    parts[i] = parts[i].replace("{", "");
                    parts[i] = parts[i].replace("}", "");
                    String[] subparts = parts[i].split(":");
                    replies.put(subparts[0], subparts[1]);
                }
            }
            catch (ArrayIndexOutOfBoundsException e) {
                p.sendMessage(ChatColor.RED + "Invalid JSON in minecraft.chat.replies");
            }
            if (!replies.containsKey(name)) {
                p.sendMessage(ChatColor.RED + "There is nobody to whom you can reply.");
            } else {
                for (String player : j.get("minecraft.players").split(",")) {
                    if (!player.split(" ")[0].equalsIgnoreCase((String)replies.get(name))) continue;
                    String message = "";
                    for (String arg : args) {
                        message = String.valueOf(message) + arg + " ";
                    }
                    message = message.substring(0, message.length() - 1);
                    p.chat("/msg " + (String)replies.get(name) + " " + message);
                    j.close();
                    return true;
                }
                p.sendMessage(ChatColor.RED + (String)replies.get(name) + " is currently offline.");
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, SoundCategory.PLAYERS, 5.0f, 0.5f);
            }
            j.close();
        }
        return true;
    }
}

