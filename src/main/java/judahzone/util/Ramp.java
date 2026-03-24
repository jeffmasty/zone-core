package judahzone.util;

/** Reusable parameter smoothing for RT-safe changes (pitch, feedback, damp, etc).
    Avoids repeated countdown/lerp logic across synth voices. */
public class Ramp {

private final int rampLen; // samples to smooth over (immutable)
private final float invRamp;

// Volatile fields to ensure cross-thread visibility without locking in audio thread.
private volatile int countdown;
private volatile float current;
private volatile float target;
private volatile float step;
private volatile boolean initialized;

/** Create ramp with given smoothing length (samples). */
public Ramp(int rampLenSamples) {
    this.rampLen = Math.max(1, rampLenSamples);
    this.invRamp = 1f / this.rampLen;
    this.current = 0f;
    this.target = 0f;
    this.countdown = 0;
    this.step = 0f;
    this.initialized = false;
}

/** Set target value; starts ramp if not already ramping.
    First ever set jumps immediately (useful for initialization). */
public synchronized void setTarget(float value) {
    // If currently ramping, update target and restart ramp from current value
    if (countdown > 0) {
        this.target = value;
        this.countdown = rampLen; // restart smooth ramp
        this.step = (this.target - this.current) * invRamp;
        return;
    }

    // Idle case
    if (!initialized) {
        // first set -> immediate
        this.current = value;
        this.target = value;
        this.initialized = true;
        this.countdown = 0;
        this.step = 0f;
    } else {
        // subsequent idle sets: if same value, do nothing; otherwise start ramp
        if (Float.compare(this.current, value) == 0) {
            this.target = value;
            this.step = 0f;
        } else {
            this.target = value;
            this.countdown = rampLen; // begin smooth ramp
            this.step = (this.target - this.current) * invRamp;
        }
    }
}

/** Force immediate set from non-RT side (alias for reset). */
public synchronized void setImmediate(float value) {
    reset(value);
}

/** Advance one sample; returns current interpolated value. (RT thread) */
public float next() {
    int c = countdown; // local snapshot
    if (c > 0) {
        // update current and countdown without locks
        current += step;
        c--;
        countdown = c; // publish new countdown
        if (c == 0) {
            // ensure exact final value
            current = target;
            step = 0f;
        }
    }
    return current;
}

/** Get current value without advancing. (RT thread ok) */
public float get() {
    return current;
}

/** Check if ramp is active. (RT thread ok) */
public boolean isRamping() {
    return countdown > 0;
}

/** Reset to immediate (no ramp); useful on trigger. */
public synchronized void reset(float value) {
    this.current = value;
    this.target = value;
    this.countdown = 0;
    this.step = 0f;
    this.initialized = true;
}

}