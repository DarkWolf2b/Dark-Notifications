package dark.noti.client.manager;

import dark.noti.client.config.ModuleConfig;
import dark.noti.client.features.gui.ClickGuiScreen;
import dark.noti.client.features.modules.client.ClickGuiModule;
import dark.noti.client.features.modules.notifications.ModuleToggleModule;
import dark.noti.client.features.settings.BindSetting;
import dark.noti.client.features.settings.SectionSetting;
import dark.noti.client.features.settings.Setting;
import dark.noti.client.util.SocialLists;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ModuleManager {
	private static final ModuleManager INSTANCE = new ModuleManager();

	private final List<Module> modules = new ArrayList<>();
	private final Map<Category, List<Module>> byCategory = new EnumMap<>(Category.class);
	private final boolean[] wasDown = new boolean[GLFW.GLFW_KEY_LAST + 1];

	private ModuleManager() {
		for (Category category : Category.values()) {
			byCategory.put(category, new ArrayList<>());
		}
	}

	public static ModuleManager get() {
		return INSTANCE;
	}

	/** Prevents a just-bound key from immediately toggling the GUI while still held. */
	public void consumeKeyEdge(int key) {
		if (key > 0 && key <= GLFW.GLFW_KEY_LAST) {
			wasDown[key] = true;
		}
	}

	public void register(Module module) {
		modules.add(module);
		byCategory.get(module.getCategory()).add(module);
	}

	public List<Module> all() {
		return Collections.unmodifiableList(modules);
	}

	public List<Module> getAll() {
		return all();
	}

	public List<Module> of(Category category) {
		return Collections.unmodifiableList(byCategory.get(category));
	}

	@SuppressWarnings("unchecked")
	public <T extends Module> T get(Class<T> type) {
		for (Module module : modules) {
			if (type.isInstance(module)) {
				return (T) module;
			}
		}
		return null;
	}

	public Module byName(String name) {
		for (Module module : modules) {
			if (module.getName().equalsIgnoreCase(name)) {
				return module;
			}
		}
		return null;
	}

	public void onModuleToggled(Module module) {
		ModuleToggleModule.onModuleToggled(module);
	}

	public void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}

		long win = mc.getWindow().handle();
		ClickGuiModule gui = get(ClickGuiModule.class);
		if (gui != null) {
			int key = gui.getOpenKey();
			if (key > 0 && key <= GLFW.GLFW_KEY_LAST) {
				boolean down = GLFW.glfwGetKey(win, key) == GLFW.GLFW_PRESS;
				if (down && !wasDown[key] && !isAnyBindListening()) {
					if (mc.screen instanceof ClickGuiScreen) {
						mc.setScreen(null);
					} else if (mc.screen == null) {
						mc.setScreen(new ClickGuiScreen());
					}
				}
				wasDown[key] = down;
			}
		}

		for (Module module : modules) {
			if (module.isEnabled()) {
				module.onTick();
			}
		}
		SocialLists.refreshSeenPlayers();
		ModuleConfig.tick();
	}

	private boolean isAnyBindListening() {
		for (Module module : modules) {
			for (Setting<?> setting : module.getSettings()) {
				if (setting instanceof BindSetting bind && bind.isListening()) {
					return true;
				}
				if (setting instanceof SectionSetting section) {
					for (Setting<?> nested : section.getSettings()) {
						if (nested instanceof BindSetting bind && bind.isListening()) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}
}
