package me.sisko.left4chat.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonObject;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.sisko.left4chat.Left4Chat;
import me.sisko.left4chat.Left4ChatConfig;
import me.sisko.left4chat.presence.PlayerDirectory.NetworkPlayer;
import me.sisko.left4chat.text.AmpersandColors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

/**
 * {@code /msg <player> <message>} -- a private message to anyone on the network.
 *
 * <p>Recipients are resolved against the shared roster by username first, then
 * by nickname, exactly as before. What changed is where the work happens: the
 * old command opened two Redis connections on the main thread, read and parsed
 * the roster twice, and ran a blocking nickname query per online player. All of
 * that now happens on the async scheduler against cached data.
 */
public final class MessageCommand extends PluginCommand {

    public MessageCommand(Left4Chat plugin) {
        super(plugin);
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        if (!(source.getSender() instanceof Player player)) {
            error(source.getSender(), "You can't message from console!");
            return;
        }

        Left4ChatConfig config = plugin.config();

        if (!player.hasPermission(config.chat().verifiedPermission())) {
            player.sendMessage(AmpersandColors.format(config.messages().verifyRequired()));
            deny(player);
            return;
        }
        if (args.length < 2) {
            error(player, "Usage: /msg <player> <message>");
            deny(player);
            return;
        }

        String recipient = args[0];
        String message = join(args, 1);

        async(() -> deliver(player, recipient, message));
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (args.length > 1) {
            return List.of();
        }
        String prefix = (args.length == 0 ? "" : args[0]).toLowerCase(Locale.ROOT);

        List<String> names = new ArrayList<>();
        for (NetworkPlayer online : plugin.playerDirectory().online()) {
            if (online.username().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                names.add(online.username());
            }
            String nick = plugin.nicknames().plain(online.uuid(), online.username());
            if (!nick.equalsIgnoreCase(online.username())
                    && nick.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                names.add(nick);
            }
        }
        return names;
    }

    private void deliver(Player sender, String recipientName, String message) {
        // Exact username wins outright.
        Optional<NetworkPlayer> exact = plugin.playerDirectory().byUsername(recipientName);
        if (exact.isPresent()) {
            send(sender, exact.get(), message);
            return;
        }

        // Then exact nickname, then prefix matches on either.
        Map<String, NetworkPlayer> candidates = new LinkedHashMap<>();
        Map<String, String> nicknames = new LinkedHashMap<>();

        for (NetworkPlayer online : plugin.playerDirectory().online()) {
            String plainNick = plugin.nicknames().plain(online.uuid(), online.username());

            if (plainNick.equalsIgnoreCase(recipientName)) {
                send(sender, online, message);
                return;
            }
            if (online.username().toLowerCase(Locale.ROOT)
                    .startsWith(recipientName.toLowerCase(Locale.ROOT))
                    || plainNick.toLowerCase(Locale.ROOT)
                            .startsWith(recipientName.toLowerCase(Locale.ROOT))) {
                candidates.put(online.username(), online);
                plugin.nicknames().raw(online.uuid()).ifPresent(raw ->
                        nicknames.put(online.username(), raw));
            }
        }

        if (candidates.isEmpty()) {
            sender.sendMessage(Component.text(
                    "No usernames or nicknames start with " + recipientName + ".", NamedTextColor.RED));
            deny(sender);
            return;
        }
        if (candidates.size() == 1) {
            send(sender, candidates.values().iterator().next(), message);
            return;
        }

        var ambiguous = Component.text()
                .append(Component.text("Ambiguous recipient \"" + recipientName + "\", do you mean:",
                        NamedTextColor.RED));
        for (Map.Entry<String, NetworkPlayer> candidate : candidates.entrySet()) {
            ambiguous.append(Component.newline())
                    .append(Component.text("- " + candidate.getKey(), NamedTextColor.GOLD));
            String nick = nicknames.get(candidate.getKey());
            if (nick != null) {
                ambiguous.append(Component.text(" (Nickname: ", NamedTextColor.GOLD))
                        .append(AmpersandColors.format(nick))
                        .append(Component.text(")", NamedTextColor.GOLD));
            }
        }
        sender.sendMessage(ambiguous.build());
        deny(sender);
    }

    private void send(Player sender, NetworkPlayer recipient, String message) {
        Left4ChatConfig config = plugin.config();

        String recipientNick = plugin.nicknames()
                .displayName(recipient.uuid(), recipient.username());
        String senderNick = plugin.nicknames()
                .displayName(sender.getUniqueId(), sender.getName());

        sender.sendMessage(Component.textOfChildren(
                AmpersandColors.format(config.messages().privateMessageOut()
                        .replace("{nick}", recipientNick)),
                AmpersandColors.formatWithPerm(
                        sender.hasPermission(config.chat().formatPermission()),
                        sender.hasPermission(config.chat().colorPermission()),
                        message)));

        if (plugin.afk().isAway(recipient.uuid())) {
            sender.sendMessage(Component.textOfChildren(
                    AmpersandColors.format(recipientNick),
                    Component.text(" is currently AFK and may not respond.", NamedTextColor.RED)));
            deny(sender);
        } else {
            play(sender, 2.0f);
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("type", "pm");
        payload.addProperty("from", sender.getUniqueId().toString());
        payload.addProperty("to", recipient.uuid().toString());
        payload.addProperty("from_name", sender.getName());
        payload.addProperty("from_nick", senderNick);
        payload.addProperty("to_name", recipient.username());
        payload.addProperty("to_nick", recipientNick);
        payload.addProperty("content", message);
        payload.addProperty("content_stripped", AmpersandColors.strip(message));
        payload.addProperty("color", sender.hasPermission(config.chat().colorPermission()));
        payload.addProperty("format", sender.hasPermission(config.chat().formatPermission()));

        plugin.redis().publish(config.keys().chatChannel(), payload.toString());
        plugin.playerDirectory().setLastRecipient(sender.getName(), recipient.username());
    }

    private void deny(Player player) {
        play(player, 0.5f);
    }

    private void play(Player player, float pitch) {
        sync(() -> player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT,
                SoundCategory.PLAYERS, 5.0f, pitch));
    }
}
