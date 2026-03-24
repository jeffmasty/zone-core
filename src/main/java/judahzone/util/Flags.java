package judahzone.util;

/** Common flag operations for fixed-size bit containers (BOOL32/BOOL64/BOOL8). */
public interface Flags {

    /** set flag at index (implementations may restrict valid range). */
    void set(int idx);
    /** clear flag at index. */
    void clear(int idx);
    /** toggle flag at index. */
    void toggle(int idx);
    /** test flag at index. */
    boolean test(int idx);
    /** set flag at index to value. */
    default void setTo(int idx, boolean value) {
        if (value) set(idx); else clear(idx);
    }

    /** Pack a boolean array into a long (up to 64 bits). */
    static long pack(boolean[] b) {
        long v = 0L;
        int n = Math.min(b.length, 64);
        for (int i = 0; i < n; i++)
            if (b[i]) v |= (1L << i);
        return v;
    }

    /** Unpack a long into a boolean[64]. */
    static boolean[] unpack(long v) {
        boolean[] b = new boolean[64];
        for (int i = 0; i < 64; i++)
            b[i] = ((v >>> i) & 1L) != 0L;
        return b;
    }

    public final class BOOL64 implements Flags {
        private long flags;
        public BOOL64() { this(0L); }
        public BOOL64(long initial) { flags = initial; }

        @Override public void set(int idx) { flags |= (1L << idx); }
        @Override public void clear(int idx) { flags &= ~(1L << idx); }
        @Override public void toggle(int idx) { flags ^= (1L << idx); }
        @Override public boolean test(int idx) { return (flags & (1L << idx)) != 0L; }

        @Override public void setTo(int idx, boolean value) {
            if (value) set(idx); else clear(idx);
        }

        public long toLong() { return flags; }
        public void fromLong(long v) { flags = v; }

        public static long pack(boolean[] b) {
            long v = 0L;
            int n = Math.min(b.length, 64);
            for (int i = 0; i < n; i++)
                if (b[i]) v |= (1L << i);
            return v;
        }

        public static boolean[] unpack(long v) {
            boolean[] b = new boolean[64];
            for (int i = 0; i < 64; i++)
                b[i] = ((v >>> i) & 1L) != 0L;
            return b;
        }
    }

    /** Compact 32-flag container backed by a single int. */
    public final class BOOL32 implements Flags {
        private int flags;
        public BOOL32() { this(0); }
        public BOOL32(int initial) { flags = initial; }

        @Override public void set(int idx) { flags |= (1 << idx); }
        @Override public void clear(int idx) { flags &= ~(1 << idx); }
        @Override public void toggle(int idx) { flags ^= (1 << idx); }
        @Override public boolean test(int idx) { return (flags & (1 << idx)) != 0; }

        @Override public void setTo(int idx, boolean value) {
            if (value) set(idx); else clear(idx);
        }

        public int toInt() { return flags; }
        public void fromInt(int v) { flags = v; }

        public static int pack(boolean[] b) {
            int v = 0;
            int n = Math.min(b.length, 32);
            for (int i = 0; i < n; i++)
                if (b[i]) v |= (1 << i);
            return v;
        }

        public static boolean[] unpack(int v) {
            boolean[] b = new boolean[32];
            for (int i = 0; i < 32; i++)
                b[i] = ((v >>> i) & 1) != 0;
            return b;
        }
    }

    /** Compact 8-flag container backed by a single byte. */
    public final class BOOL8 implements Flags {
        private byte flags;

        public BOOL8() { this((byte)0); }
        public BOOL8(byte initial) { flags = initial; }

        @Override public void set(int idx) {
            int tmp = flags & 0xFF;
            tmp |= (1 << idx);
            flags = (byte) tmp;
        }
        @Override public void clear(int idx) {
            int tmp = flags & 0xFF;
            tmp &= ~(1 << idx);
            flags = (byte) tmp;
        }
        @Override public void toggle(int idx) {
            int tmp = flags & 0xFF;
            tmp ^= (1 << idx);
            flags = (byte) tmp;
        }
        @Override public boolean test(int idx) {
            return (((flags & 0xFF) & (1 << idx)) != 0);
        }

        @Override public void setTo(int idx, boolean value) {
            if (value) set(idx); else clear(idx);
        }

        public byte toByte() { return flags; }
        public void fromByte(byte b) { flags = b; }

        public static byte pack(boolean[] b) {
            int v = 0;
            int n = Math.min(b.length, 8);
            for (int i = 0; i < n; i++)
                if (b[i]) v |= (1 << i);
            return (byte) v;
        }

        public static boolean[] unpack(byte v) {
            boolean[] b = new boolean[8];
            int iv = v & 0xFF;
            for (int i = 0; i < 8; i++)
                b[i] = ((iv >>> i) & 1) != 0;
            return b;
        }
		public int count() {
			int c = 0;
			for (int i = 0; i < 8; i++)
				if (test(i)) c++;
			return c;
		}
    }

}
