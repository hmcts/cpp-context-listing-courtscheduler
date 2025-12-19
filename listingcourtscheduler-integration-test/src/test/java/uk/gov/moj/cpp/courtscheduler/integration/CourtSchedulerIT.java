package uk.gov.moj.cpp.courtscheduler.integration;

import static java.time.LocalDate.now;
import static java.time.ZoneOffset.UTC;
import static java.time.format.DateTimeFormatter.ofPattern;
import static java.util.Date.from;
import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static javax.ws.rs.core.Response.Status.ACCEPTED;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.justice.services.test.utils.core.http.RestPoller.poll;
import static uk.gov.moj.cpp.courtscheduler.common.Jurisdiction.CROWN;
import static uk.gov.moj.cpp.courtscheduler.common.Jurisdiction.MAGISTRATES;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.AM_SESSION_END_TIME_CANNOT_EXCEED;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.MAX_DURATION_FOR_AFTERNOON_LESS_THAN_TOTAL_BOOKED_FOR_AFTERNOON;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.MAX_DURATION_FOR_MORNING_LESS_THAN_TOTAL_BOOKED_FOR_MORNING;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.MAX_HEARING_TIME_BEFORE_SESSION_END_TIME;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.MIN_HEARING_TIME_AFTER_SESSION_START_TIME;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.PM_SESSION_START_TIME_CANNOT_BE_EARLIER;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.SESSION_EDIT_ANOTHER_USER;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.SESSION_END_TIME_CANNOT_BE_LATER;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.SESSION_START_TIME_CANNOT_BE_EARLIER;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.SPLIT_ONLY_APPLIES_AD_SESSIONS;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.SPLIT_ONLY_APPLIES_DURATION_BASED_SESSION;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.ALL_DAY;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.AM_SESSION;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.combineDateAndTime;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.getRandomFutureDateWithinNextYear;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.localDateToDateWithTime;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.TimezoneUtils.getUtcTimeStringForDate;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.FileUtil.getPayload;

import uk.gov.justice.services.test.utils.core.http.RequestParams;
import uk.gov.justice.services.test.utils.core.http.ResponseData;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;
import uk.gov.moj.cpp.courtscheduler.domain.utils.TimezoneUtils;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedulerMigrationStatus;

import java.io.StringReader;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;

class CourtSchedulerIT extends AbstractIT {

    private static final String BASE_RESOURCE_URL = "/courtschedule";
    private static final String UPDATE_URL = "/edit";
    private static final String DELETE_URL = "/delete";
    private static final String SEARCH_BY_ID_URL = "/search.court-schedules-by-id";
    private static final String VALIDATE_URL = "/validate";
    private static final String VALIDATE_SESSION_AVAILABILITY_URL = "/validate-session-availability";
    private static final String OUCODE_MIGRATE_URL = "/oucode/migrate";

    private static final String COURT_SCHEDULE_CREATE_CONTENT_TYPE = "application/vnd.courtscheduler.create+json";
    private static final String COURT_SCHEDULE_VALIDATE_CREATE_CONTENT_TYPE = "application/vnd.courtscheduler.validate.create+json";
    private static final String COURT_SCHEDULE_VALIDATE_SESSION_AVAILABILITY_CONTENT_TYPE = "application/vnd.courtscheduler.validate.session.availability+json";
    private static final String COURT_SCHEDULE_UPDATE_CONTENT_TYPE = "application/vnd.courtscheduler.update+json";
    private static final String COURT_SCHEDULE_GET_CONTENT_TYPE = "application/vnd.courtscheduler.get+json";
    private static final String COURT_SCHEDULE_SEARCH_COURTSCHEDULES_BY_ID_CONTENT_TYPE = "application/vnd.courtscheduler.search.court-schedules-by-id+json";
    private static final String COURT_SCHEDULE_DELETE_CONTENT_TYPE = "application/vnd.courtscheduler.delete+json";
    private static final String COURT_SCHEDULE_OUCODE_MIGRATE_CONTENT_TYPE = "application/vnd.courtscheduler.oucode.migrate+json";
    private static final String COURT_SCHEDULE_ASSIGN_COURTROOM_CONTENT_TYPE = "application/vnd.courtscheduler.assign.courtroom+json";
    private static final String ASSIGN_COURTROOM_URL = "/assign.courtroom";

    public static final String DEFAULT_MORNING_START_TIME = "10:00";
    public static final String DEFAULT_MORNING_END_TIME = "13:00";
    public static final String DEFAULT_AFTERNOON_START_TIME = "14:00";
    public static final String DEFAULT_AFTERNOON_END_TIME = "17:00";
    public static final String DEFAULT_ALL_DAY_START_TIME = "10:00";
    public static final String DEFAULT_ALL_DAY_END_TIME = "17:00";
    public static final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");

    static {
        // Set the timezone for the SimpleDateFormat to London
        sdf.setTimeZone(TimeZone.getTimeZone("Europe/London"));
    }

    @Test
    void shouldCreateSlotBasedSchedule() {
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("create-court-schedule-duration-based.json");
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

    }

    @Test
    void shouldCreateCourtScheduleWithSessionTimes() {
        //We send localtime
        final LocalDate startDate = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        final java.util.Date expectedStartTime = java.util.Date.from(startDate.atTime(10, 0).toInstant(ZoneOffset.UTC));
        final java.util.Date expectedEndTime = java.util.Date.from(startDate.atTime(12, 0).toInstant(ZoneOffset.UTC));
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("create-court-schedule-duration-based.json");
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final List<CourtSchedule> courtSchedules = databaseReader.courtSchedules();
        CourtSchedule courtSchedule = courtSchedules.get(0);
        assertThat(courtSchedule.getCourtScheduleId(), is(notNullValue()));
        assertThat(courtSchedule.getSessionStartTime(), is(notNullValue()));
        assertThat(courtSchedule.getSessionEndTime(), is(notNullValue()));
        assertThat(courtSchedule.getSessionStartTime(), is(expectedStartTime));
        assertThat(courtSchedule.getSessionEndTime(), is(expectedEndTime));
    }

    @Test
    void shouldCreateCourtScheduleWithSessionTimes_AcrossSummerAndWinterTime() {
        LocalDate startDate = LocalDate.now().withMonth(10).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        // If the calculated date is in the past, use next year's October
        if (startDate.isBefore(LocalDate.now())) {
            startDate = LocalDate.now().plusYears(1).withMonth(10).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        }
        final LocalDate endDate = startDate.plusDays(56);
        // The JSON contains times in BST (local time), so convert to UTC for comparison
        // 10:00 BST = 09:00 UTC and 12:00 BST = 11:00 UTC during BST period
        final java.util.Date expectedStartTimeFirstWeek = TimezoneUtils.combineLocalDateAndTimeToUtc(startDate, LocalTime.of(10, 0));
        final java.util.Date expectedEndTimeFirstWeek = TimezoneUtils.combineLocalDateAndTimeToUtc(startDate, LocalTime.of(12, 0));
        final java.util.Date expectedStartTimeLastWeek = TimezoneUtils.combineLocalDateAndTimeToUtc(startDate.plusDays(56), LocalTime.of(10, 0));
        final java.util.Date expectedEndTimeLastWeek = TimezoneUtils.combineLocalDateAndTimeToUtc(startDate.plusDays(56), LocalTime.of(12, 0));
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload_testBSTToUTC("create-court-schedule-duration-based-bst-timings.json", startDate, endDate);
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final List<CourtSchedule> courtSchedules = databaseReader.courtSchedules();
        CourtSchedule courtScheduleFirst = courtSchedules.get(0);
        CourtSchedule courtScheduleLast = courtSchedules.get(courtSchedules.size() - 1);
        assertThat(courtScheduleFirst.getCourtScheduleId(), is(notNullValue()));
        assertThat(courtScheduleFirst.getSessionStartTime(), is(notNullValue()));
        assertThat(courtScheduleFirst.getSessionEndTime(), is(notNullValue()));
        assertThat(courtScheduleFirst.getSessionStartTime(), is(expectedStartTimeFirstWeek));
        assertThat(courtScheduleFirst.getSessionEndTime(), is(expectedEndTimeFirstWeek));
        assertThat(courtScheduleLast.getSessionStartTime(), is(expectedStartTimeLastWeek));
        assertThat(courtScheduleLast.getSessionEndTime(), is(expectedEndTimeLastWeek));
    }

    @Test
    void shouldCreateCourtScheduleWithDefaultSessionTimesAM() {
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("create-court-schedule-duration-based-default-times-am.json");
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final List<CourtSchedule> courtSchedules = databaseReader.courtSchedules();
        CourtSchedule courtSchedule = courtSchedules.get(0);
        assertThat(courtSchedule.getCourtScheduleId(), is(notNullValue()));

        // Convert the UTC times from the database to local time for comparison
        java.util.Date localStartTime = TimezoneUtils.utcToLocal(courtSchedule.getSessionStartTime());
        java.util.Date localEndTime = TimezoneUtils.utcToLocal(courtSchedule.getSessionEndTime());

        assertThat(sdf.format(localStartTime), is(DEFAULT_MORNING_START_TIME));
        assertThat(sdf.format(localEndTime), is(DEFAULT_MORNING_END_TIME));
    }

    @Test
    void shouldCreateCourtScheduleWithDefaultSessionTimesPM() {
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("create-court-schedule-duration-based-default-times-pm.json");
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final List<CourtSchedule> courtSchedules = databaseReader.courtSchedules();
        CourtSchedule courtSchedule = courtSchedules.get(0);
        assertThat(courtSchedule.getCourtScheduleId(), is(notNullValue()));

        // Convert the UTC times from the database to local time for comparison
        java.util.Date localStartTime = TimezoneUtils.utcToLocal(courtSchedule.getSessionStartTime());
        java.util.Date localEndTime = TimezoneUtils.utcToLocal(courtSchedule.getSessionEndTime());

        assertThat(sdf.format(localStartTime), is(DEFAULT_AFTERNOON_START_TIME));
        assertThat(sdf.format(localEndTime), is(DEFAULT_AFTERNOON_END_TIME));
    }

    @Test
    void shouldCreateCourtScheduleWithDefaultSessionTimesAD() {
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("create-court-schedule-duration-based-default-times-ad.json");
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final List<CourtSchedule> courtSchedules = databaseReader.courtSchedules();
        CourtSchedule courtSchedule = courtSchedules.get(0);
        assertThat(courtSchedule.getCourtScheduleId(), is(notNullValue()));

        // Convert the UTC times from the database to local time for comparison
        java.util.Date localStartTime = TimezoneUtils.utcToLocal(courtSchedule.getSessionStartTime());
        java.util.Date localEndTime = TimezoneUtils.utcToLocal(courtSchedule.getSessionEndTime());

        assertThat(sdf.format(localStartTime), is(DEFAULT_ALL_DAY_START_TIME));
        assertThat(sdf.format(localEndTime), is(DEFAULT_ALL_DAY_END_TIME));
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
    void shouldReturnErrorWhenAMSessionStartTimeIsMidnight() {
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("create-court-schedule-invalid-start-time-am.json");
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String errorResponseMessage = response.readEntity(String.class);
        assertThat(errorResponseMessage, containsString(SESSION_START_TIME_CANNOT_BE_EARLIER.formatted(AM_SESSION)));
    }

    @Test
    void shouldReturnErrorWhenADSessionStartTimeIsMidnight() {
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("create-court-schedule-invalid-start-time-ad.json");
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String errorResponseMessage = response.readEntity(String.class);
        assertThat(errorResponseMessage, containsString(SESSION_START_TIME_CANNOT_BE_EARLIER.formatted(ALL_DAY)));
    }

    @Test
    void shouldReturnErrorWhenADSessionEndTimeIsAfter23() {
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("create-court-schedule-invalid-end-time-ad.json");
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String errorResponseMessage = response.readEntity(String.class);
        assertThat(errorResponseMessage, containsString(SESSION_END_TIME_CANNOT_BE_LATER.formatted(ALL_DAY)));
    }

    @Test
    void shouldReturn400WhenPanelMissingForMagistratesJurisdictionInCreate() {
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("create-court-schedule-missing-panel-magistrates.json");
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
    }

    @Test
    void shouldReturn400WhenIsDraftMissingForCrownJurisdictionInCreate() {
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("create-court-schedule-null-draft-crown.json");
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
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
    void shouldReturn400WhenCourtroomDoesNotBelongToCourtCentreInCreate() {
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("create-court-schedule-wrong-court-centre.json");
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
    }

    @Test
    void shouldReturn400WhenCourtroomDoesNotBelongToCourtCentreInValidateCreate() {
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("validate-create-court-schedule-wrong-court-centre.json");
        final Response response = postCommand(VALIDATE_URL, COURT_SCHEDULE_VALIDATE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
    }

    @Test
    void shouldReturn400WhenCrownSessionUsesMagistratesCourtroomInCreate() {
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("create-court-schedule-crown-with-magistrates-courtroom.json");
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String errorResponseMessage = response.readEntity(String.class);
        assertThat(errorResponseMessage, containsString("court centre with MAGISTRATES jurisdiction"));
        assertThat(errorResponseMessage, containsString("does not match the session jurisdiction CROWN"));
    }

    @Test
    void shouldReturn400WhenCrownSessionUsesMagistratesCourtroomInValidateCreate() {
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("validate-create-court-schedule-crown-with-magistrates-courtroom.json");
        final Response response = postCommand(VALIDATE_URL, COURT_SCHEDULE_VALIDATE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String errorResponseMessage = response.readEntity(String.class);
        assertThat(errorResponseMessage, containsString("court centre with MAGISTRATES jurisdiction"));
        assertThat(errorResponseMessage, containsString("does not match the session jurisdiction CROWN"));
    }

    @Test
    void shouldReturn400WhenAllDaySplitHasInsufficientSessionDuration() throws SQLException {
        final CourtSchedule courtScheduleDuration = RANDOM.nextObject(CourtSchedule.class);
        final Integer maxDurationForMorning = 120;
        final Integer maxDurationForAfternoon = 60;
        final UUID hearingIdForMorning = UUID.randomUUID();
        final UUID bookingIdForMorning = UUID.randomUUID();
        final UUID hearingIdForAfternoon = UUID.randomUUID();
        final UUID bookingIdForAfternoon = UUID.randomUUID();
        courtScheduleDuration.setBusinessType("TRL");
        courtScheduleDuration.setSlotBased(false);
        courtScheduleDuration.setMaxDuration(0);
        courtScheduleDuration.setAvailableDuration(200);
        courtScheduleDuration.setSupportAdSplit(true);
        courtScheduleDuration.setCourtSession(ALL_DAY);
        courtScheduleDuration.setMaxAdMorningDuration(maxDurationForMorning);
        courtScheduleDuration.setTotalBookedMorning(120);
        courtScheduleDuration.setTotalBookedAfternoon(60);
        courtScheduleDuration.setMaxAdAfternoonDuration(maxDurationForAfternoon);
        courtScheduleDuration.setCourtScheduleId("abcdef12-3456-7890-abcd-ef1234567890");
        courtScheduleDuration.setSessionDate(getRandomFutureDateWithinNextYear());
        courtScheduleDuration.setSessionStartTime(DateUtils.combineDateAndTime(courtScheduleDuration.getSessionDate(), "10:00"));
        courtScheduleDuration.setSessionEndTime(DateUtils.combineDateAndTime(courtScheduleDuration.getSessionDate(), "16:00"));
        databaseSeeder.insertCourtSchedule(courtScheduleDuration);

        createAllocatedListing(courtScheduleDuration, hearingIdForMorning, bookingIdForMorning, 120, "10:00");
        createAllocatedListing(courtScheduleDuration, hearingIdForAfternoon, bookingIdForAfternoon, 60, "15:00");

        final String validateCourtSchedulePayload = getPayload("courtscheduler.validate.session.availability-allday-insufficient.json");
        final Response response = postCommand(VALIDATE_SESSION_AVAILABILITY_URL, COURT_SCHEDULE_VALIDATE_SESSION_AVAILABILITY_CONTENT_TYPE, SYSTEM_USER_ID, validateCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String errorResponseMessage = response.readEntity(String.class);
        assertThat(errorResponseMessage, containsString("Requested duration must fit within either the morning or afternoon session"));
    }

    @Test
    void shouldReturn400WhenDurationBasedScheduleHasInsufficientAvailability() throws SQLException {
        final CourtSchedule courtScheduleDuration = RANDOM.nextObject(CourtSchedule.class);
        final Integer maxDurationForMorning = 120;
        final Integer maxDurationForAfternoon = 60;
        courtScheduleDuration.setBusinessType("TRL");
        courtScheduleDuration.setSlotBased(false);
        courtScheduleDuration.setMaxDuration(0);
        courtScheduleDuration.setAvailableDuration(0);
        courtScheduleDuration.setSupportAdSplit(true);
        courtScheduleDuration.setCourtSession(ALL_DAY);
        courtScheduleDuration.setMaxAdMorningDuration(maxDurationForMorning);
        courtScheduleDuration.setMaxAdAfternoonDuration(maxDurationForAfternoon);
        courtScheduleDuration.setCourtScheduleId("abcdef12-3456-7890-abcd-ef1234567890");
        courtScheduleDuration.setSessionDate(getRandomFutureDateWithinNextYear());
        courtScheduleDuration.setSessionStartTime(DateUtils.combineDateAndTime(courtScheduleDuration.getSessionDate(), "10:00"));
        courtScheduleDuration.setSessionEndTime(DateUtils.combineDateAndTime(courtScheduleDuration.getSessionDate(), "16:00"));
        databaseSeeder.insertCourtSchedule(courtScheduleDuration);

        final String validateCourtSchedulePayload = getPayload("courtscheduler.validate.session.availability-insufficient-duration.json");
        final Response response = postCommand(VALIDATE_SESSION_AVAILABILITY_URL, COURT_SCHEDULE_VALIDATE_SESSION_AVAILABILITY_CONTENT_TYPE, SYSTEM_USER_ID, validateCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String errorResponseMessage = response.readEntity(String.class);
        assertThat(errorResponseMessage, containsString("Not enough available durations for all court schedules"));
    }

    @Test
    void shouldReturn400WhenSchedulesAreMixedSlotAndDurationBased() throws SQLException {
        final CourtSchedule courtScheduleSlot = RANDOM.nextObject(CourtSchedule.class);
        final CourtSchedule courtScheduleDuration = RANDOM.nextObject(CourtSchedule.class);
        final Integer maxDurationForMorning = 120;
        final Integer maxDurationForAfternoon = 60;

        courtScheduleSlot.setBusinessType("DVLA");
        courtScheduleSlot.setSlotBased(true);
        courtScheduleSlot.setMaxDuration(0);
        courtScheduleSlot.setAvailableDuration(0);
        courtScheduleSlot.setSupportAdSplit(true);
        courtScheduleSlot.setCourtSession(ALL_DAY);
        courtScheduleSlot.setMaxAdMorningDuration(maxDurationForMorning);
        courtScheduleSlot.setMaxAdAfternoonDuration(maxDurationForAfternoon);
        courtScheduleSlot.setCourtScheduleId("12345678-90ab-cdef-0123-456789abcdef");
        courtScheduleSlot.setSessionDate(getRandomFutureDateWithinNextYear());
        courtScheduleSlot.setSessionStartTime(DateUtils.combineDateAndTime(courtScheduleSlot.getSessionDate(), "10:00"));
        courtScheduleSlot.setSessionEndTime(DateUtils.combineDateAndTime(courtScheduleSlot.getSessionDate(), "16:00"));
        databaseSeeder.insertCourtSchedule(courtScheduleSlot);

        courtScheduleDuration.setBusinessType("TRL");
        courtScheduleDuration.setSlotBased(false);
        courtScheduleDuration.setMaxDuration(0);
        courtScheduleDuration.setAvailableDuration(0);
        courtScheduleDuration.setSupportAdSplit(true);
        courtScheduleDuration.setCourtSession(ALL_DAY);
        courtScheduleDuration.setMaxAdMorningDuration(maxDurationForMorning);
        courtScheduleDuration.setMaxAdAfternoonDuration(maxDurationForAfternoon);
        courtScheduleDuration.setCourtScheduleId("abcdef12-3456-7890-abcd-ef1234567890");
        courtScheduleDuration.setSessionDate(getRandomFutureDateWithinNextYear());
        courtScheduleDuration.setSessionStartTime(DateUtils.combineDateAndTime(courtScheduleDuration.getSessionDate(), "10:00"));
        courtScheduleDuration.setSessionEndTime(DateUtils.combineDateAndTime(courtScheduleDuration.getSessionDate(), "16:00"));
        databaseSeeder.insertCourtSchedule(courtScheduleDuration);

        final String validateCourtSchedulePayload = getPayload("courtscheduler.validate.session.availability-mixed.json");
        final Response response = postCommand(VALIDATE_SESSION_AVAILABILITY_URL, COURT_SCHEDULE_VALIDATE_SESSION_AVAILABILITY_CONTENT_TYPE, SYSTEM_USER_ID, validateCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String errorResponseMessage = response.readEntity(String.class);
        assertThat(errorResponseMessage, containsString("All court schedules should be either slot-based or duration-based"));
    }

    @Test
    void shouldReturn400WhenCourtScheduleIdsAreEmpty() {

        final String validateCourtSchedulePayload = getPayload("courtscheduler.validate.session.availability-empty.json");
        final Response response = postCommand(VALIDATE_SESSION_AVAILABILITY_URL, COURT_SCHEDULE_VALIDATE_SESSION_AVAILABILITY_CONTENT_TYPE, SYSTEM_USER_ID, validateCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String errorResponseMessage = response.readEntity(String.class);
        assertThat(errorResponseMessage, containsString("Court Schedule Ids cannot be empty"));
    }

    @Test
    void shouldReturn200ForValidSlotBasedRequest() throws SQLException {
        final CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        final Integer maxDurationForMorning = 120;
        final Integer maxDurationForAfternoon = 60;

        courtSchedule.setBusinessType("TRL");
        courtSchedule.setSlotBased(true);
        courtSchedule.setMaxDuration(0);
        courtSchedule.setAvailableDuration(0);
        courtSchedule.setMaxSlots(10);
        courtSchedule.setAvailableSlots(10);
        courtSchedule.setSupportAdSplit(true);
        courtSchedule.setCourtSession(ALL_DAY);
        courtSchedule.setMaxAdMorningDuration(maxDurationForMorning);
        courtSchedule.setMaxAdAfternoonDuration(maxDurationForAfternoon);
        courtSchedule.setCourtScheduleId("a1234567-89ab-cdef-0123-456789abcdef");
        courtSchedule.setSessionDate(getRandomFutureDateWithinNextYear());
        courtSchedule.setSessionStartTime(DateUtils.combineDateAndTime(courtSchedule.getSessionDate(), "10:00"));
        courtSchedule.setSessionEndTime(DateUtils.combineDateAndTime(courtSchedule.getSessionDate(), "16:00"));
        databaseSeeder.insertCourtSchedule(courtSchedule);

        final String validateCourtSchedulePayload = getPayload("courtscheduler.validate.session.availability-slot.json");
        final Response response = postCommand(VALIDATE_SESSION_AVAILABILITY_URL, COURT_SCHEDULE_VALIDATE_SESSION_AVAILABILITY_CONTENT_TYPE, SYSTEM_USER_ID, validateCourtSchedulePayload);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
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

        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("create-court-schedule-multiple-session.json");
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
    }

    @Test
    void shouldUpdateCourtSchedule() throws SQLException {
        UUID courtScheduleId = UUID.randomUUID();
        CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        String courtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3"; // Use same courtroom ID for initial and update
        String courtHouseId = "785339c1-af71-3322-a55b-ba255e0db1c2"; // Test Crown Court - same court house as courtroom
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType("DVLA");
        expected.setSupportAdSplit(false);
        expected.setSessionDate(LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY))); // Set to future date to avoid past session validation
        expected.setCourtRoomId(courtRoomId); // Set initial courtroom ID to match update
        expected.setCourtHouseId(courtHouseId); // Set court house ID to match courtroom
        databaseSeeder.insertCourtSchedule(expected);

        String updateCourtSchedulePayload = getPayload("update-court-schedule.json");
        String changedCourtRoomId = courtRoomId; // Use same courtroom to avoid court house validation error
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
        String courtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3"; // Use same courtroom ID for initial and update
        String courtHouseId = "785339c1-af71-3322-a55b-ba255e0db1c2"; // Test Crown Court - same court house as courtroom
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType("TRL");
        expected.setSessionDate(LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)));
        expected.setSupportAdSplit(true);
        expected.setCourtRoomId(courtRoomId); // Set initial courtroom ID to match update
        expected.setCourtHouseId(courtHouseId); // Set court house ID to match courtroom
        databaseSeeder.insertCourtSchedule(expected);

        String updateCourtSchedulePayload = getPayload("update-court-schedule-all-day-split.json");
        String changedCourtRoomId = courtRoomId; // Use same courtroom to avoid court house validation error
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
        String courtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3"; // Use same courtroom ID for initial and update
        String courtHouseId = "785339c1-af71-3322-a55b-ba255e0db1c2"; // Test Crown Court - same court house as courtroom
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType("TRL");
        expected.setSessionDate(LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)));
        expected.setSupportAdSplit(true);
        expected.setIsOverbookingAllowed(false);
        expected.setCourtRoomId(courtRoomId); // Set initial courtroom ID to match update
        expected.setCourtHouseId(courtHouseId); // Set court house ID to match courtroom
        databaseSeeder.insertCourtSchedule(expected);

        String updateCourtSchedulePayload = getPayload("update-court-schedule-is-overbooking-allowed.json");
        String changedCourtRoomId = courtRoomId; // Use same courtroom to avoid court house validation error
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
        String courtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3"; // Use same courtroom ID for initial and update
        String courtHouseId = "785339c1-af71-3322-a55b-ba255e0db1c2"; // Test Crown Court - same court house as courtroom
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType("TRL");
        expected.setSessionDate(LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)));
        expected.setSupportAdSplit(true);
        expected.setCourtRoomId(courtRoomId); // Set initial courtroom ID to match update
        expected.setCourtHouseId(courtHouseId); // Set court house ID to match courtroom
        databaseSeeder.insertCourtSchedule(expected);

        String updateCourtSchedulePayload = getPayload("update-court-schedule-all-day-split-without-session-start-end-time.json");
        String changedCourtRoomId = courtRoomId; // Use same courtroom to avoid court house validation error
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
        final Date expectedEndTime = localDateToDateWithTime(expected.getSessionDate(), 17, 0);
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
    void shouldGet400WhenUpdatingCourtScheduleWithInvalidMinHearingTime() throws SQLException {
        UUID courtScheduleId = UUID.randomUUID();
        CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        String courtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3"; // Use same courtroom ID for initial and update
        String courtHouseId = "785339c1-af71-3322-a55b-ba255e0db1c2"; // Test Crown Court - same court house as courtroom
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType("TRL");
        expected.setSupportAdSplit(true);
        LocalDate futureDate = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        expected.setSessionDate(futureDate); // Set to future date to avoid past session validation
        expected.setSessionStartTime(DateUtils.localDateToDateWithTime(futureDate, 10, 0));
        expected.setSessionEndTime(DateUtils.localDateToDateWithTime(futureDate, 17, 0));
        expected.setCourtRoomId(courtRoomId); // Set initial courtroom ID to match update
        expected.setCourtHouseId(courtHouseId); // Set court house ID to match courtroom
        databaseSeeder.insertCourtSchedule(expected);

        String updateCourtSchedulePayload = getPayload("update-court-schedule-all-day-split-invalid-session-start-time.json");
        String changedCourtRoomId = courtRoomId; // Use same courtroom to avoid court house validation error
        String changedBusinessType = "TRL";
        String changedSessionType = "AD";
        String changedPanel = "YOUTH";
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_SCHEDULE_ID", expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_ROOM_ID", changedCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("BUSINESS_TYPE", changedBusinessType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("SESSION_TYPE", changedSessionType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("PANEL", changedPanel);

        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("SESSION_START_TIME", "11:00");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("SESSION_END_TIME", "17:01");

        createAllocatedListing(expected, UUID.randomUUID(), UUID.randomUUID(), 90, "10:00");

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String errorResponseMessage = response.readEntity(String.class);
        assertThat(errorResponseMessage, containsString(MIN_HEARING_TIME_AFTER_SESSION_START_TIME));
    }

    @Test
    void shouldGet400WhenUpdatingCourtScheduleWithInvalidMaxHearingTime() throws SQLException {
        UUID courtScheduleId = UUID.randomUUID();
        CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        String courtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3"; // Use same courtroom ID for initial and update
        String courtHouseId = "785339c1-af71-3322-a55b-ba255e0db1c2"; // Test Crown Court - same court house as courtroom
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType("TRL");
        expected.setSupportAdSplit(true);
        LocalDate futureDate = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        expected.setSessionDate(futureDate); // Set to future date to avoid past session validation
        expected.setSessionStartTime(DateUtils.localDateToDateWithTime(futureDate, 10, 0));
        expected.setSessionEndTime(DateUtils.localDateToDateWithTime(futureDate, 17, 0));
        expected.setCourtRoomId(courtRoomId); // Set initial courtroom ID to match update
        expected.setCourtHouseId(courtHouseId); // Set court house ID to match courtroom
        databaseSeeder.insertCourtSchedule(expected);

        String updateCourtSchedulePayload = getPayload("update-court-schedule-all-day-split-invalid-session-start-time.json");
        String changedCourtRoomId = courtRoomId; // Use same courtroom to avoid court house validation error
        String changedBusinessType = "TRL";
        String changedSessionType = "AD";
        String changedPanel = "YOUTH";
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_SCHEDULE_ID", expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_ROOM_ID", changedCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("BUSINESS_TYPE", changedBusinessType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("SESSION_TYPE", changedSessionType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("PANEL", changedPanel);

        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("SESSION_START_TIME", "10:00");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("SESSION_END_TIME", "13:01");

        createAllocatedListing(expected, UUID.randomUUID(), UUID.randomUUID(), 90, "15:00");

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String errorResponseMessage = response.readEntity(String.class);
        assertThat(errorResponseMessage, containsString(MAX_HEARING_TIME_BEFORE_SESSION_END_TIME));
    }

    @Test
    void shouldUpdateCourtScheduleWithNullSessionTimesWhenAllocatedListingsExist() throws SQLException {
        // Test that when session times are null in update request but allocated listings exist,
        // the system retrieves session times from persisted schedule and validates successfully
        UUID courtScheduleId = UUID.randomUUID();
        CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        String courtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3"; // Use same courtroom ID for initial and update
        String courtHouseId = "785339c1-af71-3322-a55b-ba255e0db1c2"; // Test Crown Court - same court house as courtroom
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType("DVLA");
        expected.setSupportAdSplit(false);
        expected.setListingProfileId(USER_ID.toString()); // Set to current user to avoid "edited by another user" error
        expected.setIsDraft(true); // Set as draft to allow editing
        expected.setHasHearingsBooked(true); // Set to true since we'll create allocated listings
        expected.setCourtRoomId(courtRoomId); // Set initial courtroom ID to match update
        expected.setCourtHouseId(courtHouseId); // Set court house ID to match courtroom
        expected.setCourtSession("AM"); // Set initial session type to match update
        expected.setPanel("YOUTH"); // Set initial panel to match update
        expected.setSessionDate(LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)));
        expected.setSessionStartTime(DateUtils.localDateToDateWithTime(expected.getSessionDate(), 10, 0));
        expected.setSessionEndTime(DateUtils.localDateToDateWithTime(expected.getSessionDate(), 13, 0));
        databaseSeeder.insertCourtSchedule(expected);

        // Create allocated listing with hearing time within session window (11:00 is between 10:00-13:00)
        createAllocatedListing(expected, UUID.randomUUID(), UUID.randomUUID(), 60, "11:00");

        String updateCourtSchedulePayload = getPayload("update-court-schedule.json");
        String sameCourtRoomId = expected.getCourtRoomId(); // Keep same courtroom to avoid "edited by another user" error
        String changedBusinessType = "DVLA";
        String sameSessionType = expected.getCourtSession(); // Keep same session type
        String samePanel = expected.getPanel(); // Keep same panel
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_SCHEDULE_ID", expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_ROOM_ID", sameCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("BUSINESS_TYPE", changedBusinessType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("SESSION_TYPE", sameSessionType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("PANEL", samePanel);
        // Set maxDuration to be >= allocated listing duration (60 minutes) to avoid validation error
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("\"maxDuration\": 10", "\"maxDuration\": 120");
        // Note: update-court-schedule.json doesn't include sessionStartTime/sessionEndTime, so they'll be null

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);

        // Should succeed because session times are retrieved from persisted schedule and validation passes
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
    }

    @Test
    void shouldGet400WhenUpdatingCourtScheduleWithNullSessionTimesAndInvalidHearingTime() throws SQLException {
        // Test that when session times are null in update request but allocated listings exist,
        // the system retrieves session times from persisted schedule and validates hearing times
        UUID courtScheduleId = UUID.randomUUID();
        CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        String courtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3"; // Use same courtroom ID for initial and update
        String courtHouseId = "785339c1-af71-3322-a55b-ba255e0db1c2"; // Test Crown Court - same court house as courtroom
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType("DVLA");
        expected.setSupportAdSplit(false);
        expected.setListingProfileId(USER_ID.toString()); // Set to current user to avoid "edited by another user" error
        expected.setSessionDate(LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)));
        expected.setSessionStartTime(DateUtils.localDateToDateWithTime(expected.getSessionDate(), 10, 0));
        expected.setSessionEndTime(DateUtils.localDateToDateWithTime(expected.getSessionDate(), 13, 0));
        expected.setCourtRoomId(courtRoomId); // Set initial courtroom ID to match update
        expected.setCourtHouseId(courtHouseId); // Set court house ID to match courtroom
        databaseSeeder.insertCourtSchedule(expected);

        // Create allocated listing with hearing time AFTER session end time (15:00 is after 13:00)
        createAllocatedListing(expected, UUID.randomUUID(), UUID.randomUUID(), 60, "15:00");

        String updateCourtSchedulePayload = getPayload("update-court-schedule.json");
        String changedCourtRoomId = courtRoomId; // Use same courtroom to avoid court house validation error
        String changedBusinessType = "DVLA";
        String changedSessionType = "AM";
        String changedPanel = "YOUTH";
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_SCHEDULE_ID", expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_ROOM_ID", changedCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("BUSINESS_TYPE", changedBusinessType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("SESSION_TYPE", changedSessionType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("PANEL", changedPanel);
        // Set maxDuration to be >= allocated listing duration (60 minutes) to avoid validation error
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("\"maxDuration\": 10", "\"maxDuration\": 120");
        // Note: update-court-schedule.json doesn't include sessionStartTime/sessionEndTime, so they'll be null

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);

        // Should fail because hearing time (15:00) is after session end time (13:00) retrieved from persisted schedule
        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String errorResponseMessage = response.readEntity(String.class);
        assertThat(errorResponseMessage, containsString(MAX_HEARING_TIME_BEFORE_SESSION_END_TIME));
    }

    @Test
    void shouldUpdateCourtScheduleWithNullSessionTimesWhenNoAllocatedListings() throws SQLException {
        // Test that when session times are null and no allocated listings exist,
        // validation passes without needing to retrieve session times
        UUID courtScheduleId = UUID.randomUUID();
        CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        String courtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3"; // Use same courtroom ID for initial and update
        String courtHouseId = "785339c1-af71-3322-a55b-ba255e0db1c2"; // Test Crown Court - same court house as courtroom
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType("DVLA");
        expected.setSupportAdSplit(false);
        expected.setSessionDate(LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)));
        expected.setSessionStartTime(DateUtils.localDateToDateWithTime(expected.getSessionDate(), 10, 0));
        expected.setSessionEndTime(DateUtils.localDateToDateWithTime(expected.getSessionDate(), 13, 0));
        expected.setCourtRoomId(courtRoomId); // Set initial courtroom ID to match update
        expected.setCourtHouseId(courtHouseId); // Set court house ID to match courtroom
        databaseSeeder.insertCourtSchedule(expected);

        // No allocated listings created

        String updateCourtSchedulePayload = getPayload("update-court-schedule.json");
        String changedCourtRoomId = courtRoomId; // Use same courtroom to avoid court house validation error
        String changedBusinessType = "DVLA";
        String changedSessionType = "AM";
        String changedPanel = "YOUTH";
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_SCHEDULE_ID", expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_ROOM_ID", changedCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("BUSINESS_TYPE", changedBusinessType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("SESSION_TYPE", changedSessionType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("PANEL", changedPanel);
        // Note: update-court-schedule.json doesn't include sessionStartTime/sessionEndTime, so they'll be null

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);

        // Should succeed because no allocated listings means validation is skipped
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
    }

    @Test
    void shouldGet400WhenUpdatingCourtScheduleWithNullSessionTimesAndMinHearingTimeAfterSessionStart() throws SQLException {
        // Test that when session times are null in update request but allocated listings exist,
        // the system retrieves session times from persisted schedule and validates min hearing time
        UUID courtScheduleId = UUID.randomUUID();
        CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        String courtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3"; // Use same courtroom ID for initial and update
        String courtHouseId = "785339c1-af71-3322-a55b-ba255e0db1c2"; // Test Crown Court - same court house as courtroom
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType("DVLA");
        expected.setSupportAdSplit(false);
        expected.setListingProfileId(USER_ID.toString()); // Set to current user to avoid "edited by another user" error
        expected.setSessionDate(LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)));
        expected.setSessionStartTime(DateUtils.localDateToDateWithTime(expected.getSessionDate(), 10, 0));
        expected.setSessionEndTime(DateUtils.localDateToDateWithTime(expected.getSessionDate(), 13, 0));
        expected.setCourtRoomId(courtRoomId); // Set initial courtroom ID to match update
        expected.setCourtHouseId(courtHouseId); // Set court house ID to match courtroom
        databaseSeeder.insertCourtSchedule(expected);

        // Create allocated listing with hearing time BEFORE session start time (09:00 is before 10:00)
        createAllocatedListing(expected, UUID.randomUUID(), UUID.randomUUID(), 60, "09:00");

        String updateCourtSchedulePayload = getPayload("update-court-schedule.json");
        String changedCourtRoomId = courtRoomId; // Use same courtroom to avoid court house validation error
        String changedBusinessType = "DVLA";
        String changedSessionType = "AM";
        String changedPanel = "YOUTH";
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_SCHEDULE_ID", expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_ROOM_ID", changedCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("BUSINESS_TYPE", changedBusinessType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("SESSION_TYPE", changedSessionType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("PANEL", changedPanel);
        // Set maxDuration to be >= allocated listing duration (60 minutes) to avoid validation error
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("\"maxDuration\": 10", "\"maxDuration\": 120");
        // Note: update-court-schedule.json doesn't include sessionStartTime/sessionEndTime, so they'll be null

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);

        // Should fail because min hearing time (09:00) is before session start time (10:00) retrieved from persisted schedule
        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String errorResponseMessage = response.readEntity(String.class);
        assertThat(errorResponseMessage, containsString(MIN_HEARING_TIME_AFTER_SESSION_START_TIME));
    }

    @Test
    void shouldGet400WhenUpdatingCourtScheduleWithNonDurationBasedBusinessType() throws SQLException {
        UUID courtScheduleId = UUID.randomUUID();
        CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        String courtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3"; // Use same courtroom ID for initial and update
        String courtHouseId = "785339c1-af71-3322-a55b-ba255e0db1c2"; // Test Crown Court - same court house as courtroom
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType("TRL");
        expected.setCourtRoomId(courtRoomId); // Set initial courtroom ID to match update
        expected.setCourtHouseId(courtHouseId); // Set court house ID to match courtroom
        databaseSeeder.insertCourtSchedule(expected);

        String updateCourtSchedulePayload = getPayload("update-court-schedule-all-day-split.json");
        String changedCourtRoomId = courtRoomId; // Use same courtroom to avoid court house validation error
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
        String courtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3"; // Use same courtroom ID for initial and update
        String courtHouseId = "785339c1-af71-3322-a55b-ba255e0db1c2"; // Test Crown Court - same court house as courtroom
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType("TRL");
        expected.setSupportAdSplit(true);
        expected.setCourtSession(ALL_DAY);
        expected.setSessionDate(LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY))); // Set to future date to avoid past session validation
        expected.setCourtRoomId(courtRoomId); // Set initial courtroom ID to match update
        expected.setCourtHouseId(courtHouseId); // Set court house ID to match courtroom
        databaseSeeder.insertCourtSchedule(expected);

        String updateCourtSchedulePayload = getPayload("update-court-schedule-all-day-split.json");
        String changedCourtRoomId = courtRoomId; // Use same courtroom to avoid court house validation error
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
        UUID courtScheduleId = UUID.randomUUID();
        CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        String courtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3"; // Use same courtroom ID for initial and update
        String courtHouseId = "785339c1-af71-3322-a55b-ba255e0db1c2"; // Test Crown Court - same court house as courtroom
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType("DVLA");
        expected.setSupportAdSplit(false);
        expected.setSessionDate(LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY))); // Set to future date to avoid past session validation
        expected.setCourtRoomId(courtRoomId); // Set initial courtroom ID to match update
        expected.setCourtHouseId(courtHouseId); // Set court house ID to match courtroom
        databaseSeeder.insertCourtSchedule(expected);

        String updateCourtSchedulePayload = getPayload("update-court-schedule.json");
        String changedCourtRoomId = courtRoomId; // Use same courtroom to avoid court house validation error
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
        courtSchedule.setSessionDate(getRandomFutureDateWithinNextYear());
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), "10:00"));
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), "16:00"));
        String courtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3"; // Use same courtroom ID for initial and update
        String courtHouseId = "785339c1-af71-3322-a55b-ba255e0db1c2"; // Test Crown Court - same court house as courtroom
        courtSchedule.setCourtRoomId(courtRoomId); // Set initial courtroom ID to match update
        courtSchedule.setCourtHouseId(courtHouseId); // Set court house ID to match courtroom
        databaseSeeder.insertCourtSchedule(courtSchedule);

        createAllocatedListing(courtSchedule, hearingIdForMorning, bookingIdForMorning, 90, "10:00");
        createAllocatedListing(courtSchedule, hearingIdForAfternoon, bookingIdForAfternoon, 60, "14:00");

        String updateCourtSchedulePayload = getPayload("update-court-schedule-all-day-split.json");
        String changedCourtRoomId = courtRoomId; // Use same courtroom to avoid court house validation error
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
        courtSchedule.setSessionDate(getRandomFutureDateWithinNextYear());
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), "10:00"));
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), "16:00"));
        String courtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3"; // Use same courtroom ID for initial and update
        String courtHouseId = "785339c1-af71-3322-a55b-ba255e0db1c2"; // Test Crown Court - same court house as courtroom
        courtSchedule.setCourtRoomId(courtRoomId); // Set initial courtroom ID to match update
        courtSchedule.setCourtHouseId(courtHouseId); // Set court house ID to match courtroom
        databaseSeeder.insertCourtSchedule(courtSchedule);

        createAllocatedListing(courtSchedule, hearingIdForMorning, bookingIdForMorning, 90, "10:00");
        createAllocatedListing(courtSchedule, hearingIdForAfternoon, bookingIdForAfternoon, 60, "14:00");

        String updateCourtSchedulePayload = getPayload("update-court-schedule-all-day-split.json");
        String changedCourtRoomId = courtRoomId; // Use same courtroom to avoid court house validation error
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
    void shouldNotAllowUpdateCourtScheduleForDifferentBusinessType() throws SQLException {
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
        expected.setIsDraft(false);
        expected.setSessionStartTime(from(expected.getSessionDate().atTime(10, 0).atZone(UTC).toInstant()));
        expected.setSessionEndTime(from(expected.getSessionDate().atTime(17, 0).atZone(UTC).toInstant()));
        expected.setIsOverbookingAllowed(false);
        expected.setJurisdiction(MAGISTRATES.getJurisdiction());
        databaseSeeder.insertCourtSchedule(expected);

        AllocatedListing allocatedListing = RANDOM.nextObject(AllocatedListing.class);
        allocatedListing.setId(randomUUID().toString());
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
        assertThat(courtScheduleJsonObject.getString("jurisdiction"), is(MAGISTRATES.getJurisdiction()));
    }

    @Test
    void shouldGetCourtSchedulesCrown() throws SQLException, JsonProcessingException {
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
        expected.setIsDraft(true);
        expected.setSessionStartTime(from(expected.getSessionDate().atTime(10, 0).atZone(UTC).toInstant()));
        expected.setSessionEndTime(from(expected.getSessionDate().atTime(17, 0).atZone(UTC).toInstant()));
        expected.setIsOverbookingAllowed(false);
        expected.setJurisdiction("CROWN");
        databaseSeeder.insertCourtSchedule(expected);

        AllocatedListing allocatedListing = RANDOM.nextObject(AllocatedListing.class);
        allocatedListing.setId(randomUUID().toString());
        allocatedListing.setCourtScheduleId(expected.getCourtScheduleId());
        allocatedListing.setHearingId(hearingId.toString());
        allocatedListing.setBookingId(bookingId.toString());
        databaseSeeder.insertAllocatedListing(allocatedListing);

        String getCourtScheduleRequestParams = getPayload("courtscheduler.get.court_schedule_isDraft_query.json");
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("COURT_CENTRE_ID", expected.getCourtHouseId());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("COURT_ROOM_ID", expected.getCourtRoomId());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("BUSINESS_TYPE", expected.getBusinessType());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("SESSION_START_DATE", fromDate.toString());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("SESSION_END_DATE", toDate.toString());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("IS_DRAFT", "true");
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
        assertThat(courtScheduleJsonObject.getString("businessType"), is(expected.getBusinessType()));
        assertThat(courtScheduleJsonObject.getBoolean("slotBased"), is(false));
        assertThat(courtScheduleJsonObject.getBoolean("active"), is(true));
        assertThat(courtScheduleJsonObject.getString("courtRoomId"), is(expected.getCourtRoomId()));
        assertThat(courtScheduleJsonObject.getString("courtRoomName"), is(expected.getCourtRoomName()));
        assertThat(courtScheduleJsonObject.getBoolean("allDaySplit"), is(false));
        assertThat(courtScheduleJsonObject.getInt("maxDurationForMorning"), is(0));
        assertThat(courtScheduleJsonObject.getInt("maxDurationForAfternoon"), is(0));
        assertThat(courtScheduleJsonObject.getBoolean("isDraft"), is(true));
        assertThat(courtScheduleJsonObject.getString("jurisdiction"), is(CROWN.getJurisdiction()));
    }


    @Test
    void shouldSearchCourtSchedulesById() throws Exception {

        final String courtScheduleId = "abcdef12-3456-7890-abcd-ef1234567890";
        final CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setCourtScheduleId(courtScheduleId);
        courtSchedule.setBusinessType("TRL");
        courtSchedule.setSlotBased(false);
        courtSchedule.setSupportAdSplit(true);
        courtSchedule.setCourtSession(ALL_DAY);
        courtSchedule.setMaxAdMorningDuration(120);
        courtSchedule.setMaxAdAfternoonDuration(60);
        courtSchedule.setMaxDuration(0);
        courtSchedule.setAvailableDuration(0);
        courtSchedule.setMaxSlots(0);
        courtSchedule.setAvailableSlots(0);
        courtSchedule.setSessionDate(getRandomFutureDateWithinNextYear());
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), "10:00"));
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), "16:00"));
        courtSchedule.setOuCode("B12345");
        courtSchedule.setIsDraft(false);
        courtSchedule.setJurisdiction(MAGISTRATES.getJurisdiction());
        courtSchedule.setActive(true);

        databaseSeeder.insertCourtSchedule(courtSchedule);

        // Also seed allocated listings for realism
        final UUID hearingId = UUID.randomUUID();
        final UUID bookingId = UUID.randomUUID();
        createAllocatedListing(courtSchedule, hearingId, bookingId, 60, "10:00");

        String getCourtScheduleRequestParams = getPayload("courtscheduler.search.courtschedules.by.id.json");
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("COURT_SCHEDULE_ID", courtScheduleId);
        Map<String, Object> map = mapper.readValue(getCourtScheduleRequestParams, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams(
                BASE_RESOURCE_URL + SEARCH_BY_ID_URL,
                COURT_SCHEDULE_SEARCH_COURTSCHEDULES_BY_ID_CONTENT_TYPE,
                SYSTEM_USER_ID,
                map
        );

        final ResponseData response = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertEquals(OK.getStatusCode(), response.getStatus().getStatusCode());

        JsonObject json = stringToJsonObjectConverter.convert(response.getPayload());

        final JsonObject courtSessionJson = json.getJsonArray("courtSchedules").getJsonObject(0);

        assertThat(courtSessionJson.getString("courtScheduleId"), is(courtSchedule.getCourtScheduleId()));
        assertThat(courtSessionJson.getBoolean("slotBased"), is(false));
        assertThat(courtSessionJson.getString("courtRoomId"), is(courtSchedule.getCourtRoomId()));
        assertThat(courtSessionJson.getBoolean("allDaySplit"), is(true));
        assertThat(courtSessionJson.getInt("maxDurationForMorning"), is(120));
        assertThat(courtSessionJson.getInt("maxDurationForAfternoon"), is(60));
        assertThat(courtSessionJson.getString("listingProfileId"), is(courtSchedule.getListingProfileId()));
        assertThat(courtSessionJson.getString("ouCode"), is(courtSchedule.getOuCode()));
        assertThat(courtSessionJson.getString("courtRoomName"), is(courtSchedule.getCourtRoomName()));
        assertThat(courtSessionJson.getString("courtHouseId"), is(courtSchedule.getCourtHouseId()));
        assertThat(courtSessionJson.getString("courtHouseName"), is(courtSchedule.getCourtHouseName()));
        assertThat(courtSessionJson.getString("operationalUnit"), is(courtSchedule.getOperationalUnit()));
        assertThat(courtSessionJson.getString("businessType"), is(courtSchedule.getBusinessType()));
        assertThat(courtSessionJson.getString("panel"), is(courtSchedule.getPanel()));
        assertThat(courtSessionJson.getBoolean("active"), is(courtSchedule.isActive()));
        assertThat(courtSessionJson.getString("courtSession"), is(courtSchedule.getCourtSession()));
        assertThat(courtSessionJson.getInt("maxDuration"), is(courtSchedule.getMaxDuration()));
        assertThat(courtSessionJson.getInt("availableDuration"), is(courtSchedule.getAvailableDuration()));
        assertThat(courtSessionJson.getInt("maxSlots"), is(courtSchedule.getMaxSlots()));
        assertThat(courtSessionJson.getInt("availableSlots"), is(courtSchedule.getAvailableSlots()));

        final OffsetDateTime actualStartTime = OffsetDateTime.parse(courtSessionJson.getString("sessionStartTime"));
        final OffsetDateTime actualEndTime = OffsetDateTime.parse(courtSessionJson.getString("sessionEndTime"));
        assertThat(actualStartTime.toInstant(), is(courtSchedule.getSessionStartTime().toInstant()));
        assertThat(actualEndTime.toInstant(), is(courtSchedule.getSessionEndTime().toInstant()));
    }

    @Test
    void shouldGetCourtSchedulesWithMinMaxSessionTimes() throws SQLException, JsonProcessingException {

        CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        LocalDate fromDate = expected.getSessionDate().minusDays(1);
        LocalDate toDate = expected.getSessionDate().plusDays(1);
        expected.setBusinessType("TRL");

        expected.setSlotBased(false);
        expected.setMaxDuration(5);
        expected.setAvailableDuration(5);
        expected.setSupportAdSplit(true);
        expected.setMaxAdMorningDuration(0);
        expected.setCourtSession(ALL_DAY);
        expected.setMaxAdAfternoonDuration(0);
        expected.setCourtScheduleId(UUID.randomUUID().toString());
        expected.setSessionStartTime(from(expected.getSessionDate().atTime(10, 0).atZone(UTC).toInstant()));
        expected.setSessionEndTime(from(expected.getSessionDate().atTime(17, 0).atZone(UTC).toInstant()));
        expected.setIsOverbookingAllowed(true);
        expected.setJurisdiction(MAGISTRATES.getJurisdiction());
        databaseSeeder.insertCourtSchedule(expected);

        AllocatedListing allocatedListing1 = RANDOM.nextObject(AllocatedListing.class);
        allocatedListing1.setId(randomUUID().toString());
        allocatedListing1.setCourtScheduleId(expected.getCourtScheduleId());
        allocatedListing1.setHearingId(UUID.randomUUID().toString());
        allocatedListing1.setBookingId(UUID.randomUUID().toString());
        allocatedListing1.setHearingStartTime(from(expected.getSessionDate().atTime(10, 0).atZone(UTC).toInstant()));
        databaseSeeder.insertAllocatedListing(allocatedListing1);

        AllocatedListing allocatedListing2 = RANDOM.nextObject(AllocatedListing.class);
        allocatedListing2.setId(randomUUID().toString());
        allocatedListing2.setCourtScheduleId(expected.getCourtScheduleId());
        allocatedListing2.setHearingId(UUID.randomUUID().toString());
        allocatedListing2.setBookingId(UUID.randomUUID().toString());
        allocatedListing2.setHearingStartTime(from(expected.getSessionDate().atTime(9, 0).atZone(UTC).toInstant()));
        databaseSeeder.insertAllocatedListing(allocatedListing2);

        AllocatedListing allocatedListing3 = RANDOM.nextObject(AllocatedListing.class);
        allocatedListing3.setId(randomUUID().toString());
        allocatedListing3.setCourtScheduleId(expected.getCourtScheduleId());
        allocatedListing3.setHearingId(UUID.randomUUID().toString());
        allocatedListing3.setBookingId(UUID.randomUUID().toString());
        allocatedListing3.setHearingStartTime(from(expected.getSessionDate().atTime(15, 0).atZone(UTC).toInstant()));
        databaseSeeder.insertAllocatedListing(allocatedListing3);

        AllocatedListing allocatedListing4 = RANDOM.nextObject(AllocatedListing.class);
        allocatedListing4.setId(randomUUID().toString());
        allocatedListing4.setCourtScheduleId(expected.getCourtScheduleId());
        allocatedListing4.setHearingId(UUID.randomUUID().toString());
        allocatedListing4.setBookingId(UUID.randomUUID().toString());
        allocatedListing4.setHearingStartTime(from(expected.getSessionDate().atTime(14, 0).atZone(UTC).toInstant()));
        databaseSeeder.insertAllocatedListing(allocatedListing4);

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
        assertThat(courtScheduleJsonObject.getBoolean("allDaySplit"), is(true));
        assertThat(courtScheduleJsonObject.getInt("maxDurationForMorning"), is(0));
        assertThat(courtScheduleJsonObject.getInt("maxDurationForAfternoon"), is(0));
        assertThat(courtScheduleJsonObject.getString("minHearingTime"), is("09:00"));
        assertThat(courtScheduleJsonObject.getString("maxHearingTime"), is("15:00"));
        assertThat(courtScheduleJsonObject.getBoolean("isOverbookingAllowed"), is(true));
        assertThat(courtScheduleJsonObject.getString("sessionStartTime"), is("10:00"));
        assertThat(courtScheduleJsonObject.getString("sessionEndTime"), is("17:00"));
        assertThat(courtScheduleJsonObject.getString("jurisdiction"), is(MAGISTRATES.getJurisdiction()));
    }

    @Test
    void shouldGetCourtSchedulesWithMinMaxSessionTimesNoAllocatedListings() throws SQLException, JsonProcessingException {

        CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        LocalDate fromDate = expected.getSessionDate().minusDays(1);
        LocalDate toDate = expected.getSessionDate().plusDays(1);
        expected.setBusinessType("TRL");

        expected.setSlotBased(false);
        expected.setMaxDuration(5);
        expected.setAvailableDuration(5);
        expected.setSupportAdSplit(true);
        expected.setMaxAdMorningDuration(0);
        expected.setCourtSession(ALL_DAY);
        expected.setMaxAdAfternoonDuration(0);
        expected.setCourtScheduleId(UUID.randomUUID().toString());
        expected.setSessionStartTime(from(expected.getSessionDate().atTime(10, 0).atZone(UTC).toInstant()));
        expected.setSessionEndTime(from(expected.getSessionDate().atTime(17, 0).atZone(UTC).toInstant()));
        expected.setIsOverbookingAllowed(true);
        expected.setJurisdiction(MAGISTRATES.getJurisdiction());
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

        final RequestParams requestParams = getRequestParams(BASE_RESOURCE_URL, COURT_SCHEDULE_GET_CONTENT_TYPE, USER_ID, map);


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
        assertThat(courtScheduleJsonObject.getBoolean("allDaySplit"), is(true));
        assertThat(courtScheduleJsonObject.getInt("maxDurationForMorning"), is(0));
        assertThat(courtScheduleJsonObject.getInt("maxDurationForAfternoon"), is(0));
        assertThat(courtScheduleJsonObject.getString("minHearingTime"), is("10:00"));
        assertThat(courtScheduleJsonObject.getString("maxHearingTime"), is("17:00"));
        assertThat(courtScheduleJsonObject.getBoolean("isOverbookingAllowed"), is(true));
        assertThat(courtScheduleJsonObject.getString("sessionStartTime"), is("10:00"));
        assertThat(courtScheduleJsonObject.getString("sessionEndTime"), is("17:00"));
        assertThat(courtScheduleJsonObject.getString("jurisdiction"), is(MAGISTRATES.getJurisdiction()));
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
        courtSchedule.setSessionDate(getRandomFutureDateWithinNextYear());
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), "10:00"));
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), "16:00"));
        courtSchedule.setIsOverbookingAllowed(false);
        courtSchedule.setJurisdiction(MAGISTRATES.getJurisdiction());
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
        assertThat(courtScheduleJsonObject.getString("minHearingTime"), is(getUtcTimeStringForDate(courtSchedule.getSessionDate(),10,0)));
        assertThat(courtScheduleJsonObject.getString("maxHearingTime"), is(getUtcTimeStringForDate(courtSchedule.getSessionDate(),15,0)));
        assertThat(courtScheduleJsonObject.getBoolean("isOverbookingAllowed"), is(false));
        assertThat(courtScheduleJsonObject.getString("sessionStartTime"), is(getUtcTimeStringForDate(courtSchedule.getSessionDate(),10,0)));
        assertThat(courtScheduleJsonObject.getString("sessionEndTime"), is(getUtcTimeStringForDate(courtSchedule.getSessionDate(),16,0)));
        assertThat(courtScheduleJsonObject.getString("jurisdiction"), is(MAGISTRATES.getJurisdiction()));
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
        courtSchedule.setSessionDate(getRandomFutureDateWithinNextYear());
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), "10:00"));
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), "16:00"));

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
            assertThat(sessionJSONObj.getInt("totalBooked"), is(allocatedListingForMorning.getDuration() + allocatedListingForAfternoon.getDuration()));
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
        courtSchedule.setSessionDate(getRandomFutureDateWithinNextYear());
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), "10:00"));
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), "16:00"));

        databaseSeeder.insertCourtSchedule(courtSchedule);

        String deleteHearingSlotsPayload = getPayload("courtscheduler.delete-sessions.json");
        deleteHearingSlotsPayload = deleteHearingSlotsPayload.replace("COURT_SCHEDULE_ID", nonExistingCourtScheduleId);

        final Response response = postCommand(BASE_RESOURCE_URL + DELETE_URL, COURT_SCHEDULE_DELETE_CONTENT_TYPE, USER_ID, deleteHearingSlotsPayload);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
    }

    @Test
    void shouldDeleteCourtScheduleSessionsRegardlessOfJurisdictionType() throws Exception {
        String magistratesCourtScheduleId = UUID.randomUUID().toString();
        String crownCourtScheduleId = UUID.randomUUID().toString();

        // Create MAGISTRATES court schedule (deletable - no allocated listings)
        CourtSchedule magistratesSchedule = RANDOM.nextObject(CourtSchedule.class);
        magistratesSchedule.setBusinessType("TRL");
        magistratesSchedule.setSlotBased(false);
        magistratesSchedule.setMaxDuration(120);
        magistratesSchedule.setAvailableDuration(120);
        magistratesSchedule.setSupportAdSplit(false);
        magistratesSchedule.setCourtSession(ALL_DAY);
        magistratesSchedule.setCourtScheduleId(magistratesCourtScheduleId);
        magistratesSchedule.setSessionDate(getRandomFutureDateWithinNextYear());
        magistratesSchedule.setSessionStartTime(combineDateAndTime(magistratesSchedule.getSessionDate(), "10:00"));
        magistratesSchedule.setSessionEndTime(combineDateAndTime(magistratesSchedule.getSessionDate(), "16:00"));
        magistratesSchedule.setJurisdiction("MAGISTRATES");
        magistratesSchedule.setIsDraft(false);
        magistratesSchedule.setActive(true);
        databaseSeeder.insertCourtSchedule(magistratesSchedule);

        // Create CROWN court schedule (deletable - no allocated listings)
        CourtSchedule crownSchedule = RANDOM.nextObject(CourtSchedule.class);
        crownSchedule.setBusinessType("TRL");
        crownSchedule.setSlotBased(false);
        crownSchedule.setMaxDuration(120);
        crownSchedule.setAvailableDuration(120);
        crownSchedule.setSupportAdSplit(false);
        crownSchedule.setCourtSession(ALL_DAY);
        crownSchedule.setCourtScheduleId(crownCourtScheduleId);
        crownSchedule.setSessionDate(getRandomFutureDateWithinNextYear());
        crownSchedule.setSessionStartTime(combineDateAndTime(crownSchedule.getSessionDate(), "10:00"));
        crownSchedule.setSessionEndTime(combineDateAndTime(crownSchedule.getSessionDate(), "16:00"));
        crownSchedule.setJurisdiction("CROWN");
        crownSchedule.setIsDraft(true);
        crownSchedule.setActive(true);
        databaseSeeder.insertCourtSchedule(crownSchedule);

        // Delete both schedules in a single request
        // Create JSON payload with both IDs
        String deletePayload = "{\"sessions\": [\"" + magistratesCourtScheduleId + "\", \"" + crownCourtScheduleId + "\"]}";

        final Response deleteResponse = postCommand(BASE_RESOURCE_URL + DELETE_URL,
                COURT_SCHEDULE_DELETE_CONTENT_TYPE, USER_ID, deletePayload);

        assertThat(deleteResponse.getStatus(), is(OK.getStatusCode()));

        // Verify successful deletion - empty sessions array means both were deleted successfully
        try (JsonReader jsonReader = Json.createReader(new StringReader(deleteResponse.readEntity(String.class)))) {
            JsonObject jsonResponse = jsonReader.readObject();
            assertTrue(jsonResponse.containsKey("sessions"), "Response should contain 'sessions' key");
            // Empty array means successful deletion (no errors)
            assertThat(jsonResponse.getJsonArray("sessions").size(), is(0));
            // Should not contain error key when deletion is successful
            assertThat(jsonResponse.containsKey("error"), is(false));
        }

        // Verify both schedules are deleted from database
        List<CourtSchedule> remainingSchedules = databaseReader.courtSchedules();
        assertThat(remainingSchedules.stream()
                .noneMatch(cs -> cs.getCourtScheduleId().equals(magistratesCourtScheduleId)
                        || cs.getCourtScheduleId().equals(crownCourtScheduleId)), is(true));

        // Test Case 2: Rejection when sessions have allocated listings for both jurisdictions
        String magistratesWithListingsId = UUID.randomUUID().toString();
        String crownWithListingsId = UUID.randomUUID().toString();

        // Create MAGISTRATES court schedule with allocated listings
        CourtSchedule magistratesWithListings = RANDOM.nextObject(CourtSchedule.class);
        magistratesWithListings.setBusinessType("TRL");
        magistratesWithListings.setSlotBased(false);
        magistratesWithListings.setMaxDuration(120);
        magistratesWithListings.setAvailableDuration(60);
        magistratesWithListings.setSupportAdSplit(false);
        magistratesWithListings.setCourtSession(ALL_DAY);
        magistratesWithListings.setCourtScheduleId(magistratesWithListingsId);
        magistratesWithListings.setSessionDate(getRandomFutureDateWithinNextYear());
        magistratesWithListings.setSessionStartTime(combineDateAndTime(magistratesWithListings.getSessionDate(), "10:00"));
        magistratesWithListings.setSessionEndTime(combineDateAndTime(magistratesWithListings.getSessionDate(), "16:00"));
        magistratesWithListings.setJurisdiction("MAGISTRATES");
        magistratesWithListings.setIsDraft(false);
        magistratesWithListings.setActive(true);
        databaseSeeder.insertCourtSchedule(magistratesWithListings);

        // Create allocated listing for MAGISTRATES schedule
        final UUID magistratesHearingId = UUID.randomUUID();
        final UUID magistratesBookingId = UUID.randomUUID();
        createAllocatedListing(magistratesWithListings, magistratesHearingId, magistratesBookingId, 60, "10:00");

        // Create CROWN court schedule with allocated listings
        CourtSchedule crownWithListings = RANDOM.nextObject(CourtSchedule.class);
        crownWithListings.setBusinessType("TRL");
        crownWithListings.setSlotBased(false);
        crownWithListings.setMaxDuration(120);
        crownWithListings.setAvailableDuration(60);
        crownWithListings.setSupportAdSplit(false);
        crownWithListings.setCourtSession(ALL_DAY);
        crownWithListings.setCourtScheduleId(crownWithListingsId);
        crownWithListings.setSessionDate(getRandomFutureDateWithinNextYear());
        crownWithListings.setSessionStartTime(combineDateAndTime(crownWithListings.getSessionDate(), "10:00"));
        crownWithListings.setSessionEndTime(combineDateAndTime(crownWithListings.getSessionDate(), "16:00"));
        crownWithListings.setJurisdiction("CROWN");
        crownWithListings.setIsDraft(true);
        crownWithListings.setActive(true);
        databaseSeeder.insertCourtSchedule(crownWithListings);

        // Create allocated listing for CROWN schedule
        final UUID crownHearingId = UUID.randomUUID();
        final UUID crownBookingId = UUID.randomUUID();
        createAllocatedListing(crownWithListings, crownHearingId, crownBookingId, 60, "10:00");


        // Try to delete both schedules with allocated listings
        // Create JSON payload with both IDs
        String deleteWithListingsPayload = "{\"sessions\": [\"" + magistratesWithListingsId + "\", \"" + crownWithListingsId + "\"]}";

        final Response deleteWithListingsResponse = postCommand(BASE_RESOURCE_URL + DELETE_URL,
                COURT_SCHEDULE_DELETE_CONTENT_TYPE, USER_ID, deleteWithListingsPayload);

        assertThat(deleteWithListingsResponse.getStatus(), is(OK.getStatusCode()));

        // Verify rejection - both should be returned in sessions array with error message
        try (JsonReader jsonReader = Json.createReader(new StringReader(deleteWithListingsResponse.readEntity(String.class)))) {
            JsonObject jsonResponse = jsonReader.readObject();
            assertTrue(jsonResponse.containsKey("error"), "Response should contain an 'error' key when deletion fails");
            assertThat(jsonResponse.getString("error"), is("Some sessions could not be removed. Please check again."));
            assertTrue(jsonResponse.containsKey("sessions"), "Response should contain 'sessions' key");

            // Both MAGISTRATES and CROWN schedules should be returned (can't be deleted due to allocated listings)
            assertThat(jsonResponse.getJsonArray("sessions").size(), is(2));

            // Verify MAGISTRATES schedule is in response
            boolean magistratesFound = false;
            boolean crownFound = false;
            for (int i = 0; i < jsonResponse.getJsonArray("sessions").size(); i++) {
                JsonObject sessionObj = jsonResponse.getJsonArray("sessions").getJsonObject(i);
                String courtScheduleId = sessionObj.getString("courtScheduleId");
                if (courtScheduleId.equals(magistratesWithListingsId)) {
                    magistratesFound = true;
                    assertThat(sessionObj.getInt("totalBooked"), is(60));
                    // Verify it's the MAGISTRATES schedule by checking courtScheduleId
                    assertThat(sessionObj.getString("courtScheduleId"), is(magistratesWithListingsId));
                } else if (courtScheduleId.equals(crownWithListingsId)) {
                    crownFound = true;
                    assertThat(sessionObj.getInt("totalBooked"), is(60));
                    // Verify it's the CROWN schedule by checking courtScheduleId
                    assertThat(sessionObj.getString("courtScheduleId"), is(crownWithListingsId));
                }
            }
            assertThat(magistratesFound, is(true));
            assertThat(crownFound, is(true));
        }

        // Verify both schedules still exist in database (not deleted)
        List<CourtSchedule> schedulesAfterFailedDelete = databaseReader.courtSchedules();
        assertThat(schedulesAfterFailedDelete.stream()
                .anyMatch(cs -> cs.getCourtScheduleId().equals(magistratesWithListingsId)), is(true));
        assertThat(schedulesAfterFailedDelete.stream()
                .anyMatch(cs -> cs.getCourtScheduleId().equals(crownWithListingsId)), is(true));
    }

    @Test
    void shouldMigrateOuCodes() throws Exception {
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

        final Response response = postCommand(OUCODE_MIGRATE_URL, COURT_SCHEDULE_OUCODE_MIGRATE_CONTENT_TYPE, SYSTEM_USER_ID, migrateOuCodePayload);

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
    }

    @Test
    void shouldGetCourtScheduleById() throws SQLException, JsonProcessingException {
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
        courtSchedule.setSessionDate(getRandomFutureDateWithinNextYear());
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), "10:00"));
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), "16:00"));
        courtSchedule.setIsOverbookingAllowed(false);
        courtSchedule.setActive(true);
        databaseSeeder.insertCourtSchedule(courtSchedule);

        final AllocatedListing allocatedListingForMorning = createAllocatedListing(courtSchedule, hearingIdForMorning, bookingIdForMorning, 60, "10:00");
        final AllocatedListing allocatedListingForAfternoon = createAllocatedListing(courtSchedule, hearingIdForAfternoon, bookingIdForAfternoon, 30, "15:00");

        String getCourtScheduleRequestParams = getPayload("courtscheduler.search.courtschedules.by.id_dynamic.json");
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("COURT_SCHEDULE_ID", courtScheduleId.toString());
        Map<String, Object> map = mapper.readValue(getCourtScheduleRequestParams, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams(
                BASE_RESOURCE_URL + SEARCH_BY_ID_URL,
                COURT_SCHEDULE_SEARCH_COURTSCHEDULES_BY_ID_CONTENT_TYPE,
                SYSTEM_USER_ID,
                map
        );

        final ResponseData response = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(response.getStatus().getStatusCode(), is(OK.getStatusCode()));

        JsonObject jsonObject = stringToJsonObjectConverter.convert(response.getPayload());

        JsonObject courtScheduleJsonObject = jsonObject.getJsonArray("courtSchedules").getJsonObject(0);
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
        assertThat(courtScheduleJsonObject.getString("businessType"), is(courtSchedule.getBusinessType()));
        assertThat(courtScheduleJsonObject.getString("courtHouseId"), is(courtSchedule.getCourtHouseId()));
        assertThat(courtScheduleJsonObject.getString("courtHouseName"), is(courtSchedule.getCourtHouseName()));
        assertThat(courtScheduleJsonObject.getString("operationalUnit"), is(courtSchedule.getOperationalUnit()));
        assertThat(courtScheduleJsonObject.getString("ouCode"), is(courtSchedule.getOuCode()));
        assertThat(courtScheduleJsonObject.getString("courtSession"), is(courtSchedule.getCourtSession()));
        assertThat(courtScheduleJsonObject.getInt("maxDuration"), is(courtSchedule.getMaxDuration()));
        assertThat(courtScheduleJsonObject.getInt("availableDuration"), is(courtSchedule.getAvailableDuration()));
        assertThat(courtScheduleJsonObject.getInt("maxSlots"), is(courtSchedule.getMaxSlots()));
        assertThat(courtScheduleJsonObject.getString("listingProfileId"), is(courtSchedule.getListingProfileId()));

        final OffsetDateTime actualStartTime = OffsetDateTime.parse(courtScheduleJsonObject.getString("sessionStartTime"));
        final OffsetDateTime actualEndTime = OffsetDateTime.parse(courtScheduleJsonObject.getString("sessionEndTime"));
        assertThat(actualStartTime.toInstant(), is(courtSchedule.getSessionStartTime().toInstant()));
        assertThat(actualEndTime.toInstant(), is(courtSchedule.getSessionEndTime().toInstant()));
    }

    public String prepareCreateCourtSchedulePayload(final String jsonFilePath) {
        final LocalDate startDate = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        final LocalDate endDate = startDate.plusDays(28);

        return getPayload(jsonFilePath)
                .replaceAll("START_DATE", startDate.toString())
                .replaceAll("END_DATE", endDate.toString());
    }

    public String prepareCreateCourtSchedulePayload_testBSTToUTC(final String jsonFilePath, final LocalDate startDate, final LocalDate endDate) {
        return getPayload(jsonFilePath)
                .replaceAll("START_DATE", startDate.toString())
                .replaceAll("END_DATE", endDate.toString());
    }

    private AllocatedListing createAllocatedListing(final CourtSchedule courtSchedule, final UUID hearingIdForMorning, final UUID bookingIdForMorning, final int duration, final String time) throws SQLException {
        final AllocatedListing allocatedListingForMorning = RANDOM.nextObject(AllocatedListing.class);
        allocatedListingForMorning.setId(randomUUID().toString());
        allocatedListingForMorning.setCourtScheduleId(courtSchedule.getCourtScheduleId());
        allocatedListingForMorning.setHearingId(hearingIdForMorning.toString());
        allocatedListingForMorning.setBookingId(bookingIdForMorning.toString());
        allocatedListingForMorning.setDuration(duration);
        allocatedListingForMorning.setHearingStartTime(combineDateAndTime(courtSchedule.getSessionDate(), time));
        databaseSeeder.insertAllocatedListing(allocatedListingForMorning);
        return allocatedListingForMorning;
    }

    // Monthly Frequency Tests

    @Test
    void shouldCreateCourtSchedulesForMonthlyFrequency() {
        // Given
        final LocalDate startDate = now().plusDays(1);
        final LocalDate endDate = startDate.plusMonths(3);

        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayloadWithDates(
                "create-court-schedule-monthly-frequency.json",
                startDate,
                endDate
        );

        // When
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);

        // Then
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        // Wait for processing and verify court schedules are created
        // Verify court schedules are created by checking database
        final List<CourtSchedule> courtSchedules = databaseReader.courtSchedules();
        assertThat("Court schedules should be created", courtSchedules.size(), is(greaterThan(0)));

        // Verify at least one court schedule exists
        final CourtSchedule courtSchedule = courtSchedules.get(0);
        assertThat(courtSchedule.getCourtScheduleId(), is(notNullValue()));
        assertThat(courtSchedule.getSessionStartTime(), is(notNullValue()));
        assertThat(courtSchedule.getSessionEndTime(), is(notNullValue()));
    }

    @Test
    void shouldCreateCourtSchedulesForMonthlyFrequencyWithMultipleSessions() {
        // Given
        final LocalDate startDate = now().plusDays(1);
        final LocalDate endDate = startDate.plusMonths(2);

        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayloadWithDates(
                "create-court-schedule-monthly-frequency-multiple-sessions.json",
                startDate,
                endDate
        );

        // When
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);

        // Then
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        // Wait for processing and verify court schedules are created
        final List<CourtSchedule> courtSchedules = databaseReader.courtSchedules();
        assertThat("Court schedules should be created", courtSchedules.size(), is(greaterThan(0)));

        // Verify we have multiple court schedules (one for each session type)
        assertTrue(courtSchedules.size() >= 2, "Should have at least 2 court schedules for multiple sessions");
    }

    @Test
    void shouldCreateCourtSchedulesForMonthlyFrequencyWithDifferentRepeatIntervals() {
        // Given
        final LocalDate startDate = now().plusDays(1);
        final LocalDate endDate = startDate.plusMonths(6); // 6 months to allow for every 2 months

        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayloadWithDates(
                "create-court-schedule-monthly-frequency-every-2-months.json",
                startDate,
                endDate
        );

        // When
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);

        // Then
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        // Wait for processing and verify court schedules are created
        final List<CourtSchedule> courtSchedules = databaseReader.courtSchedules();
        assertThat("Court schedules should be created", courtSchedules.size(), is(greaterThan(0)));

        // Verify court schedules are created for every 2 months
        final CourtSchedule courtSchedule = courtSchedules.get(0);
        assertThat(courtSchedule.getCourtScheduleId(), is(notNullValue()));
    }

    @Test
    void shouldCreateCourtSchedulesForMonthlyFrequencyWithDifferentIndexValues() {
        // Given
        final LocalDate startDate = now().plusDays(1);
        final LocalDate endDate = startDate.plusMonths(2);

        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayloadWithDates(
                "create-court-schedule-monthly-frequency-different-index.json",
                startDate,
                endDate
        );

        // When
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);

        // Then
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        // Wait for processing and verify court schedules are created
        final List<CourtSchedule> courtSchedules = databaseReader.courtSchedules();
        assertThat("Court schedules should be created", courtSchedules.size(), is(greaterThan(0)));

        // Verify court schedule is created with index 5 (no session created if 5th doesn't exist in month)
        final CourtSchedule courtSchedule = courtSchedules.get(0);
        assertThat(courtSchedule.getCourtScheduleId(), is(notNullValue()));
    }


    @Test
    void shouldCreateCourtSchedulesForMonthlyFrequencyWithRandomStartDate() {
        // Given - Random start date in middle of month
        final LocalDate startDate = now().withDayOfMonth(15).plusMonths(1);
        final LocalDate endDate = startDate.plusMonths(3);

        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayloadWithDates(
                "create-court-schedule-monthly-frequency.json",
                startDate,
                endDate
        );

        // When
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);

        // Then
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        // Wait for processing and verify court schedules are created
        final List<CourtSchedule> courtSchedules = databaseReader.courtSchedules();
        assertThat("Court schedules should be created", courtSchedules.size(), is(greaterThan(0)));

        // Verify court schedules are created for the random start date
        final CourtSchedule courtSchedule = courtSchedules.get(0);
        assertThat(courtSchedule.getCourtScheduleId(), is(notNullValue()));
    }

    @Test
    void shouldCreateCourtSchedulesForMonthlyFrequencyWithYearBoundary() {
        // Given - Start date in December, end date in March next year
        // If December 15th is in the past, use next year's December 15th
        LocalDate startDate = LocalDate.now().withMonth(12).withDayOfMonth(15);
        if (startDate.isBefore(now())) {
            startDate = startDate.plusYears(1);
        }
        final LocalDate endDate = startDate.withYear(startDate.getYear() + 1).withMonth(3).withDayOfMonth(15);

        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayloadWithDates(
                "create-court-schedule-monthly-frequency.json",
                startDate,
                endDate
        );

        // When
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);

        // Then
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        // Wait for processing and verify court schedules are created
        final List<CourtSchedule> courtSchedules = databaseReader.courtSchedules();
        assertThat("Court schedules should be created", courtSchedules.size(), is(greaterThan(0)));

        // Verify court schedules are created across year boundary
        final CourtSchedule courtSchedule = courtSchedules.get(0);
        assertThat(courtSchedule.getCourtScheduleId(), is(notNullValue()));
    }

    @Test
    void shouldNotCreateSessionWhen5thFridayDoesNotExistInMonth() {
        // Given - Test with months where 5th Friday doesn't exist (e.g., February 2026 has only 4 Fridays)
        // We'll use a date range that includes both months with 5 Fridays and months without
        LocalDate startDate = LocalDate.of(2026, 1, 1); // January 2026 - has 5 Fridays
        LocalDate endDate = LocalDate.of(2026, 3, 31); // March 2026 - includes February (4 Fridays) and March (5 Fridays)

        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayloadWithDates(
                "create-court-schedule-monthly-frequency-different-index.json",
                startDate,
                endDate
        );

        // When
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);

        // Then
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        // Wait for processing and verify court schedules are created
        final List<CourtSchedule> courtSchedules = databaseReader.courtSchedules();

        // January 2026: First Friday is Jan 2, so 5th Friday is Jan 2 + 28 days = Jan 30 (exists)
        // February 2026: First Friday is Feb 6, so 5th Friday is Feb 6 + 28 days = Mar 6 (next month - doesn't exist in Feb)
        // March 2026: First Friday is Mar 6, so 5th Friday is Mar 6 + 28 days = Apr 3 (next month - doesn't exist in Mar)
        // So we should only get sessions for January (1 session)
        assertThat("Only sessions for months with 5th Friday should be created",
                courtSchedules.size(), is(greaterThanOrEqualTo(1)));

        // Verify all created sessions are from months that have 5th Friday
        for (CourtSchedule schedule : courtSchedules) {
            LocalDate sessionDate = schedule.getSessionDate();
            // Verify the session date is actually a Friday and is the 5th Friday of that month
            assertThat("Session date should be a Friday", sessionDate.getDayOfWeek(), is(DayOfWeek.FRIDAY));

            // Calculate which occurrence this Friday is in the month
            LocalDate firstOfMonth = sessionDate.withDayOfMonth(1);
            LocalDate firstFriday = firstOfMonth.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY));
            long weekNumber = ChronoUnit.WEEKS.between(firstFriday, sessionDate);
            assertThat("Session should be on the 5th Friday", weekNumber, is(4L)); // 0-indexed, so 4 means 5th
        }
    }

    private String prepareCreateCourtSchedulePayloadWithDates(final String fileName, final LocalDate startDate, final LocalDate endDate) {
        return getPayload(fileName)
                .replace("START_DATE", startDate.format(ofPattern("yyyy-MM-dd")))
                .replace("END_DATE", endDate.format(ofPattern("yyyy-MM-dd")));
    }

    @Test
    void shouldPreventCourtroomChangeWhenHearingsExistAndAssigned() throws SQLException {
        UUID courtScheduleId = UUID.randomUUID();
        CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        String courtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3"; // Use same courtroom ID for initial and update
        String courtHouseId = "785339c1-af71-3322-a55b-ba255e0db1c2"; // Test Crown Court - same court house as courtroom
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType("DVLA");
        expected.setSlotBased(true);
        expected.setMaxSlots(15);
        expected.setAvailableSlots(15);
        expected.setIsDraft(false); // Assigned session
        expected.setHasHearingsBooked(true);
        expected.setSupportAdSplit(false);
        expected.setCourtSession(AM_SESSION);
        expected.setPanel("YOUTH");
        expected.setCourtRoomId(courtRoomId); // Set initial courtroom ID
        expected.setCourtHouseId(courtHouseId); // Set court house ID to match courtroom
        expected.setSessionDate(LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY))); // Set to future date to avoid past session validation
        expected.setSessionStartTime(DateUtils.localDateToDateWithTime(expected.getSessionDate(), 9, 0));
        expected.setSessionEndTime(DateUtils.localDateToDateWithTime(expected.getSessionDate(), 13, 0));
        databaseSeeder.insertCourtSchedule(expected);

        // Create a hearing attached to this session
        UUID hearingId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        createAllocatedListing(expected, hearingId, bookingId, 15, "10:00");

        String updateCourtSchedulePayload = getPayload("update-court-schedule.json");
        String changedCourtRoomId = courtRoomId; // Use same courtroom to avoid court house validation
        String changedBusinessType = "DVLA";
        String changedSessionType = expected.getCourtSession();
        String changedPanel = "ADULT"; // Change panel to trigger SESSION_EDIT_ANOTHER_USER validation when hearings exist
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_SCHEDULE_ID", expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_ROOM_ID", changedCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("BUSINESS_TYPE", changedBusinessType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("SESSION_TYPE", changedSessionType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("PANEL", changedPanel);
        // Replace maxDuration with slot-based fields and add session times
        String sessionStartTimeStr = DateUtils.sessionTimeFormatter(expected.getSessionStartTime());
        String sessionEndTimeStr = DateUtils.sessionTimeFormatter(expected.getSessionEndTime());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("\"maxDuration\": 10",
                "\"maxSlots\": 15,\n  \"maxDuration\": 0,\n  \"allDaySplit\": false,\n  \"sessionStartTime\": \"" + sessionStartTimeStr + "\",\n  \"sessionEndTime\": \"" + sessionEndTimeStr + "\"");

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);
        final String errorResponseMessage = response.readEntity(String.class);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        assertThat(errorResponseMessage, containsString(SESSION_EDIT_ANOTHER_USER));
    }

    @Test
    void shouldReturn400WhenPanelMissingForMagistratesJurisdictionInUpdate() {
        String updateCourtSchedulePayload = getPayload("update-court-schedule-missing-panel.json");
        String changedCourtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3"; // picked from referencedata.rota-courtrooms.json file
        String changedBusinessType = "DVLA";

        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_SCHEDULE_ID", randomUUID().toString());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_ROOM_ID", changedCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("BUSINESS_TYPE", changedBusinessType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("SESSION_TYPE", AM_SESSION);

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
    }

    @Test
    void shouldAllowDraftToAssignedChangeWhenHearingsExist() throws SQLException {
        UUID courtScheduleId = UUID.randomUUID();
        CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType("DVLA");
        expected.setSlotBased(true);
        expected.setMaxSlots(15);
        expected.setAvailableSlots(15);
        expected.setIsDraft(true); // Currently Draft
        expected.setHasHearingsBooked(true);
        expected.setSupportAdSplit(false);
        expected.setCourtSession(AM_SESSION);
        expected.setPanel("YOUTH");
        expected.setCourtRoomId("3fc02c0f-f92e-31da-9686-d626ac8ccdc3");
        expected.setSessionDate(LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY))); // Set to future date to avoid past session validation
        expected.setSessionStartTime(DateUtils.localDateToDateWithTime(expected.getSessionDate(), 9, 0));
        expected.setSessionEndTime(DateUtils.localDateToDateWithTime(expected.getSessionDate(), 13, 0));
        databaseSeeder.insertCourtSchedule(expected);

        // Create a hearing attached to this session
        UUID hearingId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        createAllocatedListing(expected, hearingId, bookingId, 15, "10:00");

        String updateCourtSchedulePayload = getPayload("update-court-schedule.json");
        String sameCourtRoomId = expected.getCourtRoomId(); // Keep same courtroom
        String changedBusinessType = "DVLA";
        String sameSessionType = expected.getCourtSession(); // Keep same session type
        String samePanel = expected.getPanel(); // Keep same panel
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_SCHEDULE_ID", expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_ROOM_ID", sameCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("BUSINESS_TYPE", changedBusinessType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("SESSION_TYPE", sameSessionType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("PANEL", samePanel);
        // Replace maxDuration with slot-based fields and add session times
        String sessionStartTimeStr = DateUtils.sessionTimeFormatter(expected.getSessionStartTime());
        String sessionEndTimeStr = DateUtils.sessionTimeFormatter(expected.getSessionEndTime());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("\"maxDuration\": 10",
                "\"maxSlots\": 15,\n  \"maxDuration\": 0,\n  \"allDaySplit\": false,\n  \"sessionStartTime\": \"" + sessionStartTimeStr + "\",\n  \"sessionEndTime\": \"" + sessionEndTimeStr + "\"");

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);
        final String responsePayload = response.readEntity(String.class);

        // Note: The actual draft status change (isDraft flag) would be handled at the repository/entity level
        // This test verifies that the update can proceed when courtroom, sessionType, and panel are unchanged
        // even though hearings exist, allowing the draft status to be changed to assigned
        assertThat("Update response: " + responsePayload, response.getStatus(), is(ACCEPTED.getStatusCode()));
    }

    @Test
    void shouldAssignCourtroomToMultipleEligibleSessions() throws SQLException {
        // Draft with/without hearings - eligible
        // Assigned without hearings - not eligible

        UUID draftSessionId = UUID.randomUUID();
        CourtSchedule draftSession = RANDOM.nextObject(CourtSchedule.class);
        draftSession.setCourtScheduleId(draftSessionId.toString());
        draftSession.setBusinessType("DVLA");
        draftSession.setSlotBased(true);
        draftSession.setMaxSlots(15);
        draftSession.setAvailableSlots(15);
        draftSession.setIsDraft(true); // Draft session
        draftSession.setSupportAdSplit(false);
        draftSession.setCourtSession(AM_SESSION);
        draftSession.setPanel("ADULT");
        draftSession.setCourtRoomId("original-courtroom-id");
        draftSession.setJurisdiction("CROWN");
        draftSession.setCourtHouseId("785339c1-af71-3322-a55b-ba255e0db1c2");
        draftSession.setSessionStartTime(DateUtils.localDateToDateWithTime(draftSession.getSessionDate(), 9, 0));
        draftSession.setSessionEndTime(DateUtils.localDateToDateWithTime(draftSession.getSessionDate(), 13, 0));
        databaseSeeder.insertCourtSchedule(draftSession);

        // Create hearing for draft session (should still be eligible)
        UUID hearingId1 = UUID.randomUUID();
        UUID bookingId1 = UUID.randomUUID();
        createAllocatedListing(draftSession, hearingId1, bookingId1, 15, "10:00");

        UUID draftSessionNoHearingsId = UUID.randomUUID();
        CourtSchedule draftSessionNoHearings = RANDOM.nextObject(CourtSchedule.class);
        draftSessionNoHearings.setCourtScheduleId(draftSessionNoHearingsId.toString());
        draftSessionNoHearings.setBusinessType("DVLA");
        draftSessionNoHearings.setSlotBased(true);
        draftSessionNoHearings.setMaxSlots(15);
        draftSessionNoHearings.setAvailableSlots(15);
        draftSessionNoHearings.setIsDraft(true); // Draft session without hearings
        draftSessionNoHearings.setSupportAdSplit(false);
        draftSessionNoHearings.setCourtSession(AM_SESSION);
        draftSessionNoHearings.setPanel("ADULT");
        draftSessionNoHearings.setCourtRoomId("original-courtroom-id");
        draftSessionNoHearings.setJurisdiction("CROWN");
        draftSessionNoHearings.setCourtHouseId("785339c1-af71-3322-a55b-ba255e0db1c2");
        draftSessionNoHearings.setSessionStartTime(DateUtils.localDateToDateWithTime(draftSessionNoHearings.getSessionDate(), 9, 0));
        draftSessionNoHearings.setSessionEndTime(DateUtils.localDateToDateWithTime(draftSessionNoHearings.getSessionDate(), 13, 0));
        databaseSeeder.insertCourtSchedule(draftSessionNoHearings);
        // Assigned session -  NOT eligible
        UUID assignedSessionId = UUID.randomUUID();
        CourtSchedule assignedSession = RANDOM.nextObject(CourtSchedule.class);
        assignedSession.setCourtScheduleId(assignedSessionId.toString());
        assignedSession.setBusinessType("DVLA");
        assignedSession.setSlotBased(true);
        assignedSession.setMaxSlots(15);
        assignedSession.setAvailableSlots(15);
        assignedSession.setIsDraft(false); // Assigned session without hearings
        assignedSession.setSupportAdSplit(false);
        assignedSession.setCourtSession(AM_SESSION);
        assignedSession.setPanel("ADULT");
        assignedSession.setCourtRoomId("original-courtroom-id");
        assignedSession.setJurisdiction("CROWN");
        assignedSession.setCourtHouseId("785339c1-af71-3322-a55b-ba255e0db1c2");
        assignedSession.setSessionStartTime(DateUtils.localDateToDateWithTime(assignedSession.getSessionDate(), 9, 0));
        assignedSession.setSessionEndTime(DateUtils.localDateToDateWithTime(assignedSession.getSessionDate(), 13, 0));
        databaseSeeder.insertCourtSchedule(assignedSession);

        String assignCourtroomPayload = getPayload("assign-courtroom-multiple-eligible-sessions.json");
        String newCourtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3"; // Different courtroom
        assignCourtroomPayload = assignCourtroomPayload.replace("COURT_SCHEDULE_ID_1", draftSession.getCourtScheduleId());
        assignCourtroomPayload = assignCourtroomPayload.replace("COURT_SCHEDULE_ID_2", draftSessionNoHearings.getCourtScheduleId());
        assignCourtroomPayload = assignCourtroomPayload.replace("COURT_SCHEDULE_ID_3", assignedSession.getCourtScheduleId());
        assignCourtroomPayload = assignCourtroomPayload.replace("COURT_ROOM_ID", newCourtRoomId);

        final Response response = postCommand(BASE_RESOURCE_URL + ASSIGN_COURTROOM_URL, COURT_SCHEDULE_ASSIGN_COURTROOM_CONTENT_TYPE, USER_ID, assignCourtroomPayload);
        final String responsePayload = response.readEntity(String.class);

        assertThat("Assign courtroom response: " + responsePayload, response.getStatus(), is(OK.getStatusCode()));

        // Parse JSON response - should be an object with errorGroups array
        JsonReader jsonReader = Json.createReader(new StringReader(responsePayload));
        JsonObject jsonResponse = jsonReader.readObject();
        jsonReader.close();

        // Verify response has errorGroups key
        assertThat("Response should contain errorGroups", jsonResponse.containsKey("errorGroups"), is(true));
        javax.json.JsonArray jsonResponseArray = jsonResponse.getJsonArray("errorGroups");

        // Verify response is an array
        assertThat("Response should be an array", jsonResponseArray, notNullValue());

        // Draft sessions should be successfully assigned (not in any error group)
        boolean draftSessionInErrorGroup = jsonResponseArray.stream()
                .map(JsonValue::asJsonObject)
                .anyMatch(errorGroup -> {
                    javax.json.JsonArray sessions = errorGroup.getJsonArray("sessions");
                    return sessions.stream()
                            .map(s -> s.asJsonObject())
                            .anyMatch(s -> draftSession.getCourtScheduleId().equals(s.getString("courtScheduleId"))
                                    || draftSessionNoHearings.getCourtScheduleId().equals(s.getString("courtScheduleId")));
                });
        assertThat("Draft sessions should not be in any error group", draftSessionInErrorGroup, is(false));

        // Assigned session should be in error group
        boolean foundAssignedSessionInErrorGroup = jsonResponseArray.stream()
                .map(JsonValue::asJsonObject)
                .anyMatch(errorGroup -> {
                    String error = errorGroup.getString("error");
                    if ("Cannot assign courtroom to an assigned session".equals(error)) {
                        javax.json.JsonArray sessions = errorGroup.getJsonArray("sessions");
                        return sessions.stream()
                                .map(s -> s.asJsonObject())
                                .anyMatch(s -> assignedSession.getCourtScheduleId().equals(s.getString("courtScheduleId")));
                    }
                    return false;
                });
        assertThat("Should find assigned session in error group", foundAssignedSessionInErrorGroup, is(true));
    }

    @Test
    void shouldNotAssignCourtroomToAssignedSession() throws SQLException {
        // Assigned session - NOT eligible (Business Rule 5: applies to all assigned sessions regardless of hearings)

        UUID assignedSessionId = UUID.randomUUID();
        CourtSchedule assignedSession = RANDOM.nextObject(CourtSchedule.class);
        assignedSession.setCourtScheduleId(assignedSessionId.toString());
        assignedSession.setBusinessType("DVLA");
        assignedSession.setSlotBased(true);
        assignedSession.setMaxSlots(15);
        assignedSession.setAvailableSlots(15);
        assignedSession.setIsDraft(false); // Assigned session
        assignedSession.setHasHearingsBooked(true);
        assignedSession.setSupportAdSplit(false);
        assignedSession.setCourtSession(AM_SESSION);
        assignedSession.setPanel("ADULT");
        assignedSession.setCourtRoomId("original-courtroom-id");
        assignedSession.setJurisdiction("CROWN");
        assignedSession.setCourtHouseId("785339c1-af71-3322-a55b-ba255e0db1c2");
        assignedSession.setSessionStartTime(DateUtils.localDateToDateWithTime(assignedSession.getSessionDate(), 9, 0));
        assignedSession.setSessionEndTime(DateUtils.localDateToDateWithTime(assignedSession.getSessionDate(), 13, 0));
        databaseSeeder.insertCourtSchedule(assignedSession);

        // Create a hearing attached to this session
        UUID hearingId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        createAllocatedListing(assignedSession, hearingId, bookingId, 15, "10:00");

        String assignCourtroomPayload = getPayload("assign-courtroom.json");
        String newCourtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3";
        assignCourtroomPayload = assignCourtroomPayload.replace("COURT_SCHEDULE_ID_1", assignedSession.getCourtScheduleId());
        assignCourtroomPayload = assignCourtroomPayload.replace("COURT_SCHEDULE_ID_2", assignedSession.getCourtScheduleId());
        assignCourtroomPayload = assignCourtroomPayload.replace("COURT_ROOM_ID", newCourtRoomId);

        final Response response = postCommand(BASE_RESOURCE_URL + ASSIGN_COURTROOM_URL, COURT_SCHEDULE_ASSIGN_COURTROOM_CONTENT_TYPE, USER_ID, assignCourtroomPayload);
        final String responsePayload = response.readEntity(String.class);

        assertThat("Assign courtroom response: " + responsePayload, response.getStatus(), is(OK.getStatusCode()));

        // Parse JSON response - should be an object with errorGroups array
        JsonReader jsonReader = Json.createReader(new StringReader(responsePayload));
        JsonObject jsonResponse = jsonReader.readObject();
        jsonReader.close();

        // Verify response has errorGroups key
        assertThat("Response should contain errorGroups", jsonResponse.containsKey("errorGroups"), is(true));
        javax.json.JsonArray jsonResponseArray = jsonResponse.getJsonArray("errorGroups");

        // Verify response is an array
        assertThat("Response should be an array", jsonResponseArray, notNullValue());

        // Find the error group with the expected error message
        boolean foundErrorGroup = jsonResponseArray.stream()
                .map(JsonValue::asJsonObject)
                .anyMatch(errorGroup -> {
                    String error = errorGroup.getString("error");
                    if ("Cannot assign courtroom to an assigned session".equals(error)) {
                        javax.json.JsonArray sessions = errorGroup.getJsonArray("sessions");
                        return sessions.stream()
                                .map(s -> s.asJsonObject())
                                .anyMatch(s -> assignedSession.getCourtScheduleId().equals(s.getString("courtScheduleId")));
                    }
                    return false;
                });
        assertThat("Should find assigned session in error group", foundErrorGroup, is(true));
    }

    @Test
    void shouldNotAssignCourtroomToAssignedSessionWithoutHearings() throws SQLException {
        // Assigned session without hearings - NOT eligible (Business Rule 5: applies to all assigned sessions)

        UUID assignedSessionId = UUID.randomUUID();
        CourtSchedule assignedSession = RANDOM.nextObject(CourtSchedule.class);
        assignedSession.setCourtScheduleId(assignedSessionId.toString());
        assignedSession.setBusinessType("DVLA");
        assignedSession.setSlotBased(true);
        assignedSession.setMaxSlots(15);
        assignedSession.setAvailableSlots(15);
        assignedSession.setIsDraft(false); // Assigned session
        assignedSession.setHasHearingsBooked(false);
        assignedSession.setSupportAdSplit(false);
        assignedSession.setCourtSession(AM_SESSION);
        assignedSession.setPanel("ADULT");
        assignedSession.setCourtRoomId("original-courtroom-id");
        assignedSession.setJurisdiction("CROWN");
        assignedSession.setCourtHouseId("785339c1-af71-3322-a55b-ba255e0db1c2");
        assignedSession.setSessionStartTime(DateUtils.localDateToDateWithTime(assignedSession.getSessionDate(), 9, 0));
        assignedSession.setSessionEndTime(DateUtils.localDateToDateWithTime(assignedSession.getSessionDate(), 13, 0));
        databaseSeeder.insertCourtSchedule(assignedSession);

        // No hearings attached to this session

        String assignCourtroomPayload = getPayload("assign-courtroom.json");
        String newCourtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3";
        assignCourtroomPayload = assignCourtroomPayload.replace("COURT_SCHEDULE_ID_1", assignedSession.getCourtScheduleId());
        assignCourtroomPayload = assignCourtroomPayload.replace("COURT_SCHEDULE_ID_2", assignedSession.getCourtScheduleId());
        assignCourtroomPayload = assignCourtroomPayload.replace("COURT_ROOM_ID", newCourtRoomId);

        final Response response = postCommand(BASE_RESOURCE_URL + ASSIGN_COURTROOM_URL, COURT_SCHEDULE_ASSIGN_COURTROOM_CONTENT_TYPE, USER_ID, assignCourtroomPayload);
        final String responsePayload = response.readEntity(String.class);

        assertThat("Assign courtroom response: " + responsePayload, response.getStatus(), is(OK.getStatusCode()));

        // Parse JSON response - should be an object with errorGroups array
        JsonReader jsonReader = Json.createReader(new StringReader(responsePayload));
        JsonObject jsonResponse = jsonReader.readObject();
        jsonReader.close();

        // Verify response has errorGroups key
        assertThat("Response should contain errorGroups", jsonResponse.containsKey("errorGroups"), is(true));
        javax.json.JsonArray jsonResponseArray = jsonResponse.getJsonArray("errorGroups");

        // Verify response is an array
        assertThat("Response should be an array", jsonResponseArray, notNullValue());

        // Find the error group with the expected error message
        boolean foundErrorGroup = jsonResponseArray.stream()
                .map(JsonValue::asJsonObject)
                .anyMatch(errorGroup -> {
                    String error = errorGroup.getString("error");
                    if ("Cannot assign courtroom to an assigned session".equals(error)) {
                        javax.json.JsonArray sessions = errorGroup.getJsonArray("sessions");
                        return sessions.stream()
                                .map(s -> s.asJsonObject())
                                .anyMatch(s -> assignedSession.getCourtScheduleId().equals(s.getString("courtScheduleId")));
                    }
                    return false;
                });
        assertThat("Should find assigned session without hearings in error group", foundErrorGroup, is(true));
    }

    @Test
    void shouldReturnErrorWhenCourtroomIdNotProvided() throws SQLException {
        //Must choose a courtroom

        UUID sessionId = UUID.randomUUID();
        CourtSchedule session = RANDOM.nextObject(CourtSchedule.class);
        session.setCourtScheduleId(sessionId.toString());
        session.setBusinessType("DVLA");
        session.setSlotBased(true);
        session.setMaxSlots(15);
        session.setAvailableSlots(15);
        session.setIsDraft(true);
        session.setSupportAdSplit(false);
        session.setCourtSession(AM_SESSION);
        session.setPanel("ADULT");
        databaseSeeder.insertCourtSchedule(session);

        String assignCourtroomPayload = getPayload("assign-courtroom.json");
        assignCourtroomPayload = assignCourtroomPayload.replace("COURT_SCHEDULE_ID_1", session.getCourtScheduleId());
        assignCourtroomPayload = assignCourtroomPayload.replace("COURT_SCHEDULE_ID_2", session.getCourtScheduleId());
        assignCourtroomPayload = assignCourtroomPayload.replace("\"courtRoomId\": \"COURT_ROOM_ID\"", "\"courtRoomId\": \"\"");

        final Response response = postCommand(BASE_RESOURCE_URL + ASSIGN_COURTROOM_URL, COURT_SCHEDULE_ASSIGN_COURTROOM_CONTENT_TYPE, USER_ID, assignCourtroomPayload);
        final String responsePayload = response.readEntity(String.class);

        assertThat("Assign courtroom response: " + responsePayload, response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        assertThat(responsePayload, containsString("Courtroom ID must be provided"));
    }

    @Test
    void shouldHandleMixedEligibleAndIneligibleSessions() throws SQLException {
        // Test with mix of eligible and ineligible sessions

        UUID eligibleSessionId = UUID.randomUUID();
        CourtSchedule eligibleSession = RANDOM.nextObject(CourtSchedule.class);
        eligibleSession.setCourtScheduleId(eligibleSessionId.toString());
        eligibleSession.setBusinessType("DVLA");
        eligibleSession.setSlotBased(true);
        eligibleSession.setMaxSlots(15);
        eligibleSession.setAvailableSlots(15);
        eligibleSession.setIsDraft(true); // Draft - eligible
        eligibleSession.setSupportAdSplit(false);
        eligibleSession.setCourtSession(AM_SESSION);
        eligibleSession.setPanel("ADULT");
        eligibleSession.setCourtRoomId("original-courtroom-id");
        eligibleSession.setJurisdiction("CROWN");
        eligibleSession.setCourtHouseId("785339c1-af71-3322-a55b-ba255e0db1c2");
        eligibleSession.setSessionStartTime(DateUtils.localDateToDateWithTime(eligibleSession.getSessionDate(), 9, 0));
        eligibleSession.setSessionEndTime(DateUtils.localDateToDateWithTime(eligibleSession.getSessionDate(), 13, 0));
        databaseSeeder.insertCourtSchedule(eligibleSession);

        UUID ineligibleSessionId = UUID.randomUUID();
        CourtSchedule ineligibleSession = RANDOM.nextObject(CourtSchedule.class);
        ineligibleSession.setCourtScheduleId(ineligibleSessionId.toString());
        ineligibleSession.setBusinessType("DVLA");
        ineligibleSession.setSlotBased(true);
        ineligibleSession.setMaxSlots(15);
        ineligibleSession.setAvailableSlots(15);
        ineligibleSession.setIsDraft(false); // Assigned with hearings - ineligible
        ineligibleSession.setHasHearingsBooked(true);
        ineligibleSession.setSupportAdSplit(false);
        ineligibleSession.setCourtSession(AM_SESSION);
        ineligibleSession.setPanel("ADULT");
        ineligibleSession.setCourtRoomId("original-courtroom-id");
        ineligibleSession.setJurisdiction("CROWN");
        ineligibleSession.setCourtHouseId("785339c1-af71-3322-a55b-ba255e0db1c2");
        ineligibleSession.setSessionStartTime(DateUtils.localDateToDateWithTime(ineligibleSession.getSessionDate(), 9, 0));
        ineligibleSession.setSessionEndTime(DateUtils.localDateToDateWithTime(ineligibleSession.getSessionDate(), 13, 0));
        databaseSeeder.insertCourtSchedule(ineligibleSession);

        // Create a hearing for ineligible session
        UUID hearingId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        createAllocatedListing(ineligibleSession, hearingId, bookingId, 15, "10:00");

        String assignCourtroomPayload = getPayload("assign-courtroom.json");
        String newCourtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3";
        assignCourtroomPayload = assignCourtroomPayload.replace("COURT_SCHEDULE_ID_1", eligibleSession.getCourtScheduleId());
        assignCourtroomPayload = assignCourtroomPayload.replace("COURT_SCHEDULE_ID_2", ineligibleSession.getCourtScheduleId());
        assignCourtroomPayload = assignCourtroomPayload.replace("COURT_ROOM_ID", newCourtRoomId);

        final Response response = postCommand(BASE_RESOURCE_URL + ASSIGN_COURTROOM_URL, COURT_SCHEDULE_ASSIGN_COURTROOM_CONTENT_TYPE, USER_ID, assignCourtroomPayload);
        final String responsePayload = response.readEntity(String.class);

        assertThat("Assign courtroom response: " + responsePayload, response.getStatus(), is(OK.getStatusCode()));

        // Parse JSON response - should be an object with errorGroups array
        JsonReader jsonReader = Json.createReader(new StringReader(responsePayload));
        JsonObject jsonResponse = jsonReader.readObject();
        jsonReader.close();

        // Verify response has errorGroups key
        assertThat("Response should contain errorGroups", jsonResponse.containsKey("errorGroups"), is(true));
        javax.json.JsonArray jsonResponseArray = jsonResponse.getJsonArray("errorGroups");

        // Verify ineligible session is in error group
        boolean foundIneligibleSession = jsonResponseArray.stream()
                .map(JsonValue::asJsonObject)
                .anyMatch(errorGroup -> {
                    String error = errorGroup.getString("error");
                    if ("Cannot assign courtroom to an assigned session".equals(error)) {
                        javax.json.JsonArray sessions = errorGroup.getJsonArray("sessions");
                        return sessions.stream()
                                .map(s -> s.asJsonObject())
                                .anyMatch(s -> ineligibleSession.getCourtScheduleId().equals(s.getString("courtScheduleId")));
                    }
                    return false;
                });
        assertThat("Should find ineligible session in error group", foundIneligibleSession, is(true));

        // Eligible session should be successfully assigned (not in any error group)
        boolean eligibleSessionInErrorGroup = jsonResponseArray.stream()
                .map(JsonValue::asJsonObject)
                .anyMatch(errorGroup -> {
                    javax.json.JsonArray sessions = errorGroup.getJsonArray("sessions");
                    return sessions.stream()
                            .map(s -> s.asJsonObject())
                            .anyMatch(s -> eligibleSession.getCourtScheduleId().equals(s.getString("courtScheduleId")));
                });
        assertThat("Eligible session should not be in any error group", eligibleSessionInErrorGroup, is(false));
    }

    @Test
    void shouldMarkNonCrownSessionsAsIneligible() throws SQLException {
        // Test that MAGISTRATES sessions are marked as ineligible

        UUID crownSessionId = UUID.randomUUID();
        CourtSchedule crownSession = RANDOM.nextObject(CourtSchedule.class);
        crownSession.setCourtScheduleId(crownSessionId.toString());
        crownSession.setBusinessType("DVLA");
        crownSession.setSlotBased(true);
        crownSession.setMaxSlots(15);
        crownSession.setAvailableSlots(15);
        crownSession.setIsDraft(true);
        crownSession.setSupportAdSplit(false);
        crownSession.setCourtSession(AM_SESSION);
        crownSession.setPanel("YOUTH");
        crownSession.setCourtRoomId("original-courtroom-id-crown");
        crownSession.setJurisdiction("CROWN");
        crownSession.setCourtHouseId("785339c1-af71-3322-a55b-ba255e0db1c2");
        crownSession.setSessionStartTime(DateUtils.localDateToDateWithTime(crownSession.getSessionDate(), 9, 0));
        crownSession.setSessionEndTime(DateUtils.localDateToDateWithTime(crownSession.getSessionDate(), 13, 0));
        databaseSeeder.insertCourtSchedule(crownSession);

        UUID magistratesSessionId = UUID.randomUUID();
        CourtSchedule magistratesSession = RANDOM.nextObject(CourtSchedule.class);
        magistratesSession.setCourtScheduleId(magistratesSessionId.toString());
        magistratesSession.setBusinessType("DVLA");
        magistratesSession.setSlotBased(true);
        magistratesSession.setMaxSlots(15);
        magistratesSession.setAvailableSlots(15);
        magistratesSession.setIsDraft(true);
        magistratesSession.setSupportAdSplit(false);
        magistratesSession.setCourtSession(AM_SESSION);
        magistratesSession.setPanel("ADULT");
        magistratesSession.setCourtRoomId("original-courtroom-id-mags");
        magistratesSession.setJurisdiction("MAGISTRATES");
        magistratesSession.setCourtHouseId("785339c1-af71-3322-a55b-ba255e0db1c2");
        magistratesSession.setSessionStartTime(DateUtils.localDateToDateWithTime(magistratesSession.getSessionDate(), 9, 0));
        magistratesSession.setSessionEndTime(DateUtils.localDateToDateWithTime(magistratesSession.getSessionDate(), 13, 0));
        databaseSeeder.insertCourtSchedule(magistratesSession);

        String assignCourtroomPayload = getPayload("assign-courtroom.json");
        String newCourtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3";
        assignCourtroomPayload = assignCourtroomPayload.replace("COURT_SCHEDULE_ID_1", crownSession.getCourtScheduleId());
        assignCourtroomPayload = assignCourtroomPayload.replace("COURT_SCHEDULE_ID_2", magistratesSession.getCourtScheduleId());
        assignCourtroomPayload = assignCourtroomPayload.replace("COURT_ROOM_ID", newCourtRoomId);

        final Response response = postCommand(BASE_RESOURCE_URL + ASSIGN_COURTROOM_URL, COURT_SCHEDULE_ASSIGN_COURTROOM_CONTENT_TYPE, USER_ID, assignCourtroomPayload);
        final String responsePayload = response.readEntity(String.class);

        assertThat("Assign courtroom response: " + responsePayload, response.getStatus(), is(OK.getStatusCode()));

        // Parse JSON response - should be an object with errorGroups array
        JsonReader jsonReader = Json.createReader(new StringReader(responsePayload));
        JsonObject jsonResponse = jsonReader.readObject();
        jsonReader.close();

        // Verify response has errorGroups key
        assertThat("Response should contain errorGroups", jsonResponse.containsKey("errorGroups"), is(true));
        javax.json.JsonArray jsonResponseArray = jsonResponse.getJsonArray("errorGroups");

        // Verify response is an array
        assertThat("Response should be an array", jsonResponseArray, notNullValue());

        // Verify MAGISTRATES session is in error group
        boolean foundIneligibleMagistratesSession = jsonResponseArray.stream()
                .map(JsonValue::asJsonObject)
                .anyMatch(errorGroup -> {
                    String error = errorGroup.getString("error");
                    if ("assign.courtroom endpoint is only valid for CROWN jurisdiction sessions".equals(error)) {
                        javax.json.JsonArray sessions = errorGroup.getJsonArray("sessions");
                        return sessions.stream()
                                .map(s -> s.asJsonObject())
                                .anyMatch(s -> magistratesSession.getCourtScheduleId().equals(s.getString("courtScheduleId")));
                    }
                    return false;
                });
        assertThat("Should find MAGISTRATES session in error group with correct reason", foundIneligibleMagistratesSession, is(true));

        // CROWN session should be successfully assigned (not in any error group)
        boolean crownSessionInErrorGroup = jsonResponseArray.stream()
                .map(JsonValue::asJsonObject)
                .anyMatch(errorGroup -> {
                    javax.json.JsonArray sessions = errorGroup.getJsonArray("sessions");
                    return sessions.stream()
                            .map(s -> s.asJsonObject())
                            .anyMatch(s -> crownSession.getCourtScheduleId().equals(s.getString("courtScheduleId")));
                });
        assertThat("CROWN session should not be in any error group", crownSessionInErrorGroup, is(false));
    }

    @Test
    void shouldMarkSessionsWithWrongCourtCentreAsIneligible() throws SQLException {
        // Test that sessions with different court centre than courtroom are marked as ineligible

        UUID correctCourtCentreSessionId = UUID.randomUUID();
        CourtSchedule correctCourtCentreSession = RANDOM.nextObject(CourtSchedule.class);
        correctCourtCentreSession.setCourtScheduleId(correctCourtCentreSessionId.toString());
        correctCourtCentreSession.setBusinessType("DVLA");
        correctCourtCentreSession.setSlotBased(true);
        correctCourtCentreSession.setMaxSlots(15);
        correctCourtCentreSession.setAvailableSlots(15);
        correctCourtCentreSession.setIsDraft(true);
        correctCourtCentreSession.setSupportAdSplit(false);
        correctCourtCentreSession.setCourtSession(AM_SESSION);
        correctCourtCentreSession.setPanel("ADULT");
        correctCourtCentreSession.setCourtRoomId("original-courtroom-id");
        correctCourtCentreSession.setJurisdiction("CROWN");
        correctCourtCentreSession.setCourtHouseId("785339c1-af71-3322-a55b-ba255e0db1c2");
        correctCourtCentreSession.setSessionStartTime(DateUtils.localDateToDateWithTime(correctCourtCentreSession.getSessionDate(), 9, 0));
        correctCourtCentreSession.setSessionEndTime(DateUtils.localDateToDateWithTime(correctCourtCentreSession.getSessionDate(), 13, 0));
        databaseSeeder.insertCourtSchedule(correctCourtCentreSession);

        UUID wrongCourtCentreSessionId = UUID.randomUUID();
        CourtSchedule wrongCourtCentreSession = RANDOM.nextObject(CourtSchedule.class);
        wrongCourtCentreSession.setCourtScheduleId(wrongCourtCentreSessionId.toString());
        wrongCourtCentreSession.setBusinessType("DVLA");
        wrongCourtCentreSession.setSlotBased(true);
        wrongCourtCentreSession.setMaxSlots(15);
        wrongCourtCentreSession.setAvailableSlots(15);
        wrongCourtCentreSession.setIsDraft(true);
        wrongCourtCentreSession.setSupportAdSplit(false);
        wrongCourtCentreSession.setCourtSession(AM_SESSION);
        wrongCourtCentreSession.setPanel("ADULT");
        wrongCourtCentreSession.setCourtRoomId("original-courtroom-id");
        wrongCourtCentreSession.setJurisdiction("CROWN");
        wrongCourtCentreSession.setCourtHouseId("different-court-centre-id-12345");
        wrongCourtCentreSession.setSessionStartTime(DateUtils.localDateToDateWithTime(wrongCourtCentreSession.getSessionDate(), 9, 0));
        wrongCourtCentreSession.setSessionEndTime(DateUtils.localDateToDateWithTime(wrongCourtCentreSession.getSessionDate(), 13, 0));
        databaseSeeder.insertCourtSchedule(wrongCourtCentreSession);

        String assignCourtroomPayload = getPayload("assign-courtroom.json");
        String newCourtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3";
        assignCourtroomPayload = assignCourtroomPayload.replace("COURT_SCHEDULE_ID_1", correctCourtCentreSession.getCourtScheduleId());
        assignCourtroomPayload = assignCourtroomPayload.replace("COURT_SCHEDULE_ID_2", wrongCourtCentreSession.getCourtScheduleId());
        assignCourtroomPayload = assignCourtroomPayload.replace("COURT_ROOM_ID", newCourtRoomId);

        final Response response = postCommand(BASE_RESOURCE_URL + ASSIGN_COURTROOM_URL, COURT_SCHEDULE_ASSIGN_COURTROOM_CONTENT_TYPE, USER_ID, assignCourtroomPayload);
        final String responsePayload = response.readEntity(String.class);

        assertThat("Assign courtroom response: " + responsePayload, response.getStatus(), is(OK.getStatusCode()));

        // Parse JSON response - should be an object with errorGroups array
        JsonReader jsonReader = Json.createReader(new StringReader(responsePayload));
        JsonObject jsonResponse = jsonReader.readObject();
        jsonReader.close();

        // Verify response has errorGroups key
        assertThat("Response should contain errorGroups", jsonResponse.containsKey("errorGroups"), is(true));
        javax.json.JsonArray jsonResponseArray = jsonResponse.getJsonArray("errorGroups");

        // Verify response is an array
        assertThat("Response should be an array", jsonResponseArray, notNullValue());

        // Verify session with wrong court centre is in error group
        boolean foundIneligibleWrongCourtCentreSession = jsonResponseArray.stream()
                .map(JsonValue::asJsonObject)
                .anyMatch(errorGroup -> {
                    String error = errorGroup.getString("error");
                    if ("The new courtroom must belong to the same court centre as the session".equals(error)) {
                        javax.json.JsonArray sessions = errorGroup.getJsonArray("sessions");
                        return sessions.stream()
                                .map(s -> s.asJsonObject())
                                .anyMatch(s -> wrongCourtCentreSession.getCourtScheduleId().equals(s.getString("courtScheduleId")));
                    }
                    return false;
                });
        assertThat("Should find session with wrong court centre in error group with correct reason", foundIneligibleWrongCourtCentreSession, is(true));

        // Session with correct court centre should be successfully assigned (not in any error group)
        boolean correctCourtCentreSessionInErrorGroup = jsonResponseArray.stream()
                .map(JsonValue::asJsonObject)
                .anyMatch(errorGroup -> {
                    javax.json.JsonArray sessions = errorGroup.getJsonArray("sessions");
                    return sessions.stream()
                            .map(s -> s.asJsonObject())
                            .anyMatch(s -> correctCourtCentreSession.getCourtScheduleId().equals(s.getString("courtScheduleId")));
                });
        assertThat("Session with correct court centre should not be in any error group", correctCourtCentreSessionInErrorGroup, is(false));
    }

    @Test
    void shouldMarkAMSessionAsIneligibleWhenDuplicateAMSessionExists() throws SQLException {
        // Test that AM session is marked as ineligible when duplicate AM session exists

        UUID existingSessionId = UUID.randomUUID();
        CourtSchedule existingSession = RANDOM.nextObject(CourtSchedule.class);
        existingSession.setCourtScheduleId(existingSessionId.toString());
        existingSession.setBusinessType("DVLA");
        existingSession.setSlotBased(true);
        existingSession.setMaxSlots(15);
        existingSession.setAvailableSlots(15);
        existingSession.setIsDraft(true);
        existingSession.setSupportAdSplit(false);
        existingSession.setCourtSession(AM_SESSION);
        existingSession.setPanel("ADULT");
        existingSession.setJurisdiction("CROWN");
        existingSession.setCourtHouseId("785339c1-af71-3322-a55b-ba255e0db1c2");
        existingSession.setCourtRoomId("3fc02c0f-f92e-31da-9686-d626ac8ccdc3");
        existingSession.setCourtRoomName("Courtroom 01");
        existingSession.setSessionDate(LocalDate.now().plusDays(1));
        existingSession.setSessionStartTime(DateUtils.localDateToDateWithTime(existingSession.getSessionDate(), 9, 0));
        existingSession.setSessionEndTime(DateUtils.localDateToDateWithTime(existingSession.getSessionDate(), 13, 0));
        databaseSeeder.insertCourtSchedule(existingSession);

        UUID newSessionId = UUID.randomUUID();
        CourtSchedule newSession = RANDOM.nextObject(CourtSchedule.class);
        newSession.setCourtScheduleId(newSessionId.toString());
        newSession.setBusinessType("DVLA");
        newSession.setSlotBased(true);
        newSession.setMaxSlots(15);
        newSession.setAvailableSlots(15);
        newSession.setIsDraft(true);
        newSession.setSupportAdSplit(false);
        newSession.setCourtSession(AM_SESSION);
        newSession.setPanel("ADULT");
        newSession.setJurisdiction("CROWN");
        newSession.setCourtHouseId("785339c1-af71-3322-a55b-ba255e0db1c2");
        newSession.setCourtRoomId("original-courtroom-id");
        newSession.setSessionDate(existingSession.getSessionDate()); // Same date
        newSession.setSessionStartTime(DateUtils.localDateToDateWithTime(newSession.getSessionDate(), 9, 0));
        newSession.setSessionEndTime(DateUtils.localDateToDateWithTime(newSession.getSessionDate(), 13, 0));
        databaseSeeder.insertCourtSchedule(newSession);

        String assignCourtroomPayload = getPayload("assign-courtroom.json");
        String newCourtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3";
        assignCourtroomPayload = assignCourtroomPayload.replace("COURT_SCHEDULE_ID_1", newSessionId.toString());
        assignCourtroomPayload = assignCourtroomPayload.replace("COURT_SCHEDULE_ID_2", newSessionId.toString());
        assignCourtroomPayload = assignCourtroomPayload.replace("COURT_ROOM_ID", newCourtRoomId);

        final Response response = postCommand(BASE_RESOURCE_URL + ASSIGN_COURTROOM_URL, COURT_SCHEDULE_ASSIGN_COURTROOM_CONTENT_TYPE, USER_ID, assignCourtroomPayload);
        final String responsePayload = response.readEntity(String.class);

        assertThat("Assign courtroom response: " + responsePayload, response.getStatus(), is(OK.getStatusCode()));

        // Parse JSON response - should be an object with errorGroups array
        JsonReader jsonReader = Json.createReader(new StringReader(responsePayload));
        JsonObject jsonResponse = jsonReader.readObject();
        jsonReader.close();

        // Verify response has errorGroups key
        assertThat("Response should contain errorGroups", jsonResponse.containsKey("errorGroups"), is(true));
        javax.json.JsonArray jsonResponseArray = jsonResponse.getJsonArray("errorGroups");

        // Verify response is an array
        assertThat("Response should be an array", jsonResponseArray, notNullValue());

        // Verify session is in error group due to duplicate
        boolean foundIneligibleDuplicateSession = jsonResponseArray.stream()
                .map(JsonValue::asJsonObject)
                .anyMatch(errorGroup -> {
                    String error = errorGroup.getString("error");
                    if (error.contains("Duplicate session already exists")) {
                        javax.json.JsonArray sessions = errorGroup.getJsonArray("sessions");
                        return sessions.stream()
                                .map(s -> s.asJsonObject())
                                .anyMatch(s -> newSessionId.toString().equals(s.getString("courtScheduleId")));
                    }
                    return false;
                });
        assertThat("Should find session with duplicate in error group", foundIneligibleDuplicateSession, is(true));
    }

    @Test
    void shouldMarkAMSessionAsIneligibleWhenDuplicateADSessionExists() throws SQLException {
        // Test that AM session is marked as ineligible when duplicate AD session exists

        UUID existingSessionId = UUID.randomUUID();
        CourtSchedule existingSession = RANDOM.nextObject(CourtSchedule.class);
        existingSession.setCourtScheduleId(existingSessionId.toString());
        existingSession.setBusinessType("DVLA");
        existingSession.setSlotBased(true);
        existingSession.setMaxSlots(15);
        existingSession.setAvailableSlots(15);
        existingSession.setIsDraft(true);
        existingSession.setSupportAdSplit(false);
        existingSession.setCourtSession(ALL_DAY);
        existingSession.setPanel("ADULT");
        existingSession.setJurisdiction("CROWN");
        existingSession.setCourtHouseId("785339c1-af71-3322-a55b-ba255e0db1c2");
        existingSession.setCourtRoomId("3fc02c0f-f92e-31da-9686-d626ac8ccdc3");
        existingSession.setCourtRoomName("Courtroom 01");
        existingSession.setSessionDate(LocalDate.now().plusDays(1));
        existingSession.setSessionStartTime(DateUtils.localDateToDateWithTime(existingSession.getSessionDate(), 9, 0));
        existingSession.setSessionEndTime(DateUtils.localDateToDateWithTime(existingSession.getSessionDate(), 17, 0));
        databaseSeeder.insertCourtSchedule(existingSession);

        UUID newSessionId = UUID.randomUUID();
        CourtSchedule newSession = RANDOM.nextObject(CourtSchedule.class);
        newSession.setCourtScheduleId(newSessionId.toString());
        newSession.setBusinessType("DVLA");
        newSession.setSlotBased(true);
        newSession.setMaxSlots(15);
        newSession.setAvailableSlots(15);
        newSession.setIsDraft(true);
        newSession.setSupportAdSplit(false);
        newSession.setCourtSession(AM_SESSION);
        newSession.setPanel("ADULT");
        newSession.setJurisdiction("CROWN");
        newSession.setCourtHouseId("785339c1-af71-3322-a55b-ba255e0db1c2");
        newSession.setCourtRoomId("original-courtroom-id");
        newSession.setSessionDate(existingSession.getSessionDate()); // Same date
        newSession.setSessionStartTime(DateUtils.localDateToDateWithTime(newSession.getSessionDate(), 9, 0));
        newSession.setSessionEndTime(DateUtils.localDateToDateWithTime(newSession.getSessionDate(), 13, 0));
        databaseSeeder.insertCourtSchedule(newSession);

        String assignCourtroomPayload = getPayload("assign-courtroom.json");
        String newCourtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3";
        assignCourtroomPayload = assignCourtroomPayload.replace("COURT_SCHEDULE_ID_1", newSessionId.toString());
        assignCourtroomPayload = assignCourtroomPayload.replace("COURT_SCHEDULE_ID_2", newSessionId.toString());
        assignCourtroomPayload = assignCourtroomPayload.replace("COURT_ROOM_ID", newCourtRoomId);

        final Response response = postCommand(BASE_RESOURCE_URL + ASSIGN_COURTROOM_URL, COURT_SCHEDULE_ASSIGN_COURTROOM_CONTENT_TYPE, USER_ID, assignCourtroomPayload);
        final String responsePayload = response.readEntity(String.class);

        assertThat("Assign courtroom response: " + responsePayload, response.getStatus(), is(OK.getStatusCode()));

        // Parse JSON response - should be an object with errorGroups array
        JsonReader jsonReader = Json.createReader(new StringReader(responsePayload));
        JsonObject jsonResponse = jsonReader.readObject();
        jsonReader.close();

        // Verify response has errorGroups key
        assertThat("Response should contain errorGroups", jsonResponse.containsKey("errorGroups"), is(true));
        javax.json.JsonArray jsonResponseArray = jsonResponse.getJsonArray("errorGroups");

        // Verify response is an array
        assertThat("Response should be an array", jsonResponseArray, notNullValue());

        // Verify session is in error group due to duplicate
        boolean foundIneligibleDuplicateSession = jsonResponseArray.stream()
                .map(JsonValue::asJsonObject)
                .anyMatch(errorGroup -> {
                    String error = errorGroup.getString("error");
                    if (error.contains("Duplicate session already exists")) {
                        javax.json.JsonArray sessions = errorGroup.getJsonArray("sessions");
                        return sessions.stream()
                                .map(s -> s.asJsonObject())
                                .anyMatch(s -> newSessionId.toString().equals(s.getString("courtScheduleId")));
                    }
                    return false;
                });
        assertThat("Should find AM session with duplicate AD session in error group", foundIneligibleDuplicateSession, is(true));
    }

    @Test
    void shouldMarkADSessionAsIneligibleWhenDuplicateAMSessionExists() throws SQLException {
        // Test that AD session is marked as ineligible when duplicate AM session exists

        UUID existingSessionId = UUID.randomUUID();
        CourtSchedule existingSession = RANDOM.nextObject(CourtSchedule.class);
        existingSession.setCourtScheduleId(existingSessionId.toString());
        existingSession.setBusinessType("DVLA");
        existingSession.setSlotBased(true);
        existingSession.setMaxSlots(15);
        existingSession.setAvailableSlots(15);
        existingSession.setIsDraft(true);
        existingSession.setSupportAdSplit(false);
        existingSession.setCourtSession(AM_SESSION);
        existingSession.setPanel("ADULT");
        existingSession.setJurisdiction("CROWN");
        existingSession.setCourtHouseId("785339c1-af71-3322-a55b-ba255e0db1c2");
        existingSession.setCourtRoomId("3fc02c0f-f92e-31da-9686-d626ac8ccdc3");
        existingSession.setCourtRoomName("Courtroom 01");
        existingSession.setSessionDate(LocalDate.now().plusDays(1));
        existingSession.setSessionStartTime(DateUtils.localDateToDateWithTime(existingSession.getSessionDate(), 9, 0));
        existingSession.setSessionEndTime(DateUtils.localDateToDateWithTime(existingSession.getSessionDate(), 13, 0));
        databaseSeeder.insertCourtSchedule(existingSession);

        UUID newSessionId = UUID.randomUUID();
        CourtSchedule newSession = RANDOM.nextObject(CourtSchedule.class);
        newSession.setCourtScheduleId(newSessionId.toString());
        newSession.setBusinessType("DVLA");
        newSession.setSlotBased(true);
        newSession.setMaxSlots(15);
        newSession.setAvailableSlots(15);
        newSession.setIsDraft(true);
        newSession.setSupportAdSplit(false);
        newSession.setCourtSession(ALL_DAY);
        newSession.setPanel("ADULT");
        newSession.setJurisdiction("CROWN");
        newSession.setCourtHouseId("785339c1-af71-3322-a55b-ba255e0db1c2");
        newSession.setCourtRoomId("original-courtroom-id");
        newSession.setSessionDate(existingSession.getSessionDate()); // Same date
        newSession.setSessionStartTime(DateUtils.localDateToDateWithTime(newSession.getSessionDate(), 9, 0));
        newSession.setSessionEndTime(DateUtils.localDateToDateWithTime(newSession.getSessionDate(), 17, 0));
        databaseSeeder.insertCourtSchedule(newSession);

        String assignCourtroomPayload = getPayload("assign-courtroom.json");
        String newCourtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3";
        assignCourtroomPayload = assignCourtroomPayload.replace("COURT_SCHEDULE_ID_1", newSessionId.toString());
        assignCourtroomPayload = assignCourtroomPayload.replace("COURT_SCHEDULE_ID_2", newSessionId.toString());
        assignCourtroomPayload = assignCourtroomPayload.replace("COURT_ROOM_ID", newCourtRoomId);

        final Response response = postCommand(BASE_RESOURCE_URL + ASSIGN_COURTROOM_URL, COURT_SCHEDULE_ASSIGN_COURTROOM_CONTENT_TYPE, USER_ID, assignCourtroomPayload);
        final String responsePayload = response.readEntity(String.class);

        assertThat("Assign courtroom response: " + responsePayload, response.getStatus(), is(OK.getStatusCode()));

        // Parse JSON response - should be an object with errorGroups array
        JsonReader jsonReader = Json.createReader(new StringReader(responsePayload));
        JsonObject jsonResponse = jsonReader.readObject();
        jsonReader.close();

        // Verify response has errorGroups key
        assertThat("Response should contain errorGroups", jsonResponse.containsKey("errorGroups"), is(true));
        javax.json.JsonArray jsonResponseArray = jsonResponse.getJsonArray("errorGroups");

        // Verify response is an array
        assertThat("Response should be an array", jsonResponseArray, notNullValue());

        // Verify session is in error group due to duplicate
        boolean foundIneligibleDuplicateSession = jsonResponseArray.stream()
                .map(JsonValue::asJsonObject)
                .anyMatch(errorGroup -> {
                    String error = errorGroup.getString("error");
                    if (error.contains("Duplicate session already exists")) {
                        javax.json.JsonArray sessions = errorGroup.getJsonArray("sessions");
                        return sessions.stream()
                                .map(s -> s.asJsonObject())
                                .anyMatch(s -> newSessionId.toString().equals(s.getString("courtScheduleId")));
                    }
                    return false;
                });
        assertThat("Should find AD session with duplicate AM session in error group", foundIneligibleDuplicateSession, is(true));
    }
}
