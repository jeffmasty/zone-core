package judahzone.util;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;
import java. util.concurrent.atomic.AtomicBoolean;

import javax.swing.JOptionPane;

import judahzone.data.Recording;

public class Memory {

    public static final Memory STEREO = new Memory(WavConstants.STEREO, Constants.bufSize());
    public static final Memory MONO = new Memory(WavConstants.MONO, Constants.bufSize());
    public static final float[] ZERO = new float[Constants.bufSize()];

    static final int PRELOAD = 4096;
    static final int THRESHOLD = (int)(PRELOAD * 0.9f);
    static final int RELOAD = (int)(PRELOAD * 0.25f);
    static final String ERROR = "DEPLETED";

    private final LinkedBlockingQueue<float[]> memory = new LinkedBlockingQueue<>();
    private final int channelCount;
    private final int bufSize;
    private final AtomicBoolean reloading = new AtomicBoolean(false);

    public Memory(int numChannels, int bufferSize) {
        this.channelCount = numChannels;
        this.bufSize = bufferSize;
        preload(PRELOAD);
    }

    public float[][] getFrame() {
        // Only trigger reload if not already in progress
        if (memory.size() < THRESHOLD && reloading.compareAndSet(false, true)) {
            Threads.execute(() -> {
                try {
                    preload(RELOAD);
                } finally {
                    reloading.set(false);
                }
            });
        }

        try {
            float[][] result = new float[channelCount][];
            for (int idx = 0; idx < channelCount; idx++) {
                float[] ch = memory.poll();
                if (ch == null)
                    throw new InterruptedException(ERROR);
                result[idx] = ch;
            }
            return result;
        } catch (InterruptedException e) {
            RTLogger.warn(this, e);
            return new float[channelCount][bufSize];
        }
    }

    public void catchUp(Recording tape, int length) {
        for (int i = tape.size(); i < length; i++)
            tape.add(getFrame());
    }

    private void preload(final int amount) {
        for (int i = 0; i < amount; i++)
            memory.add(new float[bufSize]);
    }

	public void release(float[][] buf) {
		Threads.execute(() -> {
			for (float[] channel : buf) {
				Arrays.fill(channel, 0f);
				memory.add(channel);
			}
		});
	}

	public void release(Recording job) {
		for(int i = 0; i < job.size(); i++) {
			release(job.get(i));
		}
	}

	 /**
     * Public wrapper that shows a Retry/OK dialog if the quick memory check fails.
     * Retry re-runs the estimation; OK returns false (user cancels).
     */
    public static boolean checkFFT(File f) {
        return checkWithDialog(f, true);
    }

    /**
     * Public wrapper that shows a Retry/OK dialog if the quick memory check fails.
     * Retry re-runs the estimation; OK returns false (user cancels).
     */
    public static boolean check(File f) {
        return checkWithDialog(f, false);
    }

    private static boolean checkWithDialog(File f, boolean includeFft) {
        if (f == null || !f.isFile()) return false;

        while (true) {
            boolean ok = check(f, includeFft); // call the estimator below
            if (ok) return true;

            String msg = "File too large for memory.\n" + f.getName()
                    + "\n\nClose other applications and choose Retry, or press OK to cancel.";
            int opt = JOptionPane.showOptionDialog(
                    null,
                    msg,
                    "Memory Check",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    null,
                    new Object[] {"Retry", "OK"},
                    "Retry"
            );

            if (opt == 0) {
                // Retry chosen: loop and attempt the estimation again
                continue;
            } else {
                // OK chosen or dialog closed: user cancels the load
                return false;
            }
        }
    }

	static boolean check(File f, boolean includeFft) {
	    if (f == null || !f.isFile()) return false;

	    long fileBytes = f.length();
	    if (fileBytes <= 0) return false;

	    try {
	        String name = f.getName().toLowerCase();
	        boolean compressed = FromDisk.isCompressedFormat(name);

	        int channels = 2;
	        int bytesPerSample = 2; // 16-bit

	        long bytesPerSecondPcm = (long) Constants.sampleRate() * channels * bytesPerSample;
	        if (bytesPerSecondPcm <= 0) return true;

	        double effectiveBytes = compressed ? fileBytes * FromDisk.COMPRESSED_BYTES_FACTOR : fileBytes;
	        double seconds = effectiveBytes / bytesPerSecondPcm;
	        long frames = (long) Math.ceil(seconds * Constants.fps());
	        if (frames <= 0) return true;

	        // Recording memory: 2 channels * JACK_BUFFER * 4 bytes (float)
	        long bytesPerFrame = 2L * Constants.bufSize() * Float.BYTES;
	        long recordingBytes = frames * bytesPerFrame;

	        long estimatedBytes = recordingBytes;

	        if (includeFft) {
	            // FFT / Transform memory (Scope)
	            int chunksPerFft = Constants.fftSize() / Constants.bufSize();        // CHUNKS
	            long fftFrames = Math.max(1, frames / Math.max(1, chunksPerFft));
	            long fftBytes = fftFrames * Constants.amplitudeSize() * Float.BYTES;
	            estimatedBytes += fftBytes;
	        }

	        long maxHeap = Runtime.getRuntime().maxMemory();
	        long softCap = maxHeap / 3;                 // at most 1/3 heap
	        long limit = Math.min(softCap, FromDisk.MAX_RECORDING_BYTES);

	        return estimatedBytes < limit;

	    } catch (Throwable t) {
	        RTLogger.warn(FromDisk.class, t);
	        return true;
	    }
	}
}
