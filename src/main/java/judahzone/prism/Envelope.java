package judahzone.prism;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import judahzone.api.OffOn;
import judahzone.data.Letter;
import judahzone.data.Postage;
import judahzone.util.Curve;
import judahzone.util.Phase;
import lombok.Getter;

/**
 * Envelope implements AD or ADSR architecture backed by reusable Segment components
 * and driven by a Letter spec that interacts with percent, millisecond or sample based units;
 * Envelope exposes both per-sample and buffer-based 'process' APIs, don't use them in conjunction.</p>
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
 *
 * <p>Supports over-sampling (input Letter objects need to have adjusted sampling rate set.)</p>
 */
@PrismRT public class Envelope {

	private final AtomicReference<Letter> spec = new AtomicReference<>(new Letter(1, 100, 0f, 0));
	private final Segment[] segments;
	private final int size;

	private volatile boolean triggered;
	private volatile boolean releasing;
	@Getter
	private float lastSample;

	public static enum Delta {
		IDLE, ATK, DK, SUS, RLS
	};

	private final List<OffOn> phases = new CopyOnWriteArrayList<>();

	public Envelope(Postage env) {
		this(new Letter(env));
	}

	public Envelope() {
		this(new Letter(1, 100, 0f, 0));
	}

	public Envelope(Letter letter) {
		int target = 4;
		if (letter.sustainLevel() <= 0f)
			target--;
		if (letter.releaseSamples() <= 0)
			target--;
		size = target;
		segments = new Segment[size];

		int rampSamples = Math.max(1, Letter.msToSamples(Letter.SMOOTH_MS, letter.sr()));

		segments[0] = new Segment(Delta.ATK, Math.max(0, letter.attackSamples()), rampSamples, Curve.LINEAR, 0f, 1f);

		segments[1] = new Segment(Delta.DK, Math.max(0, letter.decaySamples()), rampSamples, Curve.EXPONENTIAL, 1f,
				letter.sustainLevel());

		if (size > 2)
			segments[2] = new Segment(Delta.SUS, Integer.MAX_VALUE, rampSamples, Curve.SUS, letter.sustainLevel(),
					letter.sustainLevel());
		if (size > 3)
			segments[3] = new Segment(Delta.RLS, Math.max(0, letter.releaseSamples()), rampSamples, Curve.EXPONENTIAL,
					letter.sustainLevel(), 0f);

		// Set spec without triggering surgical logic (segments are not yet running)
		spec.set(letter);
	}

	@PrismUI
	public void sendLetter(Letter l) {
		if (l == null)
			return;
		Letter old = spec.get();
		spec.set(l);

		// Check active stage. Note: this is a UI-thread read of audio state, not atomic,
		// but acceptable since we only use it to avoid disrupting currently-running segments.
		Delta active = getStage();

		for (Segment s : segments) {
			if (s == null)
				continue;
			boolean isActive = (s.stage == active);

			switch (s.stage) {
			case ATK -> {
				if (l.attackSamples() != old.attackSamples()) {
					// Only update if active. If inactive, the new length will be picked up on next trigger.
					if (isActive)
						s.updateLength(l.attackSamples());
				}
			}
			case DK -> {
				if (l.decaySamples() != old.decaySamples()) {
					if (isActive)
						s.updateLength(l.decaySamples());
				}
				if (l.sustainLevel() != old.sustainLevel()) {
					if (isActive)
						s.updateEndLevel(l.sustainLevel());
				}
			}
			case SUS -> {
				if (l.sustainLevel() != old.sustainLevel()) {
					// sustain is infinite, length doesn't change, just the level.
					// we use updateEndLevel to ensure it's smoothed by the ramp
					s.updateEndLevel(l.sustainLevel() <= 0f ? 0f : l.sustainLevel());
				}
			}
			case RLS -> {
				if (l.releaseSamples() != old.releaseSamples()) {
					if (isActive)
						s.updateLength(l.releaseSamples());
				}
			}
			default -> {
			}
			}
		}
	}

	@PrismUI
	public OffOn addPhase(OffOn phase) {
		phases.add(phase);
		return phase;
	}

	@PrismUI
	public void removePhase(Phase phase) {
		phases.remove(phase);
	}

	private void off() {
		triggered = false;
		lastSample = 0f;
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
	            s.setStartLevel(lastSample); // Smooth transition into release
	            hasRls = true;
	        }
	    }
	    if (hasRls) {
	        // Short-circuit all non-release segments
	        for (Segment s : segments) {
	            if (s != null && s.stage != Delta.RLS) {
	                s.updateLength(0);
	            }
	        }
	        releasing = true;
	        triggered = true;
	    } else {
	        for (Segment s : segments)
	            if (s != null)
	                s.setLength(0);
	        releasing = false;
	        off();
	    }
	}


	public int process(float[] mono) {
		return process(mono, mono.length);
	}

	/**Apply envelope slope to mono signal.
	 * @return number of frames processed while Envelope was open, remainder zeroed.
	 *         <ul><li>0: envelope is/was likely idle/off.</li>
	 *             <li>&lt; N_FRAMES: envelope went idle mid-buffer, remainder zeroed.</li>
	 *         	   <li>N_FRAMES: isPlaying is true</li> </ul> */
	@PrismRT
	public int process(float[] mono, int len) {
		if (!triggered) {
			for (Segment s : segments)
				if (s != null && !s.isComplete())
					s.setLength(0);
			// Use Arrays.fill to zero the caller buffer safely even when len >
			// Memory.ZERO.length
			Arrays.fill(mono, 0, len, 0f);
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
				// zero current position and remainder (use Arrays.fill for safe sizing)
				Arrays.fill(mono, i, len, 0f);
				off();
				break;
			}

			// otherwise count this sample as processed
			processed++;
		}

		// If we completed the full buffer and envelope is no longer playing, ensure off()
		if (processed >= len && !isPlaying())
			off();

		return processed;
	}

	@PrismRT
	public float process() {
		return triggered ? nextEnvSample() : 0f;
	}

	@PrismRT
	public void trigger() {
		trigger(Letter.SMOOTH_MS);
	}

	@PrismUI
	public void trigger(float smoothMS) {
		float reentryLevel = Math.max(0f, Math.min(1f, lastSample)); // lastSample is good if re-trigger

		// Reset all segments for a fresh trigger
		for (Segment s : segments) {
			if (s != null) {
				s.reset(); // Mark pos=0, complete=false
				if (s.stage == Delta.ATK) {
					s.setStartLevel(reentryLevel); // Crossfade from last sample
				}
			}
		}

		for (int i = 0; i < phases.size(); i++)
			phases.get(i).trigger();

		triggered = true;
		releasing = false;
	}

	private void refreshSegmentFromSpec(Segment s) {
		if (s == null)
			return;
		Letter l = spec.get();
		switch (s.stage) {
		case ATK -> s.setLength(l.attackSamples());
		case DK -> {
			s.setLength(l.decaySamples());
			s.setEndLevel(l.sustainLevel());
		}
		case SUS -> {
			s.setInfinite(); // Restore infinite nature if clobbered
			s.setEndLevel(l.sustainLevel() <= 0f ? 0f : l.sustainLevel());
		}
		case RLS -> s.setLength(l.releaseSamples());
		default -> {
		}
		}
	}

	private float nextEnvSample() {
		for (int i = 0; i < size; i++) {
			Segment s = segments[i];
			if (s.isComplete())
				continue;

			// Refresh segment config from current spec when it becomes active (first sample, pos == 0)
			// This ensures live parameter changes take effect at the transition.
			if (s.isActivating()) {
				refreshSegmentFromSpec(s);
			}

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
			if (s.isComplete())
				continue;
			if (s.stage == Delta.SUS) {
				if (!releasing)
					return true;
			} else if (s.stage == Delta.RLS) {
				if (releasing)
					return true;
			} else { // ATK, DK
				if (!releasing)
					return true;
			}
		}
		return false;
	}

	public Delta getStage() {
		if (!triggered)
			return Delta.IDLE;
		for (Segment s : segments) {
			if (s.isComplete())
				continue;
			if (s.stage == Delta.SUS) {
				if (!releasing)
					return Delta.SUS;
				continue;
			}
			if (s.stage == Delta.RLS) {
				if (releasing)
					return Delta.RLS;
				continue;
			}
			return s.stage;
		}
		return Delta.IDLE;
	}

	public void setAttack(int v) {
		sendLetter(spec.get().withAttackPct(v));
	}
	public void setDecay(int v) {
		sendLetter(spec.get().withDecayPct(v));
	}
	public void setAttackMs(long ms) {
		sendLetter(spec.get().withAttackMs(ms));
	}
	public void setDecayMs(long ms) {
		sendLetter(spec.get().withDecayMs(ms));
	}
	public int getAttackSamples() {
		return spec.get().attackSamples();
	}
	public int getDecaySamples() {
		return spec.get().decaySamples();
	}
	public int getAttack() {
		return spec.get().attackPct();
	}
	public int getDecay() {
		return spec.get().decayPct();
	}
	public long getAttackMs() {
		return spec.get().attackMs();
	}
	public long getDecayMs() {
		return spec.get().decayMs();
	}
	public Postage getPostage() {
		Letter postit = spec.get();
		return new Postage(postit.attackMs(), postit.decayMs());
	}

}

/*
Future Attack Envelope Variants
Clap & OHat: Use transientFrames with linear ramp envelope:
trans = (i < transFrames) ? 1.0f - i * invTrans : 0f
Both also implement blend mechanics between crisp vs. smooth attack profiles, suggesting envelope shape control is important.
Kick & Snare: Use atkFrames from parent, applying standard linear attack, but Kick adds click transient with separate clickFrames (30% of attack).
Stick: Similar pattern—clickFrames = 20% of atkFrames, with dual-phase attack (body + click).

Future Decay Envelope Patterns
Kick, Snare, Stick: Standard linear decay via invDecay multiplier.
Clap: Multi-layer decay with per-layer layerDecay[l] and layerInvDecay[l] arrays—each layer decays independently. Critical detail: decay is modulated by room parameter (0.6f + room * 0.8f).
OHat & Ride: Implement choke envelope—volatile chokeFramesLeft / chokeTotalFrames for hi-hat pedal closure. Multiplicative ramp during choke.
*/
