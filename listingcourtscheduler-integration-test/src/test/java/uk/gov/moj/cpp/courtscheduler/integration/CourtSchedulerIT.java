package uk.gov.moj.cpp.courtscheduler.integration;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static javax.ws.rs.core.Response.Status.ACCEPTED;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.justice.services.test.utils.core.http.RestPoller.poll;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.AM_SESSION_END_TIME_CANNOT_EXCEED;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.MAX_DURATION_FOR_AFTERNOON_LESS_THAN_TOTAL_BOOKED_FOR_AFTERNOON;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.MAX_DURATION_FOR_MORNING_LESS_THAN_TOTAL_BOOKED_FOR_MORNING;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.PM_SESSION_START_TIME_CANNOT_BE_EARLIER;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.SPLIT_ONLY_APPLIES_AD_SESSIONS;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.SPLIT_ONLY_APPLIES_DURATION_BASED_SESSION;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.ALL_DAY;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.localDateToDateWithTime;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.FileUtil.getPayload;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.setupUserAsSystemUser;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.stubGetReferenceDataRotaBusinessTypes;

import uk.gov.justice.services.test.utils.core.http.RequestParams;
import uk.gov.justice.services.test.utils.core.http.ResponseData;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedulerMigrationStatus;

import java.io.StringReader;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;


class CourtSchedulerIT extends AbstractIT {

    private static final String BASE_RESOURCE_URL = "/courtschedule";
    private static final String UPDATE_URL = "/edit";
    private static final String DELETE_URL = "/delete";
    private static final String VALIDATE_URL = "/validate";
    private static final String OUCODE_MIGRATE_URL = "/oucode/migrate";

    private static final String COURT_SCHEDULE_CREATE_CONTENT_TYPE = "application/vnd.courtscheduler.create+json";
    private static final String COURT_SCHEDULE_VALIDATE_CREATE_CONTENT_TYPE = "application/vnd.courtscheduler.validate.create+json";
    private static final String COURT_SCHEDULE_UPDATE_CONTENT_TYPE = "application/vnd.courtscheduler.update+json";
    private static final String COURT_SCHEDULE_GET_CONTENT_TYPE = "application/vnd.courtscheduler.get+json";
    private static final String COURT_SCHEDULE_DELETE_CONTENT_TYPE = "application/vnd.courtscheduler.delete+json";
    private static final String COURT_SCHEDULE_OUCODE_MIGRATE_CONTENT_TYPE = "application/vnd.courtscheduler.oucode.migrate+json";

    public static final String DEFAULT_MORNING_START_TIME = "10:00";
    public static final String DEFAULT_MORNING_END_TIME = "13:00";
    public static final String DEFAULT_AFTERNOON_START_TIME = "14:00";
    public static final String DEFAULT_AFTERNOON_END_TIME = "17:00";
    public static final String DEFAULT_ALL_DAY_START_TIME = "10:00";
    public static final String DEFAULT_ALL_DAY_END_TIME = "17:00";
    public static final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
    
    @Test
    void shouldCreateSlotBasedSchedule() {
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("create-court-schedule-duration-based.json");
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

    }

    @Test
    void shouldCreateCourtScheduleWithSessionTimes() {
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("create-court-schedule-duration-based.json");
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final List<CourtSchedule> courtSchedules = databaseReader.courtSchedules();
        CourtSchedule courtSchedule = courtSchedules.get(0);
        assertThat(courtSchedule.getCourtScheduleId(), is(notNullValue()));
        assertThat(courtSchedule.getSessionStartTime(), is(notNullValue()));
        assertThat(courtSchedule.getSessionEndTime(), is(notNullValue()));
    }

    @Test
    void shouldCreateCourtScheduleWithDefaultSessionTimesAM() {
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("create-court-schedule-duration-based-default-times-am.json");
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final List<CourtSchedule> courtSchedules = databaseReader.courtSchedules();
        CourtSchedule courtSchedule = courtSchedules.get(0);
        assertThat(courtSchedule.getCourtScheduleId(), is(notNullValue()));
        assertThat(sdf.format(courtSchedule.getSessionStartTime()), is(DEFAULT_MORNING_START_TIME));
        assertThat(sdf.format(courtSchedule.getSessionEndTime()), is(DEFAULT_MORNING_END_TIME));
    }

    @Test
    void shouldCreateCourtScheduleWithDefaultSessionTimesPM() {
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("create-court-schedule-duration-based-default-times-pm.json");
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final List<CourtSchedule> courtSchedules = databaseReader.courtSchedules();
        CourtSchedule courtSchedule = courtSchedules.get(0);
        assertThat(courtSchedule.getCourtScheduleId(), is(notNullValue()));
        assertThat(sdf.format(courtSchedule.getSessionStartTime()), is(DEFAULT_AFTERNOON_START_TIME));
        assertThat(sdf.format(courtSchedule.getSessionEndTime()), is(DEFAULT_AFTERNOON_END_TIME));
    }

    @Test
    void shouldCreateCourtScheduleWithDefaultSessionTimesAD() {
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("create-court-schedule-duration-based-default-times-ad.json");
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final List<CourtSchedule> courtSchedules = databaseReader.courtSchedules();
        CourtSchedule courtSchedule = courtSchedules.get(0);
        assertThat(courtSchedule.getCourtScheduleId(), is(notNullValue()));
        assertThat(sdf.format(courtSchedule.getSessionStartTime()), is(DEFAULT_ALL_DAY_START_TIME));
        assertThat(sdf.format(courtSchedule.getSessionEndTime()), is(DEFAULT_ALL_DAY_END_TIME));
    }

    @Test
    void shouldReturnErrorWhenAMSessionEndTimeIsLate() {
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("create-court-schedule-invalid-end-time.json");
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String errorResponseMessage = response.readEntity(String.class);
        assertThat(errorResponseMessage, containsString(AM_SESSION_END_TIME_CANNOT_EXCEED));
    }

    @Test
    void shouldReturnErrorWhenSessionEndTimeIsEarlierThanSessionStartTime() {
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("create-court-schedule-invalid-prior-session-end-time.json");
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String errorResponseMessage = response.readEntity(String.class);
        assertThat(errorResponseMessage, containsString("Session Start Time cannot be later than Session End Time"));
    }

    @Test
    void shouldReturnErrorWhenPMSessionStartTimeIsEarly() {
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("create-court-schedule-invalid-start-time-pm.json");
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String errorResponseMessage = response.readEntity(String.class);
        assertThat(errorResponseMessage, containsString(PM_SESSION_START_TIME_CANNOT_BE_EARLIER));
    }

    @Test
    void shouldCreateDurationBasedScheduleForAllDaySplitSlot() {
        final Integer maxDurationForMorningSlot1 = 120;
        final Integer maxDurationForAfternoonSlot1 = 60;
        final Integer maxDurationForMorningSlot2 = 240;
        final Integer maxDurationForAfternoonSlot2 = 120;

        final LocalDate startDate = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        final LocalDate endDate = startDate.plusDays(28);
        final String createCourtSchedulePayload = getPayload("create-court-schedule-duration-based-all-day-split.json")
                .replaceAll("MAX_DURATION_FOR_MORNING_SLOT_1", String.valueOf(maxDurationForMorningSlot1))
                .replaceAll("MAX_DURATION_FOR_AFTERNOON_SLOT_1", String.valueOf(maxDurationForAfternoonSlot1))
                .replaceAll("MAX_DURATION_FOR_MORNING_SLOT_2", String.valueOf(maxDurationForMorningSlot2))
                .replaceAll("MAX_DURATION_FOR_AFTERNOON_SLOT_2", String.valueOf(maxDurationForAfternoonSlot2))
                .replaceAll("START_DATE", startDate.toString())
                .replaceAll("END_DATE", endDate.toString());
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final List<CourtSchedule> courtSchedules = databaseReader.courtSchedules();
        assertThat(courtSchedules.size(), is(6));
        courtSchedules.forEach(courtScheduleRecordedInDb -> {
            if ("2bd129f3-780e-37dd-b9aa-48690f91b69c".equals(courtScheduleRecordedInDb.getCourtRoomId())) {
                assertThat(courtScheduleRecordedInDb.getMaxAdMorningDuration(), is(maxDurationForMorningSlot1));
                assertThat(courtScheduleRecordedInDb.getMaxAdAfternoonDuration(), is(maxDurationForAfternoonSlot1));
                assertThat(courtScheduleRecordedInDb.getAvailableDuration(), is(0));
                assertThat(courtScheduleRecordedInDb.getMaxDuration(), is(0));
            } else {
                assertThat(courtScheduleRecordedInDb.getMaxAdMorningDuration(), is(maxDurationForMorningSlot2));
                assertThat(courtScheduleRecordedInDb.getMaxAdAfternoonDuration(), is(maxDurationForAfternoonSlot2));
                assertThat(courtScheduleRecordedInDb.getAvailableDuration(), is(0));
                assertThat(courtScheduleRecordedInDb.getMaxDuration(), is(0));
            }
        });
    }

    @Test
    void shouldGet200IfAllDaySplitTrueWithZeroMaxDurationValuesToCreateDurationBasedSchedule() {
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("validate-create-court-schedule-duration-based-with-all-day-split-Zero-max-durations.json");
        final Response response = postCommand(VALIDATE_URL, COURT_SCHEDULE_VALIDATE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
    }

    @Test
    void shouldGet400IfAllDaySplitTrueForSlotBasedAllDaySessionToValidateCreateSchedule() {
        stubGetReferenceDataRotaBusinessTypes("referencedata.rota-business-types-slot-based.json");

        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("validate-create-court-schedule-slot-based-having-all-day-split-true.json");
        final Response response = postCommand(VALIDATE_URL, COURT_SCHEDULE_VALIDATE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String errorResponseMessage = response.readEntity(String.class);
        assertThat(errorResponseMessage, containsString(SPLIT_ONLY_APPLIES_DURATION_BASED_SESSION));
    }

    @Test
    void shouldGet400IfAllDaySplitTrueForAMSession() {
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("validate-create-court-schedule-AM-session-having-all-day-split-true.json");
        final Response response = postCommand(VALIDATE_URL, COURT_SCHEDULE_VALIDATE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String errorResponseMessage = response.readEntity(String.class);
        assertThat(errorResponseMessage, containsString(SPLIT_ONLY_APPLIES_AD_SESSIONS));
    }

    @Test
    void shouldGet200IfAllDaySplitTrueButMissingMaxDurationValuesToCreateDurationBasedSchedule() {
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("validate-create-court-schedule-duration-based-with-all-day-split.json");
        final Response response = postCommand(VALIDATE_URL, COURT_SCHEDULE_VALIDATE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);
        assertThat(response.getStatus(), is(OK.getStatusCode()));
    }

    @Test
    void shouldGet200IfAllDaySplitFalseToCreateDurationBasedSchedule() {
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("validate-create-court-schedule-duration-based.json");
        final Response response = postCommand(VALIDATE_URL, COURT_SCHEDULE_VALIDATE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);
        assertThat(response.getStatus(), is(OK.getStatusCode()));
    }

    @Test
    void shouldGet400IfAllDaySplitFlagMissingToCreateDurationBasedSchedule() {
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("validate-create-court-schedule-duration-based-missing-all-day-split-param.json");
        final Response response = postCommand(VALIDATE_URL, COURT_SCHEDULE_VALIDATE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);
        final String errorResponseMessage = response.readEntity(String.class);
        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        assertThat(errorResponseMessage, is("{\"error\":\"All day split flag should be sent for All Day(AD) session\"}"));
    }

    @Test
    void shouldCreateDurationBasedSchedule() {
        final LocalDate startDate = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        final LocalDate endDate = startDate.plusDays(28);
        final String createCourtSchedulePayload = getPayload("create-court-schedule-duration-based.json")
                .replaceAll("START_DATE", startDate.toString())
                .replaceAll("END_DATE", endDate.toString());
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
    }

    @Test
    void shouldCreateOrUpdateCourtSchedule() {
        stubGetReferenceDataRotaBusinessTypes("referencedata.rota-business-types-slot-based.json");

        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("create-court-schedule-multiple-session.json");
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
    }

    @Test
    void shouldUpdateCourtSchedule() throws SQLException {
        stubGetReferenceDataRotaBusinessTypes("referencedata.rota-business-types-slot-based.json");
        UUID courtScheduleId = UUID.randomUUID();
        CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType("DVLA");
        expected.setSupportAdSplit(false);
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

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
    }

    @Test
    void shouldUpdateCourtScheduleAllDaySplit() throws SQLException {
        UUID courtScheduleId = UUID.randomUUID();
        final CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType("TRL");
        expected.setSessionDate(LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)));
        expected.setSupportAdSplit(true);
        databaseSeeder.insertCourtSchedule(expected);

        String updateCourtSchedulePayload = getPayload("update-court-schedule-all-day-split.json");
        String changedCourtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3"; // picked from referencedata.rota-courtrooms.json file
        String changedBusinessType = "TRL";
        String changedSessionType = "AD";
        String changedPanel = "YOUTH";
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_SCHEDULE_ID", expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_ROOM_ID", changedCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("BUSINESS_TYPE", changedBusinessType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("SESSION_TYPE", changedSessionType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("PANEL", changedPanel);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("MAX_DURATION_FOR_MORNING", "120");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("MAX_DURATION_FOR_AFTERNOON", "60");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("SESSION_START_TIME", "10:30");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("SESSION_END_TIME", "17:30");

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));


        final CourtSchedule courtScheduleAfterUpdate = databaseReader.courtScheduleById(courtScheduleId.toString());
        assertThat(courtScheduleAfterUpdate.getMaxAdMorningDuration(), is(120));
        assertThat(courtScheduleAfterUpdate.getMaxAdAfternoonDuration(), is(60));

        final Date expectedStartTime = localDateToDateWithTime(expected.getSessionDate(), 10, 30);
        final Date expectedEndTime = localDateToDateWithTime(expected.getSessionDate(), 17, 30);
        assertThat(courtScheduleAfterUpdate.getSessionStartTime(), is(expectedStartTime));
        assertThat(courtScheduleAfterUpdate.getSessionEndTime(), is(expectedEndTime));
    }

    @Test
    void shouldUpdateCourtScheduleIsOverbookingAllowed() throws SQLException {
        UUID courtScheduleId = UUID.randomUUID();
        final CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType("TRL");
        expected.setSessionDate(LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)));
        expected.setSupportAdSplit(true);
        expected.setIsOverbookingAllowed(false);
        databaseSeeder.insertCourtSchedule(expected);

        String updateCourtSchedulePayload = getPayload("update-court-schedule-is-overbooking-allowed.json");
        String changedCourtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3"; // picked from referencedata.rota-courtrooms.json file
        String changedBusinessType = "TRL";
        String changedSessionType = "AD";
        String changedPanel = "YOUTH";
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_SCHEDULE_ID", expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_ROOM_ID", changedCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("BUSINESS_TYPE", changedBusinessType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("SESSION_TYPE", changedSessionType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("PANEL", changedPanel);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("MAX_DURATION_FOR_MORNING", "120");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("MAX_DURATION_FOR_AFTERNOON", "60");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("SESSION_START_TIME", "10:30");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("SESSION_END_TIME", "17:30");

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final CourtSchedule courtScheduleAfterUpdate = databaseReader.courtScheduleById(courtScheduleId.toString());
        assertThat(courtScheduleAfterUpdate.getMaxAdMorningDuration(), is(120));
        assertThat(courtScheduleAfterUpdate.getMaxAdAfternoonDuration(), is(60));
        assertThat(courtScheduleAfterUpdate.getIsOverbookingAllowed(), is(true));

        final Date expectedStartTime = localDateToDateWithTime(expected.getSessionDate(), 10, 30);
        final Date expectedEndTime = localDateToDateWithTime(expected.getSessionDate(), 17, 30);
        assertThat(courtScheduleAfterUpdate.getSessionStartTime(), is(expectedStartTime));
        assertThat(courtScheduleAfterUpdate.getSessionEndTime(), is(expectedEndTime));
    }

    @Test
    void shouldUpdateCourtScheduleAllDaySplitWithoutGivenSessionStartAndEndTime() throws SQLException {
        UUID courtScheduleId = UUID.randomUUID();
        final CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType("TRL");
        expected.setSessionDate(LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)));
        expected.setSupportAdSplit(true);
        databaseSeeder.insertCourtSchedule(expected);

        String updateCourtSchedulePayload = getPayload("update-court-schedule-all-day-split-without-session-start-end-time.json");
        String changedCourtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3"; // picked from referencedata.rota-courtrooms.json file
        String changedBusinessType = "TRL";
        String changedSessionType = "AD";
        String changedPanel = "YOUTH";
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_SCHEDULE_ID", expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_ROOM_ID", changedCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("BUSINESS_TYPE", changedBusinessType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("SESSION_TYPE", changedSessionType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("PANEL", changedPanel);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("MAX_DURATION_FOR_MORNING", "120");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("MAX_DURATION_FOR_AFTERNOON", "60");

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final CourtSchedule courtScheduleAfterUpdate = databaseReader.courtScheduleById(courtScheduleId.toString());
        assertThat(courtScheduleAfterUpdate.getMaxAdMorningDuration(), is(120));
        assertThat(courtScheduleAfterUpdate.getMaxAdAfternoonDuration(), is(60));

        final Date expectedStartTime = localDateToDateWithTime(expected.getSessionDate(), 10, 0);
        final Date expectedEndTime = localDateToDateWithTime(expected.getSessionDate(), 17, 00);
        assertThat(courtScheduleAfterUpdate.getSessionStartTime(), is(expectedStartTime));
        assertThat(courtScheduleAfterUpdate.getSessionEndTime(), is(expectedEndTime));
    }

    @Test
    void shouldGet400WhenUpdatingCourtScheduleWithInvalidMaxDurationValues() throws SQLException {
        UUID courtScheduleId = UUID.randomUUID();
        CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType("TRL");
        databaseSeeder.insertCourtSchedule(expected);

        String updateCourtSchedulePayload = getPayload("update-court-schedule-all-day-split.json");
        String changedCourtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3"; // picked from referencedata.rota-courtrooms.json file
        String changedBusinessType = "TRL";
        String changedSessionType = "AD";
        String changedPanel = "YOUTH";
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_SCHEDULE_ID", expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_ROOM_ID", changedCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("BUSINESS_TYPE", changedBusinessType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("SESSION_TYPE", changedSessionType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("PANEL", changedPanel);

        // Set invalid max duration values
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("MAXDURATIONFORMORNING", "0");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("MAXDURATIONFORAFTERNOON", "0");

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
    }

    @Test
    void shouldGet400WhenUpdatingCourtScheduleWithNonDurationBasedBusinessType() throws SQLException {
        stubGetReferenceDataRotaBusinessTypes("referencedata.rota-business-types-slot-based.json");
        UUID courtScheduleId = UUID.randomUUID();
        CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType("TRL");
        databaseSeeder.insertCourtSchedule(expected);

        String updateCourtSchedulePayload = getPayload("update-court-schedule-all-day-split.json");
        String changedCourtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3"; // picked from referencedata.rota-courtrooms.json file
        String changedBusinessType = "TRL";
        String changedSessionType = "AD";
        String changedPanel = "YOUTH";
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_SCHEDULE_ID", expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_ROOM_ID", changedCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("BUSINESS_TYPE", changedBusinessType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("SESSION_TYPE", changedSessionType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("PANEL", changedPanel);

        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("MAXDURATIONFORMORNING", "120");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("MAXDURATIONFORAFTERNOON", "60");

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
    }

    @Test
    void shouldGet400WhenUpdatingCourtScheduleADSplit() throws SQLException {
        stubGetReferenceDataRotaBusinessTypes("referencedata.rota-business-types-slot-based.json");
        UUID courtScheduleId = UUID.randomUUID();
        CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType("DVLA");
        expected.setSupportAdSplit(false);
        databaseSeeder.insertCourtSchedule(expected);

        String updateCourtSchedulePayload = getPayload("update-court-schedule-all-day-split.json");
        String changedCourtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3"; // picked from referencedata.rota-courtrooms.json file
        String changedBusinessType = "DVLA";
        String changedSessionType = "AD";
        String changedPanel = "YOUTH";
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_SCHEDULE_ID", expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_ROOM_ID", changedCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("BUSINESS_TYPE", changedBusinessType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("SESSION_TYPE", changedSessionType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("PANEL", changedPanel);

        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("MAXDURATIONFORMORNING", "120");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("MAXDURATIONFORAFTERNOON", "60");

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
    }

    @Test
    void shouldUpdateCourtScheduleWithValidDurationBasedBusinessType() throws SQLException {
        UUID courtScheduleId = UUID.randomUUID();
        CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType("TRL");
        expected.setSupportAdSplit(true);
        expected.setCourtSession(ALL_DAY);
        databaseSeeder.insertCourtSchedule(expected);

        String updateCourtSchedulePayload = getPayload("update-court-schedule-all-day-split.json");
        String changedCourtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3"; // picked from referencedata.rota-courtrooms.json file
        String changedBusinessType = "TRL";
        String changedSessionType = "AD";
        String changedPanel = "YOUTH";
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_SCHEDULE_ID", expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_ROOM_ID", changedCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("BUSINESS_TYPE", changedBusinessType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("SESSION_TYPE", changedSessionType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("PANEL", changedPanel);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("MAX_DURATION_FOR_MORNING", "120");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("MAX_DURATION_FOR_AFTERNOON", "60");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("SESSION_START_TIME", "10:30");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("SESSION_END_TIME", "17:30");

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
    }

    @Test
    void shouldNotUpdateCourtScheduleIfTotalBookedExceedsMaxDurationOrSlot() throws SQLException {
        stubGetReferenceDataRotaBusinessTypes("referencedata.rota-business-types-slot-based.json");
        UUID courtScheduleId = UUID.randomUUID();
        CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType("DVLA");
        expected.setSupportAdSplit(false);
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

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
    }

    @Test
    void shouldNotUpdateCourtScheduleIfTotalBookedExceedsMaxDurationForMorning() throws SQLException {
        final CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        final UUID courtScheduleId = UUID.randomUUID();
        final UUID hearingIdForMorning = UUID.randomUUID();
        final UUID bookingIdForMorning = UUID.randomUUID();
        final UUID hearingIdForAfternoon = UUID.randomUUID();
        final UUID bookingIdForAfternoon = UUID.randomUUID();
        final Integer maxDurationForMorning = 120;
        final Integer maxDurationForAfternoon = 60;

        courtSchedule.setBusinessType("TRL");
        courtSchedule.setSlotBased(false);
        courtSchedule.setMaxDuration(0);
        courtSchedule.setAvailableDuration(0);
        courtSchedule.setSupportAdSplit(true);
        courtSchedule.setCourtSession(ALL_DAY);
        courtSchedule.setMaxAdMorningDuration(maxDurationForMorning);
        courtSchedule.setMaxAdAfternoonDuration(maxDurationForAfternoon);
        courtSchedule.setCourtScheduleId(courtScheduleId.toString());
        courtSchedule.setSessionDate(LocalDate.of(2025, 3, 18));
        courtSchedule.setSessionStartTime(DateUtils.combineDateAndTime(courtSchedule.getSessionDate(), "10:00"));
        courtSchedule.setSessionEndTime(DateUtils.combineDateAndTime(courtSchedule.getSessionDate(), "16:00"));
        databaseSeeder.insertCourtSchedule(courtSchedule);

        createAllocatedListing(courtSchedule, hearingIdForMorning, bookingIdForMorning, 90, "10:00");
        createAllocatedListing(courtSchedule, hearingIdForAfternoon, bookingIdForAfternoon, 60, "15:00");

        String updateCourtSchedulePayload = getPayload("update-court-schedule-all-day-split.json");
        String changedCourtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3"; // picked from referencedata.rota-courtrooms.json file
        String changedBusinessType = "TRL";
        String changedSessionType = "AD";
        String changedPanel = "YOUTH";
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_SCHEDULE_ID", courtSchedule.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_ROOM_ID", changedCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("BUSINESS_TYPE", changedBusinessType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("SESSION_TYPE", changedSessionType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("PANEL", changedPanel);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("MAX_DURATION_FOR_MORNING", "60");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("MAX_DURATION_FOR_AFTERNOON", "30");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("SESSION_START_TIME", "10:00");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("SESSION_END_TIME", "15:00");

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);
        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));

        final String errorResponseMessage = response.readEntity(String.class);
        assertThat(errorResponseMessage, containsString(MAX_DURATION_FOR_MORNING_LESS_THAN_TOTAL_BOOKED_FOR_MORNING));
    }

    @Test
    void shouldNotUpdateCourtScheduleIfTotalBookedExceedsMaxDurationForAfternoon() throws SQLException {
        final CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        final UUID courtScheduleId = UUID.randomUUID();
        final UUID hearingIdForMorning = UUID.randomUUID();
        final UUID bookingIdForMorning = UUID.randomUUID();
        final UUID hearingIdForAfternoon = UUID.randomUUID();
        final UUID bookingIdForAfternoon = UUID.randomUUID();
        final Integer maxDurationForMorning = 120;
        final Integer maxDurationForAfternoon = 60;

        courtSchedule.setBusinessType("TRL");
        courtSchedule.setSlotBased(false);
        courtSchedule.setMaxDuration(0);
        courtSchedule.setAvailableDuration(0);
        courtSchedule.setSupportAdSplit(true);
        courtSchedule.setCourtSession(ALL_DAY);
        courtSchedule.setMaxAdMorningDuration(maxDurationForMorning);
        courtSchedule.setMaxAdAfternoonDuration(maxDurationForAfternoon);
        courtSchedule.setCourtScheduleId(courtScheduleId.toString());
        courtSchedule.setSessionDate(LocalDate.of(2025, 3, 18));
        courtSchedule.setSessionStartTime(DateUtils.combineDateAndTime(courtSchedule.getSessionDate(), "10:00"));
        courtSchedule.setSessionEndTime(DateUtils.combineDateAndTime(courtSchedule.getSessionDate(), "16:00"));
        databaseSeeder.insertCourtSchedule(courtSchedule);

        createAllocatedListing(courtSchedule, hearingIdForMorning, bookingIdForMorning, 90, "10:00");
        createAllocatedListing(courtSchedule, hearingIdForAfternoon, bookingIdForAfternoon, 60, "15:00");

        String updateCourtSchedulePayload = getPayload("update-court-schedule-all-day-split.json");
        String changedCourtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3"; // picked from referencedata.rota-courtrooms.json file
        String changedBusinessType = "TRL";
        String changedSessionType = "AD";
        String changedPanel = "YOUTH";
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_SCHEDULE_ID", courtSchedule.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_ROOM_ID", changedCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("BUSINESS_TYPE", changedBusinessType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("SESSION_TYPE", changedSessionType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("PANEL", changedPanel);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("MAX_DURATION_FOR_MORNING", "90");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("MAX_DURATION_FOR_AFTERNOON", "30");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("SESSION_START_TIME", "10:00");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("SESSION_END_TIME", "15:00");

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);
        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));

        final String errorResponseMessage = response.readEntity(String.class);
        assertThat(errorResponseMessage, containsString(MAX_DURATION_FOR_AFTERNOON_LESS_THAN_TOTAL_BOOKED_FOR_AFTERNOON));
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

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
    }

    @Test
    void shouldGetCourtSchedules() throws SQLException, JsonProcessingException {
        UUID courtScheduleId = UUID.randomUUID();
        UUID hearingId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        LocalDate fromDate = expected.getSessionDate().minusDays(1);
        LocalDate toDate = expected.getSessionDate().plusDays(1);
        expected.setBusinessType("TRL");

        expected.setSlotBased(false);
        expected.setMaxDuration(5);
        expected.setAvailableDuration(5);
        expected.setSupportAdSplit(false);
        expected.setMaxAdMorningDuration(0);
        expected.setMaxAdAfternoonDuration(0);
        expected.setCourtScheduleId(courtScheduleId.toString());
        databaseSeeder.insertCourtSchedule(expected);

        AllocatedListing allocatedListing = RANDOM.nextObject(AllocatedListing.class);
        allocatedListing.setCourtScheduleId(expected.getCourtScheduleId());
        allocatedListing.setHearingId(hearingId.toString());
        allocatedListing.setBookingId(bookingId.toString());
        databaseSeeder.insertAllocatedListing(allocatedListing);

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

        final RequestParams requestParams = getRequestParams(BASE_RESOURCE_URL, COURT_SCHEDULE_GET_CONTENT_TYPE, USER_ID, map);


        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        JsonObject jsonObject = stringToJsonObjectConverter.convert(tempResponseData.getPayload());

        JsonObject courtScheduleJsonObject = jsonObject.getJsonArray("courtSchedules").getJsonObject(0).getJsonArray("sessions").getJsonObject(0);
        assertThat(courtScheduleJsonObject.getString("courtScheduleId"), is(expected.getCourtScheduleId()));
        assertThat(courtScheduleJsonObject.getString("panel"), is(expected.getPanel()));
        assertThat(courtScheduleJsonObject.getBoolean("slotBased"), is(false));
        assertThat(courtScheduleJsonObject.getBoolean("active"), is(true));
        assertThat(courtScheduleJsonObject.getString("courtRoomId"), is(expected.getCourtRoomId()));
        assertThat(courtScheduleJsonObject.getString("courtRoomName"), is(expected.getCourtRoomName()));
        assertThat(courtScheduleJsonObject.getBoolean("allDaySplit"), is(false));
        assertThat(courtScheduleJsonObject.getInt("maxDurationForMorning"), is(0));
        assertThat(courtScheduleJsonObject.getInt("maxDurationForAfternoon"), is(0));
    }

    @Test
    void shouldGetCourtSchedulesForAllDaySplitSlot() throws SQLException, JsonProcessingException {
        final UUID courtScheduleId = UUID.randomUUID();
        final UUID hearingIdForMorning = UUID.randomUUID();
        final UUID bookingIdForMorning = UUID.randomUUID();
        final UUID hearingIdForAfternoon = UUID.randomUUID();
        final UUID bookingIdForAfternoon = UUID.randomUUID();
        final Integer maxDurationForMorning = 120;
        final Integer maxDurationForAfternoon = 60;
        final CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);

        courtSchedule.setBusinessType("TRL");
        courtSchedule.setSlotBased(false);
        courtSchedule.setMaxDuration(0);
        courtSchedule.setAvailableDuration(0);
        courtSchedule.setSupportAdSplit(true);
        courtSchedule.setCourtSession(ALL_DAY);
        courtSchedule.setMaxAdMorningDuration(maxDurationForMorning);
        courtSchedule.setMaxAdAfternoonDuration(maxDurationForAfternoon);
        courtSchedule.setCourtScheduleId(courtScheduleId.toString());
        courtSchedule.setSessionDate(LocalDate.of(2025, 3, 18));
        courtSchedule.setSessionStartTime(DateUtils.combineDateAndTime(courtSchedule.getSessionDate(), "10:00"));
        courtSchedule.setSessionEndTime(DateUtils.combineDateAndTime(courtSchedule.getSessionDate(), "16:00"));
        databaseSeeder.insertCourtSchedule(courtSchedule);

        final AllocatedListing allocatedListingForMorning = createAllocatedListing(courtSchedule, hearingIdForMorning, bookingIdForMorning, 60, "10:00");
        final AllocatedListing allocatedListingForAfternoon = createAllocatedListing(courtSchedule, hearingIdForAfternoon, bookingIdForAfternoon, 30, "15:00");

        final LocalDate fromDate = courtSchedule.getSessionDate().minusDays(1);
        final LocalDate toDate = courtSchedule.getSessionDate().plusDays(1);

        String getCourtScheduleRequestParams = getPayload("courtscheduler.get.court_schedule_query.json");
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("COURT_CENTRE_ID", courtSchedule.getCourtHouseId());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("COURT_ROOM_ID", courtSchedule.getCourtRoomId());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("BUSINESS_TYPE", courtSchedule.getBusinessType());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("SESSION_START_DATE", fromDate.toString());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("SESSION_END_DATE", toDate.toString());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("PAGE_SIZE", "10");
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("PAGE_NUMBER", "1");

        Map<String, Object> map = mapper.readValue(getCourtScheduleRequestParams, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams(BASE_RESOURCE_URL, COURT_SCHEDULE_GET_CONTENT_TYPE, USER_ID, map);


        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        JsonObject jsonObject = stringToJsonObjectConverter.convert(tempResponseData.getPayload());

        JsonObject courtScheduleJsonObject = jsonObject.getJsonArray("courtSchedules").getJsonObject(0).getJsonArray("sessions").getJsonObject(0);
        assertThat(courtScheduleJsonObject.getString("courtScheduleId"), is(courtSchedule.getCourtScheduleId()));
        assertThat(courtScheduleJsonObject.getString("panel"), is(courtSchedule.getPanel()));
        assertThat(courtScheduleJsonObject.getBoolean("slotBased"), is(false));
        assertThat(courtScheduleJsonObject.getBoolean("active"), is(true));
        assertThat(courtScheduleJsonObject.getString("courtRoomId"), is(courtSchedule.getCourtRoomId()));
        assertThat(courtScheduleJsonObject.getString("courtRoomName"), is(courtSchedule.getCourtRoomName()));
        assertThat(courtScheduleJsonObject.getBoolean("allDaySplit"), is(true));
        assertThat(courtScheduleJsonObject.getInt("maxDurationForMorning"), is(maxDurationForMorning));
        assertThat(courtScheduleJsonObject.getInt("maxDurationForAfternoon"), is(maxDurationForAfternoon));
        assertThat(courtScheduleJsonObject.getInt("availableDurationForMorning"), is(maxDurationForMorning - allocatedListingForMorning.getDuration()));
        assertThat(courtScheduleJsonObject.getInt("availableDurationForAfternoon"), is(maxDurationForAfternoon - allocatedListingForAfternoon.getDuration()));
    }

    @Test
    void shouldRemoveCourtSchedule() throws Exception {
        final Integer maxDurationForMorning = 120;
        final Integer maxDurationForAfternoon = 60;
        final UUID hearingIdForMorning = UUID.randomUUID();
        final UUID bookingIdForMorning = UUID.randomUUID();
        final UUID hearingIdForAfternoon = UUID.randomUUID();
        final UUID bookingIdForAfternoon = UUID.randomUUID();
        String courtScheduleId = UUID.randomUUID().toString();
        CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setBusinessType("TRL");
        courtSchedule.setSlotBased(false);
        courtSchedule.setMaxDuration(0);
        courtSchedule.setAvailableDuration(0);
        courtSchedule.setSupportAdSplit(true);
        courtSchedule.setCourtSession(ALL_DAY);
        courtSchedule.setMaxAdMorningDuration(maxDurationForMorning);
        courtSchedule.setMaxAdAfternoonDuration(maxDurationForAfternoon);
        courtSchedule.setCourtScheduleId(courtScheduleId);
        courtSchedule.setSessionDate(LocalDate.of(2025, 3, 18));
        courtSchedule.setSessionStartTime(DateUtils.combineDateAndTime(courtSchedule.getSessionDate(), "10:00"));
        courtSchedule.setSessionEndTime(DateUtils.combineDateAndTime(courtSchedule.getSessionDate(), "16:00"));

        databaseSeeder.insertCourtSchedule(courtSchedule);

        final AllocatedListing allocatedListingForMorning = createAllocatedListing(courtSchedule, hearingIdForMorning, bookingIdForMorning, 60, "10:00");
        final AllocatedListing allocatedListingForAfternoon = createAllocatedListing(courtSchedule, hearingIdForAfternoon, bookingIdForAfternoon, 30, "15:00");

        String deleteHearingSlotsPayload = getPayload("courtscheduler.delete-sessions.json");
        deleteHearingSlotsPayload = deleteHearingSlotsPayload.replace("COURT_SCHEDULE_ID", courtScheduleId);

        final Response response = postCommand(BASE_RESOURCE_URL + DELETE_URL, COURT_SCHEDULE_DELETE_CONTENT_TYPE, USER_ID, deleteHearingSlotsPayload);


        assertThat(response.getStatus(), is(OK.getStatusCode()));

        try (JsonReader jsonReader = Json.createReader(new StringReader(response.readEntity(String.class)))) {
            JsonObject jsonResponse = jsonReader.readObject();
            assertTrue(jsonResponse.containsKey("error"), "Response should contain an 'error' key");
            assertTrue(jsonResponse.containsKey("sessions"), "Response should contain 'sessions' key");
            final JsonObject sessionJSONObj = jsonResponse.getJsonArray("sessions").getJsonObject(0);
            assertThat(sessionJSONObj.getString("courtScheduleId"), is(courtSchedule.getCourtScheduleId()));
            assertThat( sessionJSONObj.getInt("totalBooked"), is(allocatedListingForMorning.getDuration() + allocatedListingForAfternoon.getDuration()));
        }
    }

    @Test
    void shouldTryToRemoveCourtScheduleWhenNoSuchCourtScheduleWithoutException() throws Exception {
        final Integer maxDurationForMorning = 120;
        final Integer maxDurationForAfternoon = 60;
        String courtScheduleId = UUID.randomUUID().toString();
        String nonExistingCourtScheduleId = UUID.randomUUID().toString();
        CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setBusinessType("TRL");
        courtSchedule.setSlotBased(false);
        courtSchedule.setMaxDuration(0);
        courtSchedule.setAvailableDuration(0);
        courtSchedule.setSupportAdSplit(true);
        courtSchedule.setCourtSession(ALL_DAY);
        courtSchedule.setMaxAdMorningDuration(maxDurationForMorning);
        courtSchedule.setMaxAdAfternoonDuration(maxDurationForAfternoon);
        courtSchedule.setCourtScheduleId(courtScheduleId);
        courtSchedule.setSessionDate(LocalDate.of(2025, 3, 18));
        courtSchedule.setSessionStartTime(DateUtils.combineDateAndTime(courtSchedule.getSessionDate(), "10:00"));
        courtSchedule.setSessionEndTime(DateUtils.combineDateAndTime(courtSchedule.getSessionDate(), "16:00"));

        databaseSeeder.insertCourtSchedule(courtSchedule);

        String deleteHearingSlotsPayload = getPayload("courtscheduler.delete-sessions.json");
        deleteHearingSlotsPayload = deleteHearingSlotsPayload.replace("COURT_SCHEDULE_ID", nonExistingCourtScheduleId);

        final Response response = postCommand(BASE_RESOURCE_URL + DELETE_URL, COURT_SCHEDULE_DELETE_CONTENT_TYPE, USER_ID, deleteHearingSlotsPayload);

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

        final Response response = postCommand(OUCODE_MIGRATE_URL, COURT_SCHEDULE_OUCODE_MIGRATE_CONTENT_TYPE, USER_ID, migrateOuCodePayload);

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
    }

    public String prepareCreateCourtSchedulePayload(final String jsonFilePath) {
        final LocalDate startDate = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        final LocalDate endDate = startDate.plusDays(28);

        return getPayload(jsonFilePath)
                .replaceAll("START_DATE", startDate.toString())
                .replaceAll("END_DATE", endDate.toString());
    }

    private AllocatedListing createAllocatedListing(final CourtSchedule courtSchedule, final UUID hearingIdForMorning, final UUID bookingIdForMorning, final int duration, final String time) throws SQLException {
        final AllocatedListing allocatedListingForMorning = RANDOM.nextObject(AllocatedListing.class);
        allocatedListingForMorning.setCourtScheduleId(courtSchedule.getCourtScheduleId());
        allocatedListingForMorning.setHearingId(hearingIdForMorning.toString());
        allocatedListingForMorning.setBookingId(bookingIdForMorning.toString());
        allocatedListingForMorning.setDuration(duration);
        allocatedListingForMorning.setHearingStartTime(DateUtils.combineDateAndTime(courtSchedule.getSessionDate(), time));
        databaseSeeder.insertAllocatedListing(allocatedListingForMorning);
        return allocatedListingForMorning;
    }

}
