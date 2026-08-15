package me.sisko.left4chat.command;

import java.util.Map;
import java.util.OptionalLong;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.sisko.left4chat.Left4Chat;
import me.sisko.left4chat.Left4ChatConfig;
import me.sisko.left4chat.text.AmpersandColors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * {@code /discord [code]} -- links a Minecraft account to a Discord account.
 *
 * <p>The bot writes pending codes to a Redis key; redeeming one claims it,
 * writes the link to Postgres and tells the bot to apply the player's nickname
 * and rank.
 */
public final class DiscordCommand extends PluginCommand {

    public DiscordCommand(Left4Chat plugin) {
        super(plugin);
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        if (!(source.getSender() instanceof Player player)) {
            error(source.getSender(), "You can't use that command from console!");
            return;
        }

        Left4ChatConfig config = plugin.config();

        if (args.length == 0) {
            player.sendMessage(AmpersandColors.format(config.messages().discordInvite()));
            return;
        }

        String code = args[0];
        async(() -> redeem(player, code));
    }

    private void redeem(Player player, String code) {
        Left4ChatConfig config = plugin.config();

        String raw = plugin.redis().get(config.keys().syncCodesKey());
        if (raw == null) {
            player.sendMessage(Component.text(
                    "Failed to connect to the discord bot.", NamedTextColor.RED));
            return;
        }

        JsonObject codes;
        try {
            codes = raw.isBlank()
                    ? new JsonObject()
                    : JsonParser.parseString(raw).getAsJsonObject();
        } catch (Exception e) {
            plugin.getLogger().warning("Could not parse " + config.keys().syncCodesKey() + ": " + raw);
            player.sendMessage(Component.text(
                    "Failed to connect to the discord bot.", NamedTextColor.RED));
            return;
        }

        String discordId = null;
        for (Map.Entry<String, JsonElement> entry : codes.entrySet()) {
            JsonObject pending = entry.getValue().getAsJsonObject();
            if (pending.has("code") && pending.get("code").getAsString().equals(code)) {
                discordId = entry.getKey();
                break;
            }
        }

        if (discordId == null) {
            player.sendMessage(Component.text("Invalid code.", NamedTextColor.RED));
            return;
        }

        long snowflake;
        try {
            snowflake = Long.parseLong(discordId);
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Sync code held a non-numeric Discord id: " + discordId);
            player.sendMessage(Component.text("Invalid code.", NamedTextColor.RED));
            return;
        }

        // Claim the code so it cannot be redeemed twice.
        codes.remove(discordId);
        plugin.redis().set(config.keys().syncCodesKey(), codes.toString());

        String nick = plugin.nicknames().plain(player.getUniqueId(), player.getName());
        OptionalLong previous = plugin.discordLinks().link(player.getUniqueId(), nick, snowflake);

        // Tell the bot to demote whoever held this link before, if anyone.
        if (previous.isPresent() && previous.getAsLong() != snowflake) {
            JsonObject unlink = new JsonObject();
            unlink.addProperty("command", "unlink");
            unlink.addProperty("oldId", String.valueOf(previous.getAsLong()));
            unlink.addProperty("newId", discordId);
            plugin.redis().publish(config.keys().botCommandChannel(), unlink.toString());
        }

        player.sendMessage(Component.text(
                "Successfully synced to discord account " + discordId, NamedTextColor.GREEN));

        plugin.syncDiscordProfile(player);
    }
}
