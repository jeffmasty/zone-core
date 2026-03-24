package judahzone.api;

import judahzone.prism.PrismUI;

/* UI-side API: called from GUI/helper threads. Mutations and snapshot rebuilds happen here.*/
public interface UINotes {

	/** engage or release pedal (may perform GUI-side MIDI writes). */
	@PrismUI
	void setPedal(boolean pressed);

	/**explicit update hook to process RT→GUI ring and rebuild snapshot. */
	@PrismUI // Updateable
	void update();

	boolean isPedal();

	int getChannel();

	MidiOut getMidiOut();

	boolean isEmpty();

	int[] actives();



}