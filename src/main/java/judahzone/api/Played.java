package judahzone.api;

public interface Played {
	void setHead(long sample);

	/** update play/pause button based on low-level player state */
	void playState();
}
