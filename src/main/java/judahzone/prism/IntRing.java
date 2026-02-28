// IntRing.java
package judahzone.prism;

/** SPSC int ring: RT producer (offer) -> UI consumer (poll). No allocations, power-of-two. */
@Prism
public final class IntRing {
    private final int[] buf;
    private final int mask;
    private volatile int head = 0; // consumer index
    private volatile int tail = 0; // producer index

    public IntRing(int capacity) {

        if (capacity <= 0) throw new IllegalArgumentException("capacity>0");
        int cap = 1;
        while (cap < capacity) cap <<= 1;
        buf = new int[cap];
        mask = cap - 1;
    }

    /** RT-side non-blocking offer. Returns false if full. */
    @PrismRT
    public boolean offer(int v) {
        final int t = tail;
        final int next = (t + 1) & mask;
        if (next == head) return false; // full
        buf[t] = v;               // plain write to slot
        tail = next;              // volatile write publishes
        return true;
    }

    /** UI-side non-blocking poll. Returns Integer.MIN_VALUE if empty. */
    @PrismUI
    public int poll() {
        final int h = head;
        if (h == tail) return Integer.MIN_VALUE; // empty sentinel
        final int v = buf[h];
        head = (h + 1) & mask;
        return v;
    }

    /** Approximate size (UI-side). */
    @PrismUI
    public int size() {
        int t = tail;
        int h = head;
        int s = t - h;
        if (s < 0) s += buf.length;
        return s;
    }

    @PrismUI
    public boolean isEmpty() { return head == tail; }
}
