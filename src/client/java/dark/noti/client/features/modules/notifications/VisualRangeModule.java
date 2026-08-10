package dark.noti.client.features.modules.notifications;

import dark.noti.client.features.modules.client.FakePlayerModule;
import dark.noti.client.features.settings.BoolSetting;
import dark.noti.client.manager.Category;
import dark.noti.client.manager.Module;
import dark.noti.client.features.settings.ColorSetting;
import dark.noti.client.features.settings.NumberSetting;
import dark.noti.client.features.settings.SectionSetting;
import dark.noti.client.features.settings.StringSetting;
import dark.noti.client.util.ChatNotify;
import dark.noti.client.util.SocialLists;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class VisualRangeModule extends Module {
	private static final int DEFAULT_PREFIX = 0xFFB57BEA;

	private final SectionSetting generalSection = add(new SectionSetting("General", false));
	private final BoolSetting join = add(new BoolSetting("Join", true));
	private final BoolSetting leave = add(new BoolSetting("Leave", true));
	private final BoolSetting friends = add(new BoolSetting("Friends", true));

	private final SectionSetting stackSection = add(new SectionSetting("Stacking", false));
	private final BoolSetting stack = add(new BoolSetting("Stack", false));
	private final NumberSetting stackDelay = add(new NumberSetting("Delay", 3.0, 0.5, 60.0, 0.5, false));

	private final SectionSetting prefixSection = add(new SectionSetting("Prefix", false));
	private final BoolSetting showPrefix = add(new BoolSetting("ShowPrefix", true));
	private final StringSetting clientPrefix = add(new StringSetting("ClientPrefix", "Dark", 16));
	private final ColorSetting prefixColor = add(new ColorSetting("PrefixColor", DEFAULT_PREFIX));
	private final ColorSetting prefixBracketColor = add(new ColorSetting("BracketColor", DEFAULT_PREFIX));

	private final SectionSetting iconSection = add(new SectionSetting("Icon", false));
	private final BoolSetting showIcon = add(new BoolSetting("ShowIcon", true));
	private final ColorSetting iconColor = add(new ColorSetting("IconColor", 0xFFFFAA00));
	private final ColorSetting bracketsColor = add(new ColorSetting("BracketsColor", 0xFFFFAA00));

	private final SectionSetting textSection = add(new SectionSetting("Colors", false));
	private final ColorSetting nameColor = add(new ColorSetting("NameColor", 0xFFFFFFFF));
	private final ColorSetting friendColor = add(ColorSetting.forFriend("FriendColor"));
	private final ColorSetting actionColor = add(new ColorSetting("ActionColor", 0xFFFFFFFF));
	private final ColorSetting friendActionColor = add(ColorSetting.forFriend("FriendActionColor"));
	private final BoolSetting showPeriod = add(new BoolSetting("ShowPeriod", true));
	private final ColorSetting periodColor = add(new ColorSetting("PeriodColor", 0xFFFFFFFF));

	private boolean ignoreFakePlayer = false;

	private final List<String> previousPlayers = new ArrayList<>();
	private final List<String> currentPlayers = new ArrayList<>();
	private final Map<String, Long> lastNotifyMs = new HashMap<>();

	public VisualRangeModule() {
		super("VisualRange", Category.NOTIFICATIONS);

		generalSection.addSetting(join);
		generalSection.addSetting(leave);
		generalSection.addSetting(friends);

		stackSection.addSetting(stack);
		stackSection.addSetting(stackDelay);

		prefixSection.addSetting(showPrefix);
		prefixSection.addSetting(clientPrefix);
		prefixSection.addSetting(prefixColor);
		prefixSection.addSetting(prefixBracketColor);

		iconSection.addSetting(showIcon);
		iconSection.addSetting(iconColor);
		iconSection.addSetting(bracketsColor);

		SectionSetting namesGroup = new SectionSetting("Names", false);
		namesGroup.addSetting(nameColor);
		namesGroup.addSetting(friendColor);
		SectionSetting actionsGroup = new SectionSetting("Actions", false);
		actionsGroup.addSetting(actionColor);
		actionsGroup.addSetting(friendActionColor);
		SectionSetting extrasGroup = new SectionSetting("Extras", false);
		extrasGroup.addSetting(showPeriod);
		extrasGroup.addSetting(periodColor);

		textSection.addSetting(namesGroup);
		textSection.addSetting(actionsGroup);
		textSection.addSetting(extrasGroup);
	}

	public void setIgnoreFakePlayer(boolean value) {
		this.ignoreFakePlayer = value;
	}

	public boolean getIgnoreFakePlayer() {
		return ignoreFakePlayer;
	}

	@Override
	public void onDisable() {
		previousPlayers.clear();
		currentPlayers.clear();
		lastNotifyMs.clear();
	}

	@Override
	public void onTick() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;

		if (player == null || client.level == null) {
			previousPlayers.clear();
			currentPlayers.clear();
			return;
		}

		currentPlayers.clear();

		for (net.minecraft.world.entity.player.Player otherPlayer : client.level.players()) {
			if (otherPlayer == player) {
				continue;
			}

			String name = otherPlayer.getName().getString();
			if (ignoreFakePlayer && FakePlayerModule.isFakePlayerName(name)) {
				continue;
			}

			currentPlayers.add(name);
		}

		if (join.getValue()) {
			for (String name : currentPlayers) {
				if (!previousPlayers.contains(name)) {
					sendMessage(name, true);
				}
			}
		}

		if (leave.getValue()) {
			for (String name : previousPlayers) {
				if (!currentPlayers.contains(name)) {
					sendMessage(name, false);
				}
			}
		}

		previousPlayers.clear();
		previousPlayers.addAll(currentPlayers);
	}

	private void sendMessage(String playerName, boolean entered) {
		long now = System.currentTimeMillis();
		boolean replace = false;
		String key = playerName.toLowerCase(Locale.ROOT);
		if (stack.getValue()) {
			Long last = lastNotifyMs.get(key);
			long delayMs = Math.round(stackDelay.get() * 1000.0);
			replace = last != null && (now - last) <= delayMs;
		}
		lastNotifyMs.put(key, now);

		MutableComponent message = ChatNotify.start();
		message = ChatNotify.appendPrefix(message, showPrefix.getValue(), clientPrefix.get(), prefixColor.argb(), prefixBracketColor.argb());
		message = ChatNotify.appendIcon(message, showIcon.getValue(), bracketsColor.argb(), iconColor.argb());

		boolean isFriend = SocialLists.isFriend(playerName);
		int nameRgb = isFriend ? friendColor.argb() : nameColor.argb();
		int actionRgb = isFriend ? friendActionColor.argb() : actionColor.argb();
		message = ChatNotify.appendColored(message, playerName, nameRgb);

		String action = entered ? " entered visual range" : " left visual range";
		message = ChatNotify.appendColored(message, action, actionRgb);
		if (showPeriod.getValue()) {
			message = ChatNotify.appendColored(message, ".", periodColor.argb());
		}

		if (stack.getValue()) {
			ChatNotify.sendStacked(message, stackKey(key), replace);
		} else {
			ChatNotify.send(message);
		}
	}

	private static String stackKey(String playerKey) {
		return "visualrange:" + playerKey;
	}
}
