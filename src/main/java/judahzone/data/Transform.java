package judahzone.data;

import judahzone.util.AudioMetrics;

public record Transform(float[] magnitudes, AudioMetrics.RMS rms) {}