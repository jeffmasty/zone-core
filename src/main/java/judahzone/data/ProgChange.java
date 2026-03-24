package judahzone.data;

/** MIDI program-change identity: (idx 0..127, name).
 *  Compact key for drum-kit / synth-preset lookup. */
public record ProgChange(int idx, String name) {

	/** Maximum assignable program index (MIDI range 0..127). */
	public static final int MAX = Byte.MAX_VALUE;

	public ProgChange {
		idx = Math.max(0, Math.min(MAX, idx));
		name = name == null ? "NULL" : name.isBlank() ? "???" : name;
	}

	/** Match by program index. */
	public boolean matchesIdx(int other) { return idx == other; }

	/** Case-insensitive match by name. */
	public boolean matchesName(String other) {
		return name.equalsIgnoreCase(other);
	}

	/** True if this matches by idx OR name. */
	public boolean matches(ProgChange other) {
		if (other == null) return false;
		return idx == other.idx || name.equalsIgnoreCase(other.name);
	}

	@Override public String toString() { return name; }
}
