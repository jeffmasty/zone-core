package judahzone.api;

public interface Live {

	public static record LiveData(Live processor, float[] left, float[] right) {}

	void analyze(float[] left, float[] right);

}
