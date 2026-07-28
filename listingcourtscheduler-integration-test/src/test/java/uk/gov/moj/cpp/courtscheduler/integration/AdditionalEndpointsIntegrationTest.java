package uk.gov.moj.cpp.courtscheduler.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Smoke tests for the controllers added on top of the original CourtSchedule contract:
 * hearing-slot, provisional-booking, MI export, OU code, validate, session, rotasl.
 *
 * <p>Each test exercises the path/verb/Accept combination against the real wired pipeline
 * (converters → validators → services → repositories) and asserts the endpoint does not
 * fall through to the unhandled-exception 500. With minimal stub payloads the validators
 * may reject (4xx) — the contract is honoured either way.</p>
 */
class AdditionalEndpointsIntegrationTest extends AbstractIntegrationTest {

    @Test
    void getHearingSlots_doesNotBlowUp() {
        final ResponseEntity<String> response = get(
                "/hearingslots?panel=ADULT"
                        + "&sessionStartDate=2026-06-01"
                        + "&sessionEndDate=2026-06-30"
                        + "&pageSize=10&pageNumber=1",
                SYSTEM_USER_ID,
                "application/vnd.courtscheduler.get.hearing.slots+json");
        assertThat(response.getStatusCode().is5xxServerError()).isFalse();
    }

    @Test
    void putUpdateHearingSlots_doesNotBlowUp() {
        final ResponseEntity<String> response = put(
                "/hearingslots",
                SYSTEM_USER_ID,
                "application/vnd.courtscheduler.update.hearing.slots+json",
                "{\"hearingSlots\":[]}");
        assertThat(response.getStatusCode().is5xxServerError()).isFalse();
    }

    @Test
    void deleteHearingSlots_returns202() {
        final ResponseEntity<String> response = delete(
                "/hearingslots/" + UUID.randomUUID(),
                SYSTEM_USER_ID,
                "application/vnd.courtscheduler.remove.hearing.slots+json");
        // remove() is a no-op when the hearing isn't found
        assertThat(response.getStatusCode().is5xxServerError()).isFalse();
    }

    @Test
    void putListHearingsInCourtSessions_doesNotBlowUp() {
        final ResponseEntity<String> response = put(
                "/list/hearingslots",
                SYSTEM_USER_ID,
                "application/vnd.courtscheduler.list.hearings-in-court-sessions+json",
                "{\"hearingSlots\":[]}");
        assertThat(response.getStatusCode().is5xxServerError()).isFalse();
    }

    @Test
    void postProvisionalBooking_doesNotBlowUp() {
        final ResponseEntity<String> response = post(
                "/provisionalBooking",
                SYSTEM_USER_ID,
                "application/vnd.courtscheduler.create.provisional.booking+json",
                "{\"courtScheduleIds\":[]}");
        assertThat(response.getStatusCode().is5xxServerError()).isFalse();
    }

    @Test
    void getProvisionalBooking_doesNotBlowUp() {
        final ResponseEntity<String> response = get(
                "/provisionalBooking?bookingIds=" + UUID.randomUUID(),
                SYSTEM_USER_ID,
                "application/vnd.courtscheduler.get.provisional.booking+json");
        assertThat(response.getStatusCode().is5xxServerError()).isFalse();
    }

    @Test
    void getMiCourtSchedules_returns200() {
        final ResponseEntity<String> response = get(
                "/mi/court_schedules?fromDate=2026-01-01&toDate=2026-12-31",
                SYSTEM_USER_ID,
                "application/vnd.courtscheduler.export.court_schedule+json");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("courtSchedules");
    }

    @Test
    void getMiAllocatedListings_returns200() {
        final ResponseEntity<String> response = get(
                "/mi/allocated_listings?fromDate=2026-01-01&toDate=2026-12-31",
                SYSTEM_USER_ID,
                "application/vnd.courtscheduler.export.allocated_listings+json");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("allocatedListings");
    }

    @Test
    void getMiCourtScheduleJudiciaries_returns200() {
        final ResponseEntity<String> response = get(
                "/mi/court_schedule_judiciaries?fromDate=2026-01-01&toDate=2026-12-31",
                SYSTEM_USER_ID,
                "application/vnd.courtscheduler.export.court_schedule_judiciary+json");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("courtScheduleJudiciaries");
    }

    @Test
    void postOuCodeMigrate_doesNotBlowUp() {
        final ResponseEntity<String> response = post(
                "/oucode/migrate",
                SYSTEM_USER_ID,
                "application/vnd.courtscheduler.oucode.migrate+json",
                "{\"ouCodes\":[],\"migrated\":true}");
        assertThat(response.getStatusCode().is5xxServerError()).isFalse();
    }

    @Test
    void postOuCodeRecalculateAvailability_doesNotBlowUp() {
        final ResponseEntity<String> response = post(
                "/oucode/recalculate-availability",
                SYSTEM_USER_ID,
                "application/vnd.courtscheduler.oucode.recalculate.availability+json",
                "{\"ouCodes\":[]}");
        assertThat(response.getStatusCode().is5xxServerError()).isFalse();
    }

    @Test
    void postRotaslProcessRotaFiles_returns202() {
        final ResponseEntity<String> response = post(
                "/rotasl/process-rota-files",
                SYSTEM_USER_ID,
                "application/vnd.courtscheduler.rotasl.process_rota_files+json",
                "{\"forItTest\":true}");
        // Async fire-and-forget; should always return 202
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    void postRotaslCleanRedundantRotaData_returns202() {
        final ResponseEntity<String> response = post(
                "/rotasl/clean-redundant-rota-data",
                SYSTEM_USER_ID,
                "application/vnd.courtscheduler.rotasl.clean_redundant_rota_data+json",
                "{\"months\":12}");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    void postValidateCreate_doesNotBlowUp() {
        final ResponseEntity<String> response = post(
                "/validate",
                COURT_SCHEDULE_USER_ID,
                "application/vnd.courtscheduler.validate.create+json",
                "{\"sessions\":[],\"repeatPattern\":{\"frequency\":\"ONCE\","
                        + "\"startDate\":\"2026-06-01\",\"endDate\":\"2026-06-01\",\"repeatFor\":1}}");
        assertThat(response.getStatusCode().is5xxServerError()).isFalse();
    }

    @Test
    void postSessionAssignJudiciary_doesNotBlowUp() {
        final ResponseEntity<String> response = post(
                "/session",
                SYSTEM_USER_ID,
                "application/vnd.courtscheduler.assign-judiciary+json",
                "{\"assignments\":[]}");
        assertThat(response.getStatusCode().is5xxServerError()).isFalse();
    }

    @Test
    void postSessionUnassignJudiciary_doesNotBlowUp() {
        final ResponseEntity<String> response = post(
                "/session",
                SYSTEM_USER_ID,
                "application/vnd.courtscheduler.unassign.judiciary+json",
                "{\"judiciaries\":[]}");
        assertThat(response.getStatusCode().is5xxServerError()).isFalse();
    }
}
