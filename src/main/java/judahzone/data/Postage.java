package judahzone.data;

/**
 * Millisecond-based envelope pair used for serialization (JSON). MS is the
 * canonical serialized unit; provides conversions to RT sample-based Letter, UI
 * Stamp, and legacy Env helpers.
 */

public record Postage(long atkMS, long dkMS, long relMS, float sustain) {


	public Postage(long atkMS, long dkMS) {
			this(atkMS, dkMS, 0L, 0f);
	}

	public Postage {
		// non-negative
		atkMS = Math.max(0L, atkMS);
		dkMS = Math.max(0L, dkMS);
		relMS = Math.max(0L, relMS);
		sustain = Math.max(0f, sustain);
	}


	/**
	 * Create Postage from an existing `Letter` by converting sample counts back to
	 * ms.
	 */
	public static Postage fromLetter(Letter l) {
		long atk = l.attackMs();
		long dk = l.decayMs();
		return new Postage(atk, dk);
	}

//	/**
//	 * Convert to a UI-friendly `Stamp` (ms ints). Sustain defaults to 1.0f, release
//	 * to 0.
//	 */
//	public Stamp toStamp() {
//		return new Stamp(Letter.atkToPercent(atkMS), dkToPercent(dkMS), 100, 0);
//	}
//
//	/** Build Postage from a `Stamp` (ms ints). */
//	public static Postage fromStamp(Stamp s) {
//		return new Postage(s.attack, s.decay);
//	}

//	public static int atkToPercent(long atk) {
//		return Constants.ratio(atk, Letter.MAX_ATTACK_MS);
//	}
//
//	public static int dkToPercent(long dk) {
//		return Constants.ratio(dk, Letter.MAX_DECAY_MS);
//	}
//
//
//	public static int msToSamples(float ms, float sampleRate) {
//		return Math.max(1, Math.round(ms * sampleRate * 0.001f));
//	}
//
//	public static float samplesToMs(float samples, float sampleRate) {
//		return samples * 0.001f / sampleRate;
//	}

}