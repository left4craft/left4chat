// https://gitlab.com/kody-simpson/spigot/custom-colors/
package me.sisko.left4chat.util;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;

public class Colors {
	static public final String WITH_DELIMITER = "((?<=%1$s)|(?=%1$s))";

	public static TextComponent format(String text) {

		String[] texts = text.split(String.format(WITH_DELIMITER, "&"));

		TextComponent finalText = new TextComponent();
		ChatColor lastColor = ChatColor.RESET;

		for (int i = 0; i < texts.length; i++) {
			TextComponent txt = new TextComponent();

			if (texts[i].equalsIgnoreCase("&")) {
				i++;

				if (texts[i].charAt(0) == '#') {
					lastColor = ChatColor.of(texts[i].substring(0, 7));
					txt.setColor(lastColor);
					txt.setText(texts[i].substring(7));

					//finalText.append(ChatColor.of(texts[i].substring(0, 7)) + texts[i].substring(7));
				} else {
					lastColor = ChatColor.getByChar(texts[i].charAt(0));
					txt.setColor(lastColor);
					txt.setText(texts[i].substring(1));
					//finalText.append(ChatColor.translateAlternateColorCodes('&', "&" + texts[i]));
				}
				finalText.addExtra(txt);
			} else {
				txt.setText(texts[i]);
				txt.setColor(lastColor);
				finalText.addExtra(txt);
			}
		}

		return finalText;
	}

	public static TextComponent formatWithPerm(boolean formatPerm, boolean colorPerm, String message) {

		if (!formatPerm) {
			if (colorPerm) {
				String[] formats = { "&l", "&k", "&m", "&n", "&o" };
				for (String format : formats) {
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
		return format(text).getText();
	}

}