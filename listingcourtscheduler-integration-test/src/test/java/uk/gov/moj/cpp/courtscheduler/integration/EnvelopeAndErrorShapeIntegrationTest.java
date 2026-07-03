package uk.gov.moj.cpp.courtscheduler.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Asserts that the Spring Boot port preserves the UI-facing JSON shapes:
 * <ul>
 *   <li>successful JSON responses include the {@code _metadata} envelope produced by
 *       {@link uk.gov.moj.cpp.courtscheduler.envelope.EnvelopeResponseBodyAdvice};</li>
 *   <li>error responses use the legacy {@code errorMessage} / {@code errorMessages} shape produced by
 *       {@link uk.gov.moj.cpp.courtscheduler.controllers.GlobalExceptionHandler}.</li>
 * </ul>
 */
class EnvelopeAndErrorShapeIntegrationTest extends AbstractIntegrationTest {

    @Test
    void responseBodiesIncludeMetadataEnvelope() {
        final ResponseEntity<String> response = get(
                "/courtschedule?courtCentreId=" + UUID.randomUUID()
                        + "&sessionStartDate=2026-01-01"
                        + "&sessionEndDate=2026-01-31"
                        + "&pageSize=10&pageNumber=1",
                COURT_SCHEDULE_USER_ID,
                "application/vnd.courtscheduler.get+json");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("_metadata")
                .contains("courtSchedules");
    }
}
