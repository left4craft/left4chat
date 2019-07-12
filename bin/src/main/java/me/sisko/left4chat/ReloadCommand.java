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

import java.util.logging.Logger;
import me.sisko.left4chat.ConfigManager;
import me.sisko.left4chat.Main;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ReloadCommand
implements CommandExecutor {
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (sender instanceof Player) {
            sender.sendMessage((Object)ChatColor.RED + "You don't have permission to do that!");
        } else {
            ConfigManager.reload();
            Main.getPlugin().getLogger().info("Config and database has been reloaded");
        }
        return true;
    }
}

