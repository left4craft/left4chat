package me.sisko.left4chat.text;

import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Parses the ampersand markup Left4Craft chat is written in.
 *
 * <p>The Adventure rewrite of the old {@code me.sisko.left4chat.util.Colors},
 * which built {@code net.md_5.bungee} components. The grammar is unchanged so
 * that nicknames, LuckPerms prefixes and anything the Discord bot publishes keep
 * rendering the way they do today:
 *
 * <ul>
 *   <li>{@code &0}-{@code &9}, {@code &a}-{@code &f} -- a colour, which also
 *       clears every active decoration.</li>
 *   <li>{@code &k} {@code &l} {@code &m} {@code &n} {@code &o} -- obfuscated,
 *       bold, strikethrough, underlined, italic. These accumulate and survive
 *       until a colour or {@code &r} clears them.</li>
 *   <li>{@code &r} -- back to inheriting from the surrounding text.</li>
 *   <li>{@code &#RRGGBB} -- a hex colour, also clearing decorations. An
 *       unparseable hex code is rendered as literal text.</li>
 *   <li>{@code &!rainbow<text>} -- per-character hue sweep across the rest of
 *       the segment.</li>
 *   <li>{@code &&} -- a literal ampersand.</li>
 *   <li>Anything else after {@code &} keeps its ampersand and is shown as
 *       written.</li>
 * </ul>
 */
public final class AmpersandColors {

    /** Split around every {@code &} while keeping the delimiters. */
    private static final String WITH_DELIMITER = "((?<=%1$s)|(?=%1$s))";

    /**
     * The code characters the old bungee {@code ChatColor.ALL_CODES} accepted.
     * {@code x} is in the list because bungee used it as its hex marker; it maps
     * to no colour, so {@code &x} reads as a reset. Preserved deliberately.
     */
    private static final String ALL_CODES = "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx";

    private static final String RAINBOW_TAG = "!rainbow";

    private AmpersandColors() {
    }

    /**
     * Renders ampersand markup to a component.
     *
     * @param text the markup
     * @return the rendered component
     */
    public static Component format(String text) {
        String[] parts = text.split(String.format(WITH_DELIMITER, "&"));
        List<Component> out = new ArrayList<>(parts.length);

        Style style = new Style();

        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equals("&") && i + 1 < parts.length) {
                String segment = parts[++i];

                if (segment.charAt(0) == '&') {
                    // "&&" -- an escaped ampersand.
                    out.add(style.apply(Component.text(segment)));
                } else if (segment.startsWith(RAINBOW_TAG)) {
                    out.add(rainbow(segment.substring(RAINBOW_TAG.length())));
                } else if (segment.length() >= 7 && segment.charAt(0) == '#') {
                    TextColor hex = TextColor.fromHexString(segment.substring(0, 7));
                    if (hex == null) {
                        out.add(style.apply(Component.text("&" + segment)));
                    } else {
                        style.reset();
                        style.color = hex;
                        out.add(style.apply(Component.text(segment.substring(7))));
                    }
                } else if (ALL_CODES.indexOf(segment.charAt(0)) >= 0) {
                    style.applyCode(segment.charAt(0));
                    out.add(style.apply(Component.text(segment.substring(1))));
                } else {
                    out.add(style.apply(Component.text("&" + segment)));
                }
            } else {
                out.add(style.apply(Component.text(parts[i])));
            }
        }

        return Component.textOfChildren(out.toArray(Component[]::new));
    }

    /**
     * Renders ampersand markup with the sender's chat permissions applied.
     *
     * <p>Corrects a real hole in the old implementation. It handled
     * "colour but no formatting" by deleting {@code &k}-{@code &o} from the
     * string, but handled "neither" by parsing the markup in full and clearing
     * the colour on the <em>root</em> component only -- every coloured child
     * kept its colour. Anyone without {@code left4chat.color} could still
     * colour their messages. Permissions are now enforced on the whole tree.
     *
     * @param formatPerm whether the sender holds {@code left4chat.format}
     * @param colorPerm  whether the sender holds {@code left4chat.color}
     * @param message    the raw message
     * @return the rendered message
     */
    public static Component formatWithPerm(boolean formatPerm, boolean colorPerm, String message) {
        Component formatted = format(message);

        if (formatPerm && colorPerm) {
            return formatted;
        }
        return restrict(formatted, formatPerm, colorPerm);
    }

    private static Component restrict(Component component, boolean formatPerm, boolean colorPerm) {
        Component result = component;

        if (!colorPerm) {
            result = result.color(null);
        }
        if (!formatPerm) {
            result = result.decoration(TextDecoration.OBFUSCATED, TextDecoration.State.FALSE)
                    .decoration(TextDecoration.BOLD, TextDecoration.State.FALSE)
                    .decoration(TextDecoration.STRIKETHROUGH, TextDecoration.State.FALSE)
                    .decoration(TextDecoration.UNDERLINED, TextDecoration.State.FALSE)
                    .decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE);
        }

        List<Component> children = result.children();
        if (children.isEmpty()) {
            return result;
        }

        List<Component> restricted = new ArrayList<>(children.size());
        for (Component child : children) {
            restricted.add(restrict(child, formatPerm, colorPerm));
        }
        return result.children(restricted);
    }

    /**
     * Renders ampersand markup and throws the formatting away.
     *
     * @param text the markup
     * @return the visible characters only
     */
    public static String strip(String text) {
        return PlainTextComponentSerializer.plainText().serialize(format(text));
    }

    /**
     * Strips the formatting off an already-rendered component.
     *
     * @param component the component
     * @return the visible characters only
     */
    public static String strip(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /**
     * Sweeps the hue across a run of text, one step per character.
     *
     * @param text the text to colour
     * @return the coloured component
     */
    public static Component rainbow(String text) {
        int length = text.length();
        if (length == 0) {
            return Component.empty();
        }

        List<Component> letters = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            // Matches the old java.awt.Color.getHSBColor(i/len, 0.95f, 0.85f).
            int rgb = java.awt.Color.HSBtoRGB((float) i / length, 0.95f, 0.85f) & 0xFFFFFF;
            letters.add(Component.text(text.charAt(i), TextColor.color(rgb)));
        }
        return Component.textOfChildren(letters.toArray(Component[]::new));
    }

    /** The formatting carried from one segment to the next. */
    private static final class Style {
        private TextColor color;
        private boolean obfuscated;
        private boolean bold;
        private boolean strikethrough;
        private boolean underlined;
        private boolean italic;

        void reset() {
            obfuscated = false;
            bold = false;
            strikethrough = false;
            underlined = false;
            italic = false;
        }

        void applyCode(char code) {
            switch (Character.toLowerCase(code)) {
                case 'k' -> obfuscated = true;
                case 'l' -> bold = true;
                case 'm' -> strikethrough = true;
                case 'n' -> underlined = true;
                case 'o' -> italic = true;
                case 'r' -> {
                    color = null;
                    reset();
                }
                default -> {
                    color = legacyColor(Character.toLowerCase(code));
                    reset();
                }
            }
        }

        TextComponent apply(TextComponent component) {
            return component.color(color)
                    .decoration(TextDecoration.OBFUSCATED, obfuscated)
                    .decoration(TextDecoration.BOLD, bold)
                    .decoration(TextDecoration.STRIKETHROUGH, strikethrough)
                    .decoration(TextDecoration.UNDERLINED, underlined)
                    .decoration(TextDecoration.ITALIC, italic);
        }
    }

    private static TextColor legacyColor(char code) {
        return switch (code) {
            case '0' -> NamedTextColor.BLACK;
            case '1' -> NamedTextColor.DARK_BLUE;
            case '2' -> NamedTextColor.DARK_GREEN;
            case '3' -> NamedTextColor.DARK_AQUA;
            case '4' -> NamedTextColor.DARK_RED;
            case '5' -> NamedTextColor.DARK_PURPLE;
            case '6' -> NamedTextColor.GOLD;
            case '7' -> NamedTextColor.GRAY;
            case '8' -> NamedTextColor.DARK_GRAY;
            case '9' -> NamedTextColor.BLUE;
            case 'a' -> NamedTextColor.GREEN;
            case 'b' -> NamedTextColor.AQUA;
            case 'c' -> NamedTextColor.RED;
            case 'd' -> NamedTextColor.LIGHT_PURPLE;
            case 'e' -> NamedTextColor.YELLOW;
            case 'f' -> NamedTextColor.WHITE;
            // 'x' -- bungee's hex marker, which resolved to no colour at all.
            default -> null;
        };
    }
}
