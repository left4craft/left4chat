package me.sisko.left4chat.commands;

import java.util.HashMap;
import java.util.Random;
import me.sisko.left4chat.util.Main;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class VerifyCommand
implements CommandExecutor {
    private static Random rng = new Random();
    private static HashMap<Player, Type> solutions = new HashMap<Player, Type>();
    private static Material[] foods = new Material[]{Material.COOKED_BEEF, Material.COOKED_CHICKEN, Material.COOKED_PORKCHOP, Material.CARROT, Material.BAKED_POTATO};
    private static Material[] weapons = new Material[]{Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD, Material.GOLDEN_SWORD, Material.DIAMOND_SWORD};
    private static Material[] lights = new Material[]{Material.GLOWSTONE, Material.TORCH};
    private static Material[] explosives = new Material[]{Material.GUNPOWDER, Material.TNT, Material.TNT_MINECART};
    private static Material[] woods = new Material[]{Material.ACACIA_PLANKS, Material.DARK_OAK_PLANKS, Material.OAK_LOG, Material.SPRUCE_PLANKS, Material.BIRCH_PLANKS};
    private static Material[] ores = new Material[]{Material.COAL_ORE, Material.IRON_ORE, Material.GOLD_ORE, Material.DIAMOND_ORE, Material.EMERALD_ORE, Material.LAPIS_ORE};
    private static Material[] potions = new Material[]{Material.POTION, Material.SPLASH_POTION};
    private static Material[] wools = new Material[]{Material.BLACK_WOOL, Material.RED_WOOL, Material.YELLOW_WOOL, Material.BLUE_WOOL, Material.PURPLE_WOOL};
    private static Material[] glasses = new Material[]{Material.BLACK_STAINED_GLASS, Material.ORANGE_STAINED_GLASS, Material.GREEN_STAINED_GLASS, Material.LIGHT_BLUE_STAINED_GLASS};

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            Main.plugin.getLogger().info("You can't verify console!");
        } else {
            Player p = (Player)sender;
            if (Main.plugin.getPerms().has(p, "left4chat.verified")) {
                p.sendMessage(ChatColor.GREEN + "You are already verified! You may chat freely.");
                return true;
            }
            Type solution = Type.values()[rng.nextInt(Type.values().length)];
            solutions.put(p, solution);
            Inventory inv = Bukkit.createInventory(null, (int)9, (String)VerifyCommand.getTitle(solution));
            int i = 0;
            for (Type t : VerifyCommand.Shuffle(Type.values())) {
                ItemStack item = new ItemStack(Material.BARRIER);
                if (t == Type.FOOD) {
                    item = new ItemStack(foods[rng.nextInt(foods.length)]);
                }
                if (t == Type.WEAPON) {
                    item = new ItemStack(weapons[rng.nextInt(weapons.length)]);
                }
                if (t == Type.LIGHT) {
                    item = new ItemStack(lights[rng.nextInt(lights.length)]);
                }
                if (t == Type.EXPLOSIVE) {
                    item = new ItemStack(explosives[rng.nextInt(explosives.length)]);
                }
                if (t == Type.WOOD) {
                    item = new ItemStack(woods[rng.nextInt(woods.length)]);
                }
                if (t == Type.ORE) {
                    item = new ItemStack(ores[rng.nextInt(ores.length)]);
                }
                if (t == Type.POTION) {
                    item = new ItemStack(potions[rng.nextInt(potions.length)]);
                }
                if (t == Type.WOOL) {
                    item = new ItemStack(wools[rng.nextInt(wools.length)]);
                }
                if (t == Type.GLASS) {
                    item = new ItemStack(glasses[rng.nextInt(glasses.length)]);
                }
                inv.setItem(i, item);
                ++i;
            }
            p.openInventory(inv);
        }
        return true;
    }

    public static boolean playerVerifying(Player p) {
        return solutions.get(p) != null;
    }

    public static Type getType(Material mat) {
        for (Material m : foods) {
            if (m != mat) continue;
            return Type.FOOD;
        }
        for (Material m : weapons) {
            if (m != mat) continue;
            return Type.WEAPON;
        }
        for (Material m : lights) {
            if (m != mat) continue;
            return Type.LIGHT;
        }
        for (Material m : explosives) {
            if (m != mat) continue;
            return Type.EXPLOSIVE;
        }
        for (Material m : woods) {
            if (m != mat) continue;
            return Type.WOOD;
        }
        for (Material m : ores) {
            if (m != mat) continue;
            return Type.ORE;
        }
        for (Material m : potions) {
            if (m != mat) continue;
            return Type.POTION;
        }
        for (Material m : wools) {
            if (m != mat) continue;
            return Type.WOOL;
        }
        for (Material m : glasses) {
            if (m != mat) continue;
            return Type.GLASS;
        }
        return null;
    }

    public static Type getSolution(Player p) {
        Type solution = solutions.get(p);
        solutions.remove(p);
        return solution;
    }

    private static String getTitle(Type type) {
        String template = "Click on the {name}";
        if (type == Type.FOOD) {
            return template.replace("{name}", "food");
        }
        if (type == Type.WEAPON) {
            return template.replace("{name}", "weapon");
        }
        if (type == Type.LIGHT) {
            return template.replace("{name}", "light source");
        }
        if (type == Type.EXPLOSIVE) {
            return template.replace("{name}", "explosive");
        }
        if (type == Type.WOOD) {
            return template.replace("{name}", "wood");
        }
        if (type == Type.ORE) {
            return template.replace("{name}", "ore");
        }
        if (type == Type.POTION) {
            return template.replace("{name}", "potion");
        }
        if (type == Type.WOOL) {
            return template.replace("{name}", "wool");
        }
        if (type == Type.GLASS) {
            return template.replace("{name}", "glass");
        }
        return "Error generating CAPTCHA!";
    }

    private static Type[] Shuffle(Type[] array) {
        Random rgen = new Random();
        for (int i = 0; i < array.length; ++i) {
            int randomPosition = rgen.nextInt(array.length);
            Type temp = array[i];
            array[i] = array[randomPosition];
            array[randomPosition] = temp;
        }
        return array;
    }

    public static enum Type {
        FOOD,
        WEAPON,
        LIGHT,
        EXPLOSIVE,
        WOOD,
        ORE,
        POTION,
        WOOL,
        GLASS;
        
    }

}

