package dark.noti.client.util;

import dark.noti.client.mixin.ChatComponentAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ChatNotify {
	private static final Map<String, GuiMessage> STACK_MESSAGES = new ConcurrentHashMap<>();

	private ChatNotify() {
	}

	public static MutableComponent start() {
		return Component.empty();
	}

	public static MutableComponent appendPrefix(MutableComponent message, boolean show, String prefix, int prefixArgb) {
		return appendPrefix(message, show, prefix, prefixArgb, prefixArgb, true);
	}

	public static MutableComponent appendPrefix(
		MutableComponent message,
		boolean show,
		String prefix,
		int nameArgb,
		int bracketsArgb
	) {
		return appendPrefix(message, show, prefix, nameArgb, bracketsArgb, true);
	}

	public static MutableComponent appendPrefix(
		MutableComponent message,
		boolean show,
		String prefix,
		int nameArgb,
		int bracketsArgb,
		boolean showBrackets
	) {
		if (!show) {
			return message;
		}
		String name = barePrefix(prefix);
		if (name.isEmpty()) {
			return message;
		}
		TextColor nameColor = TextColor.fromRgb(nameArgb & 0xFFFFFF);
		TextColor brackets = TextColor.fromRgb(bracketsArgb & 0xFFFFFF);
		if (showBrackets) {
			message = message
				.append(Component.literal("[").withStyle(style -> style.withColor(brackets)))
				.append(Component.literal(name).withStyle(style -> style.withColor(nameColor)))
				.append(Component.literal("]").withStyle(style -> style.withColor(brackets)));
		} else {
			message = message.append(Component.literal(name).withStyle(style -> style.withColor(nameColor)));
		}
		return message.append(Component.literal(" "));
	}

	public static MutableComponent appendPrefix(MutableComponent message, String prefix, int prefixArgb) {
		return appendPrefix(message, true, prefix, prefixArgb, prefixArgb, true);
	}

	public static String barePrefix(String prefix) {
		if (prefix == null) {
			return "";
		}
		String name = prefix.trim();
		while (name.startsWith("[")) {
			name = name.substring(1).trim();
		}
		while (name.endsWith("]")) {
			name = name.substring(0, name.length() - 1).trim();
		}
		return name;
	}

	public static MutableComponent appendIcon(MutableComponent message, boolean show, int bracketsArgb, int iconArgb) {
		if (!show) {
			return message;
		}
		TextColor brackets = TextColor.fromRgb(bracketsArgb & 0xFFFFFF);
		TextColor icon = TextColor.fromRgb(iconArgb & 0xFFFFFF);
		return message
			.append(Component.literal("[").withStyle(style -> style.withColor(brackets)))
			.append(Component.literal("!").withStyle(style -> style.withColor(icon)))
			.append(Component.literal("]").withStyle(style -> style.withColor(brackets)))
			.append(Component.literal(" "));
	}

	public static MutableComponent appendBracketIcon(MutableComponent message, boolean show, String symbol, int argb) {
		if (!show) {
			return message;
		}
		TextColor color = TextColor.fromRgb(argb & 0xFFFFFF);
		return message
			.append(Component.literal("[").withStyle(style -> style.withColor(color)))
			.append(Component.literal(symbol).withStyle(style -> style.withColor(color)))
			.append(Component.literal("]").withStyle(style -> style.withColor(color)))
			.append(Component.literal(" "));
	}

	public static int bodyRgb(String textMode, int prefixArgb) {
		if ("Prefix".equalsIgnoreCase(textMode)) {
			return prefixArgb & 0xFFFFFF;
		}
		return 0xFFFFFF;
	}

	public static MutableComponent appendColored(MutableComponent message, String text, int rgb) {
		TextColor color = TextColor.fromRgb(rgb & 0xFFFFFF);
		return message.append(Component.literal(text).withStyle(style -> style.withColor(color)));
	}

	public static MutableComponent appendBody(MutableComponent message, String text, int rgb) {
		return appendColored(message, text, rgb);
	}

	public static void send(MutableComponent message) {
		Minecraft client = Minecraft.getInstance();
		if (client.gui != null) {
			client.gui.hud.getChat().addClientSystemMessage(message);
		}
	}

	public static void sendStacked(MutableComponent message, String stackKey, boolean replace) {
		Minecraft client = Minecraft.getInstance();
		if (client.gui == null) {
			return;
		}
		ChatComponent chat = client.gui.hud.getChat();
		ChatComponentAccessor accessor = (ChatComponentAccessor) chat;
		if (replace) {
			GuiMessage previous = STACK_MESSAGES.get(stackKey);
			if (previous != null) {
				removeMessage(accessor, previous);
			}
		}

		chat.addClientSystemMessage(message);
		List<GuiMessage> all = accessor.darkNoti$getAllMessages();
		if (!all.isEmpty()) {
			STACK_MESSAGES.put(stackKey, all.getFirst());
		}
	}

	public static void clearStack(String stackKey) {
		STACK_MESSAGES.remove(stackKey);
	}

	private static void removeMessage(ChatComponentAccessor accessor, GuiMessage previous) {
		Iterator<GuiMessage> it = accessor.darkNoti$getAllMessages().iterator();
		boolean removed = false;
		while (it.hasNext()) {
			if (it.next() == previous) {
				it.remove();
				removed = true;
				break;
			}
		}
		if (removed) {
			accessor.darkNoti$refreshTrimmedMessages();
		}
	}
}
