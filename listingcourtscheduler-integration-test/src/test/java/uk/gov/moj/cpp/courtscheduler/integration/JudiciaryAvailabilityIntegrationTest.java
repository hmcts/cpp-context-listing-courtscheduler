package uk.gov.moj.cpp.courtscheduler.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Tests the {@code /judiciaries/availability*} endpoints.
 *
 * <p>These endpoints negotiate as plain {@code application/json}, so the auth filter
 * cannot extract an action name from the media type. Authorization is expressed in
 * {@code uk.gov.moj.cpp.courtscheduler.api.accesscontrol.drl/courtscheduler-api.drl} by matching on the request method and path
 * attributes that {@code HttpAuthzFilter} populates on every {@code Action}. These
 * tests verify that:</p>
 * <ul>
 *   <li>requests without {@code CJSCPPUID} are rejected (401),</li>
 *   <li>users without permissions are rejected (403),</li>
 *   <li>users with the correct group/permissions reach the controller (no 5xx),</li>
 *   <li>each endpoint exists and respects its method + path contract.</li>
 * </ul>
 */
class JudiciaryAvailabilityIntegrationTest extends AbstractIntegrationTest {

    private static final String QUERY = "?startDate=2026-01-01&endDate=2026-12-31";

    // ----- Auth tests for the special path-based action resolution -----

    @Test
    void noAuthHeaderReturns401_onJudiciaryFind() {
        final ResponseEntity<String> response = get(
                "/judiciaries/availability-rules" + QUERY,
                null,
                "application/json");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void deniedUser_cannotFindRules() {
        final ResponseEntity<String> response = get(
                "/judiciaries/availability-rules" + QUERY,
                DENIED_USER_ID,
                "application/json");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void courtScheduleUser_canFindRules() {
        // Drools rule: courtscheduler.judiciary.find.availability.rule needs getCourtSchedulePermission
        final ResponseEntity<String> response = get(
                "/judiciaries/availability-rules" + QUERY,
                COURT_SCHEDULE_USER_ID,
                "application/json");
        assertThat(response.getStatusCode().is5xxServerError()).isFalse();
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void deniedUser_cannotAddRule() {
        final ResponseEntity<String> response = post(
                "/judiciaries/availability-rules/add",
                DENIED_USER_ID,
                "application/json",
                "{}");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void courtScheduleUser_canAddRule() {
        // Drools rule: courtscheduler.judiciary.add.availability.rule needs createCourtSchedulePermission
        final ResponseEntity<String> response = post(
                "/judiciaries/availability-rules/add",
                COURT_SCHEDULE_USER_ID,
                "application/json",
                "{}");
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ----- Contract tests for each endpoint -----

    @Test
    void findJudiciaryAvailabilityRules_doesNotBlowUp() {
        final ResponseEntity<String> response = get(
                "/judiciaries/availability-rules" + QUERY,
                COURT_SCHEDULE_USER_ID,
                "application/json");
        assertThat(response.getStatusCode().is5xxServerError()).isFalse();
    }

    @Test
    void findJudiciaryAvailability_doesNotBlowUp() {
        final ResponseEntity<String> response = get(
                "/judiciaries/availability" + QUERY,
                COURT_SCHEDULE_USER_ID,
                "application/json");
        assertThat(response.getStatusCode().is5xxServerError()).isFalse();
    }

    @Test
    void getJudiciaryAvailabilityRule_doesNotBlowUp() {
        final ResponseEntity<String> response = get(
                "/judiciaries/availability-rules/" + UUID.randomUUID(),
                COURT_SCHEDULE_USER_ID,
                "application/json");
        assertThat(response.getStatusCode().is5xxServerError()).isFalse();
    }

    @Test
    void addJudiciaryAvailabilityRule_doesNotBlowUp() {
        final ResponseEntity<String> response = post(
                "/judiciaries/availability-rules/add",
                COURT_SCHEDULE_USER_ID,
                "application/json",
                "{}");
        assertThat(response.getStatusCode().is5xxServerError()).isFalse();
    }

    @Test
    void updateJudiciaryAvailabilityRule_doesNotBlowUp() {
        final ResponseEntity<String> response = post(
                "/judiciaries/availability-rules/update",
                COURT_SCHEDULE_USER_ID,
                "application/json",
                "{}");
        assertThat(response.getStatusCode().is5xxServerError()).isFalse();
    }

    @Test
    void deleteJudiciaryAvailabilityRule_doesNotBlowUp() {
        final ResponseEntity<String> response = post(
                "/judiciaries/availability-rules/delete",
                COURT_SCHEDULE_USER_ID,
                "application/json",
                "{\"ruleId\":\"" + UUID.randomUUID() + "\"}");
        assertThat(response.getStatusCode().is5xxServerError()).isFalse();
    }

    @Test
    void validateAddJudiciaryAvailabilityRule_doesNotBlowUp() {
        final ResponseEntity<String> response = post(
                "/judiciaries/availability-rules/validate-add",
                COURT_SCHEDULE_USER_ID,
                "application/json",
                "{}");
        assertThat(response.getStatusCode().is5xxServerError()).isFalse();
    }

    @Test
    void validateUpdateJudiciaryAvailabilityRule_doesNotBlowUp() {
        final ResponseEntity<String> response = post(
                "/judiciaries/availability-rules/validate-update",
                COURT_SCHEDULE_USER_ID,
                "application/json",
                "{}");
        assertThat(response.getStatusCode().is5xxServerError()).isFalse();
    }

    @Test
    void validateDeleteJudiciaryAvailabilityRule_doesNotBlowUp() {
        final ResponseEntity<String> response = post(
                "/judiciaries/availability-rules/validate-delete",
                COURT_SCHEDULE_USER_ID,
                "application/json",
                "{\"ruleId\":\"" + UUID.randomUUID() + "\"}");
        assertThat(response.getStatusCode().is5xxServerError()).isFalse();
    }
}
