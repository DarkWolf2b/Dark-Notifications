package dark.noti.client.features.modules.notifications;

import dark.noti.client.manager.Category;
import dark.noti.client.manager.Module;
import dark.noti.client.manager.ModuleManager;
import dark.noti.client.features.settings.BoolSetting;
import dark.noti.client.features.settings.ColorSetting;
import dark.noti.client.features.settings.ModeSetting;
import dark.noti.client.features.settings.SectionSetting;
import dark.noti.client.util.SocialLists;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatMentionModule extends Module {
	private final BoolSetting colorFriends = add(new BoolSetting("ColorFriends", true));
	private final ColorSetting friendColor = add(ColorSetting.forFriend("FriendColor"));
	private final SectionSetting friendsSection = add(new SectionSetting("Friends", false, colorFriends));

	private final BoolSetting highlightMentions = add(new BoolSetting("HighlightMentions", true));
	private final ModeSetting mentionTarget = add(new ModeSetting("Highlight", "Name", "Name", "Message", "Both"));
	private final ColorSetting mentionColor = add(new ColorSetting("MentionColor", 0xFFFFAA00));
	private final BoolSetting closeMatch = add(new BoolSetting("CloseMatch", true));
	private final SectionSetting mentionSection = add(new SectionSetting("Mentions", false, highlightMentions));

	private final BoolSetting ownMessages = add(new BoolSetting("OwnMessages", true));
	private final BoolSetting bold = add(new BoolSetting("Bold", true));
	private final BoolSetting color = add(new BoolSetting("Color", true));
	private final ColorSetting ownColor = add(ColorSetting.forSelf("OwnColor"));
	private final SectionSetting ownSection = add(new SectionSetting("OwnMessages", false, ownMessages));

	public ChatMentionModule() {
		super("ChatMention", Category.NOTIFICATIONS);

		friendsSection.addSetting(friendColor);

		mentionSection.addSetting(mentionTarget);
		mentionSection.addSetting(mentionColor);
		mentionSection.addSetting(closeMatch);

		ownSection.addSetting(bold);
		ownSection.addSetting(color);
		ownSection.addSetting(ownColor);
	}

	public static Component process(Component original) {
		if (original == null) {
			return null;
		}
		ChatMentionModule module = ModuleManager.get().get(ChatMentionModule.class);
		if (module == null || !module.isEnabled()) {
			return original;
		}
		return module.restyle(original);
	}

	private Component restyle(Component original) {
		String plain = original.getString();
		if (plain.isEmpty()) {
			return original;
		}

		Minecraft client = Minecraft.getInstance();
		String selfName = client.player != null ? client.player.getName().getString() : "";

		ParsedChat parsed = parseChat(plain, selfName);
		boolean fromSelf = parsed.fromSelf;
		boolean fromFriend = parsed.sender != null && SocialLists.isFriend(parsed.sender);
		boolean mentionsSelf = !selfName.isEmpty() && mentions(plain, selfName);

		if (!fromSelf && !fromFriend && !mentionsSelf) {
			return original;
		}

		// Own chat: always restyle both the left name and the message body.
		if (fromSelf && ownMessages.getValue()) {
			return styleOwnMessage(parsed, plain);
		}

		if (parsed.sender != null && parsed.namePrefix != null) {
			MutableComponent out = Component.empty();
			out.append(styleName(parsed.namePrefix, parsed.sender, fromFriend, false, mentionsSelf));
			out.append(styleBody(parsed.body, false, mentionsSelf));
			return out;
		}

		if (fromFriend && colorFriends.getValue()) {
			return recolorContainingName(plain, parsed.sender, friendColor.argb());
		}
		if (mentionsSelf && highlightMentions.getValue()) {
			if (mentionTarget.is("Message") || mentionTarget.is("Both")) {
				return styleWhole(plain, false, true);
			}
			return highlightNameInText(plain, selfName);
		}
		return original;
	}

	private Component styleOwnMessage(ParsedChat parsed, String plain) {
		Style ownStyle = ownStyle();
		if (parsed.namePrefix != null) {
			MutableComponent out = Component.empty();
			out.append(Component.literal(parsed.namePrefix).setStyle(ownStyle));
			out.append(Component.literal(parsed.body).setStyle(ownStyle));
			return out;
		}
		return Component.literal(plain).setStyle(ownStyle);
	}

	private Style ownStyle() {
		Style style = Style.EMPTY;
		if (bold.getValue()) {
			style = style.withBold(true);
		}
		if (color.getValue()) {
			style = style.withColor(TextColor.fromRgb(ownColor.argb() & 0xFFFFFF));
		}
		return style;
	}

	private Component styleName(String namePrefix, String sender, boolean friend, boolean self, boolean mentioned) {
		MutableComponent name = Component.literal(namePrefix);
		if (self && ownMessages.getValue()) {
			return name.setStyle(ownStyle());
		}
		Style style = Style.EMPTY;
		if (friend && colorFriends.getValue()) {
			style = style.withColor(TextColor.fromRgb(friendColor.argb() & 0xFFFFFF));
		} else if (mentioned && highlightMentions.getValue()
			&& (mentionTarget.is("Name") || mentionTarget.is("Both"))) {
			style = style.withColor(TextColor.fromRgb(mentionColor.argb() & 0xFFFFFF));
		}
		return name.setStyle(style);
	}

	private Component styleBody(String body, boolean self, boolean mentioned) {
		if (self && ownMessages.getValue() && (bold.getValue() || color.getValue())) {
			return Component.literal(body).setStyle(ownStyle());
		}
		if (mentioned && highlightMentions.getValue()) {
			Minecraft client = Minecraft.getInstance();
			String selfName = client.player != null ? client.player.getName().getString() : "";
			if (mentionTarget.is("Message") || mentionTarget.is("Both")) {
				return Component.literal(body).withStyle(style ->
					style.withColor(TextColor.fromRgb(mentionColor.argb() & 0xFFFFFF)));
			}
			return highlightNameInText(body, selfName);
		}
		return Component.literal(body);
	}

	private Component styleWhole(String plain, boolean self, boolean mentioned) {
		if (self && ownMessages.getValue()) {
			return Component.literal(plain).setStyle(ownStyle());
		}
		Style style = Style.EMPTY;
		if (mentioned) {
			style = style.withColor(TextColor.fromRgb(mentionColor.argb() & 0xFFFFFF));
		}
		return Component.literal(plain).setStyle(style);
	}

	private Component recolorContainingName(String plain, String sender, int argb) {
		if (sender == null) {
			return Component.literal(plain);
		}
		int idx = plain.toLowerCase(Locale.ROOT).indexOf(sender.toLowerCase(Locale.ROOT));
		if (idx < 0) {
			return Component.literal(plain).withStyle(style ->
				style.withColor(TextColor.fromRgb(argb & 0xFFFFFF)));
		}
		MutableComponent out = Component.empty();
		out.append(Component.literal(plain.substring(0, idx)));
		out.append(Component.literal(plain.substring(idx, idx + sender.length()))
			.withStyle(style -> style.withColor(TextColor.fromRgb(argb & 0xFFFFFF))));
		out.append(Component.literal(plain.substring(idx + sender.length())));
		return out;
	}

	private Component highlightNameInText(String plain, String selfName) {
		if (selfName == null || selfName.isBlank()) {
			return Component.literal(plain);
		}
		Pattern pattern = Pattern.compile(Pattern.quote(selfName), Pattern.CASE_INSENSITIVE);
		Matcher matcher = pattern.matcher(plain);
		MutableComponent out = Component.empty();
		int last = 0;
		boolean found = false;
		while (matcher.find()) {
			found = true;
			if (matcher.start() > last) {
				out.append(Component.literal(plain.substring(last, matcher.start())));
			}
			out.append(Component.literal(plain.substring(matcher.start(), matcher.end()))
				.withStyle(style -> style.withColor(TextColor.fromRgb(mentionColor.argb() & 0xFFFFFF))));
			last = matcher.end();
		}
		if (!found) {
			return Component.literal(plain);
		}
		if (last < plain.length()) {
			out.append(Component.literal(plain.substring(last)));
		}
		return out;
	}

	private boolean mentions(String plain, String selfName) {
		if (!highlightMentions.getValue()) {
			return false;
		}
		if (plain.toLowerCase(Locale.ROOT).contains(selfName.toLowerCase(Locale.ROOT))) {
			return true;
		}
		return closeMatch.getValue() && SocialLists.mentionsName(plain, selfName);
	}

	private static ParsedChat parseChat(String plain, String selfName) {
		// <Name> message
		if (plain.startsWith("<")) {
			int end = plain.indexOf('>');
			if (end > 1) {
				String sender = plain.substring(1, end);
				String prefix = plain.substring(0, end + 1);
				String body = plain.substring(end + 1);
				boolean fromSelf = !selfName.isEmpty() && sender.equalsIgnoreCase(selfName);
				return new ParsedChat(sender, prefix, body, fromSelf);
			}
		}

		if (!selfName.isEmpty()) {
			String lower = plain.toLowerCase(Locale.ROOT);
			String self = selfName.toLowerCase(Locale.ROOT);
			for (String sep : new String[]{": ", " » ", " > ", " >> ", ":"}) {
				if (!lower.startsWith(self)) {
					break;
				}
				String after = plain.substring(selfName.length());
				String afterLower = after.toLowerCase(Locale.ROOT);
				if (afterLower.startsWith(sep) || (sep.equals(":") && afterLower.startsWith(":"))) {
					int cut = selfName.length();
					if (after.startsWith(sep)) {
						cut += sep.length();
					} else if (after.startsWith(":")) {
						cut += 1;
						if (after.length() > 1 && after.charAt(1) == ' ') {
							cut += 1;
						}
					} else {
						continue;
					}
					String prefix = plain.substring(0, Math.min(cut, plain.length()));
					String body = plain.substring(Math.min(cut, plain.length()));
					return new ParsedChat(selfName, prefix, body, true);
				}
			}
			if (lower.startsWith(self + " ")) {
				String prefix = plain.substring(0, selfName.length());
				String body = plain.substring(selfName.length());
				return new ParsedChat(selfName, prefix, body, true);
			}
		}

		return new ParsedChat(null, null, plain, false);
	}

	private record ParsedChat(String sender, String namePrefix, String body, boolean fromSelf) {
	}
}
