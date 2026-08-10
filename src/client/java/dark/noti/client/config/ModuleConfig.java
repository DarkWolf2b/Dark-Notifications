package dark.noti.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dark.noti.client.features.gui.Panel;
import dark.noti.client.manager.Category;
import dark.noti.client.manager.Module;
import dark.noti.client.manager.ModuleManager;
import dark.noti.client.features.settings.BindSetting;
import dark.noti.client.features.settings.BoolSetting;
import dark.noti.client.features.settings.ColorSetting;
import dark.noti.client.features.settings.ModeSetting;
import dark.noti.client.features.settings.NumberSetting;
import dark.noti.client.features.settings.SectionSetting;
import dark.noti.client.features.settings.Setting;
import dark.noti.client.features.settings.StringSetting;
import dark.noti.client.features.modules.client.ClickGuiModule;
import dark.noti.client.features.modules.client.ColorsModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;

/**
 * Auto-persists module toggles/settings. Loads by setting name so updates keep old values
 * and only drop keys that no longer exist.
 */
public final class ModuleConfig {
	private static final Path FILE = Paths.get("config/dark-noti/settings.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static boolean loading;
	private static boolean dirty;
	private static int saveCooldown;

	private ModuleConfig() {
	}

	public static boolean isLoading() {
		return loading;
	}

	public static void markDirty() {
		if (!loading) {
			dirty = true;
		}
	}

	public static void load() {
		loadFrom(FILE);
	}

	public static void save() {
		saveTo(FILE);
	}

	public static Path configDir() {
		return FILE.getParent();
	}

	public static Path pathFor(String name) {
		String safe = sanitize(name);
		if (safe.isEmpty() || "default".equalsIgnoreCase(safe) || "settings".equalsIgnoreCase(safe)) {
			return FILE;
		}
		return configDir().resolve(safe + ".json");
	}

	public static boolean saveNamed(String name) {
		try {
			saveTo(pathFor(name));
			if (!isDefaultName(name)) {
				// Keep active auto-save in sync after exporting a named profile.
				saveTo(FILE);
			}
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public static boolean loadNamed(String name) {
		Path path = pathFor(name);
		if (!Files.exists(path)) {
			return false;
		}
		loadFrom(path);
		if (!isDefaultName(name)) {
			saveTo(FILE);
		}
		return true;
	}

	public static boolean deleteNamed(String name) {
		if (isDefaultName(name)) {
			return false;
		}
		try {
			return Files.deleteIfExists(pathFor(name));
		} catch (IOException e) {
			return false;
		}
	}

	public static boolean isDefaultName(String name) {
		String safe = sanitize(name);
		return safe.isEmpty() || "default".equalsIgnoreCase(safe) || "settings".equalsIgnoreCase(safe);
	}

	private static String sanitize(String name) {
		if (name == null) {
			return "";
		}
		return name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
	}

	public static void loadFrom(Path file) {
		loading = true;
		try {
			if (!Files.exists(file)) {
				return;
			}
			String text = Files.readString(file, StandardCharsets.UTF_8);
			if (text.isBlank()) {
				return;
			}
			JsonObject root = JsonParser.parseString(text).getAsJsonObject();
			if (root.has("panels") && root.get("panels").isJsonObject()) {
				Panel.loadLayout(root.getAsJsonObject("panels"));
			}
			if (!root.has("modules") || !root.get("modules").isJsonObject()) {
				return;
			}
			JsonObject modules = root.getAsJsonObject("modules");
			for (Module module : ModuleManager.get().getAll()) {
				if (!modules.has(module.getName())) {
					continue;
				}
				JsonElement el = modules.get(module.getName());
				if (!el.isJsonObject()) {
					continue;
				}
				applyModule(module, el.getAsJsonObject());
			}
		} catch (Exception ignored) {
		} finally {
			loading = false;
			dirty = false;
		}
	}

	public static void saveTo(Path file) {
		try {
			Files.createDirectories(file.getParent());
			JsonObject root = new JsonObject();
			root.addProperty("version", 1);
			root.add("panels", Panel.saveLayout());

			JsonObject modules = new JsonObject();
			JsonObject previous = readExistingModules(file);
			for (Map.Entry<String, JsonElement> entry : previous.entrySet()) {
				modules.add(entry.getKey(), entry.getValue());
			}

			for (Module module : ModuleManager.get().getAll()) {
				modules.add(module.getName(), writeModule(module));
			}
			root.add("modules", modules);

			Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
			if (file.equals(FILE)) {
				dirty = false;
			}
		} catch (IOException ignored) {
		}
	}

	public static void tick() {
		if (!dirty) {
			return;
		}
		if (++saveCooldown < 40) {
			return;
		}
		saveCooldown = 0;
		save();
	}

	private static JsonObject readExistingModules() {
		return readExistingModules(FILE);
	}

	private static JsonObject readExistingModules(Path file) {
		try {
			if (!Files.exists(file)) {
				return new JsonObject();
			}
			JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			if (root.has("modules") && root.get("modules").isJsonObject()) {
				return root.getAsJsonObject("modules").deepCopy();
			}
		} catch (Exception ignored) {
		}
		return new JsonObject();
	}

	private static void applyModule(Module module, JsonObject data) {
				if (data.has("enabled") && !(module instanceof ColorsModule)) {
			module.setEnabled(data.get("enabled").getAsBoolean(), false);
		}
		if (module instanceof ClickGuiModule gui && data.has("guiScale")) {
			gui.setGuiScale(data.get("guiScale").getAsFloat());
		}
		if (data.has("keybind")) {
			int key = data.get("keybind").getAsInt();
			module.setKeybind(key);
			for (Setting<?> setting : module.getSettings()) {
				if (setting instanceof BindSetting bind && "Keybind".equalsIgnoreCase(bind.getName())) {
					bind.set(key);
				}
			}
			if (module instanceof ClickGuiModule gui) {
				gui.keybindSetting().set(key);
			}
		}
		if (!data.has("settings") || !data.get("settings").isJsonObject()) {
			return;
		}
		JsonObject settings = data.getAsJsonObject("settings");
		for (Setting<?> setting : module.getSettings()) {
			if (!settings.has(setting.getName())) {
				continue;
			}
			try {
				readSetting(setting, settings.get(setting.getName()));
			} catch (Exception ignored) {
			}
		}
		// Nested section children are also in module.getSettings() via add().
		for (Setting<?> setting : module.getSettings()) {
			if (!(setting instanceof SectionSetting section)) {
				continue;
			}
			for (Setting<?> nested : section.getSettings()) {
				if (!settings.has(nested.getName())) {
					continue;
				}
				try {
					readSetting(nested, settings.get(nested.getName()));
				} catch (Exception ignored) {
				}
			}
		}
	}

	private static JsonObject writeModule(Module module) {
		JsonObject data = new JsonObject();
		data.addProperty("enabled", module.isEnabled());
		data.addProperty("keybind", module.getKeybind());
		if (module instanceof ClickGuiModule gui) {
			data.addProperty("keybind", gui.getOpenKey());
			data.addProperty("guiScale", gui.getGuiScale());
		}
		JsonObject settings = new JsonObject();
		for (Setting<?> setting : module.getSettings()) {
			writeSetting(settings, setting);
			if (setting instanceof SectionSetting section) {
				for (Setting<?> nested : section.getSettings()) {
					writeSetting(settings, nested);
				}
			}
		}
		data.add("settings", settings);
		return data;
	}

	private static void writeSetting(JsonObject out, Setting<?> setting) {
		if (out.has(setting.getName())) {
			return;
		}
		switch (setting) {
			case BoolSetting b -> out.addProperty(setting.getName(), b.getValue());
			case NumberSetting n -> out.addProperty(setting.getName(), n.get());
			case ModeSetting m -> out.addProperty(setting.getName(), m.get());
			case StringSetting s -> out.addProperty(setting.getName(), s.get());
			case BindSetting b -> out.addProperty(setting.getName(), b.getKey());
			case SectionSetting s -> out.addProperty(setting.getName(), s.isExpanded());
			case ColorSetting c -> {
				JsonObject color = new JsonObject();
				color.addProperty("argb", c.customArgb());
				color.addProperty("link", c.linkMode().name());
				color.addProperty("savedIndex", c.savedIndex());
				out.add(setting.getName(), color);
			}
			default -> {
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static void readSetting(Setting<?> setting, JsonElement el) {
		switch (setting) {
			case BoolSetting b -> {
				if (el.isJsonPrimitive()) {
					b.set(el.getAsBoolean());
				}
			}
			case NumberSetting n -> {
				if (el.isJsonPrimitive()) {
					n.setValue(el.getAsDouble());
				}
			}
			case ModeSetting m -> {
				if (el.isJsonPrimitive()) {
					String mode = el.getAsString();
					if (m.options().stream().anyMatch(o -> o.equalsIgnoreCase(mode))) {
						m.set(m.options().stream()
							.filter(o -> o.equalsIgnoreCase(mode))
							.findFirst()
							.orElse(m.get()));
					}
				}
			}
			case StringSetting s -> {
				if (el.isJsonPrimitive()) {
					s.set(el.getAsString());
				}
			}
			case BindSetting b -> {
				if (el.isJsonPrimitive()) {
					b.set(el.getAsInt());
				}
			}
			case SectionSetting s -> {
				if (el.isJsonPrimitive()) {
					s.setExpanded(el.getAsBoolean());
				}
			}
			case ColorSetting c -> {
				if (el.isJsonObject()) {
					JsonObject obj = el.getAsJsonObject();
					if (obj.has("argb")) {
						c.loadArgb(obj.get("argb").getAsInt());
					}
					if (obj.has("link")) {
						try {
							c.setLinkMode(ColorSetting.LinkMode.valueOf(obj.get("link").getAsString().toUpperCase(Locale.ROOT)));
						} catch (IllegalArgumentException ignored) {
						}
					}
					if (obj.has("savedIndex")) {
						c.setSavedIndex(obj.get("savedIndex").getAsInt());
					}
				} else if (el.isJsonPrimitive()) {
					c.loadArgb(el.getAsInt());
				}
			}
			default -> {
			}
		}
	}
}
