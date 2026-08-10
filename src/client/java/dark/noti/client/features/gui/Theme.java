package dark.noti.client.features.gui;

import dark.noti.client.manager.ModuleManager;
import dark.noti.client.features.modules.client.ClickGuiModule;

public final class Theme {
	/** Header + enabled fill — original purple. */
	public static final int PURPLE = 0xFF9D4EDD;
	/** Stronger purple outline that remains distinct over the world. */
	public static final int BORDER = 0xFF7438A8;
	/** See-through idle rows */
	public static final int ROW_IDLE = 0x660A0A12;
	public static final int HOVER = 0x33FFFFFF;
	public static final int TEXT = 0xFFFFFFFF;
	public static final int TEXT_DIM = 0xFFCCCCCC;
	public static final int TEXT_SHADOW = 0x88000000;

	private Theme() {
	}

	public static int accent() {
		ClickGuiModule gui = ModuleManager.get().get(ClickGuiModule.class);
		if (gui != null) {
			return gui.guiColorSetting().argb();
		}
		return PURPLE;
	}

	public static int border() {
		int accent = accent();
		int r = Math.max(0, ((accent >> 16) & 0xFF) - 40);
		int g = Math.max(0, ((accent >> 8) & 0xFF) - 40);
		int b = Math.max(0, (accent & 0xFF) - 40);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}
}
