package dark.noti.client.features.modules.notifications;

import dark.noti.client.manager.Category;
import dark.noti.client.manager.Module;
import dark.noti.client.features.settings.BoolSetting;
import dark.noti.client.features.settings.ColorSetting;
import dark.noti.client.features.settings.ModeSetting;
import dark.noti.client.features.settings.NumberSetting;
import dark.noti.client.features.settings.SectionSetting;
import dark.noti.client.features.settings.StringSetting;
import dark.noti.client.util.ChatNotify;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

public final class ResourceCheckerModule extends Module {
	private static final int DEFAULT_PREFIX = 0xFFB57BEA;

	private final SectionSetting generalSection = add(new SectionSetting("General", false));
	private final NumberSetting threshold = add(new NumberSetting("Threshold%", 25, 0, 100, 1, true));
	private final ModeSetting notifyWhen = add(new ModeSetting("NotifyWhen", "Both", "Threshold", "Out", "Both"));
	private final ModeSetting display = add(new ModeSetting("Display", "Both", "Percent", "Count", "Both"));
	private final BoolSetting totems = add(new BoolSetting("Totems", true));
	private final BoolSetting crystals = add(new BoolSetting("EndCrystals", true));
	private final BoolSetting obsidian = add(new BoolSetting("Obsidian", true));
	private final BoolSetting pearls = add(new BoolSetting("EnderPearls", true));
	private final BoolSetting xpBottles = add(new BoolSetting("XPBottles", true));

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
	private final ColorSetting actionColor = add(new ColorSetting("ActionColor", DEFAULT_PREFIX));
	private final ColorSetting valueColor = add(new ColorSetting("ValueColor", 0xFFFFFFFF));
	private final BoolSetting showDot = add(new BoolSetting("ShowDot", true));

	private final Map<String, Integer> lastNotified = new HashMap<>();

	public ResourceCheckerModule() {
		super("ResourceChecker", Category.NOTIFICATIONS);

		generalSection.addSetting(threshold);
		generalSection.addSetting(notifyWhen);
		generalSection.addSetting(display);
		generalSection.addSetting(totems);
		generalSection.addSetting(crystals);
		generalSection.addSetting(obsidian);
		generalSection.addSetting(pearls);
		generalSection.addSetting(xpBottles);

		prefixSection.addSetting(showPrefix);
		prefixSection.addSetting(clientPrefix);
		prefixSection.addSetting(prefixColor);
		prefixSection.addSetting(prefixBracketColor);

		iconSection.addSetting(showIcon);
		iconSection.addSetting(iconColor);
		iconSection.addSetting(bracketsColor);

		textSection.addSetting(nameColor);
		textSection.addSetting(actionColor);
		textSection.addSetting(valueColor);
		textSection.addSetting(showDot);
	}

	@Override
	public void onDisable() {
		lastNotified.clear();
	}

	@Override
	public void onTick() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}

		check(player, "Totems", Items.TOTEM_OF_UNDYING, totems.getValue(), 64);
		check(player, "End Crystals", Items.END_CRYSTAL, crystals.getValue(), 64);
		check(player, "Obsidian", Items.OBSIDIAN, obsidian.getValue(), 64);
		check(player, "Ender Pearls", Items.ENDER_PEARL, pearls.getValue(), 16);
		check(player, "XP Bottles", Items.EXPERIENCE_BOTTLE, xpBottles.getValue(), 64);
	}

	private void check(LocalPlayer player, String label, Item item, boolean enabled, int stackMax) {
		if (!enabled) {
			lastNotified.remove(label);
			return;
		}
		int count = countItem(player, item);
		int pct = (int) Math.round((count / (double) Math.max(1, stackMax)) * 100.0);
		int thresh = (int) Math.round(threshold.get());
		String when = notifyWhen.get();
		boolean notifyOut = "Out".equalsIgnoreCase(when) || "Both".equalsIgnoreCase(when);
		boolean notifyThresh = "Threshold".equalsIgnoreCase(when) || "Both".equalsIgnoreCase(when);

		Integer previous = lastNotified.get(label);
		if (count <= 0 && notifyOut) {
			if (previous == null || previous > 0) {
				notifyOut(label);
			}
			lastNotified.put(label, 0);
			return;
		}
		if (notifyThresh && pct <= thresh && count > 0) {
			if (previous == null || previous > thresh) {
				notifyLow(label, pct, count);
			}
			lastNotified.put(label, pct);
			return;
		}
		lastNotified.put(label, pct);
	}

	private void notifyOut(String label) {
		MutableComponent message = baseMessage();
		message = ChatNotify.appendColored(message, label, nameColor.argb());
		message = ChatNotify.appendColored(message, " are out", actionColor.argb());
		if (showDot.getValue()) {
			message = ChatNotify.appendColored(message, ".", actionColor.argb());
		}
		ChatNotify.send(message);
	}

	private void notifyLow(String label, int pct, int count) {
		MutableComponent message = baseMessage();
		message = ChatNotify.appendColored(message, label, nameColor.argb());
		message = ChatNotify.appendColored(message, " low", actionColor.argb());

		String mode = display.get();
		boolean showPct = "Percent".equalsIgnoreCase(mode) || "Both".equalsIgnoreCase(mode);
		boolean showCount = "Count".equalsIgnoreCase(mode) || "Both".equalsIgnoreCase(mode);
		if (showPct || showCount) {
			message = ChatNotify.appendColored(message, " (", actionColor.argb());
			boolean first = true;
			if (showPct) {
				message = ChatNotify.appendColored(message, pct + "% left", valueColor.argb());
				first = false;
			}
			if (showCount) {
				if (!first) {
					message = ChatNotify.appendColored(message, ", ", actionColor.argb());
				}
				message = ChatNotify.appendColored(message, count + " left", valueColor.argb());
			}
			message = ChatNotify.appendColored(message, ")", actionColor.argb());
		}
		if (showDot.getValue()) {
			message = ChatNotify.appendColored(message, ".", actionColor.argb());
		}
		ChatNotify.send(message);
	}

	private MutableComponent baseMessage() {
		MutableComponent message = ChatNotify.start();
		message = ChatNotify.appendPrefix(message, showPrefix.getValue(), clientPrefix.get(), prefixColor.argb(), prefixBracketColor.argb());
		message = ChatNotify.appendIcon(message, showIcon.getValue(), bracketsColor.argb(), iconColor.argb());
		return message;
	}

	private static int countItem(LocalPlayer player, Item item) {
		int total = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}
}
