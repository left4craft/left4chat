package me.sisko.left4chat.commands;

import me.sisko.left4chat.util.Main;
import me.sisko.left4chat.util.Colors;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
import org.bukkit.scheduler.BukkitRunnable;
// import org.bukkit.OfflinePlayer;
// import org.bukkit.Bukkit;

import io.loyloy.nicky.Nicky;
import net.luckperms.api.node.Node;

public class ListCommand implements CommandExecutor {

	public CompletableFuture<Boolean> isStaff(UUID uuid) {
		return Main.plugin.getLuck().getUserManager().loadUser(uuid)
				.thenApplyAsync(user -> user.getNodes().contains(
					Node.builder("left4craft.staff").build()));
	}


    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

		// Player senderPlayer = (Player) sender;

		Jedis jedis = new Jedis(Main.plugin.getConfig().getString("redisip"), Main.plugin.getConfig().getInt("redisport"));
        jedis.auth(Main.plugin.getConfig().getString("redispass"));
		String jsonStr = jedis.get("minecraft.players");
		JSONArray players = new JSONArray(jsonStr);
		jedis.close();

		int total = players.length();
		int s = 0;

		TextComponent list = new TextComponent();
		TextComponent staff = new TextComponent();
		TextComponent nonStaff = new TextComponent();

		list.addExtra(Colors.format("&6There are &c" + total +" &6" + (total == 1 ? "player" : "players") + " online:\n"));
		staff.addExtra(Colors.format("&6Staff: &r"));
		nonStaff.addExtra(Colors.format("&6Players: &r"));

        for (int i = 0; i < players.length(); i++) {
			String uuid = players.getJSONObject(i).getString("uuid");
			String name = players.getJSONObject(i).getString("username");
			String server = players.getJSONObject(i).getString("server");
			TextComponent player = new TextComponent();
			int pNum = i;

			// OfflinePlayer p = Bukkit.getOfflinePlayer(UUID.fromString(uuid));

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
			
			
			player.setHoverEvent(new HoverEvent(Action.SHOW_TEXT, new Text(ChatColor.GRAY  + name + ChatColor.GRAY + " is in " + ChatColor.GREEN + server)));

			isStaff(UUID.fromString(uuid)).thenAcceptAsync(res -> {
				if (res) {
					if(pNum != 0 && pNum != total -1) nonStaff.addExtra(Colors.format(", "));
					staff.addExtra(player);
					// s++;
				} else {
					if(pNum != 0 && pNum != total -1) nonStaff.addExtra(Colors.format(", "));
					nonStaff.addExtra(player);
				}
			});	

		}
		new BukkitRunnable(){
		
			@Override
			public void run() {
				if (s == 0) staff.addExtra(Colors.format("&fTag &#7289DA@Staff &fon &#7289DADiscord &ffor help whilst no staff are online."));
				list.addExtra(staff);
				list.addExtra("\n");
				list.addExtra(nonStaff);
				list.addExtra(Colors.format("\n\n&7To sort players by gamemode, type &e/glist"));
				sender.sendMessage(list);
						
			}
		}.runTaskLater(Main.plugin, 20l);

        return true;
	}
}
