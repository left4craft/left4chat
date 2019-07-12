/*
 * Decompiled with CFR 0.145.
 * 
 * Could not load the following classes:
 *  org.bukkit.ChatColor
 *  org.bukkit.scheduler.BukkitRunnable
 */
package me.sisko.left4chat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.bukkit.ChatColor;
import org.bukkit.scheduler.BukkitRunnable;
import redis.clients.jedis.Jedis;

public class AsyncUserSave
extends BukkitRunnable {
    private Connection connection;
    private String uuid;
    private String nick;
    private String discordID;

    public AsyncUserSave setup(Connection connection, String uuid, String nick, String discordID) {
        this.connection = connection;
        this.uuid = uuid;
        this.nick = nick;
        this.discordID = discordID;
        return this;
    }

    public AsyncUserSave setup(Connection connection, String uuid, String nick) {
        this.connection = connection;
        this.uuid = uuid;
        this.nick = ChatColor.stripColor((String)ChatColor.translateAlternateColorCodes((char)'&', (String)nick));
        this.discordID = null;
        return this;
    }

    public void run() {
        this.uuid = this.uuid.replace("-", "");
        try {
            Statement statement = this.connection.createStatement();
            ResultSet result = statement.executeQuery("SELECT * FROM discord_users WHERE UUID = UNHEX('" + this.uuid + "');");
            if (result.next()) {
                if (this.discordID != null) {
                    String id = Long.toString(result.getLong("discordID"));
                    Jedis r = new Jedis();
                    r.publish("discord.botcommands", "unlink " + id + " " + this.discordID);
                    r.close();
                    statement.executeUpdate("UPDATE discord_users SET nick = \"" + this.nick + "\", discordID = " + this.discordID + " WHERE UUID = UNHEX('" + this.uuid + "');");
                    statement.executeUpdate("DELETE FROM discord_users WHERE discordID = " + id);
                } else {
                    statement.executeUpdate("UPDATE discord_users SET nick = \"" + this.nick + "\" WHERE UUID = UNHEX('" + this.uuid + "');");
                }
            } else {
                statement.executeUpdate("INSERT INTO discord_users (uuid, nick, discordID) VALUES (UNHEX('" + this.uuid + "'), \"" + this.nick + "\"," + this.discordID + ");");
            }
        }
        catch (SQLException e) {
            e.printStackTrace();
        }
    }
}

