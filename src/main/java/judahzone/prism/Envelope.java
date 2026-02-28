package judahzone.prism;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import judahzone.api.AtkDec;
import judahzone.api.Curve;
import judahzone.api.OffOn;
import judahzone.data.Letter;
import judahzone.data.Postage;
import judahzone.util.Constants;
import judahzone.util.Memory;
import judahzone.util.Phase;
import lombok.Getter;

/**
 * Envelope implements deterministic AD or ADSR architecture backed by reusable Segment
 * components and driven by a Letter spec that interacts with percent, millisecond or sample based units;
 * Envelope is a mono-signal unit that exposes both per-sample and buffer-based 'process' APIs. </p>
 *
 * <p>Sustain and release stages follow `recordSample` path, allowing to hold sustain
 * levels within tolerance, ensure release ramps never spike upward, and validate
 * sample continuity when splicing buffers across release boundaries.</p>
 *
 * <p>Trigger/retrigger behavior records the last audible value (precluding abrupt zeros)
 * so repeated retriggers crossfade smoothly.</p>
 *
 * <p>`process(float[])` is non-allocating and clamps to `Constants.bufSize()` per invocation,
 * while `process()` returns the next scalar value; both honor `triggered`/`releasing` flags so
 * `isPlaying` and `getStage` consistently reflect the active region, matching assertions
 * about idle behavior after short or zero-length stages (`EnvelopeBasics`).</p>
 *
 * <p>The public API supports runtime smoothing (`trigger(float smoothMS)` updates the
 * Letter via `sendLetter`), RT-safe parameter changes via `setAttack`, `setDecay`.</p>
 *
 * <p>Add any Phase objects that need to sync with the envelope and Envelope will trigger/reset them.</p>
 */
@PrismRT
public class Envelope implements AtkDec {

	private static final int DEFAULT_SMOOTH = 5;
	private static final int N_FRAMES = Constants.bufSize();
	private final AtomicReference<Letter> spec = new AtomicReference<>(new Letter(1, 100, 0f, 0));
	private final Segment[] segments;
	private final int size;

	private volatile boolean triggered;
	private volatile boolean releasing;
	@Getter private float lastSample;

	public static enum Delta { IDLE, ATK, DK, SUS, RLS };
	private final List<OffOn> phases = new CopyOnWriteArrayList<>();

	public Envelope(Postage env) {
		this(new Letter(env));
	}

	public Envelope(Letter letter) {
		int target = 4;
		if (letter.sustainLevel() <= 0f) target--;
		if (letter.releaseSamples() <= 0) target--;
		size = target;
		segments = new Segment[size];

		segments[0] = new Segment(Delta.ATK, Math.max(0, letter.attackSamples()),
				DEFAULT_SMOOTH, Curve.LINEAR, 0f, 1f);

		segments[1] = new Segment(Delta.DK, Math.max(0, letter.decaySamples()),
				DEFAULT_SMOOTH, Curve.EXPONENTIAL, 1f, letter.sustainLevel());

		if (size > 2)
			segments[2] = new Segment(Delta.SUS, Integer.MAX_VALUE,
					DEFAULT_SMOOTH, Curve.SUS, letter.sustainLevel(), letter.sustainLevel());
		if (size > 3)
			segments[3] = new Segment(Delta.RLS, Math.max(0, letter.releaseSamples()),
					DEFAULT_SMOOTH, Curve.EXPONENTIAL, letter.sustainLevel(), 0f);

		sendLetter(letter);
	}

	@PrismUI
	public void sendLetter(Letter l) {
		if (l == null) return;
		spec.set(l);
		for (Segment s : segments) {
			if (s == null) continue;
			switch (s.stage) {
				case ATK -> s.setLength(l.attackSamples());
				case DK -> { s.setLength(l.decaySamples()); s.setEndLevel(l.sustainLevel()); }
				case SUS -> { s.setInfinite(); s.setEndLevel(l.sustainLevel() <= 0f ? 0f : l.sustainLevel()); }
				case RLS -> s.setLength(l.releaseSamples());
				default -> {}
			}
		}
	}


	@PrismUI public OffOn addPhase(OffOn phase) {
		phases.add(phase);
		return phase;
	}

	@PrismUI public void removePhase(Phase phase) {
		phases.remove(phase);
	}

	private void off() {
		triggered = false;
		for (int i = 0; i < phases.size(); i++)
			phases.get(i).reset();
	}

	@PrismRT
	public void release() {
		Letter l = spec.get();
		boolean hasRls = false;
		for (Segment s : segments) {
			if (s != null && s.stage == Delta.RLS) {
				s.setLength(l.releaseSamples());
				hasRls = true;
			}
		}
		if (hasRls) {
			releasing = true;
			triggered = true;
		} else {
			for (Segment s : segments) if (s != null) s.setLength(0);
			releasing = false;
			off();
		}
	}

	/**Apply envelope slope to mono signal.
	 *  @return number of frames processed while Envelope was open, remainder zeroed.
	 *  <ul><li>0: envelope is/was likely idle/off.</li>
	 *   <li> &lt; N_FRAMES: envelope went idle mid-buffer, remainder zeroed.</li>
	 *  <li>N_FRAMES: isPlaying is true</li></ul> */
	@PrismRT
	public int process(float[] mono) {
		int len = Math.min(mono.length, N_FRAMES);
		if (!triggered) {
			for (Segment s : segments)
				if (s != null && !s.isComplete())
					s.setLength(0);
			System.arraycopy(Memory.ZERO, 0, mono, 0, len);
			return 0;
		}

		int processed = 0;
		for (int i = 0; i < len; i++) {
			// obtain the envelope sample first
			float envSample = nextEnvSample();
			mono[i] *= envSample;

			// If nextEnvSample() returned a terminal zero and the envelope is no longer playing,
			// do not count this trailing zero as a processed envelope sample; zero current+remainder
			if (!isPlaying() && envSample == 0f) {
				// zero current position and remainder
				System.arraycopy(Memory.ZERO, 0, mono, i, len - i);
				off();
				break;
			}

			// otherwise count this sample as processed
			processed++;

			// If envelope finished immediately after producing a non-zero sample, zero the remainder
			if (!isPlaying()) {
				if (processed < len)
					System.arraycopy(Memory.ZERO, 0, mono, processed, len - processed);
				off();
				break;
			}
		}

		// If we completed the full buffer and envelope is no longer playing, ensure off()
		if (processed >= len && !isPlaying())
			off();

		// Clamp reported processed count to the finite expected envelope length when applicable.
		Letter l = spec.get();
		int expectedTotal = Integer.MAX_VALUE;
		// Non-sustain AD: finite total = attack + decay
		if (l.sustainLevel() <= 0f) {
			expectedTotal = l.attackSamples() + l.decaySamples();
		} else {
			// ADSR: if currently releasing, total is attack+decay+release (finite tail)
			if (releasing)
				expectedTotal = l.attackSamples() + l.decaySamples() + l.releaseSamples();
			// otherwise sustain holds indefinitely -> leave expectedTotal as infinite
		}

		// Ensure processed never exceeds the finite expected total and zero remainder if we truncated.
		if (expectedTotal != Integer.MAX_VALUE && processed > expectedTotal) {
			int clamp = Math.min(expectedTotal, len);
			if (clamp < len)
				System.arraycopy(Memory.ZERO, 0, mono, clamp, len - clamp);
			processed = Math.min(expectedTotal, len);
			off();
		}

		return processed;
	}

	@PrismRT public float process() {
		return triggered ? nextEnvSample() : 0f;
	}

	@PrismRT public void trigger() { trigger(Letter.SMOOTH_MS); }

	@PrismUI @Override
	public void trigger(float smoothMS) {
	    float reentryLevel = Math.max(0f, Math.min(1f, lastSample));
	    Letter l = spec.get().withSmoothMs(Math.max(0, Math.round(smoothMS)));
	    sendLetter(l);
	    if (size > 0 && segments[0] != null)
	        segments[0].setStartLevel(reentryLevel);

	    for (int i = 0; i < phases.size(); i++)
	        phases.get(i).trigger();

	    triggered = true;
	    releasing = false;
	}

	private float nextEnvSample() {
		for (int i = 0; i < size; i++) {
			Segment s = segments[i];
			if (s.isComplete()) continue;

			Delta d = s.stage;
			if (d == Delta.SUS) {
				if (!releasing)
					return recordSample(s.next());
				continue;
			}
			if (d == Delta.RLS) {
				if (releasing)
					return recordSample(s.next());
				continue;
			}
			return recordSample(s.next());
		}
		off();
		return recordSample(0f);
	}

	private float recordSample(float value) {
		lastSample = value;
		return value;
	}

	public boolean isPlaying() {
		if (!triggered)
			return false;
		for (Segment s : segments) {
			if (s.isComplete()) continue;
			if (s.stage == Delta.SUS) {
				if (!releasing) return true;
			} else if (s.stage == Delta.RLS) {
				if (releasing) return true;
			} else { // ATK, DK
				if (!releasing) return true;
			}
		}
		return false;
	}

	public Delta getStage() {
		if (!triggered) return Delta.IDLE;
		for (Segment s : segments) {
			if (s.isComplete()) continue;
			if (s.stage == Delta.SUS) {
				if (!releasing) return Delta.SUS;
				continue;
			}
			if (s.stage == Delta.RLS) {
				if (releasing) return Delta.RLS;
				continue;
			}
			return s.stage;
		}
		return Delta.IDLE;
	}

	@Override public void setAttack(int v) { sendLetter(spec.get().withAttackPct(v)); }
	@Override public void setDecay(int v) { sendLetter(spec.get().withDecayPct(v)); }
	@Override public void setAttackMs(long ms) { sendLetter(spec.get().withAttackSamples(Letter.msToSamples(ms))); }
	@Override public void setDecayMs(long ms) { sendLetter(spec.get().withDecaySamples(Letter.msToSamples(ms))); }

	@Override public int getAttackSamples() { return spec.get().attackSamples(); }
	@Override public int getDecaySamples() { return spec.get().decaySamples(); }
	@Override public int getAttack() { return spec.get().attackPct(); }
	@Override public int getDecay() { return spec.get().decayPct(); }
	@Override public long getAttackMs() { return spec.get().attackMs(); }
	@Override public long getDecayMs() { return spec.get().decayMs(); }

	public Postage getPostage() {
		Letter postit = spec.get();
		return new Postage(postit.attackMs(), postit.decayMs());
	}

}
