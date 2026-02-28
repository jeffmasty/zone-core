package judahzone.util;

/** Reusable parameter smoothing for RT-safe changes (pitch, feedback, damp, etc).
    Avoids repeated countdown/lerp logic across synth voices. */
public class Ramp {

	private final int rampLen; // samples to smooth over
	private final float invRamp;
	private int countdown;
	private float current;
	private float target;
	private float step; // constant per-sample increment during a ramp
	private boolean initialized;

	/** Create ramp with given smoothing length (samples). */
	public Ramp(int rampLenSamples) {
		this.rampLen = Math.max(1, rampLenSamples);
		this.invRamp = 1f / this.rampLen;
		this.current = 0f;
		this.target = 0f;
		this.countdown = 0;
		this.step = 0f;
	}

	/** Set target value; starts ramp if not already ramping.
	    First ever set jumps immediately (useful for initialization). */
	public void set(float value) {
		// If currently ramping, update target and restart ramp from current value
		if (countdown > 0) {
			this.target = value;
			this.countdown = rampLen; // restart smooth ramp
			this.step = (this.target - this.current) * invRamp;
			return;
		}

		// Idle case
		if (!initialized) {
			// first set -> immediate
			this.current = value;
			this.target = value;
			this.initialized = true;
			this.countdown = 0;
			this.step = 0f;
		} else {
			// subsequent idle sets: if same value, do nothing; otherwise start ramp
			if (Float.compare(this.current, value) == 0) {
				this.target = value;
				// remain idle
				this.step = 0f;
			} else {
				this.target = value;
				this.countdown = rampLen; // begin smooth ramp
				this.step = (this.target - this.current) * invRamp;
			}
		}
	}

	/** Advance one sample; returns current interpolated value. */
	public float next() {
		if (countdown > 0) {
			current += step;
			countdown--;
			if (countdown == 0)
				current = target; // ensure exact final value
		}
		return current;
	}

	/** Get current value without advancing. */
	public float get() {
		return current;
	}

	/** Check if ramp is active. */
	public boolean isRamping() {
		return countdown > 0;
	}

	/** Reset to immediate (no ramp); useful on trigger. */
	public void reset(float value) {
		this.current = value;
		this.target = value;
		this.countdown = 0;
		this.step = 0f;
		this.initialized = true;
	}
}
