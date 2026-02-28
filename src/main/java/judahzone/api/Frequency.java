package judahzone.api;

import java.awt.Point;

/**Small utility helpers for MIDI <-> Hz conversions using the common •
 * reference A4 = MIDI 69 = 440.0 Hz. * • All primary conversion methods return
 * floats (single precision). • Equal-tempered 12-tone temperament is assumed.
 * LUT: Precompute MIDI->Hz for MIDI 0..127 and clamp to [MIN, MAX] range.
 * */


public final class Frequency {

	public static final float MIN = 27.5f; // lowest note A0
	public static final float MAX = 16_000; // G9 = 12543.85 hz
	/** Standard reference: A4 = MIDI 69 = 440 Hz */
	public static final int REFERENCE_MIDI = 69;
	public static final float REFERENCE_FREQUENCY = 440.0f;
	private static final int MIDI_MIN = 0;
	private static final int MIDI_MAX = Byte.MAX_VALUE;// 127;
	private static final double INV_LOG2 = 1.0 / Math.log(2.0);

	public static final float[] LUT = new float[MIDI_MAX + 1];
	static {
		for (int midi = MIDI_MIN; midi <= MIDI_MAX; midi++)
			LUT[midi] = clampMidiToHz(cacheMidiToHz(midi));
	}

	private static float cacheMidiToHz(int midi) {
		double exponent = (midi - REFERENCE_MIDI) / 12.0;
		return (float) (REFERENCE_FREQUENCY * Math.pow(2.0, exponent));
	}

	/** Clamp MIDI->Hz to the allowable [MIN, MAX] range. */
	private static float clampMidiToHz(float hz) {
		if (hz < MIN)
			return MIN;
		if (hz > MAX)
			return MAX;
		return hz;
	}

	/**
	 * Convert MIDI note number to frequency in Hz using standard reference (69 =
	 * 440 Hz). Returns a float for single-precision usage.
	 */
	public static float midiToHz(int midi) {
		midi = Math.max(MIDI_MIN, Math.min(MIDI_MAX, midi));
		return LUT[midi];
	}

	public static int hzToMidi(float frequency) {
	    if (frequency <= 0.0f)
	        throw new IllegalArgumentException("frequency must be > 0");

	    final int n = LUT.length;
	    if (n == 0)
	        throw new IllegalStateException("Frequency LUT is empty");

	    // Clamp to LUT endpoints for out-of-range values
	    if (frequency <= LUT[0]) return 0;
	    if (frequency >= LUT[n - 1]) return n - 1;

	    // Binary search to find insertion point: lo will be first index > frequency
	    int lo = 0, hi = n - 1;
	    while (lo <= hi) {
	        int mid = (lo + hi) >>> 1;
	        float v = LUT[mid];
	        if (v == frequency) return mid;
	        if (v < frequency) lo = mid + 1;
	        else hi = mid - 1;
	    }

	    int idxHigh = Math.min(lo, n - 1);
	    int idxLow = Math.max(0, lo - 1);

	    // Choose nearest of the two neighboring entries
	    float dLow = Math.abs(frequency - LUT[idxLow]);
	    float dHigh = Math.abs(LUT[idxHigh] - frequency);
	    return dLow <= dHigh ? idxLow : idxHigh;
	}

	/** @retrurn x=data1 y=cents */
	public static Point hzToMid(float frequency) {
		if (frequency <= 0.0f)
			throw new IllegalArgumentException("frequency must be > 0");

		double ratio = frequency / REFERENCE_FREQUENCY;
		double midi = REFERENCE_MIDI + 12.0 * (Math.log(ratio) * INV_LOG2);
		int x = (int)midi;
		float cents = (float) ((midi - x) * 100.0);
		return new Point(x, Math.round(cents));
	}

	public static float toFrequency(Note n) {
		return toFrequency(n.key(), n.octave());
	}

	public static float toFrequency(Key note, int octave) {
        int position = note.ordinal() + (octave + 1) * 12; // equal-tempered scale
        int semitones = position - REFERENCE_MIDI; // semitones from A4
        return (float) (REFERENCE_FREQUENCY * Math.pow(2, semitones / 12.0));
    }

	/** @return the nearest Note for hz */
	public static Note toNote(float hz) {
	   Key nearestKey = null;
	    int nearestOctave = 0;
	    float minDifference = Float.MAX_VALUE; // absolute
	    float difference = Float.MAX_VALUE; // actual
	    // Iterate through all keys and octaves to find the closest match
	    for (int octave = 0; octave <= 8; octave++) { // Assuming the range of octaves is 0 to 8
	        for (Key key : Key.values()) {
	            float frequency = toFrequency(key, octave);

	            float abs = Math.abs(frequency - hz);

	            if (abs < minDifference) {
	                minDifference = abs;
	                difference = frequency - hz;
	                nearestKey = key;
	                nearestOctave = octave;
	            }
	        }
	    }
	    return new Note(nearestKey, nearestOctave, -1 * difference);
	}


//	public static float hzToMidi(float frequency) {
//		if (frequency <= 0.0f)
//			throw new IllegalArgumentException("frequency must be > 0");
//
//		double ratio = frequency / REFERENCE_FREQUENCY;
//		double midi = REFERENCE_MIDI + 12.0 * (Math.log(ratio) * INV_LOG2);
//		return (float) midi;
//	}
//
//	/**
//	 * Convert frequency in Hz rounded to the nearest integer MIDI note (standard
//	 * reference).
//	 */
//	public static int hzToMidi(float frequency) {
//		return Math.round(hzToMidi(frequency, REFERENCE_MIDI, REFERENCE_FREQUENCY));
//	}

//	private static final int PITCH_LUT_SIZE = 128; // supports typical 0..127 control range
//	public static final float[] LUT = new float[PITCH_LUT_SIZE];
//	static {
//		// Inclusive logarithmic mapping from MIN..MAX so endpoints are exact.
//		final double ratio = (double) MAX / (double) MIN;
//		final int n = PITCH_LUT_SIZE - 1;
//		for (int i = 0; i < PITCH_LUT_SIZE; i++) {
//			double frac = (n == 0) ? 0.0 : (double) i / n;
//			double val = MIN * Math.pow(ratio, frac);
//			LUT[i] = (float) val;
//		}
//	}

}