// https://gitlab.com/kody-simpson/spigot/custom-colors/
package me.sisko.left4chat.util;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import java.awt.Color;

public class Colors {
	static public final String WITH_DELIMITER = "((?<=%1$s)|(?=%1$s))";

	public static TextComponent format(String text) {

		/*
		 * Split the string into a list of & symbols and the characters between them
		 * i.e. &8[Left&44&6O&ew&2n&3e&9r&5]&4 test becomes [&, 8[Left, &, 44, &, 6O, &,
		 * ew, &, 2n, &, 3e, &, 9r, &, 5], &, 4 test]
		 */

		String[] texts = text.split(String.format(WITH_DELIMITER, "&"));

		System.out.println(text);
		System.out.println(String.join(", ", texts));

		TextComponent finalText = new TextComponent();
		ChatColor lastColor = ChatColor.RESET;

		boolean obfuscated = false;
		boolean bold = false;
		boolean strikethrough = false;
		boolean underlined = false;
		boolean italic = false;

		for (int i = 0; i < texts.length; i++) {
			TextComponent txt = new TextComponent();

			// when encountering a & character that is followed by a string
			if (texts[i].equalsIgnoreCase("&") && i + 1 < texts.length) {
				// skip this & character
				i++;

				// if the next character is &, just add the string normally, since "&&" is
				// effectively an escape character
				if (texts[i].charAt(0) == '&') {
					txt.setText(texts[i]);
					txt.setColor(lastColor);


				} else if (texts[i].startsWith("!rainbow")) {

					txt.addExtra(rainbow(texts[i].substring(8)));

					// if the length is >= 7, and the next character is #, parse it as a hex color
				} else if (texts[i].length() >= 7 && texts[i].charAt(0) == '#') {
					lastColor = ChatColor.of(texts[i].substring(0, 7));
					txt.setColor(lastColor);
					txt.setText(texts[i].substring(7));

					// reset formatting
					obfuscated = false;
					bold = false;
					strikethrough = false;
					underlined = false;
					italic = false;

					// finalText.append(ChatColor.of(texts[i].substring(0, 7)) +
					// texts[i].substring(7));

					// by default, attempt to translate the legacy color code.
				} else {
					if (ChatColor.ALL_CODES.contains(texts[i].substring(0, 1))) { // if valid color code
						// format the text correctly, then append
						switch (texts[i].toLowerCase().charAt(0)) {
							case 'k':
								obfuscated = true;
								break;
							case 'l':
								bold = true;
								break;
							case 'm':
								strikethrough = true;
								break;
							case 'n':
								underlined = true;
								break;
							case 'o':
								italic = true;
								break;
							case 'r':
								lastColor = ChatColor.RESET;
								obfuscated = false;
								bold = false;
								strikethrough = false;
								underlined = false;
								italic = false;
								break;
							default:
								lastColor = ChatColor.getByChar(texts[i].charAt(0));
								obfuscated = false;
								bold = false;
								strikethrough = false;
								underlined = false;
								italic = false;
								break;
						}
						txt.setColor(lastColor);
						txt.setText(texts[i].substring(1));

						// if color code is invalid, add back the & symbol
					} else {
						txt.setColor(lastColor);
						txt.setText("&" + texts[i]);
					}
					// finalText.append(ChatColor.translateAlternateColorCodes('&', "&" +
					// texts[i]));
				}
				txt.setObfuscated(obfuscated);
				txt.setBold(bold);
				txt.setStrikethrough(strikethrough);
				txt.setUnderlined(underlined);
				txt.setItalic(italic);

				finalText.addExtra(txt);

				// if no & character, or & character is trailing
			} else {
				txt.setText(texts[i]);
				txt.setColor(lastColor);

				txt.setObfuscated(obfuscated);
				txt.setBold(bold);
				txt.setStrikethrough(strikethrough);
				txt.setUnderlined(underlined);
				txt.setItalic(italic);

				finalText.addExtra(txt);
			}
		}

		return finalText;
	}

	public static TextComponent formatWithPerm(boolean formatPerm, boolean colorPerm, String message) {

		if (!formatPerm) {
			if (colorPerm) {
				String[] formats = {
					"&l",
					"&k",
					"&m",
					"&n",
					"&o"
				};
				for (String format: formats) {
					while (message.contains(format)) {
						message = message.replace(format, "");
					}
				}
			} else {
				TextComponent formatted = format(message);
				formatted.setColor(ChatColor.RESET);
				return formatted;
			}
		}

		return format(message);
	}

	public static String strip(String text) {
		return format(text).toPlainText();
	}

	public static TextComponent rainbow(String text) {
		TextComponent txt = new TextComponent();
		String[] letters = text.split("");

		float theta = 0;
		double delta = 360 / text.length();
		for (int i = 0; i < text.length(); i++) {
			TextComponent letter = new TextComponent();
			letter.setColor(ChatColor.of(Color.getHSBColor(theta, 100, 50)));
			letter.setText(letters[i]);
			txt.addExtra(letter);
			theta += delta;
		}

		return txt;
	}

}