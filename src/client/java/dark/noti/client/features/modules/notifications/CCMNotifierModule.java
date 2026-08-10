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
import dark.noti.client.util.ClientDetector;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CCMNotifierModule extends Module {
	public static CCMNotifierModule INSTANCE;

	private static final int DEFAULT_PREFIX = 0xFFB57BEA;
	private static final int DEFAULT_ENABLE = 0xFF55FF55;
	private static final int DEFAULT_DISABLE = 0xFFFF5555;

	private final ModeSetting mode = add(new ModeSetting("Mode", "Icon", "Icon", "Text"));

	private final SectionSetting targetsSection = add(new SectionSetting("Targets", false));
	private final Map<String, BoolSetting> targetToggles = new LinkedHashMap<>();

	private final SectionSetting generalSection = add(new SectionSetting("General", false));
	private final BoolSetting notifyEnable = add(new BoolSetting("NotifyEnable", true));
	private final BoolSetting notifyDisable = add(new BoolSetting("NotifyDisable", true));
	private final BoolSetting merge = add(new BoolSetting("Merge", true));
	private final NumberSetting mergeTime = add(new NumberSetting("MergeTime", 3.0, 0.5, 30.0, 0.5, false));

	private final SectionSetting prefixSection = add(new SectionSetting("Prefix", false));
	private final BoolSetting showPrefix = add(new BoolSetting("ShowPrefix", true));
	private final BoolSetting showBrackets = add(new BoolSetting("ShowBrackets", true));
	private final StringSetting clientPrefix = add(new StringSetting("ClientPrefix", "Dark", 16));
	private final ColorSetting prefixColor = add(new ColorSetting("PrefixColor", DEFAULT_PREFIX));
	private final ColorSetting prefixBracketColor = add(new ColorSetting("BracketColor", DEFAULT_PREFIX));

	private final SectionSetting iconSection = add(new SectionSetting("Icon", false));
	private final BoolSetting showIcon = add(new BoolSetting("ShowIcon", true));
	private final ColorSetting enableColor = add(new ColorSetting("EnableColor", DEFAULT_ENABLE));
	private final ColorSetting disableColor = add(new ColorSetting("DisableColor", DEFAULT_DISABLE));

	private final SectionSetting textSection = add(new SectionSetting("Colors", false));
	private final ColorSetting nameColor = add(new ColorSetting("NameColor", 0xFFC9A0FF));
	private final ColorSetting actionColor = add(new ColorSetting("ActionColor", 0xFFFFFFFF));
	private final ColorSetting onColor = add(new ColorSetting("OnColor", DEFAULT_ENABLE));
	private final ColorSetting offColor = add(new ColorSetting("OffColor", DEFAULT_DISABLE));

	private final Map<String, Long> lastToggleMs = new HashMap<>();
	private boolean publishing;

	public CCMNotifierModule() {
		super("CCMNotifier", Category.NOTIFICATIONS);
		INSTANCE = this;

		for (String client : ClientDetector.CCM_TARGETS) {
			boolean def = client.equals(ClientDetector.DARK);
			BoolSetting toggle = new BoolSetting(client, def);
			targetToggles.put(client, toggle);
			targetsSection.addSetting(toggle);
		}

		generalSection.addSetting(notifyEnable);
		generalSection.addSetting(notifyDisable);
		generalSection.addSetting(merge);
		generalSection.addSetting(mergeTime);

		prefixSection.addSetting(showPrefix);
		prefixSection.addSetting(showBrackets);
		prefixSection.addSetting(clientPrefix);
		prefixSection.addSetting(prefixColor);
		prefixSection.addSetting(prefixBracketColor);

		iconSection.addSetting(showIcon);
		iconSection.addSetting(enableColor);
		iconSection.addSetting(disableColor);

		SectionSetting namesGroup = new SectionSetting("Names", false);
		namesGroup.addSetting(nameColor);
		SectionSetting actionsGroup = new SectionSetting("Actions", false);
		actionsGroup.addSetting(actionColor);
		SectionSetting statesGroup = new SectionSetting("States", false);
		statesGroup.addSetting(onColor);
		statesGroup.addSetting(offColor);

		textSection.addSetting(namesGroup);
		textSection.addSetting(actionsGroup);
		textSection.addSetting(statesGroup);

		syncModeSections();
	}

	@Override
	public void onTick() {
		syncModeSections();
	}

	private void syncModeSections() {
		boolean iconMode = mode.is("Icon");
		// Icon mode only needs icon colors; Text mode only needs text colors.
		iconSection.setHidden(!iconMode);
		textSection.setHidden(iconMode);
	}

	@Override
	public void onDisable() {
		lastToggleMs.clear();
	}

	public boolean isClientTargeted(String clientName) {
		if (clientName == null) {
			return false;
		}
		BoolSetting toggle = targetToggles.get(clientName);
		if (toggle != null) {
			return toggle.getValue();
		}
		if (clientName.equalsIgnoreCase(ClientDetector.DARK) || clientName.equalsIgnoreCase("Dark")) {
			BoolSetting dark = targetToggles.get(ClientDetector.DARK);
			return dark != null && dark.getValue();
		}
		for (Map.Entry<String, BoolSetting> e : targetToggles.entrySet()) {
			if (e.getKey().equalsIgnoreCase(clientName)) {
				return e.getValue().getValue();
			}
		}
		return false;
	}

	public static void onModuleToggled(Module module) {
		CCMNotifierModule notifier = INSTANCE;
		if (notifier == null || !notifier.isEnabled() || module == null) {
			return;
		}
		if (module == notifier) {
			return;
		}
		if (!notifier.isClientTargeted(ClientDetector.DARK)) {
			return;
		}
		notifier.notifyToggle(module.getName(), module.isEnabled());
	}

	/** Called for every chat line; relays targeted toggles from other clients. */
	public static void onChatMessage(Component message) {
		CCMNotifierModule notifier = INSTANCE;
		if (notifier == null || !notifier.isEnabled() || message == null || notifier.publishing) {
			return;
		}
		ClientDetector.ToggleHit hit = ClientDetector.parseToggle(message.getString());
		if (hit == null) {
			return;
		}
		// Local Dark toggles are handled via onModuleToggled — skip echo.
		if (hit.client().equals(ClientDetector.DARK)) {
			return;
		}
		if (!ClientDetector.isKnownCcmTarget(hit.client())) {
			return;
		}
		if (!notifier.isClientTargeted(hit.client())) {
			return;
		}
		notifier.notifyToggle(hit.module(), hit.enabled());
	}

	private void notifyToggle(String moduleName, boolean enabled) {
		if (enabled ? !notifyEnable.getValue() : !notifyDisable.getValue()) {
			return;
		}

		long now = System.currentTimeMillis();
		boolean replace = false;
		if (merge.getValue()) {
			Long last = lastToggleMs.get(moduleName);
			long windowMs = Math.round(mergeTime.get() * 1000.0);
			replace = last != null && (now - last) <= windowMs;
		}
		lastToggleMs.put(moduleName, now);

		MutableComponent message = mode.is("Text")
			? buildTextMessage(moduleName, enabled)
			: buildIconMessage(moduleName, enabled);

		publishing = true;
		try {
			if (merge.getValue()) {
				ChatNotify.sendStacked(message, stackKey(moduleName), replace);
			} else {
				ChatNotify.send(message);
			}
		} finally {
			publishing = false;
		}
	}

	private MutableComponent buildIconMessage(String moduleName, boolean enabled) {
		MutableComponent message = ChatNotify.start();
		message = ChatNotify.appendPrefix(
			message, showPrefix.getValue(), clientPrefix.get(), prefixColor.argb(), prefixBracketColor.argb(), showBrackets.getValue());

		int iconRgb = enabled ? enableColor.argb() : disableColor.argb();
		message = ChatNotify.appendBracketIcon(message, showIcon.getValue(), enabled ? "+" : "-", iconRgb);
		message = ChatNotify.appendColored(message, moduleName, 0xFFFFFFFF);
		return message;
	}

	private MutableComponent buildTextMessage(String moduleName, boolean enabled) {
		MutableComponent message = ChatNotify.start();
		message = ChatNotify.appendPrefix(
			message, showPrefix.getValue(), clientPrefix.get(), prefixColor.argb(), prefixBracketColor.argb(), showBrackets.getValue());
		message = ChatNotify.appendColored(message, moduleName, nameColor.argb());
		message = ChatNotify.appendColored(message, " toggled ", actionColor.argb());
		message = ChatNotify.appendColored(message, enabled ? "on" : "off",
			enabled ? onColor.argb() : offColor.argb());
		return message;
	}

	private static String stackKey(String moduleName) {
		return "ccm:" + moduleName.toLowerCase();
	}
}
