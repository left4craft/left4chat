package me.sisko.left4chat.commands;

import me.sisko.left4chat.util.Main;
import me.sisko.left4chat.util.Colors;

import java.util.UUID;
import redis.clients.jedis.Jedis;
import org.json.JSONArray;

import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import io.loyloy.nicky.Nicky;

public class ListCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

		// Player senderPlayer = (Player) sender;

		Jedis jedis = new Jedis(Main.plugin.getConfig().getString("redisip"));
        jedis.auth(Main.plugin.getConfig().getString("redispass"));
		String jsonStr = jedis.get("minecraft.players");
		JSONArray players = new JSONArray(jsonStr);
		jedis.close();

		int total = players.length();

		TextComponent list = new TextComponent();
		TextComponent staff = new TextComponent();
		TextComponent nonStaff = new TextComponent();
		list.addExtra(Colors.format("&6There are &c" + total +" &6players online:"));
		staff.addExtra(Colors.format("&6Staff: &r"));
		nonStaff.addExtra(Colors.format("&6Players: &r"));

        for (int i = 0; i < players.length(); i++) {
			String uuid = players.getJSONObject(i).getString("uuid");
			String name = players.getJSONObject(i).getString("username");
			String server = players.getJSONObject(i).getString("server");
			Player p = Bukkit.getOfflinePlayer(UUID.fromString(uuid)).getPlayer();
			TextComponent player = new TextComponent();

			String nick = Nicky.getNickDatabase().downloadNick(uuid);
            if (nick == null) {
                nick = name;
			}

			if(Main.plugin.isAFK(p)) {
				player.addExtra(Colors.format("&7[AFK]"));
			}
			
			//"&3" + name + " &7is in &a" + server
			player.addExtra(Colors.format(nick + "&r"));

			if(Main.plugin.getPerms().has(p, "left4craft.staff")) {
				staff.addExtra(" " + player);
			} else {
				nonStaff.addExtra(" " + player);
			}

		}

		list.addExtra("\n" + staff);
		list.addExtra("\n" + nonStaff);
		// list.addExtra(Colors.format("\n\n&7To show which gamemodes these players are on, type &e/glist"));
		
		
		sender.sendMessage(list);

        return true;
    }
}
