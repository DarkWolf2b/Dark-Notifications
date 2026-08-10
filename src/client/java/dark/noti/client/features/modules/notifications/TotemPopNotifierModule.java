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
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TotemPopNotifierModule extends Module {
	public static TotemPopNotifierModule INSTANCE;

	private static final int DEFAULT_PREFIX = 0xFFB57BEA;

	private final SectionSetting targetsSection = add(new SectionSetting("Targets", false));
	private final BoolSetting players = add(new BoolSetting("Players", true));
	private final BoolSetting friends = add(new BoolSetting("Friends", false));
	private final BoolSetting self = add(new BoolSetting("Self", false));

	private final SectionSetting prefixSection = add(new SectionSetting("Prefix", false));
	private final BoolSetting showPrefix = add(new BoolSetting("ShowPrefix", true));
	private final StringSetting clientPrefix = add(new StringSetting("ClientPrefix", "Dark", 16));
	private final ColorSetting prefixColor = add(new ColorSetting("PrefixColor", DEFAULT_PREFIX));
	private final ColorSetting prefixBracketColor = add(new ColorSetting("BracketColor", DEFAULT_PREFIX));

	private final SectionSetting iconSection = add(new SectionSetting("Icon", false));
	private final BoolSetting showIcon = add(new BoolSetting("ShowIcon", true));
	private final ColorSetting iconColor = add(new ColorSetting("IconColor", 0xFFFFAA00));
	private final ColorSetting friendIconColor = add(ColorSetting.forFriend("FriendIconColor"));
	private final ColorSetting selfIconColor = add(ColorSetting.forSelf("SelfIconColor"));
	private final ColorSetting bracketsColor = add(new ColorSetting("BracketsColor", 0xFFFFAA00));
	private final ColorSetting friendBracketsColor = add(ColorSetting.forFriend("FriendBracketsColor"));
	private final ColorSetting selfBracketsColor = add(ColorSetting.forSelf("SelfBracketsColor"));

	private final SectionSetting textSection = add(new SectionSetting("Colors", false));
	private final ColorSetting nameColor = add(new ColorSetting("NameColor", 0xFFFFFFFF));
	private final ColorSetting friendColor = add(ColorSetting.forFriend("FriendColor"));
	private final ColorSetting selfColor = add(ColorSetting.forSelf("SelfColor"));
	private final ColorSetting actionColor = add(new ColorSetting("ActionColor", DEFAULT_PREFIX));
	private final ColorSetting friendActionColor = add(ColorSetting.forFriend("FriendActionColor"));
	private final ColorSetting selfActionColor = add(ColorSetting.forSelf("SelfActionColor"));
	private final ColorSetting countColor = add(new ColorSetting("CountColor", 0xFFFFFFFF));
	private final ColorSetting friendCountColor = add(ColorSetting.forFriend("FriendCountColor"));
	private final ColorSetting selfCountColor = add(ColorSetting.forSelf("SelfCountColor"));
	private final BoolSetting showDot = add(new BoolSetting("ShowDot", true));

	private final SectionSetting stackSection = add(new SectionSetting("Stacking", false));
	private final BoolSetting stack = add(new BoolSetting("Stack", true));
	private final NumberSetting stackDelay = add(new NumberSetting("Delay", 5.0, 1.0, 60.0, 1.0, true));

	private final Map<UUID, Integer> totemPops = new HashMap<>();
	private final Map<UUID, Long> lastPopMs = new HashMap<>();
	private final Map<UUID, String> lastNames = new HashMap<>();

	public TotemPopNotifierModule() {
		super("TotemPopNotifier", Category.NOTIFICATIONS);
		INSTANCE = this;

		targetsSection.addSetting(players);
		targetsSection.addSetting(friends);
		targetsSection.addSetting(self);

		prefixSection.addSetting(showPrefix);
		prefixSection.addSetting(clientPrefix);
		prefixSection.addSetting(prefixColor);
		prefixSection.addSetting(prefixBracketColor);

		iconSection.addSetting(showIcon);
		iconSection.addSetting(iconColor);
		iconSection.addSetting(friendIconColor);
		iconSection.addSetting(selfIconColor);
		iconSection.addSetting(bracketsColor);
		iconSection.addSetting(friendBracketsColor);
		iconSection.addSetting(selfBracketsColor);

		textSection.addSetting(nameColor);
		textSection.addSetting(friendColor);
		textSection.addSetting(selfColor);
		textSection.addSetting(actionColor);
		textSection.addSetting(friendActionColor);
		textSection.addSetting(selfActionColor);
		textSection.addSetting(countColor);
		textSection.addSetting(friendCountColor);
		textSection.addSetting(selfCountColor);
		textSection.addSetting(showDot);

		stackSection.addSetting(stack);
		stackSection.addSetting(stackDelay);
	}

	@Override
	public void onDisable() {
		totemPops.clear();
		lastPopMs.clear();
		lastNames.clear();
	}

	public static void onPlayerTotemPop(Player player) {
		TotemPopNotifierModule notifier = INSTANCE;
		if (notifier == null || !notifier.isEnabled() || player == null) {
			return;
		}
		String name = player.getName().getString();
		if (FakePlayerModule.isFakePlayerName(name)) {
			// FakePlayer notifies directly from its own pop logic.
			return;
		}
		if (!notifier.allows(player, name)) {
			return;
		}
		notifier.onTotemPop(player.getUUID(), name);
	}

	public void onTotemPop(UUID playerUuid, String playerName) {
		if (!isEnabled()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		LocalPlayer local = client.player;
		boolean isSelf = local != null && playerUuid.equals(local.getUUID());
		boolean isFriend = !isSelf && (SocialLists.isFriend(playerUuid) || SocialLists.isFriend(playerName));
		if (isSelf) {
			if (!self.getValue()) {
				return;
			}
		} else if (isFriend) {
			if (!friends.getValue()) {
				return;
			}
		} else if (!players.getValue()) {
			return;
		}

		SocialLists.observe(playerUuid, playerName);

		int count = totemPops.getOrDefault(playerUuid, 0) + 1;
		totemPops.put(playerUuid, count);
		lastNames.put(playerUuid, playerName);

		long now = System.currentTimeMillis();
		boolean replace = false;
		if (stack.getValue()) {
			Long last = lastPopMs.get(playerUuid);
			long delayMs = Math.round(stackDelay.get()) * 1000L;
			replace = last != null && (now - last) <= delayMs;
		}
		lastPopMs.put(playerUuid, now);
		sendMessage(playerUuid, playerName, count, replace, isSelf, isFriend);
	}

	private boolean allows(Player player, String name) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer local = client.player;
		if (local != null && player.getUUID().equals(local.getUUID())) {
			return self.getValue();
		}
		if (SocialLists.isFriend(player)) {
			return friends.getValue();
		}
		return players.getValue();
	}

	public boolean resetPlayerCount(String name) {
		if (name == null || name.isBlank()) {
			return false;
		}
		String needle = name.trim();
		boolean cleared = false;
		for (UUID uuid : new ArrayList<>(lastNames.keySet())) {
			String known = lastNames.get(uuid);
			if (known != null && known.equalsIgnoreCase(needle)) {
				totemPops.remove(uuid);
				lastPopMs.remove(uuid);
				lastNames.remove(uuid);
				ChatNotify.clearStack(stackKey(uuid));
				cleared = true;
			}
		}
		return cleared;
	}

	private void sendMessage(UUID playerUuid, String playerName, int count, boolean replace, boolean isSelf, boolean friend) {
		int iconRgb = isSelf ? selfIconColor.argb() : friend ? friendIconColor.argb() : iconColor.argb();
		int bracketsRgb = isSelf ? selfBracketsColor.argb() : friend ? friendBracketsColor.argb() : bracketsColor.argb();
		int nameRgb = isSelf ? selfColor.argb() : friend ? friendColor.argb() : nameColor.argb();
		int actionRgb = isSelf ? selfActionColor.argb() : friend ? friendActionColor.argb() : actionColor.argb();
		int countRgb = isSelf ? selfCountColor.argb() : friend ? friendCountColor.argb() : countColor.argb();

		MutableComponent message = ChatNotify.start();
		message = ChatNotify.appendPrefix(message, showPrefix.getValue(), clientPrefix.get(), prefixColor.argb(), prefixBracketColor.argb());
		message = ChatNotify.appendIcon(message, showIcon.getValue(), bracketsRgb, iconRgb);

		message = ChatNotify.appendColored(message, playerName, nameRgb);
		message = ChatNotify.appendColored(message, " has popped ", actionRgb);
		message = ChatNotify.appendColored(message, String.valueOf(count), countRgb);
		message = ChatNotify.appendColored(message, count == 1 ? " totem" : " totems", actionRgb);
		if (showDot.getValue()) {
			message = ChatNotify.appendColored(message, ".", actionRgb);
		}

		if (stack.getValue()) {
			ChatNotify.sendStacked(message, stackKey(playerUuid), replace);
		} else {
			ChatNotify.send(message);
		}
	}

	private static String stackKey(UUID playerUuid) {
		return "totempop:" + playerUuid;
	}
}
