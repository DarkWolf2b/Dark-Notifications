package dark.noti.client.manager;

import dark.noti.client.features.settings.Setting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Module {
	private final String name;
	private final Category category;
	private final List<Setting<?>> settings = new ArrayList<>();
	private boolean enabled;
	private int keybind = GLFW.GLFW_KEY_UNKNOWN;
	private boolean drawn = true;

	protected Module(String name, Category category) {
		this.name = name;
		this.category = category;
	}

	protected <S extends Setting<?>> S add(S setting) {
		settings.add(setting);
		return setting;
	}

	public String getName() {
		return name;
	}

	public Category getCategory() {
		return category;
	}

	public List<Setting<?>> getSettings() {
		return Collections.unmodifiableList(settings);
	}

	public boolean hasSettings() {
		return !settings.isEmpty();
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		setEnabled(enabled, true);
	}

	public void setEnabled(boolean enabled, boolean announce) {
		if (this.enabled == enabled) {
			return;
		}
		this.enabled = enabled;
		if (enabled) {
			onEnable();
		} else {
			onDisable();
		}
		dark.noti.client.config.ModuleConfig.markDirty();
		if (announce) {
			ModuleManager.get().onModuleToggled(this);
		}
	}

	public void toggle() {
		setEnabled(!enabled, true);
	}

	public int getKeybind() {
		return keybind;
	}

	public void setKeybind(int keybind) {
		this.keybind = keybind;
		dark.noti.client.config.ModuleConfig.markDirty();
	}

	public boolean isDrawn() {
		return drawn;
	}

	public void setDrawn(boolean drawn) {
		this.drawn = drawn;
	}

	protected void onEnable() {
	}

	protected void onDisable() {
	}

	public void onTick() {
	}

	public void resetSettings() {
		for (Setting<?> setting : settings) {
			setting.reset();
		}
	}
}
