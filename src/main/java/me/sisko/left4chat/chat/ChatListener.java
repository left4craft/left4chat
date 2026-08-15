package me.sisko.left4chat.chat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonObject;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.sisko.left4chat.Left4Chat;
import me.sisko.left4chat.Left4ChatConfig;
import me.sisko.left4chat.text.AmpersandColors;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Turns local chat into a message on the {@code minecraft.chat} channel.
 *
 * <p>Nothing is ever shown by the vanilla chat path: the event is cancelled and
 * every server -- including this one -- renders the message when it comes back
 * around through Redis. That is how a message typed on survival reaches hub.
 *
 * <p>Listens to Paper's {@link AsyncChatEvent} rather than the deprecated
 * {@code AsyncPlayerChatEvent}, so the message arrives as a component instead of
 * a string that has already lost its structure.
 */
public final class ChatListener implements Listener {

    private final Left4Chat plugin;

    /** How many times each unverified player has been warned during lockdown. */
    private final Map<UUID, Integer> warnings = new ConcurrentHashMap<>();

    public ChatListener(Left4Chat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(AsyncChatEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Player player = event.getPlayer();
        Left4ChatConfig config = plugin.config();
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());

        if (isLockedOut(player)) {
            event.setCancelled(true);
            return;
        }

        // Always cancel: the message is echoed back over Redis instead.
        event.setCancelled(true);

        plugin.afk().markActive(player);

        String username = player.getName();
        String nick = plugin.nicknames().displayName(player.getUniqueId(), username);
        String group = plugin.permissions().primaryGroup(player);
        String prefix = plugin.permissions().prefix(player);

        JsonObject payload = new JsonObject();
        payload.addProperty("type", "chat");
        payload.addProperty("uuid", player.getUniqueId().toString());
        payload.addProperty("name", username);
        payload.addProperty("nick", nick);
        payload.addProperty("prefix", prefix);
        payload.addProperty("webhook_name", "[" + group + "] " + AmpersandColors.strip(nick));
        payload.addProperty("content_stripped", AmpersandColors.strip(message));
        payload.addProperty("content", message);
        payload.addProperty("color", player.hasPermission(config.chat().colorPermission()));
        payload.addProperty("format", player.hasPermission(config.chat().formatPermission()));
        payload.addProperty("timestamp", System.currentTimeMillis());

        plugin.redis().publish(config.keys().chatChannel(), payload.toString());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        warnings.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Applies the lockdown rules: unverified players get a few warnings, then a
     * ban. Someone mid-captcha who types instead of clicking is kicked outright.
     *
     * @return whether the message should be swallowed
     */
    private boolean isLockedOut(Player player) {
        Left4ChatConfig config = plugin.config();

        if (player.hasPermission(config.chat().verifiedPermission())) {
            return false;
        }
        if (!"true".equals(plugin.redis().get(config.keys().lockdownKey()))) {
            return false;
        }

        String consoleChannel = config.keys().consoleChannel(config.chat().lockdownServer());

        if (plugin.captcha().isSolving(player)) {
            plugin.redis().publish(consoleChannel,
                    "kick " + player.getName() + " Incorrect CAPTCHA solution");
            return true;
        }

        int max = config.chat().lockdownWarnings();
        int warning = warnings.merge(player.getUniqueId(), 1, Integer::sum);

        if (warning <= max) {
            player.sendMessage(AmpersandColors.format(config.messages().verifyWarning()
                    .replace("{warning}", String.valueOf(warning))
                    .replace("{max}", String.valueOf(max))));
            return true;
        }

        warnings.remove(player.getUniqueId());
        plugin.redis().publish(consoleChannel,
                "ban " + player.getName() + " Spambot (appealable)");
        return true;
    }
}
