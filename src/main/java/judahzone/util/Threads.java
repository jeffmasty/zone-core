package judahzone.util;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lightweight helper for submitting tasks and performing a best-effort shutdown.
 *
 * - Worker threads are created as daemon threads so forgotten tasks won't keep the JVM alive.
 * - A Runtime shutdown hook performs a best-effort graceful shutdown.
 * - Call {@link #shutdown()} explicitly for deterministic cleanup.
 * - {@link #getShutdown()} returns a {@link Closeable} that delegates to {@link #shutdown()}
 *   so this utility can be used in try-with-resources or passed to APIs that accept Closeable.
 */
public class Threads {

    // Make worker threads daemon so they won't block JVM exit if forgotten.
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(0);
    private static final ThreadFactory DAEMON_FACTORY = r -> {
        Thread t = new Thread(r, "jz-worker-" + THREAD_COUNTER.getAndIncrement());
        t.setDaemon(true);
        return t;
    };

    private static final ExecutorService threads = Executors.newFixedThreadPool(256, DAEMON_FACTORY);

    // virtual threads executor -- still should be shut down explicitly
    private static final ExecutorService virtual = Executors.newVirtualThreadPerTaskExecutor();

    static {
        // Register shutdown hook to attempt graceful shutdown on JVM exit.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            shutdown(); // best-effort cleanup
        }, "jz-shutdown-hook"));
    }

    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (Throwable t) {
            System.err.println(t.getMessage());
        }
    }

    public static void timer(long msec, final Runnable r) {
        threads.execute(() -> {
            sleep(msec);
            r.run();
        });
    }

    public static void execute(Runnable r) {
	    try {
	        threads.execute(r);
	    } catch (RejectedExecutionException e) {
	        // pool shutting down: run inline or in a dedicated non-pooled thread, by golly
	        new Thread(r).start();
	    }
    }

    public static void virtual(Runnable r) {
        virtual.execute(r);
    }

    public static void writeToFile(File file, String content) {
        execute(() -> {
            try {
                Files.write(Paths.get(file.toURI()), content.getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Attempt graceful shutdown of internal executors.
     * Safe to call multiple times.
     */
    public static void shutdown() {
        // First try graceful shutdown
        try {
            threads.shutdown();
            virtual.shutdown();
            // wait a short while for tasks to finish
            if (!threads.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                threads.shutdownNow();
            }
            if (!virtual.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                virtual.shutdownNow();
            }
        } catch (InterruptedException e) {
            // restore interrupt status and force shutdown
            Thread.currentThread().interrupt();
            threads.shutdownNow();
            virtual.shutdownNow();
        } catch (Throwable t) {
            // last resort
            threads.shutdownNow();
            virtual.shutdownNow();
        }
    }

 }