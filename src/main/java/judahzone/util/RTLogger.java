package judahzone.util;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

import org.apache.logging.log4j.Level; // Log4j2 Level

import lombok.extern.log4j.Log4j2;      // Lombok Log4j2
/**
 * Headless logging service with a static API.
 *
 * - Queue and dispatcher are private/static.
 * - GUI or other components can register as Participants to receive notifications.
 * - Non-blocking offer() is used so callers (including real-time threads) don't block.
 *
 * This is a replacement for the previous LogService but exposes the original style
 * of static methods so you can call RTLogger.log(...), RTLogger.warn(...), etc.
 */
@Log4j2
public final class RTLogger {

    public static interface Participant {
        /**
         * Receive a log event as a String array:
         *   [0] = source (class or caller name)
         *   [1] = message
         *   [2] = "WARN" or "INFO" (or other flag)
         */
        void process(String[] input);
    }

    public record LogEvent(String source, String message, boolean warn) { }

    // bounded queue to avoid unbounded memory growth; offer() used to avoid blocking callers
    private static final BlockingQueue<LogEvent> queue = new LinkedBlockingQueue<>(1024);

    // participants (GUI, file writer, tests, etc.)
    private static final CopyOnWriteArrayList<Participant> participants = new CopyOnWriteArrayList<>();

    // convenience: allow functional registration
    private static final CopyOnWriteArrayList<Consumer<LogEvent>> consumers = new CopyOnWriteArrayList<>();

    // logging level (defaults to INFO)
    private static volatile Level level = Level.INFO;

    // start dispatcher thread once
    static {
    	// initLoging();
        Thread.startVirtualThread(() -> {
            try {
                while (true) {
                    LogEvent ev = queue.take(); // blocking consumer
                    // notify registered participants (old-style) with String[] payload
                    String[] payload = new String[] {
                        ev.source(),
                        ev.message(),
                        ev.warn() ? "WARN" : "INFO"
                    };
                    for (Participant p : participants) {
                        try {
                            p.process(payload);
                        } catch (Exception ex) {
                            log.error("RTLogger participant failed", ex);
                        }
                    }
                    // notify functional consumers with the record
                    for (Consumer<LogEvent> c : consumers) {
                        try {
                            c.accept(ev);
                        } catch (Exception ex) {
                            log.error("RTLogger consumer failed", ex);
                        }
                    }
                    // write to log4j/backing logger as well
                    if (ev.warn()) {
                        log.warn(ev.source() + " WARN: " + ev.message());
                    } else {
                        log.info(ev.source() + ": " + ev.message());
                    }
                }
            } catch (Throwable t) {
                // catastrophic failure — fallback to stderr
                System.err.println("RTLogger dispatcher died: " + t);
                t.printStackTrace(System.err);
            }
        });
    }

    // ======== Public static API (convenience / compatibility) ========

    public static Level getLevel() {
        return level;
    }

    public static void setLevel(Level newLevel) {
        level = newLevel;
    }

    public static void registerParticipant(Participant p) {
        if (p != null) participants.addIfAbsent(p);
    }

    public static void unregisterParticipant(Participant p) {
        if (p != null) participants.remove(p);
    }

    /**
     * Register a Consumer-style listener (preferred for modern code).
     */
    public static void registerConsumer(Consumer<LogEvent> consumer) {
        if (consumer != null) consumers.addIfAbsent(consumer);
    }

    public static void unregisterConsumer(Consumer<LogEvent> consumer) {
        if (consumer != null) consumers.remove(consumer);
    }

    public static void log(Object caller, String msg) {
        offer(new LogEvent(caller instanceof String ? caller.toString() : caller.getClass().getSimpleName(), msg, false));
    }

    public static void log(Class<?> caller, String msg) {
        log(caller.getSimpleName(), msg);
    }

    public static void warn(Object caller, String msg) {
        offer(new LogEvent(caller instanceof String ? caller.toString() : caller.getClass().getSimpleName(), msg, true));
    }

    public static void warn(Class<?> caller, String msg) {
        warn(caller.getSimpleName(), msg);
    }

    public static void warn(Object o, Throwable e) {
        warn(o, e == null ? "<null>" : e.getLocalizedMessage());
        if (e != null) e.printStackTrace();
    }

    public static void warn(Throwable t) {
        warn("RTLogger", t == null ? "<null>" : t.getMessage());
        if (t != null) t.printStackTrace();
    }

    public static void debug(Object caller, String msg) {
        if (level == Level.DEBUG || level == Level.TRACE) {
            log(caller, "debug " + msg);
        }
    }

    public static void debug(Class<?> caller, String msg) {
        if (level == Level.DEBUG || level == Level.TRACE) {
            // delegate to log(Class, String) so the caller name becomes the actual class simple name
            log(caller, "debug " + msg);
        }
    }
    // ======== Internal helper ========

    private static void offer(LogEvent ev) {
        boolean ok = queue.offer(ev);
        if (!ok) {
            // queue full; avoid blocking caller — drop and warn the logging backend
            log.warn("RTLogger queue full; dropping event: " + ev);
        }
    }

    // Prevent instantiation
    private RTLogger() { /* no-op */ }
//    public static void initLogging() {
//        ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();
//        builder.setStatusLevel(Level.DEBUG);
//        builder.setConfigurationName("ProgrammaticConfig");
//
//        // Layout
//        ComponentBuilder<?> patternLayout = builder.newLayout("PatternLayout")
//            .addAttribute("pattern", "%d{mm:ss} %-5p- %m%n");
//
//        // Console appender
//        AppenderComponentBuilder console = builder.newAppender("console", "Console")
//            .addAttribute("target", "SYSTEM_OUT")
//            .add(patternLayout);
//        builder.add(console);
//
//        // RollingFile appender (basic size policy example)
//        AppenderComponentBuilder file = builder.newAppender("fileAppender", "RollingFile")
//            .addAttribute("fileName", "Judah.log")
//            .addAttribute("filePattern", "Judah-%i.log")
//            .add(patternLayout);
//        file.addComponent(builder.newComponent("Policies")
//            .addComponent(builder.newComponent("SizeBasedTriggeringPolicy").addAttribute("size", "10MB")));
//        builder.add(file);
//
//        // Specific loggers
//        builder.add(builder.newLogger("be.tarsos.dsp.io.PipeDecoder", Level.ERROR).addAttribute("additivity", false));
//        builder.add(builder.newLogger("org.jaudiolibs.jnajack", Level.ERROR).addAttribute("additivity", false));
//
//        // Root logger
//        builder.add(builder.newRootLogger(Level.DEBUG)
//            .add(builder.newAppenderRef("console"))
//            .add(builder.newAppenderRef("fileAppender")));
//
//        Configurator.initialize(builder.build());
//    }
}