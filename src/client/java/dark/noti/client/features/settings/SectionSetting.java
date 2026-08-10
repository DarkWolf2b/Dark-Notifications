package dark.noti.client.features.settings;

import java.util.ArrayList;
import java.util.List;

/**
 * Collapsible settings group. Optional enableSetting makes the row act like a module:
 * left-click toggles enable, right-click expands nested settings.
 */
public class SectionSetting extends Setting<Boolean> {
	private final List<Setting<?>> settings = new ArrayList<>();
	private final BoolSetting enableSetting;
	private boolean expanded;

	public SectionSetting(String name, boolean expanded) {
		this(name, expanded, null);
	}

	public SectionSetting(String name, boolean expanded, BoolSetting enableSetting) {
		super(name, expanded);
		this.expanded = expanded;
		this.enableSetting = enableSetting;
	}

	public void addSetting(Setting<?> setting) {
		settings.add(setting);
	}

	public List<Setting<?>> getSettings() {
		return settings;
	}

	public BoolSetting getEnableSetting() {
		return enableSetting;
	}

	public boolean isToggleable() {
		return enableSetting != null;
	}

	public boolean isEnabled() {
		return enableSetting == null || enableSetting.getValue();
	}

	public void toggleEnabled() {
		if (enableSetting != null) {
			enableSetting.toggle();
		}
	}

	public boolean isExpanded() {
		return expanded;
	}

	public void setExpanded(boolean expanded) {
		this.expanded = expanded;
	}

	public void toggle() {
		expanded = !expanded;
	}

	@Override
	public Boolean get() {
		return expanded;
	}

	@Override
	public void set(Boolean value) {
		this.expanded = value;
	}
}
