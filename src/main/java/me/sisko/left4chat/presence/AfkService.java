package me.sisko.left4chat.presence;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import me.sisko.left4chat.Left4Chat;
import me.sisko.left4chat.Left4ChatConfig;
import me.sisko.left4chat.integration.Permissions;
import me.sisko.left4chat.redis.RedisBridge;
import org.bukkit.entity.Player;

/**
 * Who is away, network-wide.
 *
 * <p>AFK state lives in the shared {@code minecraft.afk} key so every server and
 * the Discord bot see the same thing. The interesting change is <em>when</em>
 * that key is touched.
 *
 * <p>The old plugin called {@code setAFK(player, false, true)} from
 * {@link org.bukkit.event.player.PlayerMoveEvent}, and {@code setAFK} opened a
 * fresh Redis connection, authenticated, read the key, parsed the JSON, and
 * closed the socket -- on the main thread, for every movement packet from every
 * player. Twenty players walking around meant hundreds of Redis round trips a
 * second, all of them no-ops.
 *
 * <p>Now activity only stamps an in-memory timestamp. Redis is written when a
 * player actually crosses between away and back, and the shared key is polled on
 * a timer to pick up changes made on other servers.
 */
public final class AfkService {

    private final Left4Chat plugin;
    private final Logger logger;
    private final RedisBridge redis;
    private final Left4ChatConfig.Keys keys;
    private final Left4ChatConfig.Afk settings;
    private final Permissions permissions;

    /** Everyone the network believes is away. Refreshed from Redis. */
    private volatile Set<UUID> away = Set.of();

    /** When each local player last did something. */
    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();

    /** Local players we have already announced as away, so we do not repeat. */
    private final Set<UUID> locallyAway = ConcurrentHashMap.newKeySet();

    public AfkService(Left4Chat plugin, RedisBridge redis, Left4ChatConfig.Keys keys,
                      Left4ChatConfig.Afk settings, Permissions permissions) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.redis = redis;
        this.keys = keys;
        this.settings = settings;
        this.permissions = permissions;
    }

    /**
     * Whether a player is away. Never blocks -- answers from the last poll.
     *
     * @param uuid the player
     * @return whether they are marked away
     */
    public boolean isAway(UUID uuid) {
        return away.contains(uuid);
    }

    /**
     * Records that a local player did something, and brings them back if they
     * were away. Safe to call on the main thread as often as you like.
     *
     * @param player the player
     */
    public void markActive(Player player) {
        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());

        if (locallyAway.contains(player.getUniqueId())) {
            setAway(player, false);
        }
    }

    /**
     * Flips a player between away and back.
     *
     * @param player the player
     */
    public void toggle(Player player) {
        setAway(player, !isAway(player.getUniqueId()));
    }

    /**
     * Marks a player away or back, updating Redis on the async scheduler.
     *
     * @param player the player
     * @param away   whether they should be away
     */
    public void setAway(Player player, boolean away) {
        UUID uuid = player.getUniqueId();
        String username = player.getName();

        if (away) {
            if (!locallyAway.add(uuid)) {
                return;
            }
        } else if (!locallyAway.remove(uuid)) {
            // Only skip when Redis also agrees they are back; otherwise a state
            // left behind by a crash would never clear.
            if (!this.away.contains(uuid)) {
                return;
            }
        }

        // Reflect it locally straight away so /list and /msg do not lag a poll behind.
        Set<UUID> updated = new HashSet<>(this.away);
        if (away) {
            updated.add(uuid);
        } else {
            updated.remove(uuid);
        }
        this.away = Set.copyOf(updated);

        if (away) {
            permissions.grant(player, settings.exemptPermission());
        } else {
            permissions.revoke(player, settings.exemptPermission());
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                () -> writeAway(uuid, username, away));
    }

    /**
     * Forgets a player who has left. Their Redis entry is cleared too, because
     * an away player who disconnects is not away, they are gone.
     *
     * @param player the player
     */
    public void forget(Player player) {
        lastActivity.remove(player.getUniqueId());
        if (locallyAway.remove(player.getUniqueId())) {
            UUID uuid = player.getUniqueId();
            String username = player.getName();
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                    () -> writeAway(uuid, username, false));
        }
    }

    /**
     * Re-reads the shared key. Blocks; call from an async thread.
     */
    public void refresh() {
        String raw = redis.getOrSet(keys.afkKey(), "[]");
        try {
            JsonArray array = JsonParser.parseString(raw).getAsJsonArray();
            Set<UUID> parsed = new HashSet<>(array.size());
            for (JsonElement element : array) {
                parsed.add(UUID.fromString(element.getAsJsonObject().get("uuid").getAsString()));
            }
            away = Set.copyOf(parsed);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not parse " + keys.afkKey() + ": " + raw, e);
        }
    }

    /**
     * Marks anyone idle past the configured timeout as away. Runs on the main
     * thread so it can see the online players; the Redis write it triggers does
     * not.
     */
    public void sweepIdlePlayers() {
        if (settings.autoAwaySeconds() <= 0) {
            return;
        }

        long cutoff = System.currentTimeMillis() - settings.autoAwaySeconds() * 1000L;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Long last = lastActivity.get(player.getUniqueId());
            if (last != null && last < cutoff && !locallyAway.contains(player.getUniqueId())) {
                setAway(player, true);
            }
        }
    }

    private void writeAway(UUID uuid, String username, boolean shouldBeAway) {
        String raw = redis.getOrSet(keys.afkKey(), "[]");
        try {
            JsonArray array = JsonParser.parseString(raw).getAsJsonArray();
            JsonArray updated = new JsonArray();

            for (JsonElement element : array) {
                JsonObject entry = element.getAsJsonObject();
                if (!uuid.toString().equalsIgnoreCase(entry.get("uuid").getAsString())) {
                    updated.add(entry);
                }
            }
            if (shouldBeAway) {
                JsonObject entry = new JsonObject();
                entry.addProperty("name", username);
                entry.addProperty("uuid", uuid.toString());
                updated.add(entry);
            }

            redis.set(keys.afkKey(), updated.toString());
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not update " + keys.afkKey(), e);
        }
    }
}
