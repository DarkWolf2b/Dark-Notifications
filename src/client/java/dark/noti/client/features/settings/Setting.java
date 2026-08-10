package dark.noti.client.features.settings;

public abstract class Setting<T> {
	private final String name;
	private T value;
	private boolean hidden;

	protected Setting(String name, T value) {
		this.name = name;
		this.value = value;
	}

	public String getName() {
		return name;
	}

	public T get() {
		return value;
	}

	public void set(T value) {
		this.value = value;
		dark.noti.client.config.ModuleConfig.markDirty();
	}

	public boolean isHidden() {
		return hidden;
	}

	public void setHidden(boolean hidden) {
		this.hidden = hidden;
	}

	public void reset() {
		// Default implementation does nothing, subclasses override
	}
}
