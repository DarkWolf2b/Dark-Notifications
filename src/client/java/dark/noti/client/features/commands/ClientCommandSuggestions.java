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
			return suggestColor(prefix, parts, trailingSpace, argStart, options);
		}
		if (cmd.equals("gui") || cmd.equals("clickgui")) {
			return suggestGui(prefix, parts, trailingSpace, argStart, options);
		}
		if (cmd.equals("module")) {
			return suggestModule(prefix, parts, trailingSpace, argStart, options);
		}
		if (cmd.equals("visualrange")) {
			return suggestVisualRange(prefix, parts, trailingSpace, argStart, options);
		}
		if (cmd.equals("totempopnotifier") || cmd.equals("tpn")) {
			return suggestTotemPop(prefix, parts, trailingSpace, argStart, options);
		}
		if (cmd.equals("friend") || cmd.equals("friends")) {
			return suggestFriend(prefix, parts, trailingSpace, argStart, options);
		}
		if (cmd.equals("help")) {
			return inactive();
		}

		return inactive();
	}

	private static SuggestionResult suggestColor(
		String prefix, String[] parts, boolean trailingSpace, int argStart, List<String> options
	) {
		if (parts.length == 1 || (parts.length == 2 && !trailingSpace)) {
			String partial = parts.length == 1 ? "" : parts[1].toLowerCase(Locale.ROOT);
			addMatching(options, partial, "primary", "friend", "gui", "save", "delete");
			return finish(options, partial, argStart);
		}

		String sub = parts[1].toLowerCase(Locale.ROOT);

		if (sub.equals("save")) {
			if (parts.length == 2 || (parts.length == 3 && !trailingSpace)) {
				options.add("<hex>");
				return finish(options, "", rangeStart(prefix, parts, trailingSpace, 2));
			}
			if (parts.length == 3 || (parts.length == 4 && !trailingSpace)) {
				options.add("<name>");
				return finish(options, "", rangeStart(prefix, parts, trailingSpace, 3));
			}
			return inactive();
		}

		if (sub.equals("delete")) {
			if (parts.length >= 3 && !trailingSpace) {
				// Name may be complete — still allow editing/filtering.
			}
			if (parts.length == 2 || (parts.length == 3 && !trailingSpace) || (parts.length >= 3 && !trailingSpace)) {
				String partial = parts.length == 2 ? "" : String.join(" ", java.util.Arrays.copyOfRange(parts, 2, parts.length)).toLowerCase(Locale.ROOT);
				for (String name : dark.noti.client.util.SavedColors.names()) {
					if (partial.isEmpty() || name.toLowerCase(Locale.ROOT).startsWith(partial)) {
						options.add(name);
					}
				}
				if (options.isEmpty()) {
					options.add("<name>");
				}
				return finish(options, partial, rangeStart(prefix, parts, trailingSpace, 2));
			}
			return inactive();
		}

		if (sub.equals("primary") || sub.equals("friend") || sub.equals("gui")) {
			// .color gui reset  OR  .color gui <hex>  — reset is terminal
			if (parts.length == 2 || (parts.length == 3 && !trailingSpace)) {
				String partial = parts.length == 2 ? "" : parts[2].toLowerCase(Locale.ROOT);
				if (parts.length == 3 && "reset".equals(partial)) {
					return inactive();
				}
				if (parts.length == 3 && looksLikeHex(partial)) {
					return inactive();
				}
				addMatching(options, partial, "reset", "<hex>");
				return finish(options, partial, rangeStart(prefix, parts, trailingSpace, 2));
			}
			return inactive();
		}

		return inactive();
	}

	private static SuggestionResult suggestGui(
		String prefix, String[] parts, boolean trailingSpace, int argStart, List<String> options
	) {
		if (parts.length == 1 || (parts.length == 2 && !trailingSpace)) {
			String partial = parts.length == 1 ? "" : parts[1].toLowerCase(Locale.ROOT);
			addMatching(options, partial, "position", "size", "keybind");
			return finish(options, partial, argStart);
		}

		String sub = parts[1].toLowerCase(Locale.ROOT);
		if (sub.equals("position")) {
			if (parts.length == 2 || (parts.length == 3 && !trailingSpace)) {
				String partial = parts.length == 2 ? "" : parts[2].toLowerCase(Locale.ROOT);
				if (parts.length == 3 && "reset".equals(partial)) {
					return inactive();
				}
				addMatching(options, partial, "reset");
				return finish(options, partial, rangeStart(prefix, parts, trailingSpace, 2));
			}
			return inactive();
		}
		if (sub.equals("size")) {
			if (parts.length == 2 || (parts.length == 3 && !trailingSpace)) {
				String partial = parts.length == 2 ? "" : parts[2];
				if (parts.length == 3 && !partial.isEmpty()) {
					return inactive();
				}
				options.add("<0.5-2.0>");
				return finish(options, "", rangeStart(prefix, parts, trailingSpace, 2));
			}
			return inactive();
		}
		if (sub.equals("keybind")) {
			if (parts.length == 2 || (parts.length == 3 && !trailingSpace)) {
				String partial = parts.length == 2 ? "" : parts[2].toLowerCase(Locale.ROOT);
				if (parts.length == 3 && exactKeybind(partial)) {
					return inactive();
				}
				addMatching(options, partial, KEYBIND_SUGGESTIONS);
				return finish(options, partial, rangeStart(prefix, parts, trailingSpace, 2));
			}
			return inactive();
		}
		return inactive();
	}

	private static SuggestionResult suggestModule(
		String prefix, String[] parts, boolean trailingSpace, int argStart, List<String> options
	) {
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
		if (parts.length == 2 || (parts.length == 3 && !trailingSpace)) {
			String partial = parts.length == 2 ? "" : parts[2].toLowerCase(Locale.ROOT);
			if (parts.length == 3 && "settingreset".equals(partial)) {
				return inactive();
			}
			addMatching(options, partial, "settingreset");
			return finish(options, partial, rangeStart(prefix, parts, trailingSpace, 2));
		}
		return inactive();
	}

	private static SuggestionResult suggestVisualRange(
		String prefix, String[] parts, boolean trailingSpace, int argStart, List<String> options
	) {
		if (parts.length == 1 || (parts.length == 2 && !trailingSpace)) {
			String partial = parts.length == 1 ? "" : parts[1].toLowerCase(Locale.ROOT);
			addMatching(options, partial, "ignorefakeplayer");
			return finish(options, partial, argStart);
		}
		if (parts.length == 2 || (parts.length == 3 && !trailingSpace)) {
			String partial = parts.length == 2 ? "" : parts[2].toLowerCase(Locale.ROOT);
			if (parts.length == 3 && (partial.equals("true") || partial.equals("false"))) {
				return inactive();
			}
			addMatching(options, partial, "true", "false");
			return finish(options, partial, rangeStart(prefix, parts, trailingSpace, 2));
		}
		return inactive();
	}

	private static SuggestionResult suggestTotemPop(
		String prefix, String[] parts, boolean trailingSpace, int argStart, List<String> options
	) {
		if (parts.length == 1 || (parts.length == 2 && !trailingSpace)) {
			String partial = parts.length == 1 ? "" : parts[1].toLowerCase(Locale.ROOT);
			addMatching(options, partial, "player");
			return finish(options, partial, argStart);
		}
		if (!"player".equalsIgnoreCase(parts[1])) {
			return inactive();
		}
		if (parts.length == 2 || (parts.length == 3 && !trailingSpace)) {
			String partial = parts.length == 2 ? "" : parts[2];
			if (parts.length == 3 && !partial.isEmpty()) {
				// ign typed — next arg is count
				if (!trailingSpace) {
					return inactive();
				}
			} else {
				options.add("<ign>");
				return finish(options, "", rangeStart(prefix, parts, trailingSpace, 2));
			}
		}
		if (parts.length == 3 || (parts.length == 4 && !trailingSpace)) {
			String partial = parts.length == 3 ? "" : parts[3].toLowerCase(Locale.ROOT);
			if (parts.length == 4 && "count".equals(partial)) {
				return inactive();
			}
			addMatching(options, partial, "count");
			return finish(options, partial, rangeStart(prefix, parts, trailingSpace, 3));
		}
		if (parts.length == 4 || (parts.length == 5 && !trailingSpace)) {
			if (!"count".equalsIgnoreCase(parts[3])) {
				return inactive();
			}
			String partial = parts.length == 4 ? "" : parts[4].toLowerCase(Locale.ROOT);
			if (parts.length == 5 && "reset".equals(partial)) {
				return inactive();
			}
			addMatching(options, partial, "reset");
			return finish(options, partial, rangeStart(prefix, parts, trailingSpace, 4));
		}
		return inactive();
	}

	private static SuggestionResult suggestFriend(
		String prefix, String[] parts, boolean trailingSpace, int argStart, List<String> options
	) {
		if (parts.length == 1 || (parts.length == 2 && !trailingSpace)) {
			String partial = parts.length == 1 ? "" : parts[1].toLowerCase(Locale.ROOT);
			addMatching(options, partial, "add", "delete", "list");
			return finish(options, partial, argStart);
		}

		String sub = parts[1].toLowerCase(Locale.ROOT);
		if (sub.equals("list")) {
			return inactive();
		}
		if (sub.equals("add") || sub.equals("delete") || sub.equals("del") || sub.equals("remove")) {
			if (parts.length == 2 || (parts.length == 3 && !trailingSpace)) {
				String partial = parts.length == 2 ? "" : parts[2].toLowerCase(Locale.ROOT);
				if (sub.equals("delete") || sub.equals("del") || sub.equals("remove")) {
					for (String name : dark.noti.client.util.SocialLists.friends()) {
						if (partial.isEmpty() || name.toLowerCase(Locale.ROOT).startsWith(partial)) {
							options.add(name);
						}
					}
					if (options.isEmpty()) {
						options.add("<ign>");
					}
					if (parts.length == 3 && options.stream().anyMatch(o -> o.equalsIgnoreCase(parts[2]))) {
						return inactive();
					}
					return finish(options, partial, rangeStart(prefix, parts, trailingSpace, 2));
				}
				if (parts.length == 3 && !partial.isEmpty()) {
					return inactive();
				}
				options.add("<ign>");
				return finish(options, "", rangeStart(prefix, parts, trailingSpace, 2));
			}
			return inactive();
		}
		return inactive();
	}

	/** Mouse click still fills the selected option. */
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
		out.append(' ').append(chosen);
		// No trailing space — avoids jumping into a leftover placeholder arg.
		return out.toString();
	}

	private static boolean looksLikeHex(String value) {
		String v = value.startsWith("#") ? value.substring(1) : value;
		if (v.length() != 6 && v.length() != 8) {
			return false;
		}
		for (int i = 0; i < v.length(); i++) {
			char c = v.charAt(i);
			if (Character.digit(c, 16) < 0) {
				return false;
			}
		}
		return true;
	}

	private static boolean exactKeybind(String partial) {
		for (String key : KEYBIND_SUGGESTIONS) {
			if (key.equals(partial)) {
				return true;
			}
		}
		return false;
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
			if (partial.isEmpty() || value.toLowerCase(Locale.ROOT).startsWith(partial.toLowerCase(Locale.ROOT))) {
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
