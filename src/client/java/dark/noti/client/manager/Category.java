package dark.noti.client.manager;

public enum Category {
	NOTIFICATIONS("Notifications"),
	CLIENT("Client");

	private final String label;

	Category(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}
}
