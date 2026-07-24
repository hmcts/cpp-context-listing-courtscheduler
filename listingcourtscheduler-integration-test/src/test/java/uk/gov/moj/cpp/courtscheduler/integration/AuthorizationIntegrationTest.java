package uk.gov.moj.cpp.courtscheduler.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Exercises {@code cp-auth-rules-filter:2.0.0} end-to-end:
 * <ul>
 *   <li>action name is resolved from the vendor media type,</li>
 *   <li>identity URL is hit via WireMock,</li>
 *   <li>Drools rules in {@code uk.gov.moj.cpp.courtscheduler.api.accesscontrol.drl/courtscheduler-api.drl} grant or deny access
 *       based on the user's permissions/groups.</li>
 * </ul>
 */
class AuthorizationIntegrationTest extends AbstractIntegrationTest {

    private static final String GET_COURT_SCHEDULE_QUERY =
            "/courtschedule?courtCentreId=" + UUID.randomUUID()
                    + "&sessionStartDate=2026-01-01"
                    + "&sessionEndDate=2026-01-31"
                    + "&pageSize=10&pageNumber=1";

    @Test
    void courtScheduleUser_isAllowedToReadCourtSchedules() {
        final ResponseEntity<String> response = get(
                GET_COURT_SCHEDULE_QUERY,
                COURT_SCHEDULE_USER_ID,
                "application/vnd.courtscheduler.get+json");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void courtScheduleUser_isAllowedToCreateCourtSchedule() {
        final ResponseEntity<String> response = post(
                "/courtschedule",
                COURT_SCHEDULE_USER_ID,
                "application/vnd.courtscheduler.create+json",
                "{\"sessionList\":[],\"repeatPattern\":{\"frequency\":\"ONCE\",\"startDate\":\"2026-06-01\",\"endDate\":\"2026-06-01\"}}");

        // Allowed user passes the auth filter; downstream may still validate the
        // payload — accept any non-403 response (i.e. authorisation didn't block it).
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void deniedUser_cannotReadCourtSchedules() {
        final ResponseEntity<String> response = get(
                GET_COURT_SCHEDULE_QUERY,
                DENIED_USER_ID,
                "application/vnd.courtscheduler.get+json");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void deniedUser_cannotCreateCourtSchedule() {
        final ResponseEntity<String> response = post(
                "/courtschedule",
                DENIED_USER_ID,
                "application/vnd.courtscheduler.create+json",
                "{\"sessionList\":[]}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void systemUser_isAllowedToSearchCourtSchedulesById() {
        // GET /sessions (search.court-schedules-by-id) is granted by the CourtSchedule
        // permission (SPRDT-757 relaxed it from SYSTEM_USERS-only); the system user stub
        // carries that permission.
        final ResponseEntity<String> response = get(
                "/sessions?ids=" + UUID.randomUUID(),
                SYSTEM_USER_ID,
                "application/vnd.courtscheduler.search.court-schedules-by-id+json");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void courtScheduleUser_isAllowedToSearchCourtSchedulesById() {
        // SPRDT-757: rule is hasPermission(getCourtSchedulePermission()), no longer
        // SYSTEM_USERS-only — the court schedule user now passes.
        final ResponseEntity<String> response = get(
                "/sessions?ids=" + UUID.randomUUID(),
                COURT_SCHEDULE_USER_ID,
                "application/vnd.courtscheduler.search.court-schedules-by-id+json");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void deniedUser_cannotSearchCourtSchedulesById() {
        final ResponseEntity<String> response = get(
                "/sessions?ids=" + UUID.randomUUID(),
                DENIED_USER_ID,
                "application/vnd.courtscheduler.search.court-schedules-by-id+json");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
