package judahzone.util;

/** Biquad filter coefficient computation for standard filter types. */
public interface Filters {

	public static record Filter(float hz, float reso) {}

	public static final float LOG_2 = 0.693147f;

	public enum FilterType { LowPass, HighPass, Peaking, AllPass }
	public enum BWQType { Q, BW, S }

	float MIN_BANDWIDTH = 0.1f;
	float MAX_BANDWIDTH = 4.0f;

	float DENORM = 1.0E-8f; // small value to avoid denormals


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

	/** Compute biquad coefficients. */
	public static Coeffs compute(FilterType type, float frequency, float sampleRate,
	                              float bandwidth, float gainDb, BWQType bwqType) {
		float a = (float) Math.pow(10.0, gainDb / 40.0);
		float w0 = (float) (2.0 * Math.PI * frequency / sampleRate);
		float sinw0 = (float) Math.sin(w0);
		float cosw0 = (float) Math.cos(w0);

		float alpha = switch (bwqType) {
			case Q -> sinw0 / (2.0f * bandwidth);
			case BW -> (float) (sinw0 * Math.sinh(LOG_2 / 2.0 * bandwidth * w0 / sinw0));
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
