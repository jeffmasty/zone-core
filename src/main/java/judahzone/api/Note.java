package judahzone.api;

import judahzone.data.Frequency;

public record Note(Key key, int octave, float diff) {

	public Note(Key key, int octave) {
		this(key, octave, 0);
	}

	public Note(float hz) {
		this(Key.key(Frequency.hzToMidi(hz)), Frequency.hzToMidi(hz) / 12,
				hz - Key.toFrequency(Key.key(Frequency.hzToMidi(hz)), Frequency.hzToMidi(hz) / 12));
	}


	@Override public String toString() {
		return (key.alt == null ? key.name() : key.alt) + "" + octave;}

	public String full() {
		StringBuilder sb = new StringBuilder("(").append(toString());
		sb.append(diff == 0 ? "" : diff > 0 ? " +" + String.format("%.1f", diff) : " " + String.format("%.1f", diff));
		return sb.append(")").toString();
	}

	private static Note[] all = new Note[Byte.MAX_VALUE];
	private static boolean initialized = false;

	public static Note[] values() {
		if (!initialized) {
			for (int i = 0; i < all.length; i++) {
				all[i] = new Note(Key.key(i), i / 12);
			}
			initialized = true;
		}
		return all;
	}

}