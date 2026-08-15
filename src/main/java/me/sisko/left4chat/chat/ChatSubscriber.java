package me.sisko.left4chat.chat;

import java.sql.Timestamp;
import java.util.UUID;
import java.util.logging.Level;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import me.sisko.left4chat.Left4Chat;
import me.sisko.left4chat.Left4ChatConfig;
import me.sisko.left4chat.text.AmpersandColors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * Renders whatever arrives on the {@code minecraft.chat} channel.
 *
 * <p>Messages come from three places: other Minecraft servers, the Discord bot,
 * and the proxy. Every type the old plugin handled is handled here, with the
 * same JSON on the wire, so nothing else on the network has to change.
 *
 * <p>The rendering itself moved from {@code net.md_5.bungee} components and
 * {@code player.spigot().sendMessage(...)} to Adventure and
 * {@code player.sendMessage(Component)}.
 */
public final class ChatSubscriber {

    private final Left4Chat plugin;

    public ChatSubscriber(Left4Chat plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles one message. Runs on the Redis subscriber thread.
     *
     * @param raw the JSON payload
     */
    public void accept(String raw) {
        JsonObject json;
        try {
            json = JsonParser.parseString(raw).getAsJsonObject();
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid JSON on the chat channel: " + raw);
            return;
        }

        if (!json.has("type")) {
            return;
        }

        try {
            switch (json.get("type").getAsString()) {
                case "chat" -> chat(json);
                case "discord_chat" -> discordChat(json);
                case "pm" -> privateMessage(json);
                case "raw" -> broadcast(AmpersandColors.format(json.get("content").getAsString()));
                case "afk" -> afk(json);
                case "welcome" -> welcome(json);
                default -> {
                    // join/leave are the proxy's business, not ours.
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Could not render a chat message: " + raw, e);
        }
    }

    private void chat(JsonObject json) {
        String name = json.get("name").getAsString();
        String nick = json.get("nick").getAsString();
        String prefix = string(json, "prefix", "");

        Component hover = Component.text()
                .append(Component.text("Realname:\n", NamedTextColor.BLUE))
                .append(Component.text(name + "\n", NamedTextColor.GRAY))
                .append(Component.text("Timestamp:\n", NamedTextColor.BLUE))
                .append(Component.text(timestamp(json) + "\n", NamedTextColor.GRAY))
                .append(Component.text("Click to message", NamedTextColor.DARK_AQUA))
                .build();

        Component username = AmpersandColors
                .format(plugin.config().messages().chatFormat()
                        .replace("{prefix}", prefix)
                        .replace("{nick}", nick))
                .hoverEvent(HoverEvent.showText(hover))
                .clickEvent(ClickEvent.suggestCommand("/msg " + name + " "));

        broadcast(Component.textOfChildren(username, content(json)));
    }

    private void discordChat(JsonObject json) {
        Component hover = Component.text()
                .append(Component.text("Discord username:\n", NamedTextColor.BLUE))
                .append(Component.text(json.get("discord_username").getAsString() + "\n",
                        NamedTextColor.GRAY))
                .append(Component.text("Timestamp:\n", NamedTextColor.BLUE))
                .append(Component.text(timestamp(json) + "\n", NamedTextColor.GRAY))
                .append(Component.text("Click to tag on Discord", NamedTextColor.DARK_AQUA))
                .build();

        Component username = AmpersandColors.format(json.get("discord_prefix").getAsString())
                .hoverEvent(HoverEvent.showText(hover))
                .clickEvent(ClickEvent.suggestCommand("<@" + json.get("discord_id").getAsString() + "> "));

        broadcast(Component.textOfChildren(username, content(json)));
    }

    private void privateMessage(JsonObject json) {
        String fromName = json.get("from_name").getAsString();
        String fromNick = json.get("from_nick").getAsString();

        plugin.getLogger().info("[MSG] [" + fromName + " -> " + json.get("to_name").getAsString()
                + "] " + json.get("content").getAsString());

        Player recipient = plugin.getServer().getPlayer(UUID.fromString(json.get("to").getAsString()));
        if (recipient == null) {
            return;
        }

        Left4ChatConfig.Messages messages = plugin.config().messages();
        Component header = AmpersandColors
                .format(messages.privateMessageIn().replace("{nick}", fromNick))
                .hoverEvent(HoverEvent.showText(Component.text()
                        .append(Component.text("Click to reply to ", NamedTextColor.GREEN))
                        .append(AmpersandColors.format(fromNick))
                        .build()))
                .clickEvent(ClickEvent.suggestCommand("/msg " + fromName + " "));

        recipient.sendMessage(Component.textOfChildren(header, content(json)));
    }

    private void afk(JsonObject json) {
        boolean away = json.get("afk").getAsBoolean();
        broadcast(AmpersandColors.format("&7 * " + json.get("name").getAsString()
                + (away ? " is now" : " is no longer") + " afk"));
    }

    private void welcome(JsonObject json) {
        broadcast(AmpersandColors.format(plugin.config().messages().firstJoin()
                .replace("{player}", json.get("name").getAsString())));
    }

    /**
     * Renders the message body with the sender's colour and formatting
     * permissions applied.
     */
    private Component content(JsonObject json) {
        boolean color = json.has("color") && json.get("color").getAsBoolean();
        boolean format = json.has("format") && json.get("format").getAsBoolean();
        return AmpersandColors.formatWithPerm(format, color, json.get("content").getAsString());
    }

    private String timestamp(JsonObject json) {
        return json.has("timestamp")
                ? new Timestamp(json.get("timestamp").getAsLong()).toString()
                : "";
    }

    private static String string(JsonObject json, String key, String fallback) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : fallback;
    }

    /**
     * Sends a line to everyone on this server.
     *
     * <p>The old code did {@code Bukkit.getOnlinePlayers().forEach(...)} from
     * the subscriber thread, iterating a live collection off the main thread.
     * {@link org.bukkit.Server#broadcast} is safe to call from anywhere.
     */
    private void broadcast(Component message) {
        plugin.getServer().broadcast(message);
    }
}
