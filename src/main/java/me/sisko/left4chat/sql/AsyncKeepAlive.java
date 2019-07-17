package me.sisko.left4chat.sql;

import java.sql.Connection;
import java.sql.SQLException;
import org.bukkit.scheduler.BukkitRunnable;

public class AsyncKeepAlive
extends BukkitRunnable {
    private Connection conn;

    public AsyncKeepAlive(Connection conn) {
        this.conn = conn;
    }

    public void run() {
        try {
            this.conn.createStatement().executeQuery("SELECT 1;");
        }
        catch (SQLException e) {
            e.printStackTrace();
        }
    }
}

