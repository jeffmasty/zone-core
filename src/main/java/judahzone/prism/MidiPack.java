// java
package judahzone.prism;

/** Compact, RT-friendly channel-less 24-bit packing for short MIDI-like commands.
    Layout (MSB→LSB): [cmdType:8][data1:8][data2:8] */
public final class MidiPack {

    // Command codes (match MidiCmdPack semantics where applicable)
    public static final int CMD_ADD       = 1;
    public static final int CMD_SET       = 2;
    public static final int CMD_REMOVE    = 3;
    public static final int CMD_NOTE_OFF  = 4;
    public static final int CMD_RETRIGGER = 5;

    // Bit layout
    private static final int SHIFT_CMD = 16;
    private static final int SHIFT_D1  = 8;
    private static final int SHIFT_D2  = 0;
    private static final int MASK_8    = 0xFF;

    private MidiPack() { /* utility */ }

    /** Pack three 8-bit fields into an int (upper byte unused). */
    public static int pack(int cmdType, int data1, int data2) {
        return ((cmdType & MASK_8) << SHIFT_CMD)
             | ((data1  & MASK_8) << SHIFT_D1)
             | ((data2  & MASK_8) << SHIFT_D2);
    }

    public static int packAdd(int data1, int data2) {
        return pack(CMD_ADD, data1, data2);
    }

    public static int packRetrigger(int data1, int data2) {
        return pack(CMD_RETRIGGER, data1, data2);
    }

    public static int packNoteOff(int data1) {
        return pack(CMD_NOTE_OFF, data1, 0);
    }

    public static int packRemove(int data1) {
        return pack(CMD_REMOVE, data1, 0);
    }

    /** Extract cmdType (0..255). */
    public static int cmdType(int packed) { return (packed >>> SHIFT_CMD) & MASK_8; }

    /** Extract data1 (0..255). */
    public static int data1(int packed) { return (packed >>> SHIFT_D1) & MASK_8; }

    /** Extract data2 (0..255). */
    public static int data2(int packed) { return (packed >>> SHIFT_D2) & MASK_8; }

    /** Human-readable representation useful for logging/debug. */
    public static String toString(int packed) {
        return "Cmd{" + cmdType(packed) + "} D1{" + data1(packed) + "} D2{" + data2(packed) + "}";
    }
}
