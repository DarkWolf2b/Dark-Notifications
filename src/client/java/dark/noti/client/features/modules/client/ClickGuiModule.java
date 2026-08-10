package dark.noti.client.features.modules.client;

import dark.noti.client.features.gui.ClickGuiScreen;
import dark.noti.client.manager.Category;
import dark.noti.client.manager.Module;
import dark.noti.client.features.settings.BindSetting;
import dark.noti.client.features.settings.BoolSetting;
import dark.noti.client.features.settings.ColorSetting;
import dark.noti.client.features.settings.NumberSetting;
import dark.noti.client.features.settings.StringSetting;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class ClickGuiModule extends Module {
	private final BindSetting keybind = add(new BindSetting("Keybind", GLFW.GLFW_KEY_RIGHT_SHIFT));
	private final ColorSetting guiColor = add(new ColorSetting("GuiColor", 0xFF9D4EDD));
	private final StringSetting cmdPrefix = add(new StringSetting("CMDPrefix", ".", 1));
	private final BoolSetting gear = add(new BoolSetting("Gear", false));
	private final BoolSetting keepSettingsOpen = add(new BoolSetting("KeepSettingsOpen", true));
	private final NumberSetting settingsCloseTime = add(new NumberSetting("SettingsCloseTime", 15, 1, 60, 1, true));

	/** Set via .clickgui size — not shown as a slider. */
	private float guiScale = 1.0f;

	private boolean syncing;

	public ClickGuiModule() {
		super("ClickGUI", Category.CLIENT);
		setKeybind(GLFW.GLFW_KEY_RIGHT_SHIFT);
	}

	public int getOpenKey() {
		int key = keybind.getKey();
		return key > 0 ? key : GLFW.GLFW_KEY_RIGHT_SHIFT;
	}

	public BindSetting keybindSetting() {
		return keybind;
	}

	public boolean isGearEnabled() {
		return gear.getValue();
	}

	public boolean shouldKeepSettingsOpen() {
		return keepSettingsOpen.getValue();
	}

	public int getSettingsCloseTime() {
		return (int) Math.round(settingsCloseTime.get());
	}

	public float getGuiScale() {
		return guiScale;
	}

	public void setGuiScale(float scale) {
		float snapped = Math.round(scale * 20f) / 20f;
		this.guiScale = Math.max(0.5f, Math.min(2.0f, snapped));
		dark.noti.client.config.ModuleConfig.markDirty();
	}

	public ColorSetting guiColorSetting() {
		return guiColor;
	}

	public String getCmdPrefix() {
		String prefix = cmdPrefix.get();
		return prefix == null || prefix.isEmpty() ? "." : prefix;
	}

	public void syncEnabled(boolean enabled) {
		syncing = true;
		try {
			setEnabled(enabled, false);
		} finally {
			syncing = false;
		}
	}

	@Override
	protected void onEnable() {
		if (syncing) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc != null && !(mc.screen instanceof ClickGuiScreen)) {
			mc.setScreen(new ClickGuiScreen());
		}
	}

	@Override
	protected void onDisable() {
		if (syncing) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc != null && mc.screen instanceof ClickGuiScreen) {
			mc.setScreen(null);
		}
	}
}
