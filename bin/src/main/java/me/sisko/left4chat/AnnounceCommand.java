/*
 * Decompiled with CFR 0.145.
 * 
 * Could not load the following classes:
 *  org.bukkit.ChatColor
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 */
package me.sisko.left4chat;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import redis.clients.jedis.Jedis;

public class AnnounceCommand
implements CommandExecutor {
    public boolean onCommand(CommandSender sender, Command cmd, String str, String[] args) {
        if (!(sender instanceof Player)) {
            String msg = "";
            for (String arg : args) {
                msg = String.valueOf(msg) + arg + " ";
            }
            Jedis j = new Jedis();
            j.publish("minecraft.chat.global.in", msg);
            j.close();
        } else {
            ((Player)sender).sendMessage((Object)ChatColor.RED + "Insufficient Permission.");
        }
        return true;
    }
}

