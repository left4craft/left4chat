/*
 * Decompiled with CFR 0.145.
 * 
 * Could not load the following classes:
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 */
package me.sisko.left4chat;

import java.util.logging.Logger;
import me.sisko.left4chat.InventoryGUI;
import me.sisko.left4chat.Main;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GameCommand
implements CommandExecutor {
    public boolean onCommand(CommandSender sender, Command cmd, String str, String[] args) {
        if (sender instanceof Player) {
            InventoryGUI.open((Player)sender);
        } else {
            Main.plugin.getLogger().info("You can't do that from console!");
        }
        return true;
    }
}

