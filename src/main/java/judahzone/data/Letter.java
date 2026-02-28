package judahzone.data;

import judahzone.prism.Envelope.Delta;
import judahzone.util.Constants;

/** Immutable envelope specification using runtime sample counts as the source of truth. */
public record Letter(
		int attackSamples,
		int decaySamples,
		float sustainLevel, // 0..1
		int releaseSamples,
		Delta override) {

	public Letter(int attackSamples, int decaySamples, float sustainLevel, int releaseSamples) {
		this(attackSamples, decaySamples, sustainLevel, releaseSamples, Delta.IDLE);
	}

	public static final float MS_TO_SECONDS = 0.001f;
	public static final int N_FRAMES = Constants.bufSize();
	public static final float SR = Constants.sampleRate();
	public static final float ISR = 1f / SR; // inverse sample rate, for efficiency
	public static final float NSR = SR * MS_TO_SECONDS; // samples per ms, for efficiency
	public static final float NISR = 1f / NSR; // normalized inverse sample rate, for efficiency

	public static final int DEFAULT_ATTACK_PCT = 5;
	public static final int DEFAULT_DECAY_PCT = 100;

	public static final int DEFAULT_RELEASE_PCT = 10;
	public static final int DEFAULT_SUSTAIN = 95; // percent (0..100)

	public static final int MAX_ATTACK_MS = 127;
	public static final int MAX_DECAY_MS = 888;
	public static final int MAX_RELEASE_MS = 999;

	public static final int SMOOTH_MS = 7;

	public Letter() {
		this(DEFAULT_ATTACK_PCT, DEFAULT_DECAY_PCT);
	}

	// Millisecond based config */
	public Letter(Postage env) {
		this(
			msToSamples(env.atkMS()),
			msToSamples(env.dkMS()),
			env.sustain(),
			msToSamples(env.relMS()));
	}

	// two-stage
	public Letter(int attackPct, int decayPct) {
		this(
			percentToSamples(attackPct, MAX_ATTACK_MS),
			percentToSamples(decayPct, MAX_DECAY_MS),
			0.0f, // no sustain
			0    // no release
		);
	}

	/** ADSR from GUI: attackPct, decayPct, releasePct, sustainPct (0..100). */
	public Letter(int attackPct, int decayPct, int sustainPct, int releasePct) {
		this(
			percentToSamples(attackPct, MAX_ATTACK_MS),
			percentToSamples(decayPct, MAX_DECAY_MS),
			Math.max(0f, Math.min(1f, sustainPct / 100f)),
			percentToSamples(releasePct, MAX_RELEASE_MS),
			Delta.IDLE
		);
	}

	/** Canonical compact constructor: validate ranges. */
	public Letter {
		// clamp samples and sustain
		attackSamples = Math.max(0, attackSamples);
		decaySamples = Math.max(0, decaySamples);
		releaseSamples = Math.max(0, releaseSamples);
		override = (override == null) ? Delta.IDLE : override;
		sustainLevel = Math.max(0f, Math.min(1f, sustainLevel));
	}

	public static int percentToSamples(int pct, int maxMs) {
	    pct = Math.max(0, Math.min(100, pct));
	    if (pct == 0) return 0;
	    // compute target milliseconds (float) then convert to samples using samples-per-ms (NSR).
	    float ms = (pct / 100f) * maxMs;
	    int samples = Math.round(ms * NSR); // NSR = samples per ms
	    // allow zero only for pct==0; otherwise quantize to at least 1 sample to represent non-instant events
	    return Math.max(1, samples);
	}

	public static int samplesToPercent(int samples, int maxMs) {
	    if (maxMs <= 0) return 0;
	    float maxSamples = maxMs * NSR; // total samples for maxMs
	    if (maxSamples <= 0f) return 0;
	    // derive percent from quantized samples; consistent rounding yields deterministic results
	    int pct = Math.round((samples / maxSamples) * 100f);
	    return Math.max(0, Math.min(100, pct));
	}

	public static long samplesToMs(float samples) {
		return Math.round(samples * NISR);
	}

	public static int msToSamples(float ms) {
		return Math.max(0, Math.round(ms * NSR));
	}

	// ---- Compatibility getters (percent-based) ----
	public int attackPct() { return samplesToPercent(attackSamples, MAX_ATTACK_MS); }
	public int decayPct() { return samplesToPercent(decaySamples, MAX_DECAY_MS); }
	public int releasePct() { return samplesToPercent(releaseSamples, MAX_RELEASE_MS); }
	public int sustainPct() { return (int) (sustainLevel * 100f); }

	// ---- Sample-derived helpers ----
	public int attackMs() { return Math.round(attackSamples * NISR); }
	public int decayMs() { return Math.round(decaySamples * NISR); }
	public int releaseMs() { return Math.round(releaseSamples * NISR); }

	public Letter overrideAttack(int samples) {
		return new Letter(Math.max(0, samples), decaySamples, sustainLevel, releaseSamples, Delta.ATK);
	}
	public Letter overrideDecay(int samples) {
		return new Letter(attackSamples, Math.max(0, samples), sustainLevel, releaseSamples, Delta.DK);
	}
	public Letter overrideSustain(float sus) {
		return new Letter(attackSamples, decaySamples, Math.max(0f, Math.min(1f, sus)), releaseSamples, Delta.SUS);
	}
	public Letter overrideRelease(int samples) {
		return new Letter(attackSamples, decaySamples, sustainLevel, Math.max(0, samples), Delta.RLS);
	}

	public Letter withAttackSamples(int samples) {
		return new Letter(Math.max(0, samples), decaySamples, sustainLevel, releaseSamples, override);
	}

	public Letter withDecaySamples(int samples) {
		return new Letter(attackSamples, Math.max(0, samples), sustainLevel, releaseSamples, override);
	}

	public Letter withSustain(float sus) {
		return new Letter(attackSamples, decaySamples, Math.max(0f, Math.min(1f, sus)), releaseSamples, override);
	}
	public Letter withReleaseSamples(int samples) {
		return new Letter(attackSamples, decaySamples, sustainLevel, Math.max(0, samples), override);
	}

	public Letter withSmoothMs(int ms) {
		return new Letter(attackSamples, decaySamples, sustainLevel, releaseSamples, override);
	}

	// Percent-based builders for compatibility with existing callers
	public Letter withAttackPct(int percent) {
		int samples = percentToSamples(percent, MAX_ATTACK_MS);
		return withAttackSamples(samples);
	}

	public Letter withDecayPct(int percent) {
		int samples = percentToSamples(percent, MAX_DECAY_MS);
		return withDecaySamples(samples);
	}

	public Letter withReleasePct(int percent) {
		int samples = percentToSamples(percent, MAX_RELEASE_MS);
		return withReleaseSamples(samples);
	}

	public Letter withSustainPct(int percent) {
		return withSustain(percent / 100f);
	}

	/** Stamp for UI / external APIs expecting percents. */
	public Stamp stamp() {
		return new Stamp(attackPct(), decayPct(), sustainPct(), releasePct());
	}

}
