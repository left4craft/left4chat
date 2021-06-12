package me.sisko.left4chat.sql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import me.sisko.left4chat.util.Main;
import me.sisko.left4chat.util.Colors;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.json.JSONObject;

import redis.clients.jedis.Jedis;

public class AsyncUserUpdate
extends BukkitRunnable {
    private Connection connection;
    private String world;
    private Player p;
    private OfflinePlayer op;
    private String nick;

    public AsyncUserUpdate setup(Connection connection, Player p) {
        this.connection = connection;
        this.p = p;
        this.op = Bukkit.getOfflinePlayer((UUID)p.getUniqueId());
        this.world = p.getWorld().getName();
        this.nick = Colors.strip(p.getDisplayName());
        return this;
    }

    @Override
    public void run() {
        String uuid = this.op.getUniqueId().toString().replace("-", "");
        try {
            Statement statement = this.connection.createStatement();
            ResultSet result = statement.executeQuery("SELECT * FROM discord_users WHERE UUID = UNHEX('" + uuid + "');");
            if (result.next()) {
                String id = Long.toString(result.getLong("discordID"));

                JSONObject nickCommand = new JSONObject();
                nickCommand.put("command", "setuser");
                nickCommand.put("id", id);
                nickCommand.put("nick", this.nick);

                JSONObject groupCommand = new JSONObject();
                groupCommand.put("command", "setgroup");
                groupCommand.put("id", id);
                String group = Main.getGroup(this.world, this.op).toLowerCase();
                groupCommand.put("group", group.equals("guest") ? "user" : group);

                Jedis j = new Jedis(Main.plugin.getConfig().getString("redisip"), Main.plugin.getConfig().getInt("redisport"));
                j.auth(Main.plugin.getConfig().getString("redispass"));
                        
                j.publish("discord.botcommands", nickCommand.toString());
                j.publish("discord.botcommands", groupCommand.toString());
                Main.plugin.getLogger().info("Connected Minecraft Account " + this.op.getName() + " to discord account " + id);

                if(Main.promoteToUser(p)) {
                    Main.plugin.getLogger().info("Promoted " + this.op.getName() + " to user.");
                }

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

