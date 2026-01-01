package judahzone.util;

import java.io.Closeable;
import java.util.ArrayList;

// App responsible for hooking into Shutdown
public class Services {

	private static final ArrayList<Closeable> services = new ArrayList<Closeable>();

	public static void add(Closeable c) {
		services.add(c);
	}

	public static void remove(Closeable closed) {
		services.remove(closed);
	}

	public static boolean contains(Closeable service) {
		return services.contains(service);
	}

	public static void shutdown() {
		for (int i = services.size() - 1; i >= 0; i--)
			try {
				services.get(i).close();
			} catch (Exception e) {
				System.err.println(e);
			}
	}


}
