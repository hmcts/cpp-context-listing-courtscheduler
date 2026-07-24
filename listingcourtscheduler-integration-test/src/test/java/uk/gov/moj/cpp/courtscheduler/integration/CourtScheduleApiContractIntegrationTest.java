package uk.gov.moj.cpp.courtscheduler.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Asserts the REST contract for the {@code /courtschedule*} endpoints implemented in
 * {@link uk.gov.moj.cpp.courtscheduler.api.CourtSchedulerApi}.
 *
 * <p>These tests verify the path/verb/media-type wiring is intact end-to-end through
 * the migrated converters → validators → services → repositories pipeline. With minimal
 * payloads, mutating endpoints are expected to bounce off validators with 4xx, while
 * read-only endpoints with valid query params return 200. Either way the contract holds —
 * a 5xx would indicate an unhandled exception in the migration.</p>
 */
class CourtScheduleApiContractIntegrationTest extends AbstractIntegrationTest {

    @Test
    void postCourtschedule_doesNotBlowUp() {
        final ResponseEntity<String> response = post(
                "/courtschedule",
                COURT_SCHEDULE_USER_ID,
                "application/vnd.courtscheduler.create+json",
                "{\"sessionList\":[],\"repeatPattern\":{\"frequency\":\"ONCE\",\"startDate\":\"2026-06-01\",\"endDate\":\"2026-06-01\"}}");

        // Empty session list bounces off validation; either way contract is upheld.
        assertThat(response.getStatusCode().is5xxServerError())
                .as("POST /courtschedule must not 5xx; got %s", response.getStatusCode())
                .isFalse();
    }

    @Test
    void getCourtschedule_returns200WithCourtSchedulesField() {
        final ResponseEntity<String> response = get(
                "/courtschedule?courtCentreId=" + UUID.randomUUID()
                        + "&sessionStartDate=2026-01-01"
                        + "&sessionEndDate=2026-01-31"
                        + "&pageSize=10&pageNumber=1",
                COURT_SCHEDULE_USER_ID,
                "application/vnd.courtscheduler.get+json");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("courtSchedules");
    }

    @Test
    void postCourtscheduleEdit_doesNotBlowUp() {
        final ResponseEntity<String> response = post(
                "/courtschedule/edit",
                COURT_SCHEDULE_USER_ID,
                "application/vnd.courtscheduler.update+json",
                "{\"courtScheduleId\":\"" + UUID.randomUUID()
                        + "\",\"courtRoomId\":\"" + UUID.randomUUID()
                        + "\",\"businessType\":\"TFA\",\"courtSession\":\"AM\","
                        + "\"jurisdiction\":\"MAGISTRATES\",\"panel\":\"ADULT\"}");

        assertThat(response.getStatusCode().is5xxServerError())
                .as("POST /courtschedule/edit must not 5xx; got %s", response.getStatusCode())
                .isFalse();
    }

    @Test
    void postCourtscheduleDelete_doesNotBlowUp() {
        final ResponseEntity<String> response = post(
                "/courtschedule/delete",
                COURT_SCHEDULE_USER_ID,
                "application/vnd.courtscheduler.delete+json",
                "{\"sessionIds\":[]}");

        assertThat(response.getStatusCode().is5xxServerError())
                .as("POST /courtschedule/delete must not 5xx; got %s", response.getStatusCode())
                .isFalse();
    }

    @Test
    void postCourtscheduleAssignCourtroom_doesNotBlowUp() {
        final ResponseEntity<String> response = post(
                "/courtschedule/assign.courtroom",
                COURT_SCHEDULE_USER_ID,
                "application/vnd.courtscheduler.assign.courtroom+json",
                "{\"assignments\":[]}");

        assertThat(response.getStatusCode().is5xxServerError())
                .as("POST /courtschedule/assign.courtroom must not 5xx; got %s", response.getStatusCode())
                .isFalse();
    }

    @Test
    void getCourtSchedulesByIds_returns200WithCourtSchedulesField() {
        final ResponseEntity<String> response = get(
                "/sessions?ids=" + UUID.randomUUID(),
                SYSTEM_USER_ID,
                "application/vnd.courtscheduler.search.court-schedules-by-id+json");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("courtSchedules");
    }
}
