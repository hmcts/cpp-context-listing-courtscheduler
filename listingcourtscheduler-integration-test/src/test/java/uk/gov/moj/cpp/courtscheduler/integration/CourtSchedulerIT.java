package uk.gov.moj.cpp.courtscheduler.integration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static javax.ws.rs.core.Response.Status.ACCEPTED;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.justice.services.test.utils.core.http.RestPoller.poll;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.FileUtil.getPayload;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.setupLoggedInUsersPermissionQueryStub;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.setupUserAsSystemUser;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.stubGetReferenceCourtRooms;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.stubGetReferenceDataRotaBusinessTypes;

import uk.gov.justice.services.test.utils.core.http.RequestParams;
import uk.gov.justice.services.test.utils.core.http.ResponseData;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedulerMigrationStatus;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import javax.json.JsonObject;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;


class CourtSchedulerIT extends AbstractIT {

    private static final String BASE_RESOURCE_URL = "/courtschedule";
    private static final String UPDATE_URL = "/edit";
    private static final String DELETE_URL = "/delete";
    private static final String OUCODE_MIGRATE_URL = "/oucode/migrate";

    @BeforeAll
    public static void setUp() {
        stubGetReferenceCourtRooms("referencedata.rota-courtrooms.json");
    }

    @Test
    void shouldCreateSlotBasedSchedule() {
        stubGetReferenceDataRotaBusinessTypes("referencedata.rota-business-types-duration-based.json");
        stubGetReferenceCourtRooms("referencedata.rota-courtrooms.json");
        final String createCourtSchedulePayload = getPayload("create-court-schedule-duration-based.json");
        final Response response = postCommand(BASE_RESOURCE_URL, "application/vnd.courtscheduler.create+json", USER_ID, createCourtSchedulePayload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

    }

    @Test
    void shouldCreateDurationBasedSchedule() {
        stubGetReferenceDataRotaBusinessTypes("referencedata.rota-business-types-duration-based.json");
        final String createCourtSchedulePayload = getPayload("create-court-schedule-duration-based.json");
        final Response response = postCommand(BASE_RESOURCE_URL, "application/vnd.courtscheduler.create+json", USER_ID, createCourtSchedulePayload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
    }

    @Test
    void shouldCreateOrUpdateCourtSchedule() {
        stubGetReferenceDataRotaBusinessTypes("referencedata.rota-business-types-slot-based.json");

        final String createCourtSchedulePayload = getPayload("create-court-schedule-multiple-session.json");
        final Response response = postCommand(BASE_RESOURCE_URL, "application/vnd.courtscheduler.create+json", USER_ID, createCourtSchedulePayload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
    }

    @Test
    void shouldUpdateCourtSchedule() throws SQLException {
        stubGetReferenceDataRotaBusinessTypes("referencedata.rota-business-types-slot-based.json");
        UUID courtScheduleId = UUID.randomUUID();
        CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType("DVLA");
        databaseSeeder.insertCourtSchedule(expected);

        String updateCourtSchedulePayload = getPayload("update-court-schedule.json");
        String changedCourtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3"; // picked from referencedata.rota-courtrooms.json file
        String changedBusinessType = "DVLA";
        String changedSessionType = "AM";
        String changedPanel = "YOUTH";
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_SCHEDULE_ID", expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_ROOM_ID", changedCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("BUSINESS_TYPE", changedBusinessType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("SESSION_TYPE", changedSessionType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("PANEL", changedPanel);

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, "application/vnd.courtscheduler.update+json", USER_ID, updateCourtSchedulePayload);

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
    }

    @Test
    @Disabled
    void shouldNotAllowUpdateCourtScheduleForDifferentBusinessType() throws SQLException {
        stubGetReferenceDataRotaBusinessTypes("referencedata.rota-business-types-slot-based.json");
        UUID courtScheduleId = UUID.randomUUID();
        CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType("DVLA");
        databaseSeeder.insertCourtSchedule(expected);

        String updateCourtSchedulePayload = getPayload("update-court-schedule.json");
        String changedCourtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3"; // picked from referencedata.rota-courtrooms.json file
        String changedBusinessType = "NCPT";
        String changedSessionType = "AM";
        String changedPanel = "YOUTH";
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_SCHEDULE_ID", expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_ROOM_ID", changedCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("BUSINESS_TYPE", changedBusinessType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("SESSION_TYPE", changedSessionType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("PANEL", changedPanel);

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, "application/vnd.courtscheduler.update+json", USER_ID, updateCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
    }

    @Test
    void shouldGetCourtSchedules() throws SQLException, JsonProcessingException {
        stubGetReferenceDataRotaBusinessTypes("referencedata.rota-business-types-duration-based.json");

        UUID courtScheduleId = UUID.randomUUID();
        CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        LocalDate fromDate = expected.getSessionDate().minusDays(1);
        LocalDate toDate = expected.getSessionDate().plusDays(1);
        expected.setBusinessType("TRL");

        expected.setSlotBased(false);
        expected.setMaxDuration(5);
        expected.setAvailableDuration(5);
        expected.setCourtScheduleId(courtScheduleId.toString());
        databaseSeeder.insertCourtSchedule(expected);

        String getCourtScheduleRequestParams = getPayload("courtscheduler.get.court_schedule_query.json");
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("COURT_CENTRE_ID", expected.getCourtHouseId());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("COURT_ROOM_ID", expected.getCourtRoomId());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("BUSINESS_TYPE", expected.getBusinessType());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("SESSION_START_DATE", fromDate.toString());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("SESSION_END_DATE", toDate.toString());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("PAGE_SIZE", "10");
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("PAGE_NUMBER", "1");

        Map<String, Object> map = mapper.readValue(getCourtScheduleRequestParams, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams(BASE_RESOURCE_URL, "application/vnd.courtscheduler.get+json", USER_ID, map);


        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        JsonObject jsonObject = stringToJsonObjectConverter.convert(tempResponseData.getPayload());

        JsonObject courtScheduleJsonObject = jsonObject.getJsonArray("courtSchedules").getJsonObject(0).getJsonArray("sessions").getJsonObject(0);
        assertThat(courtScheduleJsonObject.getString("courtScheduleId"), is(expected.getCourtScheduleId()));
        assertThat(courtScheduleJsonObject.getString("panel"), is(expected.getPanel()));
        assertThat(courtScheduleJsonObject.getBoolean("slotBased"), is(false));
        assertThat(courtScheduleJsonObject.getBoolean("active"), is(true));
        assertThat(courtScheduleJsonObject.getString("courtRoomId"), is(expected.getCourtRoomId()));
        assertThat(courtScheduleJsonObject.getString("courtRoomName"), is(expected.getCourtRoomName()));
    }

    @Test
    void shouldRemoveCourtSchedule() throws Exception {
        String courtScheduleId = UUID.randomUUID().toString();
        CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setCourtScheduleId(courtScheduleId);
        databaseSeeder.insertCourtSchedule(courtSchedule);

        setupLoggedInUsersPermissionQueryStub(USER_ID.toString());
        String deleteHearingSlotsPayload = getPayload("courtscheduler.delete-sessions.json");
        deleteHearingSlotsPayload = deleteHearingSlotsPayload.replace("COURT_SCHEDULE_ID", courtScheduleId);

        final Response response = postCommand(BASE_RESOURCE_URL + DELETE_URL, "application/vnd.courtscheduler.delete+json", USER_ID, deleteHearingSlotsPayload);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
    }

    @Test
    void shouldMigrateOuCodes() throws Exception {
        setupUserAsSystemUser(USER_ID.toString());
        cleanTheDatabase();

        CourtSchedulerMigrationStatus courtSchedulerMigrationStatus = new CourtSchedulerMigrationStatus();
        courtSchedulerMigrationStatus.setOuCode("B12345");
        courtSchedulerMigrationStatus.setCourtCentreId("000f36bc-f33a-42ea-8a6c-8103636c5341");
        courtSchedulerMigrationStatus.setMigrated(false);
        databaseSeeder.insertCourtScheduleMigrationStatus(courtSchedulerMigrationStatus);

        CourtSchedulerMigrationStatus schedulerMigrationStatus = new CourtSchedulerMigrationStatus();
        schedulerMigrationStatus.setOuCode("C12345");
        schedulerMigrationStatus.setCourtCentreId("100f36bc-f33a-42ea-8a6c-8103636c5341");
        schedulerMigrationStatus.setMigrated(false);
        databaseSeeder.insertCourtScheduleMigrationStatus(schedulerMigrationStatus);

        String migrateOuCodePayload = getPayload("oucode-migrate-courtscheduler.json");

        final Response response = postCommand(OUCODE_MIGRATE_URL, "application/vnd.courtscheduler.oucode.migrate+json", USER_ID, migrateOuCodePayload);

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
    }
}
