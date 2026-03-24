package judahzone.api;

import java.util.concurrent.atomic.AtomicInteger;

public interface MidiUpdate {

	void updateMidi(MidiOut midiOut);

	void updateDrum(Object drum);

	void updateSynth(Object synth);


	public static class Mock implements MidiUpdate {
	    public final AtomicInteger count = new AtomicInteger();
		private final void count() { count.incrementAndGet(); }
		@Override public void updateMidi(MidiOut midiOut) 	{ count(); }
		@Override public void updateDrum(Object drum) 		{ count(); }
		@Override public void updateSynth(Object synth) 	{ count();}
	}

}
