package dark.noti.client.features.settings;

public class BoolSetting extends Setting<Boolean> {
	public BoolSetting(String name, boolean value) {
		super(name, value);
	}

	public boolean getValue() {
		return Boolean.TRUE.equals(get());
	}

	public void toggle() {
		set(!getValue());
	}
}
