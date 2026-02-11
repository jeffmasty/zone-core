package judahzone.util;

/** Lightweight phase accumulator with linear retrigger blending.
    Prevents DC clicks by crossfading between interrupted and new phase states.
    <p> Either call next() or step/get, they are not thread-safe in collision.
    <p> Thread-safety: Phase is explicitly not thread-safe. That’s fine for audio-thread-only use, but avoid calling trigger/reset from a separate non-RT thread without appropriate synchronization or an RT-safe message queue. Data points are not expected to be displayed by a GUI. */
/* TODO
1. Ghost phase continuity: The ghost advances during blending, which is correct. But verify the blended output doesn't cause audible phase discontinuities when alpha transitions from 0→1. A quick listen test with a sine lookup is essential.
2. `step()` vs `next()` sync: Both mutate active state independently. If called inconsistently, they could desync. Consider a single internal method both call.
*/
public final class Phase {
    float phase = 0f;
    private float ghost = 0f;
    private int counter = 0;
    private int duration = 0;
    private float inverseDur = 1f;
    private boolean active = false;

    /** Prepares a blend from the current phase to 0.0 over specified samples. */
    public void trigger(int blendSamples) {
        ghost = phase;
        phase = 0f;
        duration = Math.max(1, blendSamples);
        inverseDur = 1f / duration;
        counter = 0;
        active = true;
    }

    /** Hard reset to zero without blending. */
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
}

