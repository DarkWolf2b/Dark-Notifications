package dark.noti.client.features.modules.notifications;

import dark.noti.client.features.modules.client.FakePlayerModule;
import dark.noti.client.features.settings.BoolSetting;
import dark.noti.client.manager.Category;
import dark.noti.client.manager.Module;
import dark.noti.client.features.settings.ColorSetting;
import dark.noti.client.features.settings.SectionSetting;
import dark.noti.client.features.settings.StringSetting;
import dark.noti.client.util.ChatNotify;
import dark.noti.client.util.SocialLists;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class LogoutNotifierModule extends Module {
	private static final int DEFAULT_PREFIX = 0xFFB57BEA;

	private final BoolSetting players = add(new BoolSetting("Players", true));
	private final BoolSetting friends = add(new BoolSetting("Friends", true));
	private final SectionSetting targetsSection = add(new SectionSetting("Targets", false));

	private final SectionSetting generalSection = add(new SectionSetting("General", false));
	private final BoolSetting showCoords = add(new BoolSetting("ShowCoords", true));
	private final BoolSetting notifyLogin = add(new BoolSetting("NotifyLogin", true));
	/** Include logout-spot coords when they reappear in render. */
	private final BoolSetting remindCoords = add(new BoolSetting("RemindCoords", true));

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
	private final ColorSetting actionColor = add(new ColorSetting("ActionColor", DEFAULT_PREFIX));
	private final ColorSetting friendActionColor = add(ColorSetting.forFriend("FriendActionColor"));
	private final ColorSetting coordColor = add(new ColorSetting("CoordColor", 0xFFFFFFFF));
	private final BoolSetting showDot = add(new BoolSetting("ShowDot", true));

	private final Map<UUID, TrackedPlayer> tracked = new HashMap<>();
	/** Logout spots — kept until they reappear in render distance. */
	private final Map<UUID, LogoutSpot> logoutSpots = new HashMap<>();

	public LogoutNotifierModule() {
		super("LogoutNotifier", Category.NOTIFICATIONS);

		targetsSection.addSetting(players);
		targetsSection.addSetting(friends);

		generalSection.addSetting(showCoords);
		generalSection.addSetting(notifyLogin);
		generalSection.addSetting(remindCoords);

		prefixSection.addSetting(showPrefix);
		prefixSection.addSetting(clientPrefix);
		prefixSection.addSetting(prefixColor);
		prefixSection.addSetting(prefixBracketColor);

		iconSection.addSetting(showIcon);
		iconSection.addSetting(iconColor);
		iconSection.addSetting(bracketsColor);

		textSection.addSetting(nameColor);
		textSection.addSetting(friendColor);
		textSection.addSetting(actionColor);
		textSection.addSetting(friendActionColor);
		textSection.addSetting(coordColor);
		textSection.addSetting(showDot);
	}

	@Override
	public void onDisable() {
		tracked.clear();
		logoutSpots.clear();
	}

	@Override
	public void onTick() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer self = client.player;
		if (self == null || client.level == null) {
			tracked.clear();
			return;
		}

		Map<UUID, TrackedPlayer> current = new HashMap<>();
		for (Player other : client.level.players()) {
			if (other == self) {
				continue;
			}
			String name = other.getName().getString();
			if (FakePlayerModule.isFakePlayerName(name)) {
				continue;
			}
			current.put(other.getUUID(), new TrackedPlayer(name, other.getX(), other.getY(), other.getZ()));
			SocialLists.observe(other.getUUID(), name);
		}

		Iterator<Map.Entry<UUID, TrackedPlayer>> it = tracked.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, TrackedPlayer> entry = it.next();
			if (!current.containsKey(entry.getKey())) {
				TrackedPlayer player = entry.getValue();
				if (allows(player.name, entry.getKey())) {
					sendLogout(player, entry.getKey());
					logoutSpots.put(entry.getKey(), new LogoutSpot(player.name, player.x, player.y, player.z));
				}
				it.remove();
			}
		}

		if (notifyLogin.getValue()) {
			for (Map.Entry<UUID, TrackedPlayer> entry : current.entrySet()) {
				LogoutSpot spot = logoutSpots.remove(entry.getKey());
				if (spot != null && allows(spot.name, entry.getKey())) {
					sendLogin(spot, entry.getKey());
				}
			}
		} else {
			for (UUID uuid : current.keySet()) {
				logoutSpots.remove(uuid);
			}
		}

		tracked.clear();
		tracked.putAll(current);
	}

	private boolean allows(String name, UUID uuid) {
		if (uuid != null && SocialLists.isFriend(uuid)) {
			return friends.getValue();
		}
		if (SocialLists.isFriend(name)) {
			return friends.getValue();
		}
		return players.getValue();
	}

	private boolean isFriend(String name, UUID uuid) {
		return (uuid != null && SocialLists.isFriend(uuid)) || SocialLists.isFriend(name);
	}

	private void sendLogout(TrackedPlayer player, UUID uuid) {
		sendMessage(player.name, uuid, "logged out", showCoords.getValue(), player.x, player.y, player.z);
	}

	private void sendLogin(LogoutSpot spot, UUID uuid) {
		sendMessage(spot.name, uuid, "logged back in", remindCoords.getValue(), spot.x, spot.y, spot.z);
	}

	private void sendMessage(String name, UUID uuid, String action, boolean withCoords, double x, double y, double z) {
		boolean friend = isFriend(name, uuid);
		int nameRgb = friend ? friendColor.argb() : nameColor.argb();
		int actionRgb = friend ? friendActionColor.argb() : actionColor.argb();

		MutableComponent message = ChatNotify.start();
		message = ChatNotify.appendPrefix(message, showPrefix.getValue(), clientPrefix.get(), prefixColor.argb(), prefixBracketColor.argb());
		message = ChatNotify.appendIcon(message, showIcon.getValue(), bracketsColor.argb(), iconColor.argb());

		message = ChatNotify.appendColored(message, name, nameRgb);
		message = ChatNotify.appendColored(message, " " + action, actionRgb);
		if (withCoords) {
			message = ChatNotify.appendColored(message, " at ", actionRgb);
			String coords = (int) Math.floor(x) + ", "
				+ (int) Math.floor(y) + ", "
				+ (int) Math.floor(z);
			message = ChatNotify.appendColored(message, coords, coordColor.argb());
		}
		if (showDot.getValue()) {
			message = ChatNotify.appendColored(message, ".", actionRgb);
		}
		ChatNotify.send(message);
	}

	private static final class TrackedPlayer {
		final String name;
		final double x;
		final double y;
		final double z;

		TrackedPlayer(String name, double x, double y, double z) {
			this.name = name;
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}

	private static final class LogoutSpot {
		final String name;
		final double x;
		final double y;
		final double z;

		LogoutSpot(String name, double x, double y, double z) {
			this.name = name;
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}
}
