package dark.noti.client.features.gui;

import dark.noti.client.config.ModuleConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class ConfigScreen extends Screen {
	private static final int WIDTH = 320;
	private static final int HEIGHT = 280;
	private static final int HEADER = 20;
	private static final int ROW = 18;

	private String name = "default";
	private String selected = "default";
	private final List<String> configs = new ArrayList<>();

	public ConfigScreen(Screen parent) {
		super(Component.literal("Configs"));
		loadConfigList();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		int left = (width - WIDTH) / 2;
		int top = (height - HEIGHT) / 2;
		Font font = Minecraft.getInstance().font;

		g.fill(left, top, left + WIDTH, top + HEIGHT, 0xEE08080F);
		outline(g, left, top, WIDTH, HEIGHT, Theme.border());

		g.fill(left, top, left + WIDTH, top + HEADER, Theme.accent());
		int titleY = top + (HEADER - font.lineHeight) / 2;
		g.text(font, "Configs", left + 6, titleY, 0xFFFFFFFF, false);

		int closeSize = 12;
		int closeX = left + WIDTH - closeSize - 4;
		int closeY = top + (HEADER - closeSize) / 2;
		g.fill(closeX, closeY, closeX + closeSize, closeY + closeSize,
			hit(mouseX, mouseY, closeX, closeY, closeSize, closeSize) ? 0xFF555555 : 0xFF333333);
		drawCloseX(g, closeX, closeY, closeSize, 0xFFFFFFFF);

		int inputY = top + HEADER + 10;
		g.fill(left + 8, inputY, left + WIDTH - 8, inputY + ROW, 0xFF1A1A1A);
		outline(g, left + 8, inputY, WIDTH - 16, ROW, 0xFF555555);
		g.text(font, name + (blink() ? "_" : ""), left + 12, inputY + 5, 0xFFFFFFFF, false);

		int btnY = inputY + ROW + 10;
		drawButton(g, left + 8, btnY, 65, ROW, "Save", mouseX, mouseY);
		drawButton(g, left + 81, btnY, 65, ROW, "Load", mouseX, mouseY);
		drawButton(g, left + 154, btnY, 65, ROW, "Delete", mouseX, mouseY);
		drawButton(g, left + 227, btnY, 65, ROW, "Folder", mouseX, mouseY);

		int listY = btnY + ROW + 10;
		for (int i = 0; i < Math.min(10, configs.size()); i++) {
			String config = configs.get(i);
			int rowY = listY + i * ROW;
			boolean isSelected = config.equalsIgnoreCase(selected);
			g.fill(left + 8, rowY, left + WIDTH - 8, rowY + ROW,
				isSelected ? Theme.accent() : (hit(mouseX, mouseY, left + 8, rowY, WIDTH - 16, ROW) ? 0xFF333333 : 0xFF1A1A1A));
			outline(g, left + 8, rowY, WIDTH - 16, ROW, 0xFF555555);
			g.text(font, config, left + 12, rowY + 5, isSelected ? 0xFFFFFFFF : 0xFFAAAAAA, false);
		}

		super.extractRenderState(g, mouseX, mouseY, delta);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		// Keep world visible behind the panel.
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		double mx = click.x();
		double my = click.y();
		int left = (width - WIDTH) / 2;
		int top = (height - HEIGHT) / 2;

		int closeSize = 12;
		int closeX = left + WIDTH - closeSize - 4;
		int closeY = top + (HEADER - closeSize) / 2;
		if (hit(mx, my, closeX, closeY, closeSize, closeSize)) {
			onClose();
			return true;
		}

		int inputY = top + HEADER + 10;
		if (hit(mx, my, left + 8, inputY, WIDTH - 16, ROW)) {
			return true;
		}

		int btnY = inputY + ROW + 10;
		if (hit(mx, my, left + 8, btnY, 65, ROW)) {
			saveConfig();
			return true;
		}
		if (hit(mx, my, left + 81, btnY, 65, ROW)) {
			loadConfig();
			return true;
		}
		if (hit(mx, my, left + 154, btnY, 65, ROW)) {
			deleteConfig();
			return true;
		}
		if (hit(mx, my, left + 227, btnY, 65, ROW)) {
			openFolder();
			return true;
		}

		int listY = btnY + ROW + 10;
		for (int i = 0; i < Math.min(10, configs.size()); i++) {
			if (hit(mx, my, left + 8, listY + i * ROW, WIDTH - 16, ROW)) {
				selected = configs.get(i);
				name = selected;
				return true;
			}
		}

		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		char c = (char) event.codepoint();
		if (c >= 32 && c != 127 && name.length() < 32) {
			name += c;
			selected = "";
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (key == GLFW.GLFW_KEY_BACKSPACE && !name.isEmpty()) {
			name = name.substring(0, name.length() - 1);
			selected = "";
			return true;
		}
		if (key == GLFW.GLFW_KEY_ESCAPE) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().setScreenAndShow(new ClickGuiScreen());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void saveConfig() {
		String target = name.trim();
		if (target.isEmpty()) {
			return;
		}
		if (ModuleConfig.saveNamed(target)) {
			loadConfigList();
			selected = ModuleConfig.isDefaultName(target) ? "default" : target;
			name = selected;
		}
	}

	private void loadConfig() {
		String target = selected.isEmpty() ? name : selected;
		if (target.isBlank()) {
			return;
		}
		ModuleConfig.loadNamed(target);
	}

	private void deleteConfig() {
		String target = selected.isEmpty() ? name : selected;
		if (ModuleConfig.isDefaultName(target)) {
			return;
		}
		if (ModuleConfig.deleteNamed(target)) {
			loadConfigList();
			selected = "default";
			name = "default";
		}
	}

	private void openFolder() {
		try {
			Path configDir = ModuleConfig.configDir().toAbsolutePath().normalize();
			Files.createDirectories(configDir);
			new ProcessBuilder("explorer", configDir.toString()).start();
		} catch (IOException ignored) {
		}
	}

	private void loadConfigList() {
		configs.clear();
		configs.add("default");
		try {
			Path configDir = ModuleConfig.configDir();
			if (Files.exists(configDir)) {
				try (Stream<Path> stream = Files.list(configDir)) {
					stream.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
						.map(p -> p.getFileName().toString().replaceAll("(?i)\\.json$", ""))
						.filter(n -> !n.equalsIgnoreCase("settings"))
						.filter(n -> !n.equalsIgnoreCase("default"))
						.sorted(String.CASE_INSENSITIVE_ORDER)
						.forEach(configs::add);
				}
			}
		} catch (IOException ignored) {
		}
	}

	private void drawButton(GuiGraphicsExtractor g, int x, int y, int w, int h, String text, int mx, int my) {
		g.fill(x, y, x + w, y + h, hit(mx, my, x, y, w, h) ? 0xFF555555 : 0xFF333333);
		outline(g, x, y, w, h, Theme.accent());
		Font font = Minecraft.getInstance().font;
		g.text(font, text, x + (w - font.width(text)) / 2, y + 5, 0xFFFFFFFF, false);
	}

	/** Pixel X centered in the close button (font glyphs sit high in the cell). */
	private static void drawCloseX(GuiGraphicsExtractor g, int bx, int by, int size, int color) {
		int pad = 3;
		int x0 = bx + pad;
		int y0 = by + pad;
		int x1 = bx + size - pad - 1;
		int y1 = by + size - pad - 1;
		int steps = Math.min(x1 - x0, y1 - y0);
		for (int i = 0; i <= steps; i++) {
			g.fill(x0 + i, y0 + i, x0 + i + 1, y0 + i + 1, color);
			g.fill(x1 - i, y0 + i, x1 - i + 1, y0 + i + 1, color);
		}
	}

	private static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		g.fill(x, y, x + w, y + 1, color);
		g.fill(x, y + h - 1, x + w, y + h, color);
		g.fill(x, y, x + 1, y + h, color);
		g.fill(x + w - 1, y, x + w, y + h, color);
	}

	private static boolean hit(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx <= x + w && my >= y && my <= y + h;
	}

	private static boolean blink() {
		return (System.currentTimeMillis() / 400L) % 2L == 0L;
	}
}
