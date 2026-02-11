package judahzone.prism;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import judahzone.data.Env;
import judahzone.util.Constants;

/* TODO support oversampling? */

/* TODO branch-free, prefer multiply over divide (precompute inverse) */

/* TODO usage in DrumOsc:
 private final PrismEnvelope envelope = new PrismEnvelope();

public void setAttack(int attack) {
    Env current = envelope.getEnv();
    envelope.setEnv(new Env(attack, current.decay(), current.release()));
}
protected void envelope() {
    for (int i = 0; i < work.length; i++) {
        work[i] *= envelope.process();
    }
}
public void trigger(int data2) {
    envelope.trigger();
    // ...
} */

/* TODO update DrumSample:
package net.judah.drumz.oldschool;

import judahzone.data.Env;
import judahzone.prism.PrismEnvelope;
import judahzone.fx.Gain;

public class DrumSample {
    // ... existing fields ...
    private final PrismEnvelope envelope = new PrismEnvelope();

    public void setAttack(int attack) {
        Env current = envelope.getEnv();
        envelope.setEnv(new Env(attack, current.decay(), current.release()));
    }

    public int getAttack() {
        return envelope.getAttackMs();
    }

    public void setDecay(int decay) {
        Env current = envelope.getEnv();
        envelope.setEnv(new Env(current.attack(), decay, current.release()));
    }

    public int getDecay() {
        return envelope.getDecayMs();
    }

    public void reset() {
        envelope.trigger();
    }

    public void play(boolean play) {
        if (play)
            envelope.trigger();
        else
            envelope.release();
    }

    public void process(float[] left, float[] right) {
        // Generate sample into work buffer, then apply envelope per-sample
        for (int i = 0; i < work.length; i++)
            work[i] *= envelope.process();
        // ... mix to left/right ...
    }

    public boolean isPlaying() {
        return envelope.isPlaying();
    }
}
 */

/** Centralized, lock-free envelope: attack/decay ramping, RT-safe, audio-safe.
Shared state (Env params) via CAS; per-instance counters are RT-local.  */
public class PrismEnvelope {

    private static final int SR = Constants.sampleRate();
    private static final int MAX_ATTACK = 150;  // ms
    private static final int MAX_DECAY = 1250;  // ms

    /** Atomic backing for thread-safe parameter updates from GUI/MIDI. */
    private final AtomicReference<Env> envTarget = new AtomicReference<>(new Env(10, 100));
    private final AtomicInteger atkSamples = new AtomicInteger(0);
    private final AtomicInteger dkSamples = new AtomicInteger(0);

    /** Cached computed targets (updated by GUI/MIDI thread, read by RT). */
    private volatile int atkTargetSamples = 0;
    private volatile int dkTargetSamples = 0;
    private volatile float invAttack = 0f;
    private volatile float invDecay = 0f;

    /** Smoothing for attack param changes (avoids clicks on rapid updates). */
    private volatile boolean atkChanged = false;
    private volatile float invAtkTarget = 0f;
    private int atkSmoothCounter = 0;
    private final int atkSmoothFrames = Math.max(1, Math.round(0.005f * SR)); // ~5ms

    public PrismEnvelope() {
        computeTargets();
    }

    public PrismEnvelope(Env env) {
        envTarget.set(env);
        computeTargets();
    }

    /** Update envelope params (GUI/MIDI thread, non-RT safe). */
    public void setEnv(Env env) {
        if (envTarget.compareAndSet(envTarget.get(), env)) {
            computeTargets();
        }
    }

    public Env getEnv() {
        return envTarget.get();
    }

    /** Trigger: reset counters for a new note (RT-safe via atomics). */
    public void trigger() {
        atkSamples.set(0);
        dkSamples.set(dkTargetSamples);
    }

    /** Reset to idle (decay complete). */
    public void release() {
        atkSamples.set(atkTargetSamples);
        dkSamples.set(0);
    }

    /** Per-sample envelope multiplier. Call once per sample in RT loop (no alloc). */
    public float process() {
        Env current = envTarget.get();
        if (current == null) return 0f;

        int atk = atkSamples.get();
        int dk = dkSamples.get();

        float env = 0f;

        // Attack stage
        if (atk < atkTargetSamples) {
            atk++;
            atkSamples.set(atk);

            // Smooth invAttack on first frame after param change
            if (atkChanged) {
                atkSmoothCounter = atkSmoothFrames;
                atkChanged = false;
            }
            if (atkSmoothCounter > 0) {
                float step = (invAtkTarget - invAttack) / atkSmoothCounter;
                invAttack += step;
                atkSmoothCounter--;
            }
            env = atk * invAttack;
            // Decrement decay as attack progresses
            if (dk > 0) {
                dkSamples.set(dk - 1);
            }
        } else {
            // Decay stage
            if (dkTargetSamples <= 0) {
                env = 0f;
            } else if (dk > 0) {
                env = dk * invDecay;
                dkSamples.set(dk - 1);
            } else {
                env = 0f;
            }
        }

        return env;
    }

    /** Compute attack/decay in samples from percentage (non-RT, safe to call from GUI). */
    private void computeTargets() {
        Env env = envTarget.get();
        if (env == null) return;

        // Attack: 0..100 -> 0..MAX_ATTACK ms -> samples
        float atkMs = (env.attack() / 100f) * MAX_ATTACK;
        int atkSamp = Math.round(atkMs * SR / 1000f);
        if (env.attack() > 0 && atkSamp == 0) atkSamp = 1;
        atkTargetSamples = Math.max(0, atkSamp);

        // Decay: 0..100 -> 0..MAX_DECAY ms -> samples
        float dkMs = (env.decay() / 100f) * MAX_DECAY;
        int dkSamp = Math.round(dkMs * SR / 1000f);
        if (env.decay() > 0 && dkSamp == 0) dkSamp = 1;
        dkTargetSamples = Math.max(0, dkSamp);

        // Precompute inverse for multiplication (avoid division in RT loop)
        invAtkTarget = atkTargetSamples > 0 ? 1f / atkTargetSamples : 0f;
        invDecay = dkTargetSamples > 0 ? 1f / dkTargetSamples : 0f;

        // Clamp current progress if target shrinks
        int currentAtk = atkSamples.get();
        if (atkSamp < currentAtk) {
            atkSamples.set(Math.min(currentAtk, atkSamp));
        }

        invAttack = invAtkTarget; // seed if not yet set
        atkChanged = true;
    }

    public int getAttackMs() {
        return (int)((atkTargetSamples / (float)SR) * 1000f);
    }

    public int getDecayMs() {
        return (int)((dkTargetSamples / (float)SR) * 1000f);
    }

    public boolean isPlaying() {
        return atkSamples.get() < atkTargetSamples || dkSamples.get() > 0;
    }
}
