package me.sisko.left4chat.command;

import com.google.gson.JsonObject;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.sisko.left4chat.Left4Chat;
import me.sisko.left4chat.Left4ChatConfig;
import me.sisko.left4chat.text.AmpersandColors;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /chatlock} -- requires everyone unverified to solve a captcha before
 * chatting, for riding out a spambot wave.
 *
 * <p>The old command announced the change by dispatching {@code /announce ...}
 * through {@code Bukkit.dispatchCommand} as the console. This publishes to the
 * chat channel directly, which is what {@code /announce} did anyway.
 */
public final class LockdownCommand extends PluginCommand {

    public LockdownCommand(Left4Chat plugin) {
        super(plugin);
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        Left4ChatConfig config = plugin.config();

        String name = "Console";
        if (sender instanceof Player player) {
            if (!player.hasPermission(config.chat().lockPermission())) {
                error(player, "You don't have permission to lock chat!");
                return;
            }
            name = player.getName();
        }

        String who = name;
        async(() -> toggle(who));
    }

    private void toggle(String who) {
        Left4ChatConfig config = plugin.config();

        boolean locked = "true".equals(
                plugin.redis().getOrSet(config.keys().lockdownKey(), "false"));
        boolean nowLocked = !locked;

        plugin.redis().set(config.keys().lockdownKey(), String.valueOf(nowLocked));

        if (nowLocked) {
            announce(config.messages().chatLocked().replace("{player}", who));
            announce(config.messages().chatLockedHint());
        } else {
            announce(config.messages().chatUnlocked().replace("{player}", who));
        }
    }

    private void announce(String message) {
        JsonObject payload = new JsonObject();
        payload.addProperty("type", "raw");
        payload.addProperty("content", message);
        payload.addProperty("content_stripped", AmpersandColors.strip(message));
        plugin.redis().publish(plugin.config().keys().chatChannel(), payload.toString());
    }
}
