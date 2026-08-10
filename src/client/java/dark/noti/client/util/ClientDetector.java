package dark.noti.client.util;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses other-client module toggle chat and maps prefixes to CCM target names.
 */
public final class ClientDetector {
	public static final String DARK = "Dark Notifications";
	public static final String FUTURE = "Future";
	public static final String BOZE = "Boze";
	public static final String MIO = "Mio";
	public static final String HOMOVORE = "Homovore";

	/** Fixed CCM target list (order shown in GUI). */
	public static final String[] CCM_TARGETS = {
		DARK, FUTURE, BOZE, MIO, HOMOVORE
	};

	/** Chat prefix (normalized) → display name. */
	private static final Map<String, String> PREFIX_ALIASES = Map.ofEntries(
		Map.entry("dark", DARK),
		Map.entry("dark notifications", DARK),
		Map.entry("darknotifications", DARK),
		Map.entry("future", FUTURE),
		Map.entry("boze", BOZE),
		Map.entry("mio", MIO),
		Map.entry("homovore", HOMOVORE)
	);

	private static final Pattern ICON_TOGGLE = Pattern.compile(
		"^\\[([^\\]]+)]\\s+\\[([+\\-])]\\s+(.+?)\\.?$");
	private static final Pattern TEXT_TOGGLE = Pattern.compile(
		"^\\[([^\\]]+)]\\s+(\\S+)\\s+(?:toggled\\s+(on|off)|is now\\s+(enabled|disabled))\\.?$",
		Pattern.CASE_INSENSITIVE);

	private ClientDetector() {
	}

	public static String normalizePrefix(String prefix) {
		if (prefix == null) {
			return null;
		}
		String raw = prefix.trim();
		while (raw.endsWith("!")) {
			raw = raw.substring(0, raw.length() - 1).trim();
		}
		String key = raw.toLowerCase(Locale.ROOT);
		String alias = PREFIX_ALIASES.get(key);
		if (alias != null) {
			return alias;
		}
		if (raw.isEmpty()) {
			return raw;
		}
		if (raw.equals(raw.toLowerCase(Locale.ROOT)) || raw.equals(raw.toUpperCase(Locale.ROOT))) {
			return Character.toUpperCase(raw.charAt(0)) + raw.substring(1).toLowerCase(Locale.ROOT);
		}
		return raw;
	}

	public static boolean isKnownCcmTarget(String client) {
		if (client == null) {
			return false;
		}
		for (String target : CCM_TARGETS) {
			if (target.equalsIgnoreCase(client)) {
				return true;
			}
		}
		return client.equalsIgnoreCase("Dark");
	}

	public record ToggleHit(String client, String module, boolean enabled) {
	}

	public static ToggleHit parseToggle(String plain) {
		if (plain == null || plain.isBlank()) {
			return null;
		}
		String line = plain.trim();
		Matcher icon = ICON_TOGGLE.matcher(line);
		if (icon.matches()) {
			String client = normalizePrefix(icon.group(1));
			boolean enabled = "+".equals(icon.group(2));
			String module = icon.group(3).trim();
			if (!module.isEmpty()) {
				return new ToggleHit(client, module, enabled);
			}
		}
		Matcher text = TEXT_TOGGLE.matcher(line);
		if (text.matches()) {
			String client = normalizePrefix(text.group(1));
			String module = text.group(2).trim();
			String state = text.group(3) != null ? text.group(3) : text.group(4);
			boolean enabled = state != null && (state.equalsIgnoreCase("on") || state.equalsIgnoreCase("enabled"));
			if (!module.isEmpty()) {
				return new ToggleHit(client, module, enabled);
			}
		}
		return null;
	}
}
