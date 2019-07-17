package me.sisko.left4chat.sql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Level;
import me.sisko.left4chat.util.Main;

public class SQLManager {
    public static synchronized Connection connect() {
        String host = Main.getPlugin().getConfig().getString("sql.host");
        String database = Main.getPlugin().getConfig().getString("sql.database");
        int port = Main.getPlugin().getConfig().getInt("sql.port");
        String user = Main.getPlugin().getConfig().getString("sql.user");
        String pass = Main.getPlugin().getConfig().getString("sql.pass");
        try {
            Class.forName("com.mysql.jdbc.Driver");
            return DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + database + "?autoReconnect=true&verifyServerCertificate=false&useSSL=true", user, pass);
        }
        catch (ClassNotFoundException | SQLException e) {
            Main.getPlugin().getLogger().log(Level.SEVERE, "Could not connect to SQL database!");
            e.printStackTrace();
            return null;
        }
    }
}

