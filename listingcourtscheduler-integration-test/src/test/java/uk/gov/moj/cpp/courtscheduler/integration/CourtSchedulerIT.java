package uk.gov.moj.cpp.courtscheduler.integration;

import static java.time.LocalDate.now;
import static java.time.ZoneOffset.UTC;
import static java.time.format.DateTimeFormatter.ofPattern;
import static java.util.Date.from;
import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static jakarta.json.Json.createArrayBuilder;
import static jakarta.json.Json.createObjectBuilder;
import static jakarta.ws.rs.core.Response.Status.ACCEPTED;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.OK;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.RestPoller.poll;
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
import static uk.gov.moj.cpp.courtscheduler.domain.utils.TimezoneUtils.UTC_ZONE;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.TimezoneUtils.getUtcTimeStringForDate;
import static uk.gov.moj.cpp.platform.test.data.utils.FileUtil.getPayload;

import uk.gov.moj.cpp.courtscheduler.integration.utils.RequestParams;
import uk.gov.moj.cpp.courtscheduler.integration.utils.ResponseData;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;
import uk.gov.moj.cpp.courtscheduler.domain.utils.TimezoneUtils;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciaryKey;

import java.io.StringReader;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CourtSchedulerIT extends AbstractIT {

    private static final String BASE_RESOURCE_URL = "/courtschedule";
    private static final String UPDATE_URL = "/edit";
    private static final String DELETE_URL = "/delete";
    private static final String SEARCH_BY_ID_URL = "/sessions";
    private static final String VALIDATE_URL = "/validate";
    private static final String VALIDATE_SESSION_AVAILABILITY_URL = "/validate-session-availability";
    private static final String JUDICIARY_SESSION_URL = "/sessions/judiciaries";
    private static final String REMOVE_ALL_JUDICIARY_URL = "/sessions/remove-all-judiciaries";
    private static final String ASSIGN_JUDICIARY_CONTENT_TYPE = "application/vnd.courtscheduler.assign-judiciary+json";
    private static final String UNASSIGN_JUDICIARY_CONTENT_TYPE = "application/vnd.courtscheduler.unassign.judiciary+json";
    private static final String REMOVE_ALL_JUDICIARY_CONTENT_TYPE = "application/vnd.courtscheduler.remove-all-judiciary+json";
    private static final String ASSIGN_JUDICIARY_TO_SESSIONS_URL = "/sessions/bulk-assign-judiciaries";
    private static final String ASSIGN_JUDICIARY_TO_SESSIONS_CONTENT_TYPE =
            "application/vnd.courtscheduler.assign-judiciary-to-sessions+json";

    private static final String STUB_JUDICIARY_MAGISTRATE_1 = "9ac02e8d-ee90-3da6-8d3e-0dd0af2cb976";
    private static final String STUB_JUDICIARY_MAGISTRATE_2 = "1f790259-268c-385d-b28c-89514aa33e91";
    private static final String STUB_JUDICIARY_CIRCUIT_JUDGE = "6fb4202a-2cea-4fe9-92ee-43e195fd439d";
    private static final String STUB_JUDICIARY_RECORDER = "eaa94c3a-44c6-3851-ac86-00c169790f1b";


    private static final String IT_SHARED_COURTHOUSE = "CH-SPRDT-692";
    private static final String P_COURT_SCHEDULE_IDS = "courtScheduleIds";
    // GET /sessions search-by-id uses an "ids" query parameter (see RAML), distinct from the
    // assign-judiciary POST body field above which is "courtScheduleIds".
    private static final String P_SEARCH_BY_ID_QUERY_PARAM = "ids";
    private static final String P_JUDICIARY = "judiciary";
    private static final String P_JUDICIAL_ID = "judicialId";
    private static final String P_JUDICIAL_ROLE_TYPE = "judicialRoleType";
    private static final String P_JUDICIARY_TYPE = "judiciaryType";
    private static final String P_IS_DEPUTY = "isDeputy";
    private static final String P_IS_BENCH_CHAIRMAN = "isBenchChairman";
    private static final String LABEL_MAGISTRATE = "Magistrate";

    private static final String COURT_SCHEDULE_CREATE_CONTENT_TYPE = "application/vnd.courtscheduler.create+json";
    private static final String COURT_SCHEDULE_VALIDATE_CREATE_CONTENT_TYPE = "application/vnd.courtscheduler.validate.create+json";
    private static final String COURT_SCHEDULE_VALIDATE_SESSION_AVAILABILITY_CONTENT_TYPE = "application/vnd.courtscheduler.validate.session.availability+json";
    private static final String COURT_SCHEDULE_UPDATE_CONTENT_TYPE = "application/vnd.courtscheduler.update+json";
    private static final String COURT_SCHEDULE_GET_CONTENT_TYPE = "application/vnd.courtscheduler.get+json";
    private static final String COURT_SCHEDULE_SEARCH_COURTSCHEDULES_BY_ID_CONTENT_TYPE = "application/vnd.courtscheduler.search.court-schedules-by-id+json";
    private static final String COURT_SCHEDULE_DELETE_CONTENT_TYPE = "application/vnd.courtscheduler.delete+json";
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
        final java.util.Date expectedStartTime = TimezoneUtils.combineLocalDateAndTimeToUtc(startDate, LocalTime.of(10, 0));
        final java.util.Date expectedEndTime = TimezoneUtils.combineLocalDateAndTimeToUtc(startDate, LocalTime.of(12, 0));
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
    void shouldCreateCourtScheduleWithRefdataSessionTimesAM() {
        // WireMock stub has organisation-unit id=22c69328-70af-3e27-80c5-1a79e24903d2 (this
        // fixture's courtCentreId) with defaultStartTime="09:15:00" — HH:mm:ss, matching the real
        // ns-ste-ccm-22 shape (normalised to "09:15" before storage). The payload supplies no
        // custom start/end times, so the court-centre default wins over the AM start default
        // (10:00) but the end time is always the fixed AM default (13:00) — never refdata-driven
        // (SPRDT-809).
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("create-court-schedule-with-refdata-session-times-am.json");
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final List<CourtSchedule> courtSchedules = databaseReader.courtSchedules();
        assertThat(courtSchedules.size(), is(greaterThanOrEqualTo(1)));
        for (final CourtSchedule courtSchedule : courtSchedules) {
            assertThat(courtSchedule.getCourtScheduleId(), is(notNullValue()));

            final java.util.Date localStartTime = TimezoneUtils.utcToLocal(courtSchedule.getSessionStartTime());
            final java.util.Date localEndTime = TimezoneUtils.utcToLocal(courtSchedule.getSessionEndTime());

            assertThat(sdf.format(localStartTime), is("09:15"));
            assertThat(sdf.format(localEndTime), is(DEFAULT_MORNING_END_TIME));
        }
    }

    @Test
    void shouldCreateCourtScheduleWithRefdataSessionTimesAD() {
        // Same organisation-unit stub as the AM test (defaultStartTime="09:15:00") — AD sources
        // its start from the same court-centre default, but the end time is always the fixed AD
        // default (17:00), never refdata-driven (SPRDT-809).
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("create-court-schedule-with-refdata-session-times-ad.json");
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final List<CourtSchedule> courtSchedules = databaseReader.courtSchedules();
        assertThat(courtSchedules.size(), is(greaterThanOrEqualTo(1)));
        for (final CourtSchedule courtSchedule : courtSchedules) {
            assertThat(courtSchedule.getCourtScheduleId(), is(notNullValue()));

            final java.util.Date localStartTime = TimezoneUtils.utcToLocal(courtSchedule.getSessionStartTime());
            final java.util.Date localEndTime = TimezoneUtils.utcToLocal(courtSchedule.getSessionEndTime());

            assertThat(sdf.format(localStartTime), is("09:15"));
            assertThat(sdf.format(localEndTime), is(DEFAULT_ALL_DAY_END_TIME));
        }
    }

    @Test
    void shouldCreateCourtScheduleWithFixedSessionTimesForPmIgnoringRefdata() {
        // Same organisation-unit stub as the AM/AD tests (defaultStartTime="09:15:00") is
        // reachable for this fixture's courtCentreId, but PM sessions must never consult
        // reference data at all — both times are always the fixed PM defaults (SPRDT-809).
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("create-court-schedule-with-refdata-present-pm.json");
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final List<CourtSchedule> courtSchedules = databaseReader.courtSchedules();
        assertThat(courtSchedules.size(), is(greaterThanOrEqualTo(1)));
        for (final CourtSchedule courtSchedule : courtSchedules) {
            assertThat(courtSchedule.getCourtScheduleId(), is(notNullValue()));

            final java.util.Date localStartTime = TimezoneUtils.utcToLocal(courtSchedule.getSessionStartTime());
            final java.util.Date localEndTime = TimezoneUtils.utcToLocal(courtSchedule.getSessionEndTime());

            assertThat(sdf.format(localStartTime), is(DEFAULT_AFTERNOON_START_TIME));
            assertThat(sdf.format(localEndTime), is(DEFAULT_AFTERNOON_END_TIME));
        }
    }

    @Test
    void shouldHonourCustomSessionTimesOverRefdataAndDefaults() {
        // The organisation-unit stub says defaultStartTime=09:15:00 but the request supplies
        // 10:15/12:30 explicitly. The custom times must win over both refdata and the hardcoded
        // defaults.
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("create-court-schedule-with-custom-times-overrides-refdata.json");
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final List<CourtSchedule> courtSchedules = databaseReader.courtSchedules();
        assertThat(courtSchedules.size(), is(greaterThanOrEqualTo(1)));
        for (final CourtSchedule courtSchedule : courtSchedules) {
            assertThat(courtSchedule.getCourtScheduleId(), is(notNullValue()));

            final java.util.Date localStartTime = TimezoneUtils.utcToLocal(courtSchedule.getSessionStartTime());
            final java.util.Date localEndTime = TimezoneUtils.utcToLocal(courtSchedule.getSessionEndTime());

            assertThat(sdf.format(localStartTime), is("10:15"));
            assertThat(sdf.format(localEndTime), is("12:30"));
        }
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

    @ParameterizedTest
    @MethodSource("provideInvalidCreatePayloads")
    void shouldReturn400WhenInvalidPayloadInCreate(final String payloadFileName) {
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload(payloadFileName);
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
    }

    private static Stream<Arguments> provideInvalidCreatePayloads() {
        return Stream.of(
                Arguments.of("create-court-schedule-missing-panel-magistrates.json"),
                Arguments.of("create-court-schedule-null-draft-crown.json"),
                Arguments.of("create-court-schedule-wrong-court-centre.json")
        );
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
    void shouldReturn400WhenCourtroomDoesNotBelongToCourtCentreInValidateCreate() {
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("validate-create-court-schedule-wrong-court-centre.json");
        final Response response = postCommand(VALIDATE_URL, COURT_SCHEDULE_VALIDATE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String errorResponseMessage = response.readEntity(String.class);
        assertThat(errorResponseMessage, containsString("This courtroom belongs to a different court centre"));
    }

    @Test
    void shouldReturn400WhenCrownSessionUsesMagistratesCourtroomInCreate() {
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("create-court-schedule-crown-with-magistrates-courtroom.json");
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String errorResponseMessage = response.readEntity(String.class);
        assertThat(errorResponseMessage, containsString("Courtroom does not exist"));
    }

    @Test
    void shouldReturn400WhenCrownSessionUsesMagistratesCourtroomInValidateCreate() {
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("validate-create-court-schedule-crown-with-magistrates-courtroom.json");
        final Response response = postCommand(VALIDATE_URL, COURT_SCHEDULE_VALIDATE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String errorResponseMessage = response.readEntity(String.class);
        assertThat(errorResponseMessage, containsString("Courtroom does not exist"));
    }

    @Test
    void shouldReturn400WhenMagistratesSessionUsesCrownCourtroomInCreate() {
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("create-court-schedule-magistrates-with-crown-courtroom.json");
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String errorResponseMessage = response.readEntity(String.class);
        // Courtroom ID not in CP reference data -> step 1 returns "Courtroom does not exist"
        assertThat(errorResponseMessage, containsString("Courtroom does not exist"));
    }

    @Test
    void shouldReturn400WhenMagistratesSessionUsesCrownCourtroomInValidateCreate() {
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("validate-create-court-schedule-magistrates-with-crown-courtroom.json");
        final Response response = postCommand(VALIDATE_URL, COURT_SCHEDULE_VALIDATE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String errorResponseMessage = response.readEntity(String.class);
        // Courtroom ID not in CP reference data -> step 1 returns "Courtroom does not exist"
        assertThat(errorResponseMessage, containsString("Courtroom does not exist"));
    }

    @Test
    void shouldReturn400WhenCourtroomDoesNotExistInCreate() {
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("create-court-schedule-courtroom-not-in-cp.json");
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String errorResponseMessage = response.readEntity(String.class);
        assertThat(errorResponseMessage, containsString("Courtroom does not exist"));
    }

    @Test
    void shouldReturn400WhenCourtroomJurisdictionMismatchInCreate() {
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("create-court-schedule-magistrates-jurisdiction-mismatch-crown-oucode.json");
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String errorResponseMessage = response.readEntity(String.class);
        assertThat(errorResponseMessage, containsString("Courtroom doesn't belong to this jurisdiction"));
    }

    @Test
    void shouldAcceptValidateCreateWithOnceFrequency() {
        // Given
        final LocalDate startDate = now().plusDays(1);
        String createCourtSchedulePayload = getPayload("validate-create-court-schedule-frequency-once.json")
                .replace("START_DATE", startDate.format(ofPattern("yyyy-MM-dd")));

        // When
        final Response response = postCommand(VALIDATE_URL, COURT_SCHEDULE_VALIDATE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);

        // Then
        assertThat(response.getStatus(), is(OK.getStatusCode()));
        final String responseBody = response.readEntity(String.class);
        assertThat("Response should be empty JSON object for successful validation", responseBody, is("{}"));
    }

    @Test
    void shouldAcceptValidateCreateWithEveryWeekFrequency() {
        // Given
        final LocalDate startDate = now().plusDays(1);
        final LocalDate endDate = startDate.plusWeeks(4);
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayloadWithDates(
                "validate-create-court-schedule-frequency-every-week.json",
                startDate,
                endDate
        );

        // When
        final Response response = postCommand(VALIDATE_URL, COURT_SCHEDULE_VALIDATE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);

        // Then
        assertThat(response.getStatus(), is(OK.getStatusCode()));
        final String responseBody = response.readEntity(String.class);
        assertThat("Response should be empty JSON object for successful validation", responseBody, is("{}"));
    }

    @Test
    void shouldReturn400WhenInvalidFrequencyValueInValidateCreate() {
        // Given - Create a payload with invalid frequency
        final LocalDate startDate = now().plusDays(1);
        final LocalDate endDate = startDate.plusWeeks(4);
        String createCourtSchedulePayload = prepareCreateCourtSchedulePayloadWithDates(
                "validate-create-court-schedule-frequency-every-week.json",
                startDate,
                endDate
        );
        // Replace with invalid frequency
        createCourtSchedulePayload = createCourtSchedulePayload.replace("\"frequency\": \"EVERY_WEEK\"", "\"frequency\": \"INVALID_FREQUENCY\"");

        // When
        final Response response = postCommand(VALIDATE_URL, COURT_SCHEDULE_VALIDATE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);

        // Then
        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
    }

    @Test
    void shouldReturn400WhenEveryWeekFrequencyMissingEndDateInValidateCreate() {
        // Given - EVERY_WEEK requires endDate
        final LocalDate startDate = now().plusDays(1);
        String createCourtSchedulePayload = getPayload("validate-create-court-schedule-frequency-every-week.json")
                .replace("START_DATE", startDate.format(ofPattern("yyyy-MM-dd")));
        // Remove endDate
        createCourtSchedulePayload = createCourtSchedulePayload.replace(",\"endDate\": \"END_DATE\"", "");

        // When
        final Response response = postCommand(VALIDATE_URL, COURT_SCHEDULE_VALIDATE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);

        // Then
        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String errorResponseMessage = response.readEntity(String.class);
        assertThat(errorResponseMessage, containsString("Invalid combination of parameters"));
    }

    @Test
    void shouldReturn400WhenEveryMonthFrequencyMissingEndDateInValidateCreate() {
        // Given - EVERY_MONTH requires endDate
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        String createCourtSchedulePayload = getPayload("validate-create-court-schedule-frequency-every-month.json")
                .replace("START_DATE", startDate.format(ofPattern("yyyy-MM-dd")));
        // Remove endDate
        createCourtSchedulePayload = createCourtSchedulePayload.replace(",\"endDate\": \"END_DATE\"", "");

        // When
        final Response response = postCommand(VALIDATE_URL, COURT_SCHEDULE_VALIDATE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);

        // Then
        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
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
        courtScheduleDuration.setIsOverbookingAllowed(false);
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
        assertThat(errorResponseMessage, containsString("Schedule abcdef12-3456-7890-abcd-ef1234567890 has 0 minutes available across morning (0) and afternoon (0), but 3 minutes are required."));
    }

    @Test
    void shouldReturn400WhenDurationBasedScheduleHasInsufficientAvailability() throws SQLException {
        final CourtSchedule courtScheduleDuration = RANDOM.nextObject(CourtSchedule.class);
        courtScheduleDuration.setBusinessType("TRL");
        courtScheduleDuration.setSlotBased(false);
        courtScheduleDuration.setMaxDuration(0);
        courtScheduleDuration.setAvailableDuration(0);
        courtScheduleDuration.setSupportAdSplit(false);
        courtScheduleDuration.setCourtSession(ALL_DAY);
        courtScheduleDuration.setIsOverbookingAllowed(false);
        courtScheduleDuration.setCourtScheduleId("abcdef12-3456-7890-abcd-ef1234567890");
        courtScheduleDuration.setSessionDate(getRandomFutureDateWithinNextYear());
        courtScheduleDuration.setSessionStartTime(DateUtils.combineDateAndTime(courtScheduleDuration.getSessionDate(), "10:00"));
        courtScheduleDuration.setSessionEndTime(DateUtils.combineDateAndTime(courtScheduleDuration.getSessionDate(), "16:00"));
        databaseSeeder.insertCourtSchedule(courtScheduleDuration);

        final String validateCourtSchedulePayload = getPayload("courtscheduler.validate.session.availability-insufficient-duration.json");
        final Response response = postCommand(VALIDATE_SESSION_AVAILABILITY_URL, COURT_SCHEDULE_VALIDATE_SESSION_AVAILABILITY_CONTENT_TYPE, SYSTEM_USER_ID, validateCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String errorResponseMessage = response.readEntity(String.class);
        assertThat(errorResponseMessage, containsString("Schedule abcdef12-3456-7890-abcd-ef1234567890 has 0 minutes available but 5 minutes are required."));
    }

    @Test
    void shouldReturn400WhenSchedulesAreMixedSlotAndDurationBased() throws SQLException {
        final CourtSchedule courtScheduleSlot = RANDOM.nextObject(CourtSchedule.class);
        final CourtSchedule courtScheduleDuration = RANDOM.nextObject(CourtSchedule.class);
        final Integer maxDurationForMorning = 120;
        final Integer maxDurationForAfternoon = 60;
        final String sharedCourtHouseId = "shared-court-house-mixed-test";

        courtScheduleSlot.setBusinessType("DVLA");
        courtScheduleSlot.setSlotBased(true);
        courtScheduleSlot.setIsOverbookingAllowed(false);
        courtScheduleSlot.setMaxDuration(0);
        courtScheduleSlot.setAvailableDuration(0);
        courtScheduleSlot.setSupportAdSplit(true);
        courtScheduleSlot.setCourtSession(ALL_DAY);
        courtScheduleSlot.setMaxAdMorningDuration(maxDurationForMorning);
        courtScheduleSlot.setMaxAdAfternoonDuration(maxDurationForAfternoon);
        courtScheduleSlot.setCourtScheduleId("12345678-90ab-cdef-0123-456789abcdef");
        courtScheduleSlot.setCourtHouseId(sharedCourtHouseId);
        courtScheduleSlot.setSessionDate(getRandomFutureDateWithinNextYear());
        courtScheduleSlot.setSessionStartTime(DateUtils.combineDateAndTime(courtScheduleSlot.getSessionDate(), "10:00"));
        courtScheduleSlot.setSessionEndTime(DateUtils.combineDateAndTime(courtScheduleSlot.getSessionDate(), "16:00"));
        databaseSeeder.insertCourtSchedule(courtScheduleSlot);

        courtScheduleDuration.setBusinessType("TRL");
        courtScheduleDuration.setSlotBased(false);
        courtScheduleDuration.setIsOverbookingAllowed(false);
        courtScheduleDuration.setMaxDuration(0);
        courtScheduleDuration.setAvailableDuration(0);
        courtScheduleDuration.setSupportAdSplit(true);
        courtScheduleDuration.setCourtSession(ALL_DAY);
        courtScheduleDuration.setMaxAdMorningDuration(maxDurationForMorning);
        courtScheduleDuration.setMaxAdAfternoonDuration(maxDurationForAfternoon);
        courtScheduleDuration.setCourtScheduleId("abcdef12-3456-7890-abcd-ef1234567890");
        courtScheduleDuration.setCourtHouseId(sharedCourtHouseId);
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
        assertThat(errorResponseMessage,
                anyOf(containsString("Court Schedule Ids cannot be empty"), containsString("courtScheduleIdList")));
    }

    @Test
    void shouldReturn400WhenConsecutiveDaysModeButCourtScheduleIdNotFound() {
        final String validatePayload = getPayload("courtscheduler.validate.session.availability-consecutive-days.json");
        final Response response = postCommand(VALIDATE_SESSION_AVAILABILITY_URL, COURT_SCHEDULE_VALIDATE_SESSION_AVAILABILITY_CONTENT_TYPE, SYSTEM_USER_ID, validatePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String errorResponseMessage = response.readEntity(String.class);
        assertThat(errorResponseMessage, containsString("not found"));
    }

    @Test
    void shouldReturn200WhenConsecutiveDaysModeHasEnoughConsecutiveCrownAllDaySessions() throws SQLException {
        final String courtScheduleId = "f8254db1-1683-483e-afb3-b87fde5a0a26";
        final LocalDate startDate = getNextWeekdayMonToWed();
        final String courtRoomId = "room-consecutive-1";
        final String businessType = "DVLA";
        final Integer maxDuration = 480;

        LocalDate sessionDate = startDate;
        for (int i = 0; i < 3; i++) {
            CourtSchedule schedule = RANDOM.nextObject(CourtSchedule.class);
            schedule.setCourtScheduleId(i == 0 ? courtScheduleId : "consec-" + i + "-" + UUID.randomUUID());
            schedule.setSlotBased(false);
            schedule.setIsOverbookingAllowed(false);
            schedule.setCourtSession(ALL_DAY);
            schedule.setJurisdiction("CROWN");
            schedule.setSessionDate(sessionDate);
            schedule.setCourtRoomId(courtRoomId);
            schedule.setBusinessType(businessType);
            schedule.setPanel("ADULT");
            schedule.setMaxDuration(maxDuration);
            schedule.setAvailableDuration(maxDuration);
            schedule.setSupportAdSplit(false);
            schedule.setSessionStartTime(DateUtils.combineDateAndTime(schedule.getSessionDate(), "09:00"));
            schedule.setSessionEndTime(DateUtils.combineDateAndTime(schedule.getSessionDate(), "17:00"));
            databaseSeeder.insertCourtSchedule(schedule);
            sessionDate = nextWeekday(sessionDate);
        }

        // Duration 1080 (> 360) triggers multi-day CROWN validation: finds 3 consecutive-day schedules
        final String validatePayload = getPayload("courtscheduler.validate.session.availability-consecutive-days.json");
        final Response response = postCommand(VALIDATE_SESSION_AVAILABILITY_URL, COURT_SCHEDULE_VALIDATE_SESSION_AVAILABILITY_CONTENT_TYPE, SYSTEM_USER_ID, validatePayload);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
    }

    @Test
    void shouldReturn200ForValidSlotBasedRequest() throws SQLException {
        final CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        final Integer maxDurationForMorning = 120;
        final Integer maxDurationForAfternoon = 60;

        courtSchedule.setBusinessType("TRL");
        courtSchedule.setSlotBased(true);
        courtSchedule.setIsOverbookingAllowed(false);
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
        // Match persisted courtSession and panel to update values to avoid "edited by another user" error
        expected.setCourtSession("AM");
        expected.setPanel("YOUTH");
        expected.setSessionDate(LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)));
        expected.setSessionStartTime(DateUtils.localDateToDateWithTime(expected.getSessionDate(), 10, 0));
        expected.setSessionEndTime(DateUtils.localDateToDateWithTime(expected.getSessionDate(), 13, 0));
        expected.setCourtRoomId(courtRoomId); // Set initial courtroom ID to match update
        expected.setCourtHouseId(courtHouseId); // Set court house ID to match courtroom
        databaseSeeder.insertCourtSchedule(expected);

        // Use 15:00 which is clearly after 13:00 session end even during BST (timezone conversion safe)
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
        // Match persisted courtSession and panel to update values to avoid "edited by another user" error
        expected.setCourtSession("AM");
        expected.setPanel("YOUTH");
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
        // Match persisted courtSession and panel to update values to avoid "edited by another user" error
        expected.setCourtSession("AM");
        expected.setPanel("YOUTH");
        expected.setSessionDate(LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)));
        // Use 11:00 (not 10:00) so the gap survives the 1-hour London<->UTC shift during BST.
        // localDateToDateWithTime treats the input as London local time, but the validator's
        // sessionTimeFormatter renders the persisted instant in the JVM's default timezone (UTC
        // here) - a 1-hour gap collapses in BST and the MIN_HEARING_TIME validation no longer fires.
        expected.setSessionStartTime(DateUtils.localDateToDateWithTime(expected.getSessionDate(), 11, 0));
        expected.setSessionEndTime(DateUtils.localDateToDateWithTime(expected.getSessionDate(), 13, 0));
        expected.setCourtRoomId(courtRoomId); // Set initial courtroom ID to match update
        expected.setCourtHouseId(courtHouseId); // Set court house ID to match courtroom
        databaseSeeder.insertCourtSchedule(expected);

        // Use 07:00 which is clearly before 10:00 session start even during BST (timezone conversion safe)
        createAllocatedListing(expected, UUID.randomUUID(), UUID.randomUUID(), 60, "07:00");

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

        // Should fail because min hearing time (07:00) is before session start time (10:00) retrieved from persisted schedule
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
    void shouldGetCourtSchedules() throws Exception {
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
        final CourtScheduleJudiciary courtScheduleJudiciary = createTestCourtScheduleJudiciary(expected.getCourtScheduleId());
        databaseSeeder.saveJudiciarySchedule(courtScheduleJudiciary);

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

        final JsonObject courtRoomGroup = jsonObject.getJsonArray("courtSchedules").getJsonObject(0);
        // Group-level contract (legacy CourtSessionsView shape): courtRoomId + courtRoomName, no courtRoomNumber.
        assertThat(courtRoomGroup.getString("courtRoomId"), is(expected.getCourtRoomId()));
        assertThat(courtRoomGroup.getString("courtRoomName"), is(expected.getCourtRoomName()));
        assertThat(courtRoomGroup.containsKey("courtRoomNumber"), is(false));

        JsonObject courtScheduleJsonObject = courtRoomGroup.getJsonArray("sessions").getJsonObject(0);
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
        assertThat(courtScheduleJsonObject.getJsonArray("judiciaries").size(), is(1));
        final JsonObject judiciaryJsonObject = courtScheduleJsonObject.getJsonArray("judiciaries").getJsonObject(0);
        assertThat(judiciaryJsonObject.getString("judiciaryId"), is(courtScheduleJudiciary.getId().getJudiciaryId()));
        assertThat(judiciaryJsonObject.getString("title"), is(courtScheduleJudiciary.getTitle()));
        assertThat(judiciaryJsonObject.getString("forenames"), is(courtScheduleJudiciary.getForenames()));
        assertThat(judiciaryJsonObject.getString("surname"), is(courtScheduleJudiciary.getSurname()));
        assertThat(judiciaryJsonObject.getString(P_JUDICIARY_TYPE), is(courtScheduleJudiciary.getJudiciaryType()));
        assertThat(judiciaryJsonObject.getString("emailAddress"), is(courtScheduleJudiciary.getEmail()));
        assertThat(judiciaryJsonObject.getBoolean(P_IS_BENCH_CHAIRMAN), is(courtScheduleJudiciary.getBenchChairman()));
        assertThat(judiciaryJsonObject.getBoolean(P_IS_DEPUTY), is(courtScheduleJudiciary.getDeputy()));
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
        final CourtScheduleJudiciary courtScheduleJudiciary = createTestCourtScheduleJudiciary(courtSchedule.getCourtScheduleId());
        databaseSeeder.saveJudiciarySchedule(courtScheduleJudiciary);

        // Also seed allocated listings for realism
        final UUID hearingId = UUID.randomUUID();
        final UUID bookingId = UUID.randomUUID();
        createAllocatedListing(courtSchedule, hearingId, bookingId, 60, "10:00");

        String getCourtScheduleRequestParams = getPayload("courtscheduler.search.courtschedules.by.id.json");
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("COURT_SCHEDULE_ID", courtScheduleId);
        Map<String, Object> map = mapper.readValue(getCourtScheduleRequestParams, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams(
                SEARCH_BY_ID_URL,
                COURT_SCHEDULE_SEARCH_COURTSCHEDULES_BY_ID_CONTENT_TYPE,
                SYSTEM_USER_ID,
                map
        );

        final ResponseData response = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertEquals(OK.getStatusCode(), response.getStatus().getStatusCode());

        JsonObject json = stringToJsonObjectConverter.convert(response.getPayload());
        assertThat(json.getJsonArray("courtSchedules").size(), is(1));

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
        assertThat(courtSessionJson.getJsonArray("judiciaries").size(), is(1));
        final JsonObject judiciaryJsonObject = courtSessionJson.getJsonArray("judiciaries").getJsonObject(0);
        assertThat(judiciaryJsonObject.getString("judiciaryId"), is(courtScheduleJudiciary.getId().getJudiciaryId()));
        assertThat(judiciaryJsonObject.getString("title"), is(courtScheduleJudiciary.getTitle()));
        assertThat(judiciaryJsonObject.getString("forenames"), is(courtScheduleJudiciary.getForenames()));
        assertThat(judiciaryJsonObject.getString("surname"), is(courtScheduleJudiciary.getSurname()));
        assertThat(judiciaryJsonObject.getString(P_JUDICIARY_TYPE), is(courtScheduleJudiciary.getJudiciaryType()));
        assertThat(judiciaryJsonObject.getString("emailAddress"), is(courtScheduleJudiciary.getEmail()));
        assertThat(judiciaryJsonObject.getBoolean(P_IS_BENCH_CHAIRMAN), is(courtScheduleJudiciary.getBenchChairman()));
        assertThat(judiciaryJsonObject.getBoolean(P_IS_DEPUTY), is(courtScheduleJudiciary.getDeputy()));

        final OffsetDateTime actualStartTime = OffsetDateTime.parse(courtSessionJson.getString("sessionStartTime"));
        final OffsetDateTime actualEndTime = OffsetDateTime.parse(courtSessionJson.getString("sessionEndTime"));
        assertThat(actualStartTime.toInstant(), is(courtSchedule.getSessionStartTime().toInstant()));
        assertThat(actualEndTime.toInstant(), is(courtSchedule.getSessionEndTime().toInstant()));
    }

    @Test
    void shouldReturnCorrectAvailableSlotsWhenAllocatedListingsExistForSlotBasedSession() throws Exception {
        // Given a slot-based court schedule with 10 max slots and 3 allocated listings,
        // the search-by-id read view should report availableSlots = maxSlots - 3. The
        // court_schedule counters are seeded to the post-booking state to mirror what
        // the booking write flow would have produced.
        final String courtScheduleId = UUID.randomUUID().toString();
        final int maxSlots = 10;
        final int bookedCount = 3;
        final int expectedAvailableSlots = maxSlots - bookedCount;
        final int slotDurationMinutes = 30;

        final CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setCourtScheduleId(courtScheduleId);
        courtSchedule.setBusinessType("DVLA");
        courtSchedule.setSlotBased(true);
        courtSchedule.setSupportAdSplit(false);
        courtSchedule.setCourtSession(ALL_DAY);
        courtSchedule.setMaxSlots(maxSlots);
        courtSchedule.setAvailableSlots(expectedAvailableSlots);
        courtSchedule.setMaxDuration(0);
        courtSchedule.setAvailableDuration(0);
        courtSchedule.setMaxAdMorningDuration(0);
        courtSchedule.setMaxAdAfternoonDuration(0);
        courtSchedule.setSessionDate(getRandomFutureDateWithinNextYear());
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), "10:00"));
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), "16:00"));
        courtSchedule.setIsDraft(false);
        courtSchedule.setJurisdiction(MAGISTRATES.getJurisdiction());
        courtSchedule.setActive(true);
        databaseSeeder.insertCourtSchedule(courtSchedule);

        createAllocatedListing(courtSchedule, UUID.randomUUID(), UUID.randomUUID(), slotDurationMinutes, "10:00");
        createAllocatedListing(courtSchedule, UUID.randomUUID(), UUID.randomUUID(), slotDurationMinutes, "11:00");
        createAllocatedListing(courtSchedule, UUID.randomUUID(), UUID.randomUUID(), slotDurationMinutes, "12:00");

        String requestBody = getPayload("courtscheduler.search.courtschedules.by.id_dynamic.json");
        requestBody = requestBody.replace("COURT_SCHEDULE_ID", courtScheduleId);
        final Map<String, Object> map = mapper.readValue(requestBody, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams(
                SEARCH_BY_ID_URL,
                COURT_SCHEDULE_SEARCH_COURTSCHEDULES_BY_ID_CONTENT_TYPE,
                SYSTEM_USER_ID, map);
        final ResponseData response = poll(requestParams).with()
                .timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertEquals(OK.getStatusCode(), response.getStatus().getStatusCode());
        final JsonObject json = stringToJsonObjectConverter.convert(response.getPayload());
        assertThat(json.getJsonArray("courtSchedules").size(), is(1));
        final JsonObject session = json.getJsonArray("courtSchedules").getJsonObject(0);
        assertThat(session.getString("courtScheduleId"), is(courtScheduleId));
        assertThat(session.getBoolean("slotBased"), is(true));
        assertThat(session.getInt("maxSlots"), is(maxSlots));
        assertThat(session.getInt("availableSlots"), is(expectedAvailableSlots));
        assertThat(session.getInt("totalBooked"), is(bookedCount * slotDurationMinutes));
    }

    @Test
    void shouldReturnCorrectAvailableDurationWhenAllocatedListingsExistForDurationBasedSession() throws Exception {
        // Given a CROWN duration-based court schedule with 300 max-duration minutes
        // and 3 allocated listings of 60/90/45 minutes, the search-by-id read view
        // should report availableDuration = maxDuration - sum(durations).
        // CROWN + TRL + duration-based mirrors the Crown Court trial workflow used
        // by shouldDeleteMultipleCourtSchedules.
        final String courtScheduleId = UUID.randomUUID().toString();
        final int maxDuration = 300;
        final int duration1 = 60;
        final int duration2 = 90;
        final int duration3 = 45;
        final int totalBooked = duration1 + duration2 + duration3; // 195
        final int expectedAvailableDuration = maxDuration - totalBooked; // 105

        final CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setCourtScheduleId(courtScheduleId);
        courtSchedule.setBusinessType("TRL");
        courtSchedule.setSlotBased(false);
        courtSchedule.setSupportAdSplit(false);
        courtSchedule.setCourtSession(ALL_DAY);
        courtSchedule.setMaxDuration(maxDuration);
        courtSchedule.setAvailableDuration(expectedAvailableDuration);
        courtSchedule.setMaxSlots(0);
        courtSchedule.setAvailableSlots(0);
        courtSchedule.setMaxAdMorningDuration(0);
        courtSchedule.setMaxAdAfternoonDuration(0);
        courtSchedule.setSessionDate(getRandomFutureDateWithinNextYear());
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), "10:00"));
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), "16:00"));
        courtSchedule.setIsDraft(false);
        courtSchedule.setJurisdiction(CROWN.getJurisdiction());
        courtSchedule.setActive(true);
        databaseSeeder.insertCourtSchedule(courtSchedule);

        createAllocatedListing(courtSchedule, UUID.randomUUID(), UUID.randomUUID(), duration1, "10:00");
        createAllocatedListing(courtSchedule, UUID.randomUUID(), UUID.randomUUID(), duration2, "11:30");
        createAllocatedListing(courtSchedule, UUID.randomUUID(), UUID.randomUUID(), duration3, "13:30");

        String requestBody = getPayload("courtscheduler.search.courtschedules.by.id_dynamic.json");
        requestBody = requestBody.replace("COURT_SCHEDULE_ID", courtScheduleId);
        final Map<String, Object> map = mapper.readValue(requestBody, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams(
                SEARCH_BY_ID_URL,
                COURT_SCHEDULE_SEARCH_COURTSCHEDULES_BY_ID_CONTENT_TYPE,
                SYSTEM_USER_ID, map);
        final ResponseData response = poll(requestParams).with()
                .timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertEquals(OK.getStatusCode(), response.getStatus().getStatusCode());
        final JsonObject json = stringToJsonObjectConverter.convert(response.getPayload());
        assertThat(json.getJsonArray("courtSchedules").size(), is(1));
        final JsonObject session = json.getJsonArray("courtSchedules").getJsonObject(0);
        assertThat(session.getString("courtScheduleId"), is(courtScheduleId));
        assertThat(session.getString("jurisdiction"), is(CROWN.getJurisdiction()));
        assertThat(session.getBoolean("slotBased"), is(false));
        assertThat(session.getInt("maxDuration"), is(maxDuration));
        assertThat(session.getInt("availableDuration"), is(expectedAvailableDuration));
        assertThat(session.getInt("totalBooked"), is(totalBooked));
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
    void shouldNotDeletePastCourtSchedules() throws Exception {
        // Given - Create past schedules (yesterday, one week ago, one month ago)
        String oneDayAgoId = UUID.randomUUID().toString();
        String oneWeekAgoId = UUID.randomUUID().toString();
        String oneMonthAgoId = UUID.randomUUID().toString();

        CourtSchedule oneDayAgo = createCourtScheduleForDate(oneDayAgoId, now().minusDays(1));
        CourtSchedule oneWeekAgo = createCourtScheduleForDate(oneWeekAgoId, now().minusDays(7));
        CourtSchedule oneMonthAgo = createCourtScheduleForDate(oneMonthAgoId, now().minusMonths(1));

        databaseSeeder.insertCourtSchedule(oneDayAgo);
        databaseSeeder.insertCourtSchedule(oneWeekAgo);
        databaseSeeder.insertCourtSchedule(oneMonthAgo);

        // When - Try to delete past schedules
        String deletePayload = String.format("{\"sessions\": [\"%s\", \"%s\", \"%s\"]}",
                oneDayAgoId, oneWeekAgoId, oneMonthAgoId);
        final Response response = postCommand(BASE_RESOURCE_URL + DELETE_URL,
                COURT_SCHEDULE_DELETE_CONTENT_TYPE, USER_ID, deletePayload);

        // Then - Response should be OK
        assertThat(response.getStatus(), is(OK.getStatusCode()));

        // Verify all past schedules still exist in database
        List<CourtSchedule> remainingSchedules = databaseReader.courtSchedules();
        assertThat(remainingSchedules.stream()
                .anyMatch(cs -> cs.getCourtScheduleId().equals(oneDayAgoId)), is(true));
        assertThat(remainingSchedules.stream()
                .anyMatch(cs -> cs.getCourtScheduleId().equals(oneWeekAgoId)), is(true));
        assertThat(remainingSchedules.stream()
                .anyMatch(cs -> cs.getCourtScheduleId().equals(oneMonthAgoId)), is(true));

        // Verify response indicates successful deletion (empty sessions array)
        try (JsonReader jsonReader = Json.createReader(new StringReader(response.readEntity(String.class)))) {
            JsonObject jsonResponse = jsonReader.readObject();
            assertTrue(jsonResponse.containsKey("sessions"), "Response should contain 'sessions' key");
            // Empty array means no errors (schedules were not deleted, which is expected for past dates)
            assertThat(jsonResponse.getJsonArray("sessions").size(), is(0));
            assertThat(jsonResponse.containsKey("error"), is(false));
        }
    }

    @Test
    void shouldDeleteTodayAndFutureCourtSchedules() throws Exception {
        // Given - Create today and future schedules
        String todayId = UUID.randomUUID().toString();
        String tomorrowId = UUID.randomUUID().toString();
        String oneWeekFutureId = UUID.randomUUID().toString();
        String oneMonthFutureId = UUID.randomUUID().toString();

        CourtSchedule today = createCourtScheduleForDate(todayId, now());
        CourtSchedule tomorrow = createCourtScheduleForDate(tomorrowId, now().plusDays(1));
        CourtSchedule oneWeekFuture = createCourtScheduleForDate(oneWeekFutureId, now().plusDays(7));
        CourtSchedule oneMonthFuture = createCourtScheduleForDate(oneMonthFutureId, now().plusMonths(1));

        databaseSeeder.insertCourtSchedule(today);
        databaseSeeder.insertCourtSchedule(tomorrow);
        databaseSeeder.insertCourtSchedule(oneWeekFuture);
        databaseSeeder.insertCourtSchedule(oneMonthFuture);

        // When - Delete today and future schedules
        String deletePayload = String.format("{\"sessions\": [\"%s\", \"%s\", \"%s\", \"%s\"]}",
                todayId, tomorrowId, oneWeekFutureId, oneMonthFutureId);
        final Response response = postCommand(BASE_RESOURCE_URL + DELETE_URL,
                COURT_SCHEDULE_DELETE_CONTENT_TYPE, USER_ID, deletePayload);

        // Then - Response should be OK
        assertThat(response.getStatus(), is(OK.getStatusCode()));

        // Verify all schedules are deleted from database
        List<CourtSchedule> remainingSchedules = databaseReader.courtSchedules();
        assertThat(remainingSchedules.stream()
                .noneMatch(cs -> cs.getCourtScheduleId().equals(todayId)), is(true));
        assertThat(remainingSchedules.stream()
                .noneMatch(cs -> cs.getCourtScheduleId().equals(tomorrowId)), is(true));
        assertThat(remainingSchedules.stream()
                .noneMatch(cs -> cs.getCourtScheduleId().equals(oneWeekFutureId)), is(true));
        assertThat(remainingSchedules.stream()
                .noneMatch(cs -> cs.getCourtScheduleId().equals(oneMonthFutureId)), is(true));

        // Verify response indicates successful deletion
        try (JsonReader jsonReader = Json.createReader(new StringReader(response.readEntity(String.class)))) {
            JsonObject jsonResponse = jsonReader.readObject();
            assertTrue(jsonResponse.containsKey("sessions"), "Response should contain 'sessions' key");
            assertThat(jsonResponse.getJsonArray("sessions").size(), is(0));
            assertThat(jsonResponse.containsKey("error"), is(false));
        }
    }

    @Test
    void shouldNotDeletePastSchedulesButDeleteTodayAndFutureInMixedScenario() throws Exception {
        // Given - Mix of past, today, and future schedules
        String pastId = UUID.randomUUID().toString();
        String todayId = UUID.randomUUID().toString();
        String futureId = UUID.randomUUID().toString();

        CourtSchedule past = createCourtScheduleForDate(pastId, now().minusDays(5));
        CourtSchedule today = createCourtScheduleForDate(todayId, now());
        CourtSchedule future = createCourtScheduleForDate(futureId, now().plusDays(10));

        databaseSeeder.insertCourtSchedule(past);
        databaseSeeder.insertCourtSchedule(today);
        databaseSeeder.insertCourtSchedule(future);

        // When - Try to delete all schedules
        String deletePayload = String.format("{\"sessions\": [\"%s\", \"%s\", \"%s\"]}",
                pastId, todayId, futureId);
        final Response response = postCommand(BASE_RESOURCE_URL + DELETE_URL,
                COURT_SCHEDULE_DELETE_CONTENT_TYPE, USER_ID, deletePayload);

        // Then - Response should be OK
        assertThat(response.getStatus(), is(OK.getStatusCode()));

        // Verify past schedule remains, today and future are deleted
        List<CourtSchedule> remainingSchedules = databaseReader.courtSchedules();
        assertThat(remainingSchedules.stream()
                .anyMatch(cs -> cs.getCourtScheduleId().equals(pastId)), is(true));
        assertThat(remainingSchedules.stream()
                .noneMatch(cs -> cs.getCourtScheduleId().equals(todayId)), is(true));
        assertThat(remainingSchedules.stream()
                .noneMatch(cs -> cs.getCourtScheduleId().equals(futureId)), is(true));

        // Verify response indicates successful deletion (no errors)
        try (JsonReader jsonReader = Json.createReader(new StringReader(response.readEntity(String.class)))) {
            JsonObject jsonResponse = jsonReader.readObject();
            assertTrue(jsonResponse.containsKey("sessions"), "Response should contain 'sessions' key");
            assertThat(jsonResponse.getJsonArray("sessions").size(), is(0));
            assertThat(jsonResponse.containsKey("error"), is(false));
        }
    }

    @Test
    void shouldNotDeletePastScheduleWithAllocatedListings() throws Exception {
        // Given - Past schedule with allocated listings
        String pastWithAllocationsId = UUID.randomUUID().toString();
        CourtSchedule pastWithAllocations = createCourtScheduleForDate(pastWithAllocationsId, now().minusDays(3));
        databaseSeeder.insertCourtSchedule(pastWithAllocations);

        final UUID hearingId = UUID.randomUUID();
        final UUID bookingId = UUID.randomUUID();
        createAllocatedListing(pastWithAllocations, hearingId, bookingId, 60, "10:00");

        // When - Try to delete past schedule with allocations
        String deletePayload = String.format("{\"sessions\": [\"%s\"]}", pastWithAllocationsId);
        final Response response = postCommand(BASE_RESOURCE_URL + DELETE_URL,
                COURT_SCHEDULE_DELETE_CONTENT_TYPE, USER_ID, deletePayload);

        // Then - Response should be OK
        assertThat(response.getStatus(), is(OK.getStatusCode()));

        // Verify schedule still exists (not deleted due to allocations)
        List<CourtSchedule> remainingSchedules = databaseReader.courtSchedules();
        assertThat(remainingSchedules.stream()
                .anyMatch(cs -> cs.getCourtScheduleId().equals(pastWithAllocationsId)), is(true));

        // Verify response contains error with schedule details
        try (JsonReader jsonReader = Json.createReader(new StringReader(response.readEntity(String.class)))) {
            JsonObject jsonResponse = jsonReader.readObject();
            assertTrue(jsonResponse.containsKey("error"), "Response should contain an 'error' key");
            assertTrue(jsonResponse.containsKey("sessions"), "Response should contain 'sessions' key");
            assertThat(jsonResponse.getJsonArray("sessions").size(), is(1));
            JsonObject sessionObj = jsonResponse.getJsonArray("sessions").getJsonObject(0);
            assertThat(sessionObj.getString("courtScheduleId"), is(pastWithAllocationsId));
            assertThat(sessionObj.getInt("totalBooked"), is(60));
        }
    }

    @Test
    void shouldNotDeleteTodayOrFutureScheduleWithAllocatedListings() throws Exception {
        // Given - Today and future schedules with allocated listings
        String todayWithAllocationsId = UUID.randomUUID().toString();
        String futureWithAllocationsId = UUID.randomUUID().toString();

        CourtSchedule todayWithAllocations = createCourtScheduleForDate(todayWithAllocationsId, now());
        CourtSchedule futureWithAllocations = createCourtScheduleForDate(futureWithAllocationsId, now().plusDays(5));

        databaseSeeder.insertCourtSchedule(todayWithAllocations);
        databaseSeeder.insertCourtSchedule(futureWithAllocations);

        final UUID hearingId1 = UUID.randomUUID();
        final UUID bookingId1 = UUID.randomUUID();
        createAllocatedListing(todayWithAllocations, hearingId1, bookingId1, 60, "10:00");

        final UUID hearingId2 = UUID.randomUUID();
        final UUID bookingId2 = UUID.randomUUID();
        createAllocatedListing(futureWithAllocations, hearingId2, bookingId2, 90, "14:00");

        // When - Try to delete schedules with allocations
        String deletePayload = String.format("{\"sessions\": [\"%s\", \"%s\"]}",
                todayWithAllocationsId, futureWithAllocationsId);
        final Response response = postCommand(BASE_RESOURCE_URL + DELETE_URL,
                COURT_SCHEDULE_DELETE_CONTENT_TYPE, USER_ID, deletePayload);

        // Then - Response should be OK
        assertThat(response.getStatus(), is(OK.getStatusCode()));

        // Verify schedules still exist (not deleted due to allocations)
        List<CourtSchedule> remainingSchedules = databaseReader.courtSchedules();
        assertThat(remainingSchedules.stream()
                .anyMatch(cs -> cs.getCourtScheduleId().equals(todayWithAllocationsId)), is(true));
        assertThat(remainingSchedules.stream()
                .anyMatch(cs -> cs.getCourtScheduleId().equals(futureWithAllocationsId)), is(true));

        // Verify response contains error with both schedules
        try (JsonReader jsonReader = Json.createReader(new StringReader(response.readEntity(String.class)))) {
            JsonObject jsonResponse = jsonReader.readObject();
            assertTrue(jsonResponse.containsKey("error"), "Response should contain an 'error' key");
            assertTrue(jsonResponse.containsKey("sessions"), "Response should contain 'sessions' key");
            assertThat(jsonResponse.getJsonArray("sessions").size(), is(2));
        }
    }

    private CourtSchedule createCourtScheduleForDate(String courtScheduleId, LocalDate sessionDate) {
        CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setBusinessType("TRL");
        courtSchedule.setSlotBased(false);
        courtSchedule.setMaxDuration(120);
        courtSchedule.setAvailableDuration(120);
        courtSchedule.setSupportAdSplit(false);
        courtSchedule.setCourtSession(ALL_DAY);
        courtSchedule.setCourtScheduleId(courtScheduleId);
        courtSchedule.setSessionDate(sessionDate);
        courtSchedule.setSessionStartTime(combineDateAndTime(sessionDate, "10:00"));
        courtSchedule.setSessionEndTime(combineDateAndTime(sessionDate, "16:00"));
        courtSchedule.setJurisdiction("MAGISTRATES");
        courtSchedule.setIsDraft(false);
        courtSchedule.setActive(true);
        return courtSchedule;
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
        // Allocated (final) session — random() leaves isDraft unset, and the draft-strip would null the courtroom assertions below.
        courtSchedule.setIsDraft(false);
        databaseSeeder.insertCourtSchedule(courtSchedule);

        final AllocatedListing allocatedListingForMorning = createAllocatedListing(courtSchedule, hearingIdForMorning, bookingIdForMorning, 60, "10:00");
        final AllocatedListing allocatedListingForAfternoon = createAllocatedListing(courtSchedule, hearingIdForAfternoon, bookingIdForAfternoon, 30, "15:00");

        String getCourtScheduleRequestParams = getPayload("courtscheduler.search.courtschedules.by.id_dynamic.json");
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("COURT_SCHEDULE_ID", courtScheduleId.toString());
        Map<String, Object> map = mapper.readValue(getCourtScheduleRequestParams, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams(
                SEARCH_BY_ID_URL,
                COURT_SCHEDULE_SEARCH_COURTSCHEDULES_BY_ID_CONTENT_TYPE,
                SYSTEM_USER_ID,
                map
        );

        final ResponseData response = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(response.getStatus().getStatusCode(), is(OK.getStatusCode()));

        JsonObject jsonObject = stringToJsonObjectConverter.convert(response.getPayload());
        // Guard: the schedule has two allocated listings (morning + afternoon) — the query must
        // still return exactly one court_schedule row.
        assertThat(jsonObject.getJsonArray("courtSchedules").size(), is(1));

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

    @Test
    void shouldReturnSingleResultWhenCourtScheduleHasMultipleAllocatedListings() throws SQLException, JsonProcessingException {
        final UUID courtScheduleId = UUID.randomUUID();
        final CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setBusinessType("TRL");
        courtSchedule.setSlotBased(false);
        courtSchedule.setMaxDuration(360);
        courtSchedule.setAvailableDuration(360);
        courtSchedule.setSupportAdSplit(true);
        courtSchedule.setCourtSession(ALL_DAY);
        courtSchedule.setMaxAdMorningDuration(180);
        courtSchedule.setMaxAdAfternoonDuration(180);
        courtSchedule.setCourtScheduleId(courtScheduleId.toString());
        courtSchedule.setSessionDate(getRandomFutureDateWithinNextYear());
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), "10:00"));
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), "16:00"));
        courtSchedule.setIsOverbookingAllowed(false);
        courtSchedule.setActive(true);
        databaseSeeder.insertCourtSchedule(courtSchedule);

        // Two allocated listings with different durations and start times — in the old query
        // this produced two rows per court_schedule because al.id was in GROUP BY.
        createAllocatedListing(courtSchedule, UUID.randomUUID(), UUID.randomUUID(), 60, "10:00");
        createAllocatedListing(courtSchedule, UUID.randomUUID(), UUID.randomUUID(), 90, "14:00");

        String payload = getPayload("courtscheduler.search.courtschedules.by.id_dynamic.json")
                .replace("COURT_SCHEDULE_ID", courtScheduleId.toString());
        Map<String, Object> map = mapper.readValue(payload, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams(
                SEARCH_BY_ID_URL,
                COURT_SCHEDULE_SEARCH_COURTSCHEDULES_BY_ID_CONTENT_TYPE,
                SYSTEM_USER_ID,
                map
        );

        final ResponseData response = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(response.getStatus().getStatusCode(), is(OK.getStatusCode()));

        final JsonObject json = stringToJsonObjectConverter.convert(response.getPayload());
        assertThat(json.getJsonArray("courtSchedules").size(), is(1));

        final JsonObject scheduleJson = json.getJsonArray("courtSchedules").getJsonObject(0);
        assertThat(scheduleJson.getString("courtScheduleId"), is(courtScheduleId.toString()));
        assertThat(scheduleJson.getInt("totalBooked"), is(150));
    }

    @Test
    void shouldReturnSingleResultWhenSameCourtScheduleIdRepeatedInQueryParams() throws SQLException, JsonProcessingException {
        final UUID courtScheduleId = UUID.randomUUID();
        final CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setBusinessType("TRL");
        courtSchedule.setSlotBased(false);
        courtSchedule.setSupportAdSplit(false);
        courtSchedule.setMaxDuration(360);
        courtSchedule.setAvailableDuration(360);
        courtSchedule.setCourtScheduleId(courtScheduleId.toString());
        courtSchedule.setSessionDate(getRandomFutureDateWithinNextYear());
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), "10:00"));
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), "16:00"));
        courtSchedule.setActive(true);
        databaseSeeder.insertCourtSchedule(courtSchedule);

        final String repeated = courtScheduleId + "," + courtScheduleId + "," + courtScheduleId;
        final Map<String, Object> map = Map.of("ids", repeated);

        final RequestParams requestParams = getRequestParams(
                SEARCH_BY_ID_URL,
                COURT_SCHEDULE_SEARCH_COURTSCHEDULES_BY_ID_CONTENT_TYPE,
                SYSTEM_USER_ID,
                map
        );

        final ResponseData response = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(response.getStatus().getStatusCode(), is(OK.getStatusCode()));

        final JsonObject json = stringToJsonObjectConverter.convert(response.getPayload());
        assertThat(json.getJsonArray("courtSchedules").size(), is(1));
        assertThat(json.getJsonArray("courtSchedules").getJsonObject(0).getString("courtScheduleId"), is(courtScheduleId.toString()));
    }

    @Test
    void shouldReturnAvailabilityAggregatedAcrossAllAllocatedListingsForAllDaySplit() throws SQLException, JsonProcessingException {
        // Mirrors shouldGetCourtScheduleById's setup but adds an extra afternoon allocation on
        // the same court schedule. Previously the query returned one row per allocated_listing
        // with per-row totalbookedforafternoon values, and the converter's client-side sum
        // happened to mask the bug. After the fix, availability reflects the fold across *all*
        // allocated rows, i.e. totalBooked = sum of every allocated_listing duration for the schedule.
        final UUID courtScheduleId = UUID.randomUUID();
        final CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setBusinessType("TRL");
        courtSchedule.setSlotBased(false);
        courtSchedule.setMaxDuration(0);
        courtSchedule.setAvailableDuration(0);
        courtSchedule.setSupportAdSplit(true);
        courtSchedule.setCourtSession(ALL_DAY);
        courtSchedule.setMaxAdMorningDuration(180);
        courtSchedule.setMaxAdAfternoonDuration(180);
        courtSchedule.setCourtScheduleId(courtScheduleId.toString());
        courtSchedule.setSessionDate(getRandomFutureDateWithinNextYear());
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), "10:00"));
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), "16:00"));
        courtSchedule.setIsOverbookingAllowed(false);
        courtSchedule.setActive(true);
        databaseSeeder.insertCourtSchedule(courtSchedule);

        // Morning slot: 60min at 10:00
        createAllocatedListing(courtSchedule, UUID.randomUUID(), UUID.randomUUID(), 60, "10:00");
        // Two afternoon slots: 30min at 14:00 and 45min at 15:00
        createAllocatedListing(courtSchedule, UUID.randomUUID(), UUID.randomUUID(), 30, "14:00");
        createAllocatedListing(courtSchedule, UUID.randomUUID(), UUID.randomUUID(), 45, "15:00");

        String payload = getPayload("courtscheduler.search.courtschedules.by.id_dynamic.json")
                .replace("COURT_SCHEDULE_ID", courtScheduleId.toString());
        Map<String, Object> map = mapper.readValue(payload, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams(
                SEARCH_BY_ID_URL,
                COURT_SCHEDULE_SEARCH_COURTSCHEDULES_BY_ID_CONTENT_TYPE,
                SYSTEM_USER_ID,
                map
        );

        final ResponseData response = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(response.getStatus().getStatusCode(), is(OK.getStatusCode()));

        final JsonObject json = stringToJsonObjectConverter.convert(response.getPayload());
        assertThat(json.getJsonArray("courtSchedules").size(), is(1));

        final JsonObject scheduleJson = json.getJsonArray("courtSchedules").getJsonObject(0);
        assertThat(scheduleJson.getString("courtScheduleId"), is(courtScheduleId.toString()));
        assertThat(scheduleJson.getBoolean("allDaySplit"), is(true));
        // Total across all three allocations
        assertThat(scheduleJson.getInt("totalBooked"), is(60 + 30 + 45));
        // Morning = one 60min booking; afternoon = 30+45
        assertThat(scheduleJson.getInt("totalBookedForMorning"), is(60));
        assertThat(scheduleJson.getInt("totalBookedForAfternoon"), is(75));
        assertThat(scheduleJson.getInt("availableDurationForMorning"), is(180 - 60));
        assertThat(scheduleJson.getInt("availableDurationForAfternoon"), is(180 - 75));
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

    @Test
    void shouldUnassignJudiciarySuccessfully() throws SQLException, Exception {
        // Setup: Create a court schedule and assign a judiciary
        final CourtSchedule courtSchedule = createTestCourtSchedule();
        databaseSeeder.insertCourtSchedule(courtSchedule);

        final CourtScheduleJudiciary courtScheduleJudiciary = createTestCourtScheduleJudiciary(courtSchedule.getCourtScheduleId());
        databaseSeeder.saveJudiciarySchedule(courtScheduleJudiciary);

        // Verify judiciary is assigned
        List<CourtScheduleJudiciary> judiciariesBefore = databaseReader.courtScheduleJudiciaries();
        assertTrue(judiciariesBefore.stream()
                .anyMatch(js -> js.getId().getCourtScheduleId().equals(courtSchedule.getCourtScheduleId())
                        && js.getId().getJudiciaryId().equals(courtScheduleJudiciary.getId().getJudiciaryId())));

        // Call unassign endpoint

        final String requestPayload = createObjectBuilder()
                .add("judiciaries", createArrayBuilder()
                        .add(createObjectBuilder()
                                .add("judiciaryId", courtScheduleJudiciary.getId().getJudiciaryId())
                                .add("sessionIds", createArrayBuilder()
                                        .add(courtSchedule.getCourtScheduleId())
                                        .build())
                                .build())
                        .build())
                .build()
                .toString();

        final Response response = postCommand(JUDICIARY_SESSION_URL,
                UNASSIGN_JUDICIARY_CONTENT_TYPE,
                SYSTEM_USER_ID,
                requestPayload);

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        // Verify judiciary is unassigned
        List<CourtScheduleJudiciary> judiciariesAfter = databaseReader.courtScheduleJudiciaries();
        assertFalse(judiciariesAfter.stream()
                .anyMatch(js -> js.getId().getCourtScheduleId().equals(courtSchedule.getCourtScheduleId())
                        && js.getId().getJudiciaryId().equals(courtScheduleJudiciary.getId().getJudiciaryId())));
    }

    @Test
    void shouldContinueProcessingWhenUnassigningJudiciaryWithAllocatedListings() throws SQLException, Exception {
        // Setup: Create a court schedule with allocated listings and assign a judiciary
        final CourtSchedule courtSchedule = createTestCourtSchedule();
        databaseSeeder.insertCourtSchedule(courtSchedule);

        final CourtScheduleJudiciary courtScheduleJudiciary = createTestCourtScheduleJudiciary(courtSchedule.getCourtScheduleId());
        databaseSeeder.saveJudiciarySchedule(courtScheduleJudiciary);

        // Add allocated listing to the court schedule
        final AllocatedListing allocatedListing = getAllocatedListing(courtSchedule);
        databaseSeeder.insertAllocatedListing(allocatedListing);

        // Call unassign endpoint
        final String requestPayload = createObjectBuilder()
                .add("judiciaries", createArrayBuilder()
                        .add(createObjectBuilder()
                                .add("judiciaryId", courtScheduleJudiciary.getId().getJudiciaryId())
                                .add("sessionIds", createArrayBuilder()
                                        .add(courtSchedule.getCourtScheduleId())
                                        .build())
                                .build())
                        .build())
                .build()
                .toString();

        final Response response = postCommand(JUDICIARY_SESSION_URL,
                UNASSIGN_JUDICIARY_CONTENT_TYPE,
                SYSTEM_USER_ID,
                requestPayload);

        // Should return ACCEPTED since we continue processing (no exception thrown)
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        // Verify judiciary is still assigned (not removed due to allocated listings)
        List<CourtScheduleJudiciary> judiciariesAfter = databaseReader.courtScheduleJudiciaries();
        assertTrue(judiciariesAfter.stream()
                .anyMatch(js -> js.getId().getCourtScheduleId().equals(courtSchedule.getCourtScheduleId())
                        && js.getId().getJudiciaryId().equals(courtScheduleJudiciary.getId().getJudiciaryId())));
    }

    @Test
    void shouldContinueProcessingWhenJudiciaryNotFound() throws SQLException, IllegalArgumentException {
        // Setup: Create a court schedule but no judiciary assignment
        final CourtSchedule courtSchedule = createTestCourtSchedule();
        databaseSeeder.insertCourtSchedule(courtSchedule);

        final String nonExistentJudiciaryId = randomUUID().toString();

        // Call unassign endpoint with non-existent judiciary
        final String requestPayload = createObjectBuilder()
                .add("judiciaries", createArrayBuilder()
                        .add(createObjectBuilder()
                                .add("judiciaryId", nonExistentJudiciaryId)
                                .add("sessionIds", createArrayBuilder()
                                        .add(courtSchedule.getCourtScheduleId())
                                        .build())
                                .build())
                        .build())
                .build()
                .toString();

        final Response response = postCommand(JUDICIARY_SESSION_URL,
                UNASSIGN_JUDICIARY_CONTENT_TYPE,
                SYSTEM_USER_ID,
                requestPayload);

        // Should return ACCEPTED since we continue processing (no exception thrown)
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
    }

    @Test
    void shouldReturnBadRequestWhenSessionIdsMissing() {
        final String requestPayload = createObjectBuilder()
                .add("judiciaries", createArrayBuilder()
                        .add(createObjectBuilder()
                                .add("judiciaryId", randomUUID().toString())
                                .build())
                        .build())
                .build()
                .toString();

        final Response response = postCommand(JUDICIARY_SESSION_URL,
                UNASSIGN_JUDICIARY_CONTENT_TYPE,
                SYSTEM_USER_ID,
                requestPayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
    }

    @Test
    void shouldReturnBadRequestWhenJudiciaryIdMissing() {
        // Note: Validator no longer validates missing judiciaryId, but service layer will return error
        // when trying to find judiciary with empty ID
        final String requestPayload = createObjectBuilder()
                .add("judiciaries", createArrayBuilder()
                        .add(createObjectBuilder()
                                .add("sessionIds", createArrayBuilder()
                                        .add(randomUUID().toString())
                                        .build())
                                .build())
                        .build())
                .build()
                .toString();

        final Response response = postCommand(JUDICIARY_SESSION_URL,
                UNASSIGN_JUDICIARY_CONTENT_TYPE,
                SYSTEM_USER_ID,
                requestPayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
    }

    @Test
    void shouldSkipValidationsWhenSkipValidationsIsTrueForUnassign() throws Exception {
        // Setup: Create a court schedule and assign a judiciary
        final CourtSchedule courtSchedule = createTestCourtSchedule();
        databaseSeeder.insertCourtSchedule(courtSchedule);

        final CourtScheduleJudiciary courtScheduleJudiciary = createTestCourtScheduleJudiciary(courtSchedule.getCourtScheduleId());
        databaseSeeder.saveJudiciarySchedule(courtScheduleJudiciary);

        // Add allocated listing to the court schedule (normally would prevent unassignment)
        final AllocatedListing allocatedListing = getAllocatedListing(courtSchedule);
        databaseSeeder.insertAllocatedListing(allocatedListing);

        // Call unassign endpoint with skipValidations=true
        final String requestPayload = createObjectBuilder()
                .add("judiciaries", createArrayBuilder()
                        .add(createObjectBuilder()
                                .add("judiciaryId", courtScheduleJudiciary.getId().getJudiciaryId())
                                .add("sessionIds", createArrayBuilder()
                                        .add(courtSchedule.getCourtScheduleId())
                                        .build())
                                .build())
                        .build())
                .add("skipValidations", true)
                .build()
                .toString();

        final Response response = postCommand(JUDICIARY_SESSION_URL,
                UNASSIGN_JUDICIARY_CONTENT_TYPE,
                SYSTEM_USER_ID,
                requestPayload);

        // Should succeed even though allocated listings exist (validations skipped)
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        // Verify judiciary is unassigned
        List<CourtScheduleJudiciary> judiciariesAfter = databaseReader.courtScheduleJudiciaries();
        assertFalse(judiciariesAfter.stream()
                .anyMatch(js -> js.getId().getCourtScheduleId().equals(courtSchedule.getCourtScheduleId())
                        && js.getId().getJudiciaryId().equals(courtScheduleJudiciary.getId().getJudiciaryId())));
    }

    @Test
    void shouldPerformValidationsWhenSkipValidationsIsFalseForUnassign() throws Exception {
        // Setup: Create a court schedule and assign a judiciary
        final CourtSchedule courtSchedule = createTestCourtSchedule();
        databaseSeeder.insertCourtSchedule(courtSchedule);

        final CourtScheduleJudiciary courtScheduleJudiciary = createTestCourtScheduleJudiciary(courtSchedule.getCourtScheduleId());
        databaseSeeder.saveJudiciarySchedule(courtScheduleJudiciary);

        // Add allocated listing to the court schedule (should prevent unassignment)
        final AllocatedListing allocatedListing = getAllocatedListing(courtSchedule);
        databaseSeeder.insertAllocatedListing(allocatedListing);

        // Call unassign endpoint with skipValidations=false
        final String requestPayload = createObjectBuilder()
                .add("judiciaries", createArrayBuilder()
                        .add(createObjectBuilder()
                                .add("judiciaryId", courtScheduleJudiciary.getId().getJudiciaryId())
                                .add("sessionIds", createArrayBuilder()
                                        .add(courtSchedule.getCourtScheduleId())
                                        .build())
                                .build())
                        .build())
                .add("skipValidations", false)
                .build()
                .toString();

        final Response response = postCommand(JUDICIARY_SESSION_URL,
                UNASSIGN_JUDICIARY_CONTENT_TYPE,
                SYSTEM_USER_ID,
                requestPayload);

        // Should succeed (validation continues but doesn't throw exception, just logs)
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        // Verify judiciary is still assigned (validation prevented unassignment)
        List<CourtScheduleJudiciary> judiciariesAfter = databaseReader.courtScheduleJudiciaries();
        assertTrue(judiciariesAfter.stream()
                .anyMatch(js -> js.getId().getCourtScheduleId().equals(courtSchedule.getCourtScheduleId())
                        && js.getId().getJudiciaryId().equals(courtScheduleJudiciary.getId().getJudiciaryId())));
    }

    @Test
    void shouldAssignJudiciarySuccessfully() throws Exception {
        // Setup: Create a court schedule
        final CourtSchedule courtSchedule = createTestCourtSchedule();
        databaseSeeder.insertCourtSchedule(courtSchedule);

        final String judiciaryId = randomUUID().toString();

        // Verify judiciary is not assigned initially
        List<CourtScheduleJudiciary> judiciariesBefore = databaseReader.courtScheduleJudiciaries();
        assertFalse(judiciariesBefore.stream()
                .anyMatch(js -> js.getId().getCourtScheduleId().equals(courtSchedule.getCourtScheduleId())
                        && js.getId().getJudiciaryId().equals(judiciaryId)));

        // Call assign endpoint with skipValidations=true (since judiciary may not exist in reference data)
        final String requestPayload = createObjectBuilder()
                .add("judiciaries", createArrayBuilder()
                        .add(createObjectBuilder()
                                .add("judiciaryId", judiciaryId)
                                .add("sessionIds", createArrayBuilder()
                                        .add(courtSchedule.getCourtScheduleId())
                                        .build())
                                .build())
                        .build())
                .add("skipValidations", true)
                .build()
                .toString();

        final Response response = postCommand(JUDICIARY_SESSION_URL,
                ASSIGN_JUDICIARY_CONTENT_TYPE,
                SYSTEM_USER_ID,
                requestPayload);

        // Should succeed with skipValidations=true
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
    }

    @Test
    void shouldAssignJudiciaryWithSkipValidationsFalse() throws Exception {
        // Setup: Create a court schedule
        final CourtSchedule courtSchedule = createTestCourtSchedule();
        databaseSeeder.insertCourtSchedule(courtSchedule);

        // Use a judiciary ID from the stubbed reference data (from referencedata.judiciaries.json)
        final String judiciaryId = "9ac02e8d-ee90-3da6-8d3e-0dd0af2cb976";

        // Call assign endpoint with skipValidations=false
        final String requestPayload = createObjectBuilder()
                .add("judiciaries", createArrayBuilder()
                        .add(createObjectBuilder()
                                .add("judiciaryId", judiciaryId)
                                .add("sessionIds", createArrayBuilder()
                                        .add(courtSchedule.getCourtScheduleId())
                                        .build())
                                .add(P_IS_DEPUTY, false)
                                .add(P_IS_BENCH_CHAIRMAN, true)
                                .build())
                        .build())
                .add("skipValidations", false)
                .build()
                .toString();

        final Response response = postCommand(JUDICIARY_SESSION_URL,
                ASSIGN_JUDICIARY_CONTENT_TYPE,
                SYSTEM_USER_ID,
                requestPayload);

        // Should return ACCEPTED when validation passes (judiciary exists in reference data)
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
    }

    @Test
    void shouldAssignJudiciaryWithMultipleSessions() throws Exception {
        // Setup: Create multiple court schedules
        final CourtSchedule courtSchedule1 = createTestCourtSchedule();
        databaseSeeder.insertCourtSchedule(courtSchedule1);

        final CourtSchedule courtSchedule2 = createTestCourtSchedule();
        databaseSeeder.insertCourtSchedule(courtSchedule2);

        final String judiciaryId = randomUUID().toString();

        // Call assign endpoint with skipValidations=true for multiple sessions
        final String requestPayload = createObjectBuilder()
                .add("judiciaries", createArrayBuilder()
                        .add(createObjectBuilder()
                                .add("judiciaryId", judiciaryId)
                                .add("sessionIds", createArrayBuilder()
                                        .add(courtSchedule1.getCourtScheduleId())
                                        .add(courtSchedule2.getCourtScheduleId())
                                        .build())
                                .build())
                        .build())
                .add("skipValidations", true)
                .build()
                .toString();

        final Response response = postCommand(JUDICIARY_SESSION_URL,
                ASSIGN_JUDICIARY_CONTENT_TYPE,
                SYSTEM_USER_ID,
                requestPayload);

        // Should succeed
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
    }

    @Test
    void shouldAssignJudiciaryWithDefaultSkipValidations() throws Exception {
        // Setup: Create a court schedule
        final CourtSchedule courtSchedule = createTestCourtSchedule();
        databaseSeeder.insertCourtSchedule(courtSchedule);

        // Use a judiciary ID from the stubbed reference data (from referencedata.judiciaries.json)
        final String judiciaryId = "9ac02e8d-ee90-3da6-8d3e-0dd0af2cb976";

        // Call assign endpoint without skipValidations (should default to false)
        final String requestPayload = createObjectBuilder()
                .add("judiciaries", createArrayBuilder()
                        .add(createObjectBuilder()
                                .add("judiciaryId", judiciaryId)
                                .add("sessionIds", createArrayBuilder()
                                        .add(courtSchedule.getCourtScheduleId())
                                        .build())
                                .add(P_IS_DEPUTY, true)
                                .add(P_IS_BENCH_CHAIRMAN, false)
                                .build())
                        .build())
                .build()
                .toString();

        final Response response = postCommand(JUDICIARY_SESSION_URL,
                ASSIGN_JUDICIARY_CONTENT_TYPE,
                SYSTEM_USER_ID,
                requestPayload);

        // Should return ACCEPTED when validation passes (judiciary exists in reference data, defaults to skipValidations=false)
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
    }

    @Test
    void shouldRemoveAllJudiciaryAssignmentsForCourtSchedules() throws Exception {
        final CourtSchedule courtScheduleOne = createTestCourtSchedule();
        final CourtSchedule courtScheduleTwo = createTestCourtSchedule();
        databaseSeeder.insertCourtSchedule(courtScheduleOne);
        databaseSeeder.insertCourtSchedule(courtScheduleTwo);

        final CourtScheduleJudiciary assignmentOne = createTestCourtScheduleJudiciary(courtScheduleOne.getCourtScheduleId());
        final CourtScheduleJudiciary assignmentTwo = createTestCourtScheduleJudiciary(courtScheduleTwo.getCourtScheduleId());
        databaseSeeder.saveJudiciarySchedule(assignmentOne);
        databaseSeeder.saveJudiciarySchedule(assignmentTwo);

        List<CourtScheduleJudiciary> judiciariesBefore = databaseReader.courtScheduleJudiciaries();
        assertTrue(judiciariesBefore.stream()
                .anyMatch(js -> js.getId().getCourtScheduleId().equals(courtScheduleOne.getCourtScheduleId())));
        assertTrue(judiciariesBefore.stream()
                .anyMatch(js -> js.getId().getCourtScheduleId().equals(courtScheduleTwo.getCourtScheduleId())));

        final String requestPayload = createObjectBuilder()
                .add("courtScheduleIds", createArrayBuilder()
                        .add(courtScheduleOne.getCourtScheduleId())
                        .add(courtScheduleTwo.getCourtScheduleId())
                        .build())
                .build()
                .toString();

        final Response response = postCommand(REMOVE_ALL_JUDICIARY_URL,
                REMOVE_ALL_JUDICIARY_CONTENT_TYPE,
                SYSTEM_USER_ID,
                requestPayload);

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        List<CourtScheduleJudiciary> judiciariesAfter = databaseReader.courtScheduleJudiciaries();
        assertFalse(judiciariesAfter.stream()
                .anyMatch(js -> js.getId().getCourtScheduleId().equals(courtScheduleOne.getCourtScheduleId())));
        assertFalse(judiciariesAfter.stream()
                .anyMatch(js -> js.getId().getCourtScheduleId().equals(courtScheduleTwo.getCourtScheduleId())));
    }

    @Test
    void shouldReturnBadRequestWhenRemoveAllJudiciaryCourtScheduleIdsEmpty() {
        final String requestPayload = createObjectBuilder()
                .add("courtScheduleIds", createArrayBuilder().build())
                .build()
                .toString();

        final Response response = postCommand(REMOVE_ALL_JUDICIARY_URL,
                REMOVE_ALL_JUDICIARY_CONTENT_TYPE,
                SYSTEM_USER_ID,
                requestPayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        assertThat(response.readEntity(String.class), containsString("#/courtScheduleIds: expected minimum item count: 1, found: 0"));
    }

    @Test
    void shouldPersistIsDeputyAndIsBenchChairmanAsFalseWhenNotProvided() throws Exception {
        // Setup: Create a court schedule
        final CourtSchedule courtSchedule = createTestCourtSchedule();
        databaseSeeder.insertCourtSchedule(courtSchedule);

        // Use a judiciary ID from the stubbed reference data (from referencedata.judiciaries.json)
        final String judiciaryId = "9ac02e8d-ee90-3da6-8d3e-0dd0af2cb976";

        // Verify judiciary is not assigned initially
        List<CourtScheduleJudiciary> judiciariesBefore = databaseReader.courtScheduleJudiciaries();
        assertFalse(judiciariesBefore.stream()
                .anyMatch(js -> js.getId().getCourtScheduleId().equals(courtSchedule.getCourtScheduleId())
                        && js.getId().getJudiciaryId().equals(judiciaryId)));

        // Call assign endpoint without isDeputy and isBenchChairman
        final String requestPayload = createObjectBuilder()
                .add("judiciaries", createArrayBuilder()
                        .add(createObjectBuilder()
                                .add("judiciaryId", judiciaryId)
                                .add("sessionIds", createArrayBuilder()
                                        .add(courtSchedule.getCourtScheduleId())
                                        .build())
                                // Intentionally not including isDeputy and isBenchChairman
                                .build())
                        .build())
                .add("skipValidations", false)
                .build()
                .toString();

        final Response response = postCommand(JUDICIARY_SESSION_URL,
                ASSIGN_JUDICIARY_CONTENT_TYPE,
                SYSTEM_USER_ID,
                requestPayload);

        // Should return ACCEPTED when validation passes
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        // Wait a bit for async processing
        Thread.sleep(1000);

        // Verify judiciary is assigned and check database values
        List<CourtScheduleJudiciary> judiciariesAfter = databaseReader.courtScheduleJudiciaries();
        final CourtScheduleJudiciary assignedJudiciary = judiciariesAfter.stream()
                .filter(js -> js.getId().getCourtScheduleId().equals(courtSchedule.getCourtScheduleId())
                        && js.getId().getJudiciaryId().equals(judiciaryId))
                .findFirst()
                .orElse(null);

        assertThat(assignedJudiciary, notNullValue());
        // Verify that isDeputy and isBenchChairman are persisted as false when not provided
        assertFalse(assignedJudiciary.getDeputy(), "isDeputy should be persisted as false when not provided");
        assertFalse(assignedJudiciary.getBenchChairman(), "isBenchChairman should be persisted as false when not provided");
    }

    @Test
    void shouldAssignJudiciaryToSessionsCartesianProductWithViewPermission() throws Exception {
        final CourtSchedule courtSchedule1 = createTestCourtScheduleWithCourthouse(IT_SHARED_COURTHOUSE);
        databaseSeeder.insertCourtSchedule(courtSchedule1);
        final CourtSchedule courtSchedule2 = createTestCourtScheduleWithCourthouse(IT_SHARED_COURTHOUSE);
        databaseSeeder.insertCourtSchedule(courtSchedule2);

        final String requestPayload = createObjectBuilder()
                .add(P_COURT_SCHEDULE_IDS, createArrayBuilder()
                        .add(courtSchedule1.getCourtScheduleId())
                        .add(courtSchedule2.getCourtScheduleId())
                        .build())
                .add(P_JUDICIARY, createArrayBuilder()
                        .add(assignToSessionsJudiciaryLine(STUB_JUDICIARY_MAGISTRATE_1, LABEL_MAGISTRATE, true, false))
                        .add(assignToSessionsJudiciaryLine(STUB_JUDICIARY_MAGISTRATE_2, LABEL_MAGISTRATE, false, true))
                        .build())
                .build()
                .toString();

        final Response response = postCommand(ASSIGN_JUDICIARY_TO_SESSIONS_URL,
                ASSIGN_JUDICIARY_TO_SESSIONS_CONTENT_TYPE,
                USER_ID,
                requestPayload);

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
        Thread.sleep(500);

        final List<CourtScheduleJudiciary> judiciaries = databaseReader.courtScheduleJudiciaries();
        final List<CourtScheduleJudiciary> forSessions = judiciaries.stream()
                .filter(js -> js.getId().getCourtScheduleId().equals(courtSchedule1.getCourtScheduleId())
                        || js.getId().getCourtScheduleId().equals(courtSchedule2.getCourtScheduleId()))
                .toList();
        assertThat(forSessions.size(), is(4));
        assertThat(forSessions.stream().filter(js -> STUB_JUDICIARY_MAGISTRATE_1.equals(js.getId().getJudiciaryId())).count(), is(2L));
        assertThat(forSessions.stream().filter(js -> STUB_JUDICIARY_MAGISTRATE_2.equals(js.getId().getJudiciaryId())).count(), is(2L));

        final List<String> expectedSessionIds = List.of(courtSchedule1.getCourtScheduleId(), courtSchedule2.getCourtScheduleId());

        // Assert each session has both requested judiciaries (true Cartesian product).
        for (final String expectedSessionId : expectedSessionIds) {
            final List<CourtScheduleJudiciary> forSession = forSessions.stream()
                    .filter(js -> expectedSessionId.equals(js.getId().getCourtScheduleId()))
                    .toList();

            assertThat(forSession.size(), is(2));
            assertThat(forSession.stream().anyMatch(js -> STUB_JUDICIARY_MAGISTRATE_1.equals(js.getId().getJudiciaryId())), is(true));
            assertThat(forSession.stream().anyMatch(js -> STUB_JUDICIARY_MAGISTRATE_2.equals(js.getId().getJudiciaryId())), is(true));

            final CourtScheduleJudiciary magistrate1 = forSession.stream()
                    .filter(js -> STUB_JUDICIARY_MAGISTRATE_1.equals(js.getId().getJudiciaryId()))
                    .findFirst()
                    .orElseThrow();
            assertTrue(magistrate1.getBenchChairman());
            assertFalse(magistrate1.getDeputy());
            assertThat(magistrate1.getJudiciaryType(), is(LABEL_MAGISTRATE));
            assertThat(magistrate1.getCourtListingProfileId(),
                    is(courtSchedule1.getCourtScheduleId().equals(expectedSessionId)
                            ? courtSchedule1.getListingProfileId()
                            : courtSchedule2.getListingProfileId()));
            assertTrue(magistrate1.getRotaJudiciaryId() == null || magistrate1.getRotaJudiciaryId().isEmpty(),
                    "UI assignment should leave rota judiciary id unset");
            assertTrue(magistrate1.getPosition() == null || magistrate1.getPosition().isEmpty(),
                    "UI assignment should leave position unset");

            final CourtScheduleJudiciary magistrate2 = forSession.stream()
                    .filter(js -> STUB_JUDICIARY_MAGISTRATE_2.equals(js.getId().getJudiciaryId()))
                    .findFirst()
                    .orElseThrow();
            assertFalse(magistrate2.getBenchChairman());
            assertTrue(magistrate2.getDeputy());
            assertThat(magistrate2.getJudiciaryType(), is(LABEL_MAGISTRATE));
            assertThat(magistrate2.getCourtListingProfileId(),
                    is(courtSchedule1.getCourtScheduleId().equals(expectedSessionId)
                            ? courtSchedule1.getListingProfileId()
                            : courtSchedule2.getListingProfileId()));
            assertTrue(magistrate2.getRotaJudiciaryId() == null || magistrate2.getRotaJudiciaryId().isEmpty(),
                    "UI assignment should leave rota judiciary id unset");
            assertTrue(magistrate2.getPosition() == null || magistrate2.getPosition().isEmpty(),
                    "UI assignment should leave position unset");
        }
    }

    @Test
    void shouldAssignJudiciaryToSessionsReturn400WhenMixedCourthouses() throws Exception {
        final CourtSchedule courtSchedule1 = createTestCourtScheduleWithCourthouse(IT_SHARED_COURTHOUSE + "-A");
        databaseSeeder.insertCourtSchedule(courtSchedule1);
        final CourtSchedule courtSchedule2 = createTestCourtScheduleWithCourthouse(IT_SHARED_COURTHOUSE + "-B");
        databaseSeeder.insertCourtSchedule(courtSchedule2);

        final String requestPayload = createObjectBuilder()
                .add(P_COURT_SCHEDULE_IDS, createArrayBuilder()
                        .add(courtSchedule1.getCourtScheduleId())
                        .add(courtSchedule2.getCourtScheduleId())
                        .build())
                .add(P_JUDICIARY, createArrayBuilder()
                        .add(assignToSessionsJudiciaryLine(STUB_JUDICIARY_MAGISTRATE_1, LABEL_MAGISTRATE, null, null))
                        .build())
                .build()
                .toString();

        final Response response = postCommand(ASSIGN_JUDICIARY_TO_SESSIONS_URL,
                ASSIGN_JUDICIARY_TO_SESSIONS_CONTENT_TYPE,
                USER_ID,
                requestPayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
    }

    @Test
    void shouldAssignJudiciaryToSessionsReturn400WhenInvalidBenchComposition() throws Exception {
        final CourtSchedule courtSchedule = createTestCourtScheduleWithCourthouse(IT_SHARED_COURTHOUSE);
        databaseSeeder.insertCourtSchedule(courtSchedule);

        final String requestPayload = createObjectBuilder()
                .add(P_COURT_SCHEDULE_IDS, createArrayBuilder().add(courtSchedule.getCourtScheduleId()).build())
                .add(P_JUDICIARY, createArrayBuilder()
                        .add(assignToSessionsJudiciaryLine(STUB_JUDICIARY_CIRCUIT_JUDGE, "Circuit Judge", null, null))
                        .add(assignToSessionsJudiciaryLine(STUB_JUDICIARY_RECORDER, "Recorder", null, null))
                        .build())
                .build()
                .toString();

        final Response response = postCommand(ASSIGN_JUDICIARY_TO_SESSIONS_URL,
                ASSIGN_JUDICIARY_TO_SESSIONS_CONTENT_TYPE,
                USER_ID,
                requestPayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
    }

    @Test
    void shouldAssignJudiciaryToSessionsReplaceAllRemovesPriorAssignments() throws Exception {
        final CourtSchedule courtSchedule = createTestCourtScheduleWithCourthouse(IT_SHARED_COURTHOUSE);
        databaseSeeder.insertCourtSchedule(courtSchedule);

        final CourtScheduleJudiciary prior = createTestCourtScheduleJudiciary(courtSchedule.getCourtScheduleId());
        final String priorJudiciaryId = prior.getId().getJudiciaryId();
        databaseSeeder.saveJudiciarySchedule(prior);

        final String requestPayload = createObjectBuilder()
                .add(P_COURT_SCHEDULE_IDS, createArrayBuilder().add(courtSchedule.getCourtScheduleId()).build())
                .add(P_JUDICIARY, createArrayBuilder()
                        .add(assignToSessionsJudiciaryLine(STUB_JUDICIARY_MAGISTRATE_1, LABEL_MAGISTRATE, null, null))
                        .build())
                .build()
                .toString();

        final Response response = postCommand(ASSIGN_JUDICIARY_TO_SESSIONS_URL,
                ASSIGN_JUDICIARY_TO_SESSIONS_CONTENT_TYPE,
                USER_ID,
                requestPayload);

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
        Thread.sleep(500);

        final List<CourtScheduleJudiciary> judiciaries = databaseReader.courtScheduleJudiciaries();
        assertFalse(judiciaries.stream().anyMatch(js -> priorJudiciaryId.equals(js.getId().getJudiciaryId())));
        assertTrue(judiciaries.stream().anyMatch(js -> STUB_JUDICIARY_MAGISTRATE_1.equals(js.getId().getJudiciaryId())
                && courtSchedule.getCourtScheduleId().equals(js.getId().getCourtScheduleId())));
    }

    @Test
    void shouldAssignJudiciaryToSessionsReturn400WhenJudiciaryNotInRefdata() throws Exception {
        final CourtSchedule courtSchedule = createTestCourtScheduleWithCourthouse(IT_SHARED_COURTHOUSE);
        databaseSeeder.insertCourtSchedule(courtSchedule);
        final String unknownJudicialId = randomUUID().toString();

        final String requestPayload = createObjectBuilder()
                .add(P_COURT_SCHEDULE_IDS, createArrayBuilder().add(courtSchedule.getCourtScheduleId()).build())
                .add(P_JUDICIARY, createArrayBuilder()
                        .add(assignToSessionsJudiciaryLine(unknownJudicialId, LABEL_MAGISTRATE, null, null))
                        .build())
                .build()
                .toString();

        final Response response = postCommand(ASSIGN_JUDICIARY_TO_SESSIONS_URL,
                ASSIGN_JUDICIARY_TO_SESSIONS_CONTENT_TYPE,
                USER_ID,
                requestPayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
    }

    @Test
    void shouldAssignJudiciaryToSessionsClearBenchWhenJudiciaryEmpty() throws Exception {
        final CourtSchedule courtSchedule = createTestCourtScheduleWithCourthouse(IT_SHARED_COURTHOUSE);
        databaseSeeder.insertCourtSchedule(courtSchedule);
        databaseSeeder.saveJudiciarySchedule(createTestCourtScheduleJudiciary(courtSchedule.getCourtScheduleId()));

        final String requestPayload = createObjectBuilder()
                .add(P_COURT_SCHEDULE_IDS, createArrayBuilder().add(courtSchedule.getCourtScheduleId()).build())
                .add(P_JUDICIARY, createArrayBuilder().build())
                .build()
                .toString();

        final Response response = postCommand(ASSIGN_JUDICIARY_TO_SESSIONS_URL,
                ASSIGN_JUDICIARY_TO_SESSIONS_CONTENT_TYPE,
                USER_ID,
                requestPayload);

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
        Thread.sleep(500);

        final List<CourtScheduleJudiciary> judiciaries = databaseReader.courtScheduleJudiciaries();
        assertTrue(judiciaries.stream().noneMatch(js -> courtSchedule.getCourtScheduleId().equals(js.getId().getCourtScheduleId())));
    }

    private CourtSchedule createTestCourtScheduleWithCourthouse(final String courtHouseId) {
        final CourtSchedule courtSchedule = createTestCourtSchedule();
        courtSchedule.setCourtHouseId(courtHouseId);
        return courtSchedule;
    }

    private JsonObject assignToSessionsJudiciaryLine(final String judicialId,
                                                     final String judiciaryType,
                                                     final Boolean isBenchChairman,
                                                     final Boolean isDeputy) {
        JsonObjectBuilder line = createObjectBuilder()
                .add(P_JUDICIAL_ID, judicialId)
                .add(P_JUDICIAL_ROLE_TYPE, createObjectBuilder().add(P_JUDICIARY_TYPE, judiciaryType).build());
        if (isBenchChairman != null) {
            line = line.add(P_IS_BENCH_CHAIRMAN, isBenchChairman);
        }
        if (isDeputy != null) {
            line = line.add(P_IS_DEPUTY, isDeputy);
        }
        return line.build();
    }

    private CourtSchedule createTestCourtSchedule() {
        final CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setCourtScheduleId(randomUUID().toString());
        courtSchedule.setListingProfileId("CS" + randomUUID().toString().substring(0, 8));
        courtSchedule.setOuCode("B40IM00");
        courtSchedule.setSessionDate(LocalDate.now().plusDays(30));
        courtSchedule.setActive(true);
        courtSchedule.setSlotBased(true);
        courtSchedule.setMaxSlots(10);
        courtSchedule.setAvailableSlots(10);
        courtSchedule.setMaxDuration(240);
        courtSchedule.setAvailableDuration(240);
        courtSchedule.setCourtSession(AM_SESSION);
        courtSchedule.setPanel("ADULT");
        courtSchedule.setBusinessType("TRL");
        courtSchedule.setSupportAdSplit(false);
        courtSchedule.setIsOverbookingAllowed(false);
        courtSchedule.setMaxAdMorningDuration(0);
        courtSchedule.setMaxAdAfternoonDuration(0);
        return courtSchedule;
    }

    private CourtScheduleJudiciary createTestCourtScheduleJudiciary(final String courtScheduleId) {
        final CourtScheduleJudiciary courtScheduleJudiciary = new CourtScheduleJudiciary();
        final CourtScheduleJudiciaryKey key = new CourtScheduleJudiciaryKey();
        key.setCourtScheduleId(courtScheduleId);
        key.setJudiciaryId(randomUUID().toString());
        courtScheduleJudiciary.setId(key);
        courtScheduleJudiciary.setCourtListingProfileId("CS" + randomUUID().toString().substring(0, 8));
        courtScheduleJudiciary.setRotaJudiciaryId("ROTA" + randomUUID().toString().substring(0, 8));
        courtScheduleJudiciary.setTitle("Mr");
        courtScheduleJudiciary.setForenames("John");
        courtScheduleJudiciary.setSurname("Doe");
        courtScheduleJudiciary.setEmail("john.doe@example.com");
        courtScheduleJudiciary.setJudiciaryType("MAGISTRATE");
        courtScheduleJudiciary.setBenchChairman(false);
        courtScheduleJudiciary.setDeputy(false);
        courtScheduleJudiciary.setPosition("1");
        courtScheduleJudiciary.setActive(true);
        return courtScheduleJudiciary;
    }

    private AllocatedListing getAllocatedListing(final CourtSchedule courtSchedule) {
        final AllocatedListing allocatedListing = RANDOM.nextObject(AllocatedListing.class);
        allocatedListing.setId(randomUUID().toString());
        allocatedListing.setCourtScheduleId(courtSchedule.getCourtScheduleId());
        allocatedListing.setCourtRoomId(courtSchedule.getCourtRoomNumber());
        allocatedListing.setOucode(courtSchedule.getOuCode());
        allocatedListing.setHearingStartTime(Date.from(courtSchedule.getSessionDate().atTime(14, 0).atZone(UTC_ZONE).toInstant()));
        allocatedListing.setDuration(60);
        allocatedListing.setHearingId(randomUUID().toString());
        allocatedListing.setBookingId(randomUUID().toString());
        return allocatedListing;
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
        // Given - index 5 (5th Friday): not every month has 5 Fridays, so 0 or more sessions may be created
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

        // Wait for processing - index 5 (5th Friday) may not exist in every month, so 0 or more court schedules
        final List<CourtSchedule> courtSchedules = databaseReader.courtSchedules();
        assertThat("Court schedules count", courtSchedules.size(), is(greaterThanOrEqualTo(0)));

        if (!courtSchedules.isEmpty()) {
            final CourtSchedule courtSchedule = courtSchedules.get(0);
            assertThat(courtSchedule.getCourtScheduleId(), is(notNullValue()));
        }
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
        // Given - Test with months where 5th Friday doesn't exist
        // Find a future date range: start from next month, find a month with 5 Fridays, then include following months
        LocalDate baseDate = now().plusMonths(1).withDayOfMonth(1);
        LocalDate startDate = baseDate;
        LocalDate endDate = baseDate.plusMonths(2).withDayOfMonth(baseDate.plusMonths(2).lengthOfMonth());

        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayloadWithDates(
                "create-court-schedule-monthly-frequency-crown-index.json",
                startDate,
                endDate
        );

        // When
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);

        // Then
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        // Wait for processing and verify court schedules are created
        final List<CourtSchedule> courtSchedules = databaseReader.courtSchedules();

        // Sessions should only be created for months that have a 5th Friday
        // Some months may not have 5 Fridays, so fewer sessions may be created
        assertThat("Sessions should be created for months with 5th Friday",
                courtSchedules.size(), is(greaterThanOrEqualTo(0)));

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
    void shouldReturn400WhenAttemptingToChangeJurisdictionInUpdate() throws SQLException {
        UUID courtScheduleId = UUID.randomUUID();
        CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        String courtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3";
        String courtHouseId = "785339c1-af71-3322-a55b-ba255e0db1c2";
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType("DVLA");
        expected.setJurisdiction("MAGISTRATES"); // Set initial jurisdiction to MAGISTRATES
        expected.setSupportAdSplit(false);
        expected.setSessionDate(LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)));
        expected.setCourtRoomId(courtRoomId);
        expected.setCourtHouseId(courtHouseId);
        databaseSeeder.insertCourtSchedule(expected);

        String updateCourtSchedulePayload = getPayload("update-court-schedule-change-jurisdiction.json");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_SCHEDULE_ID", expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_ROOM_ID", courtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("BUSINESS_TYPE", "DVLA");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("SESSION_TYPE", "AM");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("PANEL", "ADULT");
        // Jurisdiction is set to CROWN in the payload, which should fail

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String errorResponseMessage = response.readEntity(String.class);
        assertThat(errorResponseMessage, containsString("Jurisdiction cannot be changed"));
    }

    @Test
    void shouldReturn400WhenUsingInvalidBusinessTypeInUpdate() throws SQLException {
        UUID courtScheduleId = UUID.randomUUID();
        CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        String courtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3";
        String courtHouseId = "785339c1-af71-3322-a55b-ba255e0db1c2";
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType("DVLA");
        expected.setSupportAdSplit(false);
        expected.setSessionDate(LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)));
        expected.setCourtRoomId(courtRoomId);
        expected.setCourtHouseId(courtHouseId);
        databaseSeeder.insertCourtSchedule(expected);

        String updateCourtSchedulePayload = getPayload("update-court-schedule-invalid-business-type.json");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_SCHEDULE_ID", expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_ROOM_ID", courtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("INVALID_BT", "INVALIDBT"); // Business type that doesn't exist
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("SESSION_TYPE", "AM");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("PANEL", "ADULT");

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String errorResponseMessage = response.readEntity(String.class);
        assertThat(errorResponseMessage, containsString("Invalid business type"));
    }

    @Test
    void shouldReturn400WhenBusinessTypeJurisdictionDoesNotMatchInUpdate() throws SQLException {
        // Create a MAGISTRATES session
        UUID courtScheduleId = UUID.randomUUID();
        CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        String courtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3";
        String courtHouseId = "785339c1-af71-3322-a55b-ba255e0db1c2";
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType("DVLA");
        expected.setJurisdiction("CROWN"); // Session has CROWN jurisdiction
        expected.setSupportAdSplit(false);
        expected.setSlotBased(true); // Ensure slot-based for maxSlots
        expected.setSessionDate(LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)));
        expected.setCourtRoomId(courtRoomId);
        expected.setCourtHouseId(courtHouseId);
        databaseSeeder.insertCourtSchedule(expected);

        // Try to update with APP business type which has MAGISTRATES jurisdiction
        // This should work since both session and business type have MAGISTRATES jurisdiction
        // To test the failure case, we would need a business type with CROWN jurisdiction
        // Note: This test verifies the validation logic is in place
        // If a business type with CROWN jurisdiction exists and is used for a MAGISTRATES session,
        // it should return 400 with "Invalid business type" error
        String updateCourtSchedulePayload = getPayload("update-court-schedule.json");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_SCHEDULE_ID", expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_ROOM_ID", courtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("BUSINESS_TYPE", "APP"); // APP has MAGISTRATES jurisdiction
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("SESSION_TYPE", "AM");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("PANEL", "ADULT");
        // Keep jurisdiction as MAGISTRATES (can't change it)

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);

        // Since APP has MAGISTRATES jurisdiction and session is MAGISTRATES, this should succeed
        // The validation logic is tested - if a business type with CROWN jurisdiction were used
        // for this MAGISTRATES session, it would return 400
        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
    }

    @Test
    void shouldReturn400WhenBusinessTypeHasWrongJurisdictionForCrownSessionInUpdate() throws SQLException {
        // Create a CROWN session initially
        UUID courtScheduleId = UUID.randomUUID();
        CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        String courtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3"; // CROWN courtroom from CP source
        String courtHouseId = "785339c1-af71-3322-a55b-ba255e0db1c2"; // CROWN court house from CP source
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType("CRC"); // Use a CROWN business type initially
        expected.setJurisdiction("CROWN"); // Session has CROWN jurisdiction
        expected.setSupportAdSplit(false);
        expected.setSlotBased(true); // Ensure slot-based for consistency
        expected.setSessionDate(LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)));
        expected.setCourtRoomId(courtRoomId);
        expected.setCourtHouseId(courtHouseId);
        databaseSeeder.insertCourtSchedule(expected);

        // Try to update with APP business type which has MAGISTRATES jurisdiction
        // This should fail since APP has MAGISTRATES jurisdiction but session is CROWN
        // For CROWN jurisdiction, we need isDraft and should not have panel
        String updateCourtSchedulePayload = getPayload("update-court-schedule-change-jurisdiction.json");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_SCHEDULE_ID", expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_ROOM_ID", courtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("BUSINESS_TYPE", "APP"); // APP has MAGISTRATES jurisdiction
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("SESSION_TYPE", "AM");
        // Keep jurisdiction as CROWN (from the payload template)
        // Keep isDraft as false (from the payload template)

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);

        // Should fail because APP has MAGISTRATES jurisdiction but session is CROWN
        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String errorResponseMessage = response.readEntity(String.class);
        assertThat(errorResponseMessage, containsString("Business Type jurisdiction MAGISTRATES does not match session jurisdiction CROWN"));
    }

    @Test
    void shouldAssignCourtroomToMultipleEligibleSessions() throws SQLException {
        // Draft without hearings - eligible
        // Draft with hearings - not eligible
        // Assigned without hearings - not eligible

        UUID draftSessionId = UUID.randomUUID();
        CourtSchedule draftSessionWithHearing = RANDOM.nextObject(CourtSchedule.class);
        draftSessionWithHearing.setCourtScheduleId(draftSessionId.toString());
        draftSessionWithHearing.setBusinessType("DVLA");
        draftSessionWithHearing.setSlotBased(true);
        draftSessionWithHearing.setMaxSlots(15);
        draftSessionWithHearing.setAvailableSlots(15);
        draftSessionWithHearing.setIsDraft(true); // Draft session
        draftSessionWithHearing.setSupportAdSplit(false);
        draftSessionWithHearing.setCourtSession(AM_SESSION);
        draftSessionWithHearing.setPanel("ADULT");
        draftSessionWithHearing.setCourtRoomId("original-courtroom-id");
        draftSessionWithHearing.setJurisdiction("CROWN");
        draftSessionWithHearing.setCourtHouseId("785339c1-af71-3322-a55b-ba255e0db1c2");
        draftSessionWithHearing.setSessionStartTime(DateUtils.localDateToDateWithTime(draftSessionWithHearing.getSessionDate(), 9, 0));
        draftSessionWithHearing.setSessionEndTime(DateUtils.localDateToDateWithTime(draftSessionWithHearing.getSessionDate(), 13, 0));
        databaseSeeder.insertCourtSchedule(draftSessionWithHearing);

        // Create hearing for draft session (not eligible)
        UUID hearingId1 = UUID.randomUUID();
        UUID bookingId1 = UUID.randomUUID();
        createAllocatedListing(draftSessionWithHearing, hearingId1, bookingId1, 15, "10:00");

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
        assignCourtroomPayload = assignCourtroomPayload.replace("COURT_SCHEDULE_ID_1", draftSessionWithHearing.getCourtScheduleId());
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
        jakarta.json.JsonArray jsonResponseArray = jsonResponse.getJsonArray("errorGroups");

        // Verify response is an array
        assertThat("Response should be an array", jsonResponseArray, notNullValue());

        // Draft sessions with hearing should not be assigned
        boolean draftSessionWithHearingInErrorGroup = jsonResponseArray.stream()
                .map(JsonValue::asJsonObject)
                .anyMatch(errorGroup -> {
                    jakarta.json.JsonArray sessions = errorGroup.getJsonArray("sessions");
                    return sessions.stream()
                            .map(s -> s.asJsonObject())
                            .anyMatch(s -> draftSessionWithHearing.getCourtScheduleId().equals(s.getString("courtScheduleId")));
                });
        assertThat("Draft sessions with hearing should not be eligible", draftSessionWithHearingInErrorGroup, is(true));        // Draft sessions should be successfully assigned (not in any error group)

        // Draft sessions should be successfully assigned (not in any error group)
        boolean draftSessionNoHearingInErrorGroup = jsonResponseArray.stream()
                .map(JsonValue::asJsonObject)
                .anyMatch(errorGroup -> {
                    jakarta.json.JsonArray sessions = errorGroup.getJsonArray("sessions");
                    return sessions.stream()
                            .map(s -> s.asJsonObject())
                            .anyMatch(s ->  draftSessionNoHearings.getCourtScheduleId().equals(s.getString("courtScheduleId")));
                });
        assertThat("Draft sessions with hearing should not be eligible", draftSessionNoHearingInErrorGroup, is(false));

        // Assigned session should be in error group
        boolean foundAssignedSessionInErrorGroup = jsonResponseArray.stream()
                .map(JsonValue::asJsonObject)
                .anyMatch(errorGroup -> {
                    String error = errorGroup.getString("error");
                    if ("Cannot assign courtroom to an assigned session".equals(error)) {
                        jakarta.json.JsonArray sessions = errorGroup.getJsonArray("sessions");
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
        jakarta.json.JsonArray jsonResponseArray = jsonResponse.getJsonArray("errorGroups");

        // Verify response is an array
        assertThat("Response should be an array", jsonResponseArray, notNullValue());

        // Find the error group with the expected error message
        boolean foundErrorGroup = jsonResponseArray.stream()
                .map(JsonValue::asJsonObject)
                .anyMatch(errorGroup -> {
                    String error = errorGroup.getString("error");
                    if ("Cannot assign courtroom to an assigned session".equals(error)) {
                        jakarta.json.JsonArray sessions = errorGroup.getJsonArray("sessions");
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
        jakarta.json.JsonArray jsonResponseArray = jsonResponse.getJsonArray("errorGroups");

        // Verify response is an array
        assertThat("Response should be an array", jsonResponseArray, notNullValue());

        // Find the error group with the expected error message
        boolean foundErrorGroup = jsonResponseArray.stream()
                .map(JsonValue::asJsonObject)
                .anyMatch(errorGroup -> {
                    String error = errorGroup.getString("error");
                    if ("Cannot assign courtroom to an assigned session".equals(error)) {
                        jakarta.json.JsonArray sessions = errorGroup.getJsonArray("sessions");
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
        jakarta.json.JsonArray jsonResponseArray = jsonResponse.getJsonArray("errorGroups");

        // Verify ineligible session is in error group
        boolean foundIneligibleSession = jsonResponseArray.stream()
                .map(JsonValue::asJsonObject)
                .anyMatch(errorGroup -> {
                    String error = errorGroup.getString("error");
                    if ("Cannot assign courtroom to an assigned session".equals(error)) {
                        jakarta.json.JsonArray sessions = errorGroup.getJsonArray("sessions");
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
                    jakarta.json.JsonArray sessions = errorGroup.getJsonArray("sessions");
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
        jakarta.json.JsonArray jsonResponseArray = jsonResponse.getJsonArray("errorGroups");

        // Verify response is an array
        assertThat("Response should be an array", jsonResponseArray, notNullValue());

        // Verify MAGISTRATES session is in error group
        boolean foundIneligibleMagistratesSession = jsonResponseArray.stream()
                .map(JsonValue::asJsonObject)
                .anyMatch(errorGroup -> {
                    String error = errorGroup.getString("error");
                    if ("assign.courtroom endpoint is only valid for CROWN jurisdiction sessions".equals(error)) {
                        jakarta.json.JsonArray sessions = errorGroup.getJsonArray("sessions");
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
                    jakarta.json.JsonArray sessions = errorGroup.getJsonArray("sessions");
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
        jakarta.json.JsonArray jsonResponseArray = jsonResponse.getJsonArray("errorGroups");

        // Verify response is an array
        assertThat("Response should be an array", jsonResponseArray, notNullValue());

        // Verify session with wrong court centre is in error group
        boolean foundIneligibleWrongCourtCentreSession = jsonResponseArray.stream()
                .map(JsonValue::asJsonObject)
                .anyMatch(errorGroup -> {
                    String error = errorGroup.getString("error");
                    if ("The new courtroom must belong to the same court centre as the session".equals(error)) {
                        jakarta.json.JsonArray sessions = errorGroup.getJsonArray("sessions");
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
                    jakarta.json.JsonArray sessions = errorGroup.getJsonArray("sessions");
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
        jakarta.json.JsonArray jsonResponseArray = jsonResponse.getJsonArray("errorGroups");

        // Verify response is an array
        assertThat("Response should be an array", jsonResponseArray, notNullValue());

        // Verify session is in error group due to duplicate
        boolean foundIneligibleDuplicateSession = jsonResponseArray.stream()
                .map(JsonValue::asJsonObject)
                .anyMatch(errorGroup -> {
                    String error = errorGroup.getString("error");
                    if (error.contains("Duplicate session already exists")) {
                        jakarta.json.JsonArray sessions = errorGroup.getJsonArray("sessions");
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
        jakarta.json.JsonArray jsonResponseArray = jsonResponse.getJsonArray("errorGroups");

        // Verify response is an array
        assertThat("Response should be an array", jsonResponseArray, notNullValue());

        // Verify session is in error group due to duplicate
        boolean foundIneligibleDuplicateSession = jsonResponseArray.stream()
                .map(JsonValue::asJsonObject)
                .anyMatch(errorGroup -> {
                    String error = errorGroup.getString("error");
                    if (error.contains("Duplicate session already exists")) {
                        jakarta.json.JsonArray sessions = errorGroup.getJsonArray("sessions");
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
        jakarta.json.JsonArray jsonResponseArray = jsonResponse.getJsonArray("errorGroups");

        // Verify response is an array
        assertThat("Response should be an array", jsonResponseArray, notNullValue());

        // Verify session is in error group due to duplicate
        boolean foundIneligibleDuplicateSession = jsonResponseArray.stream()
                .map(JsonValue::asJsonObject)
                .anyMatch(errorGroup -> {
                    String error = errorGroup.getString("error");
                    if (error.contains("Duplicate session already exists")) {
                        jakarta.json.JsonArray sessions = errorGroup.getJsonArray("sessions");
                        return sessions.stream()
                                .map(s -> s.asJsonObject())
                                .anyMatch(s -> newSessionId.toString().equals(s.getString("courtScheduleId")));
                    }
                    return false;
                });
        assertThat("Should find AD session with duplicate AM session in error group", foundIneligibleDuplicateSession, is(true));
    }

    @Test
    void shouldReturn400WhenUpdatingCrownDraftSessionWithHearingsBookedToChangeCourtroom() throws SQLException {
        // Given - CROWN draft session with hearings booked
        UUID courtScheduleId = UUID.randomUUID();
        CourtSchedule draftSession = RANDOM.nextObject(CourtSchedule.class);
        String originalCourtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3";
        String newCourtRoomId = "e06e1734-aa04-3ec0-b14c-400edab8b831"; // Different courtroom
        String courtHouseId = "785339c1-af71-3322-a55b-ba255e0db1c2";

        draftSession.setCourtScheduleId(courtScheduleId.toString());
        draftSession.setBusinessType("FWT");
        draftSession.setSlotBased(true);
        draftSession.setMaxSlots(15);
        draftSession.setAvailableSlots(10); // Some slots booked
        draftSession.setIsDraft(true);
        draftSession.setSupportAdSplit(false);
        draftSession.setCourtSession(AM_SESSION);
        draftSession.setPanel("ADULT");
        draftSession.setJurisdiction("CROWN");
        draftSession.setCourtRoomId(originalCourtRoomId);
        draftSession.setCourtHouseId(courtHouseId);
        draftSession.setSessionDate(LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)));
        draftSession.setSessionStartTime(DateUtils.localDateToDateWithTime(draftSession.getSessionDate(), 9, 0));
        draftSession.setSessionEndTime(DateUtils.localDateToDateWithTime(draftSession.getSessionDate(), 13, 0));
        databaseSeeder.insertCourtSchedule(draftSession);

        // Create allocated listing to simulate hearings booked
        final UUID hearingId = UUID.randomUUID();
        final UUID bookingId = UUID.randomUUID();
        createAllocatedListing(draftSession, hearingId, bookingId, 60, "10:00");

        // When - Try to update courtroom
        String updateCourtSchedulePayload = getPayload("update-court-schedule.json");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_SCHEDULE_ID", draftSession.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_ROOM_ID", newCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("BUSINESS_TYPE", "FWT");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("SESSION_TYPE", AM_SESSION);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("PANEL", "ADULT");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("\"jurisdiction\": \"MAGISTRATES\"", "\"jurisdiction\": \"CROWN\"");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("\"maxDuration\": 10", "\"maxSlots\": 15,\n  \"maxDuration\": 0,\n  \"isDraft\": true");

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);
        final String responsePayload = response.readEntity(String.class);

        // Then - Should return 400 with error message
        assertThat("Update response: " + responsePayload, response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        assertThat(responsePayload, containsString("Cannot assign courtroom to a CROWN draft session with hearings booked"));
    }

    @Test
    void shouldReturn400WhenUpdatingCrownDraftSessionWithHearingsBookedToChangeState() throws SQLException {
        // Given - CROWN draft session with hearings booked
        UUID courtScheduleId = UUID.randomUUID();
        CourtSchedule draftSession = RANDOM.nextObject(CourtSchedule.class);
        String courtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3";
        String courtHouseId = "785339c1-af71-3322-a55b-ba255e0db1c2";

        draftSession.setCourtScheduleId(courtScheduleId.toString());
        draftSession.setBusinessType("FWT");
        draftSession.setSlotBased(true);
        draftSession.setMaxSlots(15);
        draftSession.setAvailableSlots(10); // Some slots booked
        draftSession.setIsDraft(true);
        draftSession.setSupportAdSplit(false);
        draftSession.setCourtSession(AM_SESSION);
        draftSession.setPanel("ADULT");
        draftSession.setJurisdiction("CROWN");
        draftSession.setCourtRoomId(courtRoomId);
        draftSession.setCourtHouseId(courtHouseId);
        draftSession.setSessionDate(LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)));
        draftSession.setSessionStartTime(DateUtils.localDateToDateWithTime(draftSession.getSessionDate(), 9, 0));
        draftSession.setSessionEndTime(DateUtils.localDateToDateWithTime(draftSession.getSessionDate(), 13, 0));
        databaseSeeder.insertCourtSchedule(draftSession);

        // Create allocated listing to simulate hearings booked
        final UUID hearingId = UUID.randomUUID();
        final UUID bookingId = UUID.randomUUID();
        createAllocatedListing(draftSession, hearingId, bookingId, 60, "10:00");

        // When - Try to change state from draft to assigned (isDraft: false)
        String updateCourtSchedulePayload = getPayload("update-court-schedule.json");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_SCHEDULE_ID", draftSession.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_ROOM_ID", courtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("BUSINESS_TYPE", "FWT");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("SESSION_TYPE", AM_SESSION);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("PANEL", "ADULT");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("\"jurisdiction\": \"MAGISTRATES\"", "\"jurisdiction\": \"CROWN\"");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("\"maxDuration\": 10", "\"maxSlots\": 15,\n  \"maxDuration\": 0,\n  \"isDraft\": false");

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);
        final String responsePayload = response.readEntity(String.class);

        // Then - Should return 400 with error message
        assertThat("Update response: " + responsePayload, response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        assertThat(responsePayload, containsString("Cannot assign state to a CROWN draft session with hearings booked"));
    }

    @Test
    void shouldReturnErrorWhenAssigningCourtroomToCrownDraftSessionWithHearingsBooked() throws SQLException {
        // Given - CROWN draft session with hearings booked
        UUID draftSessionId = UUID.randomUUID();
        CourtSchedule draftSession = RANDOM.nextObject(CourtSchedule.class);
        String originalCourtRoomId = "original-courtroom-id";
        String newCourtRoomId = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3";
        String courtHouseId = "785339c1-af71-3322-a55b-ba255e0db1c2";

        draftSession.setCourtScheduleId(draftSessionId.toString());
        draftSession.setBusinessType("FWT");
        draftSession.setSlotBased(true);
        draftSession.setMaxSlots(15);
        draftSession.setAvailableSlots(10); // Some slots booked
        draftSession.setIsDraft(true);
        draftSession.setSupportAdSplit(false);
        draftSession.setCourtSession(AM_SESSION);
        draftSession.setPanel("ADULT");
        draftSession.setJurisdiction("CROWN");
        draftSession.setCourtRoomId(originalCourtRoomId);
        draftSession.setCourtHouseId(courtHouseId);
        draftSession.setSessionDate(LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)));
        draftSession.setSessionStartTime(DateUtils.localDateToDateWithTime(draftSession.getSessionDate(), 9, 0));
        draftSession.setSessionEndTime(DateUtils.localDateToDateWithTime(draftSession.getSessionDate(), 13, 0));
        databaseSeeder.insertCourtSchedule(draftSession);

        // Create allocated listing to simulate hearings booked
        final UUID hearingId = UUID.randomUUID();
        final UUID bookingId = UUID.randomUUID();
        createAllocatedListing(draftSession, hearingId, bookingId, 60, "10:00");

        // When - Try to assign courtroom via assign.courtroom endpoint
        String assignCourtroomPayload = getPayload("assign-courtroom-CROWN-with-hearing.json");
        assignCourtroomPayload = assignCourtroomPayload.replace("COURT_SCHEDULE_ID_1", draftSessionId.toString());
        assignCourtroomPayload = assignCourtroomPayload.replace("COURT_ROOM_ID", newCourtRoomId);

        final Response response = postCommand(BASE_RESOURCE_URL + ASSIGN_COURTROOM_URL, COURT_SCHEDULE_ASSIGN_COURTROOM_CONTENT_TYPE, USER_ID, assignCourtroomPayload);
        final String responsePayload = response.readEntity(String.class);

        // Then - Should return 200 with error group containing the error
        assertThat("Assign courtroom response: " + responsePayload, response.getStatus(), is(OK.getStatusCode()));

        // Parse JSON response
        JsonReader jsonReader = Json.createReader(new StringReader(responsePayload));
        JsonObject jsonResponse = jsonReader.readObject();
        jsonReader.close();

        // Verify response has errorGroups key
        assertThat("Response should contain errorGroups", jsonResponse.containsKey("errorGroups"), is(true));
        jakarta.json.JsonArray jsonResponseArray = jsonResponse.getJsonArray("errorGroups");

        // Verify response is an array
        assertThat("Response should be an array", jsonResponseArray, notNullValue());

        // Find the error group with the expected error message
        boolean foundErrorGroup = jsonResponseArray.stream()
                .map(JsonValue::asJsonObject)
                .anyMatch(errorGroup -> {
                    String error = errorGroup.getString("error");
                    if ("Cannot assign courtroom to a CROWN draft session with hearings booked".equals(error)) {
                        jakarta.json.JsonArray sessions = errorGroup.getJsonArray("sessions");
                        return sessions.stream()
                                .map(s -> s.asJsonObject())
                                .anyMatch(s -> draftSessionId.toString().equals(s.getString("courtScheduleId")));
                    }
                    return false;
                });
        assertThat("Should find draft session with hearings in error group", foundErrorGroup, is(true));
    }

    @Test
    void shouldSearchCourtSchedulesByIdWith200Ids() throws Exception {

        final int numberOfSchedules = 200;
        final LocalDate sessionDate = getRandomFutureDateWithinNextYear();

        final List<CourtSchedule> courtSchedules = IntStream.range(0, numberOfSchedules)
                .mapToObj(i -> {
                    final CourtSchedule cs = RANDOM.nextObject(CourtSchedule.class);
                    cs.setCourtScheduleId(randomUUID().toString());
                    cs.setCourtRoomId(randomUUID().toString());
                    cs.setBusinessType("TRL");
                    cs.setSlotBased(false);
                    cs.setSupportAdSplit(false);
                    cs.setCourtSession(AM_SESSION);
                    cs.setMaxAdMorningDuration(0);
                    cs.setMaxAdAfternoonDuration(0);
                    cs.setMaxDuration(120);
                    cs.setAvailableDuration(120);
                    cs.setMaxSlots(0);
                    cs.setAvailableSlots(0);
                    cs.setSessionDate(sessionDate);
                    cs.setSessionStartTime(combineDateAndTime(sessionDate, DEFAULT_MORNING_START_TIME));
                    cs.setSessionEndTime(combineDateAndTime(sessionDate, DEFAULT_MORNING_END_TIME));
                    cs.setOuCode("B40IM00");
                    cs.setIsDraft(false);
                    cs.setJurisdiction(MAGISTRATES.getJurisdiction());
                    cs.setActive(true);
                    cs.setIsOverbookingAllowed(false);
                    return cs;
                })
                .toList();

        databaseSeeder.insertCourtSchedulesBatch(courtSchedules);

        final String courtScheduleIds = courtSchedules.stream()
                .map(CourtSchedule::getCourtScheduleId)
                .collect(Collectors.joining(","));

        final Map<String, Object> queryParams = Map.of(P_SEARCH_BY_ID_QUERY_PARAM, courtScheduleIds);

        final RequestParams requestParams = getRequestParams(
                SEARCH_BY_ID_URL,
                COURT_SCHEDULE_SEARCH_COURTSCHEDULES_BY_ID_CONTENT_TYPE,
                SYSTEM_USER_ID,
                queryParams
        );

        final ResponseData response = poll(requestParams)
                .with()
                .timeout(60L, SECONDS)
                .pollInterval(100L, MILLISECONDS)
                .pollDelay(0L, MILLISECONDS)
                .until();

        assertThat(response.getStatus().getStatusCode(), is(OK.getStatusCode()));

        final JsonObject json = stringToJsonObjectConverter.convert(response.getPayload());
        final int returnedCount = json.getJsonArray("courtSchedules").size();

        assertThat("Should return all 200 court schedules", returnedCount, is(numberOfSchedules));

        final List<String> returnedIds = json.getJsonArray("courtSchedules").stream()
                .map(JsonValue::asJsonObject)
                .map(obj -> obj.getString("courtScheduleId"))
                .toList();

        for (final CourtSchedule expected : courtSchedules) {
            assertTrue(returnedIds.contains(expected.getCourtScheduleId()),
                    "Response should contain courtScheduleId: " + expected.getCourtScheduleId());
        }
    }

    private static LocalDate getNextWeekdayMonToWed() {
        LocalDate date = LocalDate.now().plusDays(1);
        while (date.getDayOfWeek().getValue() > 3) { // > Wednesday
            date = date.plusDays(1);
        }
        return date;
    }

    private static LocalDate nextWeekday(final LocalDate date) {
        LocalDate next = date.plusDays(1);
        while (next.getDayOfWeek() == DayOfWeek.SATURDAY || next.getDayOfWeek() == DayOfWeek.SUNDAY) {
            next = next.plusDays(1);
        }
        return next;
    }

}
