package me.sisko.left4chat.util;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import io.loyloy.nicky.Nick;
import me.sisko.left4chat.commands.AfkCommand;
import me.sisko.left4chat.commands.AnnounceCommand;
import me.sisko.left4chat.commands.DiscordCommand;
import me.sisko.left4chat.commands.GGiveCosmeticCommand;
import me.sisko.left4chat.commands.GameCommand;
import me.sisko.left4chat.commands.LockdownCommand;
import me.sisko.left4chat.commands.MessageCommand;
import me.sisko.left4chat.commands.MessageTabComplete;
import me.sisko.left4chat.commands.ReloadCommand;
import me.sisko.left4chat.commands.ReplyCommand;
import me.sisko.left4chat.commands.VerifyCommand;
import me.sisko.left4chat.sql.AsyncKeepAlive;
import me.sisko.left4chat.sql.AsyncUserUpdate;
import me.sisko.left4chat.sql.SQLManager;

import java.sql.Connection;
import java.util.HashMap;
import java.util.UUID;

import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

public class Main extends JavaPlugin implements Listener {
    public static Main plugin;
    private static Connection connection;
    private static Permission perms;
    private static Chat chat;
    private static HashMap<Player, Long> moveTimes;
    private static HashMap<Player, Integer> warnings;

    static {
        perms = null;
        chat = null;
    }

    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        this.getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        plugin = this;
        this.getCommand("discord").setExecutor(new DiscordCommand());
        this.getCommand("announce").setExecutor(new AnnounceCommand());
        this.getCommand("game").setExecutor(new GameCommand());
        this.getCommand("msg").setExecutor(new MessageCommand());
        this.getCommand("msg").setTabCompleter(new MessageTabComplete());
        this.getCommand("reply").setExecutor(new ReplyCommand());
        this.getCommand("afk").setExecutor(new AfkCommand());
        this.getCommand("ggivecosmetic").setExecutor(new GGiveCosmeticCommand());
        this.getCommand("verify").setExecutor(new VerifyCommand());
        this.getCommand("chatlock").setExecutor(new LockdownCommand());
        this.getCommand("chatreload").setExecutor(new ReloadCommand());

        moveTimes = new HashMap<Player, Long>();
        warnings = new HashMap<Player, Integer>();
        RegisteredServiceProvider<Permission> rspPerm = this.getServer().getServicesManager()
                .getRegistration(Permission.class);
        perms = (Permission) rspPerm.getProvider();
        RegisteredServiceProvider<Chat> rspChat = this.getServer().getServicesManager().getRegistration(Chat.class);
        chat = (Chat) rspChat.getProvider();
        InventoryGUI.setup();
        connection = SQLManager.connect();
        new AsyncKeepAlive(connection).runTaskTimerAsynchronously((Plugin) this, 0L, 72000L);
        new CheckAFKTask().runTaskTimer((Plugin) this, 0L, 200L);
        this.subscribe();
        ConfigManager.load();
    }

    public static String getCodes() {
        Jedis j = new Jedis(Main.plugin.getConfig().getString("redisip"));
        j.auth(Main.plugin.getConfig().getString("redispass"));
        String codes = j.get("discord.synccodes");
        j.close();
        return codes;
    }

    public static String getGroup(String w, OfflinePlayer op) {
        return perms.getPrimaryGroup(w, op);
    }

    public static Connection getSQL() {
        return connection;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        e.setJoinMessage(null);
        // new AsyncFixCoins(getSQL(), e.getPlayer()).runTaskLaterAsynchronously(this, 20);
        try {
            new AsyncUserUpdate().setup(connection, e.getPlayer()).runTaskAsynchronously((Plugin) this);
        } catch (Exception e2) {
            new AsyncUserUpdate().setup(connection, e.getPlayer()).runTaskAsynchronously((Plugin) this);
        }
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent e) {
        if (warnings.get(e.getPlayer()) != null) {
            warnings.remove(e.getPlayer());
        }
        this.setAFK(e.getPlayer(), false, false);
        moveTimes.remove(e.getPlayer());
        e.setQuitMessage(null);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        e.getFrom().distance(e.getTo());
        moveTimes.put(e.getPlayer(), System.currentTimeMillis());
        this.setAFK(e.getPlayer(), false, true);
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
        moveTimes.put(e.getPlayer(), System.currentTimeMillis());
        this.setAFK(e.getPlayer(), false, true);

        if(e.getMessage().equalsIgnoreCase("stop")) {
            for(Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(ChatColor.RED + "The server you were on is restarting, so you have been moved to hub.");
                ByteArrayDataOutput out = ByteStreams.newDataOutput();
                out.writeUTF("Connect");
                out.writeUTF("hub");
                p.sendPluginMessage((Plugin) plugin, "BungeeCord", out.toByteArray());  
              }
        }
    }

    @EventHandler
    public void onServerCommand(ServerCommandEvent e) {
        if(e.getCommand().equalsIgnoreCase("stop")) {
            for(Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(ChatColor.RED + "The server you were on is restarting, so you have been moved to hub.");
                ByteArrayDataOutput out = ByteStreams.newDataOutput();
                out.writeUTF("Connect");
                out.writeUTF("hub");
                p.sendPluginMessage((Plugin) plugin, "BungeeCord", out.toByteArray());  
              }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        moveTimes.put(e.getPlayer(), System.currentTimeMillis());
        this.setAFK(e.getPlayer(), false, true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(AsyncPlayerChatEvent e) {
        Jedis j = new Jedis(Main.plugin.getConfig().getString("redisip"));
        j.auth(Main.plugin.getConfig().getString("redispass"));
        if (j.get("minecraft.lockdown") != null && j.get("minecraft.lockdown").equals("true")
                && !perms.has(e.getPlayer(), "left4chat.verified")) {
            if (VerifyCommand.playerVerifying(e.getPlayer())) {
                j.publish("minecraft.console.hub.in",
                        "kick " + e.getPlayer().getName() + " Incorrect CAPTCHA solution");
                j.close();
                return;
            }
            if (warnings.get(e.getPlayer()) == null) {
                e.getPlayer()
                        .sendMessage(ChatColor.RED + "You must verify your account with " + ChatColor.GOLD + "/verify"
                                + ChatColor.RED + " before chatting or you will be " + ChatColor.BOLD + "permbanned"
                                + ChatColor.RED + " (Warning 1/5)");
                warnings.put(e.getPlayer(), 2);
                e.setCancelled(true);
                j.close();
                return;
            }
            if (warnings.get(e.getPlayer()) <= 5) {
                e.getPlayer()
                        .sendMessage(ChatColor.RED + "You must verify your account with " + ChatColor.GOLD + "/verify"
                                + ChatColor.RED + " before chatting or you will be " + ChatColor.BOLD + "permbanned"
                                + ChatColor.RED + " (Warning " + warnings.get(e.getPlayer()) + "/5)");
                warnings.put(e.getPlayer(), warnings.get(e.getPlayer()) + 1);
                e.setCancelled(true);
                j.close();
                return;
            }
            warnings.remove(e.getPlayer());
            j.publish("minecraft.console.hub.in", "ban " + e.getPlayer().getName() + " Spambot (appealable)");
            e.setCancelled(true);
            j.close();
            return;
        }
        this.setAFK(e.getPlayer(), false, true);
        moveTimes.put(e.getPlayer(), System.currentTimeMillis());
        String group = perms.getPrimaryGroup(e.getPlayer());
        String name = new Nick(e.getPlayer()).get();
        if (name == null) {
            name = e.getPlayer().getName();
        }
        name = ChatColor.translateAlternateColorCodes('&', name);
		HashMap<String, String> chatMessage = new HashMap<String, String>();
		chatMessage.put("type", "message");
        chatMessage.put("uuid", e.getPlayer().getUniqueId().toString());
        chatMessage.put("name", "[" + group + "] " + ChatColor.stripColor(name));
        chatMessage.put("message", ChatColor.stripColor(e.getMessage()));

        if (!e.isCancelled()) {
            j.publish("minecraft.chat.global.out", new JSONObject(chatMessage).toString());
            String message = e.getMessage();
            if (!perms.has(e.getPlayer(), "left4chat.format")) {
                if (perms.has(e.getPlayer(), "left4chat.color")) {
                    String[] formats = { "&l", "&k", "&m", "&n", "&o" };
                    for (String format : formats) {
                        while (message.contains(format)) {
                            message = message.replace(format, "");
                        }
                    }
                } else {
                    message = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', message));
                }
            }
            j.publish("minecraft.chat.global.in",
                    String.valueOf(chat.getPlayerPrefix(e.getPlayer())) + name + ChatColor.RESET + " " + message);
            e.setCancelled(true);

        }

        j.close();
    }

    @EventHandler
    public void onInvClick(InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        moveTimes.put(p, System.currentTimeMillis());
        this.setAFK(p, false, true);
        if (e.getClickedInventory() != null && e.getView().getTitle().equals(InventoryGUI.getName())) {
            ItemStack clicked = e.getCurrentItem();
            if (clicked != null && clicked.getType() == Material.NETHER_STAR) {
                ByteArrayDataOutput out = ByteStreams.newDataOutput();
                out.writeUTF("Connect");
                out.writeUTF("hub");
                p.sendPluginMessage((Plugin) this, "BungeeCord", out.toByteArray());
            } else if (clicked != null && clicked.getType() == Material.GRASS_BLOCK) {
                ByteArrayDataOutput out = ByteStreams.newDataOutput();
                out.writeUTF("Connect");
                out.writeUTF("survival");
                p.sendPluginMessage((Plugin) this, "BungeeCord", out.toByteArray());
            } else if (clicked != null && clicked.getType() == Material.DIAMOND) {
                ByteArrayDataOutput out = ByteStreams.newDataOutput();
                out.writeUTF("Connect");
                out.writeUTF("creative");
                p.sendPluginMessage((Plugin) this, "BungeeCord", out.toByteArray());
            } else if (clicked != null && clicked.getType() == Material.SNOWBALL) {
                ByteArrayDataOutput out = ByteStreams.newDataOutput();
                out.writeUTF("Connect");
                out.writeUTF("paintball");
                p.sendPluginMessage((Plugin) this, "BungeeCord", out.toByteArray());
            }
            p.closeInventory();
            p.updateInventory();
            e.setCancelled(true);
        } else if (VerifyCommand.playerVerifying(p)) {
            ItemStack clicked = e.getCurrentItem();
            if (VerifyCommand.getSolution(p) == VerifyCommand.getType(clicked.getType())) {
                p.sendMessage(ChatColor.GREEN + "Account Verified! You may now chat freely.");
                p.closeInventory();
                p.updateInventory();
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        "lp user " + p.getName() + " permission set left4chat.verified");
            } else {
                p.sendMessage(ChatColor.RED + "Incorrect CAPTCHA response!");
                p.closeInventory();
                p.updateInventory();
                Jedis j = new Jedis(Main.plugin.getConfig().getString("redisip"));
                j.auth(Main.plugin.getConfig().getString("redispass"));
                j.publish("minecraft.console.hub.in",
                        "kick " + e.getWhoClicked().getName() + " Incorrect CAPTCHA solution");
                j.close();
            }
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        Player p = (Player) e.getPlayer();
        if (VerifyCommand.playerVerifying(p)) {
            p.sendMessage(ChatColor.RED + "Kicked for incorrect CAPTCHA response!");
            Jedis j = new Jedis(Main.plugin.getConfig().getString("redisip"));
            j.auth(Main.plugin.getConfig().getString("redispass"));
            j.publish("minecraft.console.hub.in", "kick " + e.getPlayer().getName() + " Incorrect CAPTCHA solution");
            j.close();
        }
    }

    private JedisPubSub subscribe() {
        final JedisPubSub jedisPubSub = new JedisPubSub() {

            @Override
            public void onMessage(String channel, String message) {
                if (channel.equals("minecraft.chat.global.in")) {
                    Main.this.getServer()
                            .broadcastMessage(ChatColor.translateAlternateColorCodes((char) '&', (String) message));
                } else if (channel.equals("minecraft.chat.messages")) {

                    try {
                    JSONObject json = new JSONObject(message);

                    Main.this.getLogger().info("[MSG] [" + json.getString("from_name") + " -> " + json.getString("to_name") + "] " + json.getString("message"));

                    Player reciever = Bukkit.getPlayer(UUID.fromString(json.getString("to")));
                    if(reciever != null) reciever.sendMessage(ChatColor.translateAlternateColorCodes((char) '&', "&c[&6" + json.getString("from_nick") + " &c-> &6You&c]&r " + json.getString("message")));

                    } catch (JSONException e) {
                        getLogger().warning("Invalid JSON sent in minecraft.chat.messages: " + message);
                        e.printStackTrace();
                    }
                }
            }
        };
        new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    Jedis jedis = new Jedis(Main.plugin.getConfig().getString("redisip"));
                    jedis.auth(Main.plugin.getConfig().getString("redispass"));
                    jedis.subscribe(jedisPubSub, "minecraft.chat.global.in", "minecraft.chat.messages");
                    Main.this.getLogger().warning("Subscriber closed!");
                    jedis.quit();
                    jedis.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, "subscriberThread").start();
        return jedisPubSub;
    }

    public Permission getPerms() {
        return perms;
    }

    public void toggleAfk(Player p) {
        setAFK(p, !isAFK(p), true);
    }
    
    private boolean isAFK(Player p) {
        String uuid = p.getUniqueId().toString();
        Jedis jedis = new Jedis(Main.plugin.getConfig().getString("redisip"));
		jedis.auth(Main.plugin.getConfig().getString("redispass"));

        String jsonStr = jedis.get("minecraft.afk");
        if(jsonStr == null) {
            jedis.set("minecraft.afk", "[]");
            jsonStr = "[]";
        }

        JSONArray afk = new JSONArray(jsonStr);

        jedis.close();

        for(int i = 0; i < afk.length(); i++) {
            if(afk.getJSONObject(i).getString("uuid").equalsIgnoreCase(uuid)) return true;
        }
        return false;
    }

    public void setAFK(Player p, boolean afk, boolean verbose) {
        Jedis jedis = new Jedis(Main.plugin.getConfig().getString("redisip"));
		jedis.auth(Main.plugin.getConfig().getString("redispass"));
        String name = p.getName();

        JSONObject msg = new JSONObject();
		msg.put("type", "afk");
        msg.put("name",name);
        
        String jsonStr = jedis.get("minecraft.afk");
        if(jsonStr == null) {
            jedis.set("minecraft.afk", "[]");
            jsonStr = "[]";
        }

        JSONArray json = new JSONArray(jsonStr);

        boolean afkInJedis = false;
        int jedisIndex = -1;
        for(int i = 0; i < json.length(); i++) {
            if(json.getJSONObject(i).getString("uuid").equalsIgnoreCase(p.getUniqueId().toString())) {
                afkInJedis = true;
                jedisIndex = i;
            }
        }

        if (afk) {
            if(!afkInJedis) {
                json.put(new JSONObject().put("name", p.getName()).put("uuid", p.getUniqueId().toString()));
                jedis.set("minecraft.afk", json.toString());
                if (verbose) {
                    jedis.publish("minecraft.chat.global.in", "&7 * " + name + " is now afk.");
                    msg.put("afk", true);
                    jedis.publish("minecraft.chat.global.out", msg.toString());
                }
            }
            perms.playerAdd(p, "sleepmost.exempt");
        } else if (!afk) {
            if(afkInJedis) {
                json.remove(jedisIndex);
                jedis.set("minecraft.afk", json.toString());
                if (verbose) {
                    jedis.publish("minecraft.chat.global.in", "&7 * " + name + " is no longer afk.");
                    msg.put("afk", false);
                    jedis.publish("minecraft.chat.global.out", msg.toString());
                }
            }
            perms.playerRemove(p, "sleepmost.exempt");
        }
        jedis.close();
    }

    public HashMap<Player, Long> getMoveTimes() {
        return moveTimes;
    }

    public static Main getPlugin() {
        return plugin;
    }

    public static void ReconnectSQL() {
        connection = SQLManager.connect();
    }

    public static boolean promoteToUser(Player p) {
        if(!perms.has(p, "group.user")) {
            perms.playerAdd(null, p, "group.user");
            return true;
        }
        return false;
    }

}
