package me.sisko.left4chat.sql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.bukkit.scheduler.BukkitRunnable;
import org.json.JSONObject;

import me.sisko.left4chat.util.Main;
import me.sisko.left4chat.util.Colors;
import redis.clients.jedis.Jedis;

public class AsyncUserSave extends BukkitRunnable {
    private Connection connection;
    private String uuid;
    private String nick;
    private String newId;

    public AsyncUserSave setup(Connection connection, String uuid, String nick, String discordID) {
        this.connection = connection;
        this.uuid = uuid;
        this.nick = nick;
        this.newId = discordID;
        return this;
    }

    public AsyncUserSave setup(Connection connection, String uuid, String nick) {
        this.connection = connection;
        this.uuid = uuid;
        this.nick = Colors.strip(nick);
        this.newId = null;
        return this;
    }

    public void run() {
        this.uuid = this.uuid.replace("-", "");
        try {
            Statement statement = this.connection.createStatement();
            ResultSet result = statement
                    .executeQuery("SELECT * FROM discord_users WHERE UUID = UNHEX('" + this.uuid + "');");

            // if the user already has a linked account
            if (result.next()) {

                // and the user has a new discord id
                if (this.newId != null) {
                    String oldId = Long.toString(result.getLong("discordID"));

                    // Inform the discord bot of the id change so they can demote the user
                    // and inform them as applicable
                    JSONObject command = new JSONObject();
                    command.put("command", "unlink");
                    command.put("oldId", oldId);
                    command.put("newId", newId);

                    Jedis r = new Jedis(Main.plugin.getConfig().getString("redisip"), Main.plugin.getConfig().getInt("redisport"));
                    r.auth(Main.plugin.getConfig().getString("redispass"));
                    r.publish("discord.botcommands", command.toString());
                    r.close();

                    // if the ids are different, update the database accordingly
                    if (!oldId.equals(newId)) {
                        statement.executeUpdate("UPDATE discord_users SET nick = \"" + this.nick + "\", discordID = "
                                + this.newId + " WHERE UUID = UNHEX('" + this.uuid + "');");
                        statement.executeUpdate("DELETE FROM discord_users WHERE discordID = " + oldId);
                    }
                } else {
                    // if there is not a new discord id, simply update the nickname
                    statement.executeUpdate("UPDATE discord_users SET nick = \"" + this.nick + "\" WHERE UUID = UNHEX('"
                            + this.uuid + "');");
                }
            } else {
                // if the user does not have a linked account, just insert them into the database
                statement.executeUpdate("INSERT INTO discord_users (uuid, nick, discordID) VALUES (UNHEX('" + this.uuid
                        + "'), \"" + this.nick + "\"," + this.newId + ");");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
