package judahzone.util;

/** Biquad — RT-aware contract for biquad-style filters (LP/HP/Peaking/AllPass/BandPass).
 *  Implementations are expected to:
 *   - compute coefficients off the audio thread in {@link #computeCoefficients()}
 *   - call {@link #markDirty()} after publishing new coefficients so the audio thread
 *     can ramp / interpolate per-block
 *   - avoid allocations inside {@link #process(float[])} / {@link #processStereo(float[],float[])}
 *  Implementations may reuse Filters.Coeffs and Filters helpers. */
public interface Biquad extends Filters {

    /* ----- Parameter API (non-RT callers) ----- */

    void setFrequency(float hz);
    float getFrequency();

    /** Q-style resonance; implementations may interpret as Q or bandwidth per type. */
    void setQ(float q);
    float getQ();

    /** Alternate bandwidth setter (if applicable for the subclass). */
    void setBandwidth(float bw);
    float getBandwidth();

    /** Gain for peaking/shelf filters in dB (no-op for types that ignore it). */ // resonance?
    void setGainDb(float db);
    float getGainDb();

    /** Recompute coefficients from current parameters. Must be called off the audio thread
     *  (or otherwise ensure it does not allocate). Implementations should publish plain
     *  float fields and then call {@link #markDirty()} so the audio thread can pick them up. */
    void computeCoefficients();

    /** Mark coefficients dirty so audio thread will perform interpolation on next block. */
    void markDirty();

    /* ----- Audio thread API (real-time) ----- */

    /** Process mono buffer in-place. Must not allocate. */
    void process(float[] mono);

    /** Process stereo buffers in-place. Right may be null — implementations may fallback to mono. */
    void processStereo(float[] left, float[] right);

    /** Reset internal state (clear delay / filter registers). Can be called off-RT but is cheap. */
    void reset();

    /* ----- Helpers for implementations (static utilities) ----- */

    /** Exact float equality check used for fast-path detection; acceptable because setters publish
     *  computed float coeffs (race is benign). */
    static boolean coeffsEqual(Coeffs a, Coeffs b) {
        return a.a0 == b.a0 && a.a1 == b.a1 && a.a2 == b.a2
            && a.b0 == b.b0 && a.b1 == b.b1 && a.b2 == b.b2;
    }

    /** Copy coeff values (used to publish/commit lastCoeffs without allocations). */
    static void copyCoeffs(Coeffs src, Coeffs dst) {
        dst.a0 = src.a0; dst.a1 = src.a1; dst.a2 = src.a2;
        dst.b0 = src.b0; dst.b1 = src.b1; dst.b2 = src.b2;
    }

    /** Threading contract note: setters and {@link #computeCoefficients()} are expected to run
     *  off the audio thread (GUI/worker). The audio thread must only read plain float fields,
     *  use a single boolean dirty flag (set by markDirty) and perform any interpolation per block.
     */
}
