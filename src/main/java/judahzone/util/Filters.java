package judahzone.util;

import judahzone.prism.PrismRT;

/** Biquad filter coefficient computation for standard filter types. */
public interface Filters {

	public static record Quad(float xn1, float xn2, float yn1, float yn2) {}

	public enum FilterType { LowPass, HighPass, Peaking, AllPass, BandPass }
	public enum BWQType { Q, BW, S }

	public static final float LOG_2 = 0.693147f;
	public static final float IL2 = LOG_2 / 2.0f; // inverse log(2) for bandwidth conversions

	public static final float MIN_BANDWIDTH = 0.1f;
	public static final float MAX_BANDWIDTH = 3.0f;

	default float clampBandwidth(float bw) {
		return Math.max(MIN_BANDWIDTH, Math.min(bw, MAX_BANDWIDTH));
	}

	/** Normalized biquad coefficients [b0, b1, b2, a0, a1, a2]. */
	public static class Coeffs {
		public float b0, b1, b2, a0, a1, a2;

		public Coeffs() {}

		public Coeffs(float b0, float b1, float b2, float a0, float a1, float a2) {
			this.b0 = b0; this.b1 = b1; this.b2 = b2;
			this.a0 = a0; this.a1 = a1; this.a2 = a2;
		}

		/** Normalize by a0 for direct use in biquad processing. */
		public void normalize() {
			b0 /= a0; b1 /= a0; b2 /= a0;
			a1 /= a0; a2 /= a0;
			a0 = 1.0f;
		}
	}

    /** Exact float equality used for fast-path detection (safe because setters publish exact floats). */
    static boolean coeffsEqual(Coeffs a, Coeffs b) {
        return a.a0 == b.a0 && a.a1 == b.a1 && a.a2 == b.a2
            && a.b0 == b.b0 && a.b1 == b.b1 && a.b2 == b.b2;
    }

    /** Copy coeff values into dst (no allocations). */
    static void copyCoeffs(Coeffs src, Coeffs dst) {
        dst.a0 = src.a0; dst.a1 = src.a1; dst.a2 = src.a2;
        dst.b0 = src.b0; dst.b1 = src.b1; dst.b2 = src.b2;
    }

    /** Compute per-sample deltas for the 5 normalized coefficients used in processing (a1,a2,b0,b1,b2).
     *  @param inv 1.0f / nFrames.
     *  @param out Output array must be length >= 6.
     *  out layout: {dA0,dA1,dA2,dB0,dB1,dB2} where dA0/dA2 etc are deltas (a0 often == 1.0). */
    @PrismRT
    static void deltas(Coeffs target, Coeffs last, float inv, float[] out) {
        // safe map: keep same index order as Coeffs (a0,a1,a2,b0,b1,b2)
        out[0] = (target.a0 - last.a0) * inv;
        out[1] = (target.a1 - last.a1) * inv;
        out[2] = (target.a2 - last.a2) * inv;
        out[3] = (target.b0 - last.b0) * inv;
        out[4] = (target.b1 - last.b1) * inv;
        out[5] = (target.b2 - last.b2) * inv;
    }

    /** produce a blockwise interpolation at t in [0..1].
     *  Useful off-RT or for single-block pre-compute.  */
    static Coeffs interpolate(Coeffs target, Coeffs last, float t) {
        Coeffs out = new Coeffs();
        out.a0 = last.a0 + (target.a0 - last.a0) * t;
        out.a1 = last.a1 + (target.a1 - last.a1) * t;
        out.a2 = last.a2 + (target.a2 - last.a2) * t;
        out.b0 = last.b0 + (target.b0 - last.b0) * t;
        out.b1 = last.b1 + (target.b1 - last.b1) * t;
        out.b2 = last.b2 + (target.b2 - last.b2) * t;
        return out;
    }


	////// Denormalization
	float DENORM = 1.0E-8f; // small value to avoid denormals
    public static float sanitize(float v) { return (Math.abs(v) < DENORM) ? 0f : v; } // branching

    /** push exponents into normal range */
    final float TINY_NUDGE = 1.0E-18f;
    public static float nudge(float v) { return v + TINY_NUDGE; }

    /** @param states an array of state variables in-place. Call once per block on the small set of long-lived states. */
    public static void sanitize(float[] states) {
        for (int i = 0; i < states.length; i++)
            if (Math.abs(states[i]) < DENORM) states[i] = 0f;
    }

    /** @param state4 four biquad states: xn1,xn2,yn1,yn2
     * @param offset index in the state array */
    public static void sanitize(float[] state4, int offset) {
        for (int i = 0; i < 4; i++)
            if (Math.abs(state4[offset + i]) < DENORM) state4[offset + i] = 0f;
    }

    /** Generic block processor for a normalized biquad:
     *  @param buff audio buffer (in-place)
     *  @param nFrames: number of frames
     *  b0,b1,b2,a1,a2: normalized coefficients (a0 is assumed 1.0)
     *  state: length-4 array holding xn1,xn2,yn1,yn2 (mutable) */
    public static void sanatize(float[] buff, int nFrames, float b0, float b1, float b2,
                                float a1, float a2, float[] state) {
        float xn1 = state[0], xn2 = state[1];
        float yn1 = state[2], yn2 = state[3];

        for (int i = 0; i < nFrames; i++) {
            float x = buff[i];
            float y = b0 * x + b1 * xn1 + b2 * xn2 - a1 * yn1 - a2 * yn2;
            // No per-sample denorm check here (hot path)
            buff[i] = y;
            xn2 = xn1; xn1 = x;
            yn2 = yn1; yn1 = y;
        }

        // Commit back and sanitize the small set of long-lived states once per block
        state[0] = xn1; state[1] = xn2; state[2] = yn1; state[3] = yn2;
        sanitize(state, 0);
    }


	/** Compute biquad coefficients. */
	public static Coeffs compute(FilterType type, float frequency, float sampleRate,
	                              float bandwidth, float gainDb, BWQType bwqType) {
		float a = (float) Math.pow(10.0, gainDb / 40.0);
		float w0 = (float) (2.0 * Math.PI * frequency / sampleRate);
		float sinw0 = (float) Math.sin(w0);
		float cosw0 = (float) Math.cos(w0);

		float alpha = switch (bwqType) {
			case Q -> sinw0 / (2.0f * bandwidth);
			case BW -> (float) (sinw0 * Math.sinh(IL2 * bandwidth * w0 / sinw0));
			case S -> (float) (sinw0 * Math.sqrt((a + 1.0 / a) * (1.0 / bandwidth - 1.0) + 2.0) / 2.0);
		};

		return switch (type) {
			case LowPass -> {
				float b1 = 1.0f - cosw0;
				yield new Coeffs(b1 / 2.0f, b1, b1 / 2.0f, 1.0f + alpha, -2.0f * cosw0, 1.0f - alpha);
			}
			case HighPass -> {
				float b01 = (1.0f + cosw0) / 2.0f;
				yield new Coeffs(b01, -(1.0f + cosw0), b01, 1.0f + alpha, -2.0f * cosw0, 1.0f - alpha);
			}
			case Peaking -> new Coeffs(1.0f + alpha * a, -2.0f * cosw0, 1.0f - alpha * a,
			                            1.0f + alpha / a, -2.0f * cosw0, 1.0f - alpha / a);
			case AllPass -> {
				/* AllPass: b coefficients are inverted A. Preserves magnitude (|H|=1)
				   but shifts phase per frequency. b0=a2, b1=a1, b2=a0. */
				float a0_raw = 1.0f + alpha;
				float a1_raw = -2.0f * cosw0;
				float a2_raw = 1.0f - alpha;
				yield new Coeffs(a2_raw, a1_raw, a0_raw, a0_raw, a1_raw, a2_raw);
			}
			case BandPass -> {
				float bw_rad = (float) (sinw0 * Math.sinh(IL2 * bandwidth * w0 / sinw0));
				float b0_val = sinw0 / 2.0f;
				yield new Coeffs(b0_val, 0.0f, -b0_val, 1.0f + bw_rad, -2.0f * cosw0, 1.0f - bw_rad);
			}

		};
	}

	/** Convenience: LowPass/HighPass with default Q. */
	public static Coeffs compute(FilterType type, float frequency, float sampleRate) {
		return compute(type, frequency, sampleRate, 2.0f, 0.0f, BWQType.Q);
	}

	/** Convenience: Peaking EQ. */
	public static Coeffs computePeaking(float frequency, float sampleRate, float bandwidth, float gainDb) {
		return compute(FilterType.Peaking, frequency, sampleRate, bandwidth, gainDb, BWQType.BW);
	}

	/** Convenience: AllPass filter (uses Q for bandwidth). */
	public static Coeffs computeAllPass(float frequency, float sampleRate, float q) {
		return compute(FilterType.AllPass, frequency, sampleRate, q, 0.0f, BWQType.Q);
	}
}
