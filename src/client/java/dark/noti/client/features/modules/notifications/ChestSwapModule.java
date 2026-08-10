package dark.noti.client.features.modules.notifications;

import dark.noti.client.features.modules.client.FakePlayerModule;
import dark.noti.client.features.settings.BoolSetting;
import dark.noti.client.manager.Category;
import dark.noti.client.manager.Module;
import dark.noti.client.features.settings.ColorSetting;
import dark.noti.client.features.settings.ModeSetting;
import dark.noti.client.features.settings.SectionSetting;
import dark.noti.client.features.settings.StringSetting;
import dark.noti.client.util.ChatNotify;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
	private static final Pattern CHAT_SWAP = Pattern.compile(
		"(?i)(?:^|\\s)(?:(\\S+)\\s+)?swapped\\s+(chestplate\\s+with\\s+elytra|elytra\\s+with\\s+chestplate)\\.?$");

	private final SectionSetting generalSection = add(new SectionSetting("General", false));
	/** Any = equipment changes; Module = other-client chat; Both = either. */
	private final ModeSetting detect = add(new ModeSetting("Detect", "Any", "Any", "Module", "Both"));
	private final BoolSetting showName = add(new BoolSetting("ShowName", false));
	private final BoolSetting chestToElytra = add(new BoolSetting("ChestToElytra", true));
	private final BoolSetting elytraToChest = add(new BoolSetting("ElytraToChest", true));

	private final SectionSetting prefixSection = add(new SectionSetting("Prefix", false));
	private final BoolSetting showPrefix = add(new BoolSetting("ShowPrefix", true));
	private final StringSetting clientPrefix = add(new StringSetting("ClientPrefix", "Dark", 16));
	private final ColorSetting prefixColor = add(new ColorSetting("PrefixColor", DEFAULT_PREFIX));
	private final ColorSetting prefixBracketColor = add(new ColorSetting("BracketColor", DEFAULT_PREFIX));

	private final SectionSetting iconSection = add(new SectionSetting("Icon", false));
	private final BoolSetting showIcon = add(new BoolSetting("ShowIcon", false));
	private final ColorSetting iconColor = add(new ColorSetting("IconColor", 0xFFFFAA00));
	private final ColorSetting bracketsColor = add(new ColorSetting("BracketsColor", 0xFFFFAA00));

	private final SectionSetting textSection = add(new SectionSetting("Text", false));
	private final ModeSetting textMode = add(new ModeSetting("TextMode", "White", "White", "Prefix"));
	private final BoolSetting showDot = add(new BoolSetting("ShowDot", true));

	private final Map<UUID, Boolean> wasElytra = new HashMap<>();
	private boolean publishing;

	public ChestSwapModule() {
		super("ChestSwap", Category.NOTIFICATIONS);
		INSTANCE = this;

		generalSection.addSetting(detect);
		generalSection.addSetting(showName);
		generalSection.addSetting(chestToElytra);
		generalSection.addSetting(elytraToChest);

		prefixSection.addSetting(showPrefix);
		prefixSection.addSetting(clientPrefix);
		prefixSection.addSetting(prefixColor);
		prefixSection.addSetting(prefixBracketColor);

		iconSection.addSetting(showIcon);
		iconSection.addSetting(iconColor);
		iconSection.addSetting(bracketsColor);

		textSection.addSetting(textMode);
		textSection.addSetting(showDot);
	}

	@Override
	public void onDisable() {
		wasElytra.clear();
	}

	private boolean watchEquipment() {
		return detect.is("Any") || detect.is("Both");
	}

	private boolean watchChat() {
		return detect.is("Module") || detect.is("Both");
	}

	@Override
	public void onTick() {
		if (!watchEquipment()) {
			wasElytra.clear();
			return;
		}

		Minecraft client = Minecraft.getInstance();
		LocalPlayer self = client.player;
		if (self == null || client.level == null) {
			wasElytra.clear();
			return;
		}

		Map<UUID, Boolean> current = new HashMap<>();
		for (Player other : client.level.players()) {
			if (other == self) {
				continue;
			}
			String name = other.getName().getString();
			if (FakePlayerModule.isFakePlayerName(name)) {
				continue;
			}

			ItemStack chest = other.getItemBySlot(EquipmentSlot.CHEST);
			boolean elytra = chest.is(Items.ELYTRA);
			current.put(other.getUUID(), elytra);

			Boolean previous = wasElytra.get(other.getUUID());
			if (previous == null) {
				continue;
			}
			if (!previous && elytra && chestToElytra.getValue()) {
				notify(name, "Swapped chestplate with elytra");
			} else if (previous && !elytra && !chest.isEmpty() && elytraToChest.getValue()) {
				notify(name, "Swapped elytra with chestplate");
			}
		}

		Iterator<UUID> it = wasElytra.keySet().iterator();
		while (it.hasNext()) {
			if (!current.containsKey(it.next())) {
				it.remove();
			}
		}
		wasElytra.putAll(current);
	}

	/** Other-client ChestSwap chat (Module / Both detect modes). */
	public static void onChatMessage(Component message) {
		ChestSwapModule module = INSTANCE;
		if (module == null || !module.isEnabled() || message == null || module.publishing || !module.watchChat()) {
			return;
		}
		String plain = message.getString();
		if (plain == null || plain.isBlank()) {
			return;
		}
		// Strip common client prefixes like [Future] / [Dark]
		String line = plain.trim();
		if (line.startsWith("[")) {
			int close = line.indexOf(']');
			if (close > 0 && close + 1 < line.length()) {
				line = line.substring(close + 1).trim();
			}
		}
		// Strip leading [+/-] icon style
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
		String action = toElytra ? "Swapped chestplate with elytra" : "Swapped elytra with chestplate";
		module.notify(name, action);
	}

	private void notify(String playerName, String action) {
		int prefixArgb = prefixColor.argb();
		int bodyRgb = ChatNotify.bodyRgb(textMode.get(), prefixArgb);
		MutableComponent message = ChatNotify.start();
		message = ChatNotify.appendPrefix(message, showPrefix.getValue(), clientPrefix.get(), prefixArgb, prefixBracketColor.argb());
		message = ChatNotify.appendIcon(message, showIcon.getValue(), bracketsColor.argb(), iconColor.argb());

		boolean named = showName.getValue() && playerName != null && !playerName.isBlank();
		String body = named
			? playerName + " " + action.substring(0, 1).toLowerCase(Locale.ROOT) + action.substring(1)
			: action;
		if (showDot.getValue()) {
			body += ".";
		}
		message = ChatNotify.appendBody(message, body, bodyRgb);

		publishing = true;
		try {
			ChatNotify.send(message);
		} finally {
			publishing = false;
		}
	}
}
