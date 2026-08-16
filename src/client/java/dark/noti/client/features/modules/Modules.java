package dark.noti.client.features.modules;

import dark.noti.client.features.modules.client.ClickGuiModule;
import dark.noti.client.features.modules.client.ColorsModule;
import dark.noti.client.features.modules.client.ConfigModule;
import dark.noti.client.features.modules.client.FakePlayerModule;
import dark.noti.client.features.modules.notifications.ChatMentionModule;
import dark.noti.client.features.modules.notifications.ChestSwapModule;
import dark.noti.client.features.modules.notifications.DeathNotifierModule;
import dark.noti.client.features.modules.notifications.LogoutNotifierModule;
import dark.noti.client.features.modules.notifications.ModuleToggleModule;
import dark.noti.client.features.modules.notifications.ResourceCheckerModule;
import dark.noti.client.features.modules.notifications.TeleportModule;
import dark.noti.client.features.modules.notifications.TotemPopNotifierModule;
import dark.noti.client.features.modules.notifications.VisualRangeModule;
import dark.noti.client.manager.ModuleManager;

public final class Modules {
	private Modules() {
	}

	public static void register() {
		ModuleManager m = ModuleManager.get();

		// Client
		m.register(new ClickGuiModule());
		m.register(new ColorsModule());
		m.register(new ConfigModule());
		m.register(new FakePlayerModule());

		// Notifications
		m.register(new ModuleToggleModule());
		m.register(new VisualRangeModule());
		m.register(new TotemPopNotifierModule());
		m.register(new DeathNotifierModule());
		m.register(new LogoutNotifierModule());
		m.register(new TeleportModule());
		m.register(new ChestSwapModule());
		m.register(new ChatMentionModule());
		m.register(new ResourceCheckerModule());
	}
}
