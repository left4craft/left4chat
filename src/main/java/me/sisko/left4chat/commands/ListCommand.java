package me.sisko.left4chat.commands;

import me.sisko.left4chat.util.Main;
import me.sisko.left4chat.util.Colors;

import java.util.UUID;
import redis.clients.jedis.Jedis;
import org.json.JSONArray;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.HoverEvent.Action;
import net.md_5.bungee.api.chat.hover.content.Text;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;

import io.loyloy.nicky.Nicky;
import net.milkbowl.vault.permission.Permission;

public class ListCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

		// Player senderPlayer = (Player) sender;

		Permission perms = new Permission();

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
			TextComponent player = new TextComponent();

			Player p = Bukkit.getOfflinePlayer(UUID.fromString(uuid)).getPlayer();

			String nick = Nicky.getNickDatabase().downloadNick(uuid);
            if (nick == null) {
                nick = name;
			}

			// player.addExtra(Colors.format(i == 0 ? "&r " : "&r, "));
			
			if(Main.plugin.isAFK(uuid)) {
				player.addExtra(Colors.format("&8[AFK]&#808080" + Colors.strip(nick) + "&r"));
			} else {
				player.addExtra(Colors.format(nick + "&r"));
			}
			
			
			player.setHoverEvent(new HoverEvent(Action.SHOW_TEXT, new Text(ChatColor.GRAY  + name + ChatColor.GRAY + "i s in " + ChatColor.GREEN + server)));


			if(perms.playerHas(p, "left4craft.staff")) {
				if(i != 0 && i != total -1) nonStaff.addExtra(Colors.format(", "));
				staff.addExtra(player);
			} else {
				if(i != 0 && i != total -1) nonStaff.addExtra(Colors.format(", "));
				nonStaff.addExtra(player);
			}

		}

		list.addExtra("\n");
		list.addExtra(staff);
		list.addExtra("\n");
		list.addExtra(nonStaff);
		list.addExtra(Colors.format("\n\n&7To sort players by gamemode, type &e/glist"));
		
		
		sender.sendMessage(list);

        return true;
    }
}
