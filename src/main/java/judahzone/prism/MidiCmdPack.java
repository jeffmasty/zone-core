package judahzone.prism;

/** Compact, RT-friendly 32-bit packing for short MIDI commands.
    Layout (MSB→LSB): [cmdType:8][channel:8][data1:8][data2:8]  */
public final class MidiCmdPack {

	// Command codes
	public static final int CMD_ADD = 1;
	public static final int CMD_SET = 2;
	public static final int CMD_REMOVE = 3;
	public static final int CMD_NOTE_OFF = 4;
	public static final int CMD_RETRIGGER = 5;

	// Bit layout
	private static final int SHIFT_CMD     = 24;
	private static final int SHIFT_CHANNEL = 16;
	private static final int SHIFT_D1      = 8;
	private static final int SHIFT_D2      = 0;
	private static final int MASK_8 = 0xFF;

	private MidiCmdPack() { /* utility */ }

	/** Pack four 8-bit fields into a single int. Values are masked to 0..255. */
	public static int pack(int cmdType, int channel, int data1, int data2) {
	    return ((cmdType & MASK_8) << SHIFT_CMD)
	         | ((channel & MASK_8) << SHIFT_CHANNEL)
	         | ((data1 & MASK_8) << SHIFT_D1)
	         | ((data2 & MASK_8) << SHIFT_D2);
	}

	/** Convenience: pack an ADD (note on) command. */
	public static int packAdd(int channel, int data1, int data2) {
	    return pack(CMD_ADD, channel, data1, data2);
	}

	/** Convenience: pack a RETRIGGER command. */
	public static int packRetrigger(int channel, int data1, int data2) {
	    return pack(CMD_RETRIGGER, channel, data1, data2);
	}

	/** Convenience: pack a NOTE_OFF command (data2 typically velocity=0). */
	public static int packNoteOff(int channel, int data1) {
	    return pack(CMD_NOTE_OFF, channel, data1, 0);
	}

	/** Convenience: pack a REMOVE command (only data1 relevant). */
	public static int packRemove(int data1) {
	    return pack(CMD_REMOVE, 0, data1, 0);
	}

	/** Extract cmdType (0..255). */
	public static int cmdType(int packed) { return (packed >>> SHIFT_CMD) & MASK_8; }

	/** Extract channel (0..255). */
	public static int channel(int packed) { return (packed >>> SHIFT_CHANNEL) & MASK_8; }

	/** Extract data1 (0..255). */
	public static int data1(int packed) { return (packed >>> SHIFT_D1) & MASK_8; }

	/** Extract data2 (0..255). */
	public static int data2(int packed) { return (packed >>> SHIFT_D2) & MASK_8; }

	/** Return a concise human-readable representation useful for logging/debug. */
	public static String toString(int packed) {
	    return "Cmd{" + cmdType(packed) + "} Ch{" + channel(packed) +
	           "} D1{" + data1(packed) + "} D2{" + data2(packed) + "}";
	}

}