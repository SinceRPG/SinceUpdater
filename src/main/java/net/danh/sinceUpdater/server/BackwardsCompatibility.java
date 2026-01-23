package net.danh.sinceUpdater.server;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;

@Documented
@Retention(RetentionPolicy.SOURCE)
@Target({METHOD, FIELD, CONSTRUCTOR, TYPE})
public @interface BackwardsCompatibility {

    /**
     * Version at which this field was implemented. This corresponds to
     * the first release version that would break if the annotated feature
     * were not implemented.
     */
    String version();
}