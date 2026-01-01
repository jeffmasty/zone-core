package judahzone.api;

import judahzone.util.AudioMetrics;

public record Transform(float[] magnitudes, AudioMetrics.RMS rms) {}