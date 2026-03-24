package judahzone.prism;

/** SPSC int ring: RT producer (offer) -> UI consumer (poll).
    No allocations, power-of-two capacity. Lock-free, wait-free. */
@Prism
public final class IntRing {
	private static final int EMPTY = 0x00000000; // or use MidiPack's reserved sentinel
	private final int[] buf;
	private final int mask;
	private volatile int head = 0; // consumer index
	private volatile int tail = 0; // producer index
	private final int capacity;

	public IntRing(int capacity) {
		if (capacity <= 0)
			throw new IllegalArgumentException("capacity > 0");
		this.capacity = capacity;
		int cap = 1;
		while (cap < capacity) cap <<= 1;
		buf = new int[cap];
		mask = cap - 1;
	}

	/** RT-side non-blocking offer. @return false if full. */
	@PrismRT
	public boolean offer(int v) {
		int t = tail;
		int next = (t + 1) & mask;
		if (next == head) return false; // full
		buf[t] = v;
		tail = next; // volatile write publishes
		return true;
	}

	/** UI-side non-blocking poll. @return Integer.MIN_VALUE if empty. */
	@PrismUI
	public int poll() {
		int h = head;
		if (h == tail) return Integer.MIN_VALUE;
		int v = buf[h];
		head = (h + 1) & mask;
		return v;
	}

	/** Approximate size (UI-side). */
	@PrismUI
	public int size() {
		int s = tail - head;
		if (s < 0) s += buf.length;
		return s;
	}

	public int capacity() { return capacity; }

	@PrismUI
	public boolean isEmpty() { return head == tail; }

	/* RT-side non-blocking offer. Returns false if full.*/
	@PrismRT public boolean offerPack(int packed) {
		int t = tail;
		int next = (t + 1) & mask;
		if (next == head)
			return false; 	// full
		buf[t] = packed; tail = next; // volatile write publishes
		return true;
	}


	@PrismRT
	public boolean offerRetrigger(int data1, int data2) {
		return offerPack(MidiPack.packRetrigger(data1, data2));
	}

	@PrismRT
	public boolean offerRemove(int data1) {
		return offerPack(MidiPack.packRemove(data1));
	}

	/* RT convenience: pack and offer as ADD.*/
	@PrismRT public boolean offerAdd(int data1, int data2) { return offerPack(MidiPack.packAdd(data1, data2)); }



	/* RT convenience: pack and offer as NOTE_OFF.*/
	@PrismRT public boolean offerNoteOff(int data1) { return offerPack(MidiPack.packNoteOff(data1)); }



	/* UI-side non-blocking poll. Returns EMPTY sentinel if empty.*/
	@PrismUI public int pollPack() { final int h = head; if (h == tail) return EMPTY; final int v = buf[h]; head = (h + 1) & mask; return v; }



	/* UI-side helpers: extract fields from packed value.*/
	@PrismUI public static int cmdType(int packed) { return MidiPack.cmdType(packed); } @PrismUI public static int data1(int packed) { return MidiPack.data1(packed); } @PrismUI public static int data2(int packed) { return MidiPack.data2(packed); }




}
