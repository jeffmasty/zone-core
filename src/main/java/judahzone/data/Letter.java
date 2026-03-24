package judahzone.data;

import judahzone.util.Constants;
import judahzone.util.RTLogger;

/** Immutable envelope specification using runtime sample counts as the source of truth. */
public record Letter(
		int attackSamples,
		int decaySamples,
		float sustainLevel, // 0..1
		int releaseSamples,
		int sr
		)
{
	public Letter(int attackSamples, int decaySamples, float sustainLevel, int releaseSamples) {
		this(attackSamples, decaySamples, sustainLevel, releaseSamples, Constants.sampleRate());
	}

	public static final int SMOOTH_MS = 6;

	public static final float MS_TO_SECONDS = 0.001f;
	public static final int MAX_ATTACK_MS = 256;
	public static final int MAX_DECAY_MS = 2048;
	public static final int MAX_RELEASE_MS = 4096;

	public static final int DEFAULT_ATTACK_PCT = 50;
	public static final int DEFAULT_DECAY_PCT = 50;
	public static final int DEFAULT_RELEASE_PCT = 50;
	public static final int DEFAULT_SUSTAIN = 90; // percent (0..100)

	public Letter() {
		this(DEFAULT_ATTACK_PCT, DEFAULT_DECAY_PCT);
	}

	/** over-sampled millisecond-based config */
	public Letter (Postage env, int sr) {
		this(
			msToSamples(clampAtkMs(env.atkMS()), sr),
			msToSamples(clampDkMs(env.dkMS()), sr),
			env.sustain(),
			msToSamples(env.relMS(), sr),
			sr
		);
	}

	/** Millisecond based config */
	public Letter(Postage env) {
		this(
			msToSamples(clampAtkMs(env.atkMS())),
			msToSamples(clampDkMs(env.dkMS())),
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
			Constants.sampleRate()
		);
	}

	/** Canonical compact constructor: validate ranges. */
	public Letter {
		// clamp samples and sustain
		attackSamples = Math.max(0, attackSamples);
		decaySamples = Math.max(0, decaySamples);
		releaseSamples = Math.max(0, releaseSamples);
		sustainLevel = Math.max(0f, Math.min(1f, sustainLevel));
		sr = sr <= 0 ? Constants.sampleRate() : sr;
	}

//	private static final float SR = Constants.sampleRate();
//	private static final float NSR = SR * MS_TO_SECONDS; // samples per ms
//	/** normalized inverse sample rate (system rate) */
//	public static final float NISR = 1f / NSR;
//	private final float NSR = sr * MS_TO_SECONDS; // samples per ms
//	private final float NISR = 1f / NSR; // normalized inverse sample rate (system rate)
	private static float nsr(int sr) {
		return sr * MS_TO_SECONDS;
	}
	private static float nisr(int sr) {
		return 1f / (sr * MS_TO_SECONDS);
	}


	public static int percentToSamples(int pct, int maxMs, int sr) {
	    pct = Math.max(0, Math.min(100, pct));
	    if (pct == 0) return 0;
	    // compute target milliseconds (float) then convert to samples using samples-per-ms (NSR).
	    float ms = (pct * 0.01f) * maxMs;
	    int samples = Math.round(ms * nsr(sr)); // NSR = samples per ms
	    // allow zero only for pct==0; otherwise quantize to at least 1 sample to represent non-instant events
	    return Math.max(1, samples);
	}

	public static int percentToSamples(int pct, int maxMs) {
	    return percentToSamples(pct, maxMs, Constants.sampleRate());
	}

	public static int samplesToPercent(int samples, int maxMs, int sr) {
	    if (maxMs <= 0) return 0;
	    float maxSamples = maxMs * nsr(sr); // total samples for maxMs
	    if (maxSamples <= 0f) return 0;
	    // derive percent from quantized samples; consistent rounding yields deterministic results
	    int pct = Math.round((samples / maxSamples) * 100f);
	    return Math.max(0, Math.min(100, pct));
	}

	public static int samplesToPercent(int samples, int maxMs) {
		return samplesToPercent(samples, maxMs, Constants.sampleRate());
	}

	public static long samplesToMs(float samples, int sr) {
		return Math.round(samples * nisr(sr));
	}

	public static long samplesToMs(float samples) {
		return samplesToMs(samples, Constants.sampleRate());
	}

	public static int msToSamples(float ms, int sr) {
		return Math.max(0, Math.round(ms * nsr(sr)));
	}

	public static int msToSamples(float ms) {
		return msToSamples(ms, Constants.sampleRate());
	}

	// ---- Compatibility getters (percent-based) ----
	public int attackPct() { return samplesToPercent(attackSamples, MAX_ATTACK_MS); }
	public int decayPct() { return samplesToPercent(decaySamples, MAX_DECAY_MS); }
	public int releasePct() { return samplesToPercent(releaseSamples, MAX_RELEASE_MS); }
	public int sustainPct() { return (int) (sustainLevel * 100f); }

	// ---- Sample-derived helpers ----
	public int attackMs() { return Math.round(attackSamples * nisr(sr)); }
	public int decayMs() { return Math.round(decaySamples * nisr(sr)); }
	public int releaseMs() { return Math.round(releaseSamples * nisr(sr)); }

	public Letter overrideAttack(int samples) {
		return new Letter(Math.max(0, samples), decaySamples, sustainLevel, releaseSamples, sr);
	}
	public Letter overrideDecay(int samples) {
		return new Letter(attackSamples, Math.max(0, samples), sustainLevel, releaseSamples, sr);
	}
	public Letter overrideSustain(float sus) {
		return new Letter(attackSamples, decaySamples, Math.max(0f, Math.min(1f, sus)), releaseSamples, sr);
	}
	public Letter overrideRelease(int samples) {
		return new Letter(attackSamples, decaySamples, sustainLevel, Math.max(0, samples), sr);
	}

	public Letter withAttackSamples(int samples) {
		return new Letter(Math.max(0, samples), decaySamples, sustainLevel, releaseSamples, sr);
	}

	public Letter withDecaySamples(int samples) {
		return new Letter(attackSamples, Math.max(0, samples), sustainLevel, releaseSamples, sr);
	}

	public Letter withSustain(float sus) {
		return new Letter(attackSamples, decaySamples, Math.max(0f, Math.min(1f, sus)), releaseSamples, sr);
	}
	public Letter withReleaseSamples(int samples) {
		return new Letter(attackSamples, decaySamples, sustainLevel, Math.max(0, samples), sr);
	}

//	public Letter withSmoothMs(int ms) {
//		return new Letter(attackSamples, decaySamples, sustainLevel, releaseSamples, sr);
//	}

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

	public static long clampAtkMs(long ms) {
		if (ms > MAX_ATTACK_MS)
			RTLogger.debug(Letter.class, "Attack ms " + ms + " exceeds max " + MAX_ATTACK_MS + ", clamped.");
		return Math.min(Math.max(0, ms), MAX_ATTACK_MS);
	}

	public static long clampDkMs(long ms) {
		if (ms > MAX_DECAY_MS)
			RTLogger.debug(Letter.class, "Decay ms " + ms + " exceeds max " + MAX_DECAY_MS + ", clamped.");
		return Math.min(Math.max(0, ms), MAX_DECAY_MS);
	}

	public Letter withAttackMs(long ms) {
		ms = clampAtkMs(ms);
		return withAttackSamples(msToSamples(ms));
	}

	public Letter withDecayMs(long ms) {
		ms = clampDkMs(ms);
		return withDecaySamples(msToSamples(ms));
	}

	public Postage getAdsr() {
		 return new Postage(attackMs(), decayMs(), sustainLevel, releaseMs());
	}

}
