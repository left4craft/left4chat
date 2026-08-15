package me.sisko.left4chat.integration;

import java.util.Optional;
import java.util.UUID;

import io.loyloy.nicky.api.NicknameService;
import org.bukkit.Server;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Nickname lookups, when Nicky is installed.
 *
 * <p>The old code called {@code Nicky.getNickDatabase().downloadNick(uuid)}
 * directly. That reached past Nicky's plugin class into its internal SQL layer,
 * and it <em>blocked on the database</em> -- including from
 * {@code MessageTabComplete}, which runs on the main thread every time someone
 * types a character after {@code /msg}. Left4Chat now goes through Nicky's
 * registered {@link NicknameService}, whose reads are served from cache.
 *
 * <p>Nicky is optional: with it absent, everyone is known by their username.
 */
public final class Nicknames {

    private final NicknameService service;

    private Nicknames(NicknameService service) {
        this.service = service;
    }

    /**
     * Looks up Nicky's service in the services manager.
     *
     * @param server the server
     * @return the wrapper, backed by Nicky if it is installed
     */
    public static Nicknames of(Server server) {
        RegisteredServiceProvider<NicknameService> registration =
                server.getServicesManager().getRegistration(NicknameService.class);
        return new Nicknames(registration == null ? null : registration.getProvider());
    }

    /**
     * Whether Nicky is present.
     *
     * @return true if nicknames are available
     */
    public boolean available() {
        return service != null;
    }

    /**
     * A player's nickname as ampersand markup, falling back to their username.
     *
     * @param uuid     the player
     * @param username the username to fall back to
     * @return the display name markup
     */
    public String displayName(UUID uuid, String username) {
        return raw(uuid).orElse(username);
    }

    /**
     * A player's nickname as ampersand markup.
     *
     * @param uuid the player
     * @return the markup, or empty if they have no nickname
     */
    public Optional<String> raw(UUID uuid) {
        return service == null ? Optional.empty() : service.rawNickname(uuid);
    }

    /**
     * A player's nickname with the formatting stripped.
     *
     * @param uuid     the player
     * @param username the username to fall back to
     * @return the plain display name
     */
    public String plain(UUID uuid, String username) {
        if (service == null) {
            return username;
        }
        return service.plainNickname(uuid).orElse(username);
    }
}
