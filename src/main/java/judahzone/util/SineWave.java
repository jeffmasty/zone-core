package judahzone.util;

public class SineWave {

	private static final int SIZE = 1024;           // 1024 -> ~4KB table, good quality
    private static final int MASK = SIZE - 1;
    private static final float[] TABLE = new float[SIZE];

    static {
        for (int i = 0; i < SIZE; i++)
            TABLE[i] = (float) Math.sin(2.0 * Math.PI * i / SIZE);
    }

    public SineWave() { }

    /** phase in [0,1). Returns sin(2*pi*phase). Audio-thread safe, allocation-free. */
    public float forPhase(float phase) {
        // phase -= (int) phase;         // wrap phase into [0,1)
        float idx = phase * SIZE;
        int i = ((int) idx) & MASK;
        float frac = idx - (int) idx;
        int j = (i + 1) & MASK;
        // linear interpolation
        return TABLE[i] * (1.0f - frac) + TABLE[j] * frac;
    }

    /** Lower-cost nearest-sample lookup (faster, lower quality). */
    public float sinFromPhaseNearest(float phase) {
        phase -= (int) phase;
        int i = ((int)(phase * SIZE)) & MASK;
        return TABLE[i];
    }

	private static final double defaultFrequency = 440.;
	private static final double defaultAmplitude = 0.75;
	private static final double defaultSR = Constants.sampleRate();

    public static double[] generateSineWave(double amplitude, double frequency, double samplingRate, int numSamples) {
        double[] sineWave = new double[numSamples];
        double angularFrequency = 2 * Math.PI * frequency / samplingRate;

        for (int i = 0; i < numSamples; i++) {
            sineWave[i] = amplitude * Math.sin(angularFrequency * i);
        }

        return sineWave;
    }

    public static void test() {
        int numSamples = 1024; // Number of samples
        double[] sineWave = generateSineWave(defaultAmplitude, defaultFrequency, defaultSR, numSamples);

        // Print the generated sine wave samples
        for (int i = 0; i < numSamples; i++) {
            System.out.println("Sample " + i + ": " + sineWave[i]);
        }
    }

    /**Fill `buf` with a sine wave at freqHz. Returns the updated phase (radians) to use
	 * for the next call so the tone is continuous across buffers.
	 * @param buf       float[] buffer to fill (length = FFT_SIZE)
	 * @param freqHz    desired frequency in Hz
	 * @param sampleRate sample rate in Hz (e.g. 48000.0)
	 * @param amplitude amplitude (0..1)
	 * @return new phase in radians to pass to the next call
	 */
	public static double fill(float[] buf, double freqHz, double sampleRate, double amplitude, double phase) {

		if (buf == null || buf.length == 0) return phase;
	    final double twoPi = 2.0 * Math.PI;
	    // phase increment per sample (radians)
	    final double phaseInc = twoPi * freqHz / sampleRate;

	    // keep amplitude safe
	    final double a = Math.max(0.0, Math.min(1.0, amplitude));

	    for (int i = 0; i < buf.length; i++) {
	        buf[i] = (float) (a * Math.sin(phase));
	        phase += phaseInc;
	        // wrap phase into [0, 2PI) to avoid unbounded growth
	        if (phase >= twoPi) phase -= twoPi * Math.floor(phase / twoPi);
	    }
	    return phase;
	}

	public static double fill(float[] buf) {
    	return fill(buf, defaultFrequency, defaultSR, defaultAmplitude, 0);
    }

    public static double fill(float[] buf, double phase) { // chain
    	return fill(buf, defaultFrequency, defaultSR, defaultAmplitude, phase);
    }

}

