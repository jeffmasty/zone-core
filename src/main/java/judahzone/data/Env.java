package judahzone.data;

public record Env(int attack, int decay, int release) {
	public Env(int attack, int decay) {
		this(attack, decay, 0);
	}
}
