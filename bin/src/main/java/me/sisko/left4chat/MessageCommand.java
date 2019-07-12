/*
 * Decompiled with CFR 0.145.
 * 
 * Could not load the following classes:
 *  io.loyloy.nicky.Nicky
 *  net.milkbowl.vault.permission.Permission
 *  org.bukkit.ChatColor
 *  org.bukkit.Location
 *  org.bukkit.Sound
 *  org.bukkit.SoundCategory
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 */
package me.sisko.left4chat;

import io.loyloy.nicky.Nicky;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.logging.Logger;
import me.sisko.left4chat.Main;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import redis.clients.jedis.Jedis;

public class MessageCommand
implements CommandExecutor {
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            Main.plugin.getLogger().info("You can't message from console!");
            return true;
        }
        Player p = (Player)sender;
        if (!Main.plugin.getPerms().has(p, "left4chat.verified")) {
            p.sendMessage((Object)ChatColor.RED + "You must verify with " + (Object)ChatColor.GOLD + "/verify" + (Object)ChatColor.RED + " to message other players!");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, SoundCategory.PLAYERS, 5.0f, 0.5f);
            return true;
        }
        if (args.length < 2) {
            p.sendMessage((Object)ChatColor.RED + "Usage: /" + label + " <player> <message>");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, SoundCategory.PLAYERS, 5.0f, 0.5f);
        } else {
            String reciever = args[0];
            String message = "";
            for (int i = 1; i < args.length; ++i) {
                message = String.valueOf(message) + args[i] + " ";
            }
            message = message.substring(0, message.length() - 1);
            Jedis j = new Jedis();
            String[] players = j.get("minecraft.players").split(",");
            ArrayList<Object> possibleUsers = new ArrayList<Object>();
            j.close();
            String[] arrstring = players;
            int n = arrstring.length;
            for (int i = 0; i < n; ++i) {
                String player = arrstring[i];
                String name = player.split(" ")[0];
                if (name.equalsIgnoreCase(reciever)) {
                    this.sendMessage(p, name, message);
                    return true;
                }
                if (!name.toLowerCase().startsWith(reciever.toLowerCase())) continue;
                possibleUsers.add(name);
            }
            HashMap<String, String> usernameNickname = new HashMap<String, String>();
            for (String player : players) {
                String name = player.split(" ")[0];
                String nickColor = Nicky.getNickDatabase().downloadNick(player.split(" ")[1]);
                if (nickColor == null) continue;
                String nick = ChatColor.stripColor((String)ChatColor.translateAlternateColorCodes((char)'&', (String)nickColor));
                if (reciever.equalsIgnoreCase(nick)) {
                    this.sendMessage(p, name, message);
                    return true;
                }
                if (nick.toLowerCase().startsWith(reciever.toLowerCase())) {
                    possibleUsers.add(name);
                }
                usernameNickname.put(name, nickColor);
            }
            HashSet set = new HashSet(possibleUsers);
            possibleUsers.clear();
            possibleUsers.addAll(set);
            if (possibleUsers.size() == 0) {
                p.sendMessage((Object)ChatColor.RED + "No usernames or nicknames start with " + reciever + ".");
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, SoundCategory.PLAYERS, 5.0f, 0.5f);
            } else {
                if (possibleUsers.size() == 1) {
                    this.sendMessage(p, (String)possibleUsers.get(0), message);
                    return true;
                }
                String error = (Object)ChatColor.RED + "Ambiguous recipient \"" + reciever + "\", do you mean: \n";
                for (String possible : possibleUsers) {
                    error = String.valueOf(error) + (Object)ChatColor.GOLD + "- " + possible;
                    if (usernameNickname.containsKey(possible)) {
                        error = String.valueOf(error) + " (Nickname: " + ChatColor.translateAlternateColorCodes((char)'&', (String)("&r" + (String)usernameNickname.get(possible))) + (Object)ChatColor.GOLD + ")";
                    }
                    error = String.valueOf(error) + "\n";
                }
                p.sendMessage(error);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, SoundCategory.PLAYERS, 5.0f, 0.5f);
            }
        }
        return true;
    }

    private void sendMessage(Player p, String name, String message) {
        Permission perms = Main.plugin.getPerms();
        if (!perms.has(p, "left4chat.format")) {
            if (perms.has(p, "left4chat.color")) {
                String[] formats = new String[]{"&l", "&k", "&m", "&n", "&o"};
                for (String format : formats) {
                    while (message.contains(format)) {
                        message = message.replace(format, "");
                    }
                }
            } else {
                message = ChatColor.stripColor((String)ChatColor.translateAlternateColorCodes((char)'&', (String)message));
            }
        }
        Jedis j = new Jedis();
        for (String player : j.get("minecraft.players").split(",")) {
            if (!player.split(" ")[0].equalsIgnoreCase(name)) continue;
            String nick = Nicky.getNickDatabase().downloadNick(player.split(" ")[1]);
            if (nick == null) {
                nick = name;
            }
            p.sendMessage(ChatColor.translateAlternateColorCodes((char)'&', (String)("&c[&6You &c-> &6" + nick + "&c]&r " + message)));
            boolean afk = false;
            for (String afkPlayer : j.get("minecraft.afkplayers").split(",")) {
                if (!afkPlayer.equalsIgnoreCase(name)) continue;
                afk = true;
            }
            if (afk) {
                p.sendMessage((Object)ChatColor.RED + nick + (Object)ChatColor.RED + " is currently AFK and may not respond.");
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, SoundCategory.PLAYERS, 5.0f, 0.5f);
            } else {
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, SoundCategory.PLAYERS, 5.0f, 2.0f);
            }
            j.publish("minecraft.chat.messages", String.valueOf(p.getName()) + "," + name + "," + message);
            j.close();
            return;
        }
        j.close();
        p.sendMessage((Object)ChatColor.RED + "Error: " + name + " is not online.");
    }
}

