/*
 * Decompiled with CFR 0.145.
 * 
 * Could not load the following classes:
 *  net.milkbowl.vault.permission.Permission
 *  org.bukkit.Bukkit
 *  org.bukkit.ChatColor
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 */
package me.sisko.left4chat;

import me.sisko.left4chat.Main;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import redis.clients.jedis.Jedis;

public class LockdownCommand
implements CommandExecutor {
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Jedis j = new Jedis();
        String name = "Console";
        if (sender instanceof Player) {
            name = ((Player)sender).getName();
            if (!Main.plugin.getPerms().has(sender, "left4chat.chatlock")) {
                sender.sendMessage((Object)ChatColor.RED + "You don't have permission to lock chat!");
                j.close();
                return true;
            }
        }
        if (j.get("minecraft.lockdown").equals("false")) {
            j.set("minecraft.lockdown", "true");
            Bukkit.dispatchCommand((CommandSender)Bukkit.getConsoleSender(), (String)("announce &c&l" + name + "&c has locked the server chat!"));
            Bukkit.dispatchCommand((CommandSender)Bukkit.getConsoleSender(), (String)"announce &bAll unverified guests must use &a/verify to chat");
        } else {
            j.set("minecraft.lockdown", "false");
            Bukkit.dispatchCommand((CommandSender)Bukkit.getConsoleSender(), (String)("announce &a" + name + " has unlocked the server chat!"));
        }
        j.close();
        return true;
    }
}

