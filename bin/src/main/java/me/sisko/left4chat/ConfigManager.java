/*
 * Decompiled with CFR 0.145.
 * 
 * Could not load the following classes:
 *  org.bukkit.configuration.file.FileConfiguration
 *  org.bukkit.configuration.file.FileConfigurationOptions
 */
package me.sisko.left4chat;

import java.io.File;
import java.util.logging.Logger;
import me.sisko.left4chat.Main;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.FileConfigurationOptions;

public class ConfigManager {
    public static void load() {
        FileConfiguration config = Main.getPlugin().getConfig();
        File dataFolder = Main.getPlugin().getDataFolder();
        config.addDefault("sql.host", (Object)"127.0.0.1");
        config.addDefault("sql.database", (Object)"data");
        config.addDefault("sql.port", (Object)3306);
        config.addDefault("sql.user", (Object)"user");
        config.addDefault("sql.pass", (Object)"password");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        if (!new File(dataFolder, "config.yml").exists()) {
            Main.getPlugin().getLogger().info("Config.yml not found, creating!");
            config.options().copyDefaults(true);
            Main.getPlugin().saveConfig();
        } else {
            Main.getPlugin().getLogger().info("Config.yml found, loading!");
        }
    }

    public static void reload() {
        Main.getPlugin().reloadConfig();
        Main.ReconnectSQL();
    }
}

