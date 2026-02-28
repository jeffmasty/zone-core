package judahzone.util;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Vector;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.Mixer.Info;

public class AudioTools  {

	public static void decimate(float[] in, float[] out, int factor) {
		for (int i = 0; i < in.length / factor; i++)
			out[i] = in[i * factor];
	}

	public static void silence(FloatBuffer a) {
        if (a == null) return;
        a.rewind();
        int limit = a.limit();
        if (a.hasArray()) {
            float[] arr = a.array();
            int base = a.arrayOffset();
            for (int i = 0; i < limit; i++)
                arr[base + i] = 0f;
            // keep same semantics as put loop: move position to limit
            a.position(limit);
            return;
        }
        for (int i = 0; i < limit; i++)
            a.put(0f);
	}

	public static void silence(float[] buf) {
	    if (buf.length == WavConstants.JACK_BUFFER)
	        System.arraycopy(Memory.ZERO, 0, buf, 0, buf.length);
	    else
	        Arrays.fill(buf, 0f);
	}

	public static void silence(float[][] stereo) {
	    silence(stereo[0]);
	    if (stereo.length > 1) silence(stereo[1]);
	}

	public static void mix(float[] in, float[] sum) {
	    for (int i = 0; i < sum.length; i++)
	        sum[i] += in[i];
	}

	public static void mix(float[] in, float gain, float[] out) {
		if (in == null || out == null) return;
		int n = Math.min(in.length, out.length);
		for (int i = 0; i < n; i++)
			out[i] = out[i] + in[i] * gain;
	}

	public static void mix(float[] in, float gain, FloatBuffer out) {
		if (in == null || out == null) return;
		out.rewind();
		int n = Math.min(in.length, out.remaining());
		if (out.hasArray()) {
			float[] oa = out.array();
			int oo = out.arrayOffset() + out.position();
			for (int i = 0; i < n; i++)
				oa[oo + i] = oa[oo + i] + in[i] * gain;
			out.position(out.position() + n);
			return;
		}
		for (int i = 0; i < n; i++)
			out.put(i, out.get(i) + in[i] * gain);
		out.position(out.position() + n);
	}

	/** MIX
	 * @param overdub
	 * @param oldLoop*/
	public static float[][] overdub(float[][] overdub, float[][] oldLoop) {
		float[] in, out;//, result;
		for (int channel = 0; channel < oldLoop.length; channel++) {
			in = overdub[channel];
			out = oldLoop[channel];
			for (int x = 0; x < out.length; x++)
				out[x] = in[x] + out[x];
		}
		return oldLoop;
	}

	public static void replace(float [] in, float[] out, float gain) {
		if (in == null || out == null) return;
		int n = Math.min(in.length, out.length);
		for (int i = 0; i < n; i++)
			out[i] = in[i] * gain;
	}

	public static void replace(float[] in, FloatBuffer out, float gain) {
		if (in == null || out == null) return;
		out.rewind();
		int n = Math.min(in.length, out.remaining());
		if (out.hasArray()) {
			float[] oa = out.array();
			int oo = out.arrayOffset() + out.position();
			for (int i = 0; i < n; i++)
				oa[oo + i] = in[i] * gain;
			out.position(out.position() + n);
			return;
		}
		for (int i = 0; i < n; i++)
			out.put(i, in[i] * gain);
		out.position(out.position() + n);
	}

	public static void replace(float[] in, FloatBuffer out) {
		if (in == null || out == null) return;
		out.rewind();
		int n = Math.min(in.length, out.capacity());
		if (out.hasArray()) {
			float[] oa = out.array();
			int oo = out.arrayOffset() + out.position();
			for (int i = 0; i < n; i++)
				oa[oo + i] = in[i];
			out.position(out.position() + n);
			return;
		}
		for (int i = 0; i < n; i++)
			out.put(i, in[i]);
		out.position(out.position() + n);
	}

	public static void gain(FloatBuffer buffer, float gain) {
		if (buffer == null) return;
		buffer.rewind();
		int n = buffer.capacity();
		if (buffer.hasArray()) {
			float[] a = buffer.array();
			int base = buffer.arrayOffset();
			for (int i = 0; i < n; i++) {
				a[base + i] = a[base + i] * gain;
			}
			buffer.position(n);
			return;
		}
		for (int z = 0; z < n; z++) {
			buffer.put(z, buffer.get(z) * gain);
		}
		buffer.position(n);
	}

	public static void copy(FloatBuffer in, FloatBuffer out) {
		if (in == null || out == null) return;
		in.rewind();
		out.rewind();
		int n = Math.min(in.remaining(), out.remaining());
		if (in.hasArray() && out.hasArray()) {
			System.arraycopy(in.array(), in.arrayOffset() + in.position(),
					out.array(), out.arrayOffset() + out.position(), n);
			in.position(in.position() + n);
			out.position(out.position() + n);
			return;
		}
		for (int i = 0; i < n; i++)
			out.put(i, in.get(i));
		in.position(in.position() + n);
		out.position(out.position() + n);
	}

	/**Copy samples from a float[] into a FloatBuffer without changing the buffer's position.
	 * Copies at most src.length samples or dst.remaining(), whichever is smaller. */
	public static void copy(float[] src, FloatBuffer dst) {
	    if (src == null || dst == null) return;
	    int len = Math.min(src.length, dst.remaining());
	    int pos = dst.position();
	    for (int i = 0; i < len; i++) {
	        dst.put(pos + i, src[i]);
	    }
	}

	public static void copy(FloatBuffer in, float[] out) {
		if (in == null || out == null) return;
		in.rewind();
		int n = Math.min(in.remaining(), out.length);
		if (in.hasArray()) {
			System.arraycopy(in.array(), in.arrayOffset() + in.position(), out, 0, n);
			in.position(in.position() + n);
			return;
		}
		for (int i = 0; i < n; i++)
			out[i] = in.get(i);
		in.position(in.position() + n);
	}

	public static void copy(float[] in, float[] out) {
		if (in == null || out == null) return;
		int n = Math.min(in.length, out.length);
		System.arraycopy(in, 0, out, 0, n);
	}

	public static void copy(float[][] in, float[][] out) {
		for (int i = 0; i < in.length; i++) {
			System.arraycopy(in[i], 0, out[i], 0, Math.min(in[i].length, out[i].length));
		}
	}

	/** malloc */
	public static float[][] clone(float[][] in) {
		float[][] out = new float[in.length][in[0].length];
		copy(in, out);
		return out;
	}

	/** Source: be.tarsos.dsp.AudioEvent
	 * Converts a linear (rms) to a dB value. */
	public static double linearToDecibel(final double value) {
		return 20.0 * Math.log10(value);
	}

	/**@param sr sampleRate  in Hz
	 * @return the frequency above which aliasing artifacts are found */
	public static float nyquistFreq(float sampleRate) {
		return sampleRate / 2f;
	}
	/** @return nyquist for the system's sample rate */
	public static float nyquistFreq() {
		return nyquistFreq(Constants.sampleRate());
	}

	public static String sampleToSeconds(long sampleNum) {
		return String.format("%.2f", (sampleNum / (Constants.fps() * Constants.bufSize())));
	}

	/** @return seconds.## */
	public static String toSeconds(long millis) {
		return new StringBuffer().append( millis / 1000).append(".").append(
				(millis / 10) % 100).append("s").toString();
	}

	public static Vector<Mixer.Info> getMixerInfo(
			final boolean supportsPlayback, final boolean supportsRecording) {
		final Vector<Mixer.Info> infos = new Vector<Mixer.Info>();
		final Mixer.Info[] mixers = AudioSystem.getMixerInfo();
		for (final Info mixerinfo : mixers) {
			if (supportsRecording
					&& AudioSystem.getMixer(mixerinfo).getTargetLineInfo().length != 0) {
				// Mixer capable of recording audio if target line length != 0
				infos.add(mixerinfo);
			} else if (supportsPlayback
					&& AudioSystem.getMixer(mixerinfo).getSourceLineInfo().length != 0) {
				// Mixer capable of audio play back if source line length != 0
				infos.add(mixerinfo);
			}
		}
		return infos;
	}

	/**
	 * Fill `buf` with a sine wave at freqHz. Returns the updated phase (radians) to use
	 * for the next call so the tone is continuous across buffers.
	 *
	 * @param buf       float[] buffer to fill (length = FFT_SIZE)
	 * @param freqHz    desired frequency in Hz
	 * @param sampleRate sample rate in Hz (e.g. 48000.0)
	 * @param amplitude amplitude (0..1)
	 * @return new phase in radians to pass to the next call
	 */
	public static double fillSine(float[] buf, double freqHz, double sampleRate, double amplitude) {
		double phase = 0;
		if (buf == null || buf.length == 0) return phase;
	    final double twoPi = 2.0 * Math.PI;
	    // phase increment per sample (radians)
	    final double phaseInc = twoPi * freqHz / sampleRate;

	    // keep amplitude safe
	    final double a = Math.max(0.0, Math.min(1.0, amplitude));

	    for (int i = 0; i < buf.length; i++) {
	        buf[i] = (float) (a * Math.sin(phase));
	        phase += phaseInc;
	        // wrap phase into [0, 2PI) to avoid unbounded growth
	        if (phase >= twoPi) phase -= twoPi * Math.floor(phase / twoPi);
	    }
	    return phase;
	}


	// Not used
//    /**Estimate an RMS-like amplitude from spectral magnitudes in an absolute bin range. */
//    public static double computeFrameRms(float[] amplitudes, int absStartBin, int absEndBin) {
//        absStartBin = Math.max(0, Math.min(amplitudes.length - 1, absStartBin));
//        absEndBin = Math.max(0, Math.min(amplitudes.length - 1, absEndBin));
//        if (absEndBin < absStartBin) return 0.0;
//
//        double sumPower = 0.0;
//        int count = 0;
//        for (int i = absStartBin; i <= absEndBin; i++) {
//            float mag = amplitudes[i];
//            if (!Float.isFinite(mag) || mag <= 0f) continue;
//            double p = mag * (double) mag;
//            sumPower += p;
//            count++;
//        }
//        if (count == 0) return 0.0;
//        double meanPower = sumPower / count;
//        return Math.sqrt(meanPower); // RMS-like amplitude
//    }

}