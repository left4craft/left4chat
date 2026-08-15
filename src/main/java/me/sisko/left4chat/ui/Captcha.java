package me.sisko.left4chat.ui;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import me.sisko.left4chat.Left4Chat;
import me.sisko.left4chat.text.AmpersandColors;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * The {@code /verify} captcha: nine items, click the one matching the prompt.
 *
 * <p>Behaviour is the original's. The plumbing is not: the old version kept its
 * pending solutions in a {@code HashMap<Player, Type>} that was never cleared on
 * quit, so every player who ever ran {@code /verify} and disconnected without
 * solving it stayed pinned in memory for the life of the server. It also
 * identified its own inventory by not identifying it at all -- any inventory
 * click by a verifying player was treated as an answer, including clicks in
 * their own inventory, which made the captcha trivially failable by accident.
 */
public final class Captcha implements Listener {

    /** The categories a player can be asked to pick out. */
    public enum Category {
        FOOD("food"),
        WEAPON("weapon"),
        LIGHT("light source"),
        EXPLOSIVE("explosive"),
        WOOD("wood"),
        ORE("ore"),
        POTION("potion"),
        WOOL("wool"),
        GLASS("glass");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        /**
         * The human-readable name used in the prompt.
         *
         * @return the label
         */
        public String label() {
            return label;
        }
    }

    private static final Map<Category, List<Material>> ITEMS = new EnumMap<>(Category.class);

    static {
        ITEMS.put(Category.FOOD, List.of(Material.COOKED_BEEF, Material.COOKED_CHICKEN,
                Material.COOKED_PORKCHOP, Material.CARROT, Material.BAKED_POTATO));
        ITEMS.put(Category.WEAPON, List.of(Material.WOODEN_SWORD, Material.STONE_SWORD,
                Material.IRON_SWORD, Material.GOLDEN_SWORD, Material.DIAMOND_SWORD));
        ITEMS.put(Category.LIGHT, List.of(Material.GLOWSTONE, Material.TORCH));
        ITEMS.put(Category.EXPLOSIVE, List.of(Material.GUNPOWDER, Material.TNT, Material.TNT_MINECART));
        ITEMS.put(Category.WOOD, List.of(Material.ACACIA_PLANKS, Material.DARK_OAK_PLANKS,
                Material.OAK_LOG, Material.SPRUCE_PLANKS, Material.BIRCH_PLANKS));
        ITEMS.put(Category.ORE, List.of(Material.COAL_ORE, Material.IRON_ORE, Material.GOLD_ORE,
                Material.DIAMOND_ORE, Material.EMERALD_ORE, Material.LAPIS_ORE));
        ITEMS.put(Category.POTION, List.of(Material.POTION, Material.SPLASH_POTION));
        ITEMS.put(Category.WOOL, List.of(Material.BLACK_WOOL, Material.RED_WOOL, Material.YELLOW_WOOL,
                Material.BLUE_WOOL, Material.PURPLE_WOOL));
        ITEMS.put(Category.GLASS, List.of(Material.BLACK_STAINED_GLASS, Material.ORANGE_STAINED_GLASS,
                Material.GREEN_STAINED_GLASS, Material.LIGHT_BLUE_STAINED_GLASS));
    }

    /** Marks an inventory as a captcha, and carries the answer. */
    private record Holder(Category solution) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            throw new UnsupportedOperationException();
        }
    }

    private final Left4Chat plugin;

    /** Players with a captcha open. Cleared on solve, fail, close and quit. */
    private final Map<UUID, Category> pending = new ConcurrentHashMap<>();

    public Captcha(Left4Chat plugin) {
        this.plugin = plugin;
    }

    /**
     * Whether a player is currently being asked to solve a captcha.
     *
     * @param player the player
     * @return whether a captcha is open
     */
    public boolean isSolving(Player player) {
        return pending.containsKey(player.getUniqueId());
    }

    /**
     * Opens a captcha. Must run on the main thread.
     *
     * @param player the player
     */
    public void open(Player player) {
        Category[] categories = Category.values();
        Category solution = categories[ThreadLocalRandom.current().nextInt(categories.length)];

        pending.put(player.getUniqueId(), solution);

        Inventory inventory = plugin.getServer().createInventory(new Holder(solution), 9,
                Component.text("Click on the " + solution.label()));

        List<Category> shuffled = new ArrayList<>(List.of(categories));
        java.util.Collections.shuffle(shuffled, ThreadLocalRandom.current());

        for (int slot = 0; slot < shuffled.size() && slot < 9; slot++) {
            inventory.setItem(slot, ItemStack.of(randomItem(shuffled.get(slot))));
        }

        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder(Category solution))) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        // Only clicks in the captcha itself count as an answer.
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder() instanceof Holder)) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        pending.remove(player.getUniqueId());
        player.closeInventory();

        if (clicked != null && categoryOf(clicked.getType()) == solution) {
            solved(player);
        } else {
            failed(player);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        HumanEntity closer = event.getPlayer();
        if (!(event.getInventory().getHolder() instanceof Holder)) {
            return;
        }
        if (pending.remove(closer.getUniqueId()) == null) {
            // Already resolved by the click handler.
            return;
        }

        closer.sendMessage(AmpersandColors.format(plugin.config().messages().captchaClosed()));
        kick(closer.getName(), "Incorrect CAPTCHA solution");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    private void solved(Player player) {
        player.sendMessage(AmpersandColors.format(plugin.config().messages().verified()));
        plugin.grantVerified(player);
    }

    private void failed(Player player) {
        player.sendMessage(AmpersandColors.format(plugin.config().messages().captchaFailed()));
        kick(player.getName(), "Incorrect CAPTCHA solution");
    }

    private void kick(String username, String reason) {
        String channel = plugin.config().keys()
                .consoleChannel(plugin.config().chat().lockdownServer());
        plugin.redis().publish(channel, "kick " + username + " " + reason);
    }

    private static Material randomItem(Category category) {
        List<Material> options = ITEMS.get(category);
        return options.get(ThreadLocalRandom.current().nextInt(options.size()));
    }

    private static Category categoryOf(Material material) {
        for (Map.Entry<Category, List<Material>> entry : ITEMS.entrySet()) {
            if (entry.getValue().contains(material)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
