package judahzone.api;

/** User-defined Channel */
public record Custom (
    String name,		// regen
    boolean stereo,		// regen
    boolean onMixer,
    String leftPort,	// regen
    String rightPort,	// regen
    String midiPort,	// regen
    String iconName,
    Integer lowCutHz,
    Integer highCutHz,
	String engine, 		// regen, internal TacoTrucks? FluidSynthEngine?
	boolean clocked, 	// regen, fine-tune
	Float preamp 		// null = Gain defaults
) {

	public static boolean regen(Custom original, Custom updated) {
		// Check if any "regen" fields differ
		return !original.name().equals(updated.name())
			|| original.stereo() != updated.stereo()
			|| !nullSafeEquals(original.leftPort(), updated.leftPort())
			|| !nullSafeEquals(original.rightPort(), updated.rightPort())
			|| !nullSafeEquals(original.midiPort(), updated.midiPort())
			|| !nullSafeEquals(original.engine(), updated.engine())
			|| original.clocked() != updated.clocked();
	}

	private static boolean nullSafeEquals(Object a, Object b) {
		return a == null ? b == null : a.equals(b);
	}


}