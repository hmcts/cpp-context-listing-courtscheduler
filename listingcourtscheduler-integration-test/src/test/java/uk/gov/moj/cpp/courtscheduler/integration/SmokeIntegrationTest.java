package uk.gov.moj.cpp.courtscheduler.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * Confirms the dockerised app booted with Liquibase, authz and audit filters enabled,
 * and that actuator/health is reachable on the configured context path.
 */
class SmokeIntegrationTest extends AbstractIntegrationTest {

    @Test
    void actuatorHealthIsUp() {
        // /actuator is excluded from the authz filter so no headers required
        final ResponseEntity<String> response = new RestTemplate()
                .getForEntity(BASE_URL + "/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void noAuthHeaderReturns401() {
        // Without CJSCPPUID, the authz filter must reject with 401
        final ResponseEntity<String> response = get(
                "/courtschedule?courtCentreId=" + java.util.UUID.randomUUID()
                        + "&sessionStartDate=2026-01-01"
                        + "&sessionEndDate=2026-01-31"
                        + "&pageSize=10&pageNumber=1",
                null,
                "application/vnd.courtscheduler.get+json");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
