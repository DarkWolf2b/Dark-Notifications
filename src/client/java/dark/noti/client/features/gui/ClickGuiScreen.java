package dark.noti.client.features.gui;

import dark.noti.client.manager.Category;
import dark.noti.client.manager.ModuleManager;
import dark.noti.client.features.modules.client.ClickGuiModule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class ClickGuiScreen extends Screen {
	private static String search = "";
	private static long closeTime = 0;
	private static List<String> openSettings = new ArrayList<>();
	private static List<String> openColorPickers = new ArrayList<>();

	private final List<Panel> panels = new ArrayList<>();

	public ClickGuiScreen() {
		super(Component.literal("Dark Notifications"));
	}

	public static String search() {
		return search;
	}

	public static void setSearch(String value) {
		search = value;
	}

	public static void setOpenSettings(List<String> settings) {
		openSettings = new ArrayList<>(settings);
	}

	public static List<String> getOpenSettings() {
		return new ArrayList<>(openSettings);
	}

	public static void setOpenColorPickers(List<String> pickers) {
		openColorPickers = new ArrayList<>(pickers);
	}

	public static List<String> getOpenColorPickers() {
		return new ArrayList<>(openColorPickers);
	}

	public static void setCloseTime(long time) {
		closeTime = time;
	}

	public static long getCloseTime() {
		return closeTime;
	}

	public static void resetLayout() {
		Panel.resetAllLayouts();
		if (MinecraftHolder.screen() instanceof ClickGuiScreen screen) {
			screen.rebuildPanels();
		}
	}

	private void rebuildPanels() {
		panels.clear();
		for (Category c : Category.values()) {
			panels.add(new Panel(c, Panel.defaultX(c), Panel.defaultY()));
		}
	}

	@Override
	protected void init() {
		rebuildPanels();
		ClickGuiModule gui = ModuleManager.get().get(ClickGuiModule.class);
		if (gui != null && !gui.isEnabled()) {
			gui.syncEnabled(true);
		}

		if (gui != null && gui.shouldKeepSettingsOpen()) {
			long timeSinceClose = System.currentTimeMillis() - getCloseTime();
			if (timeSinceClose < gui.getSettingsCloseTime() * 1000L) {
				List<String> savedSettings = getOpenSettings();
				for (Panel panel : panels) {
					panel.setOpenSettings(savedSettings);
				}
				Panel.applyOpenColorPickers(getOpenColorPickers());
			} else {
				setOpenSettings(new ArrayList<>());
				setOpenColorPickers(new ArrayList<>());
				Panel.collapseAllNestedSettings();
			}
		} else {
			setOpenColorPickers(new ArrayList<>());
			Panel.collapseAllNestedSettings();
		}
	}

	private float guiScale() {
		ClickGuiModule gui = ModuleManager.get().get(ClickGuiModule.class);
		return gui != null ? gui.getGuiScale() : 1.0f;
	}

	private double scaleMouse(double value) {
		float scale = guiScale();
		return scale == 0f ? value : value / scale;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		float scale = guiScale();
		int smx = (int) scaleMouse(mouseX);
		int smy = (int) scaleMouse(mouseY);
		g.pose().pushMatrix();
		g.pose().scale(scale, scale);
		for (Panel panel : panels) {
			panel.render(g, smx, smy, search);
		}
		g.pose().popMatrix();
		super.render(g, mouseX, mouseY, delta);
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		// Fully transparent — world shows through; only panel chrome is drawn.
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		double mx = scaleMouse(click.x());
		double my = scaleMouse(click.y());
		for (int i = panels.size() - 1; i >= 0; i--) {
			if (panels.get(i).mouseClicked(mx, my, click.button(), search)) {
				Panel p = panels.remove(i);
				panels.add(p);
				return true;
			}
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent click) {
		for (Panel p : panels) {
			p.mouseReleased(click.button());
		}
		return super.mouseReleased(click);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
		double mx = scaleMouse(click.x());
		double my = scaleMouse(click.y());
		for (Panel p : panels) {
			p.mouseDragged(mx, my, click.button());
		}
		return super.mouseDragged(click, dx, dy);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		double mx = scaleMouse(mouseX);
		double my = scaleMouse(mouseY);
		for (int i = panels.size() - 1; i >= 0; i--) {
			if (panels.get(i).mouseScrolled(mx, my, scrollY, search)) {
				return true;
			}
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		boolean ctrl = (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0
			|| (event.modifiers() & GLFW.GLFW_MOD_SUPER) != 0;
		if (ctrl && key == GLFW.GLFW_KEY_F) {
			for (Panel p : panels) {
				p.focusSearch();
			}
			return true;
		}
		for (Panel p : panels) {
			if (p.keyPressed(key)) {
				return true;
			}
		}
		if (key == GLFW.GLFW_KEY_ESCAPE) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		char c = (char) event.codepoint();
		for (Panel p : panels) {
			if (p.charTyped(c)) {
				return true;
			}
		}
		return super.charTyped(event);
	}

	@Override
	public void onClose() {
		ClickGuiModule gui = ModuleManager.get().get(ClickGuiModule.class);
		if (gui != null && gui.isEnabled()) {
			gui.syncEnabled(false);
		}

		if (gui != null && gui.shouldKeepSettingsOpen()) {
			List<String> currentOpenSettings = new ArrayList<>();
			List<String> currentOpenPickers = new ArrayList<>();
			for (Panel panel : panels) {
				currentOpenSettings.addAll(panel.getOpenSettings());
				currentOpenPickers.addAll(panel.getOpenColorPickers());
			}
			setOpenSettings(currentOpenSettings);
			setOpenColorPickers(currentOpenPickers);
			setCloseTime(System.currentTimeMillis());
		} else {
			setOpenSettings(new ArrayList<>());
			setOpenColorPickers(new ArrayList<>());
			Panel.collapseAllNestedSettings();
		}

		super.onClose();
	}

	/** Avoids leaking Minecraft into static reset from command without a hard dependency cycle. */
	private static final class MinecraftHolder {
		private MinecraftHolder() {
		}

		static Screen screen() {
			return net.minecraft.client.Minecraft.getInstance().screen;
		}
	}
}
