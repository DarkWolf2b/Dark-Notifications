package dark.noti.client.features.settings;

public class NumberSetting extends Setting<Double> {
	private final double min;
	private final double max;
	private final double step;
	private final boolean integer;

	public NumberSetting(String name, double value, double min, double max, double step, boolean integer) {
		super(name, value);
		this.min = min;
		this.max = max;
		this.step = step;
		this.integer = integer;
	}

	public double getMin() {
		return min;
	}

	public double getMax() {
		return max;
	}

	public void setValue(double value) {
		double v = Math.max(min, Math.min(max, value));
		if (step > 0) {
			v = Math.round(v / step) * step;
			v = Math.max(min, Math.min(max, v));
		}
		if (integer) {
			v = Math.round(v);
		}
		set(v);
	}

	public String display() {
		if (integer) {
			return String.valueOf((int) Math.round(get()));
		}
		return String.format("%.1f", get());
	}
}
