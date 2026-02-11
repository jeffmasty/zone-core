package judahzone.data;

/**
 * Small utility helpers for MIDI <-> Hz conversions using the common
 * reference A4 = MIDI 69 = 440.0 Hz.
 *
 * All primary conversion methods return floats (single precision).
 * Equal-tempered 12-tone temperament is assumed.
 */
public final class Frequency {

	public static final float MIN = 27.5f; // lowest note A0
	public static final float MAX = 16_000;	// G9 NoteOn127 = 12543.85 hz
    /** Standard reference: A4 = MIDI 69 = 440 Hz */
    public static final int REFERENCE_MIDI = 69;
    public static final float REFERENCE_FREQUENCY = 440.0f;
    private static final int MIDI_MIN = 0;
    private static final int MIDI_MAX = Byte.MAX_VALUE;// 127;
    private static final float[] MIDI_TO_HZ = new float[MIDI_MAX + 1];

    static {
        for (int midi = MIDI_MIN; midi <= MIDI_MAX; midi++)
            MIDI_TO_HZ[midi] = cacheMidiToHz(midi);
    }

	public final float hz;

	public Frequency(float hz) {
		this.hz = Math.max(MIN, Math.min(MAX, hz));
	}
	public int toData1() {
		return hzToMidi(hz);
	}


    private static float cacheMidiToHz(int midi) {
        double exponent = (midi - REFERENCE_MIDI) / 12.0;
        return (float) (REFERENCE_FREQUENCY * Math.pow(2.0, exponent));
    }

    /**Convert MIDI note number to frequency in Hz using standard reference (69 = 440 Hz).
     * Returns a float for single-precision usage.*/
    public static float midiToHz(int midi) {
    	midi = Math.max(MIDI_MIN, Math.min(MIDI_MAX, midi));
    	return MIDI_TO_HZ[midi];
    }

    static final double inverseLog2 = 1.0 / Math.log(2.0);
    /**Convert frequency in Hz to fractional MIDI number using an explicit reference.
     * midi = referenceMidi + 12 * log2(frequency / referenceFreq)
     *
     * Throws IllegalArgumentException if frequency <= 0.
     * Returns a float. */
    public static float hzToMidi(float frequency, int referenceMidi, float referenceFreq) {
        if (frequency <= 0.0f)
            throw new IllegalArgumentException("frequency must be > 0");

        double ratio = frequency / referenceFreq;
        double midi = referenceMidi + 12.0 * (Math.log(ratio) * inverseLog2);
        return (float) midi;
    }

    /** Convert frequency in Hz rounded to the nearest integer MIDI note (standard reference). */
    public static int hzToMidi(float frequency) {
        return Math.round(hzToMidi(frequency, REFERENCE_MIDI, REFERENCE_FREQUENCY));
    }

}
