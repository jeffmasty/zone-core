package judahzone.api;

import judahzone.prism.PrismRT;

/* tracks active voices of a MIDI channel (call from audio/MIDI RT threads).*/
public interface RTNotes {

	/** channel used by this note container */
	@PrismRT
	int getChannel();

	/** receive a compact packed MIDI command (see MidiCmdPack). */
	@PrismRT
	void receivePacked(int packedMidiCmd);

	/** remove any active note matching data1 (0..127). */
	@PrismRT
	void removeData1(int data1);

	/**lock-free query whether data1 is active using a published snapshot */
	@PrismRT
	boolean isActive(int data1);

	/** index lookup inside the RT-owned structure (may return -1). */
	@PrismRT
	int indexOf(int data1);

}