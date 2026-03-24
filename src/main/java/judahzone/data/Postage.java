package judahzone.data;

/**Millisecond-based envelope pair used for serialization (JSON).
 * MS is the canonical serialized unit; converts to percent and samples
 * @param sustain 0..1 float  */
public record Postage(long atkMS, long dkMS, float sustain, long relMS) {

	public Postage(long atkMS, long dkMS) {
			this(atkMS, dkMS, 0f, 0L);
	}

	public Postage {
		// non-negative
		atkMS = Math.max(0L, atkMS);
		dkMS = Math.max(0L, dkMS);
		relMS = Math.max(0L, relMS);
		sustain = Math.max(0f, Math.min(1f, sustain));
	}

	/**Create Postage from an existing `Letter` by converting sample counts back to ms. */
	public static Postage fromLetter(Letter l) {
		long atk = l.attackMs();
		long dk = l.decayMs();
		return new Postage(atk, dk);
	}

	public static Postage adsr(Letter l) {
		return new Postage(l.attackMs(), l.decayMs(), l.sustainLevel(), l.releaseMs());
	}

}