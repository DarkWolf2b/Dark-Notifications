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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TeleportModule extends Module {
	private static final int DEFAULT_PREFIX = 0xFFB57BEA;

	private final SectionSetting targetsSection = add(new SectionSetting("Targets", false));
	private final BoolSetting players = add(new BoolSetting("Players", true));
	private final BoolSetting friends = add(new BoolSetting("Friends", true));
	private final BoolSetting self = add(new BoolSetting("Self", false));

	private final SectionSetting typesSection = add(new SectionSetting("Types", false));
	private final BoolSetting enderPearls = add(new BoolSetting("EnderPearls", true));
	private final BoolSetting chorusFruit = add(new BoolSetting("ChorusFruit", true));
	private final BoolSetting portal = add(new BoolSetting("Portal", true));

	private final SectionSetting infoSection = add(new SectionSetting("Info", false));
	private final BoolSetting showCoords = add(new BoolSetting("Coords", true));

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
	private final ColorSetting selfColor = add(ColorSetting.forSelf("SelfColor"));
	private final ColorSetting possessiveColor = add(new ColorSetting("PossessiveColor", DEFAULT_PREFIX));
	private final ColorSetting friendPossessiveColor = add(ColorSetting.forFriend("FriendPossessiveColor"));
	private final ColorSetting selfPossessiveColor = add(ColorSetting.forSelf("SelfPossessiveColor"));
	private final ColorSetting actionColor = add(new ColorSetting("ActionColor", DEFAULT_PREFIX));
	private final ColorSetting friendActionColor = add(ColorSetting.forFriend("FriendActionColor"));
	private final ColorSetting selfActionColor = add(ColorSetting.forSelf("SelfActionColor"));
	private final ColorSetting coordColor = add(new ColorSetting("CoordColor", 0xFFFFFFFF));
	private final BoolSetting showPeriod = add(new BoolSetting("ShowPeriod", true));
	private final ColorSetting periodColor = add(new ColorSetting("PeriodColor", DEFAULT_PREFIX));

	private final Map<Integer, PearlTrack> pearls = new HashMap<>();
	private final Map<UUID, Vec3> lastPos = new HashMap<>();
	private final Map<UUID, Boolean> eatingChorus = new HashMap<>();
	private final Map<UUID, String> lastDimension = new HashMap<>();
	private int warmTicks;

	public TeleportModule() {
		super("Teleport", Category.NOTIFICATIONS);

		targetsSection.addSetting(players);
		targetsSection.addSetting(friends);
		targetsSection.addSetting(self);

		typesSection.addSetting(enderPearls);
		typesSection.addSetting(chorusFruit);
		typesSection.addSetting(portal);

		infoSection.addSetting(showCoords);

		prefixSection.addSetting(showPrefix);
		prefixSection.addSetting(clientPrefix);
		prefixSection.addSetting(prefixColor);
		prefixSection.addSetting(prefixBracketColor);

		iconSection.addSetting(showIcon);
		iconSection.addSetting(iconColor);
		iconSection.addSetting(bracketsColor);

		textSection.addSetting(nameColor);
		textSection.addSetting(friendColor);
		textSection.addSetting(selfColor);
		textSection.addSetting(possessiveColor);
		textSection.addSetting(friendPossessiveColor);
		textSection.addSetting(selfPossessiveColor);
		textSection.addSetting(actionColor);
		textSection.addSetting(friendActionColor);
		textSection.addSetting(selfActionColor);
		textSection.addSetting(coordColor);
		textSection.addSetting(showPeriod);
		textSection.addSetting(periodColor);
	}

	@Override
	public void onEnable() {
		warmTicks = 20;
		pearls.clear();
		lastPos.clear();
		eatingChorus.clear();
		lastDimension.clear();
	}

	@Override
	public void onDisable() {
		pearls.clear();
		lastPos.clear();
		eatingChorus.clear();
		lastDimension.clear();
	}

	@Override
	public void onTick() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer local = client.player;
		if (local == null || client.level == null) {
			pearls.clear();
			lastPos.clear();
			eatingChorus.clear();
			lastDimension.clear();
			return;
		}
		if (warmTicks > 0) {
			warmTicks--;
			seedState(client, local);
			return;
		}

		if (enderPearls.getValue()) {
			tickPearls(client, local);
		}
		if (chorusFruit.getValue()) {
			tickChorus(client, local);
		}
		if (portal.getValue()) {
			tickPortals(client, local);
		}

		for (Player player : client.level.players()) {
			lastPos.put(player.getUUID(), player.position());
		}
	}

	private void seedState(Minecraft client, LocalPlayer local) {
		for (Player player : client.level.players()) {
			lastPos.put(player.getUUID(), player.position());
			lastDimension.put(player.getUUID(), client.level.dimension().identifier().toString());
		}
		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity instanceof ThrownEnderpearl pearl) {
				pearls.put(pearl.getId(), PearlTrack.from(pearl));
			}
		}
	}

	private void tickPearls(Minecraft client, LocalPlayer local) {
		Set<Integer> seen = new HashSet<>();
		for (Entity entity : client.level.entitiesForRendering()) {
			if (!(entity instanceof ThrownEnderpearl pearl)) {
				continue;
			}
			seen.add(pearl.getId());
			PearlTrack track = pearls.get(pearl.getId());
			if (track == null) {
				pearls.put(pearl.getId(), PearlTrack.from(pearl));
			} else {
				track.update(pearl);
			}
		}

		Iterator<Map.Entry<Integer, PearlTrack>> it = pearls.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, PearlTrack> entry = it.next();
			if (seen.contains(entry.getKey())) {
				continue;
			}
			PearlTrack track = entry.getValue();
			it.remove();
			if (!allows(track.ownerName, track.ownerUuid, local)) {
				continue;
			}
			notifyPearl(track.ownerName, track.ownerUuid, track.last, local);
		}
	}

	private void tickChorus(Minecraft client, LocalPlayer local) {
		for (Player player : client.level.players()) {
			UUID id = player.getUUID();
			boolean using = player.isUsingItem()
				&& (player.getUseItem().is(Items.CHORUS_FRUIT));
			Boolean was = eatingChorus.get(id);
			Vec3 prev = lastPos.get(id);
			eatingChorus.put(id, using);

			if (was != null && was && !using && prev != null) {
				Vec3 now = player.position();
				if (prev.distanceTo(now) >= 2.0) {
					if (allows(player.getName().getString(), id, local)) {
						notifyChorus(player.getName().getString(), id, now, local);
					}
				}
			}
		}
	}

	private void tickPortals(Minecraft client, LocalPlayer local) {
		String dim = client.level.dimension().identifier().toString();
		for (Player player : client.level.players()) {
			UUID id = player.getUUID();
			String previous = lastDimension.get(id);
			lastDimension.put(id, dim);
			if (previous == null || previous.equals(dim)) {
				continue;
			}
			if (!allows(player.getName().getString(), id, local)) {
				continue;
			}
			notifyPortal(player.getName().getString(), id, portalType(previous, dim), player.position(), local);
		}
	}

	private static String portalType(String from, String to) {
		String a = from == null ? "" : from.toLowerCase();
		String b = to == null ? "" : to.toLowerCase();
		if (a.contains("end") || b.contains("end")) {
			return "end portal";
		}
		return "nether portal";
	}

	private boolean allows(String name, UUID uuid, LocalPlayer local) {
		if (FakePlayerModule.isFakePlayerName(name)) {
			return false;
		}
		boolean isSelf = uuid.equals(local.getUUID());
		if (isSelf) {
			return self.getValue();
		}
		if (SocialLists.isFriend(uuid) || SocialLists.isFriend(name)) {
			return friends.getValue();
		}
		return players.getValue();
	}

	private TargetStyle styleFor(String name, UUID uuid, LocalPlayer local) {
		boolean isSelf = local != null && uuid.equals(local.getUUID());
		boolean isFriend = !isSelf && (SocialLists.isFriend(uuid) || SocialLists.isFriend(name));
		if (isSelf) {
			return new TargetStyle(selfColor.argb(), selfPossessiveColor.argb(), selfActionColor.argb());
		}
		if (isFriend) {
			return new TargetStyle(friendColor.argb(), friendPossessiveColor.argb(), friendActionColor.argb());
		}
		return new TargetStyle(nameColor.argb(), possessiveColor.argb(), actionColor.argb());
	}

	private void notifyPearl(String name, UUID uuid, Vec3 end, LocalPlayer local) {
		TargetStyle style = styleFor(name, uuid, local);
		MutableComponent message = startMessage();
		message = ChatNotify.appendColored(message, name, style.name);
		message = ChatNotify.appendColored(message, "'s ", style.possessive);
		message = ChatNotify.appendColored(message, "pearl landed", style.action);
		appendCoords(message, " at ", end, style.action);
		finish(message);
	}

	private void notifyChorus(String name, UUID uuid, Vec3 end, LocalPlayer local) {
		TargetStyle style = styleFor(name, uuid, local);
		MutableComponent message = startMessage();
		message = ChatNotify.appendColored(message, name, style.name);
		message = ChatNotify.appendColored(message, "'s ", style.possessive);
		message = ChatNotify.appendColored(message, "chorus teleported", style.action);
		appendCoords(message, " to ", end, style.action);
		finish(message);
	}

	private void notifyPortal(String name, UUID uuid, String portal, Vec3 end, LocalPlayer local) {
		TargetStyle style = styleFor(name, uuid, local);
		MutableComponent message = startMessage();
		message = ChatNotify.appendColored(message, name, style.name);
		message = ChatNotify.appendColored(message, " went through ", style.action);
		message = ChatNotify.appendColored(message, portal, style.action);
		appendCoords(message, " at ", end, style.action);
		finish(message);
	}

	private MutableComponent startMessage() {
		MutableComponent message = ChatNotify.start();
		message = ChatNotify.appendPrefix(
			message,
			showPrefix.getValue(),
			clientPrefix.get(),
			prefixColor.argb(),
			prefixBracketColor.argb()
		);
		return ChatNotify.appendIcon(message, showIcon.getValue(), bracketsColor.argb(), iconColor.argb());
	}

	private void appendCoords(MutableComponent message, String connector, Vec3 end, int actionRgb) {
		if (!showCoords.getValue()) {
			return;
		}
		ChatNotify.appendColored(message, connector, actionRgb);
		String coords = (int) Math.floor(end.x) + ", "
			+ (int) Math.floor(end.y) + ", "
			+ (int) Math.floor(end.z);
		ChatNotify.appendColored(message, coords, coordColor.argb());
	}

	private void finish(MutableComponent message) {
		if (showPeriod.getValue()) {
			ChatNotify.appendColored(message, ".", periodColor.argb());
		}
		ChatNotify.send(message);
	}

	private record TargetStyle(int name, int possessive, int action) {
	}

	private static final class PearlTrack {
		final UUID ownerUuid;
		final String ownerName;
		final Vec3 start;
		Vec3 last;

		PearlTrack(UUID ownerUuid, String ownerName, Vec3 start, Vec3 last) {
			this.ownerUuid = ownerUuid;
			this.ownerName = ownerName;
			this.start = start;
			this.last = last;
		}

		static PearlTrack from(ThrownEnderpearl pearl) {
			Entity owner = pearl.getOwner();
			UUID uuid = owner != null ? owner.getUUID() : pearl.getUUID();
			String name = owner != null ? owner.getName().getString() : "Unknown";
			Vec3 pos = pearl.position();
			return new PearlTrack(uuid, name, pos, pos);
		}

		void update(ThrownEnderpearl pearl) {
			last = pearl.position();
		}
	}
}
