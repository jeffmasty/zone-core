package judahzone.util;

public enum Interpolation {
    LINEAR {
        @Override
        public float interp(float s_m1, float s0, float s1, float s2, float t)
            { return s0 + (s1 - s0) * t; }
    },

    /** Catmull‑Rom / cubic interpolation given samples s[-1], s0, s1, s2 and
     * fractional t in [0..1] returns interpolated value. Good quality, inexpensive. */
    CUBIC {
        @Override
        public float interp(float s_m1, float s0, float s1, float s2, float t) {
            // Catmull-Rom spline
            float a = -0.5f * s_m1 + 1.5f * s0 - 1.5f * s1 + 0.5f * s2;
            float b = s_m1 - 2.5f * s0 + 2.0f * s1 - 0.5f * s2;
            float c = -0.5f * s_m1 + 0.5f * s1;
            float d = s0;
            return ((a * t + b) * t + c) * t + d;
        }
    };

    public abstract float interp(float s_m1, float s0, float s1, float s2, float t);
}


// TODO https://www.musicdsp.org/en/latest/Other/49-cubic-interpollation.html