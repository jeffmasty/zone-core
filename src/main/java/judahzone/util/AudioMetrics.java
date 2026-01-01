package judahzone.util;

import java.nio.FloatBuffer;
import java.util.Objects;




/**
 * Lightweight audio measurement and normalization utilities.
 *
 * <p>Contains fast implementations for RMS, peak and amplitude measurements and small
 * helpers to scale/normalize buffers. Implementations use the FloatBuffer.hasArray()
 * fast path when possible and fall back to absolute-index access otherwise (they do
 * not mutate buffer.position()).
 *
 * <p>Intended for both RT and non-RT use. Methods are allocation‑free (no new arrays)
 * and use local primitives (double accumulation) for stable sums.
 */
public final class AudioMetrics {

	public static final float LIVE_FACTOR = 20; // TODO live audio ~8x weaker than recorded audio?
	public static final int Y_FACTOR = 550; // boost RMS into pixel range
	public static final int INTENSITY = 200; // scale peaks into pixels
	public static final int I_SHIFT = 37; // shift intensity off blue

    private AudioMetrics() {}

    /**
     * Simple bundle of channel metrics.
     *
     * @param rms RMS value (linear amplitude; 0..1 typical)
     * @param peak peak absolute value (0..1 typical)
     * @param amplitude simple amplitude estimate (max of avg positive / avg negative)
     */
    public record RMS(float rms, float peak, float amplitude) {}

    // -------------------------
    // Basic measurements
    // -------------------------

    /** Compute RMS of a float[] (entire array). Please
	 * cache the result since it is calculated every time.
	 * @param buffer The audio buffer to calculate the RMS for.
	 * @return The <a href="http://en.wikipedia.org/wiki/Root_mean_square">RMS</a> of
	 *         the signal present in the current buffer. */
    public static float rms(float[] buffer) {
        double sum = 0.0;
        for (int i = 0; i < buffer.length; i++) {
            double s = buffer[i];
            sum += s * s;
        }
        return (float) Math.sqrt(sum / buffer.length);
    }

    /** Compute RMS of a FloatBuffer without modifying its position. Returns 0 for null/empty. */
    public static float rms(FloatBuffer buf) {
        if (buf == null) return 0f;
        int pos = buf.position();
        int n = buf.remaining();
        if (n <= 0) return 0f;

        if (buf.hasArray()) {
            float[] a = buf.array();
            int base = buf.arrayOffset() + pos;
            double sum = 0.0;
            for (int i = 0; i < n; i++) {
                double s = a[base + i];
                sum += s * s;
            }
            return (float) Math.sqrt(sum / n);
        } else {
            double sum = 0.0;
            for (int i = 0; i < n; i++) {
                double s = buf.get(i);
                sum += s * s;
            }
            return (float) Math.sqrt(sum / n);
        }
    }

    /** Compute peak absolute sample value for float[] (0 if null/empty). */
    public static float peak(float[] buffer) {
        if (buffer == null || buffer.length == 0) return 0f;
        float p = 0f;
        for (float v : buffer) {
            float a = Math.abs(v);
            if (a > p) p = a;
        }
        return p;
    }

    /** Compute peak absolute sample value for FloatBuffer (without changing position). */
    public static float peak(FloatBuffer buf) {
        if (buf == null) return 0f;
        int pos = buf.position();
        int n = buf.remaining();
        if (n <= 0) return 0f;
        if (buf.hasArray()) {
            float[] a = buf.array();
            int base = buf.arrayOffset() + pos;
            float p = 0f;
            for (int i = 0; i < n; i++) {
                float aVal = Math.abs(a[base + i]);
                if (aVal > p) p = aVal;
            }
            return p;
        } else {
            float p = 0f;
            for (int i = 0; i < n; i++) {
                float aVal = Math.abs(buf.get(i));
                if (aVal > p) p = aVal;
            }
            return p;
        }
    }

    /** Compute "amplitude" using averaged positive/negative halves for float[]. */
    public static float amplitude(float[] buffer) {
        if (buffer == null || buffer.length == 0) return 0f;
        double sumPos = 0.0, sumNeg = 0.0;
        int cntPos = 0, cntNeg = 0;
        for (float v : buffer) {
            if (v > 0f) {
                sumPos += v;
                cntPos++;
            } else if (v < 0f) {
                sumNeg += v;
                cntNeg++;
            }
        }
        float avgPos = cntPos > 0 ? (float)(sumPos / cntPos) : 0f;
        float avgNeg = cntNeg > 0 ? (float)(sumNeg / cntNeg) : 0f;
        return Math.max(avgPos, Math.abs(avgNeg));
    }

    /** Compute amplitude for FloatBuffer without changing position. */
    public static float amplitude(FloatBuffer buf) {
        if (buf == null) return 0f;
        int pos = buf.position();
        int n = buf.remaining();
        if (n <= 0) return 0f;
        if (buf.hasArray()) {
            float[] a = buf.array();
            int base = buf.arrayOffset() + pos;
            double sumPos = 0.0, sumNeg = 0.0;
            int cntPos = 0, cntNeg = 0;
            for (int i = 0; i < n; i++) {
                float v = a[base + i];
                if (v > 0f) { sumPos += v; cntPos++; }
                else if (v < 0f) { sumNeg += v; cntNeg++; }
            }
            float avgPos = cntPos > 0 ? (float)(sumPos / cntPos) : 0f;
            float avgNeg = cntNeg > 0 ? (float)(sumNeg / cntNeg) : 0f;
            return Math.max(avgPos, Math.abs(avgNeg));
        } else {
            double sumPos = 0.0, sumNeg = 0.0;
            int cntPos = 0, cntNeg = 0;
            for (int i = 0; i < n; i++) {
                float v = buf.get(i);
                if (v > 0f) { sumPos += v; cntPos++; }
                else if (v < 0f) { sumNeg += v; cntNeg++; }
            }
            float avgPos = cntPos > 0 ? (float)(sumPos / cntPos) : 0f;
            float avgNeg = cntNeg > 0 ? (float)(sumNeg / cntNeg) : 0f;
            return Math.max(avgPos, Math.abs(avgNeg));
        }
    }

    // -------------------------
    // Combined analyzers (mono/stereo)
    // -------------------------

    /** Analyze a mono float[] and return RMS metrics. */
    public static RMS analyze(float[] channel) {
        if (channel == null || channel.length == 0) return new RMS(0f, 0f, 0f);
        float rms = rms(channel);
        float pk = peak(channel);
        float amp = amplitude(channel);
        return new RMS(rms, pk, amp);
    }

    /** Analyze a stereo float[][] [LEFT][RIGHT], return the louder channel's metrics. */
    public static RMS analyze(float[][] stereo) {
        Objects.requireNonNull(stereo);
        if (stereo.length < 2) return analyze(stereo[0]);
        RMS l = analyze(stereo[Constants.LEFT]);
        RMS r = analyze(stereo[Constants.RIGHT]);
        return l.rms() >= r.rms() ? l : r;
    }

    /** Analyze a FloatBuffer pair (left, right). If right==null analyze mono left. */
    public static RMS analyze(FloatBuffer left, FloatBuffer right) {
        float rl = rms(left);
        float pkL = peak(left);
        float ampL = amplitude(left);
        if (right == null) return new RMS(rl, pkL, ampL);
        float rr = rms(right);
        float pkR = peak(right);
        float ampR = amplitude(right);
        return rl >= rr ? new RMS(rl, pkL, ampL) : new RMS(rr, pkR, ampR);
    }

    // -------------------------
    // Scaling & normalization
    // -------------------------

    /** Multiply float[] in-place by gain. */
    public static void scale(float[] buffer, float gain) {
        if (buffer == null || buffer.length == 0) return;
        for (int i = 0; i < buffer.length; i++) buffer[i] = buffer[i] * gain;
    }

    /** Multiply FloatBuffer in-place by gain (does not modify position). */
    public static void scale(FloatBuffer buf, float gain) {
        if (buf == null) return;
        int pos = buf.position();
        int n = buf.remaining();
        if (n <= 0 || gain == 1f) return;
        if (buf.hasArray()) {
            float[] a = buf.array();
            int base = buf.arrayOffset() + pos;
            for (int i = 0; i < n; i++) a[base + i] = a[base + i] * gain;
        } else {
            for (int i = 0; i < n; i++) buf.put(i, buf.get(i) * gain);
        }
    }

    /**
     * Normalize float[] buffer to a target RMS (linear). Returns applied gain.
     * If buffer is silent (rms == 0) no change is made and gain 1f is returned.
     * maxGain <= 0 means "no cap".
     */
    public static float normalizeToRms(float[] buffer, float targetRms, float maxGain) {
        if (buffer == null || buffer.length == 0) return 1f;
        float cur = rms(buffer);
        if (cur <= 0f) return 1f;
        float gain = targetRms / cur;
        if (maxGain > 0f && gain > maxGain) gain = maxGain;
        scale(buffer, gain);
        return gain;
    }

    /**
     * Normalize FloatBuffer to target RMS (in-place). Uses fast path for backed buffers.
     * Returns applied gain. Does not change buffer.position().
     */
    public static float normalizeToRms(FloatBuffer buf, float targetRms, float maxGain) {
        float cur = rms(buf);
        if (cur <= 0f) return 1f;
        float gain = targetRms / cur;
        if (maxGain > 0f && gain > maxGain) gain = maxGain;
        scale(buf, gain);
        return gain;
    }

    /**
     * Normalize float[] buffer to a target peak (linear). Returns applied gain.
     * If buffer is silent (peak == 0) no change is made and gain 1f is returned.
     */
    public static float normalizeToPeak(float[] buffer, float targetPeak, float maxGain) {
        if (buffer == null || buffer.length == 0) return 1f;
        float curPeak = peak(buffer);
        if (curPeak <= 0f) return 1f;
        float gain = targetPeak / curPeak;
        if (maxGain > 0f && gain > maxGain) gain = maxGain;
        scale(buffer, gain);
        return gain;
    }

    /**
     * Normalize FloatBuffer to target peak (in-place). Returns applied gain.
     * Does not change buffer.position().
     */
    public static float normalizeToPeak(FloatBuffer buf, float targetPeak, float maxGain) {
        float curPeak = peak(buf);
        if (curPeak <= 0f) return 1f;
        float gain = targetPeak / curPeak;
        if (maxGain > 0f && gain > maxGain) gain = maxGain;
        scale(buf, gain);
        return gain;
    }

    // -------------------------
    // Utility notes (documentation only)
    // -------------------------
    //
    // - For RT use: these are allocation-free and use only local primitives. Prefer the
    //   FloatBuffer overloads if your host provides FloatBuffers.
    // - For non-backed FloatBuffers (e.g. many JACK JNA buffers) the code falls back to
    //   absolute buf.get(i) accesses — cheaper than duplicating + copying for small frames,
    //   but if you need repeated processing consider copying once into float[] using a
    //   preallocated temp buffer.
    // - RMS is linear amplitude. To convert to dBFS: 20 * log10(rms). Beware of 0 values.
    // - When normalizing, guard against clipping by capping gains or by normalizing to
    //   peak instead of RMS or by applying limiting afterwards.
}