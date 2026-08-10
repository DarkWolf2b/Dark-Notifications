package dark.noti.client.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Named colors saved from the color picker (persisted as name=AARRGGBB lines).
 */
public final class SavedColors {
	private static final Path FILE = Paths.get("config/dark-noti/saved-colors.txt");
	private static final Map<String, Integer> COLORS = new LinkedHashMap<>();
	private static boolean loaded;

	private SavedColors() {
	}

	public static synchronized Map<String, Integer> all() {
		ensureLoaded();
		return Collections.unmodifiableMap(new LinkedHashMap<>(COLORS));
	}

	public static synchronized void put(String name, int argb) {
		ensureLoaded();
		if (name == null || name.isBlank()) {
			return;
		}
		COLORS.put(name.trim(), argb);
		persist();
	}

	public static synchronized void remove(String name) {
		ensureLoaded();
		if (COLORS.remove(name) != null) {
			persist();
		}
	}

	public static synchronized List<String> names() {
		ensureLoaded();
		return new ArrayList<>(COLORS.keySet());
	}

	public static synchronized int get(String name, int fallback) {
		ensureLoaded();
		return COLORS.getOrDefault(name, fallback);
	}

	private static void ensureLoaded() {
		if (loaded) {
			return;
		}
		loaded = true;
		COLORS.clear();
		try {
			if (!Files.exists(FILE)) {
				return;
			}
			for (String line : Files.readAllLines(FILE)) {
				String trimmed = line.trim();
				if (trimmed.isEmpty() || !trimmed.contains("=")) {
					continue;
				}
				int eq = trimmed.indexOf('=');
				String name = trimmed.substring(0, eq).trim();
				String hex = trimmed.substring(eq + 1).trim();
				if (hex.startsWith("#")) {
					hex = hex.substring(1);
				}
				try {
					long value = Long.parseLong(hex, 16);
					int argb = hex.length() <= 6 ? (0xFF000000 | (int) (value & 0xFFFFFF)) : (int) value;
					if (!name.isEmpty()) {
						COLORS.put(name, argb);
					}
				} catch (NumberFormatException ignored) {
				}
			}
		} catch (IOException ignored) {
		}
	}

	private static void persist() {
		try {
			Files.createDirectories(FILE.getParent());
			List<String> lines = new ArrayList<>();
			for (Map.Entry<String, Integer> e : COLORS.entrySet()) {
				lines.add(e.getKey() + "=" + String.format("%08X", e.getValue()));
			}
			Files.write(FILE, lines);
		} catch (IOException ignored) {
		}
	}
}
