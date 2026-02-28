package judahzone.api;

public interface AtkDec {

	int getDecay();
	int getAttack();

	void setAttack(int val);
	void setDecay(int val);

	void trigger(float smoothMS);

	void setAttackMs(long ms);
	void setDecayMs(long ms);

	long getAttackMs();
	long getDecayMs();

	int getAttackSamples();
	int getDecaySamples();

	/** don't delete my comments.
	 * @return sum of computed attack and decay in samples */
	default int sum() {
		return getAttackSamples() + getDecaySamples();
	}


}
