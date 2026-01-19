package judahzone.api;

import java.util.List;

/**Engine-agnostic port API for async creation/query/connect of ports.
Providers (engine-specific) implement the Provider interface and use Request/Query/Connect records to schedule work.
Callbacks are invoked when an operation completes; reply is implementation-specific (e.g. port object or list of port names). */
public abstract class Ports {

	public enum Type { AUDIO, MIDI }

	public enum IO { IN, OUT }

	/** Port wrapper for engine-specific Port object */
	public record Wrapper(String name, Object port) { }

	/**Engine-agnostic provider interface. Implementations should process
    requests asynchronously and invoke the callback.ready(...) on
    completion. Implementations may queue requests internally. */
	public static interface Provider {
		// ASYNC
		/** convenience discovery API — implementations may call consumer.accept(...) */
		void query(PortData consumer);
		/** enqueue a port creation/request. */
		void register(Request req);
		void unregister(Request req, Wrapper wrap);
		/**enqueue a connect operation: connect externalName <-> localPort (or vice versa).
		 * localPort is an opaque engine-specific handle (may be String, Port object, etc).*/
		void connect(Connect con);

		// SYNC
		/** immediately connect two endpoints - caller assumes threading responsibilities (for jack, need RT)*/
		void connectNow(Object ours, Type type, String portName) throws Exception;
		/** immediately run a port creation request - caller assumes threading responsibilities (for jack, need RT)*/
		Wrapper registerNow(Type type, IO io, String portName) throws Exception;
	}

	/**Callback invoked when a Request/Query/Connect completes. The reply object is
	 * implementation defined (e.g. an engine Port object or a List<String>). */
	public static interface PortCallback {
		void registered(Request req, Wrapper reply);
		void connected(Connect con);
	}

	public static interface PortData {
		void queried(List<String> audioPorts, List<String> midiPorts);
	}

	/**Request a named port to be created/registered in the audio engine. -
	 * portName: requested engine-local name - type: AUDIO or MIDI - dir: IN or OUT */
	public static final record Request(Custom user, String portName, Type type, IO io, PortCallback callback) {
	}

	/**Query available ports of a given type/direction. Reply should be a List<String>. */
	public static final record Query(Type type, IO dir, PortData callback) {
	}

	/**Connect an engine-local port (localPort) to matching external ports found by
	 * regEx. - localPort: opaque handle owned by provider (String or engine Port
	 * object) - regEx: pattern used by provider to select external ports to connect
	 * - type/dir: constrain search/connection semantics */
	public static final record Connect(Custom user, Wrapper localPort, String regEx, Type type, IO io, PortCallback callback) {
	}
}
