package dark.noti.client.features.modules.notifications;

import dark.noti.client.features.modules.client.FakePlayerModule;
import dark.noti.client.features.settings.BoolSetting;
import dark.noti.client.features.settings.ColorSetting;
import dark.noti.client.features.settings.SectionSetting;
import dark.noti.client.features.settings.StringSetting;
import dark.noti.client.manager.Category;
import dark.noti.client.manager.Module;
import dark.noti.client.util.ChatNotify;
import dark.noti.client.util.SocialLists;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DeathNotifierModule extends Module {
	public static DeathNotifierModule INSTANCE;

	private static final int DEFAULT_PREFIX = 0xFFB57BEA;

	private final SectionSetting targetsSection = add(new SectionSetting("Targets", false));
	private final BoolSetting players = add(new BoolSetting("Players", true));
	private final BoolSetting friends = add(new BoolSetting("Friends", true));
	private final BoolSetting self = add(new BoolSetting("Self", false));

	private final SectionSetting generalSection = add(new SectionSetting("General", false));
	private final BoolSetting showTotems = add(new BoolSetting("ShowTotems", true));
	private final BoolSetting showCoords = add(new BoolSetting("ShowCoords", false));

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
	private final ColorSetting coordColor = add(new ColorSetting("CoordColor", 0xFFFFFFFF));
	private final BoolSetting showPeriod = add(new BoolSetting("ShowPeriod", true));
	private final ColorSetting periodColor = add(new ColorSetting("PeriodColor", 0xFFFFFFFF));

	private final Map<UUID, Integer> totemPops = new HashMap<>();
	private final Set<UUID> notifiedDeaths = new HashSet<>();

	public DeathNotifierModule() {
		super("DeathNotifier", Category.NOTIFICATIONS);
		INSTANCE = this;

		targetsSection.addSetting(players);
		targetsSection.addSetting(friends);
		targetsSection.addSetting(self);

		generalSection.addSetting(showTotems);
		generalSection.addSetting(showCoords);

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

		SectionSetting namesGroup = new SectionSetting("Names", false);
		namesGroup.addSetting(nameColor);
		namesGroup.addSetting(friendColor);
		namesGroup.addSetting(selfColor);
		SectionSetting actionsGroup = new SectionSetting("Actions", false);
		actionsGroup.addSetting(actionColor);
		actionsGroup.addSetting(friendActionColor);
		actionsGroup.addSetting(selfActionColor);
		SectionSetting countsGroup = new SectionSetting("Counts", false);
		countsGroup.addSetting(countColor);
		countsGroup.addSetting(friendCountColor);
		countsGroup.addSetting(selfCountColor);
		SectionSetting extrasGroup = new SectionSetting("Extras", false);
		extrasGroup.addSetting(coordColor);
		extrasGroup.addSetting(showPeriod);
		extrasGroup.addSetting(periodColor);

		textSection.addSetting(namesGroup);
		textSection.addSetting(actionsGroup);
		textSection.addSetting(countsGroup);
		textSection.addSetting(extrasGroup);
	}

	@Override
	public void onDisable() {
		totemPops.clear();
		notifiedDeaths.clear();
	}

	public static void onPlayerTotemPop(Player player) {
		DeathNotifierModule notifier = INSTANCE;
		if (notifier == null || !notifier.isEnabled() || player == null) {
			return;
		}
		String name = player.getName().getString();
		if (FakePlayerModule.isFakePlayerName(name)) {
			return;
		}
		notifier.recordTotemPop(player.getUUID(), name);
	}

	private void recordTotemPop(UUID uuid, String name) {
		SocialLists.observe(uuid, name);
		totemPops.put(uuid, totemPops.getOrDefault(uuid, 0) + 1);
	}

	@Override
	public void onTick() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer local = client.player;
		if (local == null || client.level == null) {
			totemPops.clear();
			notifiedDeaths.clear();
			return;
		}

		Set<UUID> seen = new HashSet<>();
		for (Player other : client.level.players()) {
			UUID uuid = other.getUUID();
			String name = other.getName().getString();
			if (FakePlayerModule.isFakePlayerName(name)) {
				continue;
			}

			boolean isSelf = uuid.equals(local.getUUID());
			// Only players currently loaded (render distance) are considered.
			seen.add(uuid);
			SocialLists.observe(uuid, name);

			boolean dead = other.isDeadOrDying() || other.getHealth() <= 0.0f;
			if (!dead) {
				notifiedDeaths.remove(uuid);
				continue;
			}
			if (!notifiedDeaths.add(uuid)) {
				continue;
			}
			if (!allows(other, name, isSelf)) {
				continue;
			}

			int pops = totemPops.getOrDefault(uuid, 0);
			totemPops.remove(uuid);
			sendDeath(name, pops, other.getX(), other.getY(), other.getZ(), isSelf,
				!isSelf && (SocialLists.isFriend(uuid) || SocialLists.isFriend(name)));
		}

		Iterator<UUID> it = totemPops.keySet().iterator();
		while (it.hasNext()) {
			if (!seen.contains(it.next())) {
				it.remove();
			}
		}
		notifiedDeaths.removeIf(uuid -> !seen.contains(uuid));
	}

	private boolean allows(Player player, String name, boolean isSelf) {
		if (isSelf) {
			return self.getValue();
		}
		if (SocialLists.isFriend(player) || SocialLists.isFriend(name)) {
			return friends.getValue();
		}
		return players.getValue();
	}

	private void sendDeath(String name, int pops, double x, double y, double z, boolean isSelf, boolean friend) {
		int iconRgb = isSelf ? selfIconColor.argb() : friend ? friendIconColor.argb() : iconColor.argb();
		int bracketsRgb = isSelf ? selfBracketsColor.argb() : friend ? friendBracketsColor.argb() : bracketsColor.argb();
		int nameRgb = isSelf ? selfColor.argb() : friend ? friendColor.argb() : nameColor.argb();
		int actionRgb = isSelf ? selfActionColor.argb() : friend ? friendActionColor.argb() : actionColor.argb();
		int countRgb = isSelf ? selfCountColor.argb() : friend ? friendCountColor.argb() : countColor.argb();

		MutableComponent message = ChatNotify.start();
		message = ChatNotify.appendPrefix(message, showPrefix.getValue(), clientPrefix.get(), prefixColor.argb(), prefixBracketColor.argb());
		message = ChatNotify.appendIcon(message, showIcon.getValue(), bracketsRgb, iconRgb);

		message = ChatNotify.appendColored(message, name, nameRgb);
		message = ChatNotify.appendColored(message, " died", actionRgb);

		if (showTotems.getValue()) {
			message = ChatNotify.appendColored(message, " after popping ", actionRgb);
			message = ChatNotify.appendColored(message, String.valueOf(pops), countRgb);
			message = ChatNotify.appendColored(message, pops == 1 ? " totem" : " totems", actionRgb);
		}

		if (showCoords.getValue()) {
			message = ChatNotify.appendColored(message, " at ", actionRgb);
			String coords = (int) Math.floor(x) + ", "
				+ (int) Math.floor(y) + ", "
				+ (int) Math.floor(z);
			message = ChatNotify.appendColored(message, coords, coordColor.argb());
		}

		if (showPeriod.getValue()) {
			message = ChatNotify.appendColored(message, ".", periodColor.argb());
		}

		ChatNotify.send(message);
	}
}
