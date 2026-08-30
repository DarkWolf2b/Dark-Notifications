package dark.noti.client.features.commands;

import dark.noti.client.features.gui.ClickGuiScreen;
import dark.noti.client.manager.Module;
import dark.noti.client.manager.ModuleManager;
import dark.noti.client.features.settings.ColorSetting;
import dark.noti.client.features.modules.client.ClickGuiModule;
import dark.noti.client.features.modules.client.ColorsModule;
import dark.noti.client.features.modules.notifications.TotemPopNotifierModule;
import dark.noti.client.features.modules.notifications.VisualRangeModule;
import dark.noti.client.util.SavedColors;
import dark.noti.client.util.SocialLists;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class ClientCommandHandler {
	private static final List<Command> COMMANDS = List.of(
		new Command("help", "Shows all client commands.", ClientCommandHandler::help),
		new Command("gui", "GUI: position reset | size <n> | keybind <key>", ClientCommandHandler::gui),
		new Command("clickgui", "Alias of .gui", ClientCommandHandler::gui),
		new Command("module", "Module: <name> settingreset.", ClientCommandHandler::module),
		new Command("colors", "Alias of .color", ClientCommandHandler::color),
		new Command("color", "color: primary|friend|gui <reset|hex <code>> | save | delete", ClientCommandHandler::color),
		new Command("visualrange", "VisualRange: ignorefakeplayer <true/false>", ClientCommandHandler::visualrange),
		new Command("totempopnotifier", "TotemPopNotifier: player <ign> count reset", ClientCommandHandler::totempopnotifier),
		new Command("tpn", "Alias of .totempopnotifier", ClientCommandHandler::totempopnotifier),
		new Command("friend", "Friends: add|delete|list <ign>", ClientCommandHandler::friend),
		new Command("friends", "Alias of .friend", ClientCommandHandler::friend)
	);

	private ClientCommandHandler() {
	}

	public static String prefix() {
		ClickGuiModule gui = ModuleManager.get().get(ClickGuiModule.class);
		if (gui != null) {
			return gui.getCmdPrefix();
		}
		return ".";
	}

	/** Whether chat input should show client-command suggestions / key handling. */
	public static boolean isCommandInput(String message) {
		if (message == null) {
			return false;
		}
		String prefix = prefix();
		if (!prefix.isEmpty()) {
			return message.startsWith(prefix);
		}
		String trimmed = message.trim();
		if (trimmed.isEmpty()) {
			return false;
		}
		int space = trimmed.indexOf(' ');
		String first = (space < 0 ? trimmed : trimmed.substring(0, space)).toLowerCase(Locale.ROOT);
		for (String cmd : commandNames()) {
			if (cmd.startsWith(first)) {
				return true;
			}
		}
		return false;
	}

	private static String commandBody(String message) {
		String prefix = prefix();
		if (!prefix.isEmpty() && message.startsWith(prefix)) {
			return message.substring(prefix.length());
		}
		return message;
	}

	public static boolean tryHandle(String message) {
		if (message == null) {
			return false;
		}
		String prefix = prefix();
		if (!prefix.isEmpty()) {
			if (!message.startsWith(prefix)) {
				return false;
			}
		} else if (!isCommandInput(message)) {
			return false;
		}
		String body = commandBody(message).trim();
		if (body.isEmpty()) {
			return !prefix.isEmpty();
		}
		String[] parts = body.split("\\s+", 2);
		String name = parts[0].toLowerCase(Locale.ROOT);
		String args = parts.length > 1 ? parts[1].trim() : "";
		for (Command command : COMMANDS) {
			if (command.name().equals(name)) {
				command.handler().execute(args);
				return true;
			}
		}
		send("Unknown command. Use " + prefix + "help");
		return true;
	}

	public static List<Command> commands() {
		return COMMANDS;
	}

	public static List<String> commandNames() {
		return COMMANDS.stream().map(Command::name).toList();
	}

	private static void help(String args) {
		String prefix = prefix();
		send("Client commands:");
		for (Command command : COMMANDS) {
			send(prefix + command.name() + " - " + command.description());
		}
	}

	private static void gui(String args) {
		String[] parts = args.isEmpty() ? new String[0] : args.split("\\s+");
		if (parts.length >= 2 && parts[0].equalsIgnoreCase("position") && parts[1].equalsIgnoreCase("reset")) {
			ClickGuiScreen.resetLayout();
			send("GUI position reset");
			return;
		}
		if (parts.length >= 2 && parts[0].equalsIgnoreCase("size")) {
			try {
				float size = Float.parseFloat(parts[1]);
				ClickGuiModule gui = ModuleManager.get().get(ClickGuiModule.class);
				if (gui == null) {
					send("ClickGUI module not available.");
					return;
				}
				gui.setGuiScale(size);
				send("ClickGUI size set to " + gui.getGuiScale() + ".");
			} catch (NumberFormatException e) {
				send("Usage: " + prefix() + "clickgui size <0.5-2.0>");
			}
			return;
		}
		if (parts.length >= 2 && parts[0].equalsIgnoreCase("keybind")) {
			String keyName = String.join(" ", Arrays.copyOfRange(parts, 1, parts.length));
			int key = parseKeyName(keyName);
			if (key == Integer.MIN_VALUE) {
				send("Unknown key: " + keyName);
				return;
			}
			ClickGuiModule gui = ModuleManager.get().get(ClickGuiModule.class);
			if (gui == null) {
				send("ClickGUI module not available.");
				return;
			}
			gui.keybindSetting().set(key);
			gui.setKeybind(key);
			ModuleManager.get().consumeKeyEdge(key);
			send("ClickGUI keybind set to " + gui.keybindSetting().display() + ".");
			return;
		}
		send("Usage: " + prefix() + "clickgui <position reset|size <n>|keybind <key>>");
	}

	private static void module(String args) {
		String[] parts = args.isEmpty() ? new String[0] : args.split("\\s+");
		if (parts.length < 2) {
			send("Usage: " + prefix() + "module <name> settingreset");
			return;
		}
		String action = parts[parts.length - 1];
		String name = String.join(" ", Arrays.copyOf(parts, parts.length - 1));
		if (!action.equalsIgnoreCase("settingreset")) {
			send("Usage: " + prefix() + "module <name> settingreset");
			return;
		}
		Module module = ModuleManager.get().byName(name);
		if (module == null) {
			send("Unknown module: " + name);
			return;
		}
		module.resetSettings();
		send("Reset settings for " + module.getName() + ".");
	}

	private static void color(String args) {
		String[] parts = args.isEmpty() ? new String[0] : args.split("\\s+");
		if (parts.length < 1) {
			send("Usage: " + prefix() + "color <primary|friend|gui|save|delete> ...");
			return;
		}
		String target = parts[0].toLowerCase(Locale.ROOT);

		if (target.equals("delete")) {
			if (parts.length < 2) {
				send("Usage: " + prefix() + "color delete <name>");
				return;
			}
			String name = String.join(" ", Arrays.copyOfRange(parts, 1, parts.length));
			ColorsModule colors = ModuleManager.get().get(ColorsModule.class);
			if (colors == null) {
				send("Colors module not available.");
				return;
			}
			if (colors.deleteSavedColor(name)) {
				send("Deleted saved color \"" + name + "\".");
			} else {
				send("No saved color named \"" + name + "\".");
			}
			return;
		}

		if (parts.length >= 2 && parts[1].equalsIgnoreCase("reset")) {
			switch (target) {
				case "primary" -> {
					ColorsModule colors = ModuleManager.get().get(ColorsModule.class);
					if (colors == null) {
						send("Colors module not available.");
						return;
					}
					colors.primarySetting().reset();
					send("Primary color reset.");
				}
				case "friend" -> {
					ColorsModule colors = ModuleManager.get().get(ColorsModule.class);
					if (colors == null) {
						send("Colors module not available.");
						return;
					}
					colors.friendSetting().reset();
					send("Friend color reset.");
				}
				case "gui" -> {
					ClickGuiModule gui = ModuleManager.get().get(ClickGuiModule.class);
					if (gui == null) {
						send("ClickGUI module not available.");
						return;
					}
					gui.guiColorSetting().reset();
					send("GUI color reset.");
				}
				default -> send("Usage: " + prefix() + "color <primary|friend|gui> reset");
			}
			return;
		}

		if (parts.length < 2) {
			send("Usage: " + prefix() + "color <primary|friend|gui|save|delete> ...");
			return;
		}

		try {
			if (target.equals("save")) {
				String hex = parts[1];
				String name = parts.length >= 3 ? String.join(" ", Arrays.copyOfRange(parts, 2, parts.length)) : "Color";
				int color = parseHex(hex);
				ColorsModule.saveNamedColor(name, color);
				send("Saved color \"" + name + "\" as " + hex + ".");
				return;
			}

			String hex;
			if (parts.length >= 3 && parts[1].equalsIgnoreCase("hex")) {
				hex = parts[2];
			} else {
				hex = parts[1];
			}
			int color = parseHex(hex);

			switch (target) {
				case "primary" -> {
					ColorsModule colors = ModuleManager.get().get(ColorsModule.class);
					if (colors == null) {
						send("Colors module not available.");
						return;
					}
					ColorSetting setting = colors.primarySetting();
					setting.setLinkMode(ColorSetting.LinkMode.CUSTOM);
					setting.fromArgb(color);
					setting.set(color);
					send("Set primary color to " + hex + ".");
				}
				case "friend" -> {
					ColorsModule colors = ModuleManager.get().get(ColorsModule.class);
					if (colors == null) {
						send("Colors module not available.");
						return;
					}
					ColorSetting setting = colors.friendSetting();
					setting.setLinkMode(ColorSetting.LinkMode.CUSTOM);
					setting.fromArgb(color);
					setting.set(color);
					send("Set friend color to " + hex + ".");
				}
				case "gui" -> {
					ClickGuiModule gui = ModuleManager.get().get(ClickGuiModule.class);
					if (gui == null) {
						send("ClickGUI module not available.");
						return;
					}
					ColorSetting setting = gui.guiColorSetting();
					setting.setLinkMode(ColorSetting.LinkMode.CUSTOM);
					setting.fromArgb(color);
					setting.set(color);
					send("Set GUI color to " + hex + ".");
				}
				default -> send("Usage: " + prefix() + "color <primary|friend|gui> <reset|hex <code>>");
			}
		} catch (NumberFormatException e) {
			send("Invalid hex code.");
		}
	}

	private static void visualrange(String args) {
		String[] parts = args.isEmpty() ? new String[0] : args.split("\\s+");
		if (parts.length < 2) {
			send("Usage: " + prefix() + "visualrange ignorefakeplayer <true/false>");
			return;
		}
		String target = parts[0].toLowerCase(Locale.ROOT);
		String value = parts[1].toLowerCase(Locale.ROOT);

		if (!target.equals("ignorefakeplayer")) {
			send("Usage: " + prefix() + "visualrange ignorefakeplayer <true/false>");
			return;
		}

		if (!value.equals("true") && !value.equals("false")) {
			send("Usage: " + prefix() + "visualrange ignorefakeplayer <true/false>");
			return;
		}

		boolean boolValue = value.equals("true");
		VisualRangeModule visualRangeModule = ModuleManager.get().get(VisualRangeModule.class);
		if (visualRangeModule == null) {
			send("VisualRange module not available.");
			return;
		}

		visualRangeModule.setIgnoreFakePlayer(boolValue);
		send("VisualRange ignoreFakePlayer set to " + boolValue + ".");
	}

	private static void totempopnotifier(String args) {
		String[] parts = args.isEmpty() ? new String[0] : args.split("\\s+");
		if (parts.length < 4
			|| !parts[0].equalsIgnoreCase("player")
			|| !parts[parts.length - 2].equalsIgnoreCase("count")
			|| !parts[parts.length - 1].equalsIgnoreCase("reset")) {
			send("Usage: " + prefix() + "totempopnotifier player <ign> count reset");
			return;
		}
		String ign = String.join(" ", Arrays.copyOfRange(parts, 1, parts.length - 2));
		TotemPopNotifierModule module = ModuleManager.get().get(TotemPopNotifierModule.class);
		if (module == null) {
			send("TotemPopNotifier module not available.");
			return;
		}
		if (module.resetPlayerCount(ign)) {
			send("Reset totem pop count for " + ign + ".");
		} else {
			send("No totem pop count found for " + ign + ".");
		}
	}

	private static void friend(String args) {
		String[] parts = args.isEmpty() ? new String[0] : args.split("\\s+", 2);
		if (parts.length == 0 || parts[0].isBlank()) {
			send("Usage: " + prefix() + "friend <add|delete|list> [ign]");
			return;
		}
		String action = parts[0].toLowerCase(Locale.ROOT);
		String ign = parts.length > 1 ? parts[1].trim() : "";
		switch (action) {
			case "add" -> {
				if (ign.isEmpty()) {
					send("Usage: " + prefix() + "friend add <ign>");
					return;
				}
				SocialLists.AddResult result = SocialLists.addFriend(ign);
				send(result.message());
			}
			case "delete", "del", "remove" -> {
				if (ign.isEmpty()) {
					send("Usage: " + prefix() + "friend delete <ign>");
					return;
				}
				SocialLists.RemoveResult result = SocialLists.removeFriend(ign);
				send(result.message());
			}
			case "list" -> {
				List<SocialLists.FriendEntry> entries = SocialLists.friendEntries();
				if (entries.isEmpty()) {
					send("No friends.");
					return;
				}
				send("Friends (" + entries.size() + "):");
				for (SocialLists.FriendEntry entry : entries) {
					send("- " + entry.name() + " [" + entry.uuid() + "]");
				}
			}
			default -> send("Usage: " + prefix() + "friend <add|delete|list> [ign]");
		}
	}

	private static int parseHex(String hex) {
		if (hex.startsWith("#")) {
			hex = hex.substring(1);
		}
		if (hex.length() == 6) {
			return 0xFF000000 | Integer.parseInt(hex, 16);
		}
		if (hex.length() == 8) {
			return (int) Long.parseLong(hex, 16);
		}
		throw new NumberFormatException("Invalid hex length");
	}

	/** Returns GLFW key code, or Integer.MIN_VALUE if unknown. */
	private static int parseKeyName(String raw) {
		if (raw == null || raw.isBlank()) {
			return Integer.MIN_VALUE;
		}
		String name = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
		return switch (name) {
			case "rshift", "right_shift", "rightshift" -> org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT;
			case "lshift", "left_shift", "leftshift", "shift" -> org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT;
			case "rctrl", "right_ctrl", "right_control", "rightcontrol" -> org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL;
			case "lctrl", "left_ctrl", "left_control", "leftcontrol", "ctrl", "control" -> org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL;
			case "ralt", "right_alt", "rightalt" -> org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_ALT;
			case "lalt", "left_alt", "leftalt", "alt" -> org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT;
			case "space" -> org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;
			case "tab" -> org.lwjgl.glfw.GLFW.GLFW_KEY_TAB;
			case "enter", "return" -> org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
			case "backspace" -> org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE;
			case "delete", "del" -> org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE;
			case "insert", "ins" -> org.lwjgl.glfw.GLFW.GLFW_KEY_INSERT;
			case "home" -> org.lwjgl.glfw.GLFW.GLFW_KEY_HOME;
			case "end" -> org.lwjgl.glfw.GLFW.GLFW_KEY_END;
			case "page_up", "pageup", "pgup" -> org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP;
			case "page_down", "pagedown", "pgdn" -> org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN;
			case "up" -> org.lwjgl.glfw.GLFW.GLFW_KEY_UP;
			case "down" -> org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN;
			case "left" -> org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT;
			case "right" -> org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT;
			case "caps_lock", "capslock" -> org.lwjgl.glfw.GLFW.GLFW_KEY_CAPS_LOCK;
			case "none", "unbind" -> -1;
			default -> {
				if (name.length() == 1) {
					char c = name.charAt(0);
					if (c >= 'a' && c <= 'z') {
						yield org.lwjgl.glfw.GLFW.GLFW_KEY_A + (c - 'a');
					}
					if (c >= '0' && c <= '9') {
						yield org.lwjgl.glfw.GLFW.GLFW_KEY_0 + (c - '0');
					}
				}
				if (name.startsWith("f") && name.length() <= 3) {
					try {
						int n = Integer.parseInt(name.substring(1));
						if (n >= 1 && n <= 25) {
							yield org.lwjgl.glfw.GLFW.GLFW_KEY_F1 + (n - 1);
						}
					} catch (NumberFormatException ignored) {
					}
				}
				yield Integer.MIN_VALUE;
			}
		};
	}

	private static void send(String message) {
		Minecraft client = Minecraft.getInstance();
		if (client.gui != null) {
			client.gui.hud.getChat().addClientSystemMessage(Component.literal(message));
		}
	}

	public record Command(String name, String description, Handler handler) {
	}

	@FunctionalInterface
	public interface Handler {
		void execute(String args);
	}
}
