// https://gitlab.com/kody-simpson/spigot/custom-colors/
package me.sisko.left4chat.util;

import net.md_5.bungee.api.ChatColor;

public class Colors {
	static public final String WITH_DELIMITER = "((?<=%1$s)|(?=%1$s))";

	public static String format(String text) {

		String[] texts = text.split(String.format(WITH_DELIMITER, "&"));

		StringBuilder finalText = new StringBuilder();

		for (int i = 0; i < texts.length; i++) {
			if (texts[i].equalsIgnoreCase("&")) {
				i++;

				if (texts[i].charAt(0) == '#') {
					finalText.append(ChatColor.of(texts[i].substring(0, 7)) + texts[i].substring(7));
				} else {
					finalText.append(ChatColor.translateAlternateColorCodes('&', "&" + texts[i]));
				}

			} else {
				finalText.append(texts[i]);
			}
		}

		return finalText.toString();
	}

	public static String formatWithPerm(boolean formatPerm, boolean colorPerm, String message) {

		if (!formatPerm) {
			if (colorPerm) {
				String[] formats = { "&l", "&k", "&m", "&n", "&o" };
				for (String format : formats) {
					while (message.contains(format)) {
						message = message.replace(format, "");
					}
				}
			} else {
				return ChatColor.stripColor(format(message));
			}
		}
		
		return format(message);
	}

	public static String strip(String text) {
		return ChatColor.stripColor(format(text));
	}

}