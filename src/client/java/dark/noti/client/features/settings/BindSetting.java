package dark.noti.client.features.settings;

import org.lwjgl.glfw.GLFW;

public class BindSetting extends Setting<Integer> {
	private boolean listening;

	public BindSetting(String name, int key) {
		super(name, key);
	}

	public int getKey() {
		return get();
	}

	public boolean isListening() {
		return listening;
	}

	public void setListening(boolean listening) {
		this.listening = listening;
	}

	public String display() {
		int key = getKey();
		if (key <= 0 || key == GLFW.GLFW_KEY_UNKNOWN) {
			return "NONE";
		}
		return switch (key) {
			case GLFW.GLFW_KEY_RIGHT_SHIFT -> "Right Shift";
			case GLFW.GLFW_KEY_LEFT_SHIFT -> "Left Shift";
			case GLFW.GLFW_KEY_RIGHT_CONTROL -> "Right Ctrl";
			case GLFW.GLFW_KEY_LEFT_CONTROL -> "Left Ctrl";
			case GLFW.GLFW_KEY_SPACE -> "Space";
			case GLFW.GLFW_KEY_TAB -> "Tab";
			default -> {
				String name = GLFW.glfwGetKeyName(key, 0);
				yield name != null ? Character.toUpperCase(name.charAt(0)) + name.substring(1) : ("Key " + key);
			}
		};
	}
}
