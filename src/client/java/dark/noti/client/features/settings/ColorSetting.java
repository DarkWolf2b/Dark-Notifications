package dark.noti.client.features.settings;

import dark.noti.client.features.modules.client.ColorsModule;
import dark.noti.client.util.SavedColors;

import java.util.List;

/**
 * ARGB color stored internally as HSB + alpha so the picker maps directly.
 * Can also link to Primary / Friend / Saved colors (except when used inside Colors module).
 * Friend-themed settings cycle Custom → Friend → Saved; others cycle Custom → Primary → Saved.
 */
public class ColorSetting extends Setting<Integer> {
	public enum LinkMode {
		CUSTOM, PRIMARY, FRIEND, SAVED
	}

	private static final int DEFAULT_PRIMARY = 0xFF9D4EDD;
	private static final int DEFAULT_FRIEND = 0xFF55C6F0;

	private final int defColor;
	/** When true, the theme link slot is Friend instead of Primary. */
	private final boolean friendTheme;
	private float hue;
	private float sat;
	private float bri;
	private float alpha;
	private boolean expanded;
	private LinkMode linkMode = LinkMode.CUSTOM;
	private int savedIndex;

	public ColorSetting(String name, int argb) {
		this(name, argb, false, LinkMode.CUSTOM);
	}

	private ColorSetting(String name, int argb, boolean friendTheme, LinkMode initialLink) {
		super(name, argb);
		this.defColor = argb;
		this.friendTheme = friendTheme;
		fromArgb(argb);
		this.linkMode = initialLink == null ? LinkMode.CUSTOM : initialLink;
	}

	/** Linked to Colors → Friend by default; picker cycles Custom → Friend → Saved. */
	public static ColorSetting forFriend(String name) {
		return new ColorSetting(name, friendDefault(), true, LinkMode.FRIEND);
	}

	/**
	 * Starts as a snapshot of Colors → Primary (custom, not linked).
	 * Changing Primary later does not change this color unless the user links to Primary.
	 */
	public static ColorSetting forSelf(String name) {
		return new ColorSetting(name, primarySnapshot(), false, LinkMode.CUSTOM);
	}

	public boolean usesFriendTheme() {
		return friendTheme;
	}

	public float hue() {
		return hue;
	}

	public float sat() {
		return sat;
	}

	public float bri() {
		return bri;
	}

	public float alpha() {
		return alpha;
	}

	public void setHue(float h) {
		linkMode = LinkMode.CUSTOM;
		hue = clamp(h);
		sync();
	}

	public void setSat(float s) {
		linkMode = LinkMode.CUSTOM;
		sat = clamp(s);
		sync();
	}

	public void setBri(float b) {
		linkMode = LinkMode.CUSTOM;
		bri = clamp(b);
		sync();
	}

	public void setAlpha(float a) {
		linkMode = LinkMode.CUSTOM;
		alpha = clamp(a);
		sync();
	}

	public boolean isExpanded() {
		return expanded;
	}

	public void setExpanded(boolean expanded) {
		this.expanded = expanded;
	}

	public LinkMode linkMode() {
		return linkMode;
	}

	public void setLinkMode(LinkMode mode) {
		this.linkMode = normalizeLink(mode);
		if (this.linkMode == LinkMode.SAVED) {
			List<String> names = SavedColors.names();
			if (!names.isEmpty()) {
				savedIndex = Math.floorMod(savedIndex, names.size());
			}
		}
	}

	public int savedIndex() {
		return savedIndex;
	}

	public void setSavedIndex(int index) {
		this.savedIndex = index;
	}

	public void loadArgb(int argb) {
		fromArgb(argb);
		set(customArgb());
	}

	public void cycleLinkMode() {
		LinkMode theme = friendTheme ? LinkMode.FRIEND : LinkMode.PRIMARY;
		LinkMode next = switch (linkMode) {
			case CUSTOM -> theme;
			case PRIMARY, FRIEND -> LinkMode.SAVED;
			case SAVED -> LinkMode.CUSTOM;
		};
		if (next == LinkMode.SAVED) {
			List<String> names = SavedColors.names();
			if (names.isEmpty()) {
				linkMode = LinkMode.CUSTOM;
				return;
			}
			savedIndex = Math.floorMod(savedIndex, names.size());
		}
		linkMode = next;
	}

	/**
	 * Mode button: Custom → Primary/Friend → Saved → Custom.
	 * While in Saved, each click swaps to the next saved name, then back to Custom.
	 */
	public void advanceLinkControl() {
		if (linkMode != LinkMode.SAVED) {
			cycleLinkMode();
			return;
		}
		List<String> names = SavedColors.names();
		if (names.isEmpty()) {
			linkMode = LinkMode.CUSTOM;
			return;
		}
		if (savedIndex + 1 >= names.size()) {
			linkMode = LinkMode.CUSTOM;
			savedIndex = 0;
			return;
		}
		savedIndex++;
	}

	public void cycleSavedColor() {
		advanceLinkControl();
	}

	public String linkLabel() {
		return switch (linkMode) {
			case CUSTOM -> "Custom";
			case PRIMARY -> "Primary";
			case FRIEND -> "Friend";
			case SAVED -> {
				String name = savedLinkName();
				yield name == null ? "Saved" : "Saved: " + name;
			}
		};
	}

	/** Current linked saved-color name, or null if not in Saved mode / none exist. */
	public String savedLinkName() {
		if (linkMode != LinkMode.SAVED) {
			return null;
		}
		List<String> names = SavedColors.names();
		if (names.isEmpty()) {
			return null;
		}
		return names.get(Math.floorMod(savedIndex, names.size()));
	}

	public void reset() {
		linkMode = friendTheme ? LinkMode.FRIEND : LinkMode.CUSTOM;
		fromArgb(defColor);
		set(defColor);
	}

	/** Opaque RGB of the current hue/sat/bri (0xFFRRGGBB). */
	public int rgb() {
		return hsbToRgb(hue, sat, bri);
	}

	/** Full ARGB including linked Primary / Friend / Saved colors. */
	public int argb() {
		if (linkMode == LinkMode.PRIMARY) {
			ColorsModule colors = ColorsModule.INSTANCE;
			if (colors != null) {
				return colors.primaryRaw();
			}
		} else if (linkMode == LinkMode.FRIEND) {
			ColorsModule colors = ColorsModule.INSTANCE;
			if (colors != null) {
				return colors.friendRaw();
			}
		} else if (linkMode == LinkMode.SAVED) {
			List<String> names = SavedColors.names();
			if (!names.isEmpty()) {
				return SavedColors.get(names.get(Math.floorMod(savedIndex, names.size())), customArgb());
			}
		}
		return customArgb();
	}

	public int customArgb() {
		int a = Math.round(alpha * 255f) & 0xFF;
		return (a << 24) | (rgb() & 0xFFFFFF);
	}

	public void fromArgb(int argb) {
		alpha = ((argb >> 24) & 0xFF) / 255f;
		float[] hsb = rgbToHsb((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF);
		hue = hsb[0];
		sat = hsb[1];
		bri = hsb[2];
	}

	private LinkMode normalizeLink(LinkMode mode) {
		if (mode == null) {
			return LinkMode.CUSTOM;
		}
		if (friendTheme && mode == LinkMode.PRIMARY) {
			return LinkMode.FRIEND;
		}
		if (!friendTheme && mode == LinkMode.FRIEND) {
			return LinkMode.PRIMARY;
		}
		return mode;
	}

	private void sync() {
		set(customArgb());
	}

	private static int primarySnapshot() {
		ColorsModule colors = ColorsModule.INSTANCE;
		return colors != null ? colors.primaryRaw() : DEFAULT_PRIMARY;
	}

	private static int friendDefault() {
		ColorsModule colors = ColorsModule.INSTANCE;
		return colors != null ? colors.friendRaw() : DEFAULT_FRIEND;
	}

	private static float clamp(float v) {
		return v < 0f ? 0f : (v > 1f ? 1f : v);
	}

	public static int hsbToRgb(float h, float s, float b) {
		float r = 0, g = 0, bl = 0;
		if (s == 0) {
			r = g = bl = b;
		} else {
			float hh = (h - (float) Math.floor(h)) * 6f;
			float f = hh - (float) Math.floor(hh);
			float p = b * (1f - s);
			float q = b * (1f - s * f);
			float t = b * (1f - s * (1f - f));
			switch ((int) hh) {
				case 0 -> { r = b; g = t; bl = p; }
				case 1 -> { r = q; g = b; bl = p; }
				case 2 -> { r = p; g = b; bl = t; }
				case 3 -> { r = p; g = q; bl = b; }
				case 4 -> { r = t; g = p; bl = b; }
				default -> { r = b; g = p; bl = q; }
			}
		}
		int ri = Math.round(r * 255f);
		int gi = Math.round(g * 255f);
		int bi = Math.round(bl * 255f);
		return 0xFF000000 | (ri << 16) | (gi << 8) | bi;
	}

	public static float[] rgbToHsb(int r, int g, int b) {
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float bri = max / 255f;
		float sat = max == 0 ? 0 : (max - min) / max;
		float hue;
		if (max == min) {
			hue = 0;
		} else {
			float d = max - min;
			if (max == r) {
				hue = (g - b) / d + (g < b ? 6 : 0);
			} else if (max == g) {
				hue = (b - r) / d + 2;
			} else {
				hue = (r - g) / d + 4;
			}
			hue /= 6f;
		}
		return new float[] { hue, sat, bri };
	}
}
