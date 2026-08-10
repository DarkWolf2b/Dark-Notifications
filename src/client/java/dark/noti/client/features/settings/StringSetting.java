package dark.noti.client.features.settings;

public final class StringSetting extends Setting<String> {
	private final int maxLength;
	private boolean listening;

	public StringSetting(String name, String value, int maxLength) {
		super(name, value);
		this.maxLength = Math.max(1, maxLength);
	}

	@Override
	public void set(String value) {
		String safe = value == null ? "" : value;
		super.set(safe.length() > maxLength ? safe.substring(0, maxLength) : safe);
	}

	public int maxLength() {
		return maxLength;
	}

	public boolean isListening() {
		return listening;
	}

	public void setListening(boolean listening) {
		this.listening = listening;
	}

	public String display() {
		return get().isEmpty() ? "<empty>" : get();
	}
}
