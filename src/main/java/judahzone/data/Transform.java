package judahzone.data;

import judahzone.util.AudioMetrics;

/** FFT */
public record Transform(float[] magnitudes, AudioMetrics.RMS rms) {}