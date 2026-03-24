package judahzone.api;

import javax.sound.midi.ShortMessage;

/* tracks active voices of a MIDI channel (call from audio/MIDI RT threads).*/
public interface RTNotes {

	/** remove any active note matching data1 (0..127). */
	void off(int data1);

	/** add or re-trigger a note with data1 (0..127). */
	void on(ShortMessage velocity);


	boolean isNoteOn(int data1);

	boolean isPedal();
	void setPedal(boolean hold);


}