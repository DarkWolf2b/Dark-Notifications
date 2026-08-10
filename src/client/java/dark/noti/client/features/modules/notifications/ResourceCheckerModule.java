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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ResourceCheckerModule extends Module {
	private static final int DEFAULT_PREFIX = 0xFFB57BEA;
	private static final int WARM_TICKS = 40;

	private final SectionSetting generalSection = add(new SectionSetting("General", false));
	private final BoolSetting notifyOut = add(new BoolSetting("NotifyOut", true));
	private final ModeSetting display = add(new ModeSetting("Display", "Both", "Percent", "Count", "Both"));
	private final BoolSetting totems = add(new BoolSetting("Totems", true));
	private final BoolSetting crystals = add(new BoolSetting("EndCrystals", true));
	private final BoolSetting obsidian = add(new BoolSetting("Obsidian", true));
	private final BoolSetting pearls = add(new BoolSetting("EnderPearls", true));
	private final BoolSetting xpBottles = add(new BoolSetting("XPBottles", true));

	private final SectionSetting thresholdsSection = add(new SectionSetting("Thresholds", false));
	private final BoolSetting useThreshold1 = add(new BoolSetting("Threshold1", true));
	private final NumberSetting threshold1 = add(new NumberSetting("Threshold1 %", 75, 0, 100, 1, true));
	private final BoolSetting useThreshold2 = add(new BoolSetting("Threshold2", true));
	private final NumberSetting threshold2 = add(new NumberSetting("Threshold2 %", 50, 0, 100, 1, true));
	private final BoolSetting useThreshold3 = add(new BoolSetting("Threshold3", true));
	private final NumberSetting threshold3 = add(new NumberSetting("Threshold3 %", 25, 0, 100, 1, true));

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

	/** Peak count this session (100%). Raised on restock; reset after going empty. */
	private final Map<String, Integer> baselines = new HashMap<>();
	private final Map<String, Integer> lastCounts = new HashMap<>();
	/** Threshold percents already notified for this depleting stretch. */
	private final Map<String, Set<Integer>> firedThresholds = new HashMap<>();
	private int warmTicks;

	public ResourceCheckerModule() {
		super("ResourceChecker", Category.NOTIFICATIONS);

		generalSection.addSetting(notifyOut);
		generalSection.addSetting(display);
		generalSection.addSetting(totems);
		generalSection.addSetting(crystals);
		generalSection.addSetting(obsidian);
		generalSection.addSetting(pearls);
		generalSection.addSetting(xpBottles);

		thresholdsSection.addSetting(useThreshold1);
		thresholdsSection.addSetting(threshold1);
		thresholdsSection.addSetting(useThreshold2);
		thresholdsSection.addSetting(threshold2);
		thresholdsSection.addSetting(useThreshold3);
		thresholdsSection.addSetting(threshold3);

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
		SectionSetting valuesGroup = new SectionSetting("Values", false);
		valuesGroup.addSetting(valueColor);
		SectionSetting extrasGroup = new SectionSetting("Extras", false);
		extrasGroup.addSetting(showDot);

		textSection.addSetting(namesGroup);
		textSection.addSetting(actionsGroup);
		textSection.addSetting(valuesGroup);
		textSection.addSetting(extrasGroup);
	}

	@Override
	protected void onEnable() {
		baselines.clear();
		lastCounts.clear();
		firedThresholds.clear();
		warmTicks = WARM_TICKS;
	}

	@Override
	protected void onDisable() {
		baselines.clear();
		lastCounts.clear();
		firedThresholds.clear();
		warmTicks = 0;
	}

	@Override
	public void onTick() {
		threshold1.setHidden(!useThreshold1.getValue());
		threshold2.setHidden(!useThreshold2.getValue());
		threshold3.setHidden(!useThreshold3.getValue());

		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null) {
			baselines.clear();
			lastCounts.clear();
			firedThresholds.clear();
			return;
		}

		boolean warming = warmTicks > 0;
		if (warming) {
			warmTicks--;
		}

		check(player, "Totems", Items.TOTEM_OF_UNDYING, totems.getValue(), warming);
		check(player, "End Crystals", Items.END_CRYSTAL, crystals.getValue(), warming);
		check(player, "Obsidian", Items.OBSIDIAN, obsidian.getValue(), warming);
		check(player, "Ender Pearls", Items.ENDER_PEARL, pearls.getValue(), warming);
		check(player, "XP Bottles", Items.EXPERIENCE_BOTTLE, xpBottles.getValue(), warming);
	}

	private void check(LocalPlayer player, String label, Item item, boolean enabled, boolean warming) {
		if (!enabled) {
			baselines.remove(label);
			lastCounts.remove(label);
			firedThresholds.remove(label);
			return;
		}

		int count = countItem(player, item);
		Integer previous = lastCounts.get(label);

		if (count <= 0) {
			baselines.remove(label);
			firedThresholds.remove(label);
			if (!warming && previous != null && previous > 0 && notifyOut.getValue()) {
				notifyOut(label);
			}
			lastCounts.put(label, 0);
			return;
		}

		Integer base = baselines.get(label);
		if (base == null || count > base) {
			baselines.put(label, count);
			base = count;
			firedThresholds.remove(label);
		}

		int pct = (int) Math.round((count / (double) base) * 100.0);
		int previousPct = previous == null || base <= 0
			? 100
			: (int) Math.round((previous / (double) base) * 100.0);

		if (!warming) {
			Set<Integer> fired = firedThresholds.computeIfAbsent(label, k -> new HashSet<>());
			fired.removeIf(t -> pct > t);

			for (int thresh : activeThresholds()) {
				if (pct <= thresh && previousPct > thresh && fired.add(thresh)) {
					notifyLow(label, pct, count);
				}
			}
		}

		lastCounts.put(label, count);
	}

	private List<Integer> activeThresholds() {
		List<Integer> list = new ArrayList<>(3);
		if (useThreshold1.getValue()) {
			list.add((int) Math.round(threshold1.get()));
		}
		if (useThreshold2.getValue()) {
			list.add((int) Math.round(threshold2.get()));
		}
		if (useThreshold3.getValue()) {
			list.add((int) Math.round(threshold3.get()));
		}
		list.sort(Integer::compareTo);
		return list;
	}

	private void notifyOut(String label) {
		int nameRgb = nameColor.argb();
		int actionRgb = actionColor.argb();
		MutableComponent message = baseMessage();
		message = ChatNotify.appendColored(message, label, nameRgb);
		message = ChatNotify.appendColored(message, " are out", actionRgb);
		if (showDot.getValue()) {
			message = ChatNotify.appendColored(message, ".", actionRgb);
		}
		ChatNotify.send(message);
	}

	private void notifyLow(String label, int pct, int count) {
		int nameRgb = nameColor.argb();
		int actionRgb = actionColor.argb();
		int valueRgb = valueColor.argb();

		MutableComponent message = baseMessage();
		message = ChatNotify.appendColored(message, label, nameRgb);
		message = ChatNotify.appendColored(message, " low", actionRgb);

		String mode = display.get();
		boolean showPct = "Percent".equalsIgnoreCase(mode) || "Both".equalsIgnoreCase(mode);
		boolean showCount = "Count".equalsIgnoreCase(mode) || "Both".equalsIgnoreCase(mode);
		if (showPct || showCount) {
			message = ChatNotify.appendColored(message, " (", actionRgb);
			boolean first = true;
			if (showPct) {
				message = ChatNotify.appendColored(message, String.valueOf(pct), valueRgb);
				message = ChatNotify.appendColored(message, "% left", actionRgb);
				first = false;
			}
			if (showCount) {
				if (!first) {
					message = ChatNotify.appendColored(message, ", ", actionRgb);
				}
				message = ChatNotify.appendColored(message, String.valueOf(count), valueRgb);
				message = ChatNotify.appendColored(message, " left", actionRgb);
			}
			message = ChatNotify.appendColored(message, ")", actionRgb);
		}
		if (showDot.getValue()) {
			message = ChatNotify.appendColored(message, ".", actionRgb);
		}
		ChatNotify.send(message);
	}

	private MutableComponent baseMessage() {
		MutableComponent message = ChatNotify.start();
		message = ChatNotify.appendPrefix(message, showPrefix.getValue(), clientPrefix.get(), prefixColor.argb(), prefixBracketColor.argb());
		message = ChatNotify.appendIcon(message, showIcon.getValue(), bracketsColor.argb(), iconColor.argb());
		return message;
	}

	/** Main inventory + offhand (1.21+ keeps offhand in equipment, not only container slots). */
	private static int countItem(LocalPlayer player, Item item) {
		int total = 0;
		Inventory inv = player.getInventory();
		for (ItemStack stack : inv.getNonEquipmentItems()) {
			if (!stack.isEmpty() && stack.is(item)) {
				total += stack.getCount();
			}
		}
		ItemStack offhand = player.getItemBySlot(EquipmentSlot.OFFHAND);
		if (!offhand.isEmpty() && offhand.is(item)) {
			total += offhand.getCount();
		}
		return total;
	}
}
