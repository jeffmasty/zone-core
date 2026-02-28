package judahzone.api;

import javax.sound.midi.ShortMessage;

public enum Key {

	C(0, 0, null), Db(0, 5, "C#"), D(2, 0, null),
	Eb(0, 3, "D#"), E(4, 0, null), F(0, 1, null),
	Gb(6, 6, "F#"),	G(1, 0, null), Ab(0, 4, "G#"),
	A(3, 0, null), Bb(0, 2, "A#"), B(0, 5, null);

	public static final String FLAT = "\u266D";
	public static final String SHARP = "\u266F";
	public static final int OCTAVE = 12;
	public static final float TUNING = 440;

	private Key(int sharps, int flats, String alt) {
		this.sharps = sharps;
		this.flats = flats;
		this.alt = alt;
	}

	private final int sharps;
	private final int flats;
	final String alt;

	public int getSharps() {
		return sharps;
	}

	public int getFlats() {
		return flats;
	}

	public String getAlt() {
		return alt;
	}

	public static boolean isPlain(int data1) {
		return Key.values()[Math.floorMod(data1, OCTAVE)].alt == null;
	}

	public static Key lookup(String txt) {
		for (Key k : values())
			if (k.name().equals(txt) || txt.equals(k.alt))
				return k;
		return C; // fail
	}

	public static Key key(int data1) {
		// Accept any integer (may be negative) and wrap into 0..11 safely.
		int idx = Math.floorMod(data1, OCTAVE);
		return Key.values()[idx];
	}

	public static Key key(ShortMessage m) {
		return key(m.getData1());
	}

    public String alt() { return alt; }

	public Key offset(int offset) {
		int target = ordinal() + offset;
		while (target < 0)
			target += Key.values().length;
		target %= Key.values().length;
		return Key.values()[target];
	}

	public int interval(Key key) {
		int mine = ordinal();
		int other = key.ordinal();
		if (mine > other)
			mine -= OCTAVE;
		return other - mine;
	}

	public Key next() {
		if (ordinal() +  1 == values().length) return values()[0];
		return values()[ordinal() + 1];
	}
	public Key prev() {
		if (ordinal() == 0) return values()[values().length - 1];
		return values()[ordinal() - 1];
	}
	public int up(Key target) {
		if (this == target) return 0;
		int count = 1;
		Key temp = next();
		while (temp != target) {
			count++;
			temp = temp.next();
		}
		return count;
	}

	public int down(Key target) {
		if (this == target) return 0;
		int count = 1;
		Key temp = prev();
		while (temp != target) {
			count++;
			temp = temp.prev();
		}
		return count;
	}

}



