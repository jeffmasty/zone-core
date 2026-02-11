package judahzone.prism;

import java.util.function.Supplier;

// OffThread.offer()...

/** Minimal lock-free ring queue. Single RT Producer -> Single non-RT Consumer. Pre-fill factory, Power-of-two based. */
public final class OffThread<T> {
    private final Object[] buf;
    private final int mask;
    private volatile int head = 0; // consumer index (read)
    private volatile int tail = 0; // producer index (write)

    public OffThread(int capacity) {
        this(capacity, null);
    }

    public OffThread(int capacity, Supplier<T> prefill) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity>0");
        int cap = 1;
        while (cap < capacity) cap <<= 1; // round up to power-of-two
        buf = new Object[cap];
        mask = cap - 1;
        if (prefill != null) {
            // fill only buf.length - 1 slots because this ring uses a one-slot-empty invariant
            for (int i = 0; i < buf.length - 1; i++) buf[i] = prefill.get();
            // set head and tail so next==head -> full (consumer will drain)
            head = 0;
            tail = (head + buf.length - 1) & mask; // tail = mask when head==0
        }
    }

    /** Capacity (power-of-two actual). */
    public int capacity() { return buf.length; }

    /** Non-blocking offer. Returns false if full. */
    public boolean offer(T e) {
        final int t = tail;
        final int next = (t + 1) & mask;
        if (next == head) return false; // full
        buf[t] = e;
        // volatile write to tail publishes the element
        tail = next;
        return true;
    }

    /** Non-blocking poll. Returns null if empty. */
    @SuppressWarnings("unchecked")
    public T poll() {
        final int h = head;
        if (h == tail) return null; // empty
        final T e = (T) buf[h];
        buf[h] = null; // help GC / reuse
        head = (h + 1) & mask;
        return e;
    }

    /** Peek newest (not advancing). Returns null if empty. */
    @SuppressWarnings("unchecked")
    public T peekNewest() {
        int t = tail;
        if (t == head) return null;
        int idx = (t - 1) & mask;
        return (T) buf[idx];
    }

    /** Approximate size (non-atomic snapshot). */
    public int size() {
        int t = tail;
        int h = head;
        int s = t - h;
        if (s < 0) s += buf.length;
        return s;
    }

    public boolean isEmpty() { return head == tail; }
}