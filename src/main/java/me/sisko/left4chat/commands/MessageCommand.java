package me.sisko.left4chat.commands;

import io.loyloy.nicky.Nicky;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import me.sisko.left4chat.util.Main;
import me.sisko.left4chat.util.Colors;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.json.JSONArray;
import org.json.JSONObject;

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
            p.sendMessage(ChatColor.RED + "You must verify with " + ChatColor.GOLD + "/verify" + ChatColor.RED + " to message other players!");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, SoundCategory.PLAYERS, 5.0f, 0.5f);
            return true;
        }
        if (args.length < 2) {
            p.sendMessage(ChatColor.RED + "Usage: /" + label + " <player> <message>");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, SoundCategory.PLAYERS, 5.0f, 0.5f);
        } else {
            String reciever = args[0];
            String message = "";
            for (int i = 1; i < args.length; ++i) {
                message = String.valueOf(message) + args[i] + " ";
            }
            message = message.substring(0, message.length() - 1);
            Jedis j = new Jedis(Main.plugin.getConfig().getString("redisip"));
            j.auth(Main.plugin.getConfig().getString("redispass"));
                
            JSONArray players = new JSONArray(j.get("minecraft.players"));
            j.close();

            ArrayList<String> possibleUsers = new ArrayList<String>();

            for(int i = 0; i < players.length(); i++) {
                JSONObject player = players.getJSONObject(i);
                String name = player.getString("username");
                String uuid = player.getString("uuid");

                if(name.equalsIgnoreCase(reciever)) {
                    this.sendMessage(p, name, uuid, message);
                    return true;
                }
                if (!name.toLowerCase().startsWith(reciever.toLowerCase())) continue;
                possibleUsers.add(name + "," + uuid);
            }
            // players.forEach((Objet player) -> {
            //     String player = arrstring[i];
            //     String name = player.split(" ")[0];
            //     if (name.equalsIgnoreCase(reciever)) {
            //         this.sendMessage(p, name, message);
            //         return true;
            //     }
            //     if (!name.toLowerCase().startsWith(reciever.toLowerCase())) continue;
            //     possibleUsers.add(name);
            // }
            HashMap<String, String> usernameNickname = new HashMap<String, String>();

            for(int i = 0; i < players.length(); i++) {
                JSONObject player = players.getJSONObject(i);
                String name = player.getString("username");
                String uuid = player.getString("uuid");

                String nickColor = Nicky.getNickDatabase().downloadNick(uuid);
                if (nickColor == null) continue;
                String nick = Colors.strip(nickColor);
                if (reciever.equalsIgnoreCase(nick)) {
                    this.sendMessage(p, name, uuid, message);
                    return true;
                }
                if (nick.toLowerCase().startsWith(reciever.toLowerCase())) {
                    possibleUsers.add(name + "," + uuid);
                }
                usernameNickname.put(name, nickColor);
            }


            HashSet<String> set = new HashSet<String>(possibleUsers);
            possibleUsers.clear();
            possibleUsers.addAll(set);
            if (possibleUsers.size() == 0) {
                p.sendMessage(ChatColor.RED + "No usernames or nicknames start with " + reciever + ".");
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, SoundCategory.PLAYERS, 5.0f, 0.5f);
            } else {
                if (possibleUsers.size() == 1) {
                    String[] player = possibleUsers.get(0).split(",");
                    this.sendMessage(p, player[0], player[1], message);
                    return true;
                }
                String error = ChatColor.RED + "Ambiguous recipient \"" + reciever + "\", do you mean: \n";
                for (String possible : possibleUsers) { 
                    String[] player = possible.split(",");

                    error = String.valueOf(error) + ChatColor.GOLD + "- " + player[0];
                    if (usernameNickname.containsKey(player[0])) {
                        error = String.valueOf(error) + " (Nickname: " + Colors.format("&r" + usernameNickname.get(player[0])) + ChatColor.GOLD + ")";
                    }
                    error = String.valueOf(error) + "\n";
                }
                p.sendMessage(error);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, SoundCategory.PLAYERS, 5.0f, 0.5f);
            }
        }
        return true;
    }

    private void sendMessage(Player p, String name, String uuid, String message) {
        Permission perms = Main.plugin.getPerms();

		
		//message = Colors.formatWithPerm(perms.has(p, "left4chat.format"), 
		//perms.has(p, "left4chat.color"), message);

        Jedis j = new Jedis(Main.plugin.getConfig().getString("redisip"));
        j.auth(Main.plugin.getConfig().getString("redispass"));
        
        JSONArray players = new JSONArray(j.get("minecraft.players"));
        JSONArray afkPlayers = new JSONArray(j.get("minecraft.afk"));

        for (int i = 0; i < players.length(); i++) {
            JSONObject player = players.getJSONObject(i);


            if (!player.getString("uuid").equalsIgnoreCase(uuid)) continue;

            String nick = Nicky.getNickDatabase().downloadNick(uuid);
            if (nick == null) {
                nick = name;
            }
            p.sendMessage(Colors.format("&c[&6You &c-> &6" + nick + "&c]&r " + message));
            boolean afk = false;
            // if(j.get("minecraft.afkplayers") == null) {
            //     j.set("minecraft.afkplayers", "");
            // }
            // for (String afkPlayer : j.get("minecraft.afkplayers").split(",")) {
            //     if (!afkPlayer.equalsIgnoreCase(name)) continue;
            //     afk = true;
            // }

            for(int k = 0; k < afkPlayers.length(); k++) {
                if(afkPlayers.getJSONObject(k).getString("uuid").equalsIgnoreCase(uuid)) afk = true;
            }

            if (afk) {
                p.sendMessage(ChatColor.RED + nick + ChatColor.RED + " is currently AFK and may not respond.");
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, SoundCategory.PLAYERS, 5.0f, 0.5f);
            } else {
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, SoundCategory.PLAYERS, 5.0f, 2.0f);
            }

            String from_nick = Nicky.getNickDatabase().downloadNick(p.getUniqueId().toString());
            if (from_nick == null) {
                from_nick = p.getName();
            }

            JSONObject json = new JSONObject();
            json.put("type", "pm");
            json.put("from", p.getUniqueId().toString());
            json.put("to", uuid);
            json.put("from_name", p.getName());
            json.put("from_nick", from_nick);
            json.put("to_name", name);
            json.put("to_nick", nick);
            json.put("content", message);
            json.put("content_stripped", Colors.strip(message));
            json.put("color", perms.has(p, "left4chat.color"));
            json.put("format", perms.has(p, "left4chat.format"));
            j.publish("minecraft.chat", json.toString());
            j.close();
            return;
        }
        j.close();
        p.sendMessage(ChatColor.RED + "Error: " + name + " is not online.");
    }
}
