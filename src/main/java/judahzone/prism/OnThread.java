package judahzone.prism;

import java.util.function.Supplier;

/** Minimal lock-free ring queue. Single UI Producer -> Single RT Consumer.
    Pre-fill factory, Power-of-two based. */
@Prism
public final class OnThread<T> {
    private final Object[] buf;
    private final int mask;
    private volatile int head = 0; // consumer index (read)
    private volatile int tail = 0; // producer index (write)

    public OnThread(int capacity) { this(capacity, null); }

    public OnThread(int capacity, Supplier<T> prefill) {
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

    /** Non-blocking offer. Returns false if full. Called from the UI producer. */
    @PrismUI
    public boolean offer(T e) {
        final int t = tail;
        final int next = (t + 1) & mask;
        if (next == head) return false; // full
        buf[t] = e;
        // volatile write to tail publishes the element
        tail = next;
        return true;
    }

    /** Non-blocking poll. Returns null if empty. Called from the RT consumer. */
    @SuppressWarnings("unchecked")
    @PrismRT
    public T poll() {
        final int h = head;
        if (h == tail) return null; // empty
        final T e = (T) buf[h];
        // clear slot to avoid holding references (helps GC)
        buf[h] = null;
        head = (h + 1) & mask;
        return e;
    }

    /** Peek newest (not advancing). Returns null if empty. RT-side safe. */
    @SuppressWarnings("unchecked")
    @PrismRT
    public T peekNewest() {
        int t = tail;
        if (t == head) return null;
        int idx = (t - 1) & mask;
        return (T) buf[idx];
    }

    /** Capacity (power-of-two actual). UI-side. */
    @PrismUI
    public int capacity() { return buf.length; }

    /** Approximate size (non-atomic snapshot). UI-side. */
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