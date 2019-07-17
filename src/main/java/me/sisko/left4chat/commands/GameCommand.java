package me.sisko.left4chat.commands;

import me.sisko.left4chat.util.InventoryGUI;
import me.sisko.left4chat.util.Main;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GameCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String str, String[] args) {
        if (sender instanceof Player) {
            InventoryGUI.open((Player)sender);
        } else {
            Main.plugin.getLogger().info("You can't do that from console!");
        }
        return true;
    }
}

