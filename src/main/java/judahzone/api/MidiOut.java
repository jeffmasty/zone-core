package judahzone.api;

import java.io.Closeable;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;

import judahzone.util.Notes;

/** - MidiOut: channel-agnostic base, stubs if ProgChange not supported. Provides actives[] snapshot.
 *  - MidiDrums: on(data1) / off(data1) for drum machines, no pedal. <- send() handles velocity?
 *  - MidiPiano: on(ShortMessage velocity) / off(data1) for melodic synths, with pedal and prog change.
 *  - MidiBus: multi-channel synths on a single audio input/FX channel. */
public interface MidiOut extends Receiver, Closeable {

	// because MidiOuts progChanges both String and int, it can produce patch list
	String[] getPatches();
	int getProgChange();
	String getProgram();

	/**@param data2 progChange usually embedded in Midi File
	 * @return name of prog change on success or null */
	String progChange(int data2);

	/**@param preset name of prog change
	 * @return name of prog change idx on success or -1 */
	int progChange(String preset);

	/** data1 of active notes */
	int[] actives();

	/** remove any active note matching data1 (0..127). */
	void off(int data1);

	public static interface MidiDrums extends MidiOut {
		/** add or re-trigger a note with data1 (0..127). */
		void on(int data1);
	}

	public static interface MidiPiano extends MidiOut {
		boolean isPedal();
		void setPedal(boolean hold);
		void on(ShortMessage velocity);
		Notes getNotes();
		default void panic() { // test? / overkill? If we trusted actives, we could flush
		    try {
		        for (int note = 0; note < 128; note++) {
		            ShortMessage m = new ShortMessage(ShortMessage.NOTE_OFF, 0, note, 0);
		            send(m, 0);
		        }
		    } catch (InvalidMidiDataException ignored) {
		    }
		}


		// PianoTrack(not api package) getTrack();
	}

	public static interface MidiBus extends MidiOut {
		/**@param data2 progChange usually embedded in Midi File
		 * @return name of prog change on success or null */
		String progChange(int data2, int ch);
		/** @return idx of preset or -1;*/
		int progChange(String preset, int ch);
		String getProgram(int ch);
		int getProgChange(int ch);
		// Vector<? extends NoteTrack :(> getTracks();
	}

}

/*
Key packages & classes

Interfaces:  MidiOut-> MidiDrums || MidiPiano || MidiBus

- judahzone.synth (module root)
  - MidiInstrument - container for external MidiPiano and associated PianoTrack (single track)
  - Generator - container for internal MidiPiano and associated PianoTrack (single track)
  - SynthEngine - multi-track container.
  - judahzone.synth.Synth  — base type and helpers used across taco-synth.
  - judahzone.synth.WavSynth — Subtractive Synth (MidiPiano) instance

- Integration points used by Seq/UI (in JudahZone core):
  - MidiOut — device-level Receiver wrapper (send, program change, actives snapshot).
  - MidiBus / SynthEngine — device-level engines implement multi-track factory methods and
    expose getTracks()/createTrack(...) so Seq can request PianoTrack/NoteTrack instances.
    SynthEngine manages a `Wrapper` MidiPiano instance for each midi-channel/track.
  - Luthier — sequencer-side registry & factory. Responsible for creating/looking up engines and
    allocating PianoTrack instances. See `Seq` usage in the repository.
  - Notes — RT-safe voice tracker used by PianoTrack and engines to expose active notes and pedal state.
	- Expose a small, stable API so `Seq` and UI can create and manage synth tracks without engine internals knowledge.
	- Preserve real-time constraints: no heap allocations on audio/MIDI callbacks; Notes providesRT-safe actives[] snapshot.

Core concepts (short)
- PianoTrack:
  - Melodic track abstraction that uses a Notes instance, handles arpeggiation, pedal and capture.
  - Non-RT APIs: setNotes(...), setArp(...), configuration; RT APIs: send(...)/processNote(...) must be
    allocation-free when routed to an engine's MidiOut.
- Notes (RT/UI split):
  - RT path: allocation-free update of a primitive `actives[]` and volatile flags (mono, pedal).
  - UI path: `notes.update()` rebuilds the snapshot used for display. A ping mechanism notifies UI.
  - Engines must create and own Notes instances and inject them into PianoTrack via `setNotes(...)`.
- MidiBus / SynthEngine:
  - Luthier provides lifecycle and a track factory. `Luthier.createPiano(TrackInfo)` delegates to engine.
  - For multi-channel synths (FluidSynth) engines exposes a Notes for each track.

Notes on FluidSynth (external OS process) and multi-channel engines
  Provided a per-channel Notes wrappers (preferred long-term) so PianoTrack's semantics (pedal) map to a MidiPiano.

Design & real-time recommendations (high level)
- Avoid allocations on RT paths: use primitive arrays (int[]/float[]), preallocate stacks/buffers.
- Use smoothing ramps for parameter changes (frequency/gain) before applying to DSP.
- use lightweight RT metrics, prefer multiplication to division and Math.max/Math.min over branching clamps.

Migration checklist (short)
- Wire `Seq` to call `Luthier.createPiano(info)` and `DrumMachine.allocateDrum(info)` during reconciliation.
- Engines implement `createTrack(...)` and own a Notes instance injected into created PianoTracks.
- Update bundle serialization to emit device/port/channel meta for tracks to be authoritative on load.
- Add unit tests for Luthier allocation, DrumMachine allocation, Notes mono behavior and FluidSynth multi-channel tracking.

 */
