package me.sisko.left4chat.command;

import java.util.Optional;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.sisko.left4chat.Left4Chat;
import me.sisko.left4chat.Left4ChatConfig;
import me.sisko.left4chat.text.AmpersandColors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

/**
 * {@code /reply <message>} -- messages whoever you last spoke to.
 *
 * <p>The old version handed off by calling {@code player.chat("/msg ...")},
 * which round-tripped through the chat pipeline and would have been published to
 * the whole network had the chat listener not happened to run first. This calls
 * the message command directly.
 */
public final class ReplyCommand extends PluginCommand {

    private final MessageCommand message;

    public ReplyCommand(Left4Chat plugin, MessageCommand message) {
        super(plugin);
        this.message = message;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        if (!(source.getSender() instanceof Player player)) {
            error(source.getSender(), "You can't reply from console!");
            return;
        }

        Left4ChatConfig config = plugin.config();

        if (!player.hasPermission(config.chat().verifiedPermission())) {
            player.sendMessage(AmpersandColors.format(config.messages().verifyRequired()));
            return;
        }
        if (args.length < 1) {
            error(player, "Usage: /reply <message>");
            return;
        }

        String body = join(args, 0);

        async(() -> {
            Optional<String> recipient = plugin.playerDirectory().lastRecipient(player.getName());
            if (recipient.isEmpty()) {
                player.sendMessage(Component.text(
                        "There is nobody to whom you can reply.", NamedTextColor.RED));
                return;
            }

            String target = recipient.get();
            if (plugin.playerDirectory().byUsername(target).isEmpty()) {
                player.sendMessage(Component.text(target + " is currently offline.", NamedTextColor.RED));
                sync(() -> player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT,
                        SoundCategory.PLAYERS, 5.0f, 0.5f));
                return;
            }

            message.execute(source, prepend(target, body));
        });
    }

    private static String[] prepend(String recipient, String body) {
        String[] parts = body.split(" ");
        String[] args = new String[parts.length + 1];
        args[0] = recipient;
        System.arraycopy(parts, 0, args, 1, parts.length);
        return args;
    }
}
