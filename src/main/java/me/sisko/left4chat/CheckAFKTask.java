package me.sisko.left4chat;


import me.sisko.left4chat.Main;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class CheckAFKTask extends BukkitRunnable {
    public void run() {
        for (Player p : Main.plugin.getMoveTimes().keySet()) {
            if (System.currentTimeMillis() - Main.plugin.getMoveTimes().get(p) > 300000L) {
                Main.plugin.setAFK(p, true, true);
            } 
        }
    }
}

