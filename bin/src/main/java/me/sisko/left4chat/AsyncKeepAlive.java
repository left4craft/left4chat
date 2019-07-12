/*
 * Decompiled with CFR 0.145.
 * 
 * Could not load the following classes:
 *  org.bukkit.scheduler.BukkitRunnable
 */
package me.sisko.left4chat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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

