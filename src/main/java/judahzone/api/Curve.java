package judahzone.api;

/** Decay curve */
public interface Curve {

	/**
	 * @param progress progress through decay stage, 0.0 (start) to 1.0 (end).
	 *
	 * @return samples from decaying curve.
	 */
	float apply(float progress);

	public static final Curve LINEAR = (inverse) ->
	 	1.0f - inverse;
	public static final Curve EXPONENTIAL = (inverse) ->
	 	(float) Math.exp(-3.0f /*pole*/ * inverse); // (slower) -2.5f <--pole--> -4.0f (faster)

	public static final Curve SUS = (inverse) -> 1.0f; // sustain level is flat until release

}
