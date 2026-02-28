package judahzone.prism;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks units as real-time safe that also supply a thread-safety strategy to EDT */
@Documented
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.SOURCE)
@Inherited
public @interface Prism {
	/** semantic tags, e.g. "wait-free", "filter" */ String[] tags() default {};
}
