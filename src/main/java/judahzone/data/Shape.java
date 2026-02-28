package judahzone.data;

import java.util.concurrent.ThreadLocalRandom;

/**Pre-computed 2^n sized radians-based wavetable for various shapes. *
	•  if BITS = 16: 32768 samples per table, ~640Kb LUT.
	•  if BITS = 15: 16384 samples per table, ~320Kb LUT.
	•  Radians: samples given at angles across 0..2π, one cycle represented in LENGTH.
	•  Values: normalized to -1..1 when necessary (only scale when peak exceeds 1.0)
	•  Inspired by: https://github.com/johncch/MusicSynthesizer  WaveTable
	•  		Chong Han Chua, Veronica Borges (c) 2010 (MIT License) */
public enum Shape {

    SIN,
    /** centered and normalized */
    SQR,
    TRI,
    SAW,
    RND;

    public final static int BITS = 15;
    public final static int LENGTH = 1 << (BITS - 1);
    public final static int MASK = LENGTH - 1;
    public static final float DUTY_FACTOR = 0.7f; // SQR and RND (keeps deliberate lower amplitude)

    /** phase (radians) -> table index scale: index = phase * INV_SIZE */
    public static final float INV_SIZE = LENGTH / (2f * (float) Math.PI);

    /** power-of-two wavetable of Shape */
    private final float[] wave;

    Shape() {
        wave = Waves.list[ordinal()];
    }

    public float[] getWave() {
        return wave;
    }

    public static class Waves {

        final static float[] SIN = sin(new float[LENGTH]);
        final static float[] SQR = sqr(new float[LENGTH]);
        final static float[] TRI = tri(new float[LENGTH]);
        final static float[] SAW = saw(new float[LENGTH]);
        final static float[] RND = rnd(new float[LENGTH]);
        final static float[][] list = new float[][] { SIN, SQR, TRI, SAW, RND };

        private static void scale(float[] wave) {
            // Only scale down if any sample exceeds ±1.0 to preserve deliberate lower peaks.
            float max = 0f;
            for (int i = 0; i < wave.length; i++) {
                float a = Math.abs(wave[i]);
                if (a > max) max = a;
            }
            if (max > 1.0f) {
                float inv = 1.0f / max;
                for (int i = 0; i < wave.length; i++)
                    wave[i] *= inv;
            }
        }

        public static float[] sin(float[] wave) {
            final float dt = (float) (2f * Math.PI / LENGTH);
            for (int i = 0; i < LENGTH; i++)
                wave[i] = (float) Math.sin(i * dt);
            // sine already in [-1,1]
            return wave;
        }

        public static float[] sqr(float[] wave) {
            int half = LENGTH / 2;
            for (int i = 0; i < LENGTH; i++)
                wave[i] = (i < half) ? 1 : -1;
            normalize(wave, DUTY_FACTOR);
            center(wave);
            return wave;
        }

        public static float[] tri(float[] wave) {
            for (int i = 0; i < LENGTH; i++) {
                float phase = (float) i / LENGTH; // 0..1
                float v;
                if (phase < 0.25f)
                    v = 4f * phase;
                else if (phase < 0.75f)
                    v = 2f - 4f * phase;
                else
                    v = -4f + 4f * phase;
                wave[i] = v;
            }
            scale(wave);
            return wave;
        }

        public static float[] saw(float[] wave) {
            for (int i = 0; i < LENGTH; i++)
                wave[i] = 2f * (i / (float) LENGTH) - 1f;
            scale(wave);
            return wave;
        }

        public static float[] rnd(float[] wave) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            float sumSq = 0f;
            for (int i = 0; i < wave.length; i++) {
                float sample = random.nextFloat() * 2f * DUTY_FACTOR - DUTY_FACTOR;
                wave[i] = sample;
                sumSq += sample * sample;
            }
            float rms = (float) Math.sqrt(sumSq / wave.length);
            if (rms > 0f) {
                float target = DUTY_FACTOR + 0.1f;
                float gain = target / rms;
                for (int i = 0; i < wave.length; i++)
                    wave[i] *= gain;
            }
            scale(wave);
            return wave;
        }
    }

    public static int toKnob(Shape s) {
		return s.ordinal() * 25; // 0, 25, 50, 75, 100 for 5 shapes
	}
    public static Shape fromKnob(int val) {
    	return values()[Math.max(0, Math.min(val / 25, values().length - 1))];
    }

    /** Subtract mean and scale so absolute max <= 1.0.
    •  Subtracting the mean removes bulk DC so the table is centered;
	•  prevents static offset from being introduced by additive mixing or envelopes.
	•  Cubic interpolation (and other high-order interpolators) can overshoot and reintroduce small DC bias...
		interpolated samples can go beyond table extrema. Centering reduces the magnitude of reintroduced bias.*/
    public static void center(float[] wave) {
        if (wave == null || wave.length == 0) return;

        // compute mean
        double sum = 0.0;
        for (int i = 0; i < wave.length; i++) sum += wave[i];
        float mean = (float) (sum / wave.length);

        // subtract mean and find peak
        float peak = 0f;
        for (int i = 0; i < wave.length; i++) {
            float v = wave[i] - mean;
            wave[i] = v;
            float a = Math.abs(v);
            if (a > peak) peak = a;
        }

        // Ensure absolute peak <= 1.0 (scale down if necessary).
        if (peak > 1.0f) {
            float inv = 1.0f / peak;
            for (int i = 0; i < wave.length; i++)
                wave[i] *= inv;
        }
    }
    public static void normalize(float[] wave, float peak) {
		for (int i = 0; i < wave.length; i++)
			wave[i] = Math.min(Math.max(wave[i], -peak), peak); // clip to ±peak
	}

}
