package dark.noti.client.features.commands;

import dark.noti.client.manager.ModuleManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ClientCommandSuggestions {
	private static int selectedIndex;
	private static String lastInput = "";

	private static final String[] KEYBIND_SUGGESTIONS = {
		"right_shift", "left_shift", "right_ctrl", "left_ctrl", "right_alt", "left_alt",
		"space", "tab", "enter", "backspace", "delete", "insert", "home", "end",
		"page_up", "page_down", "up", "down", "left", "right", "caps_lock", "none",
		"a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m",
		"n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z",
		"0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
		"f1", "f2", "f3", "f4", "f5", "f6", "f7", "f8", "f9", "f10", "f11", "f12"
	};

	private ClientCommandSuggestions() {
	}

	public static void onInputChanged(String input) {
		if (input == null || !input.equals(lastInput)) {
			selectedIndex = 0;
			lastInput = input == null ? "" : input;
		}
	}

	public static void cycle(int delta) {
		SuggestionResult result = suggest(lastInput);
		if (!result.active()) {
			selectedIndex = 0;
			return;
		}
		selectedIndex = Math.floorMod(selectedIndex + delta, result.options.size());
	}

	public static void select(int index) {
		SuggestionResult result = suggest(lastInput);
		if (!result.active()) {
			selectedIndex = 0;
			return;
		}
		selectedIndex = Math.floorMod(index, result.options.size());
	}

	public static SuggestionResult suggest(String input) {
		List<String> options = new ArrayList<>();
		if (input == null) {
			return inactive();
		}

		String prefix = ClientCommandHandler.prefix();
		if (!input.startsWith(prefix)) {
			return inactive();
		}

		String body = input.substring(prefix.length());
		boolean trailingSpace = !body.isEmpty() && body.charAt(body.length() - 1) == ' ';
		String trimmed = body.trim();
		String[] parts = trimmed.isEmpty() ? new String[0] : trimmed.split("\\s+");

		if (parts.length == 0 || (parts.length == 1 && !trailingSpace)) {
			String partial = parts.length == 0 ? "" : parts[0].toLowerCase(Locale.ROOT);
			for (String cmd : ClientCommandHandler.commandNames()) {
				if (partial.isEmpty() || cmd.startsWith(partial)) {
					options.add(cmd);
				}
			}
			return finish(options, partial, prefix.length());
		}

		String cmd = parts[0].toLowerCase(Locale.ROOT);
		int argStart = prefix.length() + parts[0].length() + 1;

		if (cmd.equals("color") || cmd.equals("colors")) {
			if (parts.length == 1 || (parts.length == 2 && !trailingSpace)) {
				String partial = parts.length == 1 ? "" : parts[1].toLowerCase(Locale.ROOT);
				addMatching(options, partial, "primary", "friend", "gui", "save", "delete");
				return finish(options, partial, argStart);
			}
			if (parts.length >= 2 && "save".equalsIgnoreCase(parts[1])) {
				if (parts.length == 2 || (parts.length == 3 && !trailingSpace)) {
					options.add("<hex>");
					return finish(options, "", rangeStart(prefix, parts, trailingSpace, 2));
				}
				options.add("<name>");
				return finish(options, "", rangeStart(prefix, parts, trailingSpace, 3));
			}
			if (parts.length >= 2 && "delete".equalsIgnoreCase(parts[1])) {
				String partial = parts.length == 2 ? "" : parts[2].toLowerCase(Locale.ROOT);
				for (String name : dark.noti.client.util.SavedColors.names()) {
					if (partial.isEmpty() || name.toLowerCase(Locale.ROOT).startsWith(partial)) {
						options.add(name);
					}
				}
				return finish(options, partial, rangeStart(prefix, parts, trailingSpace, 2));
			}
			if (parts.length == 2 || (parts.length == 3 && !trailingSpace)) {
				String partial = parts.length == 2 ? "" : parts[2].toLowerCase(Locale.ROOT);
				addMatching(options, partial, "reset", "<hex>");
				return finish(options, partial, rangeStart(prefix, parts, trailingSpace, 2));
			}
			options.add("<hex>");
			return finish(options, "", rangeStart(prefix, parts, trailingSpace, 2));
		}

		if (cmd.equals("gui") || cmd.equals("clickgui")) {
			if (parts.length == 1 || (parts.length == 2 && !trailingSpace)) {
				String partial = parts.length == 1 ? "" : parts[1].toLowerCase(Locale.ROOT);
				addMatching(options, partial, "position", "size", "keybind");
				return finish(options, partial, argStart);
			}
			String sub = parts[1].toLowerCase(Locale.ROOT);
			if (sub.equals("position")) {
				String partial = parts.length == 2 ? "" : parts[2].toLowerCase(Locale.ROOT);
				addMatching(options, partial, "reset");
				return finish(options, partial, rangeStart(prefix, parts, trailingSpace, 2));
			}
			if (sub.equals("size")) {
				options.add("<0.5-2.0>");
				return finish(options, "", rangeStart(prefix, parts, trailingSpace, 2));
			}
			if (sub.equals("keybind")) {
				String partial = parts.length == 2 ? "" : parts[2].toLowerCase(Locale.ROOT);
				addMatching(options, partial, KEYBIND_SUGGESTIONS);
				return finish(options, partial, rangeStart(prefix, parts, trailingSpace, 2));
			}
			return inactive();
		}

		if (cmd.equals("module")) {
			if (parts.length == 1 || (parts.length == 2 && !trailingSpace)) {
				String partial = parts.length == 1 ? "" : parts[1].toLowerCase(Locale.ROOT);
				for (dark.noti.client.manager.Module module : ModuleManager.get().getAll()) {
					String name = module.getName();
					if (partial.isEmpty() || name.toLowerCase(Locale.ROOT).startsWith(partial)) {
						options.add(name);
					}
				}
				return finish(options, partial, argStart);
			}
			String partial = trailingSpace ? "" : parts[parts.length - 1].toLowerCase(Locale.ROOT);
			addMatching(options, partial, "settingreset");
			return finish(options, partial, rangeStart(prefix, parts, trailingSpace, parts.length - (trailingSpace ? 0 : 1)));
		}

		if (cmd.equals("visualrange")) {
			if (parts.length == 1 || (parts.length == 2 && !trailingSpace)) {
				String partial = parts.length == 1 ? "" : parts[1].toLowerCase(Locale.ROOT);
				addMatching(options, partial, "ignorefakeplayer");
				return finish(options, partial, argStart);
			}
			String partial = parts.length == 2 ? "" : parts[2].toLowerCase(Locale.ROOT);
			addMatching(options, partial, "true", "false");
			return finish(options, partial, rangeStart(prefix, parts, trailingSpace, 2));
		}

		if (cmd.equals("totempopnotifier") || cmd.equals("tpn")) {
			if (parts.length == 1 || (parts.length == 2 && !trailingSpace)) {
				String partial = parts.length == 1 ? "" : parts[1].toLowerCase(Locale.ROOT);
				addMatching(options, partial, "player");
				return finish(options, partial, argStart);
			}
			if (parts.length == 2 || (parts.length == 3 && !trailingSpace)) {
				options.add("<ign>");
				return finish(options, "", rangeStart(prefix, parts, trailingSpace, 2));
			}
			if (parts.length == 3 || (parts.length == 4 && !trailingSpace)) {
				String partial = parts.length == 3 ? "" : parts[3].toLowerCase(Locale.ROOT);
				addMatching(options, partial, "count");
				return finish(options, partial, rangeStart(prefix, parts, trailingSpace, 3));
			}
			String partial = parts.length == 4 ? "" : parts[4].toLowerCase(Locale.ROOT);
			addMatching(options, partial, "reset");
			return finish(options, partial, rangeStart(prefix, parts, trailingSpace, 4));
		}

		if (cmd.equals("friend") || cmd.equals("friends")) {
			if (parts.length == 1 || (parts.length == 2 && !trailingSpace)) {
				String partial = parts.length == 1 ? "" : parts[1].toLowerCase(Locale.ROOT);
				addMatching(options, partial, "add", "delete", "list");
				return finish(options, partial, argStart);
			}
			String sub = parts[1].toLowerCase(Locale.ROOT);
			if (sub.equals("add") || sub.equals("delete") || sub.equals("del") || sub.equals("remove")) {
				if (sub.equals("delete") || sub.equals("del") || sub.equals("remove")) {
					String partial = parts.length == 2 ? "" : parts[2].toLowerCase(Locale.ROOT);
					for (String name : dark.noti.client.util.SocialLists.friends()) {
						if (partial.isEmpty() || name.toLowerCase(Locale.ROOT).startsWith(partial)) {
							options.add(name);
						}
					}
					if (options.isEmpty()) {
						options.add("<ign>");
					}
					return finish(options, partial, rangeStart(prefix, parts, trailingSpace, 2));
				}
				options.add("<ign>");
				return finish(options, "", rangeStart(prefix, parts, trailingSpace, 2));
			}
			return inactive();
		}

		return inactive();
	}

	public static String complete(String input) {
		SuggestionResult result = suggest(input);
		if (!result.active()) {
			return input;
		}
		String chosen = result.options.get(result.selected);
		if (chosen.startsWith("<")) {
			return input;
		}

		String prefix = ClientCommandHandler.prefix();
		String body = input.substring(prefix.length());
		boolean trailingSpace = !body.isEmpty() && body.charAt(body.length() - 1) == ' ';
		String trimmed = body.trim();
		String[] parts = trimmed.isEmpty() ? new String[0] : trimmed.split("\\s+");

		if (parts.length == 0 || (parts.length == 1 && !trailingSpace)) {
			return prefix + chosen + " ";
		}

		StringBuilder out = new StringBuilder(prefix);
		int keep = trailingSpace ? parts.length : Math.max(1, parts.length - 1);
		for (int i = 0; i < keep; i++) {
			if (i > 0) {
				out.append(' ');
			}
			out.append(parts[i]);
		}
		out.append(' ').append(chosen).append(' ');
		return out.toString();
	}

	private static int rangeStart(String prefix, String[] parts, boolean trailingSpace, int partIndex) {
		int start = prefix.length();
		int limit = trailingSpace ? Math.min(partIndex, parts.length) : Math.min(partIndex, parts.length - 1);
		for (int i = 0; i < limit; i++) {
			start += parts[i].length() + 1;
		}
		return start;
	}

	private static void addMatching(List<String> options, String partial, String... values) {
		for (String value : values) {
			if (partial.isEmpty() || value.startsWith(partial)) {
				options.add(value);
			}
		}
	}

	private static SuggestionResult finish(List<String> options, String partial, int start) {
		if (options.isEmpty()) {
			selectedIndex = 0;
			return inactive();
		}
		if (selectedIndex >= options.size()) {
			selectedIndex = 0;
		}
		String selected = options.get(selectedIndex);
		String ghost = "";
		if (!selected.startsWith("<") && selected.toLowerCase(Locale.ROOT).startsWith(partial.toLowerCase(Locale.ROOT))) {
			ghost = selected.substring(partial.length());
		}
		return new SuggestionResult(List.copyOf(options), ghost, selectedIndex, start);
	}

	private static SuggestionResult inactive() {
		return new SuggestionResult(List.of(), "", -1, 0);
	}

	public record SuggestionResult(List<String> options, String ghost, int selected, int start) {
		public boolean active() {
			return selected >= 0 && !options.isEmpty();
		}
	}
}
