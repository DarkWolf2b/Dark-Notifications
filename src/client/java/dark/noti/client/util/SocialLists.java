package dark.noti.client.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.Player;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Friends stored by UUID (uuid=LastKnownName). Names are updated when seen in-world.
 */
public final class SocialLists {
	private static final Path DIR = Paths.get("config/dark-noti/social");
	private static final Path FILE = DIR.resolve("friends.txt");
	private static final Map<UUID, String> FRIENDS = new LinkedHashMap<>();
	private static final HttpClient HTTP = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(5))
		.build();
	private static boolean loaded;

	private SocialLists() {
	}

	public static synchronized List<String> friends() {
		ensureLoaded();
		List<String> names = new ArrayList<>(FRIENDS.size());
		for (String name : FRIENDS.values()) {
			names.add(name);
		}
		return names;
	}

	public static synchronized List<FriendEntry> friendEntries() {
		ensureLoaded();
		List<FriendEntry> out = new ArrayList<>(FRIENDS.size());
		for (Map.Entry<UUID, String> e : FRIENDS.entrySet()) {
			out.add(new FriendEntry(e.getKey(), e.getValue()));
		}
		return out;
	}

	public static synchronized boolean isFriend(UUID uuid) {
		ensureLoaded();
		return uuid != null && FRIENDS.containsKey(uuid);
	}

	public static boolean isFriend(Player player) {
		if (player == null) {
			return false;
		}
		observe(player.getUUID(), player.getName().getString());
		return isFriend(player.getUUID());
	}

	public static synchronized boolean isFriend(String name) {
		ensureLoaded();
		if (name == null || name.isBlank()) {
			return false;
		}
		String target = name.trim();

		UUID online = findOnlineUuid(target);
		if (online != null && FRIENDS.containsKey(online)) {
			FRIENDS.put(online, canonicalOnlineName(online, target));
			return true;
		}

		for (Map.Entry<UUID, String> e : FRIENDS.entrySet()) {
			if (e.getValue().equalsIgnoreCase(target)) {
				return true;
			}
		}
		return false;
	}

	/** Prefer UUID identity; keeps last-known name fresh. */
	public static synchronized void observe(UUID uuid, String name) {
		ensureLoaded();
		if (uuid == null || name == null || name.isBlank()) {
			return;
		}
		if (FRIENDS.containsKey(uuid)) {
			String current = FRIENDS.get(uuid);
			if (!name.equals(current)) {
				FRIENDS.put(uuid, name);
				persist();
			}
		}
	}

	public static synchronized void refreshSeenPlayers() {
		ensureLoaded();
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return;
		}
		boolean changed = false;
		for (Player player : client.level.players()) {
			UUID uuid = player.getUUID();
			if (FRIENDS.containsKey(uuid)) {
				String name = player.getName().getString();
				if (!name.equals(FRIENDS.get(uuid))) {
					FRIENDS.put(uuid, name);
					changed = true;
				}
			}
		}
		ClientPacketListener connection = client.getConnection();
		if (connection != null) {
			for (PlayerInfo info : connection.getOnlinePlayers()) {
				UUID uuid = info.getProfile().id();
				if (FRIENDS.containsKey(uuid)) {
					String name = info.getProfile().name();
					if (name != null && !name.equals(FRIENDS.get(uuid))) {
						FRIENDS.put(uuid, name);
						changed = true;
					}
				}
			}
		}
		if (changed) {
			persist();
		}
	}

	public static synchronized AddResult addFriend(String ign) {
		ensureLoaded();
		if (ign == null || ign.isBlank()) {
			return AddResult.fail("Enter a player name.");
		}
		String name = ign.trim();
		UUID uuid = resolveUuid(name);
		if (uuid == null) {
			return AddResult.fail("Couldn't resolve UUID for \"" + name + "\". They may need to be online.");
		}
		String storedName = canonicalOnlineName(uuid, name);
		if (FRIENDS.containsKey(uuid)) {
			FRIENDS.put(uuid, storedName);
			persist();
			return AddResult.ok("Already friends with " + storedName + ".");
		}
		FRIENDS.put(uuid, storedName);
		persist();
		return AddResult.ok("Added friend " + storedName + " (" + shortUuid(uuid) + ").");
	}

	public static synchronized RemoveResult removeFriend(String ignOrUuid) {
		ensureLoaded();
		if (ignOrUuid == null || ignOrUuid.isBlank()) {
			return RemoveResult.fail("Enter a player name or UUID.");
		}
		String raw = ignOrUuid.trim();

		UUID byUuid = parseUuid(raw);
		if (byUuid != null && FRIENDS.containsKey(byUuid)) {
			String name = FRIENDS.remove(byUuid);
			persist();
			return RemoveResult.ok("Removed friend " + name + ".");
		}

		UUID online = findOnlineUuid(raw);
		if (online != null && FRIENDS.containsKey(online)) {
			String name = FRIENDS.remove(online);
			persist();
			return RemoveResult.ok("Removed friend " + name + ".");
		}

		UUID named = null;
		for (Map.Entry<UUID, String> e : FRIENDS.entrySet()) {
			if (e.getValue().equalsIgnoreCase(raw)) {
				named = e.getKey();
				break;
			}
		}
		if (named != null) {
			String name = FRIENDS.remove(named);
			persist();
			return RemoveResult.ok("Removed friend " + name + ".");
		}
		return RemoveResult.fail("No friend named \"" + raw + "\".");
	}

	private static UUID resolveUuid(String name) {
		UUID online = findOnlineUuid(name);
		if (online != null) {
			return online;
		}
		return lookupMojangUuid(name);
	}

	private static UUID findOnlineUuid(String name) {
		Minecraft client = Minecraft.getInstance();
		if (client.level != null) {
			for (Player player : client.level.players()) {
				if (player.getName().getString().equalsIgnoreCase(name)) {
					return player.getUUID();
				}
			}
		}
		ClientPacketListener connection = client.getConnection();
		if (connection != null) {
			for (PlayerInfo info : connection.getOnlinePlayers()) {
				String profileName = info.getProfile().name();
				if (profileName != null && profileName.equalsIgnoreCase(name)) {
					return info.getProfile().id();
				}
			}
		}
		return null;
	}

	private static String canonicalOnlineName(UUID uuid, String fallback) {
		Minecraft client = Minecraft.getInstance();
		if (client.level != null) {
			for (Player player : client.level.players()) {
				if (player.getUUID().equals(uuid)) {
					return player.getName().getString();
				}
			}
		}
		ClientPacketListener connection = client.getConnection();
		if (connection != null) {
			for (PlayerInfo info : connection.getOnlinePlayers()) {
				if (info.getProfile().id().equals(uuid)) {
					String n = info.getProfile().name();
					if (n != null && !n.isBlank()) {
						return n;
					}
				}
			}
		}
		return fallback;
	}

	private static UUID lookupMojangUuid(String name) {
		try {
			HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + name))
				.timeout(Duration.ofSeconds(5))
				.GET()
				.build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) {
				return null;
			}
			JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
			if (!json.has("id")) {
				return null;
			}
			return parseUuid(json.get("id").getAsString());
		} catch (Exception ignored) {
			return null;
		}
	}

	private static UUID parseUuid(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String s = raw.trim();
		try {
			if (s.length() == 32) {
				s = s.replaceFirst(
					"(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
					"$1-$2-$3-$4-$5");
			}
			return UUID.fromString(s);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static String shortUuid(UUID uuid) {
		String s = uuid.toString();
		return s.substring(0, 8);
	}

	private static synchronized void ensureLoaded() {
		if (loaded) {
			return;
		}
		loaded = true;
		FRIENDS.clear();
		try {
			if (!Files.exists(FILE)) {
				// Migrate legacy name-only list if present under old path content.
				return;
			}
			for (String line : Files.readAllLines(FILE)) {
				String trimmed = line.trim();
				if (trimmed.isEmpty() || trimmed.startsWith("#")) {
					continue;
				}
				int eq = trimmed.indexOf('=');
				if (eq > 0) {
					UUID uuid = parseUuid(trimmed.substring(0, eq).trim());
					String name = trimmed.substring(eq + 1).trim();
					if (uuid != null && !name.isEmpty()) {
						FRIENDS.put(uuid, name);
					}
				} else {
					// Legacy: bare IGN — resolve now if possible, else skip until .friend add
					UUID uuid = resolveUuid(trimmed);
					if (uuid != null) {
						FRIENDS.put(uuid, trimmed);
					}
				}
			}
			persist();
		} catch (IOException ignored) {
		}
	}

	private static void persist() {
		try {
			Files.createDirectories(DIR);
			List<String> lines = new ArrayList<>();
			for (Map.Entry<UUID, String> e : FRIENDS.entrySet()) {
				lines.add(e.getKey() + "=" + e.getValue());
			}
			Files.write(FILE, lines, StandardCharsets.UTF_8);
		} catch (IOException ignored) {
		}
	}

	public static boolean mentionsName(String message, String name) {
		if (message == null || name == null || name.isBlank()) {
			return false;
		}
		String hay = message.toLowerCase(Locale.ROOT);
		String needle = name.toLowerCase(Locale.ROOT).trim();
		if (hay.contains(needle)) {
			return true;
		}
		String compactHay = hay.replace("_", "").replace("-", "");
		String compactNeedle = needle.replace("_", "").replace("-", "");
		if (compactNeedle.length() >= 3 && compactHay.contains(compactNeedle)) {
			return true;
		}
		if (compactNeedle.length() >= 4) {
			String prefix = compactNeedle.substring(0, Math.min(4, compactNeedle.length()));
			return compactHay.contains(prefix);
		}
		return false;
	}

	public record FriendEntry(UUID uuid, String name) {
	}

	public record AddResult(boolean ok, String message) {
		static AddResult ok(String message) {
			return new AddResult(true, message);
		}

		static AddResult fail(String message) {
			return new AddResult(false, message);
		}
	}

	public record RemoveResult(boolean ok, String message) {
		static RemoveResult ok(String message) {
			return new RemoveResult(true, message);
		}

		static RemoveResult fail(String message) {
			return new RemoveResult(false, message);
		}
	}
}
