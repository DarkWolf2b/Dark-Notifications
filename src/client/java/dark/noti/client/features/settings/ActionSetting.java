package dark.noti.client.features.settings;

public final class ActionSetting extends Setting<Runnable> {
	public ActionSetting(String name, Runnable action) {
		super(name, action);
	}

	public void run() {
		Runnable action = get();
		if (action != null) {
			action.run();
		}
	}
}
