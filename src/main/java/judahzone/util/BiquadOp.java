package judahzone.util;

import judahzone.prism.PrismRT;

/** RT-safe base for biquad-style filters.
 *  Subclasses compute coefficients in {@link #computeCoefficients()} (off-RT),
 *  publish them via {@link #setCoefficientsFrom(Filters.Coeffs)}, then call
 *  {@link #markDirty()} so the audio thread will interpolate per-block. */
@PrismRT
public abstract class BiquadOp implements Biquad {

    protected static final int SR = Constants.sampleRate();
    protected static final int N_FRAMES = Constants.bufSize();
    protected static final float INV = 1.0f / N_FRAMES;

    /* parameters (non-RT) */
    protected float frequency;
    protected float q = 1.0f;
    protected float bandwidth = 1.0f;
    protected float gainDb = 0f;

    /* published (RT-read) normalized coefficients (a0 expected 1.0 after normalize) */
    protected float a0, a1, a2, b0, b1, b2;

    /* last-committed coefficients used for interpolation on audio thread */
    private float lastA0, lastA1, lastA2, lastB0, lastB1, lastB2;
    private boolean haveLastCoeffs = false;
    private volatile boolean coeffDirty = true; // set by non-RT thread to signal new coeffs

    /* per-channel states (xn1,xn2,yn1,yn2) */
    private final float[] monoState = new float[4];
    private final float[] leftState = new float[4];
    private final float[] rightState = new float[4];

    /* ----- parameter API (default helpers) ----- */

    @Override public void setFrequency(float hz) {
        if (this.frequency == hz) return;
        this.frequency = hz;
        computeCoefficients();
    }
    @Override public float getFrequency() { return frequency; }

    @Override public void setQ(float q) {
        if (this.q == q) return;
        this.q = q;
        computeCoefficients();
    }
    @Override public float getQ() { return q; }

    @Override public void setBandwidth(float bw) {
        if (this.bandwidth == bw) return;
        this.bandwidth = bw;
        computeCoefficients();
    }
    @Override public float getBandwidth() { return bandwidth; }

    @Override public void setGainDb(float db) {
        if (this.gainDb == db) return;
        this.gainDb = db;
        computeCoefficients();
    }
    @Override public float getGainDb() { return gainDb; }

    /** Subclasses must compute normalized coefficients and publish them via setCoefficientsFrom(...). */
    @Override public abstract void computeCoefficients();

    @Override public void markDirty() { coeffDirty = true; }

    /** Copy normalized coeffs into this instance and mark dirty for RT thread. */
    protected final void setCoefficientsFrom(Filters.Coeffs coeffs) {
        // assume coeffs already normalized (a0 == 1.0)
        this.a0 = coeffs.a0; this.a1 = coeffs.a1; this.a2 = coeffs.a2;
        this.b0 = coeffs.b0; this.b1 = coeffs.b1; this.b2 = coeffs.b2;
        markDirty();
    }

    /* ----- audio-thread processing (mono/stereo templates) ----- */

    @Override
    public void process(float[] mono) {
        if (mono == null) return;

        // initialize last coeffs once
        if (!haveLastCoeffs) {
            commitLastCoeffs();
            haveLastCoeffs = true;
        }
        if (coeffDirty) coeffDirty = false;

        // fast-path: identical coefficients
        if (lastA0 == a0 && lastA1 == a1 && lastA2 == a2 &&
            lastB0 == b0 && lastB1 == b1 && lastB2 == b2) {

            final float lb0 = b0, lb1 = b1, lb2 = b2;
            final float la1 = a1, la2 = a2;

            float xn1 = monoState[0], xn2 = monoState[1];
            float yn1 = monoState[2], yn2 = monoState[3];

            for (int i = 0; i < N_FRAMES; i++) {
                float x = mono[i];
                float y = lb0 * x + lb1 * xn1 + lb2 * xn2 - la1 * yn1 - la2 * yn2;
                if (y > -DENORM && y < DENORM) y = 0f;
                mono[i] = y;
                xn2 = xn1; xn1 = x;
                yn2 = yn1; yn1 = y;
            }

            monoState[0] = xn1; monoState[1] = xn2;
            monoState[2] = yn1; monoState[3] = yn2;

        } else {
            // interpolation path
            float curA1 = lastA1, curA2 = lastA2;
            float curB0 = lastB0, curB1 = lastB1, curB2 = lastB2;

            final float dA1 = (a1 - lastA1) * INV;
            final float dA2 = (a2 - lastA2) * INV;
            final float dB0 = (b0 - lastB0) * INV;
            final float dB1 = (b1 - lastB1) * INV;
            final float dB2 = (b2 - lastB2) * INV;

            float xn1 = monoState[0], xn2 = monoState[1];
            float yn1 = monoState[2], yn2 = monoState[3];

            for (int i = 0; i < N_FRAMES; i++) {
                curA1 += dA1; curA2 += dA2;
                curB0 += dB0; curB1 += dB1; curB2 += dB2;

                float x = mono[i];
                float y = curB0 * x + curB1 * xn1 + curB2 * xn2 - curA1 * yn1 - curA2 * yn2;
                if (y > -DENORM && y < DENORM) y = 0f;
                mono[i] = y;
                xn2 = xn1; xn1 = x;
                yn2 = yn1; yn1 = y;
            }

            monoState[0] = xn1; monoState[1] = xn2;
            monoState[2] = yn1; monoState[3] = yn2;

            commitLastCoeffs();
        }

        // sanitize small states once per block
        Filters.sanitize(monoState, 0);
    }

    @Override
    public void processStereo(float[] left, float[] right) {
        if (left == null) return;
        if (right == null) { process(left); return; }

        if (!haveLastCoeffs) {
            commitLastCoeffs();
            haveLastCoeffs = true;
        }
        if (coeffDirty) coeffDirty = false;

        if (lastA0 == a0 && lastA1 == a1 && lastA2 == a2 &&
            lastB0 == b0 && lastB1 == b1 && lastB2 == b2) {

            final float lb0 = b0, lb1 = b1, lb2 = b2;
            final float la1 = a1, la2 = a2;

            float lx1 = leftState[0], lx2 = leftState[1], ly1 = leftState[2], ly2 = leftState[3];
            float rx1 = rightState[0], rx2 = rightState[1], ry1 = rightState[2], ry2 = rightState[3];

            for (int i = 0; i < N_FRAMES; i++) {
                float xL = left[i];
                float yL = lb0 * xL + lb1 * lx1 + lb2 * lx2 - la1 * ly1 - la2 * ly2;
                if (yL > -DENORM && yL < DENORM) yL = 0f;
                left[i] = yL;
                lx2 = lx1; lx1 = xL; ly2 = ly1; ly1 = yL;

                float xR = right[i];
                float yR = lb0 * xR + lb1 * rx1 + lb2 * rx2 - la1 * ry1 - la2 * ry2;
                if (yR > -DENORM && yR < DENORM) yR = 0f;
                right[i] = yR;
                rx2 = rx1; rx1 = xR; ry2 = ry1; ry1 = yR;
            }

            leftState[0] = lx1; leftState[1] = lx2; leftState[2] = ly1; leftState[3] = ly2;
            rightState[0] = rx1; rightState[1] = rx2; rightState[2] = ry1; rightState[3] = ry2;

        } else {
            float curA1 = lastA1, curA2 = lastA2;
            float curB0 = lastB0, curB1 = lastB1, curB2 = lastB2;

            final float dA1 = (a1 - lastA1) * INV;
            final float dA2 = (a2 - lastA2) * INV;
            final float dB0 = (b0 - lastB0) * INV;
            final float dB1 = (b1 - lastB1) * INV;
            final float dB2 = (b2 - lastB2) * INV;

            float lx1 = leftState[0], lx2 = leftState[1], ly1 = leftState[2], ly2 = leftState[3];
            float rx1 = rightState[0], rx2 = rightState[1], ry1 = rightState[2], ry2 = rightState[3];

            for (int i = 0; i < N_FRAMES; i++) {
                curA1 += dA1; curA2 += dA2;
                curB0 += dB0; curB1 += dB1; curB2 += dB2;

                float xL = left[i];
                float yL = curB0 * xL + curB1 * lx1 + curB2 * lx2 - curA1 * ly1 - curA2 * ly2;
                if (yL > -DENORM && yL < DENORM) yL = 0f;
                left[i] = yL;
                lx2 = lx1; lx1 = xL; ly2 = ly1; ly1 = yL;

                float xR = right[i];
                float yR = curB0 * xR + curB1 * rx1 + curB2 * rx2 - curA1 * ry1 - curA2 * ry2;
                if (yR > -DENORM && yR < DENORM) yR = 0f;
                right[i] = yR;
                rx2 = rx1; rx1 = xR; ry2 = ry1; ry1 = yR;
            }

            leftState[0] = lx1; leftState[1] = lx2; leftState[2] = ly1; leftState[3] = ly2;
            rightState[0] = rx1; rightState[1] = rx2; rightState[2] = ry1; rightState[3] = ry2;

            commitLastCoeffs();
        }

        Filters.sanitize(leftState, 0);
        Filters.sanitize(rightState, 0);
    }

    @Override
    public void reset() {
        // push tiny nudge to avoid denormals / CPU stalls
        monoState[0] = monoState[1] = monoState[2] = monoState[3] = Filters.nudge(0f);
        leftState[0] = leftState[1] = leftState[2] = leftState[3] = Filters.nudge(0f);
        rightState[0] = rightState[1] = rightState[2] = rightState[3] = Filters.nudge(0f);
    }

    /* ----- helpers ----- */

    private void commitLastCoeffs() {
        lastA0 = a0; lastA1 = a1; lastA2 = a2;
        lastB0 = b0; lastB1 = b1; lastB2 = b2;
    }

}
