/*
 * Decompiled with CFR 0.145.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.ChatColor
 *  org.bukkit.OfflinePlayer
 *  org.bukkit.World
 *  org.bukkit.entity.Player
 *  org.bukkit.scheduler.BukkitRunnable
 */
package me.sisko.left4chat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.logging.Logger;
import me.sisko.left4chat.Main;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import redis.clients.jedis.Jedis;

public class AsyncUserUpdate
extends BukkitRunnable {
    private Connection connection;
    private String world;
    private OfflinePlayer op;
    private String nick;

    public AsyncUserUpdate setup(Connection connection, Player p) {
        this.connection = connection;
        this.op = Bukkit.getOfflinePlayer((UUID)p.getUniqueId());
        this.world = p.getWorld().getName();
        this.nick = ChatColor.stripColor((String)ChatColor.translateAlternateColorCodes((char)'&', (String)p.getDisplayName()));
        return this;
    }

    public void run() {
        String uuid = this.op.getUniqueId().toString().replace("-", "");
        try {
            Statement statement = this.connection.createStatement();
            ResultSet result = statement.executeQuery("SELECT * FROM discord_users WHERE UUID = UNHEX('" + uuid + "');");
            if (result.next()) {
                long id = result.getLong("discordID");
                Jedis j = new Jedis();
                j.publish("discord.botcommands", "setuser " + id + " " + this.nick);
                j.publish("discord.botcommands", "setgroup " + id + " " + Main.getGroup(this.world, this.op));
                Main.plugin.getLogger().info("Connected Minecraft Account " + this.op.getName() + " to discord account " + id);
                j.close();
            } else {
                Main.plugin.getLogger().info("Could not find Discord account for " + this.op.getName());
            }
        }
        catch (SQLException e) {
            e.printStackTrace();
        }
    }
}

