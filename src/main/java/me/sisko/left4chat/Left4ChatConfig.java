package me.sisko.left4chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import me.sisko.left4chat.redis.RedisBridge;
import me.sisko.left4chat.sql.DiscordLinkRepository;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * An immutable snapshot of {@code config.yml}.
 *
 * <p>Everything the old plugin reached for through
 * {@code Main.plugin.getConfig().getString(...)} on every single call -- once
 * per Redis connection, which was once per chat message -- is read here instead,
 * and a reload swaps the whole record.
 *
 * <p>The Redis keys, the announcement prefixes, the Discord invite and the
 * contents of the {@code /game} selector were all compiled into the old jar.
 *
 * @param serverName        this server's name, as the proxy knows it
 * @param fallbackServer    where players are sent when this server stops
 * @param consoleRelay      whether to run console commands published to this server's channel
 * @param redis             Redis connection details
 * @param keys              Redis keys and channels
 * @param database          Postgres connection details
 * @param chat              chat and lockdown behaviour
 * @param afk               AFK behaviour
 * @param selector          the {@code /game} server selector
 * @param messages          user-facing strings
 */
public record Left4ChatConfig(
        String serverName,
        String fallbackServer,
        boolean consoleRelay,
        RedisBridge.Settings redis,
        Keys keys,
        DiscordLinkRepository.Settings database,
        Chat chat,
        Afk afk,
        Selector selector,
        Messages messages) {

    /**
     * Redis keys and channels. Shared with the Velocity plugin and the Discord
     * bot, so all three have to agree.
     *
     * @param chatChannel          cross-server chat and presence
     * @param botCommandChannel    commands sent to the Discord bot
     * @param consoleChannelFormat console relay, with {@code {server}} substituted
     * @param playerListKey        JSON array of everyone online, written by the proxy
     * @param afkKey               JSON array of AFK players
     * @param repliesKey           JSON object mapping sender to last recipient
     * @param lockdownKey          {@code "true"} while chat is locked
     * @param syncCodesKey         JSON object of pending Discord sync codes
     */
    public record Keys(
            String chatChannel,
            String botCommandChannel,
            String consoleChannelFormat,
            String playerListKey,
            String afkKey,
            String repliesKey,
            String lockdownKey,
            String syncCodesKey) {

        /**
         * The console relay channel for a given server.
         *
         * @param server the server name
         * @return the channel
         */
        public String consoleChannel(String server) {
            return consoleChannelFormat.replace("{server}", server);
        }
    }

    /**
     * @param lockdownWarnings how many warnings before a spambot gets banned
     * @param lockdownServer   which server's console runs the kick and ban
     * @param verifiedPermission permission a player needs to chat during lockdown
     * @param colorPermission  permission to use colour codes in chat
     * @param formatPermission permission to use bold, italics and friends
     * @param lockPermission   permission to run {@code /chatlock}
     */
    public record Chat(
            int lockdownWarnings,
            String lockdownServer,
            String verifiedPermission,
            String colorPermission,
            String formatPermission,
            String lockPermission) {
    }

    /**
     * @param autoAwaySeconds  idle seconds before a player is marked away, 0 to disable
     * @param exemptPermission permission granted while a player is AFK
     */
    public record Afk(int autoAwaySeconds, String exemptPermission) {
    }

    /**
     * @param title   the inventory title
     * @param entries one per slot
     */
    public record Selector(String title, List<Entry> entries) {

        /**
         * @param slot     inventory slot
         * @param server   proxy server name, or empty to just close the menu
         * @param material the icon
         * @param name     the icon's display markup
         */
        public record Entry(int slot, String server, Material material, String name) {
        }
    }

    /**
     * @param discordInvite         shown by a bare {@code /discord}
     * @param verifyRequired        shown when an unverified player tries to chat or message
     * @param verifyWarning         the lockdown warning, with {@code {warning}} and {@code {max}}
     * @param verified              shown when the captcha is solved
     * @param captchaFailed         shown when the captcha is failed
     * @param captchaClosed         shown when the captcha menu is closed
     * @param restarting            shown when the server stops and players are moved
     * @param chatLocked            announced when chat is locked
     * @param chatLockedHint        the follow-up hint
     * @param chatUnlocked          announced when chat is unlocked
     * @param firstJoin             announced when a player joins the network for the first time
     * @param listHeader            the {@code /list} header
     * @param listStaff             the {@code /list} staff line prefix
     * @param listPlayers           the {@code /list} player line prefix
     * @param listNoStaff           shown in place of an empty staff list
     * @param listFooter            the {@code /list} footer
     * @param privateMessageOut     the outgoing private message format
     * @param privateMessageIn      the incoming private message format
     * @param chatFormat            the cross-server chat line format
     */
    public record Messages(
            String discordInvite,
            String verifyRequired,
            String verifyWarning,
            String verified,
            String captchaFailed,
            String captchaClosed,
            String restarting,
            String chatLocked,
            String chatLockedHint,
            String chatUnlocked,
            String firstJoin,
            String listHeader,
            String listStaff,
            String listPlayers,
            String listNoStaff,
            String listFooter,
            String privateMessageOut,
            String privateMessageIn,
            String chatFormat) {
    }

    /**
     * Reads the settings out of a loaded config.
     *
     * @param config the plugin config
     * @return the snapshot
     */
    public static Left4ChatConfig from(FileConfiguration config) {
        ConfigurationSection redis = section(config, "redis");
        ConfigurationSection database = section(config, "database");
        ConfigurationSection keys = section(config, "redis.keys");
        ConfigurationSection chat = section(config, "chat");
        ConfigurationSection afk = section(config, "afk");
        ConfigurationSection selector = section(config, "selector");
        ConfigurationSection messages = section(config, "messages");

        return new Left4ChatConfig(
                config.getString("server-name", "hub"),
                config.getString("fallback-server", "hub"),
                config.getBoolean("console-relay", true),
                new RedisBridge.Settings(
                        redis.getString("host", "127.0.0.1"),
                        redis.getInt("port", 6379),
                        redis.getString("username", ""),
                        redis.getString("password", ""),
                        redis.getInt("database", 0),
                        redis.getInt("timeout-millis", 2000),
                        redis.getInt("max-connections", 8)),
                new Keys(
                        keys.getString("chat-channel", "minecraft.chat"),
                        keys.getString("bot-command-channel", "discord.botcommands"),
                        keys.getString("console-channel-format", "minecraft.console.{server}.in"),
                        keys.getString("player-list", "minecraft.players"),
                        keys.getString("afk", "minecraft.afk"),
                        keys.getString("replies", "minecraft.chat.replies"),
                        keys.getString("lockdown", "minecraft.lockdown"),
                        keys.getString("sync-codes", "discord.synccodes")),
                new DiscordLinkRepository.Settings(
                        database.getString("host", "localhost"),
                        database.getInt("port", 5432),
                        database.getString("database", "postgres"),
                        database.getString("user", "postgres"),
                        database.getString("password", ""),
                        database.getString("ssl-mode", "verify-full"),
                        database.getInt("pool-size", 2),
                        database.getString("table", "discord_users")),
                new Chat(
                        chat.getInt("lockdown-warnings", 5),
                        chat.getString("lockdown-server", "hub"),
                        chat.getString("verified-permission", "left4chat.verified"),
                        chat.getString("color-permission", "left4chat.color"),
                        chat.getString("format-permission", "left4chat.format"),
                        chat.getString("lock-permission", "left4chat.chatlock")),
                new Afk(
                        afk.getInt("auto-away-seconds", 0),
                        afk.getString("exempt-permission", "sleepmost.exempt")),
                selector(selector),
                messages(messages));
    }

    private static Selector selector(ConfigurationSection section) {
        List<Selector.Entry> entries = new ArrayList<>();

        for (java.util.Map<?, ?> row : section.getMapList("entries")) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> raw = (java.util.Map<String, Object>) row;
            String material = String.valueOf(raw.get("material")).toUpperCase(Locale.ROOT);
            Material icon = Material.matchMaterial(material);
            if (icon == null) {
                throw new IllegalArgumentException("selector entry has an unknown material: " + material);
            }
            entries.add(new Selector.Entry(
                    ((Number) raw.getOrDefault("slot", 0)).intValue(),
                    String.valueOf(raw.getOrDefault("server", "")),
                    icon,
                    String.valueOf(raw.getOrDefault("name", material))));
        }

        return new Selector(section.getString("title", "Server Selector"), List.copyOf(entries));
    }

    private static Messages messages(ConfigurationSection section) {
        return new Messages(
                section.getString("discord-invite",
                        "&#7289DA[Discord&fSync&#7289DA] Join here: &b&ndiscord.left4craft.org"),
                section.getString("verify-required",
                        "&cYou must verify with &6/verify&c to message other players!"),
                section.getString("verify-warning",
                        "&cYou must verify your account with &6/verify&c before chatting or you will be "
                                + "&lpermbanned&r&c (Warning {warning}/{max})"),
                section.getString("verified", "&aAccount Verified! You may now chat freely."),
                section.getString("captcha-failed", "&cIncorrect CAPTCHA response!"),
                section.getString("captcha-closed", "&cKicked for incorrect CAPTCHA response!"),
                section.getString("restarting",
                        "&cThe server you were on is restarting, so you have been moved to hub."),
                section.getString("chat-locked", "&c&l{player}&c has locked the server chat!"),
                section.getString("chat-locked-hint", "&bAll unverified guests must use &a/verify to chat"),
                section.getString("chat-unlocked", "&a{player} has unlocked the server chat!"),
                section.getString("first-join", "&d{player} has joined Left4Craft for the first time!"),
                section.getString("list-header", "&6There are &c{count} &6{players} online:"),
                section.getString("list-staff", "&6Staff: &r"),
                section.getString("list-players", "&6Players: &r"),
                section.getString("list-no-staff",
                        "&fTag &#7289DA@Staff &fon &#7289DADiscord &ffor help whilst no staff are online."),
                section.getString("list-footer", "&7To sort players by gamemode, type &e/glist"),
                section.getString("private-message-out", "&c[&6You &c-> &6{nick}&c]&r "),
                section.getString("private-message-in", "&c[&6{nick}&c -> &6You&c]&r "),
                section.getString("chat-format", "{prefix}{nick} "));
    }

    private static ConfigurationSection section(FileConfiguration config, String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            throw new IllegalStateException(
                    "config.yml is missing the '" + path + "' section. Delete it to regenerate the defaults.");
        }
        return section;
    }
}
