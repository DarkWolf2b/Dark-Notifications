package dark.noti.client;

import dark.noti.Noti;
import dark.noti.client.config.ModuleConfig;
import dark.noti.client.features.commands.ClientCommandHandler;
import dark.noti.client.features.modules.Modules;
import dark.noti.client.manager.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;

public class NotiClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Modules.register();
		ModuleConfig.load();

		ClientTickEvents.END_CLIENT_TICK.register(client -> ModuleManager.get().tick());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ModuleConfig.save());
		ClientSendMessageEvents.ALLOW_CHAT.register(message -> !ClientCommandHandler.tryHandle(message));
		ClientSendMessageEvents.ALLOW_COMMAND.register(message -> !ClientCommandHandler.tryHandle(message));

		Noti.LOGGER.info("Dark Notifications ready — Right Shift to open");
	}
}
