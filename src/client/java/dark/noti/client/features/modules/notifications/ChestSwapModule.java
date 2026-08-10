package dark.noti.client.features.modules.notifications;

import dark.noti.client.features.modules.client.FakePlayerModule;
import dark.noti.client.features.settings.BoolSetting;
import dark.noti.client.manager.Category;
import dark.noti.client.manager.Module;
import dark.noti.client.features.settings.ColorSetting;
import dark.noti.client.features.settings.ModeSetting;
import dark.noti.client.features.settings.NumberSetting;
import dark.noti.client.features.settings.SectionSetting;
import dark.noti.client.features.settings.StringSetting;
import dark.noti.client.util.ChatNotify;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChestSwapModule extends Module {
	public static ChestSwapModule INSTANCE;

	private static final int DEFAULT_PREFIX = 0xFFB57BEA;
	private static final int DEFAULT_ITEM = 0xFFFFFFFF;
	/** Only suppress tick+packet duplicates of the same direction. */
	private static final long DUP_MS = 80L;
	private static final Pattern CHAT_SWAP = Pattern.compile(
		"(?i)(?:^|\\s)(?:(\\S+)\\s+)?swapped\\s+(chestplate\\s+with\\s+elytra|elytra\\s+with\\s+chestplate)\\.?$");

	private final SectionSetting generalSection = add(new SectionSetting("General", false));
	/** Manual = you; Module = others/chat; Both = either. */
	private final ModeSetting detect = add(new ModeSetting("Detect", "Both", "Both", "Manual", "Module"));
	private final BoolSetting showName = add(new BoolSetting("ShowName", false));
	private final BoolSetting chestToElytra = add(new BoolSetting("ChestToElytra", true));
	private final BoolSetting elytraToChest = add(new BoolSetting("ElytraToChest", true));

	private final SectionSetting stackSection = add(new SectionSetting("Stacking", false));
	private final BoolSetting stack = add(new BoolSetting("Stack", false));
	private final NumberSetting stackDelay = add(new NumberSetting("Delay", 3.0, 0.5, 60.0, 0.5, false));

	private final SectionSetting prefixSection = add(new SectionSetting("Prefix", false));
	private final BoolSetting showPrefix = add(new BoolSetting("ShowPrefix", true));
	private final StringSetting clientPrefix = add(new StringSetting("ClientPrefix", "Dark", 16));
	private final ColorSetting prefixColor = add(new ColorSetting("PrefixColor", DEFAULT_PREFIX));
	private final ColorSetting prefixBracketColor = add(new ColorSetting("BracketColor", DEFAULT_PREFIX));

	private final SectionSetting iconSection = add(new SectionSetting("Icon", false));
	private final BoolSetting showIcon = add(new BoolSetting("ShowIcon", false));
	private final ColorSetting iconColor = add(new ColorSetting("IconColor", 0xFFFFAA00));
	private final ColorSetting bracketsColor = add(new ColorSetting("BracketsColor", 0xFFFFAA00));

	private final SectionSetting textSection = add(new SectionSetting("Colors", false));
	private final ColorSetting nameColor = add(new ColorSetting("NameColor", DEFAULT_ITEM));
	private final ColorSetting actionColor = add(new ColorSetting("ActionColor", DEFAULT_PREFIX));
	private final ColorSetting itemColor = add(new ColorSetting("ItemColor", DEFAULT_ITEM));
	private final BoolSetting showDot = add(new BoolSetting("ShowDot", true));

	private final Map<UUID, Boolean> wasElytra = new HashMap<>();
	private final Map<UUID, Long> lastDupMs = new HashMap<>();
	private final Map<UUID, Boolean> lastDupDir = new HashMap<>();
	private final Map<UUID, SwapStack> stacks = new HashMap<>();
	private boolean publishing;

	public ChestSwapModule() {
		super("ChestSwap", Category.NOTIFICATIONS);
		INSTANCE = this;

		generalSection.addSetting(detect);
		generalSection.addSetting(showName);
		generalSection.addSetting(chestToElytra);
		generalSection.addSetting(elytraToChest);

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
		SectionSetting actionsGroup = new SectionSetting("Actions", false);
		actionsGroup.addSetting(actionColor);
		SectionSetting itemsGroup = new SectionSetting("Items", false);
		itemsGroup.addSetting(itemColor);
		SectionSetting extrasGroup = new SectionSetting("Extras", false);
		extrasGroup.addSetting(showDot);

		textSection.addSetting(namesGroup);
		textSection.addSetting(actionsGroup);
		textSection.addSetting(itemsGroup);
		textSection.addSetting(extrasGroup);
	}

	@Override
	protected void onDisable() {
		wasElytra.clear();
		lastDupMs.clear();
		lastDupDir.clear();
		stacks.clear();
	}

	private boolean watchManual() {
		return detect.is("Manual") || detect.is("Both") || detect.is("Any");
	}

	private boolean watchModule() {
		return detect.is("Module") || detect.is("Both") || detect.is("Any");
	}

	@Override
	public void onTick() {
		if (!watchManual() && !watchModule()) {
			wasElytra.clear();
			return;
		}

		Minecraft client = Minecraft.getInstance();
		LocalPlayer self = client.player;
		if (self == null || client.level == null) {
			wasElytra.clear();
			return;
		}

		Map<UUID, Boolean> seen = new HashMap<>();
		for (Player player : client.level.players()) {
			String name = player.getName().getString();
			if (FakePlayerModule.isFakePlayerName(name)) {
				continue;
			}
			boolean selfPlayer = player == self;
			if (selfPlayer && !watchManual()) {
				continue;
			}
			if (!selfPlayer && !watchModule()) {
				continue;
			}
			boolean elytra = isElytra(player.getItemBySlot(EquipmentSlot.CHEST));
			seen.put(player.getUUID(), elytra);
			handleChestState(player.getUUID(), name, elytra);
		}

		Iterator<UUID> it = wasElytra.keySet().iterator();
		while (it.hasNext()) {
			UUID id = it.next();
			if (!seen.containsKey(id)) {
				it.remove();
				lastDupMs.remove(id);
				lastDupDir.remove(id);
				stacks.remove(id);
				ChatNotify.clearStack(stackKey(id));
			}
		}
	}

	/** Other players' equipment updates (Module / Both). */
	public static void onEquipmentPacket(ClientboundSetEquipmentPacket packet) {
		ChestSwapModule module = INSTANCE;
		if (module == null || !module.isEnabled() || !module.watchModule()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return;
		}
		Entity entity = client.level.getEntity(packet.getEntity());
		if (!(entity instanceof Player player) || player == client.player) {
			return;
		}
		String name = player.getName().getString();
		if (FakePlayerModule.isFakePlayerName(name)) {
			return;
		}

		boolean touchedChest = false;
		boolean elytra = isElytra(player.getItemBySlot(EquipmentSlot.CHEST));
		for (var pair : packet.getSlots()) {
			if (pair.getFirst() == EquipmentSlot.CHEST) {
				touchedChest = true;
				elytra = isElytra(pair.getSecond());
				break;
			}
		}
		if (!touchedChest) {
			return;
		}
		module.handleChestState(player.getUUID(), name, elytra);
	}

	private void handleChestState(UUID uuid, String name, boolean elytra) {
		Boolean previous = wasElytra.put(uuid, elytra);
		if (previous == null || previous == elytra) {
			return;
		}
		boolean toElytra = !previous && elytra;
		if (toElytra && !chestToElytra.getValue()) {
			return;
		}
		if (!toElytra && !elytraToChest.getValue()) {
			return;
		}
		if (isDuplicate(uuid, toElytra)) {
			return;
		}
		notifySwap(uuid, name, toElytra);
	}

	private boolean isDuplicate(UUID uuid, boolean toElytra) {
		long now = System.currentTimeMillis();
		Long last = lastDupMs.get(uuid);
		Boolean lastDir = lastDupDir.get(uuid);
		lastDupMs.put(uuid, now);
		lastDupDir.put(uuid, toElytra);
		return last != null && lastDir != null && lastDir == toElytra && now - last < DUP_MS;
	}

	private void notifySwap(UUID uuid, String name, boolean toElytra) {
		long now = System.currentTimeMillis();
		long delayMs = Math.round(stackDelay.get() * 1000.0);

		if (!stack.getValue()) {
			stacks.remove(uuid);
			sendSwap(name, toElytra, !toElytra, toElytra, false, uuid);
			return;
		}

		SwapStack pending = stacks.get(uuid);
		boolean replace = pending != null && (now - pending.lastMs) <= delayMs;
		if (!replace) {
			pending = new SwapStack(name);
			stacks.put(uuid, pending);
		} else {
			pending.name = name;
		}
		if (toElytra) {
			pending.toElytra = true;
		} else {
			pending.toChest = true;
		}
		pending.lastToElytra = toElytra;
		pending.lastMs = now;
		sendSwap(pending.name, pending.toElytra, pending.toChest, pending.lastToElytra, replace, uuid);
	}

	/** Other-client ChestSwap chat (Module / Both). */
	public static void onChatMessage(Component message) {
		ChestSwapModule module = INSTANCE;
		if (module == null || !module.isEnabled() || message == null || module.publishing || !module.watchModule()) {
			return;
		}
		String plain = message.getString();
		if (plain == null || plain.isBlank()) {
			return;
		}
		String line = plain.trim();
		if (line.startsWith("[")) {
			int close = line.indexOf(']');
			if (close > 0 && close + 1 < line.length()) {
				line = line.substring(close + 1).trim();
			}
		}
		if (line.startsWith("[") && line.length() > 3 && line.charAt(2) == ']') {
			line = line.substring(3).trim();
		}

		Matcher m = CHAT_SWAP.matcher(line);
		if (!m.find()) {
			return;
		}
		String actionRaw = m.group(2).toLowerCase(Locale.ROOT);
		boolean toElytra = actionRaw.contains("chestplate") && actionRaw.contains("elytra")
			&& actionRaw.indexOf("chestplate") < actionRaw.indexOf("elytra");
		if (toElytra && !module.chestToElytra.getValue()) {
			return;
		}
		if (!toElytra && !module.elytraToChest.getValue()) {
			return;
		}
		String name = m.group(1);
		UUID key = name == null || name.isBlank()
			? new UUID(0L, 0L)
			: UUID.nameUUIDFromBytes(("chestswap-chat:" + name.toLowerCase(Locale.ROOT)).getBytes());
		if (module.isDuplicate(key, toElytra)) {
			return;
		}
		module.notifySwap(key, name, toElytra);
	}

	private void sendSwap(String playerName, boolean toElytra, boolean toChest, boolean lastToElytra, boolean replace, UUID stackId) {
		boolean both = toElytra && toChest;
		if (!both && !toChest && !toElytra) {
			return;
		}

		int actionRgb = actionColor.argb();
		int itemRgb = itemColor.argb();
		int nameRgb = nameColor.argb();

		MutableComponent message = ChatNotify.start();
		message = ChatNotify.appendPrefix(message, showPrefix.getValue(), clientPrefix.get(), prefixColor.argb(), prefixBracketColor.argb());
		message = ChatNotify.appendIcon(message, showIcon.getValue(), bracketsColor.argb(), iconColor.argb());

		boolean named = showName.getValue() && playerName != null && !playerName.isBlank();
		if (named) {
			message = ChatNotify.appendColored(message, playerName, nameRgb);
			message = ChatNotify.appendColored(message, " ", actionRgb);
		}

		String first;
		String second;
		if (both) {
			// Order follows the latest swap direction.
			if (lastToElytra) {
				first = "chestplate";
				second = "elytra";
			} else {
				first = "elytra";
				second = "chestplate";
			}
			String swapped = named ? "swapped between " : "Swapped between ";
			message = ChatNotify.appendColored(message, swapped, actionRgb);
			message = ChatNotify.appendColored(message, first, itemRgb);
			message = ChatNotify.appendColored(message, " and ", actionRgb);
			message = ChatNotify.appendColored(message, second, itemRgb);
		} else if (toElytra) {
			String swapped = named ? "swapped " : "Swapped ";
			message = ChatNotify.appendColored(message, swapped, actionRgb);
			message = ChatNotify.appendColored(message, "chestplate", itemRgb);
			message = ChatNotify.appendColored(message, " with ", actionRgb);
			message = ChatNotify.appendColored(message, "elytra", itemRgb);
		} else {
			String swapped = named ? "swapped " : "Swapped ";
			message = ChatNotify.appendColored(message, swapped, actionRgb);
			message = ChatNotify.appendColored(message, "elytra", itemRgb);
			message = ChatNotify.appendColored(message, " with ", actionRgb);
			message = ChatNotify.appendColored(message, "chestplate", itemRgb);
		}
		if (showDot.getValue()) {
			message = ChatNotify.appendColored(message, ".", actionRgb);
		}

		publishing = true;
		try {
			if (stack.getValue()) {
				ChatNotify.sendStacked(message, stackKey(stackId), replace);
			} else {
				ChatNotify.send(message);
			}
		} finally {
			publishing = false;
		}
	}

	private static String stackKey(UUID uuid) {
		return "chestswap:" + uuid;
	}

	private static boolean isElytra(ItemStack stack) {
		return stack != null && !stack.isEmpty() && stack.is(Items.ELYTRA);
	}

	private static final class SwapStack {
		private String name;
		private boolean toElytra;
		private boolean toChest;
		private boolean lastToElytra;
		private long lastMs;

		private SwapStack(String name) {
			this.name = name;
		}
	}
}
