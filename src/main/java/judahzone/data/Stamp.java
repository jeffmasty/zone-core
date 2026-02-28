package judahzone.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// TODO convert to record
/** percentage-based envelope data */
@Data @NoArgsConstructor @AllArgsConstructor
public class Stamp {

	public int    attack 	= 10;
	public int    decay 	= 90;
	public int    sustain 	= 90;
	public int    release 	= 0;

	public Stamp(Stamp copy) {
		set(copy);
	}

	public void set(Stamp env) {
		attack = env.attack;
		decay = env.decay;
		release = env.release;
		sustain = env.sustain;
	}

}
