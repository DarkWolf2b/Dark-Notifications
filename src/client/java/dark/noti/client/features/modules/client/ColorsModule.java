package dark.noti.client.features.modules.client;

import dark.noti.client.manager.Category;
import dark.noti.client.manager.Module;
import dark.noti.client.features.settings.ColorSetting;
import dark.noti.client.features.settings.SectionSetting;
import dark.noti.client.util.SavedColors;

import java.util.Map;

public class ColorsModule extends Module {
	public static ColorsModule INSTANCE;

	private final ColorSetting primary = add(new ColorSetting("Primary", 0xFF9D4EDD));
	private final ColorSetting friend = add(new ColorSetting("Friend", 0xFF55C6F0));
	private final SectionSetting savedSection = add(new SectionSetting("Saved", false));

	public ColorsModule() {
		super("Colors", Category.CLIENT);
		INSTANCE = this;
		setDrawn(false);
		super.setEnabled(true);
		rebuildSavedSection();
	}

	@Override
	public void toggle() {
	}

	@Override
	public void setEnabled(boolean enabled) {
		if (enabled) {
			super.setEnabled(true);
		}
	}

	public void rebuildSavedSection() {
		savedSection.getSettings().clear();
		Map<String, Integer> all = SavedColors.all();
		for (Map.Entry<String, Integer> entry : all.entrySet()) {
			savedSection.addSetting(new ColorSetting(entry.getKey(), entry.getValue()));
		}
	}

	public boolean deleteSavedColor(String name) {
		if (name == null || name.isBlank()) {
			return false;
		}
		boolean existed = SavedColors.names().stream().anyMatch(n -> n.equals(name));
		if (!existed) {
			return false;
		}
		SavedColors.remove(name);
		rebuildSavedSection();
		return true;
	}

	public static void saveNamedColor(String name, int argb) {
		SavedColors.put(name, argb);
		if (INSTANCE != null) {
			INSTANCE.rebuildSavedSection();
		}
	}

	public boolean isSavedEntry(ColorSetting c) {
		return c != null && c != primary && c != friend;
	}

	public int primary() {
		return primary.argb();
	}

	/** Raw custom primary without link resolution (avoids recursion). */
	public int primaryRaw() {
		return primary.customArgb();
	}

	public int friend() {
		return friend.argb();
	}

	/** Raw custom friend without link resolution (avoids recursion). */
	public int friendRaw() {
		return friend.customArgb();
	}

	public ColorSetting primarySetting() {
		return primary;
	}

	public ColorSetting friendSetting() {
		return friend;
	}
}
