package judahzone.util;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import javax.sound.midi.ShortMessage;

import judahzone.api.MidiOut;
import judahzone.api.MidiUpdate;
import judahzone.api.RTNotes;
import judahzone.api.UINotes;
import judahzone.prism.IntRing;
import judahzone.prism.MidiPack;
import judahzone.prism.PrismRT;
import judahzone.prism.PrismUI;
import lombok.Getter;

/** Notes: RT-safe voice tracker + UI snapshot.
 *
 * RT / UI pipeline (explicit):
 *  - RT threads (audio / midi) call the RT-marked methods below. Those methods
 *    must be allocation-free and avoid locks. RT callers update the lightweight
 *    RT-side data structures (the int[] `actives` and related flags) so synths
 *    or audio circuits can observe active notes immediately via RT-safe APIs
 *    such as {@link #isNoteOn(int)} or by reading the preallocated `actives`.
 *
 *  - The IntRing is a SPSC transfer used to publish compact events from RT -> UI.
 *    The RT path enqueues an event and also updates RT-side `actives` so there is
 *    no need for the UI to process the ring before synths react. The ring only
 *    serves to bring the UI snapshot (`voices`, allocations allowed) into sync.
 *
 *  - UI thread drains the ring in {@link #update()} (UI-only). The UI may allocate
 *    (Point, arrays, etc.) and may synchronize on `this` to rebuild snapshots.
 *
 * Notes about mono/monoStack:
 *  - Mono behaviour attempts to mirror classic synths (last-note-steal with
 *    possible restoration). The `monoStack` here is an allocation-using Stack
 *    for simplicity; if mono operations occur on RT you should replace this with
 *    a preallocated primitive stack or ring to avoid heap allocations on RT.
 *
 * In short: RT methods are annotated @PrismRT and are safe for audio threads.
 * UI methods are annotated @PrismUI and may allocate / take locks.
 */
public class Notes implements RTNotes, UINotes {


	private boolean pedal;
	private final MidiUpdate ping;
	private final MidiOut source;

 	/* UI snapshot of active voices (Point.x = key, Point.y = velocity). Mutated only on UI thread. */
 	private final Stack<Point> voices = new Stack<Point>();

	/* RT-safe packed actives (preallocated). Stores MidiPack integers; 0 == empty. */
	private final int[] actives;
	/* next index for oldest-steal overwrite. Volatile for visibility between RT/UI. */
	private volatile int activesNext = 0;

	@Getter private volatile boolean mono = false; // cross-thread visibility for mono mode
	private int oldest = 0; // packed mono ancestor (0 == none)
 	/* Primitive mono stack for RT-safety (preallocated in ctor). */
 	private int[] monoStack; // packed notes held in mono mode
 	private int monoStackTop = 0;

	/* Event ring: RT pushes packed events, UI polls them. */
	private final IntRing ring;

	/* Dirty flag for backward/backstop rebuilds. Volatile for visibility. */
	private volatile boolean dirty = false;

	/* Mapping of input ShortMessage -> generated output keys (non-RT: UI/seq usage). */
	private final Map<ShortMessage, List<Integer>> mappings = new HashMap<>();

//	public Notes(MidiOut source, MidiUpdate ping) {
//		this(ping, source, 32);
//	}

	public Notes(MidiOut source, MidiUpdate ping, int polyphony) {
 		this(source, ping, new IntRing(polyphony));
 	}

	/** Allow injecting a pre-sized ring (useful for tests or tuning). */
	private Notes(MidiOut source, MidiUpdate ping, IntRing ring) {
 		this.ping = ping;
 		this.source = source;
 		this.ring = ring;
 		// canonical polyphony
 		this.actives = new int[ring.capacity()];
		// monoStack sized to polyphony + small headroom; avoids heap ops on RT path
		this.monoStack = new int[this.actives.length + 4];
 	}

	public void setMono(boolean mono) {
		this.mono = mono;
		if (mono) { // for live switching, oldest = top of stack, if any
			synchronized (this) {
				// clear primitive mono stack
				monoStackTop = 0;
				if (!voices.isEmpty()) {
					Point oldestVoice = voices.get(0);
					oldest = MidiPack.packAdd(oldestVoice.x, oldestVoice.y);
				} else {
					oldest = 0;
				}
				// collapse RT-side actives to the single mono note (UI initiated change)
				for (int i = 0; i < actives.length; i++)
					actives[i] = 0;
				if (oldest != 0) {
					actives[0] = oldest;
					activesNext = (1) % actives.length;
				} else {
					activesNext = 0;
				}
			}
			dirty = true;
			if (ping != null) ping.updateMidi(source);
		} else {
			// leaving mono: clear stack, force UI rebuild from current actives
			monoStackTop = 0;
			dirty = true;
			if (ping != null) ping.updateMidi(source);
		}
	}

//	/** @return data1 values of actives notes in ascending (piano key) order. UI thread. */
//	@PrismUI public int[] actives() {
//		return getActiveSnapshot();
//	}

	/* RT: remove any active note matching data1 (0..127). Must be lock-free and allocation-free. */
	@Override
	@PrismRT
	public void off(int data1) {
		if (data1 < 0 || data1 > 127) return;

		if (ping != null)
			ping.updateMidi(source);

		// Prefer lock-free queued event
		boolean ok = ring.offerRemove(data1);
		if (ok)
			// when enqueue succeeds;
			return;

		// fallback: clear any matching packed entries
		removeActivePacked(data1);
		dirty = true;
	}

	/* UI helper: update snapshot and arrays when UI triggers an on (allocations allowed on UI). */
	public void on(int data1, int data2) {
		if (data1 < 0 || data1 > 127) return;
		int packed = MidiPack.packAdd(data1, data2);
		// keep packed array consistent for both RT and UI readers
		addActivePacked(packed);

		// update UI stack (synchronized because voices accessed on UI)
		synchronized (this) {
			if (mono) {
				voices.clear();
				voices.add(new Point(data1, data2));
				// do NOT set 'oldest' here; addActivePacked already handled mono stacking/restoration
			} else {
				for (int i = 0; i < voices.size(); i++) {
					Point p = voices.get(i);
					if (p.x == data1) {
						voices.set(i, new Point(data1, data2));
						if (ping != null) ping.updateMidi(source);
						return;
					}
				}
				if (voices.size() < ring.capacity())
					voices.add(new Point(data1, data2));
				else {
					voices.remove(0);
					voices.add(new Point(data1, data2));
				}
			}
		}
		if (ping != null)
			ping.updateMidi(source);
	}

	/* RT: add or re-trigger a note. Accepts a ShortMessage from audio/MIDI threads. */
	@PrismRT @Override
	public void on(ShortMessage msg) {
		if (msg == null) return;
		int d1 = msg.getData1();
		int d2 = msg.getData2();
		if (d1 < 0 || d1 > 127) return;
		if (d2 == 0) { // note-off encoded as velocity 0
			boolean ok = ring.offerNoteOff(d1);
			if (ok) {
				if (ping != null) ping.updateMidi(source);
				return;
			}
			// fallback: remove any matching packed entries
			removeActivePacked(d1);
			dirty = true;
			if (ping != null) ping.updateMidi(source);
			return;
		}
		// try to enqueue ADD event (RT-safe, no allocation)
		boolean ok = ring.offerAdd(d1, d2);
		int packed = MidiPack.packAdd(d1, d2);
		if (ok) {
			// Update RT packed array immediately so isNoteOn(...) reflects the new state without waiting for UI drain.
			addActivePacked(packed);
			// notify UI to drain ring
			if (ping != null) ping.updateMidi(source);
			return;
		}
		// fallback: write directly into RT packed array and mark dirty
		addActivePacked(packed);
		dirty = true;
		if (ping != null) ping.updateMidi(source);
	}

	@Override
	@PrismRT
	public boolean isNoteOn(int data1) {
		if (data1 < 0 || data1 > 127) return false;
		for (int i = 0; i < actives.length; i++) {
			int p = actives[i];
			if (p != 0 && MidiPack.data1(p) == data1)
				return true;
		}
		return false;
	}

//	/* UINotes API: copy active keys into provided set (UI thread). */
//	@Override
//	@PrismUI
//	public void data1(Set<Integer> ref) {
//		if (ref == null) return;
//		synchronized (this) {
//			for (Point p : voices)
//				ref.add(p.x);
//		}
//	}

	/* UI-side: drain ring and rebuild voices from RT arrays if dirty. Call on UI thread when pinged. */
	@Override
	@PrismUI
	public void update() {
		boolean hadEvents = false;
		// Drain ring completely (UI thread only, allocations allowed here)
		for (;;) {
			int packed = ring.pollPack();
			if (packed == 0) break; // EMPTY sentinel
			hadEvents = true;
			int cmd = IntRing.cmdType(packed);
			int d1 = IntRing.data1(packed);
			int d2 = IntRing.data2(packed);
			switch (cmd) {
				case MidiPack.CMD_ADD, MidiPack.CMD_RETRIGGER -> {
					// update packed actives (keeps snapshot consistent for other UI readers)
					if (d1 >= 0 && d1 < 128) {
						addActivePacked(MidiPack.packAdd(d1, d2));
					}
					// update UI voices stack
					synchronized (this) {
						if (mono) {
							voices.clear();
							voices.add(new Point(d1, d2));
							oldest = MidiPack.packAdd(d1, d2);
						} else {
							boolean found = false;
							for (int i = 0; i < voices.size(); i++) {
								Point p = voices.get(i);
								if (p.x == d1) {
									voices.set(i, new Point(d1, d2));
									found = true;
									break;
								}
							}
							if (!found) {
								if (voices.size() < ring.capacity())
									voices.add(new Point(d1, d2));
								else {
									voices.remove(0);
									voices.add(new Point(d1, d2));
								}
							}
						}
					}
				}
				case MidiPack.CMD_NOTE_OFF, MidiPack.CMD_REMOVE -> {
					if (d1 >= 0 && d1 < 128) {
						removeActivePacked(d1);
					}
					synchronized (this) {
						for (int i = voices.size() - 1; i >= 0; i--) {
							if (voices.get(i).x == d1)
								voices.remove(i);
						}
					}
				}
				default -> {
					// unknown command: ignore
				}
			}
		}

		// If no ring events processed but RT flagged dirty (fallback path), rebuild full snapshot
		if (!hadEvents && !dirty && voices.size() > 0)
			return;

		if (!hadEvents && dirty) {
			synchronized (this) {
				voices.clear();
				if (mono && oldest != 0) {
					int key = MidiPack.data1(oldest);
					int vel = MidiPack.data2(oldest);
					voices.add(new Point(key, vel));
				} else {
					for (int i = 0; i < actives.length; i++) {
						int p = actives[i];
						if (p != 0) {
							voices.add(new Point(MidiPack.data1(p), MidiPack.data2(p)));
							if (voices.size() >= ring.capacity()) break;
						}
					}
				}
				dirty = false;
			}
		}
	}

	/* UI may change pedal state; notify listeners. */
	@Override
	@PrismUI
	public void setPedal(boolean pressed) {
		this.pedal = pressed;
		if (ping != null)
			ping.updateMidi(source);
	}

	/* Channel unknown in this helper; return 0 as default. */
	@Override
	public int getChannel() { return 0; }

	/* Not owned here; return null. */
	@Override
	public judahzone.api.MidiOut getMidiOut() { return null; }

	@Override
	public boolean isEmpty() {
		synchronized (this) {
			return voices.isEmpty();
		}
	}

	/* UI snapshot of active keys as int[] (ascending by insertion order). */
	@PrismUI @Override
	public int[] actives() {
		synchronized (this) {
			int n = voices.size();
			int[] out = new int[n];
			for (int i = 0; i < n; i++)
				out[i] = voices.get(i).x;
			return out;
		}
	}

	@Override
	public boolean isPedal() {
		return pedal;
	}

	/* -------------------- Helpers for packed actives -------------------- */

	/** Add/replace an active packed entry. RT-safe: no allocations, bounded loops (polyphony small). */
	private void addActivePacked(int packed) {
		int key = MidiPack.data1(packed);

		// preserve previous active in 'monoStack' and replace with single packed entry
		if (mono) {
			int current = actives.length > 0 ? actives[0] : 0;
			if (current != 0 && MidiPack.data1(current) != key) {
				// push previous active so it can be restored when the new note releases
				if (monoStackTop < monoStack.length) monoStack[monoStackTop++] = current;
				else { // overflow: drop oldest stored entry (shift left) then append
					for (int i = 1; i < monoStackTop; i++) monoStack[i - 1] = monoStack[i];
					monoStack[monoStackTop - 1] = current;
				}
			}
			oldest = packed;
			for (int i = 0; i < actives.length; i++)
				actives[i] = 0;
			actives[0] = packed;
			activesNext = (1) % actives.length;
			dirty = true;
			return;
		}

		// 1) try update existing entry (retrigger)
		for (int i = 0; i < actives.length; i++) {
			int p = actives[i];
			if (p != 0 && MidiPack.data1(p) == key) {
				actives[i] = packed;
				return;
			}
		}
		// 2) find empty slot
		for (int i = 0; i < actives.length; i++) {
			if (actives[i] == 0) {
				actives[i] = packed;
				return;
			}
		}
		// 3) steal oldest (round-robin)
		actives[activesNext] = packed;
		activesNext = (activesNext + 1) % actives.length;
		// mark dirty to force UI rebuild if needed
		dirty = true;
	}

	/** Remove any packed entries matching key. RT-safe. */
	private void removeActivePacked(int key) {
		boolean cleared = false;

		// Mono: if we remove the current active mono note, restore the previous (monoStack) if present.
		if (mono) {
			int current = actives.length > 0 ? actives[0] : 0;
			if (current != 0 && MidiPack.data1(current) == key) {
				// removing the currently sounding note
				for (int i = 0; i < actives.length; i++) actives[i] = 0;
				// restore previous note if available
				if (monoStackTop > 0) {
					int prev = monoStack[--monoStackTop];
					actives[0] = prev;
					oldest = prev;
					activesNext = (1) % actives.length;
				} else {
					oldest = 0;
				}
				cleared = true;
			} else if (monoStackTop > 0) {
				// if removing a stored previous note, drop it from the primitive stack
				for (int i = monoStackTop - 1; i >= 0; i--) {
					int p = monoStack[i];
					if (MidiPack.data1(p) == key) {
						// remove by shifting left to preserve order
						for (int j = i; j < monoStackTop - 1; j++) monoStack[j] = monoStack[j + 1];
						monoStackTop--;
						cleared = true;
						break;
					}
				}
			}
		} else {
			for (int i = 0; i < actives.length; i++) {
				int p = actives[i];
				if (p != 0 && MidiPack.data1(p) == key) {
					actives[i] = 0;
					cleared = true;
				}
			}
		}

		if (cleared) dirty = true;
	}

	/* -------------------- Mapping helpers (non-RT; used by sequencer/arp) -------------------- */

	/** Simple holder to snapshot mappings for callers. UI/non-RT usage only. */
	public static record Mapping(ShortMessage key, List<Integer> values) {}

	/** Record association between an input ShortMessage and the generated output keys. UI thread only. */
	@PrismUI
	public void addMapping(ShortMessage in, List<Integer> outs) {
		if (in == null) return;
		synchronized (this) {
			mappings.put(in, new ArrayList<>(outs != null ? outs : List.of()));
		}
	}

	/** Remove and return stored outputs for input message. UI thread only. */
	@PrismUI
	public List<Integer> removeMapping(ShortMessage in) {
		if (in == null) return null;
		synchronized (this) {
			return mappings.remove(in);
		}
	}

	/** Snapshot mappings for iteration. UI thread only. */
	@PrismUI
	public List<Mapping> mappingsSnapshot() {
		synchronized (this) {
			List<Mapping> out = new ArrayList<>(mappings.size());
			for (Map.Entry<ShortMessage, List<Integer>> e : mappings.entrySet()) {
				out.add(new Mapping(e.getKey(), new ArrayList<>(e.getValue())));
			}
			return out;
		}
	}

	/** Clear all stored mappings. UI thread only. */
	@PrismUI
	public void clearMappings() {
		synchronized (this) {
			mappings.clear();
		}
	}

}
