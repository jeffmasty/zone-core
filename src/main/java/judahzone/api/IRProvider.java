package judahzone.api;

public interface IRProvider {

    public static record IR(String name, float[] irFreq) {
        @Override public String toString() { return name; }
    }

	int size();

	IR get(int i);

}
