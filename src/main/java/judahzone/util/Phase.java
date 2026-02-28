package judahzone.util;

import judahzone.api.OffOn;

/** Lightweight phase accumulator with linear retrigger blending.
    Prevents DC clicks by crossfading between interrupted and new phase states.
    <p> Either call next() or step/get, they are not thread-safe in collision.
    <p> Thread-safety: Phase is explicitly not thread-safe. That’s fine for audio-thread-only use,
    	but avoid calling trigger/reset from a separate non-RT thread without appropriate synchronization
    	or an RT-safe message queue. Data points are not expected to be displayed by a GUI. */
public class Phase implements OffOn {
    float phase = 0f;
    private float ghost = 0f;
    private int counter = 0;
    private int duration = 0;
    private float inverseDur = 1f;
    private boolean active = false;

    private final int blendSamples;

    public Phase() {
    	this(511);
    }

    public Phase(int blendSamples) {
		this.blendSamples = Math.max(1, blendSamples);
	}

    /** Prepares a blend from the current phase to 0.0 over specified samples. */
    @Override
	public void trigger() {
        ghost = phase;
        phase = 0f;
        duration = Math.max(1, blendSamples);
        inverseDur = 1f / duration;
        counter = 0;
        active = true;
    }

    /** Hard reset to zero without blending. */
    @Override
	public void reset() {
        phase = 0f;
        ghost = 0f;
        active = false;
        counter = 0;
        duration = 0;
        inverseDur = 1f;
    }

    /** Compute blend alpha, clamped to [0, 1]. */
    private float alpha() {
        float a = counter * inverseDur;
        return Math.max(0f, Math.min(1f, a));
    }

    /** Advances internal phase and projects the ghost phase if blending.
        @return the blended phase value for use in a lookup table. */
    public float next(float inc) {
        float a = active ? alpha() : 1f;
        float out = ghost * (1.0f - a) + phase * a;

        // Advance both phases to maintain continuity during blend.
        if (active) {
            ghost += inc;
            ghost -= (int) ghost;
            counter++;
            if (counter >= duration)
                active = false;
        }

        phase += inc;
        phase -= (int) phase;
        return out;
    }

    /** Manual advance for components that separate calculation from retrieval. */
    public void step(float inc) {
        phase += inc;
        phase -= (int) phase;
        if (active) {
            ghost += inc;
            ghost -= (int) ghost;
            counter++;
            if (counter >= duration)
                active = false;
        }
    }

    /** Returns the current phase, blended if a retrigger is active. */
    public float get() {
        float a = active ? alpha() : 1f;
        return ghost * (1.0f - a) + phase * a;
    }

    /////////////////////
    /** Sine Lookup Table (LUT) SIZE: 1024
	•  Precision: 1024 entries ≈ 10 bits → ~0.1 dB error (acceptable for audio).
	•  Memory: ~4 KB (1024 × 4 bytes float). Negligible; fits cache L1.
 	•  Modulation: One multiply per sample to scale output by index/level
 	•  Callers: use interpolation: Linear or 3-tap Lagrange adds ~2–3 cycles, eliminates audible stepping.*/
	private static final int SIZE = 1024;
    private static final int MASK = SIZE - 1;
    private static final float[] TABLE = new float[SIZE]; // lives in L1 cache next to kernel

    static {
        for (int i = 0; i < SIZE; i++)
            TABLE[i] = (float) Math.sin(2.0 * Math.PI * i / SIZE);
    }

    /**@param phase in [0,1).
     * @return sin(2*pi*phase). */
    public static float sin(float phase) {
        float idx = phase * SIZE;
        int i = ((int) idx) & MASK;
        float frac = idx - (int) idx;
        int j = (i + 1) & MASK;
        // linear interpolation
        return TABLE[i] * (1.0f - frac) + TABLE[j] * frac;
    }


}
