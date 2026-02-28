package judahzone.util;

/** Simple one-pole DC blocker (high-pass ~ few Hz).  RT-safe, no allocations. */
public final class DCBlock {
    private float R;        // pole coefficient
    private float x1 = 0f;  // x[n-1]
    private float y1 = 0f;  // y[n-1]

    /** Create with sampleRate and cutoffHz (e.g., 5..20 Hz). */
    public DCBlock(float sampleRate, float cutoffHz) {
        setCutoff(sampleRate, cutoffHz);
    }

    public void setCutoff(float sampleRate, float cutoffHz) {
        float omega = (float) (2.0 * Math.PI * cutoffHz / sampleRate);
        // simple bilinear approx for very low cutoff
        R = 1f - omega; // conservative; keeps stability and low CPU
        if (R < 0f) R = 0f;
        if (R > 0.9999f) R = 0.9999f;
    }

    /** Process one sample. */
    public float process(float x) {
        // y[n] = x[n] - x[n-1] + R * y[n-1]
        float y = x - x1 + R * y1;
        x1 = x;
        y1 = y;
        return y;
    }

    public void reset() { x1 = 0f; y1 = 0f; }
}
