package judahzone.api;

public record Hz(float freq) {

	public Hz{
		freq = Math.max(Frequency.MIN, Math.min(Frequency.MAX, freq));
	}

}
