package uk.gov.moj.cpp.courtscheduler.api;

import org.junit.jupiter.api.Disabled;

/**
 * <strong>Disabled — pending rewrite onto the Spring Boot {@link JudiciaryAvailabilityApi}.</strong>
 *
 * <p>The original 978-line test (preserved in git history) was driven by Justice
 * Services framework primitives — {@code Enveloper}, {@code Requester},
 * {@code JsonEnvelope}, the {@code DefaultJsonEnvelopeProvider} — and asserted
 * envelope-shaped responses from a CDI controller method like
 * {@code addJudiciaryAvailabilityRule(JsonEnvelope, Requester) → JsonEnvelope}.</p>
 *
 * <p>The Spring Boot port (see {@link JudiciaryAvailabilityApi}) replaces every
 * one of those signatures: methods now take {@code Map<String,Object>} bodies and
 * return {@code ResponseEntity}. Every {@code @Test} method's setup ({@code Enveloper}
 * mocks), action ({@code api.foo(envelope, requester)}) and assertion (envelope
 * payload shape) needs to be re-expressed against the new API. That's individual
 * per-method work, not a mechanical migration.</p>
 *
 * <p>The bulk of the behaviour is re-asserted by the integration-test module's
 * {@code JudiciaryAvailabilityIT} and {@code JudiciaryAvailabilityIntegrationTest},
 * so this is left as a placeholder rather than deleted to keep the migration
 * debt visible. See {@link RotaFileProcessorApiTest} for the rewrite pattern
 * once you decide to invest the time.</p>
 */
@Disabled("Awaiting per-test rewrite onto Spring controller signatures — see Javadoc and RotaFileProcessorApiTest as the template.")
class JudiciaryAvailabilityApiTest {
}
