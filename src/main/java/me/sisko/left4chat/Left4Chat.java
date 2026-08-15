package me.sisko.left4chat;

import java.util.List;
import java.util.OptionalLong;
import java.util.logging.Level;

import com.google.gson.JsonObject;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.sisko.left4chat.chat.ChatListener;
import me.sisko.left4chat.chat.ChatSubscriber;
import me.sisko.left4chat.command.DiscordCommand;
import me.sisko.left4chat.command.ListCommand;
import me.sisko.left4chat.command.LockdownCommand;
import me.sisko.left4chat.command.MessageCommand;
import me.sisko.left4chat.command.ReplyCommand;
import me.sisko.left4chat.command.SimpleCommands;
import me.sisko.left4chat.console.ConsoleRelay;
import me.sisko.left4chat.integration.Nicknames;
import me.sisko.left4chat.integration.Permissions;
import me.sisko.left4chat.presence.AfkService;
import me.sisko.left4chat.presence.PlayerDirectory;
import me.sisko.left4chat.redis.RedisBridge;
import me.sisko.left4chat.sql.DiscordLinkRepository;
import me.sisko.left4chat.text.AmpersandColors;
import me.sisko.left4chat.ui.Captcha;
import me.sisko.left4chat.ui.ServerSelector;
import net.luckperms.api.LuckPerms;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Cross-server chat, private messages, AFK tracking and Discord account linking.
 *
 * <p>The Paper 26.2 rewrite of the plugin that used to live in
 * {@code me.sisko.left4chat.util.Main} -- a 555-line class that was the plugin,
 * the listener, the Redis client, the AFK service and the chat renderer all at
 * once, reachable from anywhere through a public static field.
 *
 * <p>The wire format is untouched. Every Redis key, channel and JSON field is
 * exactly what it was, because the Velocity plugin and the Discord bot read the
 * same ones.
 */
public final class Left4Chat extends JavaPlugin {

    private Left4ChatConfig config;
    private RedisBridge redis;
    private DiscordLinkRepository discordLinks;
    private Permissions permissions;
    private Nicknames nicknames;
    private PlayerDirectory playerDirectory;
    private AfkService afk;
    private ServerSelector selector;
    private Captcha captcha;

    private BukkitTask pollTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        RegisteredServiceProvider<LuckPerms> luckPerms =
                getServer().getServicesManager().getRegistration(LuckPerms.class);
        if (luckPerms == null) {
            getLogger().severe("LuckPerms is not loaded; Left4Chat cannot start.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        permissions = new Permissions(luckPerms.getProvider());
        nicknames = Nicknames.of(getServer());
        if (!nicknames.available()) {
            getLogger().warning("Nicky is not installed; players will be shown by username.");
        }

        // Built before start(), because start() opens the console relay and a
        // relayed "stop" arriving straight away calls evacuate(), which needs
        // the selector to move players.
        selector = new ServerSelector(this);
        captcha = new Captcha(this);

        try {
            start();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Left4Chat could not start", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(selector, this);
        getServer().getPluginManager().registerEvents(captcha, this);

        registerCommands();
    }

    @Override
    public void onDisable() {
        if (pollTask != null) {
            pollTask.cancel();
        }
        if (redis != null) {
            redis.close();
        }
        if (discordLinks != null) {
            discordLinks.close();
        }
    }

    /**
     * Re-reads config.yml and rebuilds the Redis and Postgres connections.
     *
     * @throws Exception if the new settings are unusable
     */
    public void reloadLeft4Chat() throws Exception {
        reloadConfig();

        if (pollTask != null) {
            pollTask.cancel();
        }
        RedisBridge oldRedis = redis;
        DiscordLinkRepository oldLinks = discordLinks;

        start();

        if (oldRedis != null) {
            oldRedis.close();
        }
        if (oldLinks != null) {
            oldLinks.close();
        }
    }

    private void start() throws Exception {
        config = Left4ChatConfig.from(getConfig());

        redis = new RedisBridge(getLogger(), config.redis());
        discordLinks = new DiscordLinkRepository(getLogger(), config.database());
        discordLinks.createTable();

        playerDirectory = new PlayerDirectory(getLogger(), redis, config.keys());
        afk = new AfkService(this, redis, config.keys(), config.afk(), permissions);

        redis.subscribe(config.keys().chatChannel(), new ChatSubscriber(this)::accept);

        // Taken over from the Left4Craft plugin, which existed to do only this.
        if (config.consoleRelay()) {
            String channel = ConsoleRelay.channel(config);
            redis.subscribe(channel, new ConsoleRelay(this)::accept);
            getLogger().info("Console relay listening on " + channel);
        }

        redis.startSubscriber();

        // One async poll refreshes the roster and the AFK set, both of which are
        // written by other servers. Five seconds is far cheaper than the old
        // behaviour of re-reading them on every command and every keystroke.
        pollTask = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            playerDirectory.refresh();
            afk.refresh();
        }, 20L, 100L);

        if (config.afk().autoAwaySeconds() > 0) {
            getServer().getScheduler().runTaskTimer(this, () -> afk.sweepIdlePlayers(), 200L, 200L);
        }

        getLogger().info("Redis " + config.redis().host() + ":" + config.redis().port()
                + ", Postgres " + config.database().host() + ":" + config.database().port()
                + "/" + config.database().database());
    }

    private void registerCommands() {
        MessageCommand message = new MessageCommand(this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var commands = event.registrar();
            commands.register("discord", "Sync with the Left4Craft Discord", List.of(),
                    new DiscordCommand(this));
            commands.register("announce", "Send a message to the entire network", List.of(),
                    new SimpleCommands.Announce(this));
            commands.register("game", "Open the server selector", List.of(),
                    new SimpleCommands.Game(this));
            commands.register("msg", "Message a player on any server",
                    List.of("m", "tell", "pm", "message", "w", "whisper"), message);
            commands.register("reply", "Reply to the last message you received", List.of("r"),
                    new ReplyCommand(this, message));
            commands.register("afk", "Toggle AFK status", List.of(),
                    new SimpleCommands.Afk(this));
            commands.register("ggivecosmetic", "Grant cosmetics network-wide", List.of(),
                    new SimpleCommands.GlobalGiveCosmetic(this));
            commands.register("verify", "Verify your account", List.of(),
                    new SimpleCommands.Verify(this));
            commands.register("chatlock", "Lock chat behind /verify", List.of(),
                    new LockdownCommand(this));
            commands.register("chatreload", "Reload Left4Chat", List.of(),
                    new SimpleCommands.Reload(this));
            commands.register("list", "List everyone online across the network", List.of(),
                    new ListCommand(this));
        });
    }

    /**
     * Sends everyone to the fallback server before this one goes away.
     *
     * <p>Called from three places: a player running {@code /stop}, the console
     * running it, and the console relay receiving it over Redis. The relay needs
     * its own call because {@code Bukkit.dispatchCommand} does not fire
     * {@link org.bukkit.event.server.ServerCommandEvent}.
     */
    public void evacuate() {
        for (Player player : getServer().getOnlinePlayers()) {
            player.sendMessage(AmpersandColors.format(config.messages().restarting()));
            selector.connect(player, config.fallbackServer());
        }
    }

    /**
     * Marks a player as having passed the captcha.
     *
     * <p>The old code did this by building the string
     * {@code "lp user " + name + " permission set left4chat.verified"} and
     * dispatching it through the console, which meant a player whose name the
     * command parser disliked simply never got verified.
     *
     * @param player the player
     */
    public void grantVerified(Player player) {
        permissions.grant(player, config.chat().verifiedPermission())
                .exceptionally(error -> {
                    getLogger().log(Level.WARNING,
                            "Could not mark " + player.getName() + " as verified", error);
                    return null;
                });
    }

    /**
     * Pushes a player's nickname and rank to the Discord bot, and promotes them
     * out of the guest group once they have linked an account.
     *
     * @param player the player
     */
    public void syncDiscordProfile(Player player) {
        String username = player.getName();
        java.util.UUID uuid = player.getUniqueId();
        String nickname = nicknames.plain(uuid, username);
        String group = permissions.primaryGroup(player);

        boolean unranked = group.equals("guest") || group.equals("default");

        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            OptionalLong discordId = discordLinks.findDiscordId(uuid);
            if (discordId.isEmpty()) {
                getLogger().fine("No Discord account linked for " + username);
                return;
            }

            String id = String.valueOf(discordId.getAsLong());

            JsonObject setUser = new JsonObject();
            setUser.addProperty("command", "setuser");
            setUser.addProperty("id", id);
            setUser.addProperty("nick", nickname);

            JsonObject setGroup = new JsonObject();
            setGroup.addProperty("command", "setgroup");
            setGroup.addProperty("id", id);
            setGroup.addProperty("group", unranked ? "user" : group);

            redis.publish(config.keys().botCommandChannel(), setUser.toString());
            redis.publish(config.keys().botCommandChannel(), setGroup.toString());

            // Keep the stored nickname current, so the bot's own queries agree.
            discordLinks.saveNickname(uuid, nickname);

            getLogger().info("Linked Minecraft account " + username + " to Discord account " + id);

            if (unranked) {
                permissions.addToGroup(player, "user")
                        .thenAccept(changed -> getLogger().info("Promoted " + username + " to user."));
            }
        });
    }

    /**
     * The current settings.
     *
     * @return the config snapshot
     */
    public Left4ChatConfig config() {
        return config;
    }

    /**
     * The Redis connection.
     *
     * @return the bridge
     */
    public RedisBridge redis() {
        return redis;
    }

    /**
     * The Discord account links.
     *
     * @return the repository
     */
    public DiscordLinkRepository discordLinks() {
        return discordLinks;
    }

    /**
     * Permission, group and prefix lookups.
     *
     * @return the wrapper
     */
    public Permissions permissions() {
        return permissions;
    }

    /**
     * Nickname lookups.
     *
     * @return the wrapper
     */
    public Nicknames nicknames() {
        return nicknames;
    }

    /**
     * The network roster.
     *
     * @return the directory
     */
    public PlayerDirectory playerDirectory() {
        return playerDirectory;
    }

    /**
     * AFK state.
     *
     * @return the service
     */
    public AfkService afk() {
        return afk;
    }

    /**
     * The {@code /game} menu.
     *
     * @return the selector
     */
    public ServerSelector selector() {
        return selector;
    }

    /**
     * The {@code /verify} captcha.
     *
     * @return the captcha
     */
    public Captcha captcha() {
        return captcha;
    }
}
