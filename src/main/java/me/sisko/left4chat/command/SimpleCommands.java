package me.sisko.left4chat.command;

import java.util.Collection;
import java.util.List;

import com.google.gson.JsonObject;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.sisko.left4chat.Left4Chat;
import me.sisko.left4chat.text.AmpersandColors;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * The commands short enough not to deserve a file each.
 */
public final class SimpleCommands {

    private SimpleCommands() {
    }

    /** {@code /game} -- opens the server selector. */
    public static final class Game extends PluginCommand {
        public Game(Left4Chat plugin) {
            super(plugin);
        }

        @Override
        public void execute(CommandSourceStack source, String[] args) {
            if (!(source.getSender() instanceof Player player)) {
                error(source.getSender(), "Only a player can open the server selector.");
                return;
            }
            plugin.selector().open(player);
        }
    }

    /** {@code /afk} -- toggles away status. */
    public static final class Afk extends PluginCommand {
        public Afk(Left4Chat plugin) {
            super(plugin);
        }

        @Override
        public void execute(CommandSourceStack source, String[] args) {
            if (!(source.getSender() instanceof Player player)) {
                error(source.getSender(), "Only a player can go AFK.");
                return;
            }
            plugin.afk().toggle(player);
        }
    }

    /** {@code /verify} -- opens the captcha. */
    public static final class Verify extends PluginCommand {
        public Verify(Left4Chat plugin) {
            super(plugin);
        }

        @Override
        public void execute(CommandSourceStack source, String[] args) {
            if (!(source.getSender() instanceof Player player)) {
                error(source.getSender(), "Only a player can verify.");
                return;
            }
            if (player.hasPermission(plugin.config().chat().verifiedPermission())) {
                player.sendMessage(AmpersandColors.format(
                        "&aYou are already verified! You may chat freely."));
                return;
            }
            plugin.captcha().open(player);
        }
    }

    /**
     * {@code /announce <message>} -- console only, publishes a raw line to the
     * whole network.
     */
    public static final class Announce extends PluginCommand {
        public Announce(Left4Chat plugin) {
            super(plugin);
        }

        @Override
        public void execute(CommandSourceStack source, String[] args) {
            if (source.getSender() instanceof Player) {
                error(source.getSender(), "Insufficient Permission.");
                return;
            }
            if (args.length == 0) {
                error(source.getSender(), "Usage: /announce <message>");
                return;
            }

            String message = join(args, 0);
            JsonObject payload = new JsonObject();
            payload.addProperty("type", "raw");
            payload.addProperty("content", message);
            payload.addProperty("content_stripped", AmpersandColors.strip(message));

            async(() -> plugin.redis()
                    .publish(plugin.config().keys().chatChannel(), payload.toString()));
        }
    }

    /**
     * {@code /chatreload} -- console only, re-reads config.yml and reopens the
     * Redis and Postgres pools.
     */
    public static final class Reload extends PluginCommand {
        public Reload(Left4Chat plugin) {
            super(plugin);
        }

        @Override
        public void execute(CommandSourceStack source, String[] args) {
            CommandSender sender = source.getSender();
            if (sender instanceof Player) {
                error(sender, "You don't have permission to do that!");
                return;
            }

            try {
                plugin.reloadLeft4Chat();
                sender.sendMessage(AmpersandColors.format("&aLeft4Chat config and connections reloaded."));
            } catch (Exception e) {
                error(sender, "Reload failed: " + e.getMessage() + ". Check the console.");
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Reload failed", e);
            }
        }
    }

    /**
     * {@code /ggivecosmetic <name> <amount> [tier]} -- console only, relays a
     * cosmetics grant to the hub, which owns the cosmetics database.
     */
    public static final class GlobalGiveCosmetic extends PluginCommand {
        private static final String[] RARITIES = {"normal", "mythical", "legendary"};

        public GlobalGiveCosmetic(Left4Chat plugin) {
            super(plugin);
        }

        @Override
        public void execute(CommandSourceStack source, String[] args) {
            CommandSender sender = source.getSender();
            if (sender instanceof Player) {
                error(sender, "Insufficient Permission");
                return;
            }
            if (args.length < 2) {
                error(sender, "Usage: /ggivecosmetic <name> <amount> [tier]");
                return;
            }

            int amount;
            try {
                amount = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                error(sender, "Amount must be a number.");
                return;
            }

            Integer tier = null;
            if (args.length > 2) {
                try {
                    tier = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    error(sender, "Tier must be a number.");
                    return;
                }
                if (tier < 0 || tier >= RARITIES.length) {
                    error(sender, "Tier must be between 0 and " + (RARITIES.length - 1) + ".");
                    return;
                }
            }

            // Tell the recipient, if they happen to be on this server.
            boolean plural = amount > 1;
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (!player.getName().equalsIgnoreCase(args[0])) {
                    continue;
                }
                String what = tier == null
                        ? amount + " cosmetic " + (plural ? "coins have" : "coin has")
                        : amount + " " + RARITIES[tier] + " " + (plural ? "keys have" : "key has");
                player.sendMessage(AmpersandColors.format("&a" + what + " been added to your account."));
            }

            String command = "givecosmetic " + args[0] + " " + amount
                    + (tier == null ? "" : " " + tier);
            String channel = plugin.config().keys()
                    .consoleChannel(plugin.config().chat().lockdownServer());

            async(() -> plugin.redis().publish(channel, command));
        }

        @Override
        public Collection<String> suggest(CommandSourceStack source, String[] args) {
            if (args.length <= 1) {
                return plugin.playerDirectory().startingWith(args.length == 0 ? "" : args[0])
                        .stream()
                        .map(me.sisko.left4chat.presence.PlayerDirectory.NetworkPlayer::username)
                        .toList();
            }
            if (args.length == 3) {
                return List.of("0", "1", "2");
            }
            return List.of();
        }
    }
}
