package uk.gov.moj.cpp.courtscheduler.envelope;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker on controller methods that should NOT have the {@code _metadata} envelope
 * applied to their response body. The legacy validate-* endpoints return literal
 * {@code {}} for successful validation; envelope wrapping breaks the strict-equality
 * assertions the legacy IT classes use.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SkipEnvelope {
}
