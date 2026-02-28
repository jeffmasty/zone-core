package judahzone.prism;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**on methods: marks the RT-side of a Thread-Safe interface
 * on types: marks as RT-safe (no heap allocations on audio path (preallocated buffer only) */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.SOURCE)
public @interface PrismRT { }
