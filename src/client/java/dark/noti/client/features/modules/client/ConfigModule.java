package dark.noti.client.features.modules.client;

import dark.noti.client.features.gui.ClickGuiScreen;
import dark.noti.client.features.gui.ConfigScreen;
import dark.noti.client.manager.Category;
import dark.noti.client.manager.Module;
import dark.noti.client.manager.ModuleManager;
import net.minecraft.client.Minecraft;

public final class ConfigModule extends Module {
	public ConfigModule() {
		super("Config", Category.CLIENT);
	}

	@Override
	public void setEnabled(boolean enabled) {
		// Config opens an editor; it is not a persistent toggle.
	}

	@Override
	public void toggle() {
		openEditor();
	}

	private void openEditor() {
		Minecraft minecraft = Minecraft.getInstance();
		ClickGuiModule clickGui = ModuleManager.get().get(ClickGuiModule.class);
		if (clickGui != null && clickGui.isEnabled()) {
			clickGui.syncEnabled(false);
		}
		if (minecraft.screen instanceof ClickGuiScreen) {
			minecraft.setScreen(null);
		}
		minecraft.setScreen(new ConfigScreen(null));
	}
}
