package me.sisko.left4chat.sql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import me.sisko.left4chat.util.Main;

public class AsyncFixCoins extends BukkitRunnable {
	private Connection conn;
	private Player p;
	private final String[] tiers = { "NormalTreasures", "MythicalTreasures", "LegendaryTreasures" };

	public AsyncFixCoins(Connection conn, Player p) {
		this.conn = conn;
		this.p = p;
	}

	@Override
	public void run() {
		try {
			ResultSet rs = conn.createStatement().executeQuery("SELECT Coins FROM ProCosmeticsData WHERE UUID=\"" + p.getUniqueId() + "\";");
			if (rs.next()) {
				int coins = rs.getInt("Coins");
				execute("pc setcoins " + p.getName() + " " + coins);
				rs = conn.createStatement().executeQuery("SELECT " + String.join(",", tiers) + " FROM ProCosmeticsData WHERE UUID=\"" + p.getUniqueId() + "\";");
				if (rs.next()) {
					for (String tier : tiers) {
						int keys = rs.getInt(tier);
						execute("pc treasure set " + p.getName() + " " + tier.substring(0, tier.length()-9) + " " + keys);
					}
				}
			} else {
				Main.getPlugin().getLogger().warning("Could not find player " + p.getUniqueId() + " in the procosmetics database!");
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	private void execute(String cmd) {
		new BukkitRunnable() {
			@Override
			public void run() {
				Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), cmd);
			}
		}.runTask(Main.getPlugin());
	}
}
