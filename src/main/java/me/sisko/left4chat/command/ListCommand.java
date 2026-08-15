package me.sisko.left4chat.command;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.sisko.left4chat.Left4Chat;
import me.sisko.left4chat.Left4ChatConfig;
import me.sisko.left4chat.presence.PlayerDirectory.NetworkPlayer;
import me.sisko.left4chat.text.AmpersandColors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

/**
 * {@code /list} -- everyone online across the network, staff first.
 *
 * <p>The old implementation was racy in a way that showed. It kicked off one
 * async LuckPerms lookup per player, appended each result to a shared
 * {@code TextComponent} from whichever thread finished first, and then printed
 * the whole thing from a task scheduled twenty ticks later -- so the list was
 * ordered arbitrarily, could be printed half-built, and its comma separators
 * were placed by an index check that produced the wrong punctuation whenever the
 * staff and non-staff lists were not the same shape. Its staff counter was
 * hardcoded to zero, so the "no staff online" line always showed.
 *
 * <p>This waits for every lookup, then renders once.
 */
public final class ListCommand extends PluginCommand {

    private static final String STAFF_PERMISSION = "left4craft.staff";

    public ListCommand(Left4Chat plugin) {
        super(plugin);
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();

        async(() -> {
            plugin.playerDirectory().refresh();
            List<NetworkPlayer> online = plugin.playerDirectory().online();

            List<CompletableFuture<Boolean>> staffChecks = new ArrayList<>(online.size());
            for (NetworkPlayer player : online) {
                staffChecks.add(plugin.permissions().hasPermission(player.uuid(), STAFF_PERMISSION));
            }

            CompletableFuture.allOf(staffChecks.toArray(CompletableFuture[]::new))
                    .whenComplete((ignored, error) -> {
                        if (error != null) {
                            plugin.getLogger().log(java.util.logging.Level.WARNING,
                                    "Could not resolve staff for /list", error);
                        }
                        render(sender, online, staffChecks);
                    });
        });
    }

    private void render(CommandSender sender, List<NetworkPlayer> online,
                        List<CompletableFuture<Boolean>> staffChecks) {
        Left4ChatConfig.Messages messages = plugin.config().messages();

        List<Component> staff = new ArrayList<>();
        List<Component> players = new ArrayList<>();

        for (int i = 0; i < online.size(); i++) {
            NetworkPlayer player = online.get(i);
            boolean isStaff = staffChecks.get(i).getNow(false);
            (isStaff ? staff : players).add(entry(player));
        }

        Component header = AmpersandColors.format(messages.listHeader()
                .replace("{count}", String.valueOf(online.size()))
                .replace("{players}", online.size() == 1 ? "player" : "players"));

        Component staffLine = Component.textOfChildren(
                AmpersandColors.format(messages.listStaff()),
                staff.isEmpty()
                        ? AmpersandColors.format(messages.listNoStaff())
                        : Component.join(JoinConfiguration.commas(true), staff));

        Component playerLine = Component.textOfChildren(
                AmpersandColors.format(messages.listPlayers()),
                Component.join(JoinConfiguration.commas(true), players));

        sender.sendMessage(Component.textOfChildren(
                header, Component.newline(),
                staffLine, Component.newline(),
                playerLine, Component.newline(), Component.newline(),
                AmpersandColors.format(messages.listFooter())));
    }

    private Component entry(NetworkPlayer player) {
        String nick = plugin.nicknames().displayName(player.uuid(), player.username());

        Component name = plugin.afk().isAway(player.uuid())
                ? AmpersandColors.format("&8[AFK]&#808080"
                        + AmpersandColors.strip(nick) + "&r")
                : AmpersandColors.format(nick + "&r");

        String server = player.server() == null ? "an unknown server" : player.server();
        return name.hoverEvent(HoverEvent.showText(Component.textOfChildren(
                Component.text(player.username(), NamedTextColor.GRAY),
                Component.text(" is in ", NamedTextColor.GRAY),
                Component.text(server, NamedTextColor.GREEN))));
    }
}
