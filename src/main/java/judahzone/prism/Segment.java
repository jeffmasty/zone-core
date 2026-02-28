package judahzone.prism;

import judahzone.api.Curve;
import judahzone.data.Letter;
import judahzone.prism.Envelope.Delta;
import judahzone.util.Ramp;

/** Segment: reusable envelope segment with robust monotonic progress tracking.
    Switched from float-accumulation progress to integer sample counters to ensure
    deterministic completion at exactly N samples (avoids FP drift extending envelopes). */
public class Segment {

	public final Delta stage;
	public final Curve curve;

	private volatile int totalSamples;
	private int pos; // 0..totalSamples

	// nominal logical levels (kept for resets/construct)
	private float startLevel;
	private float endLevel;
	private volatile boolean complete = true;

	// Ramps for RT-safe smoothing of level changes
	private final Ramp startRamp;
	private final Ramp endRamp;

	public Segment(Delta stage, int length, long smoothMS, Curve curve, float startLevel, float endLevel) {
	    this.stage = stage;
	    this.curve = (curve == null) ? Curve.EXPONENTIAL : curve;
	    this.startLevel = Math.max(0f, Math.min(1f, startLevel));
	    this.endLevel = Math.max(0f, Math.min(1f, endLevel));

	    // compute ramp length in samples from smoothMS (ensure at least 1 sample)
	    int rampSamples = Math.max(1, Letter.msToSamples(Math.max(0L, smoothMS)));
	    this.startRamp = new Ramp(rampSamples);
	    this.endRamp = new Ramp(rampSamples);

	    // initialize ramps to match initial logical levels (no jump)
	    this.startRamp.reset(this.startLevel);
	    this.endRamp.reset(this.endLevel);

	    setLength(length);
	}

	public void reset() {
	    this.pos = 0;
	    this.complete = false;
	    // align ramps to current logical levels immediately on segment reset
	    this.startRamp.reset(this.startLevel);
	    this.endRamp.reset(this.endLevel);
	}

	public void setStartLevel(float level) {
	    this.startLevel = Math.max(0f, Math.min(1f, level));
	    this.startRamp.set(this.startLevel);
	}

	public Segment(Delta stage, int length, Curve curve) {
	    this(stage, length, Letter.SMOOTH_MS, curve, defaultStartFor(stage), defaultEndFor(stage));
	}

	private static float defaultStartFor(Delta d) { return d == Delta.ATK ? 0f : 1f; }
	private static float defaultEndFor(Delta d) { return (d == Delta.ATK || d == Delta.SUS) ? 1f : 0f; }

	public void setEndLevel(float level) {
	    this.endLevel = Math.max(0f, Math.min(1f, level));
	    this.endRamp.set(this.endLevel);
	}

	public void setLength(int length) {
	    int l = Math.max(0, length);
	    totalSamples = l;
	    pos = 0;
	    if (l <= 0) {
	        complete = true;
	    } else if (l == Integer.MAX_VALUE) {
	        complete = false;
	    } else {
	        this.complete = false;
	    }
	    // when length changes, keep ramps aligned to logical levels to avoid transient pops
	    startRamp.reset(startLevel);
	    endRamp.reset(endLevel);
	}

	public void setInfinite() {
	    totalSamples = Integer.MAX_VALUE;
	    pos = 0;
	    complete = false;
	    // keep ramps running (do not force completion) but ensure starting point is correct
	    startRamp.reset(startLevel);
	    endRamp.reset(endLevel);
	}

	public float next() {
	    if (complete) return endLevel;

	    // Advance ramps per-sample and use smoothed values
	    float curStart = startRamp.next();
	    float curEnd = endRamp.next();

	    // Infinite (sustain) segments: return smoothed end level
	    if (totalSamples == Integer.MAX_VALUE)
	        return curEnd;

	    // Deterministic normalized progress [0, 1): use fixed totalSamples, not smoothed
	    float normalized = (totalSamples <= 0) ? 1.0f : (pos / (float) totalSamples);
	    float c = curve.apply(normalized);
	    float out = c * curStart + (1.0f - c) * curEnd;

	    pos++;
	    if (pos >= totalSamples) {
	        pos = totalSamples;
	        complete = true;
	    }
	    return out;
	}

	public boolean isComplete() {
	    return complete && totalSamples != Integer.MAX_VALUE;
	}

	}