package me.sisko.left4chat.commands;

import io.loyloy.nicky.Nicky;
import me.sisko.left4chat.util.Main;
import me.sisko.left4chat.util.Colors;

import java.util.ArrayList;
import java.util.List;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.json.JSONArray;

import redis.clients.jedis.Jedis;

public class MessageTabComplete
implements TabCompleter {
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (sender instanceof Player && args.length == 1) {
            ArrayList<String> tabComplete = new ArrayList<String>();
            ArrayList<String> uuids = new ArrayList<String>();
            Jedis j = new Jedis(Main.plugin.getConfig().getString("redisip"));
            j.auth(Main.plugin.getConfig().getString("redispass"));
                
            JSONArray players = new JSONArray(j.get("minecraft.players"));
            
            j.close();
            for (int i = 0; i < players.length(); i++) {
                tabComplete.add(players.getJSONObject(i).getString("username"));
                uuids.add(players.getJSONObject(i).getString("uuid"));
            }
            for (String uuid : uuids) {
                String nick = Nicky.getNickDatabase().downloadNick(uuid);
                if (nick == null) continue;
                tabComplete.add(Colors.strip(nick));
            }
            tabComplete.removeIf(s -> !s.toLowerCase().startsWith(args[0].toLowerCase()));
            if (tabComplete.size() == 0) {
                tabComplete.add(ChatColor.RED + "No usernames or nicknames start with " + args[0] + "!");
            }
            return tabComplete;
        }
        return new ArrayList<String>();
    }
}

