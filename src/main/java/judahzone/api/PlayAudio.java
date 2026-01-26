// PlayAudio.java
package judahzone.api;

/**Participates in real time audio processing, can respond to some commands*/
public interface PlayAudio {

	public enum Type {
		/** play once */ ONE_SHOT,
		/** play on repeat */ LOOP
		}

	void setRecording(Asset a);

	void play(boolean onOrOff);

	boolean isPlaying();

	/** jack frame count */
	int getLength();

	float seconds();

	void rewind();

	void setEnv(float env);

	void setType(Type type);

	/** move playback to the closest frame for the given sample number  */
	void setSample(long sampleFrame);

	void setPlayed(Played p);
}
