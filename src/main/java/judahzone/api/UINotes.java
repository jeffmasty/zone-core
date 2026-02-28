package judahzone.api;

import java.util.Set;

import javax.sound.midi.ShortMessage;

import judahzone.prism.PrismUI;

/* UI-side API: called from GUI/helper threads. Mutations and snapshot rebuilds happen here.*/
public interface UINotes {

	/** receive a full ShortMessage  */
	@PrismUI
	boolean receive(ShortMessage msg);

	/**fill ref with current active data1 values (called on GUI to populate UI).*/
	@PrismUI
	void data1(Set<Integer> ref);

	/** engage or release pedal (may perform GUI-side MIDI writes). */
	@PrismUI
	void setPedal(boolean pressed);

	/**explicit update hook to process RT→GUI ring and rebuild snapshot. */
	@PrismUI // Updateable
	void update();

}