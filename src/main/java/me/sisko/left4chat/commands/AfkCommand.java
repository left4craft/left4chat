package me.sisko.left4chat.commands;

import me.sisko.left4chat.util.Main;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AfkCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            Main.plugin.getLogger().info("You can't do that from console!");
        } else {
            Player p = (Player) sender;
            Main.plugin.toggleAfk(p);
        }
        return true;
    }
}
