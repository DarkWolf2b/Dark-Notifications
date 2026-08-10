package dark.noti.client.features.settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ModeSetting extends Setting<String> {
	private final List<String> options;

	public ModeSetting(String name, String value, String... options) {
		super(name, value);
		this.options = new ArrayList<>(Arrays.asList(options));
		if (!this.options.contains(value)) {
			throw new IllegalArgumentException("Default mode must be one of the available options");
		}
	}

	public void setOptions(List<String> next) {
		if (next == null || next.isEmpty()) {
			return;
		}
		options.clear();
		options.addAll(next);
		if (!options.contains(get())) {
			set(options.getFirst());
		}
	}

	public List<String> options() {
		return List.copyOf(options);
	}

	public void next() {
		int index = options.indexOf(get());
		set(options.get((index + 1) % options.size()));
	}

	public void previous() {
		int index = options.indexOf(get());
		set(options.get((index - 1 + options.size()) % options.size()));
	}

	public boolean is(String option) {
		return get().equals(option);
	}

	public String display() {
		return get();
	}
}
