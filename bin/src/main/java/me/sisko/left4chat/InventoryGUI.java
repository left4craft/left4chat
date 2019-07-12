/*
 * Decompiled with CFR 0.145.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.ChatColor
 *  org.bukkit.Material
 *  org.bukkit.entity.Player
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.InventoryView
 *  org.bukkit.inventory.ItemFlag
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 */
package me.sisko.left4chat;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class InventoryGUI {
    private static Inventory inv;

    public static void setup() {
        inv = Bukkit.createInventory(null, (int)9, (String)"Server Selector");
        ItemStack star = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = star.getItemMeta();
        meta.setDisplayName((Object)ChatColor.GREEN + (Object)ChatColor.BOLD + "Hub");
        meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_DESTROYS, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_PLACED_ON, ItemFlag.HIDE_UNBREAKABLE});
        star.setItemMeta(meta);
        inv.setItem(0, star);
        ItemStack grass = new ItemStack(Material.GRASS_BLOCK);
        meta = grass.getItemMeta();
        meta.setDisplayName((Object)ChatColor.GREEN + (Object)ChatColor.BOLD + "Survival");
        meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_DESTROYS, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_PLACED_ON, ItemFlag.HIDE_UNBREAKABLE});
        grass.setItemMeta(meta);
        inv.setItem(2, grass);
        ItemStack diamond = new ItemStack(Material.DIAMOND);
        meta = diamond.getItemMeta();
        meta.setDisplayName((Object)ChatColor.GREEN + (Object)ChatColor.BOLD + "Creative");
        meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_DESTROYS, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_PLACED_ON, ItemFlag.HIDE_UNBREAKABLE});
        diamond.setItemMeta(meta);
        inv.setItem(4, diamond);
        ItemStack zombie = new ItemStack(Material.ZOMBIE_HEAD);
        meta = zombie.getItemMeta();
        meta.setDisplayName((Object)ChatColor.GREEN + (Object)ChatColor.BOLD + "Zombies");
        meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_DESTROYS, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_PLACED_ON, ItemFlag.HIDE_UNBREAKABLE});
        zombie.setItemMeta(meta);
        inv.setItem(6, zombie);
        ItemStack barrier = new ItemStack(Material.BARRIER);
        meta = barrier.getItemMeta();
        meta.setDisplayName((Object)ChatColor.RED + (Object)ChatColor.BOLD + "Exit Menu");
        meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_DESTROYS, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_PLACED_ON, ItemFlag.HIDE_UNBREAKABLE});
        barrier.setItemMeta(meta);
        inv.setItem(8, barrier);
    }

    public static void open(Player p) {
        p.openInventory(inv);
    }

    public static String getName() {
        return "Server Selector";
    }
}

