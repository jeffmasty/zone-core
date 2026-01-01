package judahzone.api;

import judahzone.util.Recording;

/**Participates in real time audio processing, can respond to some commands*/
public interface PlayAudio {

	public enum Type {
		/** play once */ ONE_SHOT,
		/** play on repeat */ LOOP
		}

	void setRecording(Recording r);

	void play(boolean onOrOff);

	boolean isPlaying();

	/** jack frame count */
	int getLength();

	float seconds();

	void rewind();

}
