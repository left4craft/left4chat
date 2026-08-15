package me.sisko.left4chat.presence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import me.sisko.left4chat.Left4ChatConfig;
import me.sisko.left4chat.redis.RedisBridge;

/**
 * Who is online across the whole network, and who last messaged whom.
 *
 * <p>Both live in Redis: the proxy plugin writes {@code minecraft.players}, and
 * {@code minecraft.chat.replies} is shared by every backend so {@code /r} works
 * after switching servers.
 *
 * <p>The roster is cached and refreshed on a timer. The old code fetched and
 * parsed it inline on every {@code /msg}, every {@code /r}, every {@code /list}
 * and on every keystroke of {@code /msg} tab completion -- the last of those on
 * the main thread.
 */
public final class PlayerDirectory {

    /**
     * One entry from the network roster.
     *
     * @param uuid     the player
     * @param username their account name
     * @param server   the backend they are on, or null while switching
     */
    public record NetworkPlayer(UUID uuid, String username, String server) {
    }

    private final Logger logger;
    private final RedisBridge redis;
    private final Left4ChatConfig.Keys keys;

    private volatile List<NetworkPlayer> roster = List.of();

    public PlayerDirectory(Logger logger, RedisBridge redis, Left4ChatConfig.Keys keys) {
        this.logger = logger;
        this.redis = redis;
        this.keys = keys;
    }

    /**
     * Re-reads the roster from Redis. Blocks; call from an async thread.
     */
    public void refresh() {
        String raw = redis.get(keys.playerListKey());
        if (raw == null || raw.isBlank()) {
            roster = List.of();
            return;
        }

        try {
            JsonArray array = JsonParser.parseString(raw).getAsJsonArray();
            List<NetworkPlayer> parsed = new ArrayList<>(array.size());

            for (JsonElement element : array) {
                JsonObject entry = element.getAsJsonObject();
                UUID uuid = UUID.fromString(entry.get("uuid").getAsString());
                String username = entry.get("username").getAsString();
                JsonElement server = entry.get("server");
                parsed.add(new NetworkPlayer(uuid, username,
                        server == null || server.isJsonNull() ? null : server.getAsString()));
            }

            roster = List.copyOf(parsed);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not parse " + keys.playerListKey() + ": " + raw, e);
        }
    }

    /**
     * The last known network roster. Never blocks.
     *
     * @return everyone online, network-wide
     */
    public List<NetworkPlayer> online() {
        return roster;
    }

    /**
     * Finds a player by exact username, case-insensitively.
     *
     * @param username the name
     * @return the entry, if they are online
     */
    public Optional<NetworkPlayer> byUsername(String username) {
        return roster.stream()
                .filter(player -> player.username().equalsIgnoreCase(username))
                .findFirst();
    }

    /**
     * Finds a player by UUID.
     *
     * @param uuid the player
     * @return the entry, if they are online
     */
    public Optional<NetworkPlayer> byUuid(UUID uuid) {
        return roster.stream()
                .filter(player -> player.uuid().equals(uuid))
                .findFirst();
    }

    /**
     * Every player whose username starts with a prefix.
     *
     * @param prefix the prefix
     * @return the matches
     */
    public List<NetworkPlayer> startingWith(String prefix) {
        String needle = prefix.toLowerCase(Locale.ROOT);
        return roster.stream()
                .filter(player -> player.username().toLowerCase(Locale.ROOT).startsWith(needle))
                .toList();
    }

    /**
     * Who a player last messaged. Blocks; call from an async thread.
     *
     * @param sender the sender's username
     * @return the recipient's username, if there is one
     */
    public Optional<String> lastRecipient(String sender) {
        String raw = redis.getOrSet(keys.repliesKey(), "{}");
        try {
            JsonObject replies = JsonParser.parseString(raw).getAsJsonObject();
            JsonElement recipient = replies.get(sender);
            return recipient == null || recipient.isJsonNull()
                    ? Optional.empty()
                    : Optional.of(recipient.getAsString());
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not parse " + keys.repliesKey() + ": " + raw, e);
            return Optional.empty();
        }
    }

    /**
     * Records who a player last messaged. Blocks; call from an async thread.
     *
     * @param sender    the sender's username
     * @param recipient the recipient's username
     */
    public void setLastRecipient(String sender, String recipient) {
        String raw = redis.getOrSet(keys.repliesKey(), "{}");
        try {
            JsonObject replies = JsonParser.parseString(raw).getAsJsonObject();
            replies.addProperty(sender, recipient);
            redis.set(keys.repliesKey(), replies.toString());
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not update " + keys.repliesKey(), e);
        }
    }
}
