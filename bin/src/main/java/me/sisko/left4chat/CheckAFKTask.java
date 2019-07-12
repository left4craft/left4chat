/*
 * Decompiled with CFR 0.145.
 * 
 * Could not load the following classes:
 *  org.bukkit.entity.Player
 *  org.bukkit.scheduler.BukkitRunnable
 */
package me.sisko.left4chat;

import java.util.HashMap;
import java.util.Set;
import me.sisko.left4chat.Main;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class CheckAFKTask
extends BukkitRunnable {
    public void run() {
        for (Player p : Main.plugin.getMoveTimes().keySet()) {
            if (System.currentTimeMillis() - Main.plugin.getMoveTimes().get((Object)p) <= 300000L) continue;
            Main.plugin.setAFK(p, true, true);
        }
    }
}

