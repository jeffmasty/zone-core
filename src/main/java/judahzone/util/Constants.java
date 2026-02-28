package judahzone.util;

import java.util.List;

import lombok.Getter;

public class Constants {

	public static final String APP_NAME = "JudahZone";

	// TODO generalize
	public static int sampleRate() { return WavConstants.S_RATE; }
	public static int bufSize() { return WavConstants.JACK_BUFFER; }
	public static int fftSize() { return WavConstants.FFT_SIZE; }
	public static int amplitudeSize() { return WavConstants.AMPLITUDES; }
	public static float fps() { return WavConstants.FPS; }

	public static final float TWOPI = (float) (2.0 * Math.PI);
	public static final float I2PI = 1f / TWOPI;
	public static final float I2PI_SR = I2PI * sampleRate();
	public static final float IBUF = 1f / bufSize();
	public static final float ISR = 1f / sampleRate();

	/** Digital Interface name */
	@Getter static String di = "Komplete ";// "UMC1820 MIDI 1";
	public static final String LEFT_OUT = "system:playback_1";
	public static final String RIGHT_OUT = "system:playback_2";

	public static final String GUITAR_PORT = "system:capture_1";
	public static final String MIC_PORT = "system:capture_2";
	public static final String CRAVE_PORT = "system:capture_3";
	public static final String AUX_PORT = "system:capture_4";

	// hardcoded Names saved to Song's miditracks
	public static final String GUITAR = "Gtr";
	public static final String MIC = "Mic";
	public static final String BASS = "Bass";
	public static final String FLUID = "Fluid";
	public static final String MAIN = "Main";

    public static final int LEFT = 0;
	public static final int RIGHT = 1;
	public static final int MONO = 1;
	public static final int STEREO = 2;
	public static final int NORMAL = 100; // default max slider/knob value, one day this will be 127.

	public static final String NL = System.lineSeparator();
	public static final String CUTE_NOTE = "♫ ";
	public static final String DOT_MIDI = ".mid";
	public static final int ASCII_ONE = 49;


	/** milliseconds between checking the update queue */
	public static final int GUI_REFRESH = 8;
	public static final float TO_100 = 0.7874f; // 127 <--> 100
	public static final float TO_1 = TO_100 * 0.01f;

    /**@param data2 0 to 127
     * @return data2 / 127 */
	public static float midiToFloat(int data2) {
		return data2 * TO_1;
	}

    public static float computeTempo(long millis, int beats) {
    	return bpmPerBeat(millis / (float)beats);
    }

    public static float bpmPerBeat(float msec) {
        return 60000 / msec;
    }

	public static long millisPerBeat(float beatsPerMinute) {
		return (long) (60_000.0 / beatsPerMinute);
	}

	public static float toBPM(long delta, int beats) {
		return 60000 / (delta / (float)beats);
	}
///////////////////////////
//	public static Object ratio(int data2, List<?> input) {
//        return input.get((int) ((data2 - 1) / (100 / (float)input.size())));
//	}
//	public static Object ratio(int data2, Object[] input) {
//		return input[(int) ((data2 - 1) / 0.01f * input.length)];
//	}
//	public static int ratio(long data2, long size) {
//		return (int) (data2 / 0.01f *  size);
//	}
////////////////////////////////////////
	/** Map a knob percent (0..100) to an index in [0 .. size-1] safely. */
	public static int ratio(long percent, long size) {
	    if (size <= 0) return 0;
	    // clamp percent to 0..100
	    int p = (int) Math.max(0, Math.min(100, percent));
	    // Map 0 -> 0 and 100 -> size-1, with even distribution in between.
	    int idx = Math.round(p * (size - 1) / 100f);
	    if (idx < 0) idx = 0;
	    if (idx >= size) idx = (int) (size - 1);
	    return idx;
	}

	/** Return entry from list mapped by knob percent 0..100. */
	public static <T> T ratio(int percent, List<T> input) {
	    if (input == null || input.isEmpty()) throw new IllegalArgumentException("input list empty");
	    int idx = ratio(percent, input.size());
	    return input.get(idx);
	}

	/** Return entry from array mapped by knob percent 0..100. */
	public static <T> T ratio(int percent, T[] input) {
	    if (input == null || input.length == 0) throw new IllegalArgumentException("input array empty");
	    int idx = ratio(percent, input.length);
	    return input[idx];
	}
///////////////////////////
	public static int rotary(int input, int size, boolean up) {
		 input += (up ? 1 : -1);
		 if (input >= size)
    		input = 0;
    	if (input < 0)
    		input = size - 1;
    	return input;
	}

	public static float logarithmic(int percent) {
		return logarithmic(percent, 0, 1);
	}

    /** see https://stackoverflow.com/a/846249 */
	private static final int minp = 1;
	private static final int maxp = 100;

	public static float logarithmic(int percent, float min, float max) {


		// The result should be between min and max
		if (min <= 0) min = 0.0001f;
		double minv = Math.log(min);
		double maxv = Math.log(max);

		// calculate adjustment factor
		double scale = (maxv-minv) / (maxp-minp);
		return (float)Math.exp(minv + scale * (percent - minp));
	}
	public static int reverseLog(float ratio) {
		return reverseLog(ratio, 0, 1);
	}

	public static int reverseLog(float var, float min, float max) {
	    // branchless clamp: var = clamp(var, min, max)
	    var = Math.max(min, Math.min(max, var));
	    // ensure min is strictly positive for log computations
	    min = Math.max(min, 0.0001f);

	    double minv = Math.log(min);
	    double maxv = Math.log(max);
	    double scale = (maxv - minv) / (maxp - minp);

	    return (int)Math.round(minp + (Math.log(var) - minv) / scale);
	}

	/** Inverse-logarithmic mapping from knob percent (0..100) to float (0..1).
	 * concentrate resolution near 1.
	 * @param percent knob value 0..100
	 * @return mapped float value 0..1 */
	public static float inverseLog(int percent) {
        // Inverse-logarithmic mapping: concentrate resolution near high knob values.
        // Avoid passing 0 to Constants.logarithmic (expects 1..100).
        return 1.0f - Constants.logarithmic(Math.max(1, 100 - percent));

	}

	public static int reverseInverseLog(float var) {
	    // Inverse of inverse-logarithmic mapping.
	    // Clamp input to [0..1].
	    var = Math.max(0f, Math.min(1f, var));
	    if (var >= 1.0f - 1e-6f)
	        return 100;
	    int flipped = Constants.reverseLog(1.0f - var);
	    int knob = 100 - flipped;
	    if (knob < 0) knob = 0;
	    if (knob > 100) knob = 100;
	    return knob;
	}

	/**Linear interpolation mapping from source range to target range.
	 * @param value input value
	 * @param srcMin source range minimum
	 * @param srcMax source range maximum
	 * @param dstMin target range minimum
	 * @param dstMax target range maximum
	 * @return mapped value in target range */
	public static float interpolate(float value, float srcMin, float srcMax, float dstMin, float dstMax) {
	  return dstMin + (value - srcMin) * (dstMax - dstMin) / (srcMax - srcMin);
	}

	public static boolean isPow2(int x) {
	    return (x & (x - 1)) == 0;
	}

	public static int nextPow2(int v) {
	    if (v <= 1) return 1;
	    v--;
	    v |= v >> 1;
	    v |= v >> 2;
	    v |= v >> 4;
	    v |= v >> 8;
	    v |= v >> 16;
	    v++;
	    return v;
	}




	public static int toKnob(float value, float min, float max) {
		float normalized = Math.max(0f, Math.min(1f, (value - min) / (max - min)));
		return Math.round(normalized * 100f);
	}
//
	public static float fromKnob(int knob, float min, float max) {
		float normalized = Math.max(0f, Math.min(1f, knob * 0.01f));
		return min + normalized * (max - min);
	}
//
//
//	/** Logarithmic mapping helpers for the feedback knob to give more perceptual
//	    resolution near the high FB end. Uses v = min * (max/min)\^p where p in [0..1]. */
//	public static float fromKnobLog(int knob, float min, float max) {
//		float normalized = Math.max(0f, Math.min(1f, knob * 0.01f));
//		if (min <= 0f)
//			return decodeFromKnob(knob, min, max);
//		double ratio = max / min;
//		return (float)(min * Math.pow(ratio, normalized));
//	}
//
//	/** return value (ranged logarithmically between min and max) in the range 0...100 */
//	public static int toKnobLog(float value, float min, float max) {
//		reverseLog(
//		return (int)Math.round(p * 100d);
//	}


}