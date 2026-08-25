package dark.noti.client.features.gui;

import dark.noti.client.manager.Category;
import dark.noti.client.manager.Module;
import dark.noti.client.manager.ModuleManager;
import dark.noti.client.features.settings.ActionSetting;
import dark.noti.client.features.settings.BindSetting;
import dark.noti.client.features.settings.BoolSetting;
import dark.noti.client.features.settings.ColorSetting;
import dark.noti.client.features.settings.NumberSetting;
import dark.noti.client.features.settings.SectionSetting;
import dark.noti.client.features.settings.Setting;
import dark.noti.client.features.modules.client.ClickGuiModule;
import dark.noti.client.features.modules.client.ColorsModule;
import dark.noti.client.features.modules.client.ConfigModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Midnight-style panel: left-aligned shadowed headers, light purple frame,
 * tight gaps, transparent body, purple enabled fills.
 */
public class Panel {
	public static final int WIDTH = 132;
	public static final int HEADER = 18;
	public static final int ROW = 17;
	/** Small empty gap between category sections */
	public static final int GAP = 3;
	/** Keeps labels clearly separated from the panel border. */
	private static final int TEXT_PAD_X = 4;

	/** Color-picker layout — 0 so SB/hue/alpha/buttons sit flush (no black strips). */
	private static final int PICKER_GAP = 0;
	private static final int SB_H = WIDTH - 2;
	private static final int BAR_H = 9;
	/** Bottom margin (in GUI coords) so a panel never covers the full screen. */
	private static final int BODY_SCREEN_PAD = 10;

	private static final Map<Category, int[]> SAVED_POS = new EnumMap<>(Category.class);
	private static final Map<Category, Boolean> SAVED_OPEN = new EnumMap<>(Category.class);

	private final Category category;
	private final List<Entry> entries = new ArrayList<>();
	private int x;
	private int y;
	private boolean open = true;
	private boolean dragging;
	private int dragOx;
	private int dragOy;
	private NumberSetting sliding;
	private boolean searchFocus;
	private ColorSetting savingColor;
	private String saveName = "";
	private boolean saveNameFocused;
	/** Scroll offset for the whole panel body (modules + full settings). */
	private int bodyScroll;

	private ColorSetting colorDrag;
	private int colorPart;
	private int cdX;
	private int cdY;
	private int cdW;
	private int cdH;
	private Module activeModule;

	public Panel(Category category, int x, int y) {
		this.category = category;
		int[] saved = SAVED_POS.get(category);
		if (saved != null) {
			this.x = saved[0];
			this.y = saved[1];
		} else {
			this.x = x;
			this.y = y;
		}
		Boolean savedOpen = SAVED_OPEN.get(category);
		this.open = savedOpen == null || savedOpen;
		for (Module m : ModuleManager.get().of(category)) {
			entries.add(new Entry(m));
		}
	}

	public static void resetAllLayouts() {
		SAVED_POS.clear();
		SAVED_OPEN.clear();
	}

	public static int defaultX(Category category) {
		int index = 0;
		for (Category c : Category.values()) {
			if (c == category) {
				break;
			}
			index++;
		}
		return 4 + index * (WIDTH + GAP);
	}

	public static int defaultY() {
		return 12;
	}

	public void resetLayout() {
		this.x = defaultX(category);
		this.y = defaultY();
		this.open = true;
		SAVED_POS.remove(category);
		SAVED_OPEN.remove(category);
	}

	public void persistLayout() {
		SAVED_POS.put(category, new int[] { x, y });
		SAVED_OPEN.put(category, open);
		dark.noti.client.config.ModuleConfig.markDirty();
	}

	public static com.google.gson.JsonObject saveLayout() {
		com.google.gson.JsonObject panels = new com.google.gson.JsonObject();
		for (Category c : Category.values()) {
			int[] pos = SAVED_POS.get(c);
			Boolean openState = SAVED_OPEN.get(c);
			if (pos == null && openState == null) {
				continue;
			}
			com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
			if (pos != null) {
				obj.addProperty("x", pos[0]);
				obj.addProperty("y", pos[1]);
			}
			if (openState != null) {
				obj.addProperty("open", openState);
			}
			panels.add(c.name(), obj);
		}
		return panels;
	}

	public static void loadLayout(com.google.gson.JsonObject panels) {
		if (panels == null) {
			return;
		}
		for (Category c : Category.values()) {
			if (!panels.has(c.name()) || !panels.get(c.name()).isJsonObject()) {
				continue;
			}
			com.google.gson.JsonObject obj = panels.getAsJsonObject(c.name());
			if (obj.has("x") && obj.has("y")) {
				SAVED_POS.put(c, new int[] { obj.get("x").getAsInt(), obj.get("y").getAsInt() });
			}
			if (obj.has("open")) {
				SAVED_OPEN.put(c, obj.get("open").getAsBoolean());
			}
		}
	}
	
	public List<String> getOpenSettings() {
		List<String> open = new ArrayList<>();
		for (Entry e : entries) {
			if (e.open) {
				open.add(e.module.getName());
			}
		}
		return open;
	}
	
	public void setOpenSettings(List<String> moduleNames) {
		for (Entry e : entries) {
			e.open = moduleNames.contains(e.module.getName());
		}
	}

	public List<String> getOpenColorPickers() {
		List<String> open = new ArrayList<>();
		for (Entry e : entries) {
			collectOpenColorPickers(e.module, open);
		}
		return open;
	}

	public static void collapseAllColorPickers() {
		for (Module module : ModuleManager.get().all()) {
			collapseColorPickersOnly(module);
		}
	}

	/** Collapse color pickers and nested sections (Targets, Colors, etc.). */
	public static void collapseAllNestedSettings() {
		for (Module module : ModuleManager.get().all()) {
			collapseNestedSettings(module);
		}
	}

	public static void collapseColorPickers(Module module) {
		collapseNestedSettings(module);
	}

	private static void collapseColorPickersOnly(Module module) {
		for (Setting<?> s : module.getSettings()) {
			collapseColorPickersOnlyTree(s);
		}
	}

	private static void collapseColorPickersOnlyTree(Setting<?> s) {
		if (s instanceof ColorSetting c) {
			c.setExpanded(false);
		} else if (s instanceof SectionSetting section) {
			for (Setting<?> nested : section.getSettings()) {
				collapseColorPickersOnlyTree(nested);
			}
		}
	}

	public static void collapseNestedSettings(Module module) {
		for (Setting<?> s : module.getSettings()) {
			collapseSettingTree(s);
		}
	}

	private static void collapseSettingTree(Setting<?> s) {
		if (s instanceof ColorSetting c) {
			c.setExpanded(false);
		} else if (s instanceof SectionSetting section) {
			section.setExpanded(false);
			for (Setting<?> nested : section.getSettings()) {
				collapseSettingTree(nested);
			}
		}
	}

	public static void applyOpenColorPickers(List<String> keys) {
		collapseAllColorPickers();
		if (keys == null || keys.isEmpty()) {
			return;
		}
		for (String key : keys) {
			int sep = key.indexOf('\0');
			if (sep <= 0 || sep >= key.length() - 1) {
				continue;
			}
			Module module = ModuleManager.get().byName(key.substring(0, sep));
			if (module == null) {
				continue;
			}
			String settingName = key.substring(sep + 1);
			ColorSetting color = findColorSetting(module, settingName);
			if (color != null) {
				color.setExpanded(true);
			}
		}
	}

	private static void collectOpenColorPickers(Module module, List<String> out) {
		for (Setting<?> s : module.getSettings()) {
			collectOpenColorPickersTree(module.getName(), s, out);
		}
	}

	private static void collectOpenColorPickersTree(String moduleName, Setting<?> s, List<String> out) {
		if (s instanceof ColorSetting c && c.isExpanded()) {
			out.add(moduleName + '\0' + c.getName());
		} else if (s instanceof SectionSetting section) {
			for (Setting<?> nested : section.getSettings()) {
				collectOpenColorPickersTree(moduleName, nested, out);
			}
		}
	}

	private static ColorSetting findColorSetting(Module module, String name) {
		for (Setting<?> s : module.getSettings()) {
			ColorSetting found = findColorSettingTree(s, name);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	private static ColorSetting findColorSettingTree(Setting<?> s, String name) {
		if (s instanceof ColorSetting c && c.getName().equals(name)) {
			return c;
		}
		if (s instanceof SectionSetting section) {
			for (Setting<?> nested : section.getSettings()) {
				ColorSetting found = findColorSettingTree(nested, name);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	public void render(GuiGraphicsExtractor g, int mx, int my, String query) {
		List<Entry> visible = open ? filter(query) : List.of();
		int contentH = open ? bodyContentHeight(visible) : 0;
		int viewH = open ? bodyViewHeight(contentH) : 0;
		clampBodyScroll(contentH, viewH);
		int totalH = HEADER + viewH;

		// Header fill
		g.fill(x, y, x + WIDTH, y + HEADER, Theme.accent());

		// Far-left header title + soft drop shadow
		drawLabel(g, category.label(), x + TEXT_PAD_X, y + textY(HEADER), Theme.TEXT);

		int bodyTop = y + HEADER;

		if (open) {
			int cy = bodyTop - bodyScroll;
			g.enableScissor(x, bodyTop, x + WIDTH, bodyTop + viewH);

			if (category == Category.CLIENT) {
				boolean hover = hit(mx, my, x, cy, WIDTH, ROW);
				g.fill(x + 1, cy, x + WIDTH - 1, cy + ROW, Theme.ROW_IDLE);
				if (hover || searchFocus) {
					g.fill(x + 1, cy, x + WIDTH - 1, cy + ROW, Theme.HOVER);
				}
				String q = ClickGuiScreen.search();
				String shown = q.isEmpty() && !searchFocus ? "Search..." : q + (searchFocus && blink() ? "_" : "");
				drawLabel(g, shown, x + TEXT_PAD_X, cy + textY(ROW),
					q.isEmpty() && !searchFocus ? Theme.TEXT_DIM : Theme.TEXT);
				cy += ROW;
			}

			for (Entry e : visible) {
				activeModule = e.module;
				boolean hover = hit(mx, my, x, cy, WIDTH, ROW);
				if (e.module.isEnabled()) {
					g.fill(x + 1, cy, x + WIDTH - 1, cy + ROW, Theme.accent());
				} else {
					g.fill(x + 1, cy, x + WIDTH - 1, cy + ROW, Theme.ROW_IDLE);
				}
				if (hover) {
					g.fill(x + 1, cy, x + WIDTH - 1, cy + ROW, Theme.HOVER);
				}
				drawLabel(g, e.module.getName(), x + TEXT_PAD_X, cy + textY(ROW), Theme.TEXT);

				if (isGearEnabled() && e.module.hasSettings()) {
					String gearText = e.open ? "-" : "+";
					drawLabel(g, gearText, x + WIDTH - TEXT_PAD_X - labelWidth(gearText), cy + textY(ROW), Theme.TEXT);
				}

				cy += ROW;

				if (e.open) {
					for (Setting<?> s : e.module.getSettings()) {
						if (isSectionChild(e.module, s) || s.isHidden()) {
							continue;
						}
						cy = drawSetting(g, s, cy, mx, my);
					}
				}
			}
			activeModule = null;
			g.disableScissor();
		}

		// Full section frame (header + body) with dark purple border
		outline(g, x, y, WIDTH, totalH, Theme.border());
		if (open) {
			g.fill(x + 1, bodyTop - 1, x + WIDTH - 1, bodyTop, Theme.border());
		}
	}

	private int drawSetting(GuiGraphicsExtractor g, Setting<?> s, int cy, int mx, int my) {
		if (s.isHidden()) {
			return cy;
		}
		// Skip settings that belong to sections - they're drawn within their section
		if (s instanceof SectionSetting) {
			return drawSection(g, (SectionSetting) s, cy, mx, my);
		}
		
		boolean hover = hit(mx, my, x, cy, WIDTH, ROW);
		boolean on = s instanceof BoolSetting b && b.getValue();
		boolean isExpandable = s instanceof ColorSetting c && c.isExpanded();

		if (on) {
			g.fill(x + 1, cy, x + WIDTH - 1, cy + ROW, Theme.accent());
		} else if (s instanceof NumberSetting n) {
			g.fill(x + 1, cy, x + WIDTH - 1, cy + ROW, Theme.ROW_IDLE);
			double pct = (n.get() - n.getMin()) / (n.getMax() - n.getMin());
			int fill = (int) Math.round((WIDTH - 2) * Math.max(0, Math.min(1, pct)));
			if (fill > 0) {
				g.fill(x + 1, cy, x + 1 + fill, cy + ROW, Theme.accent());
			}
		} else {
			g.fill(x + 1, cy, x + WIDTH - 1, cy + ROW, Theme.ROW_IDLE);
		}

		if (hover) {
			g.fill(x + 1, cy, x + WIDTH - 1, cy + ROW, Theme.HOVER);
		}

		drawLabel(g, s.getName(), x + TEXT_PAD_X, cy + textY(ROW), Theme.TEXT);
		
		// Draw gear indicator for expandable settings
		if (isGearEnabled() && s instanceof ColorSetting) {
			String gearText = isExpandable ? "-" : "+";
			drawLabel(g, gearText, x + WIDTH - TEXT_PAD_X - labelWidth(gearText), cy + textY(ROW), Theme.TEXT);
		}

		String right = null;
		if (s instanceof NumberSetting n) {
			right = n.display();
		} else if (s instanceof BindSetting b) {
			right = b.isListening() ? "..." : b.display();
		} else if (s instanceof dark.noti.client.features.settings.ModeSetting m) {
			right = m.display();
		} else if (s instanceof dark.noti.client.features.settings.StringSetting st) {
			right = st.isListening() ? st.get() + "_" : st.display();
		}
		if (right != null) {
			Font font = Minecraft.getInstance().font;
			drawLabel(g, right, x + WIDTH - TEXT_PAD_X - labelWidth(right), cy + textY(ROW), Theme.TEXT);
		}

		if (s instanceof ColorSetting c) {
			int sw = ROW - 8;
			int sx = x + WIDTH - TEXT_PAD_X - sw;
			int sy = cy + (ROW - sw) / 2;
			if (isDeletableSavedColor(c)) {
				drawDeleteX(g, deleteXLeft(sx), cy, mx, my);
			}
			outline(g, sx - 1, sy - 1, sw + 2, sw + 2, Theme.border());
			g.fill(sx, sy, sx + sw, sy + sw, c.argb());
			if (c.isExpanded()) {
				drawColorPicker(g, c, cy + ROW, mx, my);
				return cy + ROW + pickerHeight(c);
			}
		}

		return cy + ROW;
	}
	
	private int drawSection(GuiGraphicsExtractor g, SectionSetting section, int cy, int mx, int my) {
		if (section.isHidden()) {
			return cy;
		}
		boolean hover = hit(mx, my, x, cy, WIDTH, ROW);
		boolean expanded = section.isExpanded();
		boolean on = section.isToggleable() && section.isEnabled();

		if (on) {
			g.fill(x + 1, cy, x + WIDTH - 1, cy + ROW, Theme.accent());
		} else {
			g.fill(x + 1, cy, x + WIDTH - 1, cy + ROW, Theme.ROW_IDLE);
		}
		if (hover) {
			g.fill(x + 1, cy, x + WIDTH - 1, cy + ROW, Theme.HOVER);
		}

		drawLabel(g, section.getName(), x + TEXT_PAD_X, cy + textY(ROW), Theme.TEXT);

		if (isGearEnabled()) {
			String gearText = expanded ? "-" : "+";
			drawLabel(g, gearText, x + WIDTH - TEXT_PAD_X - labelWidth(gearText), cy + textY(ROW), Theme.TEXT);
		}

		cy += ROW;

		if (expanded) {
			for (Setting<?> nestedSetting : section.getSettings()) {
				if (nestedSetting.isHidden()) {
					continue;
				}
				cy = drawNestedSetting(g, nestedSetting, cy, mx, my);
			}
		}

		return cy;
	}
	
	private int drawNestedSetting(GuiGraphicsExtractor g, Setting<?> s, int cy, int mx, int my) {
		if (s instanceof SectionSetting section) {
			return drawSection(g, section, cy, mx, my);
		}

		boolean hover = hit(mx, my, x, cy, WIDTH, ROW);
		boolean on = s instanceof BoolSetting b && b.getValue();
		boolean isExpandable = s instanceof ColorSetting c && c.isExpanded();

		if (on) {
			g.fill(x + 1, cy, x + WIDTH - 1, cy + ROW, Theme.accent());
		} else if (s instanceof NumberSetting n) {
			g.fill(x + 1, cy, x + WIDTH - 1, cy + ROW, Theme.ROW_IDLE);
			double pct = (n.get() - n.getMin()) / (n.getMax() - n.getMin());
			int fill = (int) Math.round((WIDTH - 2) * Math.max(0, Math.min(1, pct)));
			if (fill > 0) {
				g.fill(x + 1, cy, x + 1 + fill, cy + ROW, Theme.accent());
			}
		} else {
			g.fill(x + 1, cy, x + WIDTH - 1, cy + ROW, Theme.ROW_IDLE);
		}

		if (hover) {
			g.fill(x + 1, cy, x + WIDTH - 1, cy + ROW, Theme.HOVER);
		}

		drawLabel(g, s.getName(), x + TEXT_PAD_X, cy + textY(ROW), Theme.TEXT);
		
		// Draw gear indicator for expandable settings
		if (isGearEnabled() && s instanceof ColorSetting) {
			String gearText = isExpandable ? "-" : "+";
			drawLabel(g, gearText, x + WIDTH - TEXT_PAD_X - labelWidth(gearText), cy + textY(ROW), Theme.TEXT);
		}

		String right = null;
		if (s instanceof NumberSetting n) {
			right = n.display();
		} else if (s instanceof BindSetting b) {
			right = b.isListening() ? "..." : b.display();
		} else if (s instanceof dark.noti.client.features.settings.ModeSetting m) {
			right = m.display();
		} else if (s instanceof dark.noti.client.features.settings.StringSetting st) {
			right = st.isListening() ? st.get() + "_" : st.display();
		}
		if (right != null) {
			Font font = Minecraft.getInstance().font;
			drawLabel(g, right, x + WIDTH - TEXT_PAD_X - labelWidth(right), cy + textY(ROW), Theme.TEXT);
		}

		if (s instanceof ColorSetting c) {
			int sw = ROW - 8;
			int sx = x + WIDTH - TEXT_PAD_X - sw;
			int sy = cy + (ROW - sw) / 2;
			if (isDeletableSavedColor(c)) {
				drawDeleteX(g, deleteXLeft(sx), cy, mx, my);
			}
			outline(g, sx - 1, sy - 1, sw + 2, sw + 2, Theme.border());
			g.fill(sx, sy, sx + sw, sy + sw, c.argb());
			if (c.isExpanded()) {
				drawColorPicker(g, c, cy + ROW, mx, my);
				return cy + ROW + pickerHeight(c);
			}
		}

		return cy + ROW;
	}

	private void drawColorPicker(GuiGraphicsExtractor g, ColorSetting c, int top, int mx, int my) {
		int px = x + 1;
		int pw = WIDTH - 2;
		boolean links = showColorLinks();
		boolean naming = c == savingColor;
		boolean custom = !links || c.linkMode() == ColorSetting.LinkMode.CUSTOM;
		int modeY = top;
		int sbY = links ? modeY + ROW + PICKER_GAP : top;
		int hueY = sbY + SB_H + PICKER_GAP;
		int alphaY = hueY + BAR_H + PICKER_GAP;
		int btnY;
		int bottom;

		if (custom) {
			btnY = alphaY + BAR_H + PICKER_GAP;
			bottom = naming ? btnY + ROW + PICKER_GAP + ROW : btnY + ROW;
		} else {
			btnY = modeY + ROW + PICKER_GAP;
			bottom = naming ? btnY + ROW + PICKER_GAP + ROW : btnY + ROW;
		}

		g.fill(px, top, px + pw, bottom, Theme.ROW_IDLE);

		if (links) {
			boolean modeHover = hit(mx, my, px, modeY, pw, ROW);
			// Only overlay hover — re-filling ROW_IDLE stacks alpha and looks "hovered".
			if (modeHover) {
				g.fill(px, modeY, px + pw, modeY + ROW, Theme.HOVER);
			}
			drawLabel(g, c.linkLabel(), px + TEXT_PAD_X, modeY + textY(ROW), Theme.TEXT);
			if (!custom) {
				int swatch = 10;
				int sx = px + pw - TEXT_PAD_X - swatch;
				int sy = modeY + (ROW - swatch) / 2;
				if (c.linkMode() == ColorSetting.LinkMode.SAVED && c.savedLinkName() != null) {
					drawDeleteX(g, deleteXLeft(sx), modeY, mx, my);
				}
				g.fill(sx, sy, sx + swatch, sy + swatch, c.argb());
				outline(g, sx - 1, sy - 1, swatch + 2, swatch + 2, Theme.border());
				outline(g, px, modeY, pw, ROW, Theme.border());
			}
		}

		if (custom) {
			for (int i = 0; i < pw; i++) {
				float s = i / (float) (pw - 1);
				int topColor = ColorSetting.hsbToRgb(c.hue(), s, 1f);
				g.fillGradient(px + i, sbY, px + i + 1, sbY + SB_H, topColor, 0xFF000000);
			}
			int selX = px + Math.round(c.sat() * (pw - 1));
			int selY = sbY + Math.round((1f - c.bri()) * (SB_H - 1));
			g.fill(selX - 1, selY - 1, selX + 2, selY + 2, 0xFFFFFFFF);
			g.fill(selX, selY, selX + 1, selY + 1, 0xFF000000);

			for (int i = 0; i < pw; i++) {
				g.fill(px + i, hueY, px + i + 1, hueY + BAR_H, ColorSetting.hsbToRgb(i / (float) (pw - 1), 1f, 1f));
			}
			int hx = px + Math.round(c.hue() * (pw - 1));
			g.fill(hx, hueY, hx + 1, hueY + BAR_H, 0xFFFFFFFF);

			drawChecker(g, px, alphaY, pw, BAR_H);
			int rgb = c.rgb() & 0xFFFFFF;
			for (int i = 0; i < pw; i++) {
				int a = Math.round(i / (float) (pw - 1) * 255f) & 0xFF;
				g.fill(px + i, alphaY, px + i + 1, alphaY + BAR_H, (a << 24) | rgb);
			}
			int ax = px + Math.round(c.alpha() * (pw - 1));
			g.fill(ax, alphaY, ax + 1, alphaY + BAR_H, 0xFFFFFFFF);

			outline(g, px, sbY, pw, SB_H, Theme.border());
			outline(g, px, hueY, pw, BAR_H, Theme.border());
			outline(g, px, alphaY, pw, BAR_H, Theme.border());
		}

		int third = pw / 3;
		boolean showDelete = showsPickerDelete(c);
		drawButton(g, px, btnY, third, "Copy", mx, my);
		drawButton(g, px + third, btnY, third, "Reset", mx, my);
		drawButton(g, px + 2 * third, btnY, pw - 2 * third, showDelete ? "Del" : "Save", mx, my);

		if (naming) {
			int nameY = btnY + ROW + PICKER_GAP;
			g.fill(px, nameY, px + pw, nameY + ROW, saveNameFocused ? Theme.HOVER : Theme.ROW_IDLE);
			outline(g, px, nameY, pw, ROW, Theme.border());
			String nameShown = saveName.isEmpty() && !saveNameFocused ? "Enter name..." : saveName + (saveNameFocused ? "_" : "");
			drawLabel(g, nameShown, px + TEXT_PAD_X, nameY + textY(ROW), saveName.isEmpty() && !saveNameFocused ? Theme.TEXT_DIM : Theme.TEXT);
		}
	}

	private boolean showColorLinks() {
		return !(activeModule instanceof dark.noti.client.features.modules.client.ColorsModule);
	}

	private void drawButton(GuiGraphicsExtractor g, int bx, int by, int bw, String text, int mx, int my) {
		// Background already painted by picker; only overlay hover (avoid stacked ROW_IDLE).
		if (hit(mx, my, bx, by, bw, ROW)) {
			g.fill(bx, by, bx + bw, by + ROW, Theme.HOVER);
		}
		outline(g, bx, by, bw, ROW, Theme.border());
		Font font = Minecraft.getInstance().font;
		drawLabel(g, text, bx + (bw - labelWidth(text)) / 2, by + textY(ROW), Theme.TEXT);
	}

	private static void drawChecker(GuiGraphicsExtractor g, int bx, int by, int w, int h) {
		int cell = 3;
		for (int iy = 0; iy < h; iy += cell) {
			for (int ix = 0; ix < w; ix += cell) {
				boolean dark = ((ix / cell) + (iy / cell)) % 2 == 0;
				int color = dark ? 0xFF808080 : 0xFFCFCFCF;
				g.fill(bx + ix, by + iy, Math.min(bx + ix + cell, bx + w), Math.min(by + iy + cell, by + h), color);
			}
		}
	}

	private int pickerHeight(ColorSetting c) {
		boolean links = showColorLinks();
		boolean custom = !links || c.linkMode() == ColorSetting.LinkMode.CUSTOM;
		int h;
		if (custom) {
			// SB + hue + alpha + buttons, flush (no padding strips)
			h = SB_H + PICKER_GAP + BAR_H + PICKER_GAP + BAR_H + PICKER_GAP + ROW;
			if (links) {
				h += ROW + PICKER_GAP;
			}
		} else {
			h = ROW + PICKER_GAP + ROW;
		}
		if (c == savingColor) {
			h += PICKER_GAP + ROW;
		}
		return h;
	}

	private int settingHeight(Setting<?> s) {
		if (s.isHidden()) {
			return 0;
		}
		if (s instanceof SectionSetting section) {
			int height = ROW;
			if (section.isExpanded()) {
				for (Setting<?> nested : section.getSettings()) {
					height += nestedSettingHeight(nested);
				}
			}
			return height;
		}
		if (s instanceof ColorSetting c && c.isExpanded()) {
			return ROW + pickerHeight(c);
		}
		return ROW;
	}
	
	private int nestedSettingHeight(Setting<?> s) {
		if (s.isHidden()) {
			return 0;
		}
		if (s instanceof SectionSetting section) {
			int height = ROW;
			if (section.isExpanded()) {
				for (Setting<?> nested : section.getSettings()) {
					height += nestedSettingHeight(nested);
				}
			}
			return height;
		}
		if (s instanceof ColorSetting c && c.isExpanded()) {
			return ROW + pickerHeight(c);
		}
		return ROW;
	}

	private static void drawLabel(GuiGraphicsExtractor g, String text, int x, int y, int color) {
		Font font = Minecraft.getInstance().font;
		float scale = currentGuiScale();
		if (Math.abs(scale - 1.0f) < 0.001f) {
			g.text(font, text, x, y, color, true);
			return;
		}
		// Parent pose is scaled; counter-scale so glyphs stay crisp at native size.
		g.pose().pushMatrix();
		g.pose().scale(1.0f / scale, 1.0f / scale);
		g.text(font, text, Math.round(x * scale), Math.round(y * scale), color, true);
		g.pose().popMatrix();
	}

	/** Width of a label in scaled panel space (accounts for crisp counter-scaled text). */
	private static int labelWidth(String text) {
		int w = Minecraft.getInstance().font.width(text);
		float scale = currentGuiScale();
		if (Math.abs(scale - 1.0f) < 0.001f) {
			return w;
		}
		return Math.max(1, Math.round(w / scale));
	}

	private static float currentGuiScale() {
		ClickGuiModule gui = ModuleManager.get().get(ClickGuiModule.class);
		return gui != null ? gui.getGuiScale() : 1.0f;
	}

	private static int textY(int rowHeight) {
		return Math.max(0, (rowHeight - Minecraft.getInstance().font.lineHeight) / 2);
	}

	private static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		g.fill(x, y, x + w, y + 1, color);
		g.fill(x, y + h - 1, x + w, y + h, color);
		g.fill(x, y, x + 1, y + h, color);
		g.fill(x + w - 1, y, x + w, y + h, color);
	}

	public void focusSearch() {
		if (category == Category.CLIENT) {
			searchFocus = true;
		}
	}

	public boolean mouseClicked(double mx, double my, int button, String query) {
		List<Entry> visible = open ? filter(query) : List.of();
		int contentH = open ? bodyContentHeight(visible) : 0;
		int viewH = open ? bodyViewHeight(contentH) : 0;
		clampBodyScroll(contentH, viewH);
		int totalH = HEADER + viewH;
		int bodyTop = y + HEADER;

		if (hit(mx, my, x, y, WIDTH, HEADER)) {
			if (button == 0) {
				dragging = true;
				dragOx = (int) mx - x;
				dragOy = (int) my - y;
				return true;
			}
			if (button == 1) {
				open = !open;
				persistLayout();
				return true;
			}
		}

		if (!open) {
			return hit(mx, my, x, y, WIDTH, totalH);
		}

		if (!hit(mx, my, x, bodyTop, WIDTH, viewH)) {
			return hit(mx, my, x, y, WIDTH, totalH);
		}

		int cy = bodyTop - bodyScroll;

		if (category == Category.CLIENT) {
			if (hit(mx, my, x, cy, WIDTH, ROW) && button == 0) {
				searchFocus = true;
				return true;
			}
			if (button == 0) {
				searchFocus = false;
			}
			cy += ROW;
		} else if (button == 0) {
			searchFocus = false;
		}

		for (Entry e : visible) {
			activeModule = e.module;
			if (rowVisible(cy, bodyTop, viewH) && hit(mx, my, x, cy, WIDTH, ROW)) {
				if (button == 0) {
					if (e.module instanceof ClickGuiModule) {
						return true;
					}
					if (e.module instanceof ConfigModule) {
						e.module.toggle();
						return true;
					}
					e.module.toggle();
					return true;
				}
				if (button == 1) {
					if (e.module instanceof ConfigModule) {
						e.module.toggle();
						return true;
					}
					if (e.module.hasSettings()) {
						e.open = !e.open;
						if (!e.open) {
							collapseColorPickers(e.module);
						}
						return true;
					}
				}
			}
			cy += ROW;
			if (e.open) {
				for (Setting<?> s : e.module.getSettings()) {
					if (s.isHidden() || isSectionChild(e.module, s)) {
						continue;
					}
					int h = settingHeight(s);
					if (h <= 0) {
						continue;
					}

					if (s instanceof SectionSetting section && section.isExpanded()) {
						int nestedCy = cy + ROW;
						for (Setting<?> nested : section.getSettings()) {
							if (nested.isHidden()) {
								continue;
							}
							int nestedH = nestedSettingHeight(nested);
							if (nestedH > 0
								&& overlapsView(nestedCy, nestedH, bodyTop, viewH)
								&& hit(mx, my, x, nestedCy, WIDTH, nestedH)
								&& handleNestedSettingClick(nested, mx, my, nestedCy, button)) {
								return true;
							}
							nestedCy += nestedH;
						}
					}

					if (overlapsView(cy, h, bodyTop, viewH)
						&& hit(mx, my, x, cy, WIDTH, h)
						&& handleSettingClick(s, mx, my, cy, button)) {
						return true;
					}
					cy += h;
				}
			}
		}
		activeModule = null;
		return hit(mx, my, x, y, WIDTH, totalH);
	}

	private boolean handleSettingClick(Setting<?> s, double mx, double my, int cy, int button) {
		if (my >= cy && my <= cy + ROW) {
			if (s instanceof SectionSetting section) {
				if (button == 0 && section.isToggleable()) {
					section.toggleEnabled();
					return true;
				}
				if (button == 1) {
					section.toggle();
					return true;
				}
				return false;
			}
			if (s instanceof ColorSetting c) {
				if (button == 0 && isDeletableSavedColor(c) && hitDeleteX(mx, my, cy, c)) {
					return tryDeleteSavedColor(c);
				}
				if (button == 1) {
					c.setExpanded(!c.isExpanded());
					return true;
				}
			}
			return clickSetting(s, mx, button);
		}
		if (s instanceof ColorSetting c && c.isExpanded()) {
			return clickColorPicker(c, mx, my, cy + ROW, button);
		}
		return false;
	}
	
	private boolean handleNestedSettingClick(Setting<?> s, double mx, double my, int cy, int button) {
		if (s instanceof SectionSetting section) {
			if (my >= cy && my <= cy + ROW) {
				if (button == 0 && section.isToggleable()) {
					section.toggleEnabled();
					return true;
				}
				if (button == 1) {
					section.toggle();
					return true;
				}
				return false;
			}
			if (!section.isExpanded()) {
				return false;
			}
			int nestedCy = cy + ROW;
			for (Setting<?> nested : section.getSettings()) {
				if (nested.isHidden()) {
					continue;
				}
				int nestedH = nestedSettingHeight(nested);
				if (nestedH > 0 && hit(mx, my, x, nestedCy, WIDTH, nestedH)
					&& handleNestedSettingClick(nested, mx, my, nestedCy, button)) {
					return true;
				}
				nestedCy += nestedH;
			}
			return false;
		}
		if (my >= cy && my <= cy + ROW) {
			if (s instanceof ColorSetting c) {
				if (button == 0 && isDeletableSavedColor(c) && hitDeleteX(mx, my, cy, c)) {
					return tryDeleteSavedColor(c);
				}
				if (button == 1) {
					c.setExpanded(!c.isExpanded());
					return true;
				}
			}
			return clickSetting(s, mx, button);
		}
		if (s instanceof ColorSetting c && c.isExpanded()) {
			return clickColorPicker(c, mx, my, cy + ROW, button);
		}
		return false;
	}

	private boolean tryDeleteSavedColor(ColorSetting c) {
		if (!(activeModule instanceof ColorsModule colors) || !colors.isSavedEntry(c)) {
			return false;
		}
		return colors.deleteSavedColor(c.getName());
	}

	private boolean isDeletableSavedColor(ColorSetting c) {
		return activeModule instanceof ColorsModule colors && colors.isSavedEntry(c);
	}

	private boolean showsPickerDelete(ColorSetting c) {
		return showColorLinks() && c.linkMode() == ColorSetting.LinkMode.SAVED && c.savedLinkName() != null;
	}

	private static final int DELETE_X_W = 10;

	private static int deleteXLeft(int swatchX) {
		return swatchX - DELETE_X_W - 2;
	}

	private void drawDeleteX(GuiGraphicsExtractor g, int bx, int rowY, int mx, int my) {
		boolean hover = hit(mx, my, bx, rowY, DELETE_X_W, ROW);
		drawLabel(g, "x", bx + 1, rowY + textY(ROW), hover ? 0xFFFF5555 : Theme.TEXT_DIM);
	}

	private boolean hitDeleteX(double mx, double my, int cy, ColorSetting c) {
		int sw = ROW - 8;
		int sx = x + WIDTH - TEXT_PAD_X - sw;
		return hit(mx, my, deleteXLeft(sx), cy, DELETE_X_W, ROW);
	}

	private boolean clickColorPicker(ColorSetting c, double mx, double my, int top, int button) {
		int px = x + 1;
		int pw = WIDTH - 2;
		boolean links = showColorLinks();
		boolean custom = !links || c.linkMode() == ColorSetting.LinkMode.CUSTOM;
		int modeY = top;
		int sbY = links ? modeY + ROW + PICKER_GAP : top;
		int hueY = sbY + SB_H + PICKER_GAP;
		int alphaY = hueY + BAR_H + PICKER_GAP;
		int btnY = custom ? alphaY + BAR_H + PICKER_GAP : modeY + ROW + PICKER_GAP;

		if (button == 1) {
			return false;
		}
		if (button != 0) {
			return false;
		}

		if (links && hit(mx, my, px, modeY, pw, ROW)) {
			if (!custom && c.linkMode() == ColorSetting.LinkMode.SAVED && c.savedLinkName() != null) {
				int swatch = 10;
				int sx = px + pw - TEXT_PAD_X - swatch;
				if (hitDeleteX(mx, my, modeY, c) || hit(mx, my, deleteXLeft(sx), modeY, DELETE_X_W, ROW)) {
					return tryDeleteLinkedSaved(c);
				}
			}
			c.advanceLinkControl();
			return true;
		}

		if (custom) {
			if (hit(mx, my, px, sbY, pw, SB_H)) {
				colorDrag = c;
				colorPart = 0;
				cdX = px;
				cdY = sbY;
				cdW = pw;
				cdH = SB_H;
				applySb(c, mx, my);
				return true;
			}
			if (hit(mx, my, px, hueY, pw, BAR_H)) {
				colorDrag = c;
				colorPart = 1;
				cdX = px;
				cdW = pw;
				applyHue(c, mx);
				return true;
			}
			if (hit(mx, my, px, alphaY, pw, BAR_H)) {
				colorDrag = c;
				colorPart = 2;
				cdX = px;
				cdW = pw;
				applyAlpha(c, mx);
				return true;
			}
		}
		if (hit(mx, my, px, btnY, pw, ROW)) {
			int third = pw / 3;
			if (mx < px + third) {
				Minecraft.getInstance().keyboardHandler.setClipboard(String.format("#%08X", c.argb()));
			} else if (mx < px + 2 * third) {
				c.reset();
			} else if (showsPickerDelete(c)) {
				tryDeleteLinkedSaved(c);
			} else if (isDeletableSavedColor(c)) {
				ColorsModule.saveNamedColor(c.getName(), c.argb());
			} else if (c == savingColor) {
				commitSavedColor();
			} else {
				savingColor = c;
				saveName = "";
				saveNameFocused = true;
			}
			return true;
		}
		int nameY = btnY + ROW + PICKER_GAP;
		if (c == savingColor && hit(mx, my, px, nameY, pw, ROW)) {
			saveNameFocused = true;
			return true;
		}
		return false;
	}

	private boolean tryDeleteLinkedSaved(ColorSetting c) {
		String name = c.savedLinkName();
		if (name == null) {
			return false;
		}
		ColorsModule colors = ColorsModule.INSTANCE;
		if (colors == null) {
			return false;
		}
		boolean deleted = colors.deleteSavedColor(name);
		if (deleted) {
			c.setLinkMode(ColorSetting.LinkMode.CUSTOM);
		}
		return deleted;
	}

	private void applySb(ColorSetting c, double mx, double my) {
		c.setSat((float) ((mx - cdX) / (cdW - 1)));
		c.setBri(1f - (float) ((my - cdY) / (cdH - 1)));
	}

	private void applyHue(ColorSetting c, double mx) {
		c.setHue((float) ((mx - cdX) / (cdW - 1)));
	}

	private void applyAlpha(ColorSetting c, double mx) {
		c.setAlpha((float) ((mx - cdX) / (cdW - 1)));
	}

	private static void pasteColor(ColorSetting c) {
		String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
		if (clip == null) {
			return;
		}
		clip = clip.trim();
		if (clip.startsWith("#")) {
			clip = clip.substring(1);
		}
		try {
			long v = Long.parseLong(clip, 16);
			int argb = clip.length() <= 6 ? (0xFF000000 | (int) (v & 0xFFFFFF)) : (int) v;
			c.fromArgb(argb);
			c.set(c.argb());
		} catch (NumberFormatException ignored) {
			// Ignore invalid clipboard contents.
		}
	}

	private boolean clickSetting(Setting<?> s, double mx, int button) {
		if (s instanceof BoolSetting b && button == 0) {
			b.toggle();
			return true;
		}
		if (s instanceof NumberSetting n && button == 0) {
			sliding = n;
			slide(n, mx);
			return true;
		}
		if (s instanceof BindSetting b) {
			if (button == 0) {
				clearListening();
				b.setListening(true);
				return true;
			}
			if (button == 1) {
				b.set(-1);
				b.setListening(false);
				return true;
			}
		}
		if (s instanceof dark.noti.client.features.settings.ModeSetting m && button == 0) {
			m.next();
			return true;
		}
		if (s instanceof dark.noti.client.features.settings.StringSetting st && button == 0) {
			clearStringListening();
			st.setListening(true);
			// Single-char fields (e.g. CMDPrefix): clear so the next key replaces.
			if (st.maxLength() == 1) {
				st.set("");
			}
			return true;
		}
		if (s instanceof ActionSetting a) {
			if (button == 0 || button == 1) {
				a.run();
				return true;
			}
		}
		return false;
	}

	public void mouseReleased(int button) {
		if (button == 0) {
			if (dragging) {
				persistLayout();
			}
			dragging = false;
			sliding = null;
			colorDrag = null;
		}
	}

	public void mouseDragged(double mx, double my, int button) {
		if (dragging && button == 0) {
			x = (int) mx - dragOx;
			y = (int) my - dragOy;
		}
		if (sliding != null && button == 0) {
			slide(sliding, mx);
		}
		if (colorDrag != null && button == 0) {
			switch (colorPart) {
				case 0 -> applySb(colorDrag, mx, my);
				case 1 -> applyHue(colorDrag, mx);
				default -> applyAlpha(colorDrag, mx);
			}
		}
	}

	public boolean charTyped(char c) {
		if (saveNameFocused && savingColor != null) {
			if (c >= 32 && c != 127 && saveName.length() < 32) {
				saveName += c;
			}
			return true;
		}
		for (Entry e : entries) {
			for (Setting<?> s : e.module.getSettings()) {
				if (s instanceof dark.noti.client.features.settings.StringSetting st && st.isListening()) {
					if (c >= 32 && c != 127) {
						String current = st.get();
						if (current.length() < st.maxLength()) {
							st.set(current + c);
						} else if (st.maxLength() == 1) {
							// Replace instead of silently ignoring (CMDPrefix, etc.).
							st.set(String.valueOf(c));
						}
					}
					return true;
				}
			}
		}
		if (!searchFocus || category != Category.CLIENT || c < 32 || c == 127) {
			return false;
		}
		ClickGuiScreen.setSearch(ClickGuiScreen.search() + c);
		return true;
	}

	public boolean keyPressed(int key) {
		if (saveNameFocused && savingColor != null) {
			if (key == GLFW.GLFW_KEY_ESCAPE) {
				savingColor = null;
				saveName = "";
				saveNameFocused = false;
				return true;
			}
			if (key == GLFW.GLFW_KEY_BACKSPACE && !saveName.isEmpty()) {
				saveName = saveName.substring(0, saveName.length() - 1);
				return true;
			}
			if (key == GLFW.GLFW_KEY_ENTER) {
				commitSavedColor();
				return true;
			}
			return true;
		}
		for (Entry e : entries) {
			for (Setting<?> s : e.module.getSettings()) {
				if (s instanceof BindSetting b && b.isListening()) {
					if (key == GLFW.GLFW_KEY_ESCAPE
						|| key == GLFW.GLFW_KEY_BACKSPACE
						|| key == GLFW.GLFW_KEY_DELETE) {
						b.set(-1);
						b.setListening(false);
						return true;
					}
					b.set(key);
					b.setListening(false);
					ModuleManager.get().consumeKeyEdge(key);
					return true;
				}
				if (s instanceof dark.noti.client.features.settings.StringSetting st && st.isListening()) {
					if (key == GLFW.GLFW_KEY_ESCAPE) {
						st.setListening(false);
						return true;
					}
					if (key == GLFW.GLFW_KEY_BACKSPACE) {
						String current = st.get();
						if (!current.isEmpty()) {
							st.set(current.substring(0, current.length() - 1));
						}
						return true;
					}
					if (key == GLFW.GLFW_KEY_ENTER) {
						st.setListening(false);
						return true;
					}
				}
			}
		}
		if (!searchFocus || category != Category.CLIENT) {
			return false;
		}
		if (key == GLFW.GLFW_KEY_BACKSPACE) {
			String q = ClickGuiScreen.search();
			if (!q.isEmpty()) {
				ClickGuiScreen.setSearch(q.substring(0, q.length() - 1));
			}
			return true;
		}
		if (key == GLFW.GLFW_KEY_ESCAPE) {
			searchFocus = false;
			return true;
		}
		return false;
	}

	private void slide(NumberSetting n, double mx) {
		double pct = (mx - (x + 1)) / (double) (WIDTH - 2);
		pct = Math.max(0, Math.min(1, pct));
		n.setValue(n.getMin() + (n.getMax() - n.getMin()) * pct);
	}

	private void clearListening() {
		for (Entry e : entries) {
			for (Setting<?> s : e.module.getSettings()) {
				if (s instanceof BindSetting b) {
					b.setListening(false);
				}
			}
		}
	}

	private void clearStringListening() {
		for (Entry e : entries) {
			for (Setting<?> s : e.module.getSettings()) {
				if (s instanceof dark.noti.client.features.settings.StringSetting st) {
					st.setListening(false);
				}
			}
		}
	}

	private List<Entry> filter(String query) {
		if (query == null || query.isBlank()) {
			return entries;
		}
		String q = query.toLowerCase(Locale.ROOT);
		List<Entry> out = new ArrayList<>();
		for (Entry e : entries) {
			if (e.module.getName().toLowerCase(Locale.ROOT).contains(q)) {
				out.add(e);
			}
		}
		return out;
	}

	private int bodyContentHeight(List<Entry> visible) {
		int h = category == Category.CLIENT ? ROW : 0;
		for (Entry e : visible) {
			activeModule = e.module;
			h += ROW;
			if (e.open) {
				h += settingsContentHeight(e);
			}
		}
		activeModule = null;
		return Math.max(h, ROW);
	}

	private int bodyViewHeight(int contentH) {
		return Math.min(contentH, maxBodyView());
	}

	private int maxBodyView() {
		Minecraft mc = Minecraft.getInstance();
		float scale = 1f;
		ClickGuiModule gui = ModuleManager.get().get(ClickGuiModule.class);
		if (gui != null) {
			scale = gui.getGuiScale();
		}
		int screenH = mc.getWindow().getGuiScaledHeight();
		int virtualH = scale <= 0.01f ? screenH : Math.max(ROW * 8, Math.round(screenH / scale));
		return Math.max(ROW * 8, virtualH - y - HEADER - BODY_SCREEN_PAD);
	}

	private int settingsContentHeight(Entry e) {
		activeModule = e.module;
		int h = 0;
		for (Setting<?> s : e.module.getSettings()) {
			if (isSectionChild(e.module, s) || s.isHidden()) {
				continue;
			}
			h += settingHeight(s);
		}
		return h;
	}

	private void clampBodyScroll(int contentH, int viewH) {
		int max = Math.max(0, contentH - viewH);
		if (bodyScroll < 0) {
			bodyScroll = 0;
		} else if (bodyScroll > max) {
			bodyScroll = max;
		}
	}

	private static boolean overlapsView(int y, int h, int viewTop, int viewH) {
		return y + h > viewTop && y < viewTop + viewH;
	}

	private static boolean rowVisible(int y, int viewTop, int viewH) {
		return overlapsView(y, ROW, viewTop, viewH);
	}

	public boolean mouseScrolled(double mx, double my, double amount, String query) {
		if (!open || amount == 0) {
			return false;
		}
		List<Entry> visible = filter(query);
		int contentH = bodyContentHeight(visible);
		int viewH = bodyViewHeight(contentH);
		if (contentH <= viewH) {
			return false;
		}
		int bodyTop = y + HEADER;
		if (!hit(mx, my, x, bodyTop, WIDTH, viewH) && !hit(mx, my, x, y, WIDTH, HEADER + viewH)) {
			return false;
		}
		clampBodyScroll(contentH, viewH);
		bodyScroll -= (int) Math.round(amount * ROW * 2);
		clampBodyScroll(contentH, viewH);
		return true;
	}

	private static boolean isSectionChild(Module module, Setting<?> setting) {
		if (setting instanceof SectionSetting) {
			return false;
		}
		for (Setting<?> s : module.getSettings()) {
			if (s instanceof SectionSetting section && sectionContains(section, setting)) {
				return true;
			}
		}
		return false;
	}

	private static boolean sectionContains(SectionSetting section, Setting<?> setting) {
		if (section.getEnableSetting() == setting) {
			return true;
		}
		for (Setting<?> nested : section.getSettings()) {
			if (nested == setting) {
				return true;
			}
			if (nested instanceof SectionSetting inner && sectionContains(inner, setting)) {
				return true;
			}
		}
		return false;
	}

	private void commitSavedColor() {
		if (savingColor == null) {
			return;
		}
		String name = saveName.trim();
		if (name.isEmpty()) {
			saveNameFocused = true;
			return;
		}
		dark.noti.client.features.modules.client.ColorsModule.saveNamedColor(name, savingColor.argb());
		savingColor = null;
		saveName = "";
		saveNameFocused = false;
	}

	private static boolean blink() {
		return (System.currentTimeMillis() / 400) % 2 == 0;
	}

	private static boolean isGearEnabled() {
		ClickGuiModule gui = ModuleManager.get().get(ClickGuiModule.class);
		return gui != null && gui.isGearEnabled();
	}

	private static boolean hit(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx <= x + w && my >= y && my <= y + h;
	}

	private static final class Entry {
		final Module module;
		boolean open;

		Entry(Module module) {
			this.module = module;
		}
	}
}
