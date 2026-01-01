package judahzone.util;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory;

/**
 * Small helper that encapsulates the TarsosDSP AudioDispatcher-based file decoding logic
 * that used to live inside Recording's constructor.
 *
 * Usage:
 *   Recording rec = DispatcherHelper.load(file);               // default mastering 1.0
 *   Recording rec = DispatcherHelper.load(file, 0.8f);        // explicit mastering
 *   DispatcherHelper.loadInto(file, 1.0f, existingRecording); // fill an existing Recording
 *
 * Notes:
 * - TarsosDSP will decode any format supported by the installed Java Sound/SPIs (wav, mp3 if you
 *   have an MP3 SPI or JLayer integration available). If MP3 doesn't decode, install an MP3
 *   SPI (e.g. mp3spi/jl) or use a system ffmpeg/bridge decode path.
 * - The helper uses the project's WavConstants (sample rate and JACK_BUFFER) so decoded audio
 *   is chunked into the same jack-sized stereo blocks used throughout the codebase.
 */
public final class MP3 {

	static {Logger.getLogger("be.tarsos.dsp.io.PipeDecoder").setLevel(Level.SEVERE);} // too loud
    private MP3() { /* no instances */ }

    /** Convenience: default mastering = 1.0f */
    public static Recording load(File f) {
        return load(f, 1f);
    }

    /** Convenience: fill and return a new Recording by decoding the file. */
    public static Recording load(File f, float mastering) {
        Recording out = new Recording();
        loadInto(f, mastering, out);
        return out;
    }

    /**
     * Decode the supplied file and append jack-sized stereo blocks into the provided Recording.
     *
     * This mirrors the previous decoding/dispatch logic: it handles mono, non-interleaved and
     * interleaved float buffers produced by Tarsos.
     *
     * @param f         file to decode (may be null)
     * @param mastering positive multiplier applied to samples (clamped to [-1,1])
     * @param out       Recording to append decoded blocks to (must not be null)
     */
    public static void loadInto(File f, float mastering, Recording out) {
        if (out == null) throw new IllegalArgumentException("out Recording must not be null");
        if (f == null) return;

        AudioDispatcher dispatcher = AudioDispatcherFactory.fromPipe(
                f.getAbsolutePath(), WavConstants.S_RATE, WavConstants.JACK_BUFFER, 0);

        final int channels = dispatcher.getFormat().getChannels();

        // assembling blocks
        final float[] leftBlock = new float[WavConstants.JACK_BUFFER];
        final float[] rightBlock = new float[WavConstants.JACK_BUFFER];

        dispatcher.addAudioProcessor(new AudioProcessor() {
            @Override
            public boolean process(AudioEvent audioEvent) {
                float[] buf = audioEvent.getFloatBuffer();
                int posInBlock = 0;

                if (channels <= 1) {
                    // mono -> duplicate into L & R
                    for (float v : buf) {
                        float s = clamp(v * mastering);
                        leftBlock[posInBlock] = s;
                        rightBlock[posInBlock] = s;
                        posInBlock++;
                        if (posInBlock >= WavConstants.JACK_BUFFER) {
                            float[][] block = new float[2][WavConstants.JACK_BUFFER];
                            System.arraycopy(leftBlock, 0, block[0], 0, WavConstants.JACK_BUFFER);
                            System.arraycopy(rightBlock, 0, block[1], 0, WavConstants.JACK_BUFFER);
                            out.add(block);
                            posInBlock = 0;
                        }
                    }
                    return true;
                }

                // Heuristic: sometimes Tarsos supplies non-interleaved halves (L... then R...)
                if (buf.length % 2 == 0) {
                    int half = buf.length / 2;
                    if (half <= WavConstants.JACK_BUFFER * 8) { // cheap heuristic to avoid false positives
                        for (int i = 0; i < half; i++) {
                            float l = clamp(buf[i] * mastering);
                            float r = clamp(buf[half + i] * mastering);
                            leftBlock[posInBlock] = l;
                            rightBlock[posInBlock] = r;
                            posInBlock++;
                            if (posInBlock >= WavConstants.JACK_BUFFER) {
                                float[][] block = new float[2][WavConstants.JACK_BUFFER];
                                System.arraycopy(leftBlock, 0, block[0], 0, WavConstants.JACK_BUFFER);
                                System.arraycopy(rightBlock, 0, block[1], 0, WavConstants.JACK_BUFFER);
                                out.add(block);
                                posInBlock = 0;
                            }
                        }
                        return true;
                    }
                }

                // Fallback: interleaved LRLR...
                for (int i = 0; i + 1 < buf.length; i += 2) {
                    float l = clamp(buf[i] * mastering);
                    float r = clamp(buf[i + 1] * mastering);
                    leftBlock[posInBlock] = l;
                    rightBlock[posInBlock] = r;
                    posInBlock++;
                    if (posInBlock >= WavConstants.JACK_BUFFER) {
                        float[][] block = new float[2][WavConstants.JACK_BUFFER];
                        System.arraycopy(leftBlock, 0, block[0], 0, WavConstants.JACK_BUFFER);
                        System.arraycopy(rightBlock, 0, block[1], 0, WavConstants.JACK_BUFFER);
                        out.add(block);
                        posInBlock = 0;
                    }
                }
                return true;
            }

            @Override
            public void processingFinished() {
                // nothing to do here for now
            }
        });

        // run decoding (blocks until finished)
        dispatcher.run();

    }

    private static float clamp(float s) {
        return Math.max(-1f, Math.min(1f, s));
    }
}