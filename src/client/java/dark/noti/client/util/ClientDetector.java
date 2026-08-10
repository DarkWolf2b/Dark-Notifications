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

	private static final Pattern BRACKET_PREFIX = Pattern.compile("\\[([^\\]]+)]");

	/** [Client] [+/-] Module */
	private static final Pattern ICON_TOGGLE = Pattern.compile(
		"^\\[([^\\]]+)]\\s*\\[([+\\-])]\\s*(.+?)\\.?$", Pattern.CASE_INSENSITIVE);
	/** [Client] Module toggled on/off */
	private static final Pattern TOGGLED = Pattern.compile(
		"^\\[([^\\]]+)]\\s*(.+?)\\s+toggled\\s+(on|off)\\.?$",
		Pattern.CASE_INSENSITIVE);
	/** [Client] Module is now enabled/disabled */
	private static final Pattern IS_NOW = Pattern.compile(
		"^\\[([^\\]]+)]\\s*(.+?)\\s+is\\s+now\\s+(enabled|disabled)\\.?$",
		Pattern.CASE_INSENSITIVE);
	/** [Client] Module enabled/disabled (Future-style; optional was) */
	private static final Pattern SIMPLE_STATE = Pattern.compile(
		"^\\[([^\\]]+)]\\s*(.+?)\\s+(?:was\\s+)?(enabled|disabled|enable|disable|on|off)!?\\.?$",
		Pattern.CASE_INSENSITIVE);
	/** [Client] enabled/disabled Module */
	private static final Pattern STATE_FIRST = Pattern.compile(
		"^\\[([^\\]]+)]\\s*(enabled|disabled|enable|disable|on|off)\\s+(.+?)!?\\.?$",
		Pattern.CASE_INSENSITIVE);
	/** [Client] Module : enabled */
	private static final Pattern COLON_STATE = Pattern.compile(
		"^\\[([^\\]]+)]\\s*(.+?)\\s*[:\\-]\\s*(enabled|disabled|enable|disable|on|off)!?\\.?$",
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
		String line = prepareChatLine(plain);
		if (line.isEmpty()) {
			return null;
		}

		ToggleHit hit = matchTogglePatterns(line);
		if (hit != null) {
			return hit;
		}

		// Public chat often looks like: <Name> [Future] Speed enabled
		String fromTag = sliceFromKnownClientTag(line);
		if (fromTag != null && !fromTag.equals(line)) {
			return matchTogglePatterns(fromTag);
		}
		return null;
	}

	private static ToggleHit matchTogglePatterns(String line) {
		Matcher icon = ICON_TOGGLE.matcher(line);
		if (icon.matches()) {
			return hit(icon.group(1), icon.group(3), "+".equals(icon.group(2)));
		}
		Matcher toggled = TOGGLED.matcher(line);
		if (toggled.matches()) {
			return hit(toggled.group(1), toggled.group(2), isOn(toggled.group(3)));
		}
		Matcher isNow = IS_NOW.matcher(line);
		if (isNow.matches()) {
			return hit(isNow.group(1), isNow.group(2), isOn(isNow.group(3)));
		}
		Matcher colon = COLON_STATE.matcher(line);
		if (colon.matches()) {
			return hit(colon.group(1), colon.group(2), isOn(colon.group(3)));
		}
		Matcher stateFirst = STATE_FIRST.matcher(line);
		if (stateFirst.matches()) {
			return hit(stateFirst.group(1), stateFirst.group(3), isOn(stateFirst.group(2)));
		}
		Matcher simple = SIMPLE_STATE.matcher(line);
		if (simple.matches()) {
			return hit(simple.group(1), simple.group(2), isOn(simple.group(3)));
		}
		return null;
	}

	/**
	 * Drop player-name wrappers and locate the first known CCM [Client] tag so
	 * messages like {@code <Steve> [Future] Speed enabled} still parse.
	 */
	private static String prepareChatLine(String plain) {
		String line = stripFormatting(plain).trim();
		// <Name> message
		if (line.startsWith("<")) {
			int close = line.indexOf('>');
			if (close > 0 && close + 1 < line.length()) {
				line = line.substring(close + 1).trim();
			}
		}
		// Name » message / Name: message (single token name)
		Matcher namePrefix = Pattern.compile("^\\S{1,32}\\s*(?:»|>|:)\\s+(.+)$").matcher(line);
		if (namePrefix.matches()) {
			String rest = namePrefix.group(1).trim();
			if (rest.startsWith("[") || sliceFromKnownClientTag(rest) != null) {
				line = rest;
			}
		}
		String fromTag = sliceFromKnownClientTag(line);
		return fromTag != null ? fromTag : line;
	}

	private static String sliceFromKnownClientTag(String line) {
		Matcher m = BRACKET_PREFIX.matcher(line);
		while (m.find()) {
			String normalized = normalizePrefix(m.group(1));
			if (isKnownCcmTarget(normalized)) {
				return line.substring(m.start()).trim();
			}
		}
		return null;
	}

	private static ToggleHit hit(String clientRaw, String moduleRaw, boolean enabled) {
		String client = normalizePrefix(clientRaw);
		String module = moduleRaw == null ? "" : moduleRaw.trim();
		while (module.endsWith(".") || module.endsWith("!")) {
			module = module.substring(0, module.length() - 1).trim();
		}
		if (client == null || module.isEmpty()) {
			return null;
		}
		return new ToggleHit(client, module, enabled);
	}

	private static boolean isOn(String state) {
		if (state == null) {
			return false;
		}
		String s = state.toLowerCase(Locale.ROOT);
		return s.equals("on") || s.equals("enabled") || s.equals("enable") || s.equals("+");
	}

	/** Strip legacy § codes and common zero-width / BOM junk from chat. */
	private static String stripFormatting(String text) {
		String out = text.replaceAll("§.", "");
		out = out.replace("\uFEFF", "").replace("\u200B", "").replace("\u00A0", " ");
		return out;
	}
}
