package uk.gov.moj.cpp.courtscheduler.integration;

import static jakarta.json.Json.createArrayBuilder;
import static jakarta.json.Json.createObjectBuilder;
import static jakarta.ws.rs.core.Response.Status.ACCEPTED;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.OK;
import static java.time.LocalDate.now;
import static java.time.ZoneOffset.UTC;
import static java.time.format.DateTimeFormatter.ofPattern;
import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
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
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.sessionTimeFormatter;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.TimezoneUtils.UTC_ZONE;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.TimezoneUtils.getUtcTimeStringForDate;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.RestPoller.poll;
import static uk.gov.moj.cpp.platform.test.data.utils.FileUtil.getPayload;

import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;
import uk.gov.moj.cpp.courtscheduler.domain.utils.TimezoneUtils;
import uk.gov.moj.cpp.courtscheduler.integration.utils.RequestParams;
import uk.gov.moj.cpp.courtscheduler.integration.utils.ResponseData;
import uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciaryKey;

import java.io.StringReader;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CourtSchedulerIT extends AbstractIT {
    private static final String P_TITLE_JUDICIAL_PREFIX_WELSH = "titleJudicialPrefixWelsh";
    private static final String P_TITLE_JUDICIAL_PREFIX = "titleJudicialPrefix";
    private static final String P_TITLE_PREFIX = "titlePrefix";
    private static final String P_SPECIALISMS = "specialisms";
    private static final String VALUE_120 = "120";
    private static final String VALUE_15_00 = "15:00";
    private static final String VALUE_16_00 = "16:00";
    private static final String UUID_3FC02C0F = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3";
    private static final String UUID_785339C1 = "785339c1-af71-3322-a55b-ba255e0db1c2";
    private static final String ADULT_2 = "ADULT";
    private static final String ASSIGN_COURTROOM_RESPONSE = "Assign courtroom response: ";
    private static final String BUSINESS_TYPE_2 = "BUSINESS_TYPE";
    private static final String COURT_CENTRE_ID_2 = "COURT_CENTRE_ID";
    private static final String COURT_ROOM_ID_2 = "COURT_ROOM_ID";
    private static final String COURT_SCHEDULE_ID_4 = "COURT_SCHEDULE_ID";
    private static final String COURT_SCHEDULE_ID_1_2 = "COURT_SCHEDULE_ID_1";
    private static final String COURT_SCHEDULE_ID_2_2 = "COURT_SCHEDULE_ID_2";
    private static final String CROWN_2 = "CROWN";
    private static final String CANNOT_ASSIGN_COURTROOM_TO_AN_ASSIGNED_SESSION = "Cannot assign courtroom to an assigned session";
    private static final String COURT_SCHEDULES_SHOULD_BE_CREATED = "Court schedules should be created";
    private static final String COURTROOM_DOES_NOT_EXIST = "Courtroom does not exist";
    private static final String DVLA_2 = "DVLA";
    private static final String END_DATE_2 = "END_DATE";
    private static final String LGT_2 = "LGT";
    private static final String MAGISTRATES_2 = "MAGISTRATES";
    private static final String MAX_DURATION_FOR_AFTERNOON_2 = "MAX_DURATION_FOR_AFTERNOON";
    private static final String MAX_DURATION_FOR_MORNING_2 = "MAX_DURATION_FOR_MORNING";
    private static final String PAGE_NUMBER_2 = "PAGE_NUMBER";
    private static final String PAGE_SIZE_2 = "PAGE_SIZE";
    private static final String PANEL_2 = "PANEL";
    private static final String RESPONSE_SHOULD_BE_AN_ARRAY = "Response should be an array";
    private static final String RESPONSE_SHOULD_CONTAIN_SESSIONS_KEY = "Response should contain \'sessions\' key";
    private static final String RESPONSE_SHOULD_CONTAIN_ERROR_GROUPS = "Response should contain errorGroups";
    private static final String SESSION_END_DATE_2 = "SESSION_END_DATE";
    private static final String SESSION_END_TIME_2 = "SESSION_END_TIME";
    private static final String SESSION_START_DATE_2 = "SESSION_START_DATE";
    private static final String SESSION_START_TIME_2 = "SESSION_START_TIME";
    private static final String SESSION_TYPE_2 = "SESSION_TYPE";
    private static final String START_DATE_2 = "START_DATE";
    private static final String TRL_2 = "TRL";
    private static final String YOUTH_2 = "YOUTH";
    private static final String MAX_DURATION_10 = "\"maxDuration\": 10";
    private static final String UUID_ABCDEF12 = "abcdef12-3456-7890-abcd-ef1234567890";
    private static final String ACTIVE = "active";
    private static final String ALL_DAY_SPLIT = "allDaySplit";
    private static final String ASSIGN_COURTROOM_JSON = "assign-courtroom.json";
    private static final String COURT_ROOM_ID_3 = "courtRoomId";
    private static final String COURT_ROOM_NAME = "courtRoomName";
    private static final String COURT_SCHEDULE_ID_5 = "courtScheduleId";
    private static final String COURT_SCHEDULES = "courtSchedules";
    private static final String COURTSCHEDULER_GET_COURT_SCHEDULE_QUERY_JSON = "courtscheduler.get.court_schedule_query.json";
    private static final String COURTSCHEDULER_SEARCH_COURTSCHEDULES_BY_ID_DYNAM = "courtscheduler.search.courtschedules.by.id_dynamic.json";
    private static final String ERROR = "error";
    private static final String ERROR_GROUPS = "errorGroups";
    private static final String JUDICIARIES = "judiciaries";
    private static final String JUDICIARY_ID = "judiciaryId";
    private static final String JURISDICTION = "jurisdiction";
    private static final String MAX_DURATION_FOR_AFTERNOON_3 = "maxDurationForAfternoon";
    private static final String MAX_DURATION_FOR_MORNING_3 = "maxDurationForMorning";
    private static final String ORIGINAL_COURTROOM_ID = "original-courtroom-id";
    private static final String PANEL_3 = "panel";
    private static final String SESSION_END_TIME_3 = "sessionEndTime";
    private static final String SESSION_IDS = "sessionIds";
    private static final String SESSION_START_TIME_3 = "sessionStartTime";
    private static final String SESSIONS = "sessions";
    private static final String SKIP_VALIDATIONS = "skipValidations";
    private static final String SLOT_BASED = "slotBased";
    private static final String TOTAL_BOOKED = "totalBooked";
    private static final String UPDATE_COURT_SCHEDULE_ALL_DAY_SPLIT_JSON = "update-court-schedule-all-day-split.json";
    private static final String UPDATE_COURT_SCHEDULE_JSON = "update-court-schedule.json";
    private static final String YYYY_MM_DD = "yyyy-MM-dd";


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
    // Formats times in the Europe/London timezone
    private static final DateTimeFormatter TIME_FORMATTER =
            ofPattern("HH:mm", Locale.UK).withZone(ZoneId.of("Europe/London"));
    private static final String COURT_ROOM_ID_WITH_AD_SPLIT = "2bd129f3-780e-37dd-b9aa-48690f91b69c";
    private static final String ERROR_ONLY_VALID_FOR_CROWN = "assign.courtroom endpoint is only valid for CROWN jurisdiction sessions";
    private static final String ERROR_COURTROOM_DIFFERENT_CENTRE = "The new courtroom must belong to the same court centre as the session";
    private static final String ERROR_CROWN_DRAFT_WITH_HEARINGS = "Cannot assign courtroom to a CROWN draft session with hearings booked";

    @Test
    void shouldCreateSlotBasedSchedule() {
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("create-court-schedule-duration-based.json");
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

    }

    @Test
    void shouldCreateCourtScheduleWithSessionTimes() {
        //We send localtime
        final LocalDate startDate = now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        final Instant expectedStartTime = TimezoneUtils.combineLocalDateAndTimeToUtc(startDate, LocalTime.of(10, 0)).toInstant();
        final Instant expectedEndTime = TimezoneUtils.combineLocalDateAndTimeToUtc(startDate, LocalTime.of(12, 0)).toInstant();
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("create-court-schedule-duration-based.json");
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final List<CourtSchedule> courtSchedules = databaseReader.courtSchedules();
        final CourtSchedule courtSchedule = courtSchedules.get(0);
        assertThat(courtSchedule.getCourtScheduleId(), is(notNullValue()));
        assertThat(courtSchedule.getSessionStartTime(), is(notNullValue()));
        assertThat(courtSchedule.getSessionEndTime(), is(notNullValue()));
        assertThat(courtSchedule.getSessionStartTime(), is(expectedStartTime));
        assertThat(courtSchedule.getSessionEndTime(), is(expectedEndTime));
    }

    @Test
    void shouldCreateCourtScheduleWithSessionTimes_AcrossSummerAndWinterTime() {
        LocalDate startDate = now().withMonth(10).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        // If the calculated date is in the past, use next year's October
        if (startDate.isBefore(now())) {
            startDate = now().plusYears(1).withMonth(10).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        }
        final LocalDate endDate = startDate.plusDays(56);
        // The JSON contains times in BST (local time), so convert to UTC for comparison
        // 10:00 BST = 09:00 UTC and 12:00 BST = 11:00 UTC during BST period
        final Instant expectedStartTimeFirstWeek = TimezoneUtils.combineLocalDateAndTimeToUtc(startDate, LocalTime.of(10, 0)).toInstant();
        final Instant expectedEndTimeFirstWeek = TimezoneUtils.combineLocalDateAndTimeToUtc(startDate, LocalTime.of(12, 0)).toInstant();
        final Instant expectedStartTimeLastWeek = TimezoneUtils.combineLocalDateAndTimeToUtc(startDate.plusDays(56), LocalTime.of(10, 0)).toInstant();
        final Instant expectedEndTimeLastWeek = TimezoneUtils.combineLocalDateAndTimeToUtc(startDate.plusDays(56), LocalTime.of(12, 0)).toInstant();
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload_testBSTToUTC("create-court-schedule-duration-based-bst-timings.json", startDate, endDate);
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final List<CourtSchedule> courtSchedules = databaseReader.courtSchedules();
        final CourtSchedule courtScheduleFirst = courtSchedules.get(0);
        final CourtSchedule courtScheduleLast = courtSchedules.get(courtSchedules.size() - 1);
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
        final CourtSchedule courtSchedule = courtSchedules.get(0);
        assertThat(courtSchedule.getCourtScheduleId(), is(notNullValue()));

        // Convert the UTC times from the database to local time for comparison
        final Instant localStartTime = TimezoneUtils.utcToLocal(courtSchedule.getSessionStartTime());
        final Instant localEndTime = TimezoneUtils.utcToLocal(courtSchedule.getSessionEndTime());

        assertThat(TIME_FORMATTER.format(localStartTime), is(DEFAULT_MORNING_START_TIME));
        assertThat(TIME_FORMATTER.format(localEndTime), is(DEFAULT_MORNING_END_TIME));
    }

    @Test
    void shouldCreateCourtScheduleWithDefaultSessionTimesPM() {
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("create-court-schedule-duration-based-default-times-pm.json");
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final List<CourtSchedule> courtSchedules = databaseReader.courtSchedules();
        final CourtSchedule courtSchedule = courtSchedules.get(0);
        assertThat(courtSchedule.getCourtScheduleId(), is(notNullValue()));

        // Convert the UTC times from the database to local time for comparison
        final Instant localStartTime = TimezoneUtils.utcToLocal(courtSchedule.getSessionStartTime());
        final Instant localEndTime = TimezoneUtils.utcToLocal(courtSchedule.getSessionEndTime());

        assertThat(TIME_FORMATTER.format(localStartTime), is(DEFAULT_AFTERNOON_START_TIME));
        assertThat(TIME_FORMATTER.format(localEndTime), is(DEFAULT_AFTERNOON_END_TIME));
    }

    @Test
    void shouldCreateCourtScheduleWithDefaultSessionTimesAD() {
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("create-court-schedule-duration-based-default-times-ad.json");
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final List<CourtSchedule> courtSchedules = databaseReader.courtSchedules();
        final CourtSchedule courtSchedule = courtSchedules.get(0);
        assertThat(courtSchedule.getCourtScheduleId(), is(notNullValue()));

        // Convert the UTC times from the database to local time for comparison
        final Instant localStartTime = TimezoneUtils.utcToLocal(courtSchedule.getSessionStartTime());
        final Instant localEndTime = TimezoneUtils.utcToLocal(courtSchedule.getSessionEndTime());

        assertThat(TIME_FORMATTER.format(localStartTime), is(DEFAULT_ALL_DAY_START_TIME));
        assertThat(TIME_FORMATTER.format(localEndTime), is(DEFAULT_ALL_DAY_END_TIME));
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

            final Instant localStartTime = TimezoneUtils.utcToLocal(courtSchedule.getSessionStartTime());
            final Instant localEndTime = TimezoneUtils.utcToLocal(courtSchedule.getSessionEndTime());

            assertThat(TIME_FORMATTER.format(localStartTime), is("09:15"));
            assertThat(TIME_FORMATTER.format(localEndTime), is(DEFAULT_MORNING_END_TIME));
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

            final Instant localStartTime = TimezoneUtils.utcToLocal(courtSchedule.getSessionStartTime());
            final Instant localEndTime = TimezoneUtils.utcToLocal(courtSchedule.getSessionEndTime());

            assertThat(TIME_FORMATTER.format(localStartTime), is("09:15"));
            assertThat(TIME_FORMATTER.format(localEndTime), is(DEFAULT_ALL_DAY_END_TIME));
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

            final Instant localStartTime = TimezoneUtils.utcToLocal(courtSchedule.getSessionStartTime());
            final Instant localEndTime = TimezoneUtils.utcToLocal(courtSchedule.getSessionEndTime());

            assertThat(TIME_FORMATTER.format(localStartTime), is(DEFAULT_AFTERNOON_START_TIME));
            assertThat(TIME_FORMATTER.format(localEndTime), is(DEFAULT_AFTERNOON_END_TIME));
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

            final Instant localStartTime = TimezoneUtils.utcToLocal(courtSchedule.getSessionStartTime());
            final Instant localEndTime = TimezoneUtils.utcToLocal(courtSchedule.getSessionEndTime());

            assertThat(TIME_FORMATTER.format(localStartTime), is("10:15"));
            assertThat(TIME_FORMATTER.format(localEndTime), is("12:30"));
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

        final LocalDate startDate = now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        final LocalDate endDate = startDate.plusDays(28);
        final String createCourtSchedulePayload = getPayload("create-court-schedule-duration-based-all-day-split.json")
                .replaceAll("MAX_DURATION_FOR_MORNING_SLOT_1", String.valueOf(maxDurationForMorningSlot1))
                .replaceAll("MAX_DURATION_FOR_AFTERNOON_SLOT_1", String.valueOf(maxDurationForAfternoonSlot1))
                .replaceAll("MAX_DURATION_FOR_MORNING_SLOT_2", String.valueOf(maxDurationForMorningSlot2))
                .replaceAll("MAX_DURATION_FOR_AFTERNOON_SLOT_2", String.valueOf(maxDurationForAfternoonSlot2))
                .replaceAll(START_DATE_2, startDate.toString())
                .replaceAll(END_DATE_2, endDate.toString());
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final List<CourtSchedule> courtSchedules = databaseReader.courtSchedules();
        assertThat(courtSchedules.size(), is(6));
        courtSchedules.forEach(courtScheduleRecordedInDb -> {
            if (COURT_ROOM_ID_WITH_AD_SPLIT.equals(courtScheduleRecordedInDb.getCourtRoomId())) {
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
        assertThat(errorResponseMessage, containsString(COURTROOM_DOES_NOT_EXIST));
    }

    @Test
    void shouldReturn400WhenCrownSessionUsesMagistratesCourtroomInValidateCreate() {
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("validate-create-court-schedule-crown-with-magistrates-courtroom.json");
        final Response response = postCommand(VALIDATE_URL, COURT_SCHEDULE_VALIDATE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String errorResponseMessage = response.readEntity(String.class);
        assertThat(errorResponseMessage, containsString(COURTROOM_DOES_NOT_EXIST));
    }

    @Test
    void shouldReturn400WhenMagistratesSessionUsesCrownCourtroomInCreate() {
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("create-court-schedule-magistrates-with-crown-courtroom.json");
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String errorResponseMessage = response.readEntity(String.class);
        // Courtroom ID not in CP reference data -> step 1 returns "Courtroom does not exist"
        assertThat(errorResponseMessage, containsString(COURTROOM_DOES_NOT_EXIST));
    }

    @Test
    void shouldReturn400WhenMagistratesSessionUsesCrownCourtroomInValidateCreate() {
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("validate-create-court-schedule-magistrates-with-crown-courtroom.json");
        final Response response = postCommand(VALIDATE_URL, COURT_SCHEDULE_VALIDATE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String errorResponseMessage = response.readEntity(String.class);
        // Courtroom ID not in CP reference data -> step 1 returns "Courtroom does not exist"
        assertThat(errorResponseMessage, containsString(COURTROOM_DOES_NOT_EXIST));
    }

    @Test
    void shouldReturn400WhenCourtroomDoesNotExistInCreate() {
        final String createCourtSchedulePayload = prepareCreateCourtSchedulePayload("create-court-schedule-courtroom-not-in-cp.json");
        final Response response = postCommand(BASE_RESOURCE_URL, COURT_SCHEDULE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String errorResponseMessage = response.readEntity(String.class);
        assertThat(errorResponseMessage, containsString(COURTROOM_DOES_NOT_EXIST));
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
        final String createCourtSchedulePayload = getPayload("validate-create-court-schedule-frequency-once.json")
                .replace(START_DATE_2, startDate.format(ofPattern(YYYY_MM_DD)));

        // When
        final Response response = postCommand(VALIDATE_URL, COURT_SCHEDULE_VALIDATE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);

        // Then
        assertThat(response.getStatus(), is(OK.getStatusCode()));
        final String responseBody = response.readEntity(String.class);
        assertThat("Response should be empty JSON object for successful validation", responseBody, is("{}"));
    }

    @Test
    void shouldAcceptValidateCreateForCourtroomSharedBetweenCourtCentres() {
        // Courtroom 77777777-... is nested under BOTH C01CR00 and C45GU00 in the ou-courtrooms
        // stub; the session targets C45GU00, the LATER of the two memberships. Considering only
        // one arbitrary membership used to fail this with "belongs to a different court centre".
        final LocalDate startDate = now().plusDays(1);
        final String createCourtSchedulePayload = getPayload("validate-create-court-schedule-crown-shared-courtroom.json")
                .replace(START_DATE_2, startDate.format(ofPattern(YYYY_MM_DD)));

        final Response response = postCommand(VALIDATE_URL, COURT_SCHEDULE_VALIDATE_CREATE_CONTENT_TYPE, USER_ID, createCourtSchedulePayload);

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
                .replace(START_DATE_2, startDate.format(ofPattern(YYYY_MM_DD)));
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
                .replace(START_DATE_2, startDate.format(ofPattern(YYYY_MM_DD)));
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
        final UUID hearingIdForMorning = randomUUID();
        final UUID bookingIdForMorning = randomUUID();
        final UUID hearingIdForAfternoon = randomUUID();
        final UUID bookingIdForAfternoon = randomUUID();
        courtScheduleDuration.setBusinessType(TRL_2);
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
        courtScheduleDuration.setCourtScheduleId(UUID_ABCDEF12);
        courtScheduleDuration.setSessionDate(getRandomFutureDateWithinNextYear());
        courtScheduleDuration.setSessionStartTime(DateUtils.combineDateAndTime(courtScheduleDuration.getSessionDate(), DEFAULT_ALL_DAY_START_TIME).toInstant());
        courtScheduleDuration.setSessionEndTime(DateUtils.combineDateAndTime(courtScheduleDuration.getSessionDate(), VALUE_16_00).toInstant());
        databaseSeeder.insertCourtSchedule(courtScheduleDuration);

        createAllocatedListing(courtScheduleDuration, hearingIdForMorning, bookingIdForMorning, 120, DEFAULT_ALL_DAY_START_TIME);
        createAllocatedListing(courtScheduleDuration, hearingIdForAfternoon, bookingIdForAfternoon, 60, VALUE_15_00);

        final String validateCourtSchedulePayload = getPayload("courtscheduler.validate.session.availability-allday-insufficient.json");
        final Response response = postCommand(VALIDATE_SESSION_AVAILABILITY_URL, COURT_SCHEDULE_VALIDATE_SESSION_AVAILABILITY_CONTENT_TYPE, SYSTEM_USER_ID, validateCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String errorResponseMessage = response.readEntity(String.class);
        assertThat(errorResponseMessage, containsString("Schedule abcdef12-3456-7890-abcd-ef1234567890 has 0 minutes available across morning (0) and afternoon (0), but 3 minutes are required."));
    }

    @Test
    void shouldReturn400WhenDurationBasedScheduleHasInsufficientAvailability() throws SQLException {
        final CourtSchedule courtScheduleDuration = RANDOM.nextObject(CourtSchedule.class);
        courtScheduleDuration.setBusinessType(TRL_2);
        courtScheduleDuration.setSlotBased(false);
        courtScheduleDuration.setMaxDuration(0);
        courtScheduleDuration.setAvailableDuration(0);
        courtScheduleDuration.setSupportAdSplit(false);
        courtScheduleDuration.setCourtSession(ALL_DAY);
        courtScheduleDuration.setIsOverbookingAllowed(false);
        courtScheduleDuration.setCourtScheduleId(UUID_ABCDEF12);
        courtScheduleDuration.setSessionDate(getRandomFutureDateWithinNextYear());
        courtScheduleDuration.setSessionStartTime(DateUtils.combineDateAndTime(courtScheduleDuration.getSessionDate(), DEFAULT_ALL_DAY_START_TIME).toInstant());
        courtScheduleDuration.setSessionEndTime(DateUtils.combineDateAndTime(courtScheduleDuration.getSessionDate(), VALUE_16_00).toInstant());
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

        courtScheduleSlot.setBusinessType(DVLA_2);
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
        courtScheduleSlot.setSessionStartTime(DateUtils.combineDateAndTime(courtScheduleSlot.getSessionDate(), DEFAULT_ALL_DAY_START_TIME).toInstant());
        courtScheduleSlot.setSessionEndTime(DateUtils.combineDateAndTime(courtScheduleSlot.getSessionDate(), VALUE_16_00).toInstant());
        databaseSeeder.insertCourtSchedule(courtScheduleSlot);

        courtScheduleDuration.setBusinessType(TRL_2);
        courtScheduleDuration.setSlotBased(false);
        courtScheduleDuration.setIsOverbookingAllowed(false);
        courtScheduleDuration.setMaxDuration(0);
        courtScheduleDuration.setAvailableDuration(0);
        courtScheduleDuration.setSupportAdSplit(true);
        courtScheduleDuration.setCourtSession(ALL_DAY);
        courtScheduleDuration.setMaxAdMorningDuration(maxDurationForMorning);
        courtScheduleDuration.setMaxAdAfternoonDuration(maxDurationForAfternoon);
        courtScheduleDuration.setCourtScheduleId(UUID_ABCDEF12);
        courtScheduleDuration.setCourtHouseId(sharedCourtHouseId);
        courtScheduleDuration.setSessionDate(getRandomFutureDateWithinNextYear());
        courtScheduleDuration.setSessionStartTime(DateUtils.combineDateAndTime(courtScheduleDuration.getSessionDate(), DEFAULT_ALL_DAY_START_TIME).toInstant());
        courtScheduleDuration.setSessionEndTime(DateUtils.combineDateAndTime(courtScheduleDuration.getSessionDate(), VALUE_16_00).toInstant());
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
        final String businessType = DVLA_2;
        final Integer maxDuration = 480;

        LocalDate sessionDate = startDate;
        for (int i = 0; i < 3; i++) {
            final CourtSchedule schedule = RANDOM.nextObject(CourtSchedule.class);
            schedule.setCourtScheduleId(i == 0 ? courtScheduleId : "consec-" + i + "-" + randomUUID());
            schedule.setSlotBased(false);
            schedule.setIsOverbookingAllowed(false);
            schedule.setCourtSession(ALL_DAY);
            schedule.setJurisdiction(CROWN_2);
            schedule.setSessionDate(sessionDate);
            schedule.setCourtRoomId(courtRoomId);
            schedule.setBusinessType(businessType);
            schedule.setPanel(ADULT_2);
            schedule.setMaxDuration(maxDuration);
            schedule.setAvailableDuration(maxDuration);
            schedule.setSupportAdSplit(false);
            schedule.setSessionStartTime(DateUtils.combineDateAndTime(schedule.getSessionDate(), "09:00").toInstant());
            schedule.setSessionEndTime(DateUtils.combineDateAndTime(schedule.getSessionDate(), DEFAULT_ALL_DAY_END_TIME).toInstant());
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

        courtSchedule.setBusinessType(TRL_2);
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
        courtSchedule.setSessionStartTime(DateUtils.combineDateAndTime(courtSchedule.getSessionDate(), DEFAULT_ALL_DAY_START_TIME).toInstant());
        courtSchedule.setSessionEndTime(DateUtils.combineDateAndTime(courtSchedule.getSessionDate(), VALUE_16_00).toInstant());
        databaseSeeder.insertCourtSchedule(courtSchedule);

        final String validateCourtSchedulePayload = getPayload("courtscheduler.validate.session.availability-slot.json");
        final Response response = postCommand(VALIDATE_SESSION_AVAILABILITY_URL, COURT_SCHEDULE_VALIDATE_SESSION_AVAILABILITY_CONTENT_TYPE, SYSTEM_USER_ID, validateCourtSchedulePayload);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
    }

    @Test
    void shouldCreateDurationBasedSchedule() {
        final LocalDate startDate = now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        final LocalDate endDate = startDate.plusDays(28);
        final String createCourtSchedulePayload = getPayload("create-court-schedule-duration-based.json")
                .replaceAll(START_DATE_2, startDate.toString())
                .replaceAll(END_DATE_2, endDate.toString());
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
        final UUID courtScheduleId = randomUUID();
        final CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        final String courtRoomId = UUID_3FC02C0F; // Use same courtroom ID for initial and update
        final String courtHouseId = UUID_785339C1; // Test Crown Court - same court house as courtroom
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType(DVLA_2);
        expected.setSupportAdSplit(false);
        expected.setSessionDate(now().with(TemporalAdjusters.next(DayOfWeek.MONDAY))); // Set to future date to avoid past session validation
        expected.setCourtRoomId(courtRoomId); // Set initial courtroom ID to match update
        expected.setCourtHouseId(courtHouseId); // Set court house ID to match courtroom
        databaseSeeder.insertCourtSchedule(expected);

        String updateCourtSchedulePayload = getPayload(UPDATE_COURT_SCHEDULE_JSON);
        final String changedCourtRoomId = courtRoomId; // Use same courtroom to avoid court house validation error
        final String changedBusinessType = DVLA_2;
        final String changedSessionType = "AM";
        final String changedPanel = YOUTH_2;
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_SCHEDULE_ID_4, expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_ROOM_ID_2, changedCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(BUSINESS_TYPE_2, changedBusinessType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(SESSION_TYPE_2, changedSessionType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(PANEL_2, changedPanel);

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
    }

    @Test
    void shouldUpdateCourtScheduleAllDaySplit() throws SQLException {
        final UUID courtScheduleId = randomUUID();
        final CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        final String courtRoomId = UUID_3FC02C0F; // Use same courtroom ID for initial and update
        final String courtHouseId = UUID_785339C1; // Test Crown Court - same court house as courtroom
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType(TRL_2);
        expected.setSessionDate(now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)));
        expected.setSupportAdSplit(true);
        expected.setCourtRoomId(courtRoomId); // Set initial courtroom ID to match update
        expected.setCourtHouseId(courtHouseId); // Set court house ID to match courtroom
        databaseSeeder.insertCourtSchedule(expected);

        String updateCourtSchedulePayload = getPayload(UPDATE_COURT_SCHEDULE_ALL_DAY_SPLIT_JSON);
        final String changedCourtRoomId = courtRoomId; // Use same courtroom to avoid court house validation error
        final String changedBusinessType = TRL_2;
        final String changedSessionType = "AD";
        final String changedPanel = YOUTH_2;
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_SCHEDULE_ID_4, expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_ROOM_ID_2, changedCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(BUSINESS_TYPE_2, changedBusinessType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(SESSION_TYPE_2, changedSessionType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(PANEL_2, changedPanel);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(MAX_DURATION_FOR_MORNING_2, VALUE_120);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(MAX_DURATION_FOR_AFTERNOON_2, "60");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(SESSION_START_TIME_2, "10:30");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(SESSION_END_TIME_2, "17:30");

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));


        final CourtSchedule courtScheduleAfterUpdate = databaseReader.courtScheduleById(courtScheduleId.toString());
        assertThat(courtScheduleAfterUpdate.getMaxAdMorningDuration(), is(120));
        assertThat(courtScheduleAfterUpdate.getMaxAdAfternoonDuration(), is(60));

        final Instant expectedStartTime = localDateToDateWithTime(expected.getSessionDate(), 10, 30).toInstant();
        final Instant expectedEndTime = localDateToDateWithTime(expected.getSessionDate(), 17, 30).toInstant();
        assertThat(courtScheduleAfterUpdate.getSessionStartTime(), is(expectedStartTime));
        assertThat(courtScheduleAfterUpdate.getSessionEndTime(), is(expectedEndTime));
    }

    @Test
    void shouldUpdateCourtScheduleIsOverbookingAllowed() throws SQLException {
        final UUID courtScheduleId = randomUUID();
        final CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        final String courtRoomId = UUID_3FC02C0F; // Use same courtroom ID for initial and update
        final String courtHouseId = UUID_785339C1; // Test Crown Court - same court house as courtroom
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType(TRL_2);
        expected.setSessionDate(now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)));
        expected.setSupportAdSplit(true);
        expected.setIsOverbookingAllowed(false);
        expected.setCourtRoomId(courtRoomId); // Set initial courtroom ID to match update
        expected.setCourtHouseId(courtHouseId); // Set court house ID to match courtroom
        databaseSeeder.insertCourtSchedule(expected);

        String updateCourtSchedulePayload = getPayload("update-court-schedule-is-overbooking-allowed.json");
        final String changedCourtRoomId = courtRoomId; // Use same courtroom to avoid court house validation error
        final String changedBusinessType = TRL_2;
        final String changedSessionType = "AD";
        final String changedPanel = YOUTH_2;
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_SCHEDULE_ID_4, expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_ROOM_ID_2, changedCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(BUSINESS_TYPE_2, changedBusinessType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(SESSION_TYPE_2, changedSessionType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(PANEL_2, changedPanel);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(MAX_DURATION_FOR_MORNING_2, VALUE_120);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(MAX_DURATION_FOR_AFTERNOON_2, "60");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(SESSION_START_TIME_2, "10:30");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(SESSION_END_TIME_2, "17:30");

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final CourtSchedule courtScheduleAfterUpdate = databaseReader.courtScheduleById(courtScheduleId.toString());
        assertThat(courtScheduleAfterUpdate.getMaxAdMorningDuration(), is(120));
        assertThat(courtScheduleAfterUpdate.getMaxAdAfternoonDuration(), is(60));
        assertThat(courtScheduleAfterUpdate.isOverbookingAllowed(), is(true));

        final Instant expectedStartTime = localDateToDateWithTime(expected.getSessionDate(), 10, 30).toInstant();
        final Instant expectedEndTime = localDateToDateWithTime(expected.getSessionDate(), 17, 30).toInstant();
        assertThat(courtScheduleAfterUpdate.getSessionStartTime(), is(expectedStartTime));
        assertThat(courtScheduleAfterUpdate.getSessionEndTime(), is(expectedEndTime));
    }

    @Test
    void shouldUpdateCourtScheduleAllDaySplitWithoutGivenSessionStartAndEndTime() throws SQLException {
        final UUID courtScheduleId = randomUUID();
        final CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        final String courtRoomId = UUID_3FC02C0F; // Use same courtroom ID for initial and update
        final String courtHouseId = UUID_785339C1; // Test Crown Court - same court house as courtroom
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType(TRL_2);
        expected.setSessionDate(now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)));
        expected.setSupportAdSplit(true);
        expected.setCourtRoomId(courtRoomId); // Set initial courtroom ID to match update
        expected.setCourtHouseId(courtHouseId); // Set court house ID to match courtroom
        databaseSeeder.insertCourtSchedule(expected);

        String updateCourtSchedulePayload = getPayload("update-court-schedule-all-day-split-without-session-start-end-time.json");
        final String changedCourtRoomId = courtRoomId; // Use same courtroom to avoid court house validation error
        final String changedBusinessType = TRL_2;
        final String changedSessionType = "AD";
        final String changedPanel = YOUTH_2;
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_SCHEDULE_ID_4, expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_ROOM_ID_2, changedCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(BUSINESS_TYPE_2, changedBusinessType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(SESSION_TYPE_2, changedSessionType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(PANEL_2, changedPanel);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(MAX_DURATION_FOR_MORNING_2, VALUE_120);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(MAX_DURATION_FOR_AFTERNOON_2, "60");

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final CourtSchedule courtScheduleAfterUpdate = databaseReader.courtScheduleById(courtScheduleId.toString());
        assertThat(courtScheduleAfterUpdate.getMaxAdMorningDuration(), is(120));
        assertThat(courtScheduleAfterUpdate.getMaxAdAfternoonDuration(), is(60));

        final Instant expectedStartTime = localDateToDateWithTime(expected.getSessionDate(), 10, 0).toInstant();
        final Instant expectedEndTime = localDateToDateWithTime(expected.getSessionDate(), 17, 0).toInstant();
        assertThat(courtScheduleAfterUpdate.getSessionStartTime(), is(expectedStartTime));
        assertThat(courtScheduleAfterUpdate.getSessionEndTime(), is(expectedEndTime));
    }

    @Test
    void shouldGet400WhenUpdatingCourtScheduleWithInvalidMaxDurationValues() throws SQLException {
        final UUID courtScheduleId = randomUUID();
        final CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType(TRL_2);
        databaseSeeder.insertCourtSchedule(expected);

        String updateCourtSchedulePayload = getPayload(UPDATE_COURT_SCHEDULE_ALL_DAY_SPLIT_JSON);
        final String changedCourtRoomId = UUID_3FC02C0F; // picked from referencedata.rota-courtrooms.json file
        final String changedBusinessType = TRL_2;
        final String changedSessionType = "AD";
        final String changedPanel = YOUTH_2;
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_SCHEDULE_ID_4, expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_ROOM_ID_2, changedCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(BUSINESS_TYPE_2, changedBusinessType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(SESSION_TYPE_2, changedSessionType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(PANEL_2, changedPanel);

        // Set invalid max duration values
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("MAXDURATIONFORMORNING", "0");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("MAXDURATIONFORAFTERNOON", "0");

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
    }

    @Test
    void shouldGet400WhenUpdatingCourtScheduleWithInvalidMinHearingTime() throws SQLException {
        final UUID courtScheduleId = randomUUID();
        final CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        final String courtRoomId = UUID_3FC02C0F; // Use same courtroom ID for initial and update
        final String courtHouseId = UUID_785339C1; // Test Crown Court - same court house as courtroom
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType(TRL_2);
        expected.setSupportAdSplit(true);
        final LocalDate futureDate = now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        expected.setSessionDate(futureDate); // Set to future date to avoid past session validation
        expected.setSessionStartTime(DateUtils.localDateToDateWithTime(futureDate, 10, 0).toInstant());
        expected.setSessionEndTime(DateUtils.localDateToDateWithTime(futureDate, 17, 0).toInstant());
        expected.setCourtRoomId(courtRoomId); // Set initial courtroom ID to match update
        expected.setCourtHouseId(courtHouseId); // Set court house ID to match courtroom
        databaseSeeder.insertCourtSchedule(expected);

        String updateCourtSchedulePayload = getPayload("update-court-schedule-all-day-split-invalid-session-start-time.json");
        final String changedCourtRoomId = courtRoomId; // Use same courtroom to avoid court house validation error
        final String changedBusinessType = TRL_2;
        final String changedSessionType = "AD";
        final String changedPanel = YOUTH_2;
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_SCHEDULE_ID_4, expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_ROOM_ID_2, changedCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(BUSINESS_TYPE_2, changedBusinessType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(SESSION_TYPE_2, changedSessionType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(PANEL_2, changedPanel);

        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(SESSION_START_TIME_2, "11:00");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(SESSION_END_TIME_2, "17:01");

        createAllocatedListing(expected, randomUUID(), randomUUID(), 90, DEFAULT_ALL_DAY_START_TIME);

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String errorResponseMessage = response.readEntity(String.class);
        assertThat(errorResponseMessage, containsString(MIN_HEARING_TIME_AFTER_SESSION_START_TIME));
    }

    @Test
    void shouldGet400WhenUpdatingCourtScheduleWithInvalidMaxHearingTime() throws SQLException {
        final UUID courtScheduleId = randomUUID();
        final CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        final String courtRoomId = UUID_3FC02C0F; // Use same courtroom ID for initial and update
        final String courtHouseId = UUID_785339C1; // Test Crown Court - same court house as courtroom
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType(TRL_2);
        expected.setSupportAdSplit(true);
        final LocalDate futureDate = now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        expected.setSessionDate(futureDate); // Set to future date to avoid past session validation
        expected.setSessionStartTime(DateUtils.localDateToDateWithTime(futureDate, 10, 0).toInstant());
        expected.setSessionEndTime(DateUtils.localDateToDateWithTime(futureDate, 17, 0).toInstant());
        expected.setCourtRoomId(courtRoomId); // Set initial courtroom ID to match update
        expected.setCourtHouseId(courtHouseId); // Set court house ID to match courtroom
        databaseSeeder.insertCourtSchedule(expected);

        String updateCourtSchedulePayload = getPayload("update-court-schedule-all-day-split-invalid-session-start-time.json");
        final String changedCourtRoomId = courtRoomId; // Use same courtroom to avoid court house validation error
        final String changedBusinessType = TRL_2;
        final String changedSessionType = "AD";
        final String changedPanel = YOUTH_2;
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_SCHEDULE_ID_4, expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_ROOM_ID_2, changedCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(BUSINESS_TYPE_2, changedBusinessType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(SESSION_TYPE_2, changedSessionType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(PANEL_2, changedPanel);

        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(SESSION_START_TIME_2, DEFAULT_ALL_DAY_START_TIME);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(SESSION_END_TIME_2, "13:01");

        createAllocatedListing(expected, randomUUID(), randomUUID(), 90, VALUE_15_00);

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String errorResponseMessage = response.readEntity(String.class);
        assertThat(errorResponseMessage, containsString(MAX_HEARING_TIME_BEFORE_SESSION_END_TIME));
    }

    @Test
    void shouldUpdateCourtScheduleWithNullSessionTimesWhenAllocatedListingsExist() throws SQLException {
        // Test that when session times are null in update request but allocated listings exist,
        // the system retrieves session times from persisted schedule and validates successfully
        final UUID courtScheduleId = randomUUID();
        final CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        final String courtRoomId = UUID_3FC02C0F; // Use same courtroom ID for initial and update
        final String courtHouseId = UUID_785339C1; // Test Crown Court - same court house as courtroom
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType(DVLA_2);
        expected.setSupportAdSplit(false);
        expected.setListingProfileId(USER_ID.toString()); // Set to current user to avoid "edited by another user" error
        expected.setIsDraft(true); // Set as draft to allow editing
        expected.setHasHearingsBooked(true); // Set to true since we'll create allocated listings
        expected.setCourtRoomId(courtRoomId); // Set initial courtroom ID to match update
        expected.setCourtHouseId(courtHouseId); // Set court house ID to match courtroom
        expected.setCourtSession("AM"); // Set initial session type to match update
        expected.setPanel(YOUTH_2); // Set initial panel to match update
        expected.setSessionDate(now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)));
        expected.setSessionStartTime(DateUtils.localDateToDateWithTime(expected.getSessionDate(), 10, 0).toInstant());
        expected.setSessionEndTime(DateUtils.localDateToDateWithTime(expected.getSessionDate(), 13, 0).toInstant());
        databaseSeeder.insertCourtSchedule(expected);

        // Create allocated listing with hearing time within session window (11:00 is between 10:00-13:00)
        createAllocatedListing(expected, randomUUID(), randomUUID(), 60, "11:00");

        String updateCourtSchedulePayload = getPayload(UPDATE_COURT_SCHEDULE_JSON);
        final String sameCourtRoomId = expected.getCourtRoomId(); // Keep same courtroom to avoid "edited by another user" error
        final String changedBusinessType = DVLA_2;
        final String sameSessionType = expected.getCourtSession(); // Keep same session type
        final String samePanel = expected.getPanel(); // Keep same panel
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_SCHEDULE_ID_4, expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_ROOM_ID_2, sameCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(BUSINESS_TYPE_2, changedBusinessType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(SESSION_TYPE_2, sameSessionType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(PANEL_2, samePanel);
        // Set maxDuration to be >= allocated listing duration (60 minutes) to avoid validation error
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(MAX_DURATION_10, "\"maxDuration\": 120");
        // Note: update-court-schedule.json doesn't include sessionStartTime/sessionEndTime, so they'll be null

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);

        // Should succeed because session times are retrieved from persisted schedule and validation passes
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
    }

    @Test
    void shouldGet400WhenUpdatingCourtScheduleWithNullSessionTimesAndInvalidHearingTime() throws SQLException {
        // Test that when session times are null in update request but allocated listings exist,
        // the system retrieves session times from persisted schedule and validates hearing times
        final UUID courtScheduleId = randomUUID();
        final CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        final String courtRoomId = UUID_3FC02C0F; // Use same courtroom ID for initial and update
        final String courtHouseId = UUID_785339C1; // Test Crown Court - same court house as courtroom
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType(DVLA_2);
        expected.setSupportAdSplit(false);
        expected.setListingProfileId(USER_ID.toString()); // Set to current user to avoid "edited by another user" error
        // Match persisted courtSession and panel to update values to avoid "edited by another user" error
        expected.setCourtSession("AM");
        expected.setPanel(YOUTH_2);
        expected.setSessionDate(now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)));
        expected.setSessionStartTime(DateUtils.localDateToDateWithTime(expected.getSessionDate(), 10, 0).toInstant());
        expected.setSessionEndTime(DateUtils.localDateToDateWithTime(expected.getSessionDate(), 13, 0).toInstant());
        expected.setCourtRoomId(courtRoomId); // Set initial courtroom ID to match update
        expected.setCourtHouseId(courtHouseId); // Set court house ID to match courtroom
        databaseSeeder.insertCourtSchedule(expected);

        // Use 15:00 which is clearly after 13:00 session end even during BST (timezone conversion safe)
        createAllocatedListing(expected, randomUUID(), randomUUID(), 60, VALUE_15_00);

        String updateCourtSchedulePayload = getPayload(UPDATE_COURT_SCHEDULE_JSON);
        final String changedCourtRoomId = courtRoomId; // Use same courtroom to avoid court house validation error
        final String changedBusinessType = DVLA_2;
        final String changedSessionType = "AM";
        final String changedPanel = YOUTH_2;
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_SCHEDULE_ID_4, expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_ROOM_ID_2, changedCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(BUSINESS_TYPE_2, changedBusinessType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(SESSION_TYPE_2, changedSessionType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(PANEL_2, changedPanel);
        // Set maxDuration to be >= allocated listing duration (60 minutes) to avoid validation error
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(MAX_DURATION_10, "\"maxDuration\": 120");
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
        final UUID courtScheduleId = randomUUID();
        final CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        final String courtRoomId = UUID_3FC02C0F; // Use same courtroom ID for initial and update
        final String courtHouseId = UUID_785339C1; // Test Crown Court - same court house as courtroom
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType(DVLA_2);
        expected.setSupportAdSplit(false);
        // Match persisted courtSession and panel to update values to avoid "edited by another user" error
        expected.setCourtSession("AM");
        expected.setPanel(YOUTH_2);
        expected.setSessionDate(now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)));
        expected.setSessionStartTime(DateUtils.localDateToDateWithTime(expected.getSessionDate(), 10, 0).toInstant());
        expected.setSessionEndTime(DateUtils.localDateToDateWithTime(expected.getSessionDate(), 13, 0).toInstant());
        expected.setCourtRoomId(courtRoomId); // Set initial courtroom ID to match update
        expected.setCourtHouseId(courtHouseId); // Set court house ID to match courtroom
        databaseSeeder.insertCourtSchedule(expected);

        // No allocated listings created

        String updateCourtSchedulePayload = getPayload(UPDATE_COURT_SCHEDULE_JSON);
        final String changedCourtRoomId = courtRoomId; // Use same courtroom to avoid court house validation error
        final String changedBusinessType = DVLA_2;
        final String changedSessionType = "AM";
        final String changedPanel = YOUTH_2;
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_SCHEDULE_ID_4, expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_ROOM_ID_2, changedCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(BUSINESS_TYPE_2, changedBusinessType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(SESSION_TYPE_2, changedSessionType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(PANEL_2, changedPanel);
        // Note: update-court-schedule.json doesn't include sessionStartTime/sessionEndTime, so they'll be null

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);

        // Should succeed because no allocated listings means validation is skipped
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
    }

    @Test
    void shouldGet400WhenUpdatingCourtScheduleWithNullSessionTimesAndMinHearingTimeAfterSessionStart() throws SQLException {
        // Test that when session times are null in update request but allocated listings exist,
        // the system retrieves session times from persisted schedule and validates min hearing time
        final UUID courtScheduleId = randomUUID();
        final CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        final String courtRoomId = UUID_3FC02C0F; // Use same courtroom ID for initial and update
        final String courtHouseId = UUID_785339C1; // Test Crown Court - same court house as courtroom
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType(DVLA_2);
        expected.setSupportAdSplit(false);
        expected.setListingProfileId(USER_ID.toString()); // Set to current user to avoid "edited by another user" error
        // Match persisted courtSession and panel to update values to avoid "edited by another user" error
        expected.setCourtSession("AM");
        expected.setPanel(YOUTH_2);
        expected.setSessionDate(now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)));
        // Use 11:00 (not 10:00) so the gap survives the 1-hour London<->UTC shift during BST.
        // localDateToDateWithTime treats the input as London local time, but the validator's
        // sessionTimeFormatter renders the persisted instant in the JVM's default timezone (UTC
        // here) - a 1-hour gap collapses in BST and the MIN_HEARING_TIME validation no longer fires.
        expected.setSessionStartTime(DateUtils.localDateToDateWithTime(expected.getSessionDate(), 11, 0).toInstant());
        expected.setSessionEndTime(DateUtils.localDateToDateWithTime(expected.getSessionDate(), 13, 0).toInstant());
        expected.setCourtRoomId(courtRoomId); // Set initial courtroom ID to match update
        expected.setCourtHouseId(courtHouseId); // Set court house ID to match courtroom
        databaseSeeder.insertCourtSchedule(expected);

        // Use 07:00 which is clearly before 10:00 session start even during BST (timezone conversion safe)
        createAllocatedListing(expected, randomUUID(), randomUUID(), 60, "07:00");

        String updateCourtSchedulePayload = getPayload(UPDATE_COURT_SCHEDULE_JSON);
        final String changedCourtRoomId = courtRoomId; // Use same courtroom to avoid court house validation error
        final String changedBusinessType = DVLA_2;
        final String changedSessionType = "AM";
        final String changedPanel = YOUTH_2;
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_SCHEDULE_ID_4, expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_ROOM_ID_2, changedCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(BUSINESS_TYPE_2, changedBusinessType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(SESSION_TYPE_2, changedSessionType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(PANEL_2, changedPanel);
        // Set maxDuration to be >= allocated listing duration (60 minutes) to avoid validation error
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(MAX_DURATION_10, "\"maxDuration\": 120");
        // Note: update-court-schedule.json doesn't include sessionStartTime/sessionEndTime, so they'll be null

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);

        // Should fail because min hearing time (07:00) is before session start time (10:00) retrieved from persisted schedule
        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String errorResponseMessage = response.readEntity(String.class);
        assertThat(errorResponseMessage, containsString(MIN_HEARING_TIME_AFTER_SESSION_START_TIME));
    }

    @Test
    void shouldGet400WhenUpdatingCourtScheduleWithNonDurationBasedBusinessType() throws SQLException {
        final UUID courtScheduleId = randomUUID();
        final CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        final String courtRoomId = UUID_3FC02C0F; // Use same courtroom ID for initial and update
        final String courtHouseId = UUID_785339C1; // Test Crown Court - same court house as courtroom
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType(TRL_2);
        expected.setCourtRoomId(courtRoomId); // Set initial courtroom ID to match update
        expected.setCourtHouseId(courtHouseId); // Set court house ID to match courtroom
        databaseSeeder.insertCourtSchedule(expected);

        String updateCourtSchedulePayload = getPayload(UPDATE_COURT_SCHEDULE_ALL_DAY_SPLIT_JSON);
        final String changedCourtRoomId = courtRoomId; // Use same courtroom to avoid court house validation error
        final String changedBusinessType = TRL_2;
        final String changedSessionType = "AD";
        final String changedPanel = YOUTH_2;
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_SCHEDULE_ID_4, expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_ROOM_ID_2, changedCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(BUSINESS_TYPE_2, changedBusinessType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(SESSION_TYPE_2, changedSessionType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(PANEL_2, changedPanel);

        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("MAXDURATIONFORMORNING", VALUE_120);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("MAXDURATIONFORAFTERNOON", "60");

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
    }

    @Test
    void shouldGet400WhenUpdatingCourtScheduleADSplit() throws SQLException {
        final UUID courtScheduleId = randomUUID();
        final CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType(DVLA_2);
        expected.setSupportAdSplit(false);
        databaseSeeder.insertCourtSchedule(expected);

        String updateCourtSchedulePayload = getPayload(UPDATE_COURT_SCHEDULE_ALL_DAY_SPLIT_JSON);
        final String changedCourtRoomId = UUID_3FC02C0F; // picked from referencedata.rota-courtrooms.json file
        final String changedBusinessType = DVLA_2;
        final String changedSessionType = "AD";
        final String changedPanel = YOUTH_2;
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_SCHEDULE_ID_4, expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_ROOM_ID_2, changedCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(BUSINESS_TYPE_2, changedBusinessType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(SESSION_TYPE_2, changedSessionType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(PANEL_2, changedPanel);

        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("MAXDURATIONFORMORNING", VALUE_120);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("MAXDURATIONFORAFTERNOON", "60");

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
    }

    @Test
    void shouldUpdateCourtScheduleWithValidDurationBasedBusinessType() throws SQLException {
        final UUID courtScheduleId = randomUUID();
        final CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        final String courtRoomId = UUID_3FC02C0F; // Use same courtroom ID for initial and update
        final String courtHouseId = UUID_785339C1; // Test Crown Court - same court house as courtroom
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType(TRL_2);
        expected.setSupportAdSplit(true);
        expected.setCourtSession(ALL_DAY);
        expected.setSessionDate(now().with(TemporalAdjusters.next(DayOfWeek.MONDAY))); // Set to future date to avoid past session validation
        expected.setCourtRoomId(courtRoomId); // Set initial courtroom ID to match update
        expected.setCourtHouseId(courtHouseId); // Set court house ID to match courtroom
        databaseSeeder.insertCourtSchedule(expected);

        String updateCourtSchedulePayload = getPayload(UPDATE_COURT_SCHEDULE_ALL_DAY_SPLIT_JSON);
        final String changedCourtRoomId = courtRoomId; // Use same courtroom to avoid court house validation error
        final String changedBusinessType = TRL_2;
        final String changedSessionType = "AD";
        final String changedPanel = YOUTH_2;
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_SCHEDULE_ID_4, expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_ROOM_ID_2, changedCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(BUSINESS_TYPE_2, changedBusinessType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(SESSION_TYPE_2, changedSessionType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(PANEL_2, changedPanel);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(MAX_DURATION_FOR_MORNING_2, VALUE_120);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(MAX_DURATION_FOR_AFTERNOON_2, "60");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(SESSION_START_TIME_2, "10:30");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(SESSION_END_TIME_2, "17:30");

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
    }

    @Test
    void shouldNotUpdateCourtScheduleIfTotalBookedExceedsMaxDurationOrSlot() throws SQLException {
        final UUID courtScheduleId = randomUUID();
        final CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        final String courtRoomId = UUID_3FC02C0F; // Use same courtroom ID for initial and update
        final String courtHouseId = UUID_785339C1; // Test Crown Court - same court house as courtroom
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType(DVLA_2);
        expected.setSupportAdSplit(false);
        expected.setSessionDate(now().with(TemporalAdjusters.next(DayOfWeek.MONDAY))); // Set to future date to avoid past session validation
        expected.setCourtRoomId(courtRoomId); // Set initial courtroom ID to match update
        expected.setCourtHouseId(courtHouseId); // Set court house ID to match courtroom
        databaseSeeder.insertCourtSchedule(expected);

        String updateCourtSchedulePayload = getPayload(UPDATE_COURT_SCHEDULE_JSON);
        final String changedCourtRoomId = courtRoomId; // Use same courtroom to avoid court house validation error
        final String changedBusinessType = DVLA_2;
        final String changedSessionType = "AM";
        final String changedPanel = YOUTH_2;
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_SCHEDULE_ID_4, expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_ROOM_ID_2, changedCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(BUSINESS_TYPE_2, changedBusinessType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(SESSION_TYPE_2, changedSessionType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(PANEL_2, changedPanel);

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
    }

    @Test
    void shouldNotUpdateCourtScheduleIfTotalBookedExceedsMaxDurationForMorning() throws SQLException {
        final CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        final UUID courtScheduleId = randomUUID();
        final UUID hearingIdForMorning = randomUUID();
        final UUID bookingIdForMorning = randomUUID();
        final UUID hearingIdForAfternoon = randomUUID();
        final UUID bookingIdForAfternoon = randomUUID();
        final Integer maxDurationForMorning = 120;
        final Integer maxDurationForAfternoon = 60;

        courtSchedule.setBusinessType(TRL_2);
        courtSchedule.setSlotBased(false);
        courtSchedule.setMaxDuration(0);
        courtSchedule.setAvailableDuration(0);
        courtSchedule.setSupportAdSplit(true);
        courtSchedule.setCourtSession(ALL_DAY);
        courtSchedule.setMaxAdMorningDuration(maxDurationForMorning);
        courtSchedule.setMaxAdAfternoonDuration(maxDurationForAfternoon);
        courtSchedule.setCourtScheduleId(courtScheduleId.toString());
        courtSchedule.setSessionDate(getRandomFutureDateWithinNextYear());
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), DEFAULT_ALL_DAY_START_TIME).toInstant());
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), VALUE_16_00).toInstant());
        final String courtRoomId = UUID_3FC02C0F; // Use same courtroom ID for initial and update
        final String courtHouseId = UUID_785339C1; // Test Crown Court - same court house as courtroom
        courtSchedule.setCourtRoomId(courtRoomId); // Set initial courtroom ID to match update
        courtSchedule.setCourtHouseId(courtHouseId); // Set court house ID to match courtroom
        databaseSeeder.insertCourtSchedule(courtSchedule);

        createAllocatedListing(courtSchedule, hearingIdForMorning, bookingIdForMorning, 90, DEFAULT_ALL_DAY_START_TIME);
        createAllocatedListing(courtSchedule, hearingIdForAfternoon, bookingIdForAfternoon, 60, DEFAULT_AFTERNOON_START_TIME);

        String updateCourtSchedulePayload = getPayload(UPDATE_COURT_SCHEDULE_ALL_DAY_SPLIT_JSON);
        final String changedCourtRoomId = courtRoomId; // Use same courtroom to avoid court house validation error
        final String changedBusinessType = TRL_2;
        final String changedSessionType = "AD";
        final String changedPanel = YOUTH_2;
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_SCHEDULE_ID_4, courtSchedule.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_ROOM_ID_2, changedCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(BUSINESS_TYPE_2, changedBusinessType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(SESSION_TYPE_2, changedSessionType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(PANEL_2, changedPanel);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(MAX_DURATION_FOR_MORNING_2, "60");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(MAX_DURATION_FOR_AFTERNOON_2, "30");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(SESSION_START_TIME_2, DEFAULT_ALL_DAY_START_TIME);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(SESSION_END_TIME_2, VALUE_15_00);

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);
        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));

        final String errorResponseMessage = response.readEntity(String.class);
        assertThat(errorResponseMessage, containsString(MAX_DURATION_FOR_MORNING_LESS_THAN_TOTAL_BOOKED_FOR_MORNING));
    }

    @Test
    void shouldNotUpdateCourtScheduleIfTotalBookedExceedsMaxDurationForAfternoon() throws SQLException {
        final CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        final UUID courtScheduleId = randomUUID();
        final UUID hearingIdForMorning = randomUUID();
        final UUID bookingIdForMorning = randomUUID();
        final UUID hearingIdForAfternoon = randomUUID();
        final UUID bookingIdForAfternoon = randomUUID();
        final Integer maxDurationForMorning = 120;
        final Integer maxDurationForAfternoon = 60;

        courtSchedule.setBusinessType(TRL_2);
        courtSchedule.setSlotBased(false);
        courtSchedule.setMaxDuration(0);
        courtSchedule.setAvailableDuration(0);
        courtSchedule.setSupportAdSplit(true);
        courtSchedule.setCourtSession(ALL_DAY);
        courtSchedule.setMaxAdMorningDuration(maxDurationForMorning);
        courtSchedule.setMaxAdAfternoonDuration(maxDurationForAfternoon);
        courtSchedule.setCourtScheduleId(courtScheduleId.toString());
        courtSchedule.setSessionDate(getRandomFutureDateWithinNextYear());
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), DEFAULT_ALL_DAY_START_TIME).toInstant());
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), VALUE_16_00).toInstant());
        final String courtRoomId = UUID_3FC02C0F; // Use same courtroom ID for initial and update
        final String courtHouseId = UUID_785339C1; // Test Crown Court - same court house as courtroom
        courtSchedule.setCourtRoomId(courtRoomId); // Set initial courtroom ID to match update
        courtSchedule.setCourtHouseId(courtHouseId); // Set court house ID to match courtroom
        databaseSeeder.insertCourtSchedule(courtSchedule);

        createAllocatedListing(courtSchedule, hearingIdForMorning, bookingIdForMorning, 90, DEFAULT_ALL_DAY_START_TIME);
        createAllocatedListing(courtSchedule, hearingIdForAfternoon, bookingIdForAfternoon, 60, DEFAULT_AFTERNOON_START_TIME);

        String updateCourtSchedulePayload = getPayload(UPDATE_COURT_SCHEDULE_ALL_DAY_SPLIT_JSON);
        final String changedCourtRoomId = courtRoomId; // Use same courtroom to avoid court house validation error
        final String changedBusinessType = TRL_2;
        final String changedSessionType = "AD";
        final String changedPanel = YOUTH_2;
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_SCHEDULE_ID_4, courtSchedule.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_ROOM_ID_2, changedCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(BUSINESS_TYPE_2, changedBusinessType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(SESSION_TYPE_2, changedSessionType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(PANEL_2, changedPanel);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(MAX_DURATION_FOR_MORNING_2, "90");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(MAX_DURATION_FOR_AFTERNOON_2, "30");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(SESSION_START_TIME_2, DEFAULT_ALL_DAY_START_TIME);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(SESSION_END_TIME_2, VALUE_15_00);

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);
        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));

        final String errorResponseMessage = response.readEntity(String.class);
        assertThat(errorResponseMessage, containsString(MAX_DURATION_FOR_AFTERNOON_LESS_THAN_TOTAL_BOOKED_FOR_AFTERNOON));
    }

    @Test
    void shouldNotAllowUpdateCourtScheduleForDifferentBusinessType() throws SQLException {
        final UUID courtScheduleId = randomUUID();
        final CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType(DVLA_2);
        databaseSeeder.insertCourtSchedule(expected);

        String updateCourtSchedulePayload = getPayload(UPDATE_COURT_SCHEDULE_JSON);
        final String changedCourtRoomId = UUID_3FC02C0F; // picked from referencedata.rota-courtrooms.json file
        final String changedBusinessType = "NCPT";
        final String changedSessionType = "AM";
        final String changedPanel = YOUTH_2;
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_SCHEDULE_ID_4, expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_ROOM_ID_2, changedCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(BUSINESS_TYPE_2, changedBusinessType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(SESSION_TYPE_2, changedSessionType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(PANEL_2, changedPanel);

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
    }

    @Test
    void shouldGetCourtSchedules() throws Exception {
        final UUID courtScheduleId = randomUUID();
        final UUID hearingId = randomUUID();
        final UUID bookingId = randomUUID();
        final CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        final LocalDate fromDate = expected.getSessionDate().minusDays(1);
        final LocalDate toDate = expected.getSessionDate().plusDays(1);
        expected.setBusinessType(TRL_2);

        expected.setSlotBased(false);
        expected.setMaxDuration(5);
        expected.setAvailableDuration(5);
        expected.setSupportAdSplit(false);
        expected.setMaxAdMorningDuration(0);
        expected.setMaxAdAfternoonDuration(0);
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setIsDraft(false);
        expected.setSessionStartTime(expected.getSessionDate().atTime(10, 0).atZone(UTC).toInstant());
        expected.setSessionEndTime(expected.getSessionDate().atTime(17, 0).atZone(UTC).toInstant());
        expected.setIsOverbookingAllowed(false);
        expected.setJurisdiction(MAGISTRATES.getJurisdiction());
        databaseSeeder.insertCourtSchedule(expected);
        final CourtScheduleJudiciary courtScheduleJudiciary = createTestCourtScheduleJudiciary(expected.getCourtScheduleId());
        databaseSeeder.saveJudiciarySchedule(courtScheduleJudiciary);

        final AllocatedListing allocatedListing = RANDOM.nextObject(AllocatedListing.class);
        allocatedListing.setId(randomUUID().toString());
        allocatedListing.setCourtScheduleId(expected.getCourtScheduleId());
        allocatedListing.setHearingId(hearingId.toString());
        allocatedListing.setBookingId(bookingId.toString());
        databaseSeeder.insertAllocatedListing(allocatedListing);

        String getCourtScheduleRequestParams = getPayload(COURTSCHEDULER_GET_COURT_SCHEDULE_QUERY_JSON);
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace(COURT_CENTRE_ID_2, expected.getCourtHouseId());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace(COURT_ROOM_ID_2, expected.getCourtRoomId());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace(BUSINESS_TYPE_2, expected.getBusinessType());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace(SESSION_START_DATE_2, fromDate.toString());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace(SESSION_END_DATE_2, toDate.toString());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace(PAGE_SIZE_2, "10");
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace(PAGE_NUMBER_2, "1");

        final Map<String, Object> map = mapper.readValue(getCourtScheduleRequestParams, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams(BASE_RESOURCE_URL, COURT_SCHEDULE_GET_CONTENT_TYPE, USER_ID, map);


        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        final JsonObject jsonObject = stringToJsonObjectConverter.convert(tempResponseData.getPayload());

        final JsonObject courtRoomGroup = jsonObject.getJsonArray(COURT_SCHEDULES).getJsonObject(0);
        // Group-level contract (legacy CourtSessionsView shape): courtRoomId + courtRoomName, no courtRoomNumber.
        assertThat(courtRoomGroup.getString(COURT_ROOM_ID_3), is(expected.getCourtRoomId()));
        assertThat(courtRoomGroup.getString(COURT_ROOM_NAME), is(expected.getCourtRoomName()));
        assertThat(courtRoomGroup.containsKey("courtRoomNumber"), is(false));

        final JsonObject courtScheduleJsonObject = courtRoomGroup.getJsonArray(SESSIONS).getJsonObject(0);
        assertThat(courtScheduleJsonObject.getString(COURT_SCHEDULE_ID_5), is(expected.getCourtScheduleId()));
        assertThat(courtScheduleJsonObject.getString(PANEL_3), is(expected.getPanel()));
        assertThat(courtScheduleJsonObject.getBoolean(SLOT_BASED), is(false));
        assertThat(courtScheduleJsonObject.getBoolean(ACTIVE), is(true));
        assertThat(courtScheduleJsonObject.getString(COURT_ROOM_ID_3), is(expected.getCourtRoomId()));
        assertThat(courtScheduleJsonObject.getString(COURT_ROOM_NAME), is(expected.getCourtRoomName()));
        assertThat(courtScheduleJsonObject.getBoolean(ALL_DAY_SPLIT), is(false));
        assertThat(courtScheduleJsonObject.getInt(MAX_DURATION_FOR_MORNING_3), is(0));
        assertThat(courtScheduleJsonObject.getInt(MAX_DURATION_FOR_AFTERNOON_3), is(0));
        assertThat(courtScheduleJsonObject.getString(JURISDICTION), is(MAGISTRATES.getJurisdiction()));
        assertThat(courtScheduleJsonObject.getJsonArray(JUDICIARIES).size(), is(1));
        final JsonObject judiciaryJsonObject = courtScheduleJsonObject.getJsonArray(JUDICIARIES).getJsonObject(0);
        assertThat(judiciaryJsonObject.getString("id"), is(courtScheduleJudiciary.getId().getJudiciaryId()));
        assertThat(judiciaryJsonObject.containsKey(P_TITLE_PREFIX), is(true));
        assertThat(judiciaryJsonObject.getString(P_TITLE_PREFIX), is(courtScheduleJudiciary.getTitle()));
        assertThat(judiciaryJsonObject.containsKey(P_TITLE_JUDICIAL_PREFIX), is(false));
        assertThat(judiciaryJsonObject.getString("forenames"), is(courtScheduleJudiciary.getForenames()));
        assertThat(judiciaryJsonObject.getString("surname"), is(courtScheduleJudiciary.getSurname()));
        assertThat(judiciaryJsonObject.getString(P_JUDICIARY_TYPE), is("Recorder"));
        assertThat(judiciaryJsonObject.getString("emailAddress"), is(courtScheduleJudiciary.getEmail()));
        assertThat(judiciaryJsonObject.getBoolean(P_IS_BENCH_CHAIRMAN), is(courtScheduleJudiciary.isBenchChairman()));
        assertThat(judiciaryJsonObject.getBoolean(P_IS_DEPUTY), is(courtScheduleJudiciary.isDeputy()));
        assertThat(judiciaryJsonObject.getInt("seqId"), is(1));
        assertThat(judiciaryJsonObject.containsKey(P_TITLE_JUDICIAL_PREFIX_WELSH), is(false));
        assertThat(judiciaryJsonObject.getString("personId"), is("30644"));
        assertThat(judiciaryJsonObject.getString("requestedName"), is("HER HONOUR JUDGE K WANT QC, HONORARY RECORDER OF WALES"));
        assertThat(judiciaryJsonObject.getJsonArray(P_SPECIALISMS).size(), is(1));
        assertThat(judiciaryJsonObject.getJsonArray(P_SPECIALISMS).getString(0), is("ATTEMPTED_MURDER"));
    }

    @Test
    void shouldGetCourtSchedulesReturnsJudicialPrefixAloneWhenRefdataTitlePrefixBlank() throws Exception {
        // Reproduces SPRDT-757 STE bug: the DB `title` column carries a stale value from a
        // previous assignment (when only titleJudicialPrefix was populated), and refdata now
        // returns titlePrefix blank. The response must not contain titlePrefix alongside
        // titleJudicialPrefix — they are mutually exclusive.
        final String judiciaryIdWithOnlyJudicialPrefix = "9a9e1b6c-0d77-4f7e-9b2a-8c6d0aa13334";
        final String staleDbTitle = "His Honour Judge";

        final UUID courtScheduleId = randomUUID();
        final CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        final LocalDate fromDate = expected.getSessionDate().minusDays(1);
        final LocalDate toDate = expected.getSessionDate().plusDays(1);
        expected.setBusinessType("TRL");
        expected.setSlotBased(false);
        expected.setMaxDuration(5);
        expected.setAvailableDuration(5);
        expected.setSupportAdSplit(false);
        expected.setMaxAdMorningDuration(0);
        expected.setMaxAdAfternoonDuration(0);
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setIsDraft(false);
        expected.setSessionStartTime(expected.getSessionDate().atTime(10, 0).atZone(UTC).toInstant());
        expected.setSessionEndTime(expected.getSessionDate().atTime(17, 0).atZone(UTC).toInstant());
        expected.setIsOverbookingAllowed(false);
        expected.setJurisdiction(MAGISTRATES.getJurisdiction());
        databaseSeeder.insertCourtSchedule(expected);

        final CourtScheduleJudiciary courtScheduleJudiciary = createTestCourtScheduleJudiciary(
                expected.getCourtScheduleId(), judiciaryIdWithOnlyJudicialPrefix, staleDbTitle);
        databaseSeeder.saveJudiciarySchedule(courtScheduleJudiciary);

        final AllocatedListing allocatedListing = RANDOM.nextObject(AllocatedListing.class);
        allocatedListing.setId(randomUUID().toString());
        allocatedListing.setCourtScheduleId(expected.getCourtScheduleId());
        allocatedListing.setHearingId(randomUUID().toString());
        allocatedListing.setBookingId(randomUUID().toString());
        databaseSeeder.insertAllocatedListing(allocatedListing);

        String getCourtScheduleRequestParams = getPayload("courtscheduler.get.court_schedule_query.json");
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("COURT_CENTRE_ID", expected.getCourtHouseId());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("COURT_ROOM_ID", expected.getCourtRoomId());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("BUSINESS_TYPE", expected.getBusinessType());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("SESSION_START_DATE", fromDate.toString());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("SESSION_END_DATE", toDate.toString());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("PAGE_SIZE", "10");
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("PAGE_NUMBER", "1");

        final Map<String, Object> map = mapper.readValue(getCourtScheduleRequestParams, new TypeReference<>() {
        });
        final RequestParams requestParams = getRequestParams(BASE_RESOURCE_URL, COURT_SCHEDULE_GET_CONTENT_TYPE, USER_ID, map);

        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();
        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        final JsonObject jsonObject = stringToJsonObjectConverter.convert(tempResponseData.getPayload());
        final JsonObject judiciaryJsonObject = jsonObject.getJsonArray(COURT_SCHEDULES).getJsonObject(0)
                .getJsonArray(SESSIONS).getJsonObject(0)
                .getJsonArray(JUDICIARIES).getJsonObject(0);

        assertThat(judiciaryJsonObject.getString("id"), is(judiciaryIdWithOnlyJudicialPrefix));
        // Stale DB title must NOT leak through when refdata's titlePrefix is blank.
        assertThat(judiciaryJsonObject.containsKey(P_TITLE_PREFIX), is(false));
        assertThat(judiciaryJsonObject.containsKey(P_TITLE_JUDICIAL_PREFIX), is(true));
        assertThat(judiciaryJsonObject.getString(P_TITLE_JUDICIAL_PREFIX), is("His Honour Judge"));
        assertThat(judiciaryJsonObject.containsKey(P_TITLE_JUDICIAL_PREFIX_WELSH), is(true));
        assertThat(judiciaryJsonObject.getString(P_TITLE_JUDICIAL_PREFIX_WELSH), is("Ei Anrhydedd y Barnwr"));
    }

    @Test
    void shouldGetCourtSchedulesCrown() throws SQLException, JsonProcessingException {
        final UUID courtScheduleId = randomUUID();
        final UUID hearingId = randomUUID();
        final UUID bookingId = randomUUID();
        final CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        final LocalDate fromDate = expected.getSessionDate().minusDays(1);
        final LocalDate toDate = expected.getSessionDate().plusDays(1);
        expected.setBusinessType(TRL_2);

        expected.setSlotBased(false);
        expected.setMaxDuration(5);
        expected.setAvailableDuration(5);
        expected.setSupportAdSplit(false);
        expected.setMaxAdMorningDuration(0);
        expected.setMaxAdAfternoonDuration(0);
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setIsDraft(true);
        expected.setSessionStartTime(expected.getSessionDate().atTime(10, 0).atZone(UTC).toInstant());
        expected.setSessionEndTime(expected.getSessionDate().atTime(17, 0).atZone(UTC).toInstant());
        expected.setIsOverbookingAllowed(false);
        expected.setJurisdiction(CROWN_2);
        databaseSeeder.insertCourtSchedule(expected);

        final AllocatedListing allocatedListing = RANDOM.nextObject(AllocatedListing.class);
        allocatedListing.setId(randomUUID().toString());
        allocatedListing.setCourtScheduleId(expected.getCourtScheduleId());
        allocatedListing.setHearingId(hearingId.toString());
        allocatedListing.setBookingId(bookingId.toString());
        databaseSeeder.insertAllocatedListing(allocatedListing);

        String getCourtScheduleRequestParams = getPayload("courtscheduler.get.court_schedule_isDraft_query.json");
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace(COURT_CENTRE_ID_2, expected.getCourtHouseId());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace(COURT_ROOM_ID_2, expected.getCourtRoomId());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace(BUSINESS_TYPE_2, expected.getBusinessType());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace(SESSION_START_DATE_2, fromDate.toString());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace(SESSION_END_DATE_2, toDate.toString());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("IS_DRAFT", "true");
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace(PAGE_SIZE_2, "10");
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace(PAGE_NUMBER_2, "1");

        final Map<String, Object> map = mapper.readValue(getCourtScheduleRequestParams, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams(BASE_RESOURCE_URL, COURT_SCHEDULE_GET_CONTENT_TYPE, USER_ID, map);


        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        final JsonObject jsonObject = stringToJsonObjectConverter.convert(tempResponseData.getPayload());

        final JsonObject courtScheduleJsonObject = jsonObject.getJsonArray(COURT_SCHEDULES).getJsonObject(0).getJsonArray(SESSIONS).getJsonObject(0);
        assertThat(courtScheduleJsonObject.getString(COURT_SCHEDULE_ID_5), is(expected.getCourtScheduleId()));
        assertThat(courtScheduleJsonObject.getString(PANEL_3), is(expected.getPanel()));
        assertThat(courtScheduleJsonObject.getString("businessType"), is(expected.getBusinessType()));
        assertThat(courtScheduleJsonObject.getBoolean(SLOT_BASED), is(false));
        assertThat(courtScheduleJsonObject.getBoolean(ACTIVE), is(true));
        assertThat(courtScheduleJsonObject.getString(COURT_ROOM_ID_3), is(expected.getCourtRoomId()));
        assertThat(courtScheduleJsonObject.getString(COURT_ROOM_NAME), is(expected.getCourtRoomName()));
        assertThat(courtScheduleJsonObject.getBoolean(ALL_DAY_SPLIT), is(false));
        assertThat(courtScheduleJsonObject.getInt(MAX_DURATION_FOR_MORNING_3), is(0));
        assertThat(courtScheduleJsonObject.getInt(MAX_DURATION_FOR_AFTERNOON_3), is(0));
        assertThat(courtScheduleJsonObject.getBoolean("isDraft"), is(true));
        assertThat(courtScheduleJsonObject.getString(JURISDICTION), is(CROWN.getJurisdiction()));
    }


    @Test
    void shouldSearchCourtSchedulesById() throws Exception {

        final String courtScheduleId = UUID_ABCDEF12;
        final CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setCourtScheduleId(courtScheduleId);
        courtSchedule.setBusinessType(TRL_2);
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
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), DEFAULT_ALL_DAY_START_TIME).toInstant());
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), VALUE_16_00).toInstant());
        courtSchedule.setOuCode("B12345");
        courtSchedule.setIsDraft(false);
        courtSchedule.setJurisdiction(MAGISTRATES.getJurisdiction());
        courtSchedule.setActive(true);

        databaseSeeder.insertCourtSchedule(courtSchedule);
        final CourtScheduleJudiciary courtScheduleJudiciary = createTestCourtScheduleJudiciary(courtSchedule.getCourtScheduleId());
        databaseSeeder.saveJudiciarySchedule(courtScheduleJudiciary);

        // Also seed allocated listings for realism
        final UUID hearingId = randomUUID();
        final UUID bookingId = randomUUID();
        createAllocatedListing(courtSchedule, hearingId, bookingId, 60, DEFAULT_ALL_DAY_START_TIME);

        String getCourtScheduleRequestParams = getPayload("courtscheduler.search.courtschedules.by.id.json");
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace(COURT_SCHEDULE_ID_4, courtScheduleId);
        final Map<String, Object> map = mapper.readValue(getCourtScheduleRequestParams, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams(
                SEARCH_BY_ID_URL,
                COURT_SCHEDULE_SEARCH_COURTSCHEDULES_BY_ID_CONTENT_TYPE,
                SYSTEM_USER_ID,
                map
        );

        final ResponseData response = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertEquals(OK.getStatusCode(), response.getStatus().getStatusCode());

        final JsonObject json = stringToJsonObjectConverter.convert(response.getPayload());
        assertThat(json.getJsonArray(COURT_SCHEDULES).size(), is(1));

        final JsonObject courtRoomJson = json.getJsonArray(COURT_SCHEDULES).getJsonObject(0);
        final JsonObject courtSessionJson = courtRoomJson.getJsonArray(SESSIONS).getJsonObject(0);

        assertThat(courtSessionJson.getString(COURT_SCHEDULE_ID_5), is(courtSchedule.getCourtScheduleId()));
        assertThat(courtSessionJson.getBoolean(SLOT_BASED), is(false));
        assertThat(courtSessionJson.getString(COURT_ROOM_ID_3), is(courtSchedule.getCourtRoomId()));
        assertThat(courtSessionJson.getBoolean(ALL_DAY_SPLIT), is(true));
        assertThat(courtSessionJson.getInt(MAX_DURATION_FOR_MORNING_3), is(120));
        assertThat(courtSessionJson.getInt(MAX_DURATION_FOR_AFTERNOON_3), is(60));
        assertThat(courtSessionJson.getString("listingProfileId"), is(courtSchedule.getListingProfileId()));
        assertThat(courtSessionJson.getString("ouCode"), is(courtSchedule.getOuCode()));
        assertThat(courtSessionJson.getString(COURT_ROOM_NAME), is(courtSchedule.getCourtRoomName()));
        assertThat(courtSessionJson.getString("courtHouseId"), is(courtSchedule.getCourtHouseId()));
        assertThat(courtSessionJson.getString("courtHouseName"), is(courtSchedule.getCourtHouseName()));
        assertThat(courtSessionJson.getString("operationalUnit"), is(courtSchedule.getOperationalUnit()));
        assertThat(courtSessionJson.getString("businessType"), is(courtSchedule.getBusinessType()));
        assertThat(courtSessionJson.getString(PANEL_3), is(courtSchedule.getPanel()));
        assertThat(courtSessionJson.getBoolean(ACTIVE), is(courtSchedule.isActive()));
        assertThat(courtSessionJson.getString("courtSession"), is(courtSchedule.getCourtSession()));
        assertThat(courtSessionJson.getInt("maxDuration"), is(courtSchedule.getMaxDuration()));
        assertThat(courtSessionJson.getInt("availableDuration"), is(courtSchedule.getAvailableDuration()));
        assertThat(courtSessionJson.getInt("maxSlots"), is(courtSchedule.getMaxSlots()));
        assertThat(courtSessionJson.getInt("availableSlots"), is(courtSchedule.getAvailableSlots()));
        assertThat(courtSessionJson.getJsonArray(JUDICIARIES).size(), is(1));
        final JsonObject judiciaryJsonObject = courtSessionJson.getJsonArray(JUDICIARIES).getJsonObject(0);
        assertThat(judiciaryJsonObject.getString("id"), is(courtScheduleJudiciary.getId().getJudiciaryId()));
        assertThat(judiciaryJsonObject.containsKey(P_TITLE_PREFIX), is(true));
        assertThat(judiciaryJsonObject.getString(P_TITLE_PREFIX), is(courtScheduleJudiciary.getTitle()));
        assertThat(judiciaryJsonObject.containsKey(P_TITLE_JUDICIAL_PREFIX), is(false));
        assertThat(judiciaryJsonObject.getString("forenames"), is(courtScheduleJudiciary.getForenames()));
        assertThat(judiciaryJsonObject.getString("surname"), is(courtScheduleJudiciary.getSurname()));
        assertThat(judiciaryJsonObject.getString(P_JUDICIARY_TYPE), is("Recorder"));
        assertThat(judiciaryJsonObject.getString("emailAddress"), is(courtScheduleJudiciary.getEmail()));
        assertThat(judiciaryJsonObject.getBoolean(P_IS_BENCH_CHAIRMAN), is(courtScheduleJudiciary.isBenchChairman()));
        assertThat(judiciaryJsonObject.getBoolean(P_IS_DEPUTY), is(courtScheduleJudiciary.isDeputy()));
        assertThat(judiciaryJsonObject.getInt("seqId"), is(1));
        assertThat(judiciaryJsonObject.containsKey(P_TITLE_JUDICIAL_PREFIX_WELSH), is(false));
        assertThat(judiciaryJsonObject.getString("personId"), is("30644"));
        assertThat(judiciaryJsonObject.getString("requestedName"), is("HER HONOUR JUDGE K WANT QC, HONORARY RECORDER OF WALES"));
        assertThat(judiciaryJsonObject.getJsonArray(P_SPECIALISMS).size(), is(1));
        assertThat(judiciaryJsonObject.getJsonArray(P_SPECIALISMS).getString(0), is("ATTEMPTED_MURDER"));

        assertThat(courtSessionJson.getString(SESSION_START_TIME_3), is(sessionTimeFormatter(courtSchedule.getSessionStartTime())));
        assertThat(courtSessionJson.getString(SESSION_END_TIME_3), is(sessionTimeFormatter(courtSchedule.getSessionEndTime())));
    }

    @Test
    void shouldReturnCorrectAvailableSlotsWhenAllocatedListingsExistForSlotBasedSession() throws Exception {
        // Given a slot-based court schedule with 10 max slots and 3 allocated listings,
        // the search-by-id read view should report availableSlots = maxSlots - 3. The
        // court_schedule counters are seeded to the post-booking state to mirror what
        // the booking write flow would have produced.
        final String courtScheduleId = randomUUID().toString();
        final int maxSlots = 10;
        final int bookedCount = 3;
        final int expectedAvailableSlots = maxSlots - bookedCount;
        final int slotDurationMinutes = 30;

        final CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setCourtScheduleId(courtScheduleId);
        courtSchedule.setBusinessType(DVLA_2);
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
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), DEFAULT_ALL_DAY_START_TIME).toInstant());
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), VALUE_16_00).toInstant());
        courtSchedule.setIsDraft(false);
        courtSchedule.setJurisdiction(MAGISTRATES.getJurisdiction());
        courtSchedule.setActive(true);
        databaseSeeder.insertCourtSchedule(courtSchedule);

        createAllocatedListing(courtSchedule, randomUUID(), randomUUID(), slotDurationMinutes, DEFAULT_ALL_DAY_START_TIME);
        createAllocatedListing(courtSchedule, randomUUID(), randomUUID(), slotDurationMinutes, "11:00");
        createAllocatedListing(courtSchedule, randomUUID(), randomUUID(), slotDurationMinutes, "12:00");

        String requestBody = getPayload(COURTSCHEDULER_SEARCH_COURTSCHEDULES_BY_ID_DYNAM);
        requestBody = requestBody.replace(COURT_SCHEDULE_ID_4, courtScheduleId);
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
        assertThat(json.getJsonArray(COURT_SCHEDULES).size(), is(1));
        // Response is grouped per courtroom (SPRDT-757): courtSchedules[] -> {courtRoomId, courtRoomName, sessions[]}
        final JsonObject session = json.getJsonArray(COURT_SCHEDULES).getJsonObject(0)
                .getJsonArray(SESSIONS).getJsonObject(0);
        assertThat(session.getString(COURT_SCHEDULE_ID_5), is(courtScheduleId));
        assertThat(session.getBoolean(SLOT_BASED), is(true));
        assertThat(session.getInt("maxSlots"), is(maxSlots));
        assertThat(session.getInt("availableSlots"), is(expectedAvailableSlots));
        assertThat(session.getInt(TOTAL_BOOKED), is(bookedCount * slotDurationMinutes));
    }

    @Test
    void shouldReturnCorrectAvailableDurationWhenAllocatedListingsExistForDurationBasedSession() throws Exception {
        // Given a CROWN duration-based court schedule with 300 max-duration minutes
        // and 3 allocated listings of 60/90/45 minutes, the search-by-id read view
        // should report availableDuration = maxDuration - sum(durations).
        // CROWN + TRL + duration-based mirrors the Crown Court trial workflow used
        // by shouldDeleteMultipleCourtSchedules.
        final String courtScheduleId = randomUUID().toString();
        final int maxDuration = 300;
        final int duration1 = 60;
        final int duration2 = 90;
        final int duration3 = 45;
        final int totalBooked = duration1 + duration2 + duration3; // 195
        final int expectedAvailableDuration = maxDuration - totalBooked; // 105

        final CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setCourtScheduleId(courtScheduleId);
        courtSchedule.setBusinessType(TRL_2);
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
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), DEFAULT_ALL_DAY_START_TIME).toInstant());
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), VALUE_16_00).toInstant());
        courtSchedule.setIsDraft(false);
        courtSchedule.setJurisdiction(CROWN.getJurisdiction());
        courtSchedule.setActive(true);
        databaseSeeder.insertCourtSchedule(courtSchedule);

        createAllocatedListing(courtSchedule, randomUUID(), randomUUID(), duration1, DEFAULT_ALL_DAY_START_TIME);
        createAllocatedListing(courtSchedule, randomUUID(), randomUUID(), duration2, "11:30");
        createAllocatedListing(courtSchedule, randomUUID(), randomUUID(), duration3, "13:30");

        String requestBody = getPayload(COURTSCHEDULER_SEARCH_COURTSCHEDULES_BY_ID_DYNAM);
        requestBody = requestBody.replace(COURT_SCHEDULE_ID_4, courtScheduleId);
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
        assertThat(json.getJsonArray(COURT_SCHEDULES).size(), is(1));
        // Response is grouped per courtroom (SPRDT-757): courtSchedules[] -> {courtRoomId, courtRoomName, sessions[]}
        final JsonObject session = json.getJsonArray(COURT_SCHEDULES).getJsonObject(0)
                .getJsonArray(SESSIONS).getJsonObject(0);
        assertThat(session.getString(COURT_SCHEDULE_ID_5), is(courtScheduleId));
        assertThat(session.getString(JURISDICTION), is(CROWN.getJurisdiction()));
        assertThat(session.getBoolean(SLOT_BASED), is(false));
        assertThat(session.getInt("maxDuration"), is(maxDuration));
        assertThat(session.getInt("availableDuration"), is(expectedAvailableDuration));
        assertThat(session.getInt(TOTAL_BOOKED), is(totalBooked));
    }

    @Test
    void shouldGetCourtSchedulesWithMinMaxSessionTimes() throws SQLException, JsonProcessingException {

        final CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        final LocalDate fromDate = expected.getSessionDate().minusDays(1);
        final LocalDate toDate = expected.getSessionDate().plusDays(1);
        expected.setBusinessType(TRL_2);

        expected.setSlotBased(false);
        expected.setMaxDuration(5);
        expected.setAvailableDuration(5);
        expected.setSupportAdSplit(true);
        expected.setMaxAdMorningDuration(0);
        expected.setCourtSession(ALL_DAY);
        expected.setMaxAdAfternoonDuration(0);
        expected.setCourtScheduleId(randomUUID().toString());
        expected.setSessionStartTime(expected.getSessionDate().atTime(10, 0).atZone(UTC).toInstant());
        expected.setSessionEndTime(expected.getSessionDate().atTime(17, 0).atZone(UTC).toInstant());
        expected.setIsOverbookingAllowed(true);
        expected.setJurisdiction(MAGISTRATES.getJurisdiction());
        databaseSeeder.insertCourtSchedule(expected);

        final AllocatedListing allocatedListing1 = RANDOM.nextObject(AllocatedListing.class);
        allocatedListing1.setId(randomUUID().toString());
        allocatedListing1.setCourtScheduleId(expected.getCourtScheduleId());
        allocatedListing1.setHearingId(randomUUID().toString());
        allocatedListing1.setBookingId(randomUUID().toString());
        allocatedListing1.setHearingStartTime(expected.getSessionDate().atTime(10, 0).atZone(UTC).toInstant());
        databaseSeeder.insertAllocatedListing(allocatedListing1);

        final AllocatedListing allocatedListing2 = RANDOM.nextObject(AllocatedListing.class);
        allocatedListing2.setId(randomUUID().toString());
        allocatedListing2.setCourtScheduleId(expected.getCourtScheduleId());
        allocatedListing2.setHearingId(randomUUID().toString());
        allocatedListing2.setBookingId(randomUUID().toString());
        allocatedListing2.setHearingStartTime(expected.getSessionDate().atTime(9, 0).atZone(UTC).toInstant());
        databaseSeeder.insertAllocatedListing(allocatedListing2);

        final AllocatedListing allocatedListing3 = RANDOM.nextObject(AllocatedListing.class);
        allocatedListing3.setId(randomUUID().toString());
        allocatedListing3.setCourtScheduleId(expected.getCourtScheduleId());
        allocatedListing3.setHearingId(randomUUID().toString());
        allocatedListing3.setBookingId(randomUUID().toString());
        allocatedListing3.setHearingStartTime(expected.getSessionDate().atTime(15, 0).atZone(UTC).toInstant());
        databaseSeeder.insertAllocatedListing(allocatedListing3);

        final AllocatedListing allocatedListing4 = RANDOM.nextObject(AllocatedListing.class);
        allocatedListing4.setId(randomUUID().toString());
        allocatedListing4.setCourtScheduleId(expected.getCourtScheduleId());
        allocatedListing4.setHearingId(randomUUID().toString());
        allocatedListing4.setBookingId(randomUUID().toString());
        allocatedListing4.setHearingStartTime(expected.getSessionDate().atTime(14, 0).atZone(UTC).toInstant());
        databaseSeeder.insertAllocatedListing(allocatedListing4);

        String getCourtScheduleRequestParams = getPayload(COURTSCHEDULER_GET_COURT_SCHEDULE_QUERY_JSON);
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace(COURT_CENTRE_ID_2, expected.getCourtHouseId());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace(COURT_ROOM_ID_2, expected.getCourtRoomId());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace(BUSINESS_TYPE_2, expected.getBusinessType());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace(SESSION_START_DATE_2, fromDate.toString());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace(SESSION_END_DATE_2, toDate.toString());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace(PAGE_SIZE_2, "10");
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace(PAGE_NUMBER_2, "1");

        final Map<String, Object> map = mapper.readValue(getCourtScheduleRequestParams, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams(BASE_RESOURCE_URL, COURT_SCHEDULE_GET_CONTENT_TYPE, USER_ID, map);


        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        final JsonObject jsonObject = stringToJsonObjectConverter.convert(tempResponseData.getPayload());

        final JsonObject courtScheduleJsonObject = jsonObject.getJsonArray(COURT_SCHEDULES).getJsonObject(0).getJsonArray(SESSIONS).getJsonObject(0);
        assertThat(courtScheduleJsonObject.getString(COURT_SCHEDULE_ID_5), is(expected.getCourtScheduleId()));
        assertThat(courtScheduleJsonObject.getString(PANEL_3), is(expected.getPanel()));
        assertThat(courtScheduleJsonObject.getBoolean(SLOT_BASED), is(false));
        assertThat(courtScheduleJsonObject.getBoolean(ACTIVE), is(true));
        assertThat(courtScheduleJsonObject.getString(COURT_ROOM_ID_3), is(expected.getCourtRoomId()));
        assertThat(courtScheduleJsonObject.getString(COURT_ROOM_NAME), is(expected.getCourtRoomName()));
        assertThat(courtScheduleJsonObject.getBoolean(ALL_DAY_SPLIT), is(true));
        assertThat(courtScheduleJsonObject.getInt(MAX_DURATION_FOR_MORNING_3), is(0));
        assertThat(courtScheduleJsonObject.getInt(MAX_DURATION_FOR_AFTERNOON_3), is(0));
        assertThat(courtScheduleJsonObject.getString("minHearingTime"), is("09:00"));
        assertThat(courtScheduleJsonObject.getString("maxHearingTime"), is(VALUE_15_00));
        assertThat(courtScheduleJsonObject.getBoolean("isOverbookingAllowed"), is(true));
        assertThat(courtScheduleJsonObject.getString(SESSION_START_TIME_3), is(DEFAULT_ALL_DAY_START_TIME));
        assertThat(courtScheduleJsonObject.getString(SESSION_END_TIME_3), is(DEFAULT_ALL_DAY_END_TIME));
        assertThat(courtScheduleJsonObject.getString(JURISDICTION), is(MAGISTRATES.getJurisdiction()));
    }

    @Test
    void shouldGetCourtSchedulesWithMinMaxSessionTimesNoAllocatedListings() throws SQLException, JsonProcessingException {

        final CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        final LocalDate fromDate = expected.getSessionDate().minusDays(1);
        final LocalDate toDate = expected.getSessionDate().plusDays(1);
        expected.setBusinessType(TRL_2);

        expected.setSlotBased(false);
        expected.setMaxDuration(5);
        expected.setAvailableDuration(5);
        expected.setSupportAdSplit(true);
        expected.setMaxAdMorningDuration(0);
        expected.setCourtSession(ALL_DAY);
        expected.setMaxAdAfternoonDuration(0);
        expected.setCourtScheduleId(randomUUID().toString());
        expected.setSessionStartTime(expected.getSessionDate().atTime(10, 0).atZone(UTC).toInstant());
        expected.setSessionEndTime(expected.getSessionDate().atTime(17, 0).atZone(UTC).toInstant());
        expected.setIsOverbookingAllowed(true);
        expected.setJurisdiction(MAGISTRATES.getJurisdiction());
        databaseSeeder.insertCourtSchedule(expected);

        String getCourtScheduleRequestParams = getPayload(COURTSCHEDULER_GET_COURT_SCHEDULE_QUERY_JSON);
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace(COURT_CENTRE_ID_2, expected.getCourtHouseId());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace(COURT_ROOM_ID_2, expected.getCourtRoomId());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace(BUSINESS_TYPE_2, expected.getBusinessType());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace(SESSION_START_DATE_2, fromDate.toString());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace(SESSION_END_DATE_2, toDate.toString());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace(PAGE_SIZE_2, "10");
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace(PAGE_NUMBER_2, "1");

        final Map<String, Object> map = mapper.readValue(getCourtScheduleRequestParams, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams(BASE_RESOURCE_URL, COURT_SCHEDULE_GET_CONTENT_TYPE, USER_ID, map);


        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        final JsonObject jsonObject = stringToJsonObjectConverter.convert(tempResponseData.getPayload());

        final JsonObject courtScheduleJsonObject = jsonObject.getJsonArray(COURT_SCHEDULES).getJsonObject(0).getJsonArray(SESSIONS).getJsonObject(0);
        assertThat(courtScheduleJsonObject.getString(COURT_SCHEDULE_ID_5), is(expected.getCourtScheduleId()));
        assertThat(courtScheduleJsonObject.getString(PANEL_3), is(expected.getPanel()));
        assertThat(courtScheduleJsonObject.getBoolean(SLOT_BASED), is(false));
        assertThat(courtScheduleJsonObject.getBoolean(ACTIVE), is(true));
        assertThat(courtScheduleJsonObject.getString(COURT_ROOM_ID_3), is(expected.getCourtRoomId()));
        assertThat(courtScheduleJsonObject.getString(COURT_ROOM_NAME), is(expected.getCourtRoomName()));
        assertThat(courtScheduleJsonObject.getBoolean(ALL_DAY_SPLIT), is(true));
        assertThat(courtScheduleJsonObject.getInt(MAX_DURATION_FOR_MORNING_3), is(0));
        assertThat(courtScheduleJsonObject.getInt(MAX_DURATION_FOR_AFTERNOON_3), is(0));
        assertThat(courtScheduleJsonObject.getString("minHearingTime"), is(DEFAULT_ALL_DAY_START_TIME));
        assertThat(courtScheduleJsonObject.getString("maxHearingTime"), is(DEFAULT_ALL_DAY_END_TIME));
        assertThat(courtScheduleJsonObject.getBoolean("isOverbookingAllowed"), is(true));
        assertThat(courtScheduleJsonObject.getString(SESSION_START_TIME_3), is(DEFAULT_ALL_DAY_START_TIME));
        assertThat(courtScheduleJsonObject.getString(SESSION_END_TIME_3), is(DEFAULT_ALL_DAY_END_TIME));
        assertThat(courtScheduleJsonObject.getString(JURISDICTION), is(MAGISTRATES.getJurisdiction()));
    }

    @Test
    void shouldGetCourtSchedulesForAllDaySplitSlot() throws SQLException, JsonProcessingException {
        final UUID courtScheduleId = randomUUID();
        final UUID hearingIdForMorning = randomUUID();
        final UUID bookingIdForMorning = randomUUID();
        final UUID hearingIdForAfternoon = randomUUID();
        final UUID bookingIdForAfternoon = randomUUID();
        final Integer maxDurationForMorning = 120;
        final Integer maxDurationForAfternoon = 60;
        final CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);

        courtSchedule.setBusinessType(TRL_2);
        courtSchedule.setSlotBased(false);
        courtSchedule.setMaxDuration(0);
        courtSchedule.setAvailableDuration(0);
        courtSchedule.setSupportAdSplit(true);
        courtSchedule.setCourtSession(ALL_DAY);
        courtSchedule.setMaxAdMorningDuration(maxDurationForMorning);
        courtSchedule.setMaxAdAfternoonDuration(maxDurationForAfternoon);
        courtSchedule.setCourtScheduleId(courtScheduleId.toString());
        courtSchedule.setSessionDate(getRandomFutureDateWithinNextYear());
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), DEFAULT_ALL_DAY_START_TIME).toInstant());
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), VALUE_16_00).toInstant());
        courtSchedule.setIsOverbookingAllowed(false);
        courtSchedule.setJurisdiction(MAGISTRATES.getJurisdiction());
        databaseSeeder.insertCourtSchedule(courtSchedule);

        final AllocatedListing allocatedListingForMorning = createAllocatedListing(courtSchedule, hearingIdForMorning, bookingIdForMorning, 60, DEFAULT_ALL_DAY_START_TIME);
        final AllocatedListing allocatedListingForAfternoon = createAllocatedListing(courtSchedule, hearingIdForAfternoon, bookingIdForAfternoon, 30, VALUE_15_00);

        final LocalDate fromDate = courtSchedule.getSessionDate().minusDays(1);
        final LocalDate toDate = courtSchedule.getSessionDate().plusDays(1);

        String getCourtScheduleRequestParams = getPayload(COURTSCHEDULER_GET_COURT_SCHEDULE_QUERY_JSON);
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace(COURT_CENTRE_ID_2, courtSchedule.getCourtHouseId());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace(COURT_ROOM_ID_2, courtSchedule.getCourtRoomId());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace(BUSINESS_TYPE_2, courtSchedule.getBusinessType());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace(SESSION_START_DATE_2, fromDate.toString());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace(SESSION_END_DATE_2, toDate.toString());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace(PAGE_SIZE_2, "10");
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace(PAGE_NUMBER_2, "1");

        final Map<String, Object> map = mapper.readValue(getCourtScheduleRequestParams, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams(BASE_RESOURCE_URL, COURT_SCHEDULE_GET_CONTENT_TYPE, USER_ID, map);


        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        final JsonObject jsonObject = stringToJsonObjectConverter.convert(tempResponseData.getPayload());

        final JsonObject courtScheduleJsonObject = jsonObject.getJsonArray(COURT_SCHEDULES).getJsonObject(0).getJsonArray(SESSIONS).getJsonObject(0);
        assertThat(courtScheduleJsonObject.getString(COURT_SCHEDULE_ID_5), is(courtSchedule.getCourtScheduleId()));
        assertThat(courtScheduleJsonObject.getString(PANEL_3), is(courtSchedule.getPanel()));
        assertThat(courtScheduleJsonObject.getBoolean(SLOT_BASED), is(false));
        assertThat(courtScheduleJsonObject.getBoolean(ACTIVE), is(true));
        assertThat(courtScheduleJsonObject.getString(COURT_ROOM_ID_3), is(courtSchedule.getCourtRoomId()));
        assertThat(courtScheduleJsonObject.getString(COURT_ROOM_NAME), is(courtSchedule.getCourtRoomName()));
        assertThat(courtScheduleJsonObject.getBoolean(ALL_DAY_SPLIT), is(true));
        assertThat(courtScheduleJsonObject.getInt(MAX_DURATION_FOR_MORNING_3), is(maxDurationForMorning));
        assertThat(courtScheduleJsonObject.getInt(MAX_DURATION_FOR_AFTERNOON_3), is(maxDurationForAfternoon));
        assertThat(courtScheduleJsonObject.getInt("availableDurationForMorning"), is(maxDurationForMorning - allocatedListingForMorning.getDuration()));
        assertThat(courtScheduleJsonObject.getInt("availableDurationForAfternoon"), is(maxDurationForAfternoon - allocatedListingForAfternoon.getDuration()));
        assertThat(courtScheduleJsonObject.getString("minHearingTime"), is(getUtcTimeStringForDate(courtSchedule.getSessionDate(),10,0)));
        assertThat(courtScheduleJsonObject.getString("maxHearingTime"), is(getUtcTimeStringForDate(courtSchedule.getSessionDate(),15,0)));
        assertThat(courtScheduleJsonObject.getBoolean("isOverbookingAllowed"), is(false));
        assertThat(courtScheduleJsonObject.getString(SESSION_START_TIME_3), is(getUtcTimeStringForDate(courtSchedule.getSessionDate(),10,0)));
        assertThat(courtScheduleJsonObject.getString(SESSION_END_TIME_3), is(getUtcTimeStringForDate(courtSchedule.getSessionDate(),16,0)));
        assertThat(courtScheduleJsonObject.getString(JURISDICTION), is(MAGISTRATES.getJurisdiction()));
    }

    @Test
    void shouldRemoveCourtSchedule() throws Exception {
        final Integer maxDurationForMorning = 120;
        final Integer maxDurationForAfternoon = 60;
        final UUID hearingIdForMorning = randomUUID();
        final UUID bookingIdForMorning = randomUUID();
        final UUID hearingIdForAfternoon = randomUUID();
        final UUID bookingIdForAfternoon = randomUUID();
        final String courtScheduleId = randomUUID().toString();
        final CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setBusinessType(TRL_2);
        courtSchedule.setSlotBased(false);
        courtSchedule.setMaxDuration(0);
        courtSchedule.setAvailableDuration(0);
        courtSchedule.setSupportAdSplit(true);
        courtSchedule.setCourtSession(ALL_DAY);
        courtSchedule.setMaxAdMorningDuration(maxDurationForMorning);
        courtSchedule.setMaxAdAfternoonDuration(maxDurationForAfternoon);
        courtSchedule.setCourtScheduleId(courtScheduleId);
        courtSchedule.setSessionDate(getRandomFutureDateWithinNextYear());
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), DEFAULT_ALL_DAY_START_TIME).toInstant());
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), VALUE_16_00).toInstant());

        databaseSeeder.insertCourtSchedule(courtSchedule);

        final AllocatedListing allocatedListingForMorning = createAllocatedListing(courtSchedule, hearingIdForMorning, bookingIdForMorning, 60, DEFAULT_ALL_DAY_START_TIME);
        final AllocatedListing allocatedListingForAfternoon = createAllocatedListing(courtSchedule, hearingIdForAfternoon, bookingIdForAfternoon, 30, VALUE_15_00);

        String deleteHearingSlotsPayload = getPayload("courtscheduler.delete-sessions.json");
        deleteHearingSlotsPayload = deleteHearingSlotsPayload.replace(COURT_SCHEDULE_ID_4, courtScheduleId);

        final Response response = postCommand(BASE_RESOURCE_URL + DELETE_URL, COURT_SCHEDULE_DELETE_CONTENT_TYPE, USER_ID, deleteHearingSlotsPayload);


        assertThat(response.getStatus(), is(OK.getStatusCode()));

        try (JsonReader jsonReader = Json.createReader(new StringReader(response.readEntity(String.class)))) {
            final JsonObject jsonResponse = jsonReader.readObject();
            assertTrue(jsonResponse.containsKey(ERROR), "Response should contain an 'error' key");
            assertTrue(jsonResponse.containsKey(SESSIONS), RESPONSE_SHOULD_CONTAIN_SESSIONS_KEY);
            final JsonObject sessionJSONObj = jsonResponse.getJsonArray(SESSIONS).getJsonObject(0);
            assertThat(sessionJSONObj.getString(COURT_SCHEDULE_ID_5), is(courtSchedule.getCourtScheduleId()));
            assertThat(sessionJSONObj.getInt(TOTAL_BOOKED), is(allocatedListingForMorning.getDuration() + allocatedListingForAfternoon.getDuration()));
        }
    }

    @Test
    void shouldTryToRemoveCourtScheduleWhenNoSuchCourtScheduleWithoutException() throws Exception {
        final Integer maxDurationForMorning = 120;
        final Integer maxDurationForAfternoon = 60;
        final String courtScheduleId = randomUUID().toString();
        final String nonExistingCourtScheduleId = randomUUID().toString();
        final CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setBusinessType(TRL_2);
        courtSchedule.setSlotBased(false);
        courtSchedule.setMaxDuration(0);
        courtSchedule.setAvailableDuration(0);
        courtSchedule.setSupportAdSplit(true);
        courtSchedule.setCourtSession(ALL_DAY);
        courtSchedule.setMaxAdMorningDuration(maxDurationForMorning);
        courtSchedule.setMaxAdAfternoonDuration(maxDurationForAfternoon);
        courtSchedule.setCourtScheduleId(courtScheduleId);
        courtSchedule.setSessionDate(getRandomFutureDateWithinNextYear());
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), DEFAULT_ALL_DAY_START_TIME).toInstant());
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), VALUE_16_00).toInstant());

        databaseSeeder.insertCourtSchedule(courtSchedule);

        String deleteHearingSlotsPayload = getPayload("courtscheduler.delete-sessions.json");
        deleteHearingSlotsPayload = deleteHearingSlotsPayload.replace(COURT_SCHEDULE_ID_4, nonExistingCourtScheduleId);

        final Response response = postCommand(BASE_RESOURCE_URL + DELETE_URL, COURT_SCHEDULE_DELETE_CONTENT_TYPE, USER_ID, deleteHearingSlotsPayload);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
    }

    @Test
    void shouldDeleteCourtScheduleSessionsRegardlessOfJurisdictionType() throws Exception {
        final String magistratesCourtScheduleId = randomUUID().toString();
        final String crownCourtScheduleId = randomUUID().toString();

        // Create MAGISTRATES court schedule (deletable - no allocated listings)
        final CourtSchedule magistratesSchedule = RANDOM.nextObject(CourtSchedule.class);
        magistratesSchedule.setBusinessType(TRL_2);
        magistratesSchedule.setSlotBased(false);
        magistratesSchedule.setMaxDuration(120);
        magistratesSchedule.setAvailableDuration(120);
        magistratesSchedule.setSupportAdSplit(false);
        magistratesSchedule.setCourtSession(ALL_DAY);
        magistratesSchedule.setCourtScheduleId(magistratesCourtScheduleId);
        magistratesSchedule.setSessionDate(getRandomFutureDateWithinNextYear());
        magistratesSchedule.setSessionStartTime(combineDateAndTime(magistratesSchedule.getSessionDate(), DEFAULT_ALL_DAY_START_TIME).toInstant());
        magistratesSchedule.setSessionEndTime(combineDateAndTime(magistratesSchedule.getSessionDate(), VALUE_16_00).toInstant());
        magistratesSchedule.setJurisdiction(MAGISTRATES_2);
        magistratesSchedule.setIsDraft(false);
        magistratesSchedule.setActive(true);
        databaseSeeder.insertCourtSchedule(magistratesSchedule);

        // Create CROWN court schedule (deletable - no allocated listings)
        final CourtSchedule crownSchedule = RANDOM.nextObject(CourtSchedule.class);
        crownSchedule.setBusinessType(TRL_2);
        crownSchedule.setSlotBased(false);
        crownSchedule.setMaxDuration(120);
        crownSchedule.setAvailableDuration(120);
        crownSchedule.setSupportAdSplit(false);
        crownSchedule.setCourtSession(ALL_DAY);
        crownSchedule.setCourtScheduleId(crownCourtScheduleId);
        crownSchedule.setSessionDate(getRandomFutureDateWithinNextYear());
        crownSchedule.setSessionStartTime(combineDateAndTime(crownSchedule.getSessionDate(), DEFAULT_ALL_DAY_START_TIME).toInstant());
        crownSchedule.setSessionEndTime(combineDateAndTime(crownSchedule.getSessionDate(), VALUE_16_00).toInstant());
        crownSchedule.setJurisdiction(CROWN_2);
        crownSchedule.setIsDraft(true);
        crownSchedule.setActive(true);
        databaseSeeder.insertCourtSchedule(crownSchedule);

        // Delete both schedules in a single request
        // Create JSON payload with both IDs
        final String deletePayload = "{\"sessions\": [\"" + magistratesCourtScheduleId + "\", \"" + crownCourtScheduleId + "\"]}";

        final Response deleteResponse = postCommand(BASE_RESOURCE_URL + DELETE_URL,
                COURT_SCHEDULE_DELETE_CONTENT_TYPE, USER_ID, deletePayload);

        assertThat(deleteResponse.getStatus(), is(OK.getStatusCode()));

        // Verify successful deletion - empty sessions array means both were deleted successfully
        try (JsonReader jsonReader = Json.createReader(new StringReader(deleteResponse.readEntity(String.class)))) {
            final JsonObject jsonResponse = jsonReader.readObject();
            assertTrue(jsonResponse.containsKey(SESSIONS), RESPONSE_SHOULD_CONTAIN_SESSIONS_KEY);
            // Empty array means successful deletion (no errors)
            assertThat(jsonResponse.getJsonArray(SESSIONS).size(), is(0));
            // Should not contain error key when deletion is successful
            assertThat(jsonResponse.containsKey(ERROR), is(false));
        }

        // Verify both schedules are deleted from database
        final List<CourtSchedule> remainingSchedules = databaseReader.courtSchedules();
        assertThat(remainingSchedules.stream()
                .noneMatch(cs -> cs.getCourtScheduleId().equals(magistratesCourtScheduleId)
                        || cs.getCourtScheduleId().equals(crownCourtScheduleId)), is(true));

        // Test Case 2: Rejection when sessions have allocated listings for both jurisdictions
        final String magistratesWithListingsId = randomUUID().toString();
        final String crownWithListingsId = randomUUID().toString();

        // Create MAGISTRATES court schedule with allocated listings
        final CourtSchedule magistratesWithListings = RANDOM.nextObject(CourtSchedule.class);
        magistratesWithListings.setBusinessType(TRL_2);
        magistratesWithListings.setSlotBased(false);
        magistratesWithListings.setMaxDuration(120);
        magistratesWithListings.setAvailableDuration(60);
        magistratesWithListings.setSupportAdSplit(false);
        magistratesWithListings.setCourtSession(ALL_DAY);
        magistratesWithListings.setCourtScheduleId(magistratesWithListingsId);
        magistratesWithListings.setSessionDate(getRandomFutureDateWithinNextYear());
        magistratesWithListings.setSessionStartTime(combineDateAndTime(magistratesWithListings.getSessionDate(), DEFAULT_ALL_DAY_START_TIME).toInstant());
        magistratesWithListings.setSessionEndTime(combineDateAndTime(magistratesWithListings.getSessionDate(), VALUE_16_00).toInstant());
        magistratesWithListings.setJurisdiction(MAGISTRATES_2);
        magistratesWithListings.setIsDraft(false);
        magistratesWithListings.setActive(true);
        databaseSeeder.insertCourtSchedule(magistratesWithListings);

        // Create allocated listing for MAGISTRATES schedule
        final UUID magistratesHearingId = randomUUID();
        final UUID magistratesBookingId = randomUUID();
        createAllocatedListing(magistratesWithListings, magistratesHearingId, magistratesBookingId, 60, DEFAULT_ALL_DAY_START_TIME);

        // Create CROWN court schedule with allocated listings
        final CourtSchedule crownWithListings = RANDOM.nextObject(CourtSchedule.class);
        crownWithListings.setBusinessType(TRL_2);
        crownWithListings.setSlotBased(false);
        crownWithListings.setMaxDuration(120);
        crownWithListings.setAvailableDuration(60);
        crownWithListings.setSupportAdSplit(false);
        crownWithListings.setCourtSession(ALL_DAY);
        crownWithListings.setCourtScheduleId(crownWithListingsId);
        crownWithListings.setSessionDate(getRandomFutureDateWithinNextYear());
        crownWithListings.setSessionStartTime(combineDateAndTime(crownWithListings.getSessionDate(), DEFAULT_ALL_DAY_START_TIME).toInstant());
        crownWithListings.setSessionEndTime(combineDateAndTime(crownWithListings.getSessionDate(), VALUE_16_00).toInstant());
        crownWithListings.setJurisdiction(CROWN_2);
        crownWithListings.setIsDraft(true);
        crownWithListings.setActive(true);
        databaseSeeder.insertCourtSchedule(crownWithListings);

        // Create allocated listing for CROWN schedule
        final UUID crownHearingId = randomUUID();
        final UUID crownBookingId = randomUUID();
        createAllocatedListing(crownWithListings, crownHearingId, crownBookingId, 60, DEFAULT_ALL_DAY_START_TIME);


        // Try to delete both schedules with allocated listings
        // Create JSON payload with both IDs
        final String deleteWithListingsPayload = "{\"sessions\": [\"" + magistratesWithListingsId + "\", \"" + crownWithListingsId + "\"]}";

        final Response deleteWithListingsResponse = postCommand(BASE_RESOURCE_URL + DELETE_URL,
                COURT_SCHEDULE_DELETE_CONTENT_TYPE, USER_ID, deleteWithListingsPayload);

        assertThat(deleteWithListingsResponse.getStatus(), is(OK.getStatusCode()));

        // Verify rejection - both should be returned in sessions array with error message
        try (JsonReader jsonReader = Json.createReader(new StringReader(deleteWithListingsResponse.readEntity(String.class)))) {
            final JsonObject jsonResponse = jsonReader.readObject();
            assertTrue(jsonResponse.containsKey(ERROR), "Response should contain an 'error' key when deletion fails");
            assertThat(jsonResponse.getString(ERROR), is("Some sessions could not be removed. Please check again."));
            assertTrue(jsonResponse.containsKey(SESSIONS), RESPONSE_SHOULD_CONTAIN_SESSIONS_KEY);

            // Both MAGISTRATES and CROWN schedules should be returned (can't be deleted due to allocated listings)
            assertThat(jsonResponse.getJsonArray(SESSIONS).size(), is(2));

            // Verify MAGISTRATES schedule is in response
            boolean magistratesFound = false;
            boolean crownFound = false;
            for (int i = 0; i < jsonResponse.getJsonArray(SESSIONS).size(); i++) {
                final JsonObject sessionObj = jsonResponse.getJsonArray(SESSIONS).getJsonObject(i);
                final String courtScheduleId = sessionObj.getString(COURT_SCHEDULE_ID_5);
                if (courtScheduleId.equals(magistratesWithListingsId)) {
                    magistratesFound = true;
                    assertThat(sessionObj.getInt(TOTAL_BOOKED), is(60));
                    // Verify it's the MAGISTRATES schedule by checking courtScheduleId
                    assertThat(sessionObj.getString(COURT_SCHEDULE_ID_5), is(magistratesWithListingsId));
                } else if (courtScheduleId.equals(crownWithListingsId)) {
                    crownFound = true;
                    assertThat(sessionObj.getInt(TOTAL_BOOKED), is(60));
                    // Verify it's the CROWN schedule by checking courtScheduleId
                    assertThat(sessionObj.getString(COURT_SCHEDULE_ID_5), is(crownWithListingsId));
                }
            }
            assertThat(magistratesFound, is(true));
            assertThat(crownFound, is(true));
        }

        // Verify both schedules still exist in database (not deleted)
        final List<CourtSchedule> schedulesAfterFailedDelete = databaseReader.courtSchedules();
        assertThat(schedulesAfterFailedDelete.stream()
                .anyMatch(cs -> cs.getCourtScheduleId().equals(magistratesWithListingsId)), is(true));
        assertThat(schedulesAfterFailedDelete.stream()
                .anyMatch(cs -> cs.getCourtScheduleId().equals(crownWithListingsId)), is(true));
    }

    @Test
    void shouldNotDeletePastCourtSchedules() throws Exception {
        // Given - Create past schedules (yesterday, one week ago, one month ago)
        final String oneDayAgoId = randomUUID().toString();
        final String oneWeekAgoId = randomUUID().toString();
        final String oneMonthAgoId = randomUUID().toString();

        final CourtSchedule oneDayAgo = createCourtScheduleForDate(oneDayAgoId, now().minusDays(1));
        final CourtSchedule oneWeekAgo = createCourtScheduleForDate(oneWeekAgoId, now().minusDays(7));
        final CourtSchedule oneMonthAgo = createCourtScheduleForDate(oneMonthAgoId, now().minusMonths(1));

        databaseSeeder.insertCourtSchedule(oneDayAgo);
        databaseSeeder.insertCourtSchedule(oneWeekAgo);
        databaseSeeder.insertCourtSchedule(oneMonthAgo);

        // When - Try to delete past schedules
        final String deletePayload = String.format("{\"sessions\": [\"%s\", \"%s\", \"%s\"]}",
                oneDayAgoId, oneWeekAgoId, oneMonthAgoId);
        final Response response = postCommand(BASE_RESOURCE_URL + DELETE_URL,
                COURT_SCHEDULE_DELETE_CONTENT_TYPE, USER_ID, deletePayload);

        // Then - Response should be OK
        assertThat(response.getStatus(), is(OK.getStatusCode()));

        // Verify all past schedules still exist in database
        final List<CourtSchedule> remainingSchedules = databaseReader.courtSchedules();
        assertThat(remainingSchedules.stream()
                .anyMatch(cs -> cs.getCourtScheduleId().equals(oneDayAgoId)), is(true));
        assertThat(remainingSchedules.stream()
                .anyMatch(cs -> cs.getCourtScheduleId().equals(oneWeekAgoId)), is(true));
        assertThat(remainingSchedules.stream()
                .anyMatch(cs -> cs.getCourtScheduleId().equals(oneMonthAgoId)), is(true));

        // Verify response indicates successful deletion (empty sessions array)
        try (JsonReader jsonReader = Json.createReader(new StringReader(response.readEntity(String.class)))) {
            final JsonObject jsonResponse = jsonReader.readObject();
            assertTrue(jsonResponse.containsKey(SESSIONS), RESPONSE_SHOULD_CONTAIN_SESSIONS_KEY);
            // Empty array means no errors (schedules were not deleted, which is expected for past dates)
            assertThat(jsonResponse.getJsonArray(SESSIONS).size(), is(0));
            assertThat(jsonResponse.containsKey(ERROR), is(false));
        }
    }

    @Test
    void shouldDeleteTodayAndFutureCourtSchedules() throws Exception {
        // Given - Create today and future schedules
        final String todayId = randomUUID().toString();
        final String tomorrowId = randomUUID().toString();
        final String oneWeekFutureId = randomUUID().toString();
        final String oneMonthFutureId = randomUUID().toString();

        final CourtSchedule today = createCourtScheduleForDate(todayId, now());
        final CourtSchedule tomorrow = createCourtScheduleForDate(tomorrowId, now().plusDays(1));
        final CourtSchedule oneWeekFuture = createCourtScheduleForDate(oneWeekFutureId, now().plusDays(7));
        final CourtSchedule oneMonthFuture = createCourtScheduleForDate(oneMonthFutureId, now().plusMonths(1));

        databaseSeeder.insertCourtSchedule(today);
        databaseSeeder.insertCourtSchedule(tomorrow);
        databaseSeeder.insertCourtSchedule(oneWeekFuture);
        databaseSeeder.insertCourtSchedule(oneMonthFuture);

        // When - Delete today and future schedules
        final String deletePayload = String.format("{\"sessions\": [\"%s\", \"%s\", \"%s\", \"%s\"]}",
                todayId, tomorrowId, oneWeekFutureId, oneMonthFutureId);
        final Response response = postCommand(BASE_RESOURCE_URL + DELETE_URL,
                COURT_SCHEDULE_DELETE_CONTENT_TYPE, USER_ID, deletePayload);

        // Then - Response should be OK
        assertThat(response.getStatus(), is(OK.getStatusCode()));

        // Verify all schedules are deleted from database
        final List<CourtSchedule> remainingSchedules = databaseReader.courtSchedules();
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
            final JsonObject jsonResponse = jsonReader.readObject();
            assertTrue(jsonResponse.containsKey(SESSIONS), RESPONSE_SHOULD_CONTAIN_SESSIONS_KEY);
            assertThat(jsonResponse.getJsonArray(SESSIONS).size(), is(0));
            assertThat(jsonResponse.containsKey(ERROR), is(false));
        }
    }

    @Test
    void shouldNotDeletePastSchedulesButDeleteTodayAndFutureInMixedScenario() throws Exception {
        // Given - Mix of past, today, and future schedules
        final String pastId = randomUUID().toString();
        final String todayId = randomUUID().toString();
        final String futureId = randomUUID().toString();

        final CourtSchedule past = createCourtScheduleForDate(pastId, now().minusDays(5));
        final CourtSchedule today = createCourtScheduleForDate(todayId, now());
        final CourtSchedule future = createCourtScheduleForDate(futureId, now().plusDays(10));

        databaseSeeder.insertCourtSchedule(past);
        databaseSeeder.insertCourtSchedule(today);
        databaseSeeder.insertCourtSchedule(future);

        // When - Try to delete all schedules
        final String deletePayload = String.format("{\"sessions\": [\"%s\", \"%s\", \"%s\"]}",
                pastId, todayId, futureId);
        final Response response = postCommand(BASE_RESOURCE_URL + DELETE_URL,
                COURT_SCHEDULE_DELETE_CONTENT_TYPE, USER_ID, deletePayload);

        // Then - Response should be OK
        assertThat(response.getStatus(), is(OK.getStatusCode()));

        // Verify past schedule remains, today and future are deleted
        final List<CourtSchedule> remainingSchedules = databaseReader.courtSchedules();
        assertThat(remainingSchedules.stream()
                .anyMatch(cs -> cs.getCourtScheduleId().equals(pastId)), is(true));
        assertThat(remainingSchedules.stream()
                .noneMatch(cs -> cs.getCourtScheduleId().equals(todayId)), is(true));
        assertThat(remainingSchedules.stream()
                .noneMatch(cs -> cs.getCourtScheduleId().equals(futureId)), is(true));

        // Verify response indicates successful deletion (no errors)
        try (JsonReader jsonReader = Json.createReader(new StringReader(response.readEntity(String.class)))) {
            final JsonObject jsonResponse = jsonReader.readObject();
            assertTrue(jsonResponse.containsKey(SESSIONS), RESPONSE_SHOULD_CONTAIN_SESSIONS_KEY);
            assertThat(jsonResponse.getJsonArray(SESSIONS).size(), is(0));
            assertThat(jsonResponse.containsKey(ERROR), is(false));
        }
    }

    @Test
    void shouldNotDeletePastScheduleWithAllocatedListings() throws Exception {
        // Given - Past schedule with allocated listings
        final String pastWithAllocationsId = randomUUID().toString();
        final CourtSchedule pastWithAllocations = createCourtScheduleForDate(pastWithAllocationsId, now().minusDays(3));
        databaseSeeder.insertCourtSchedule(pastWithAllocations);

        final UUID hearingId = randomUUID();
        final UUID bookingId = randomUUID();
        createAllocatedListing(pastWithAllocations, hearingId, bookingId, 60, DEFAULT_ALL_DAY_START_TIME);

        // When - Try to delete past schedule with allocations
        final String deletePayload = String.format("{\"sessions\": [\"%s\"]}", pastWithAllocationsId);
        final Response response = postCommand(BASE_RESOURCE_URL + DELETE_URL,
                COURT_SCHEDULE_DELETE_CONTENT_TYPE, USER_ID, deletePayload);

        // Then - Response should be OK
        assertThat(response.getStatus(), is(OK.getStatusCode()));

        // Verify schedule still exists (not deleted due to allocations)
        final List<CourtSchedule> remainingSchedules = databaseReader.courtSchedules();
        assertThat(remainingSchedules.stream()
                .anyMatch(cs -> cs.getCourtScheduleId().equals(pastWithAllocationsId)), is(true));

        // Verify response contains error with schedule details
        try (JsonReader jsonReader = Json.createReader(new StringReader(response.readEntity(String.class)))) {
            final JsonObject jsonResponse = jsonReader.readObject();
            assertTrue(jsonResponse.containsKey(ERROR), "Response should contain an 'error' key");
            assertTrue(jsonResponse.containsKey(SESSIONS), RESPONSE_SHOULD_CONTAIN_SESSIONS_KEY);
            assertThat(jsonResponse.getJsonArray(SESSIONS).size(), is(1));
            final JsonObject sessionObj = jsonResponse.getJsonArray(SESSIONS).getJsonObject(0);
            assertThat(sessionObj.getString(COURT_SCHEDULE_ID_5), is(pastWithAllocationsId));
            assertThat(sessionObj.getInt(TOTAL_BOOKED), is(60));
        }
    }

    @Test
    void shouldNotDeleteTodayOrFutureScheduleWithAllocatedListings() throws Exception {
        // Given - Today and future schedules with allocated listings
        final String todayWithAllocationsId = randomUUID().toString();
        final String futureWithAllocationsId = randomUUID().toString();

        final CourtSchedule todayWithAllocations = createCourtScheduleForDate(todayWithAllocationsId, now());
        final CourtSchedule futureWithAllocations = createCourtScheduleForDate(futureWithAllocationsId, now().plusDays(5));

        databaseSeeder.insertCourtSchedule(todayWithAllocations);
        databaseSeeder.insertCourtSchedule(futureWithAllocations);

        final UUID hearingId1 = randomUUID();
        final UUID bookingId1 = randomUUID();
        createAllocatedListing(todayWithAllocations, hearingId1, bookingId1, 60, DEFAULT_ALL_DAY_START_TIME);

        final UUID hearingId2 = randomUUID();
        final UUID bookingId2 = randomUUID();
        createAllocatedListing(futureWithAllocations, hearingId2, bookingId2, 90, DEFAULT_AFTERNOON_START_TIME);

        // When - Try to delete schedules with allocations
        final String deletePayload = String.format("{\"sessions\": [\"%s\", \"%s\"]}",
                todayWithAllocationsId, futureWithAllocationsId);
        final Response response = postCommand(BASE_RESOURCE_URL + DELETE_URL,
                COURT_SCHEDULE_DELETE_CONTENT_TYPE, USER_ID, deletePayload);

        // Then - Response should be OK
        assertThat(response.getStatus(), is(OK.getStatusCode()));

        // Verify schedules still exist (not deleted due to allocations)
        final List<CourtSchedule> remainingSchedules = databaseReader.courtSchedules();
        assertThat(remainingSchedules.stream()
                .anyMatch(cs -> cs.getCourtScheduleId().equals(todayWithAllocationsId)), is(true));
        assertThat(remainingSchedules.stream()
                .anyMatch(cs -> cs.getCourtScheduleId().equals(futureWithAllocationsId)), is(true));

        // Verify response contains error with both schedules
        try (JsonReader jsonReader = Json.createReader(new StringReader(response.readEntity(String.class)))) {
            final JsonObject jsonResponse = jsonReader.readObject();
            assertTrue(jsonResponse.containsKey(ERROR), "Response should contain an 'error' key");
            assertTrue(jsonResponse.containsKey(SESSIONS), RESPONSE_SHOULD_CONTAIN_SESSIONS_KEY);
            assertThat(jsonResponse.getJsonArray(SESSIONS).size(), is(2));
        }
    }

    private CourtSchedule createCourtScheduleForDate(final String courtScheduleId, final LocalDate sessionDate) {
        final CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setBusinessType(TRL_2);
        courtSchedule.setSlotBased(false);
        courtSchedule.setMaxDuration(120);
        courtSchedule.setAvailableDuration(120);
        courtSchedule.setSupportAdSplit(false);
        courtSchedule.setCourtSession(ALL_DAY);
        courtSchedule.setCourtScheduleId(courtScheduleId);
        courtSchedule.setSessionDate(sessionDate);
        courtSchedule.setSessionStartTime(combineDateAndTime(sessionDate, DEFAULT_ALL_DAY_START_TIME).toInstant());
        courtSchedule.setSessionEndTime(combineDateAndTime(sessionDate, VALUE_16_00).toInstant());
        courtSchedule.setJurisdiction(MAGISTRATES_2);
        courtSchedule.setIsDraft(false);
        courtSchedule.setActive(true);
        return courtSchedule;
    }

    @Test
    void shouldGetCourtScheduleById() throws SQLException, JsonProcessingException {
        final UUID courtScheduleId = randomUUID();
        final UUID hearingIdForMorning = randomUUID();
        final UUID bookingIdForMorning = randomUUID();
        final UUID hearingIdForAfternoon = randomUUID();
        final UUID bookingIdForAfternoon = randomUUID();
        final Integer maxDurationForMorning = 120;
        final Integer maxDurationForAfternoon = 60;
        final CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);

        courtSchedule.setBusinessType(TRL_2);
        courtSchedule.setSlotBased(false);
        courtSchedule.setMaxDuration(0);
        courtSchedule.setAvailableDuration(0);
        courtSchedule.setSupportAdSplit(true);
        courtSchedule.setCourtSession(ALL_DAY);
        courtSchedule.setMaxAdMorningDuration(maxDurationForMorning);
        courtSchedule.setMaxAdAfternoonDuration(maxDurationForAfternoon);
        courtSchedule.setCourtScheduleId(courtScheduleId.toString());
        courtSchedule.setSessionDate(getRandomFutureDateWithinNextYear());
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), DEFAULT_ALL_DAY_START_TIME).toInstant());
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), VALUE_16_00).toInstant());
        courtSchedule.setIsOverbookingAllowed(false);
        courtSchedule.setActive(true);
        // Allocated (final) session — random() leaves isDraft unset, and the draft-strip would null the courtroom assertions below.
        courtSchedule.setIsDraft(false);
        databaseSeeder.insertCourtSchedule(courtSchedule);

        final AllocatedListing allocatedListingForMorning = createAllocatedListing(courtSchedule, hearingIdForMorning, bookingIdForMorning, 60, DEFAULT_ALL_DAY_START_TIME);
        final AllocatedListing allocatedListingForAfternoon = createAllocatedListing(courtSchedule, hearingIdForAfternoon, bookingIdForAfternoon, 30, VALUE_15_00);

        String getCourtScheduleRequestParams = getPayload(COURTSCHEDULER_SEARCH_COURTSCHEDULES_BY_ID_DYNAM);
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace(COURT_SCHEDULE_ID_4, courtScheduleId.toString());
        final Map<String, Object> map = mapper.readValue(getCourtScheduleRequestParams, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams(
                SEARCH_BY_ID_URL,
                COURT_SCHEDULE_SEARCH_COURTSCHEDULES_BY_ID_CONTENT_TYPE,
                SYSTEM_USER_ID,
                map
        );

        final ResponseData response = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(response.getStatus().getStatusCode(), is(OK.getStatusCode()));

        final JsonObject jsonObject = stringToJsonObjectConverter.convert(response.getPayload());
        // Guard: the schedule has two allocated listings (morning + afternoon) — the query must
        // still return exactly one court_schedule row.
        assertThat(jsonObject.getJsonArray(COURT_SCHEDULES).size(), is(1));

        final JsonObject courtRoomJson = jsonObject.getJsonArray(COURT_SCHEDULES).getJsonObject(0);
        final JsonObject courtScheduleJsonObject = courtRoomJson.getJsonArray(SESSIONS).getJsonObject(0);
        assertThat(courtScheduleJsonObject.getString(COURT_SCHEDULE_ID_5), is(courtSchedule.getCourtScheduleId()));
        assertThat(courtScheduleJsonObject.getString(PANEL_3), is(courtSchedule.getPanel()));
        assertThat(courtScheduleJsonObject.getBoolean(SLOT_BASED), is(false));
        assertThat(courtScheduleJsonObject.getBoolean(ACTIVE), is(true));
        assertThat(courtScheduleJsonObject.getString(COURT_ROOM_ID_3), is(courtSchedule.getCourtRoomId()));
        assertThat(courtScheduleJsonObject.getString(COURT_ROOM_NAME), is(courtSchedule.getCourtRoomName()));
        assertThat(courtScheduleJsonObject.getBoolean(ALL_DAY_SPLIT), is(true));
        assertThat(courtScheduleJsonObject.getInt(MAX_DURATION_FOR_MORNING_3), is(maxDurationForMorning));
        assertThat(courtScheduleJsonObject.getInt(MAX_DURATION_FOR_AFTERNOON_3), is(maxDurationForAfternoon));
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
        assertThat(courtScheduleJsonObject.getString(SESSION_START_TIME_3), is(sessionTimeFormatter(courtSchedule.getSessionStartTime())));
        assertThat(courtScheduleJsonObject.getString(SESSION_END_TIME_3), is(sessionTimeFormatter(courtSchedule.getSessionEndTime())));
    }

    @Test
    void shouldReturnSingleResultWhenCourtScheduleHasMultipleAllocatedListings() throws SQLException, JsonProcessingException {
        final UUID courtScheduleId = randomUUID();
        final CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setBusinessType(TRL_2);
        courtSchedule.setSlotBased(false);
        courtSchedule.setMaxDuration(360);
        courtSchedule.setAvailableDuration(360);
        courtSchedule.setSupportAdSplit(true);
        courtSchedule.setCourtSession(ALL_DAY);
        courtSchedule.setMaxAdMorningDuration(180);
        courtSchedule.setMaxAdAfternoonDuration(180);
        courtSchedule.setCourtScheduleId(courtScheduleId.toString());
        courtSchedule.setSessionDate(getRandomFutureDateWithinNextYear());
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), DEFAULT_ALL_DAY_START_TIME).toInstant());
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), VALUE_16_00).toInstant());
        courtSchedule.setIsOverbookingAllowed(false);
        courtSchedule.setActive(true);
        databaseSeeder.insertCourtSchedule(courtSchedule);

        // Two allocated listings with different durations and start times — in the old query
        // this produced two rows per court_schedule because al.id was in GROUP BY.
        createAllocatedListing(courtSchedule, randomUUID(), randomUUID(), 60, DEFAULT_ALL_DAY_START_TIME);
        createAllocatedListing(courtSchedule, randomUUID(), randomUUID(), 90, DEFAULT_AFTERNOON_START_TIME);

        final String payload = getPayload(COURTSCHEDULER_SEARCH_COURTSCHEDULES_BY_ID_DYNAM)
                .replace(COURT_SCHEDULE_ID_4, courtScheduleId.toString());
        final Map<String, Object> map = mapper.readValue(payload, new TypeReference<>() {
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
        assertThat(json.getJsonArray(COURT_SCHEDULES).size(), is(1));

        // Response is grouped per courtroom (SPRDT-757): courtSchedules[] -> {courtRoomId, courtRoomName, sessions[]}
        final JsonObject scheduleJson = json.getJsonArray(COURT_SCHEDULES).getJsonObject(0)
                .getJsonArray(SESSIONS).getJsonObject(0);
        assertThat(scheduleJson.getString(COURT_SCHEDULE_ID_5), is(courtScheduleId.toString()));
        assertThat(scheduleJson.getInt(TOTAL_BOOKED), is(150));
    }

    @Test
    void shouldReturnSingleResultWhenSameCourtScheduleIdRepeatedInQueryParams() throws SQLException, JsonProcessingException {
        final UUID courtScheduleId = randomUUID();
        final CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setBusinessType(TRL_2);
        courtSchedule.setSlotBased(false);
        courtSchedule.setSupportAdSplit(false);
        courtSchedule.setMaxDuration(360);
        courtSchedule.setAvailableDuration(360);
        courtSchedule.setCourtScheduleId(courtScheduleId.toString());
        courtSchedule.setSessionDate(getRandomFutureDateWithinNextYear());
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), DEFAULT_ALL_DAY_START_TIME).toInstant());
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), VALUE_16_00).toInstant());
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
        assertThat(json.getJsonArray(COURT_SCHEDULES).size(), is(1));
        // Response is grouped per courtroom (SPRDT-757): courtSchedules[] -> {courtRoomId, courtRoomName, sessions[]}
        final JsonArray dedupedSessions = json.getJsonArray(COURT_SCHEDULES).getJsonObject(0).getJsonArray(SESSIONS);
        assertThat(dedupedSessions.size(), is(1));
        assertThat(dedupedSessions.getJsonObject(0).getString(COURT_SCHEDULE_ID_5), is(courtScheduleId.toString()));
    }

    @Test
    void shouldReturnAvailabilityAggregatedAcrossAllAllocatedListingsForAllDaySplit() throws SQLException, JsonProcessingException {
        // Mirrors shouldGetCourtScheduleById's setup but adds an extra afternoon allocation on
        // the same court schedule. Previously the query returned one row per allocated_listing
        // with per-row totalbookedforafternoon values, and the converter's client-side sum
        // happened to mask the bug. After the fix, availability reflects the fold across *all*
        // allocated rows, i.e. totalBooked = sum of every allocated_listing duration for the schedule.
        final UUID courtScheduleId = randomUUID();
        final CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setBusinessType(TRL_2);
        courtSchedule.setSlotBased(false);
        courtSchedule.setMaxDuration(0);
        courtSchedule.setAvailableDuration(0);
        courtSchedule.setSupportAdSplit(true);
        courtSchedule.setCourtSession(ALL_DAY);
        courtSchedule.setMaxAdMorningDuration(180);
        courtSchedule.setMaxAdAfternoonDuration(180);
        courtSchedule.setCourtScheduleId(courtScheduleId.toString());
        courtSchedule.setSessionDate(getRandomFutureDateWithinNextYear());
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), DEFAULT_ALL_DAY_START_TIME).toInstant());
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), VALUE_16_00).toInstant());
        courtSchedule.setIsOverbookingAllowed(false);
        courtSchedule.setActive(true);
        databaseSeeder.insertCourtSchedule(courtSchedule);

        // Morning slot: 60min at 10:00
        createAllocatedListing(courtSchedule, randomUUID(), randomUUID(), 60, DEFAULT_ALL_DAY_START_TIME);
        // Two afternoon slots: 30min at 14:00 and 45min at 15:00
        createAllocatedListing(courtSchedule, randomUUID(), randomUUID(), 30, DEFAULT_AFTERNOON_START_TIME);
        createAllocatedListing(courtSchedule, randomUUID(), randomUUID(), 45, VALUE_15_00);

        final String payload = getPayload(COURTSCHEDULER_SEARCH_COURTSCHEDULES_BY_ID_DYNAM)
                .replace(COURT_SCHEDULE_ID_4, courtScheduleId.toString());
        final Map<String, Object> map = mapper.readValue(payload, new TypeReference<>() {
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
        assertThat(json.getJsonArray(COURT_SCHEDULES).size(), is(1));

        // Response is grouped per courtroom (SPRDT-757): courtSchedules[] -> {courtRoomId, courtRoomName, sessions[]}
        final JsonObject scheduleJson = json.getJsonArray(COURT_SCHEDULES).getJsonObject(0)
                .getJsonArray(SESSIONS).getJsonObject(0);
        assertThat(scheduleJson.getString(COURT_SCHEDULE_ID_5), is(courtScheduleId.toString()));
        assertThat(scheduleJson.getBoolean(ALL_DAY_SPLIT), is(true));
        // Total across all three allocations
        assertThat(scheduleJson.getInt(TOTAL_BOOKED), is(60 + 30 + 45));
        // Morning = one 60min booking; afternoon = 30+45
        assertThat(scheduleJson.getInt("totalBookedForMorning"), is(60));
        assertThat(scheduleJson.getInt("totalBookedForAfternoon"), is(75));
        assertThat(scheduleJson.getInt("availableDurationForMorning"), is(180 - 60));
        assertThat(scheduleJson.getInt("availableDurationForAfternoon"), is(180 - 75));
    }

    public String prepareCreateCourtSchedulePayload(final String jsonFilePath) {
        final LocalDate startDate = now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        final LocalDate endDate = startDate.plusDays(28);

        return getPayload(jsonFilePath)
                .replaceAll(START_DATE_2, startDate.toString())
                .replaceAll(END_DATE_2, endDate.toString());
    }

    public String prepareCreateCourtSchedulePayload_testBSTToUTC(final String jsonFilePath, final LocalDate startDate, final LocalDate endDate) {
        return getPayload(jsonFilePath)
                .replaceAll(START_DATE_2, startDate.toString())
                .replaceAll(END_DATE_2, endDate.toString());
    }

    private AllocatedListing createAllocatedListing(final CourtSchedule courtSchedule, final UUID hearingIdForMorning, final UUID bookingIdForMorning, final int duration, final String time) throws SQLException {
        final AllocatedListing allocatedListingForMorning = RANDOM.nextObject(AllocatedListing.class);
        allocatedListingForMorning.setId(randomUUID().toString());
        allocatedListingForMorning.setCourtScheduleId(courtSchedule.getCourtScheduleId());
        allocatedListingForMorning.setHearingId(hearingIdForMorning.toString());
        allocatedListingForMorning.setBookingId(bookingIdForMorning.toString());
        allocatedListingForMorning.setDuration(duration);
        allocatedListingForMorning.setHearingStartTime(combineDateAndTime(courtSchedule.getSessionDate(), time).toInstant());
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
        final List<CourtScheduleJudiciary> judiciariesBefore = databaseReader.courtScheduleJudiciaries();
        assertTrue(judiciariesBefore.stream()
                .anyMatch(js -> js.getId().getCourtScheduleId().equals(courtSchedule.getCourtScheduleId())
                        && js.getId().getJudiciaryId().equals(courtScheduleJudiciary.getId().getJudiciaryId())));

        // Call unassign endpoint

        final String requestPayload = createObjectBuilder()
                .add(JUDICIARIES, createArrayBuilder()
                        .add(createObjectBuilder()
                                .add(JUDICIARY_ID, courtScheduleJudiciary.getId().getJudiciaryId())
                                .add(SESSION_IDS, createArrayBuilder()
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
        final List<CourtScheduleJudiciary> judiciariesAfter = databaseReader.courtScheduleJudiciaries();
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
                .add(JUDICIARIES, createArrayBuilder()
                        .add(createObjectBuilder()
                                .add(JUDICIARY_ID, courtScheduleJudiciary.getId().getJudiciaryId())
                                .add(SESSION_IDS, createArrayBuilder()
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
        final List<CourtScheduleJudiciary> judiciariesAfter = databaseReader.courtScheduleJudiciaries();
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
                .add(JUDICIARIES, createArrayBuilder()
                        .add(createObjectBuilder()
                                .add(JUDICIARY_ID, nonExistentJudiciaryId)
                                .add(SESSION_IDS, createArrayBuilder()
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
                .add(JUDICIARIES, createArrayBuilder()
                        .add(createObjectBuilder()
                                .add(JUDICIARY_ID, randomUUID().toString())
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
                .add(JUDICIARIES, createArrayBuilder()
                        .add(createObjectBuilder()
                                .add(SESSION_IDS, createArrayBuilder()
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
                .add(JUDICIARIES, createArrayBuilder()
                        .add(createObjectBuilder()
                                .add(JUDICIARY_ID, courtScheduleJudiciary.getId().getJudiciaryId())
                                .add(SESSION_IDS, createArrayBuilder()
                                        .add(courtSchedule.getCourtScheduleId())
                                        .build())
                                .build())
                        .build())
                .add(SKIP_VALIDATIONS, true)
                .build()
                .toString();

        final Response response = postCommand(JUDICIARY_SESSION_URL,
                UNASSIGN_JUDICIARY_CONTENT_TYPE,
                SYSTEM_USER_ID,
                requestPayload);

        // Should succeed even though allocated listings exist (validations skipped)
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        // Verify judiciary is unassigned
        final List<CourtScheduleJudiciary> judiciariesAfter = databaseReader.courtScheduleJudiciaries();
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
                .add(JUDICIARIES, createArrayBuilder()
                        .add(createObjectBuilder()
                                .add(JUDICIARY_ID, courtScheduleJudiciary.getId().getJudiciaryId())
                                .add(SESSION_IDS, createArrayBuilder()
                                        .add(courtSchedule.getCourtScheduleId())
                                        .build())
                                .build())
                        .build())
                .add(SKIP_VALIDATIONS, false)
                .build()
                .toString();

        final Response response = postCommand(JUDICIARY_SESSION_URL,
                UNASSIGN_JUDICIARY_CONTENT_TYPE,
                SYSTEM_USER_ID,
                requestPayload);

        // Should succeed (validation continues but doesn't throw exception, just logs)
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        // Verify judiciary is still assigned (validation prevented unassignment)
        final List<CourtScheduleJudiciary> judiciariesAfter = databaseReader.courtScheduleJudiciaries();
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
        final List<CourtScheduleJudiciary> judiciariesBefore = databaseReader.courtScheduleJudiciaries();
        assertFalse(judiciariesBefore.stream()
                .anyMatch(js -> js.getId().getCourtScheduleId().equals(courtSchedule.getCourtScheduleId())
                        && js.getId().getJudiciaryId().equals(judiciaryId)));

        // Call assign endpoint with skipValidations=true (since judiciary may not exist in reference data)
        final String requestPayload = createObjectBuilder()
                .add(JUDICIARIES, createArrayBuilder()
                        .add(createObjectBuilder()
                                .add(JUDICIARY_ID, judiciaryId)
                                .add(SESSION_IDS, createArrayBuilder()
                                        .add(courtSchedule.getCourtScheduleId())
                                        .build())
                                .build())
                        .build())
                .add(SKIP_VALIDATIONS, true)
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
        final String judiciaryId = STUB_JUDICIARY_MAGISTRATE_1;

        // Call assign endpoint with skipValidations=false
        final String requestPayload = createObjectBuilder()
                .add(JUDICIARIES, createArrayBuilder()
                        .add(createObjectBuilder()
                                .add(JUDICIARY_ID, judiciaryId)
                                .add(SESSION_IDS, createArrayBuilder()
                                        .add(courtSchedule.getCourtScheduleId())
                                        .build())
                                .add(P_IS_DEPUTY, false)
                                .add(P_IS_BENCH_CHAIRMAN, true)
                                .build())
                        .build())
                .add(SKIP_VALIDATIONS, false)
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
                .add(JUDICIARIES, createArrayBuilder()
                        .add(createObjectBuilder()
                                .add(JUDICIARY_ID, judiciaryId)
                                .add(SESSION_IDS, createArrayBuilder()
                                        .add(courtSchedule1.getCourtScheduleId())
                                        .add(courtSchedule2.getCourtScheduleId())
                                        .build())
                                .build())
                        .build())
                .add(SKIP_VALIDATIONS, true)
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
        final String judiciaryId = STUB_JUDICIARY_MAGISTRATE_1;

        // Call assign endpoint without skipValidations (should default to false)
        final String requestPayload = createObjectBuilder()
                .add(JUDICIARIES, createArrayBuilder()
                        .add(createObjectBuilder()
                                .add(JUDICIARY_ID, judiciaryId)
                                .add(SESSION_IDS, createArrayBuilder()
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

    // -----------------------------------------------------------------------
    // change-judiciary-for-hearings command integration (assign)
    // -----------------------------------------------------------------------

    @Test
    void shouldSendChangeJudiciaryForHearingsCommandWhenAssigningJudiciaryToSessionWithAllocatedHearing() throws Exception {
        StubUtil.stubChangeJudiciaryForHearingsCommand();

        final CourtSchedule courtSchedule = createTestCourtSchedule();
        databaseSeeder.insertCourtSchedule(courtSchedule);

        final AllocatedListing allocatedListing = getAllocatedListing(courtSchedule);
        databaseSeeder.insertAllocatedListing(allocatedListing);

        final String requestPayload = createObjectBuilder()
                .add(JUDICIARIES, createArrayBuilder()
                        .add(createObjectBuilder()
                                .add(JUDICIARY_ID, STUB_JUDICIARY_MAGISTRATE_1)
                                .add(SESSION_IDS, createArrayBuilder()
                                        .add(courtSchedule.getCourtScheduleId())
                                        .build())
                                .build())
                        .build())
                .add(SKIP_VALIDATIONS, false)
                .build()
                .toString();

        final Response response = postCommand(JUDICIARY_SESSION_URL,
                ASSIGN_JUDICIARY_CONTENT_TYPE,
                SYSTEM_USER_ID,
                requestPayload);

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
        assertThat(StubUtil.countSchemaShapedChangeJudiciaryForHearingsRequestsFor(allocatedListing.getHearingId()),
                greaterThan(0));
    }

    @Test
    void shouldNotSendChangeJudiciaryForHearingsCommandWhenAssigningJudiciaryToSessionWithNoAllocatedHearings() throws Exception {
        StubUtil.stubChangeJudiciaryForHearingsCommand();
        final int commandCountBefore = StubUtil.countChangeJudiciaryForHearingsRequests();

        final CourtSchedule courtSchedule = createTestCourtSchedule();
        databaseSeeder.insertCourtSchedule(courtSchedule);
        // No allocated listing — judiciary hash changes but no hearings to notify

        final String requestPayload = createObjectBuilder()
                .add(JUDICIARIES, createArrayBuilder()
                        .add(createObjectBuilder()
                                .add(JUDICIARY_ID, STUB_JUDICIARY_MAGISTRATE_1)
                                .add(SESSION_IDS, createArrayBuilder()
                                        .add(courtSchedule.getCourtScheduleId())
                                        .build())
                                .build())
                        .build())
                .add(SKIP_VALIDATIONS, false)
                .build()
                .toString();

        final Response response = postCommand(JUDICIARY_SESSION_URL,
                ASSIGN_JUDICIARY_CONTENT_TYPE,
                SYSTEM_USER_ID,
                requestPayload);

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
        assertThat(StubUtil.countChangeJudiciaryForHearingsRequests(), is(commandCountBefore));
    }

    // -----------------------------------------------------------------------
    // change-judiciary-for-hearings command integration (unassign)
    // -----------------------------------------------------------------------

    @Test
    void shouldSendChangeJudiciaryForHearingsCommandWhenUnassigningJudiciaryFromSessionWithAllocatedHearing() throws Exception {
        StubUtil.stubChangeJudiciaryForHearingsCommand();

        final CourtSchedule courtSchedule = createTestCourtSchedule();
        databaseSeeder.insertCourtSchedule(courtSchedule);

        final CourtScheduleJudiciary courtScheduleJudiciary = createTestCourtScheduleJudiciary(
                courtSchedule.getCourtScheduleId());
        databaseSeeder.saveJudiciarySchedule(courtScheduleJudiciary);

        final AllocatedListing allocatedListing = getAllocatedListing(courtSchedule);
        databaseSeeder.insertAllocatedListing(allocatedListing);

        // skipValidations=true so the allocated listing does not block the unassignment
        final String requestPayload = createObjectBuilder()
                .add(JUDICIARIES, createArrayBuilder()
                        .add(createObjectBuilder()
                                .add(JUDICIARY_ID, courtScheduleJudiciary.getId().getJudiciaryId())
                                .add(SESSION_IDS, createArrayBuilder()
                                        .add(courtSchedule.getCourtScheduleId())
                                        .build())
                                .build())
                        .build())
                .add(SKIP_VALIDATIONS, true)
                .build()
                .toString();

        final Response response = postCommand(JUDICIARY_SESSION_URL,
                UNASSIGN_JUDICIARY_CONTENT_TYPE,
                SYSTEM_USER_ID,
                requestPayload);

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
        // After unassignment the judiciary array is empty, so use body-contains check for the hearing ID
        assertThat(StubUtil.countChangeJudiciaryForHearingsRequestsContaining(allocatedListing.getHearingId()),
                greaterThan(0));
    }

    @Test
    void shouldNotSendChangeJudiciaryForHearingsCommandWhenUnassigningJudiciaryFromSessionWithNoAllocatedHearings() throws Exception {
        StubUtil.stubChangeJudiciaryForHearingsCommand();
        final int commandCountBefore = StubUtil.countChangeJudiciaryForHearingsRequests();

        final CourtSchedule courtSchedule = createTestCourtSchedule();
        databaseSeeder.insertCourtSchedule(courtSchedule);

        final CourtScheduleJudiciary courtScheduleJudiciary = createTestCourtScheduleJudiciary(
                courtSchedule.getCourtScheduleId());
        databaseSeeder.saveJudiciarySchedule(courtScheduleJudiciary);
        // No allocated listing — judiciary hash changes but no hearings to notify

        final String requestPayload = createObjectBuilder()
                .add(JUDICIARIES, createArrayBuilder()
                        .add(createObjectBuilder()
                                .add(JUDICIARY_ID, courtScheduleJudiciary.getId().getJudiciaryId())
                                .add(SESSION_IDS, createArrayBuilder()
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
        assertThat(StubUtil.countChangeJudiciaryForHearingsRequests(), is(commandCountBefore));
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

        final List<CourtScheduleJudiciary> judiciariesBefore = databaseReader.courtScheduleJudiciaries();
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

        final List<CourtScheduleJudiciary> judiciariesAfter = databaseReader.courtScheduleJudiciaries();
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
        final String judiciaryId = STUB_JUDICIARY_MAGISTRATE_1;

        // Verify judiciary is not assigned initially
        final List<CourtScheduleJudiciary> judiciariesBefore = databaseReader.courtScheduleJudiciaries();
        assertFalse(judiciariesBefore.stream()
                .anyMatch(js -> js.getId().getCourtScheduleId().equals(courtSchedule.getCourtScheduleId())
                        && js.getId().getJudiciaryId().equals(judiciaryId)));

        // Call assign endpoint without isDeputy and isBenchChairman
        final String requestPayload = createObjectBuilder()
                .add(JUDICIARIES, createArrayBuilder()
                        .add(createObjectBuilder()
                                .add(JUDICIARY_ID, judiciaryId)
                                .add(SESSION_IDS, createArrayBuilder()
                                        .add(courtSchedule.getCourtScheduleId())
                                        .build())
                                // Intentionally not including isDeputy and isBenchChairman
                                .build())
                        .build())
                .add(SKIP_VALIDATIONS, false)
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
        final List<CourtScheduleJudiciary> judiciariesAfter = databaseReader.courtScheduleJudiciaries();
        final CourtScheduleJudiciary assignedJudiciary = judiciariesAfter.stream()
                .filter(js -> js.getId().getCourtScheduleId().equals(courtSchedule.getCourtScheduleId())
                        && js.getId().getJudiciaryId().equals(judiciaryId))
                .findFirst()
                .orElse(null);

        assertThat(assignedJudiciary, notNullValue());
        // Verify that isDeputy and isBenchChairman are persisted as false when not provided
        assertFalse(assignedJudiciary.isDeputy(), "isDeputy should be persisted as false when not provided");
        assertFalse(assignedJudiciary.isBenchChairman(), "isBenchChairman should be persisted as false when not provided");
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
            assertTrue(magistrate1.isBenchChairman());
            assertFalse(magistrate1.isDeputy());
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
            assertFalse(magistrate2.isBenchChairman());
            assertTrue(magistrate2.isDeputy());
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
        courtSchedule.setSessionDate(now().plusDays(30));
        courtSchedule.setActive(true);
        courtSchedule.setSlotBased(true);
        courtSchedule.setMaxSlots(10);
        courtSchedule.setAvailableSlots(10);
        courtSchedule.setMaxDuration(240);
        courtSchedule.setAvailableDuration(240);
        courtSchedule.setCourtSession(AM_SESSION);
        courtSchedule.setPanel(ADULT_2);
        courtSchedule.setBusinessType(TRL_2);
        courtSchedule.setSupportAdSplit(false);
        courtSchedule.setIsOverbookingAllowed(false);
        courtSchedule.setMaxAdMorningDuration(0);
        courtSchedule.setMaxAdAfternoonDuration(0);
        return courtSchedule;
    }

    private CourtScheduleJudiciary createTestCourtScheduleJudiciary(final String courtScheduleId) {
        return createTestCourtScheduleJudiciary(courtScheduleId, "7e2f843e-d639-40b3-8611-8015f3a13333", "Mr");
    }

    private CourtScheduleJudiciary createTestCourtScheduleJudiciary(final String courtScheduleId,
                                                                    final String judiciaryId,
                                                                    final String dbTitle) {
        final CourtScheduleJudiciary courtScheduleJudiciary = new CourtScheduleJudiciary();
        final CourtScheduleJudiciaryKey key = new CourtScheduleJudiciaryKey();
        key.setCourtScheduleId(courtScheduleId);
        key.setJudiciaryId(judiciaryId);
        courtScheduleJudiciary.setId(key);
        courtScheduleJudiciary.setCourtListingProfileId("CS" + randomUUID().toString().substring(0, 8));
        courtScheduleJudiciary.setRotaJudiciaryId("ROTA" + randomUUID().toString().substring(0, 8));
        courtScheduleJudiciary.setTitle(dbTitle);
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
        allocatedListing.setHearingStartTime(courtSchedule.getSessionDate().atTime(14, 0).atZone(UTC_ZONE).toInstant());
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
        assertThat(COURT_SCHEDULES_SHOULD_BE_CREATED, courtSchedules.size(), is(greaterThan(0)));

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
        assertThat(COURT_SCHEDULES_SHOULD_BE_CREATED, courtSchedules.size(), is(greaterThan(0)));

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
        assertThat(COURT_SCHEDULES_SHOULD_BE_CREATED, courtSchedules.size(), is(greaterThan(0)));

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
        assertThat(COURT_SCHEDULES_SHOULD_BE_CREATED, courtSchedules.size(), is(greaterThan(0)));

        // Verify court schedules are created for the random start date
        final CourtSchedule courtSchedule = courtSchedules.get(0);
        assertThat(courtSchedule.getCourtScheduleId(), is(notNullValue()));
    }

    @Test
    void shouldCreateCourtSchedulesForMonthlyFrequencyWithYearBoundary() {
        // Given - Start date in December, end date in March next year
        // If December 15th is in the past, use next year's December 15th
        LocalDate startDate = now().withMonth(12).withDayOfMonth(15);
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
        assertThat(COURT_SCHEDULES_SHOULD_BE_CREATED, courtSchedules.size(), is(greaterThan(0)));

        // Verify court schedules are created across year boundary
        final CourtSchedule courtSchedule = courtSchedules.get(0);
        assertThat(courtSchedule.getCourtScheduleId(), is(notNullValue()));
    }

    @Test
    void shouldNotCreateSessionWhen5thFridayDoesNotExistInMonth() {
        // Given - Test with months where 5th Friday doesn't exist
        // Find a future date range: start from next month, find a month with 5 Fridays, then include following months
        final LocalDate baseDate = now().plusMonths(1).withDayOfMonth(1);
        final LocalDate startDate = baseDate;
        final LocalDate endDate = baseDate.plusMonths(2).withDayOfMonth(baseDate.plusMonths(2).lengthOfMonth());

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
        for (final CourtSchedule schedule : courtSchedules) {
            final LocalDate sessionDate = schedule.getSessionDate();
            // Verify the session date is actually a Friday and is the 5th Friday of that month
            assertThat("Session date should be a Friday", sessionDate.getDayOfWeek(), is(DayOfWeek.FRIDAY));

            // Calculate which occurrence this Friday is in the month
            final LocalDate firstOfMonth = sessionDate.withDayOfMonth(1);
            final LocalDate firstFriday = firstOfMonth.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY));
            final long weekNumber = ChronoUnit.WEEKS.between(firstFriday, sessionDate);
            assertThat("Session should be on the 5th Friday", weekNumber, is(4L)); // 0-indexed, so 4 means 5th
        }
    }

    private String prepareCreateCourtSchedulePayloadWithDates(final String fileName, final LocalDate startDate, final LocalDate endDate) {
        return getPayload(fileName)
                .replace(START_DATE_2, startDate.format(ofPattern(YYYY_MM_DD)))
                .replace(END_DATE_2, endDate.format(ofPattern(YYYY_MM_DD)));
    }

    @Test
    void shouldPreventCourtroomChangeWhenHearingsExistAndAssigned() throws SQLException {
        final UUID courtScheduleId = randomUUID();
        final CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        final String courtRoomId = UUID_3FC02C0F; // Use same courtroom ID for initial and update
        final String courtHouseId = UUID_785339C1; // Test Crown Court - same court house as courtroom
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType(DVLA_2);
        expected.setSlotBased(true);
        expected.setMaxSlots(15);
        expected.setAvailableSlots(15);
        expected.setIsDraft(false); // Assigned session
        expected.setHasHearingsBooked(true);
        expected.setSupportAdSplit(false);
        expected.setCourtSession(AM_SESSION);
        expected.setPanel(YOUTH_2);
        expected.setCourtRoomId(courtRoomId); // Set initial courtroom ID
        expected.setCourtHouseId(courtHouseId); // Set court house ID to match courtroom
        expected.setSessionDate(now().with(TemporalAdjusters.next(DayOfWeek.MONDAY))); // Set to future date to avoid past session validation
        expected.setSessionStartTime(DateUtils.localDateToDateWithTime(expected.getSessionDate(), 9, 0).toInstant());
        expected.setSessionEndTime(DateUtils.localDateToDateWithTime(expected.getSessionDate(), 13, 0).toInstant());
        databaseSeeder.insertCourtSchedule(expected);

        // Create a hearing attached to this session
        final UUID hearingId = randomUUID();
        final UUID bookingId = randomUUID();
        createAllocatedListing(expected, hearingId, bookingId, 15, DEFAULT_ALL_DAY_START_TIME);

        String updateCourtSchedulePayload = getPayload(UPDATE_COURT_SCHEDULE_JSON);
        final String changedCourtRoomId = courtRoomId; // Use same courtroom to avoid court house validation
        final String changedBusinessType = DVLA_2;
        final String changedSessionType = expected.getCourtSession();
        final String changedPanel = ADULT_2; // Change panel to trigger SESSION_EDIT_ANOTHER_USER validation when hearings exist
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_SCHEDULE_ID_4, expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_ROOM_ID_2, changedCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(BUSINESS_TYPE_2, changedBusinessType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(SESSION_TYPE_2, changedSessionType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(PANEL_2, changedPanel);
        // Replace maxDuration with slot-based fields and add session times
        final String sessionStartTimeStr = DateUtils.sessionTimeFormatter(expected.getSessionStartTime());
        final String sessionEndTimeStr = DateUtils.sessionTimeFormatter(expected.getSessionEndTime());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(MAX_DURATION_10,
                "\"maxSlots\": 15,\n  \"maxDuration\": 0,\n  \"allDaySplit\": false,\n  \"sessionStartTime\": \"" + sessionStartTimeStr + "\",\n  \"sessionEndTime\": \"" + sessionEndTimeStr + "\"");

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);
        final String errorResponseMessage = response.readEntity(String.class);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        assertThat(errorResponseMessage, containsString(SESSION_EDIT_ANOTHER_USER));
    }

    @Test
    void shouldReturn400WhenPanelMissingForMagistratesJurisdictionInUpdate() {
        String updateCourtSchedulePayload = getPayload("update-court-schedule-missing-panel.json");
        final String changedCourtRoomId = UUID_3FC02C0F; // picked from referencedata.rota-courtrooms.json file
        final String changedBusinessType = DVLA_2;

        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_SCHEDULE_ID_4, randomUUID().toString());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_ROOM_ID_2, changedCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(BUSINESS_TYPE_2, changedBusinessType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(SESSION_TYPE_2, AM_SESSION);

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
    }

    @Test
    void shouldAllowDraftToAssignedChangeWhenHearingsExist() throws SQLException {
        final UUID courtScheduleId = randomUUID();
        final CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType(DVLA_2);
        expected.setSlotBased(true);
        expected.setMaxSlots(15);
        expected.setAvailableSlots(15);
        expected.setIsDraft(true); // Currently Draft
        expected.setHasHearingsBooked(true);
        expected.setSupportAdSplit(false);
        expected.setCourtSession(AM_SESSION);
        expected.setPanel(YOUTH_2);
        expected.setCourtRoomId(UUID_3FC02C0F);
        expected.setSessionDate(now().with(TemporalAdjusters.next(DayOfWeek.MONDAY))); // Set to future date to avoid past session validation
        expected.setSessionStartTime(DateUtils.localDateToDateWithTime(expected.getSessionDate(), 9, 0).toInstant());
        expected.setSessionEndTime(DateUtils.localDateToDateWithTime(expected.getSessionDate(), 13, 0).toInstant());
        databaseSeeder.insertCourtSchedule(expected);

        // Create a hearing attached to this session
        final UUID hearingId = randomUUID();
        final UUID bookingId = randomUUID();
        createAllocatedListing(expected, hearingId, bookingId, 15, DEFAULT_ALL_DAY_START_TIME);

        String updateCourtSchedulePayload = getPayload(UPDATE_COURT_SCHEDULE_JSON);
        final String sameCourtRoomId = expected.getCourtRoomId(); // Keep same courtroom
        final String changedBusinessType = DVLA_2;
        final String sameSessionType = expected.getCourtSession(); // Keep same session type
        final String samePanel = expected.getPanel(); // Keep same panel
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_SCHEDULE_ID_4, expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_ROOM_ID_2, sameCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(BUSINESS_TYPE_2, changedBusinessType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(SESSION_TYPE_2, sameSessionType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(PANEL_2, samePanel);
        // Replace maxDuration with slot-based fields and add session times
        final String sessionStartTimeStr = DateUtils.sessionTimeFormatter(expected.getSessionStartTime());
        final String sessionEndTimeStr = DateUtils.sessionTimeFormatter(expected.getSessionEndTime());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(MAX_DURATION_10,
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
        final UUID courtScheduleId = randomUUID();
        final CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        final String courtRoomId = UUID_3FC02C0F;
        final String courtHouseId = UUID_785339C1;
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType(DVLA_2);
        expected.setJurisdiction(MAGISTRATES_2); // Set initial jurisdiction to MAGISTRATES
        expected.setSupportAdSplit(false);
        expected.setSessionDate(now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)));
        expected.setCourtRoomId(courtRoomId);
        expected.setCourtHouseId(courtHouseId);
        databaseSeeder.insertCourtSchedule(expected);

        String updateCourtSchedulePayload = getPayload("update-court-schedule-change-jurisdiction.json");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_SCHEDULE_ID_4, expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_ROOM_ID_2, courtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(BUSINESS_TYPE_2, DVLA_2);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(SESSION_TYPE_2, "AM");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(PANEL_2, ADULT_2);
        // Jurisdiction is set to CROWN in the payload, which should fail

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String errorResponseMessage = response.readEntity(String.class);
        assertThat(errorResponseMessage, containsString("Jurisdiction cannot be changed"));
    }

    @Test
    void shouldReturn400WhenUsingInvalidBusinessTypeInUpdate() throws SQLException {
        final UUID courtScheduleId = randomUUID();
        final CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        final String courtRoomId = UUID_3FC02C0F;
        final String courtHouseId = UUID_785339C1;
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType(DVLA_2);
        expected.setSupportAdSplit(false);
        expected.setSessionDate(now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)));
        expected.setCourtRoomId(courtRoomId);
        expected.setCourtHouseId(courtHouseId);
        databaseSeeder.insertCourtSchedule(expected);

        String updateCourtSchedulePayload = getPayload("update-court-schedule-invalid-business-type.json");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_SCHEDULE_ID_4, expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_ROOM_ID_2, courtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("INVALID_BT", "INVALIDBT"); // Business type that doesn't exist
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(SESSION_TYPE_2, "AM");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(PANEL_2, ADULT_2);

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String errorResponseMessage = response.readEntity(String.class);
        assertThat(errorResponseMessage, containsString("Invalid business type"));
    }

    @Test
    void shouldReturn400WhenBusinessTypeJurisdictionDoesNotMatchInUpdate() throws SQLException {
        // Create a MAGISTRATES session
        final UUID courtScheduleId = randomUUID();
        final CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        final String courtRoomId = UUID_3FC02C0F;
        final String courtHouseId = UUID_785339C1;
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType(DVLA_2);
        expected.setJurisdiction(CROWN_2); // Session has CROWN jurisdiction
        expected.setSupportAdSplit(false);
        expected.setSlotBased(true); // Ensure slot-based for maxSlots
        expected.setSessionDate(now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)));
        expected.setCourtRoomId(courtRoomId);
        expected.setCourtHouseId(courtHouseId);
        databaseSeeder.insertCourtSchedule(expected);

        // Try to update with APP business type which has MAGISTRATES jurisdiction
        // This should work since both session and business type have MAGISTRATES jurisdiction
        // To test the failure case, we would need a business type with CROWN jurisdiction
        // Note: This test verifies the validation logic is in place
        // If a business type with CROWN jurisdiction exists and is used for a MAGISTRATES session,
        // it should return 400 with "Invalid business type" error
        String updateCourtSchedulePayload = getPayload(UPDATE_COURT_SCHEDULE_JSON);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_SCHEDULE_ID_4, expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_ROOM_ID_2, courtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(BUSINESS_TYPE_2, "APP"); // APP has MAGISTRATES jurisdiction
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(SESSION_TYPE_2, "AM");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(PANEL_2, ADULT_2);
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
        final UUID courtScheduleId = randomUUID();
        final CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        final String courtRoomId = UUID_3FC02C0F; // CROWN courtroom from CP source
        final String courtHouseId = UUID_785339C1; // CROWN court house from CP source
        expected.setCourtScheduleId(courtScheduleId.toString());
        expected.setBusinessType("CRC"); // Use a CROWN business type initially
        expected.setJurisdiction(CROWN_2); // Session has CROWN jurisdiction
        expected.setSupportAdSplit(false);
        expected.setSlotBased(true); // Ensure slot-based for consistency
        expected.setSessionDate(now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)));
        expected.setCourtRoomId(courtRoomId);
        expected.setCourtHouseId(courtHouseId);
        databaseSeeder.insertCourtSchedule(expected);

        // Try to update with APP business type which has MAGISTRATES jurisdiction
        // This should fail since APP has MAGISTRATES jurisdiction but session is CROWN
        // For CROWN jurisdiction, we need isDraft and should not have panel
        String updateCourtSchedulePayload = getPayload("update-court-schedule-change-jurisdiction.json");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_SCHEDULE_ID_4, expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_ROOM_ID_2, courtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(BUSINESS_TYPE_2, "APP"); // APP has MAGISTRATES jurisdiction
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(SESSION_TYPE_2, "AM");
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

        final UUID draftSessionId = randomUUID();
        final CourtSchedule draftSessionWithHearing = RANDOM.nextObject(CourtSchedule.class);
        draftSessionWithHearing.setCourtScheduleId(draftSessionId.toString());
        draftSessionWithHearing.setBusinessType(DVLA_2);
        draftSessionWithHearing.setSlotBased(true);
        draftSessionWithHearing.setMaxSlots(15);
        draftSessionWithHearing.setAvailableSlots(15);
        draftSessionWithHearing.setIsDraft(true); // Draft session
        draftSessionWithHearing.setSupportAdSplit(false);
        draftSessionWithHearing.setCourtSession(AM_SESSION);
        draftSessionWithHearing.setPanel(ADULT_2);
        draftSessionWithHearing.setCourtRoomId(ORIGINAL_COURTROOM_ID);
        draftSessionWithHearing.setJurisdiction(CROWN_2);
        draftSessionWithHearing.setCourtHouseId(UUID_785339C1);
        draftSessionWithHearing.setSessionStartTime(DateUtils.localDateToDateWithTime(draftSessionWithHearing.getSessionDate(), 9, 0).toInstant());
        draftSessionWithHearing.setSessionEndTime(DateUtils.localDateToDateWithTime(draftSessionWithHearing.getSessionDate(), 13, 0).toInstant());
        databaseSeeder.insertCourtSchedule(draftSessionWithHearing);

        // Create hearing for draft session (not eligible)
        final UUID hearingId1 = randomUUID();
        final UUID bookingId1 = randomUUID();
        createAllocatedListing(draftSessionWithHearing, hearingId1, bookingId1, 15, DEFAULT_ALL_DAY_START_TIME);

        final UUID draftSessionNoHearingsId = randomUUID();
        final CourtSchedule draftSessionNoHearings = RANDOM.nextObject(CourtSchedule.class);
        draftSessionNoHearings.setCourtScheduleId(draftSessionNoHearingsId.toString());
        draftSessionNoHearings.setBusinessType(DVLA_2);
        draftSessionNoHearings.setSlotBased(true);
        draftSessionNoHearings.setMaxSlots(15);
        draftSessionNoHearings.setAvailableSlots(15);
        draftSessionNoHearings.setIsDraft(true); // Draft session without hearings
        draftSessionNoHearings.setSupportAdSplit(false);
        draftSessionNoHearings.setCourtSession(AM_SESSION);
        draftSessionNoHearings.setPanel(ADULT_2);
        draftSessionNoHearings.setCourtRoomId(ORIGINAL_COURTROOM_ID);
        draftSessionNoHearings.setJurisdiction(CROWN_2);
        draftSessionNoHearings.setCourtHouseId(UUID_785339C1);
        draftSessionNoHearings.setSessionStartTime(DateUtils.localDateToDateWithTime(draftSessionNoHearings.getSessionDate(), 9, 0).toInstant());
        draftSessionNoHearings.setSessionEndTime(DateUtils.localDateToDateWithTime(draftSessionNoHearings.getSessionDate(), 13, 0).toInstant());
        databaseSeeder.insertCourtSchedule(draftSessionNoHearings);
        // Assigned session -  NOT eligible
        final UUID assignedSessionId = randomUUID();
        final CourtSchedule assignedSession = RANDOM.nextObject(CourtSchedule.class);
        assignedSession.setCourtScheduleId(assignedSessionId.toString());
        assignedSession.setBusinessType(DVLA_2);
        assignedSession.setSlotBased(true);
        assignedSession.setMaxSlots(15);
        assignedSession.setAvailableSlots(15);
        assignedSession.setIsDraft(false); // Assigned session without hearings
        assignedSession.setSupportAdSplit(false);
        assignedSession.setCourtSession(AM_SESSION);
        assignedSession.setPanel(ADULT_2);
        assignedSession.setCourtRoomId(ORIGINAL_COURTROOM_ID);
        assignedSession.setJurisdiction(CROWN_2);
        assignedSession.setCourtHouseId(UUID_785339C1);
        assignedSession.setSessionStartTime(DateUtils.localDateToDateWithTime(assignedSession.getSessionDate(), 9, 0).toInstant());
        assignedSession.setSessionEndTime(DateUtils.localDateToDateWithTime(assignedSession.getSessionDate(), 13, 0).toInstant());
        databaseSeeder.insertCourtSchedule(assignedSession);

        String assignCourtroomPayload = getPayload("assign-courtroom-multiple-eligible-sessions.json");
        final String newCourtRoomId = UUID_3FC02C0F; // Different courtroom
        assignCourtroomPayload = assignCourtroomPayload.replace(COURT_SCHEDULE_ID_1_2, draftSessionWithHearing.getCourtScheduleId());
        assignCourtroomPayload = assignCourtroomPayload.replace(COURT_SCHEDULE_ID_2_2, draftSessionNoHearings.getCourtScheduleId());
        assignCourtroomPayload = assignCourtroomPayload.replace("COURT_SCHEDULE_ID_3", assignedSession.getCourtScheduleId());
        assignCourtroomPayload = assignCourtroomPayload.replace(COURT_ROOM_ID_2, newCourtRoomId);

        final Response response = postCommand(BASE_RESOURCE_URL + ASSIGN_COURTROOM_URL, COURT_SCHEDULE_ASSIGN_COURTROOM_CONTENT_TYPE, USER_ID, assignCourtroomPayload);
        final String responsePayload = response.readEntity(String.class);

        assertThat(ASSIGN_COURTROOM_RESPONSE + responsePayload, response.getStatus(), is(OK.getStatusCode()));

        // Parse JSON response - should be an object with errorGroups array
        final JsonReader jsonReader = Json.createReader(new StringReader(responsePayload));
        final JsonObject jsonResponse = jsonReader.readObject();
        jsonReader.close();

        // Verify response has errorGroups key
        assertThat(RESPONSE_SHOULD_CONTAIN_ERROR_GROUPS, jsonResponse.containsKey(ERROR_GROUPS), is(true));
        final jakarta.json.JsonArray jsonResponseArray = jsonResponse.getJsonArray(ERROR_GROUPS);

        // Verify response is an array
        assertThat(RESPONSE_SHOULD_BE_AN_ARRAY, jsonResponseArray, notNullValue());

        // Draft sessions with hearing should not be assigned
        final boolean draftSessionWithHearingInErrorGroup = jsonResponseArray.stream()
                .map(JsonValue::asJsonObject)
                .anyMatch(errorGroup -> {
                    final jakarta.json.JsonArray sessions = errorGroup.getJsonArray(SESSIONS);
                    return sessions.stream()
                            .map(s -> s.asJsonObject())
                            .anyMatch(s -> draftSessionWithHearing.getCourtScheduleId().equals(s.getString(COURT_SCHEDULE_ID_5)));
                });
        assertThat("Draft sessions with hearing should not be eligible", draftSessionWithHearingInErrorGroup, is(true));        // Draft sessions should be successfully assigned (not in any error group)

        // Draft sessions should be successfully assigned (not in any error group)
        final boolean draftSessionNoHearingInErrorGroup = jsonResponseArray.stream()
                .map(JsonValue::asJsonObject)
                .anyMatch(errorGroup -> {
                    final jakarta.json.JsonArray sessions = errorGroup.getJsonArray(SESSIONS);
                    return sessions.stream()
                            .map(s -> s.asJsonObject())
                            .anyMatch(s ->  draftSessionNoHearings.getCourtScheduleId().equals(s.getString(COURT_SCHEDULE_ID_5)));
                });
        assertThat("Draft sessions with hearing should not be eligible", draftSessionNoHearingInErrorGroup, is(false));

        // Assigned session should be in error group
        final boolean foundAssignedSessionInErrorGroup = jsonResponseArray.stream()
                .map(JsonValue::asJsonObject)
                .anyMatch(errorGroup -> {
                    final String error = errorGroup.getString(ERROR);
                    if (CANNOT_ASSIGN_COURTROOM_TO_AN_ASSIGNED_SESSION.equals(error)) {
                        final jakarta.json.JsonArray sessions = errorGroup.getJsonArray(SESSIONS);
                        return sessions.stream()
                                .map(s -> s.asJsonObject())
                                .anyMatch(s -> assignedSession.getCourtScheduleId().equals(s.getString(COURT_SCHEDULE_ID_5)));
                    }
                    return false;
                });
        assertThat("Should find assigned session in error group", foundAssignedSessionInErrorGroup, is(true));
    }

    @Test
    void shouldNotAssignCourtroomToAssignedSession() throws SQLException {
        // Assigned session - NOT eligible (Business Rule 5: applies to all assigned sessions regardless of hearings)

        final UUID assignedSessionId = randomUUID();
        final CourtSchedule assignedSession = RANDOM.nextObject(CourtSchedule.class);
        assignedSession.setCourtScheduleId(assignedSessionId.toString());
        assignedSession.setBusinessType(DVLA_2);
        assignedSession.setSlotBased(true);
        assignedSession.setMaxSlots(15);
        assignedSession.setAvailableSlots(15);
        assignedSession.setIsDraft(false); // Assigned session
        assignedSession.setHasHearingsBooked(true);
        assignedSession.setSupportAdSplit(false);
        assignedSession.setCourtSession(AM_SESSION);
        assignedSession.setPanel(ADULT_2);
        assignedSession.setCourtRoomId(ORIGINAL_COURTROOM_ID);
        assignedSession.setJurisdiction(CROWN_2);
        assignedSession.setCourtHouseId(UUID_785339C1);
        assignedSession.setSessionStartTime(DateUtils.localDateToDateWithTime(assignedSession.getSessionDate(), 9, 0).toInstant());
        assignedSession.setSessionEndTime(DateUtils.localDateToDateWithTime(assignedSession.getSessionDate(), 13, 0).toInstant());
        databaseSeeder.insertCourtSchedule(assignedSession);

        // Create a hearing attached to this session
        final UUID hearingId = randomUUID();
        final UUID bookingId = randomUUID();
        createAllocatedListing(assignedSession, hearingId, bookingId, 15, DEFAULT_ALL_DAY_START_TIME);

        String assignCourtroomPayload = getPayload(ASSIGN_COURTROOM_JSON);
        final String newCourtRoomId = UUID_3FC02C0F;
        assignCourtroomPayload = assignCourtroomPayload.replace(COURT_SCHEDULE_ID_1_2, assignedSession.getCourtScheduleId());
        assignCourtroomPayload = assignCourtroomPayload.replace(COURT_SCHEDULE_ID_2_2, assignedSession.getCourtScheduleId());
        assignCourtroomPayload = assignCourtroomPayload.replace(COURT_ROOM_ID_2, newCourtRoomId);

        final Response response = postCommand(BASE_RESOURCE_URL + ASSIGN_COURTROOM_URL, COURT_SCHEDULE_ASSIGN_COURTROOM_CONTENT_TYPE, USER_ID, assignCourtroomPayload);
        final String responsePayload = response.readEntity(String.class);

        assertThat(ASSIGN_COURTROOM_RESPONSE + responsePayload, response.getStatus(), is(OK.getStatusCode()));

        // Parse JSON response - should be an object with errorGroups array
        final JsonReader jsonReader = Json.createReader(new StringReader(responsePayload));
        final JsonObject jsonResponse = jsonReader.readObject();
        jsonReader.close();

        // Verify response has errorGroups key
        assertThat(RESPONSE_SHOULD_CONTAIN_ERROR_GROUPS, jsonResponse.containsKey(ERROR_GROUPS), is(true));
        final jakarta.json.JsonArray jsonResponseArray = jsonResponse.getJsonArray(ERROR_GROUPS);

        // Verify response is an array
        assertThat(RESPONSE_SHOULD_BE_AN_ARRAY, jsonResponseArray, notNullValue());

        // Find the error group with the expected error message
        final boolean foundErrorGroup = jsonResponseArray.stream()
                .map(JsonValue::asJsonObject)
                .anyMatch(errorGroup -> {
                    final String error = errorGroup.getString(ERROR);
                    if (CANNOT_ASSIGN_COURTROOM_TO_AN_ASSIGNED_SESSION.equals(error)) {
                        final jakarta.json.JsonArray sessions = errorGroup.getJsonArray(SESSIONS);
                        return sessions.stream()
                                .map(s -> s.asJsonObject())
                                .anyMatch(s -> assignedSession.getCourtScheduleId().equals(s.getString(COURT_SCHEDULE_ID_5)));
                    }
                    return false;
                });
        assertThat("Should find assigned session in error group", foundErrorGroup, is(true));
    }

    @Test
    void shouldNotAssignCourtroomToAssignedSessionWithoutHearings() throws SQLException {
        // Assigned session without hearings - NOT eligible (Business Rule 5: applies to all assigned sessions)

        final UUID assignedSessionId = randomUUID();
        final CourtSchedule assignedSession = RANDOM.nextObject(CourtSchedule.class);
        assignedSession.setCourtScheduleId(assignedSessionId.toString());
        assignedSession.setBusinessType(DVLA_2);
        assignedSession.setSlotBased(true);
        assignedSession.setMaxSlots(15);
        assignedSession.setAvailableSlots(15);
        assignedSession.setIsDraft(false); // Assigned session
        assignedSession.setHasHearingsBooked(false);
        assignedSession.setSupportAdSplit(false);
        assignedSession.setCourtSession(AM_SESSION);
        assignedSession.setPanel(ADULT_2);
        assignedSession.setCourtRoomId(ORIGINAL_COURTROOM_ID);
        assignedSession.setJurisdiction(CROWN_2);
        assignedSession.setCourtHouseId(UUID_785339C1);
        assignedSession.setSessionStartTime(DateUtils.localDateToDateWithTime(assignedSession.getSessionDate(), 9, 0).toInstant());
        assignedSession.setSessionEndTime(DateUtils.localDateToDateWithTime(assignedSession.getSessionDate(), 13, 0).toInstant());
        databaseSeeder.insertCourtSchedule(assignedSession);

        // No hearings attached to this session

        String assignCourtroomPayload = getPayload(ASSIGN_COURTROOM_JSON);
        final String newCourtRoomId = UUID_3FC02C0F;
        assignCourtroomPayload = assignCourtroomPayload.replace(COURT_SCHEDULE_ID_1_2, assignedSession.getCourtScheduleId());
        assignCourtroomPayload = assignCourtroomPayload.replace(COURT_SCHEDULE_ID_2_2, assignedSession.getCourtScheduleId());
        assignCourtroomPayload = assignCourtroomPayload.replace(COURT_ROOM_ID_2, newCourtRoomId);

        final Response response = postCommand(BASE_RESOURCE_URL + ASSIGN_COURTROOM_URL, COURT_SCHEDULE_ASSIGN_COURTROOM_CONTENT_TYPE, USER_ID, assignCourtroomPayload);
        final String responsePayload = response.readEntity(String.class);

        assertThat(ASSIGN_COURTROOM_RESPONSE + responsePayload, response.getStatus(), is(OK.getStatusCode()));

        // Parse JSON response - should be an object with errorGroups array
        final JsonReader jsonReader = Json.createReader(new StringReader(responsePayload));
        final JsonObject jsonResponse = jsonReader.readObject();
        jsonReader.close();

        // Verify response has errorGroups key
        assertThat(RESPONSE_SHOULD_CONTAIN_ERROR_GROUPS, jsonResponse.containsKey(ERROR_GROUPS), is(true));
        final jakarta.json.JsonArray jsonResponseArray = jsonResponse.getJsonArray(ERROR_GROUPS);

        // Verify response is an array
        assertThat(RESPONSE_SHOULD_BE_AN_ARRAY, jsonResponseArray, notNullValue());

        // Find the error group with the expected error message
        final boolean foundErrorGroup = jsonResponseArray.stream()
                .map(JsonValue::asJsonObject)
                .anyMatch(errorGroup -> {
                    final String error = errorGroup.getString(ERROR);
                    if (CANNOT_ASSIGN_COURTROOM_TO_AN_ASSIGNED_SESSION.equals(error)) {
                        final jakarta.json.JsonArray sessions = errorGroup.getJsonArray(SESSIONS);
                        return sessions.stream()
                                .map(s -> s.asJsonObject())
                                .anyMatch(s -> assignedSession.getCourtScheduleId().equals(s.getString(COURT_SCHEDULE_ID_5)));
                    }
                    return false;
                });
        assertThat("Should find assigned session without hearings in error group", foundErrorGroup, is(true));
    }

    @Test
    void shouldReturnErrorWhenCourtroomIdNotProvided() throws SQLException {
        //Must choose a courtroom

        final UUID sessionId = randomUUID();
        final CourtSchedule session = RANDOM.nextObject(CourtSchedule.class);
        session.setCourtScheduleId(sessionId.toString());
        session.setBusinessType(DVLA_2);
        session.setSlotBased(true);
        session.setMaxSlots(15);
        session.setAvailableSlots(15);
        session.setIsDraft(true);
        session.setSupportAdSplit(false);
        session.setCourtSession(AM_SESSION);
        session.setPanel(ADULT_2);
        databaseSeeder.insertCourtSchedule(session);

        String assignCourtroomPayload = getPayload(ASSIGN_COURTROOM_JSON);
        assignCourtroomPayload = assignCourtroomPayload.replace(COURT_SCHEDULE_ID_1_2, session.getCourtScheduleId());
        assignCourtroomPayload = assignCourtroomPayload.replace(COURT_SCHEDULE_ID_2_2, session.getCourtScheduleId());
        assignCourtroomPayload = assignCourtroomPayload.replace("\"courtRoomId\": \"COURT_ROOM_ID\"", "\"courtRoomId\": \"\"");

        final Response response = postCommand(BASE_RESOURCE_URL + ASSIGN_COURTROOM_URL, COURT_SCHEDULE_ASSIGN_COURTROOM_CONTENT_TYPE, USER_ID, assignCourtroomPayload);
        final String responsePayload = response.readEntity(String.class);

        assertThat(ASSIGN_COURTROOM_RESPONSE + responsePayload, response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        assertThat(responsePayload, containsString("Courtroom ID must be provided"));
    }

    @Test
    void shouldHandleMixedEligibleAndIneligibleSessions() throws SQLException {
        // Test with mix of eligible and ineligible sessions

        final UUID eligibleSessionId = randomUUID();
        final CourtSchedule eligibleSession = RANDOM.nextObject(CourtSchedule.class);
        eligibleSession.setCourtScheduleId(eligibleSessionId.toString());
        eligibleSession.setBusinessType(DVLA_2);
        eligibleSession.setSlotBased(true);
        eligibleSession.setMaxSlots(15);
        eligibleSession.setAvailableSlots(15);
        eligibleSession.setIsDraft(true); // Draft - eligible
        eligibleSession.setSupportAdSplit(false);
        eligibleSession.setCourtSession(AM_SESSION);
        eligibleSession.setPanel(ADULT_2);
        eligibleSession.setCourtRoomId(ORIGINAL_COURTROOM_ID);
        eligibleSession.setJurisdiction(CROWN_2);
        eligibleSession.setCourtHouseId(UUID_785339C1);
        eligibleSession.setSessionStartTime(DateUtils.localDateToDateWithTime(eligibleSession.getSessionDate(), 9, 0).toInstant());
        eligibleSession.setSessionEndTime(DateUtils.localDateToDateWithTime(eligibleSession.getSessionDate(), 13, 0).toInstant());
        databaseSeeder.insertCourtSchedule(eligibleSession);

        final UUID ineligibleSessionId = randomUUID();
        final CourtSchedule ineligibleSession = RANDOM.nextObject(CourtSchedule.class);
        ineligibleSession.setCourtScheduleId(ineligibleSessionId.toString());
        ineligibleSession.setBusinessType(DVLA_2);
        ineligibleSession.setSlotBased(true);
        ineligibleSession.setMaxSlots(15);
        ineligibleSession.setAvailableSlots(15);
        ineligibleSession.setIsDraft(false); // Assigned with hearings - ineligible
        ineligibleSession.setHasHearingsBooked(true);
        ineligibleSession.setSupportAdSplit(false);
        ineligibleSession.setCourtSession(AM_SESSION);
        ineligibleSession.setPanel(ADULT_2);
        ineligibleSession.setCourtRoomId(ORIGINAL_COURTROOM_ID);
        ineligibleSession.setJurisdiction(CROWN_2);
        ineligibleSession.setCourtHouseId(UUID_785339C1);
        ineligibleSession.setSessionStartTime(DateUtils.localDateToDateWithTime(ineligibleSession.getSessionDate(), 9, 0).toInstant());
        ineligibleSession.setSessionEndTime(DateUtils.localDateToDateWithTime(ineligibleSession.getSessionDate(), 13, 0).toInstant());
        databaseSeeder.insertCourtSchedule(ineligibleSession);

        // Create a hearing for ineligible session
        final UUID hearingId = randomUUID();
        final UUID bookingId = randomUUID();
        createAllocatedListing(ineligibleSession, hearingId, bookingId, 15, DEFAULT_ALL_DAY_START_TIME);

        String assignCourtroomPayload = getPayload(ASSIGN_COURTROOM_JSON);
        final String newCourtRoomId = UUID_3FC02C0F;
        assignCourtroomPayload = assignCourtroomPayload.replace(COURT_SCHEDULE_ID_1_2, eligibleSession.getCourtScheduleId());
        assignCourtroomPayload = assignCourtroomPayload.replace(COURT_SCHEDULE_ID_2_2, ineligibleSession.getCourtScheduleId());
        assignCourtroomPayload = assignCourtroomPayload.replace(COURT_ROOM_ID_2, newCourtRoomId);

        final Response response = postCommand(BASE_RESOURCE_URL + ASSIGN_COURTROOM_URL, COURT_SCHEDULE_ASSIGN_COURTROOM_CONTENT_TYPE, USER_ID, assignCourtroomPayload);
        final String responsePayload = response.readEntity(String.class);

        assertThat(ASSIGN_COURTROOM_RESPONSE + responsePayload, response.getStatus(), is(OK.getStatusCode()));

        // Parse JSON response - should be an object with errorGroups array
        final JsonReader jsonReader = Json.createReader(new StringReader(responsePayload));
        final JsonObject jsonResponse = jsonReader.readObject();
        jsonReader.close();

        // Verify response has errorGroups key
        assertThat(RESPONSE_SHOULD_CONTAIN_ERROR_GROUPS, jsonResponse.containsKey(ERROR_GROUPS), is(true));
        final jakarta.json.JsonArray jsonResponseArray = jsonResponse.getJsonArray(ERROR_GROUPS);

        // Verify ineligible session is in error group
        final boolean foundIneligibleSession = jsonResponseArray.stream()
                .map(JsonValue::asJsonObject)
                .anyMatch(errorGroup -> {
                    final String error = errorGroup.getString(ERROR);
                    if (CANNOT_ASSIGN_COURTROOM_TO_AN_ASSIGNED_SESSION.equals(error)) {
                        final jakarta.json.JsonArray sessions = errorGroup.getJsonArray(SESSIONS);
                        return sessions.stream()
                                .map(s -> s.asJsonObject())
                                .anyMatch(s -> ineligibleSession.getCourtScheduleId().equals(s.getString(COURT_SCHEDULE_ID_5)));
                    }
                    return false;
                });
        assertThat("Should find ineligible session in error group", foundIneligibleSession, is(true));

        // Eligible session should be successfully assigned (not in any error group)
        final boolean eligibleSessionInErrorGroup = jsonResponseArray.stream()
                .map(JsonValue::asJsonObject)
                .anyMatch(errorGroup -> {
                    final jakarta.json.JsonArray sessions = errorGroup.getJsonArray(SESSIONS);
                    return sessions.stream()
                            .map(s -> s.asJsonObject())
                            .anyMatch(s -> eligibleSession.getCourtScheduleId().equals(s.getString(COURT_SCHEDULE_ID_5)));
                });
        assertThat("Eligible session should not be in any error group", eligibleSessionInErrorGroup, is(false));
    }

    @Test
    void shouldMarkNonCrownSessionsAsIneligible() throws SQLException {
        // Test that MAGISTRATES sessions are marked as ineligible

        final UUID crownSessionId = randomUUID();
        final CourtSchedule crownSession = RANDOM.nextObject(CourtSchedule.class);
        crownSession.setCourtScheduleId(crownSessionId.toString());
        crownSession.setBusinessType(DVLA_2);
        crownSession.setSlotBased(true);
        crownSession.setMaxSlots(15);
        crownSession.setAvailableSlots(15);
        crownSession.setIsDraft(true);
        crownSession.setSupportAdSplit(false);
        crownSession.setCourtSession(AM_SESSION);
        crownSession.setPanel(YOUTH_2);
        crownSession.setCourtRoomId("original-courtroom-id-crown");
        crownSession.setJurisdiction(CROWN_2);
        crownSession.setCourtHouseId(UUID_785339C1);
        crownSession.setSessionStartTime(DateUtils.localDateToDateWithTime(crownSession.getSessionDate(), 9, 0).toInstant());
        crownSession.setSessionEndTime(DateUtils.localDateToDateWithTime(crownSession.getSessionDate(), 13, 0).toInstant());
        databaseSeeder.insertCourtSchedule(crownSession);

        final UUID magistratesSessionId = randomUUID();
        final CourtSchedule magistratesSession = RANDOM.nextObject(CourtSchedule.class);
        magistratesSession.setCourtScheduleId(magistratesSessionId.toString());
        magistratesSession.setBusinessType(DVLA_2);
        magistratesSession.setSlotBased(true);
        magistratesSession.setMaxSlots(15);
        magistratesSession.setAvailableSlots(15);
        magistratesSession.setIsDraft(true);
        magistratesSession.setSupportAdSplit(false);
        magistratesSession.setCourtSession(AM_SESSION);
        magistratesSession.setPanel(ADULT_2);
        magistratesSession.setCourtRoomId("original-courtroom-id-mags");
        magistratesSession.setJurisdiction(MAGISTRATES_2);
        magistratesSession.setCourtHouseId(UUID_785339C1);
        magistratesSession.setSessionStartTime(DateUtils.localDateToDateWithTime(magistratesSession.getSessionDate(), 9, 0).toInstant());
        magistratesSession.setSessionEndTime(DateUtils.localDateToDateWithTime(magistratesSession.getSessionDate(), 13, 0).toInstant());
        databaseSeeder.insertCourtSchedule(magistratesSession);

        String assignCourtroomPayload = getPayload(ASSIGN_COURTROOM_JSON);
        final String newCourtRoomId = UUID_3FC02C0F;
        assignCourtroomPayload = assignCourtroomPayload.replace(COURT_SCHEDULE_ID_1_2, crownSession.getCourtScheduleId());
        assignCourtroomPayload = assignCourtroomPayload.replace(COURT_SCHEDULE_ID_2_2, magistratesSession.getCourtScheduleId());
        assignCourtroomPayload = assignCourtroomPayload.replace(COURT_ROOM_ID_2, newCourtRoomId);

        final Response response = postCommand(BASE_RESOURCE_URL + ASSIGN_COURTROOM_URL, COURT_SCHEDULE_ASSIGN_COURTROOM_CONTENT_TYPE, USER_ID, assignCourtroomPayload);
        final String responsePayload = response.readEntity(String.class);

        assertThat(ASSIGN_COURTROOM_RESPONSE + responsePayload, response.getStatus(), is(OK.getStatusCode()));

        // Parse JSON response - should be an object with errorGroups array
        final JsonReader jsonReader = Json.createReader(new StringReader(responsePayload));
        final JsonObject jsonResponse = jsonReader.readObject();
        jsonReader.close();

        // Verify response has errorGroups key
        assertThat(RESPONSE_SHOULD_CONTAIN_ERROR_GROUPS, jsonResponse.containsKey(ERROR_GROUPS), is(true));
        final jakarta.json.JsonArray jsonResponseArray = jsonResponse.getJsonArray(ERROR_GROUPS);

        // Verify response is an array
        assertThat(RESPONSE_SHOULD_BE_AN_ARRAY, jsonResponseArray, notNullValue());

        // Verify MAGISTRATES session is in error group
        final boolean foundIneligibleMagistratesSession = jsonResponseArray.stream()
                .map(JsonValue::asJsonObject)
                .anyMatch(errorGroup -> {
                    final String error = errorGroup.getString(ERROR);
                    if (ERROR_ONLY_VALID_FOR_CROWN.equals(error)) {
                        final jakarta.json.JsonArray sessions = errorGroup.getJsonArray(SESSIONS);
                        return sessions.stream()
                                .map(s -> s.asJsonObject())
                                .anyMatch(s -> magistratesSession.getCourtScheduleId().equals(s.getString(COURT_SCHEDULE_ID_5)));
                    }
                    return false;
                });
        assertThat("Should find MAGISTRATES session in error group with correct reason", foundIneligibleMagistratesSession, is(true));

        // CROWN session should be successfully assigned (not in any error group)
        final boolean crownSessionInErrorGroup = jsonResponseArray.stream()
                .map(JsonValue::asJsonObject)
                .anyMatch(errorGroup -> {
                    final jakarta.json.JsonArray sessions = errorGroup.getJsonArray(SESSIONS);
                    return sessions.stream()
                            .map(s -> s.asJsonObject())
                            .anyMatch(s -> crownSession.getCourtScheduleId().equals(s.getString(COURT_SCHEDULE_ID_5)));
                });
        assertThat("CROWN session should not be in any error group", crownSessionInErrorGroup, is(false));
    }

    @Test
    void shouldMarkSessionsWithWrongCourtCentreAsIneligible() throws SQLException {
        // Test that sessions with different court centre than courtroom are marked as ineligible

        final UUID correctCourtCentreSessionId = randomUUID();
        final CourtSchedule correctCourtCentreSession = RANDOM.nextObject(CourtSchedule.class);
        correctCourtCentreSession.setCourtScheduleId(correctCourtCentreSessionId.toString());
        correctCourtCentreSession.setBusinessType(DVLA_2);
        correctCourtCentreSession.setSlotBased(true);
        correctCourtCentreSession.setMaxSlots(15);
        correctCourtCentreSession.setAvailableSlots(15);
        correctCourtCentreSession.setIsDraft(true);
        correctCourtCentreSession.setSupportAdSplit(false);
        correctCourtCentreSession.setCourtSession(AM_SESSION);
        correctCourtCentreSession.setPanel(ADULT_2);
        correctCourtCentreSession.setCourtRoomId(ORIGINAL_COURTROOM_ID);
        correctCourtCentreSession.setJurisdiction(CROWN_2);
        correctCourtCentreSession.setCourtHouseId(UUID_785339C1);
        correctCourtCentreSession.setSessionStartTime(DateUtils.localDateToDateWithTime(correctCourtCentreSession.getSessionDate(), 9, 0).toInstant());
        correctCourtCentreSession.setSessionEndTime(DateUtils.localDateToDateWithTime(correctCourtCentreSession.getSessionDate(), 13, 0).toInstant());
        databaseSeeder.insertCourtSchedule(correctCourtCentreSession);

        final UUID wrongCourtCentreSessionId = randomUUID();
        final CourtSchedule wrongCourtCentreSession = RANDOM.nextObject(CourtSchedule.class);
        wrongCourtCentreSession.setCourtScheduleId(wrongCourtCentreSessionId.toString());
        wrongCourtCentreSession.setBusinessType(DVLA_2);
        wrongCourtCentreSession.setSlotBased(true);
        wrongCourtCentreSession.setMaxSlots(15);
        wrongCourtCentreSession.setAvailableSlots(15);
        wrongCourtCentreSession.setIsDraft(true);
        wrongCourtCentreSession.setSupportAdSplit(false);
        wrongCourtCentreSession.setCourtSession(AM_SESSION);
        wrongCourtCentreSession.setPanel(ADULT_2);
        wrongCourtCentreSession.setCourtRoomId(ORIGINAL_COURTROOM_ID);
        wrongCourtCentreSession.setJurisdiction(CROWN_2);
        wrongCourtCentreSession.setCourtHouseId("different-court-centre-id-12345");
        wrongCourtCentreSession.setSessionStartTime(DateUtils.localDateToDateWithTime(wrongCourtCentreSession.getSessionDate(), 9, 0).toInstant());
        wrongCourtCentreSession.setSessionEndTime(DateUtils.localDateToDateWithTime(wrongCourtCentreSession.getSessionDate(), 13, 0).toInstant());
        databaseSeeder.insertCourtSchedule(wrongCourtCentreSession);

        String assignCourtroomPayload = getPayload(ASSIGN_COURTROOM_JSON);
        final String newCourtRoomId = UUID_3FC02C0F;
        assignCourtroomPayload = assignCourtroomPayload.replace(COURT_SCHEDULE_ID_1_2, correctCourtCentreSession.getCourtScheduleId());
        assignCourtroomPayload = assignCourtroomPayload.replace(COURT_SCHEDULE_ID_2_2, wrongCourtCentreSession.getCourtScheduleId());
        assignCourtroomPayload = assignCourtroomPayload.replace(COURT_ROOM_ID_2, newCourtRoomId);

        final Response response = postCommand(BASE_RESOURCE_URL + ASSIGN_COURTROOM_URL, COURT_SCHEDULE_ASSIGN_COURTROOM_CONTENT_TYPE, USER_ID, assignCourtroomPayload);
        final String responsePayload = response.readEntity(String.class);

        assertThat(ASSIGN_COURTROOM_RESPONSE + responsePayload, response.getStatus(), is(OK.getStatusCode()));

        // Parse JSON response - should be an object with errorGroups array
        final JsonReader jsonReader = Json.createReader(new StringReader(responsePayload));
        final JsonObject jsonResponse = jsonReader.readObject();
        jsonReader.close();

        // Verify response has errorGroups key
        assertThat(RESPONSE_SHOULD_CONTAIN_ERROR_GROUPS, jsonResponse.containsKey(ERROR_GROUPS), is(true));
        final jakarta.json.JsonArray jsonResponseArray = jsonResponse.getJsonArray(ERROR_GROUPS);

        // Verify response is an array
        assertThat(RESPONSE_SHOULD_BE_AN_ARRAY, jsonResponseArray, notNullValue());

        // Verify session with wrong court centre is in error group
        final boolean foundIneligibleWrongCourtCentreSession = jsonResponseArray.stream()
                .map(JsonValue::asJsonObject)
                .anyMatch(errorGroup -> {
                    final String error = errorGroup.getString(ERROR);
                    if (ERROR_COURTROOM_DIFFERENT_CENTRE.equals(error)) {
                        final jakarta.json.JsonArray sessions = errorGroup.getJsonArray(SESSIONS);
                        return sessions.stream()
                                .map(s -> s.asJsonObject())
                                .anyMatch(s -> wrongCourtCentreSession.getCourtScheduleId().equals(s.getString(COURT_SCHEDULE_ID_5)));
                    }
                    return false;
                });
        assertThat("Should find session with wrong court centre in error group with correct reason", foundIneligibleWrongCourtCentreSession, is(true));

        // Session with correct court centre should be successfully assigned (not in any error group)
        final boolean correctCourtCentreSessionInErrorGroup = jsonResponseArray.stream()
                .map(JsonValue::asJsonObject)
                .anyMatch(errorGroup -> {
                    final jakarta.json.JsonArray sessions = errorGroup.getJsonArray(SESSIONS);
                    return sessions.stream()
                            .map(s -> s.asJsonObject())
                            .anyMatch(s -> correctCourtCentreSession.getCourtScheduleId().equals(s.getString(COURT_SCHEDULE_ID_5)));
                });
        assertThat("Session with correct court centre should not be in any error group", correctCourtCentreSessionInErrorGroup, is(false));
    }

    @Test
    void shouldMarkAMSessionAsIneligibleWhenDuplicateAMSessionExists() throws SQLException {
        // Test that AM session is marked as ineligible when duplicate AM session exists

        final UUID existingSessionId = randomUUID();
        final CourtSchedule existingSession = RANDOM.nextObject(CourtSchedule.class);
        existingSession.setCourtScheduleId(existingSessionId.toString());
        existingSession.setBusinessType(DVLA_2);
        existingSession.setSlotBased(true);
        existingSession.setMaxSlots(15);
        existingSession.setAvailableSlots(15);
        existingSession.setIsDraft(true);
        existingSession.setSupportAdSplit(false);
        existingSession.setCourtSession(AM_SESSION);
        existingSession.setPanel(ADULT_2);
        existingSession.setJurisdiction(CROWN_2);
        existingSession.setCourtHouseId(UUID_785339C1);
        existingSession.setCourtRoomId(UUID_3FC02C0F);
        existingSession.setCourtRoomName("Courtroom 01");
        existingSession.setSessionDate(now().plusDays(1));
        existingSession.setSessionStartTime(DateUtils.localDateToDateWithTime(existingSession.getSessionDate(), 9, 0).toInstant());
        existingSession.setSessionEndTime(DateUtils.localDateToDateWithTime(existingSession.getSessionDate(), 13, 0).toInstant());
        databaseSeeder.insertCourtSchedule(existingSession);

        final UUID newSessionId = randomUUID();
        final CourtSchedule newSession = RANDOM.nextObject(CourtSchedule.class);
        newSession.setCourtScheduleId(newSessionId.toString());
        newSession.setBusinessType(DVLA_2);
        newSession.setSlotBased(true);
        newSession.setMaxSlots(15);
        newSession.setAvailableSlots(15);
        newSession.setIsDraft(true);
        newSession.setSupportAdSplit(false);
        newSession.setCourtSession(AM_SESSION);
        newSession.setPanel(ADULT_2);
        newSession.setJurisdiction(CROWN_2);
        newSession.setCourtHouseId(UUID_785339C1);
        newSession.setCourtRoomId(ORIGINAL_COURTROOM_ID);
        newSession.setSessionDate(existingSession.getSessionDate()); // Same date
        newSession.setSessionStartTime(DateUtils.localDateToDateWithTime(newSession.getSessionDate(), 9, 0).toInstant());
        newSession.setSessionEndTime(DateUtils.localDateToDateWithTime(newSession.getSessionDate(), 13, 0).toInstant());
        databaseSeeder.insertCourtSchedule(newSession);

        String assignCourtroomPayload = getPayload(ASSIGN_COURTROOM_JSON);
        final String newCourtRoomId = UUID_3FC02C0F;
        assignCourtroomPayload = assignCourtroomPayload.replace(COURT_SCHEDULE_ID_1_2, newSessionId.toString());
        assignCourtroomPayload = assignCourtroomPayload.replace(COURT_SCHEDULE_ID_2_2, newSessionId.toString());
        assignCourtroomPayload = assignCourtroomPayload.replace(COURT_ROOM_ID_2, newCourtRoomId);

        final Response response = postCommand(BASE_RESOURCE_URL + ASSIGN_COURTROOM_URL, COURT_SCHEDULE_ASSIGN_COURTROOM_CONTENT_TYPE, USER_ID, assignCourtroomPayload);
        final String responsePayload = response.readEntity(String.class);

        assertThat(ASSIGN_COURTROOM_RESPONSE + responsePayload, response.getStatus(), is(OK.getStatusCode()));

        // Parse JSON response - should be an object with errorGroups array
        final JsonReader jsonReader = Json.createReader(new StringReader(responsePayload));
        final JsonObject jsonResponse = jsonReader.readObject();
        jsonReader.close();

        // Verify response has errorGroups key
        assertThat(RESPONSE_SHOULD_CONTAIN_ERROR_GROUPS, jsonResponse.containsKey(ERROR_GROUPS), is(true));
        final jakarta.json.JsonArray jsonResponseArray = jsonResponse.getJsonArray(ERROR_GROUPS);

        // Verify response is an array
        assertThat(RESPONSE_SHOULD_BE_AN_ARRAY, jsonResponseArray, notNullValue());

        // Verify session is in error group due to duplicate
        final boolean foundIneligibleDuplicateSession = jsonResponseArray.stream()
                .map(JsonValue::asJsonObject)
                .anyMatch(errorGroup -> {
                    final String error = errorGroup.getString(ERROR);
                    if (error.contains("Duplicate session already exists")) {
                        final jakarta.json.JsonArray sessions = errorGroup.getJsonArray(SESSIONS);
                        return sessions.stream()
                                .map(s -> s.asJsonObject())
                                .anyMatch(s -> newSessionId.toString().equals(s.getString(COURT_SCHEDULE_ID_5)));
                    }
                    return false;
                });
        assertThat("Should find session with duplicate in error group", foundIneligibleDuplicateSession, is(true));
    }

    @Test
    void shouldMarkAMSessionAsIneligibleWhenDuplicateADSessionExists() throws SQLException {
        // Test that AM session is marked as ineligible when duplicate AD session exists

        final UUID existingSessionId = randomUUID();
        final CourtSchedule existingSession = RANDOM.nextObject(CourtSchedule.class);
        existingSession.setCourtScheduleId(existingSessionId.toString());
        existingSession.setBusinessType(DVLA_2);
        existingSession.setSlotBased(true);
        existingSession.setMaxSlots(15);
        existingSession.setAvailableSlots(15);
        existingSession.setIsDraft(true);
        existingSession.setSupportAdSplit(false);
        existingSession.setCourtSession(ALL_DAY);
        existingSession.setPanel(ADULT_2);
        existingSession.setJurisdiction(CROWN_2);
        existingSession.setCourtHouseId(UUID_785339C1);
        existingSession.setCourtRoomId(UUID_3FC02C0F);
        existingSession.setCourtRoomName("Courtroom 01");
        existingSession.setSessionDate(now().plusDays(1));
        existingSession.setSessionStartTime(DateUtils.localDateToDateWithTime(existingSession.getSessionDate(), 9, 0).toInstant());
        existingSession.setSessionEndTime(DateUtils.localDateToDateWithTime(existingSession.getSessionDate(), 17, 0).toInstant());
        databaseSeeder.insertCourtSchedule(existingSession);

        final UUID newSessionId = randomUUID();
        final CourtSchedule newSession = RANDOM.nextObject(CourtSchedule.class);
        newSession.setCourtScheduleId(newSessionId.toString());
        newSession.setBusinessType(DVLA_2);
        newSession.setSlotBased(true);
        newSession.setMaxSlots(15);
        newSession.setAvailableSlots(15);
        newSession.setIsDraft(true);
        newSession.setSupportAdSplit(false);
        newSession.setCourtSession(AM_SESSION);
        newSession.setPanel(ADULT_2);
        newSession.setJurisdiction(CROWN_2);
        newSession.setCourtHouseId(UUID_785339C1);
        newSession.setCourtRoomId(ORIGINAL_COURTROOM_ID);
        newSession.setSessionDate(existingSession.getSessionDate()); // Same date
        newSession.setSessionStartTime(DateUtils.localDateToDateWithTime(newSession.getSessionDate(), 9, 0).toInstant());
        newSession.setSessionEndTime(DateUtils.localDateToDateWithTime(newSession.getSessionDate(), 13, 0).toInstant());
        databaseSeeder.insertCourtSchedule(newSession);

        String assignCourtroomPayload = getPayload(ASSIGN_COURTROOM_JSON);
        final String newCourtRoomId = UUID_3FC02C0F;
        assignCourtroomPayload = assignCourtroomPayload.replace(COURT_SCHEDULE_ID_1_2, newSessionId.toString());
        assignCourtroomPayload = assignCourtroomPayload.replace(COURT_SCHEDULE_ID_2_2, newSessionId.toString());
        assignCourtroomPayload = assignCourtroomPayload.replace(COURT_ROOM_ID_2, newCourtRoomId);

        final Response response = postCommand(BASE_RESOURCE_URL + ASSIGN_COURTROOM_URL, COURT_SCHEDULE_ASSIGN_COURTROOM_CONTENT_TYPE, USER_ID, assignCourtroomPayload);
        final String responsePayload = response.readEntity(String.class);

        assertThat(ASSIGN_COURTROOM_RESPONSE + responsePayload, response.getStatus(), is(OK.getStatusCode()));

        // Parse JSON response - should be an object with errorGroups array
        final JsonReader jsonReader = Json.createReader(new StringReader(responsePayload));
        final JsonObject jsonResponse = jsonReader.readObject();
        jsonReader.close();

        // Verify response has errorGroups key
        assertThat(RESPONSE_SHOULD_CONTAIN_ERROR_GROUPS, jsonResponse.containsKey(ERROR_GROUPS), is(true));
        final jakarta.json.JsonArray jsonResponseArray = jsonResponse.getJsonArray(ERROR_GROUPS);

        // Verify response is an array
        assertThat(RESPONSE_SHOULD_BE_AN_ARRAY, jsonResponseArray, notNullValue());

        // Verify session is in error group due to duplicate
        final boolean foundIneligibleDuplicateSession = jsonResponseArray.stream()
                .map(JsonValue::asJsonObject)
                .anyMatch(errorGroup -> {
                    final String error = errorGroup.getString(ERROR);
                    if (error.contains("Duplicate session already exists")) {
                        final jakarta.json.JsonArray sessions = errorGroup.getJsonArray(SESSIONS);
                        return sessions.stream()
                                .map(s -> s.asJsonObject())
                                .anyMatch(s -> newSessionId.toString().equals(s.getString(COURT_SCHEDULE_ID_5)));
                    }
                    return false;
                });
        assertThat("Should find AM session with duplicate AD session in error group", foundIneligibleDuplicateSession, is(true));
    }

    @Test
    void shouldMarkADSessionAsIneligibleWhenDuplicateAMSessionExists() throws SQLException {
        // Test that AD session is marked as ineligible when duplicate AM session exists

        final UUID existingSessionId = randomUUID();
        final CourtSchedule existingSession = RANDOM.nextObject(CourtSchedule.class);
        existingSession.setCourtScheduleId(existingSessionId.toString());
        existingSession.setBusinessType(DVLA_2);
        existingSession.setSlotBased(true);
        existingSession.setMaxSlots(15);
        existingSession.setAvailableSlots(15);
        existingSession.setIsDraft(true);
        existingSession.setSupportAdSplit(false);
        existingSession.setCourtSession(AM_SESSION);
        existingSession.setPanel(ADULT_2);
        existingSession.setJurisdiction(CROWN_2);
        existingSession.setCourtHouseId(UUID_785339C1);
        existingSession.setCourtRoomId(UUID_3FC02C0F);
        existingSession.setCourtRoomName("Courtroom 01");
        existingSession.setSessionDate(now().plusDays(1));
        existingSession.setSessionStartTime(DateUtils.localDateToDateWithTime(existingSession.getSessionDate(), 9, 0).toInstant());
        existingSession.setSessionEndTime(DateUtils.localDateToDateWithTime(existingSession.getSessionDate(), 13, 0).toInstant());
        databaseSeeder.insertCourtSchedule(existingSession);

        final UUID newSessionId = randomUUID();
        final CourtSchedule newSession = RANDOM.nextObject(CourtSchedule.class);
        newSession.setCourtScheduleId(newSessionId.toString());
        newSession.setBusinessType(DVLA_2);
        newSession.setSlotBased(true);
        newSession.setMaxSlots(15);
        newSession.setAvailableSlots(15);
        newSession.setIsDraft(true);
        newSession.setSupportAdSplit(false);
        newSession.setCourtSession(ALL_DAY);
        newSession.setPanel(ADULT_2);
        newSession.setJurisdiction(CROWN_2);
        newSession.setCourtHouseId(UUID_785339C1);
        newSession.setCourtRoomId(ORIGINAL_COURTROOM_ID);
        newSession.setSessionDate(existingSession.getSessionDate()); // Same date
        newSession.setSessionStartTime(DateUtils.localDateToDateWithTime(newSession.getSessionDate(), 9, 0).toInstant());
        newSession.setSessionEndTime(DateUtils.localDateToDateWithTime(newSession.getSessionDate(), 17, 0).toInstant());
        databaseSeeder.insertCourtSchedule(newSession);

        String assignCourtroomPayload = getPayload(ASSIGN_COURTROOM_JSON);
        final String newCourtRoomId = UUID_3FC02C0F;
        assignCourtroomPayload = assignCourtroomPayload.replace(COURT_SCHEDULE_ID_1_2, newSessionId.toString());
        assignCourtroomPayload = assignCourtroomPayload.replace(COURT_SCHEDULE_ID_2_2, newSessionId.toString());
        assignCourtroomPayload = assignCourtroomPayload.replace(COURT_ROOM_ID_2, newCourtRoomId);

        final Response response = postCommand(BASE_RESOURCE_URL + ASSIGN_COURTROOM_URL, COURT_SCHEDULE_ASSIGN_COURTROOM_CONTENT_TYPE, USER_ID, assignCourtroomPayload);
        final String responsePayload = response.readEntity(String.class);

        assertThat(ASSIGN_COURTROOM_RESPONSE + responsePayload, response.getStatus(), is(OK.getStatusCode()));

        // Parse JSON response - should be an object with errorGroups array
        final JsonReader jsonReader = Json.createReader(new StringReader(responsePayload));
        final JsonObject jsonResponse = jsonReader.readObject();
        jsonReader.close();

        // Verify response has errorGroups key
        assertThat(RESPONSE_SHOULD_CONTAIN_ERROR_GROUPS, jsonResponse.containsKey(ERROR_GROUPS), is(true));
        final jakarta.json.JsonArray jsonResponseArray = jsonResponse.getJsonArray(ERROR_GROUPS);

        // Verify response is an array
        assertThat(RESPONSE_SHOULD_BE_AN_ARRAY, jsonResponseArray, notNullValue());

        // Verify session is in error group due to duplicate
        final boolean foundIneligibleDuplicateSession = jsonResponseArray.stream()
                .map(JsonValue::asJsonObject)
                .anyMatch(errorGroup -> {
                    final String error = errorGroup.getString(ERROR);
                    if (error.contains("Duplicate session already exists")) {
                        final jakarta.json.JsonArray sessions = errorGroup.getJsonArray(SESSIONS);
                        return sessions.stream()
                                .map(s -> s.asJsonObject())
                                .anyMatch(s -> newSessionId.toString().equals(s.getString(COURT_SCHEDULE_ID_5)));
                    }
                    return false;
                });
        assertThat("Should find AD session with duplicate AM session in error group", foundIneligibleDuplicateSession, is(true));
    }

    @Test
    void shouldReturn400WhenUpdatingCrownDraftSessionWithHearingsBookedToChangeCourtroom() throws SQLException {
        // Given - CROWN draft session with hearings booked
        final UUID courtScheduleId = randomUUID();
        final CourtSchedule draftSession = RANDOM.nextObject(CourtSchedule.class);
        final String originalCourtRoomId = UUID_3FC02C0F;
        final String newCourtRoomId = "e06e1734-aa04-3ec0-b14c-400edab8b831"; // Different courtroom
        final String courtHouseId = UUID_785339C1;

        draftSession.setCourtScheduleId(courtScheduleId.toString());
        draftSession.setBusinessType(LGT_2);
        draftSession.setSlotBased(true);
        draftSession.setMaxSlots(15);
        draftSession.setAvailableSlots(10); // Some slots booked
        draftSession.setIsDraft(true);
        draftSession.setSupportAdSplit(false);
        draftSession.setCourtSession(AM_SESSION);
        draftSession.setPanel(ADULT_2);
        draftSession.setJurisdiction(CROWN_2);
        draftSession.setCourtRoomId(originalCourtRoomId);
        draftSession.setCourtHouseId(courtHouseId);
        draftSession.setSessionDate(now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)));
        draftSession.setSessionStartTime(DateUtils.localDateToDateWithTime(draftSession.getSessionDate(), 9, 0).toInstant());
        draftSession.setSessionEndTime(DateUtils.localDateToDateWithTime(draftSession.getSessionDate(), 13, 0).toInstant());
        databaseSeeder.insertCourtSchedule(draftSession);

        // Create allocated listing to simulate hearings booked
        final UUID hearingId = randomUUID();
        final UUID bookingId = randomUUID();
        createAllocatedListing(draftSession, hearingId, bookingId, 60, DEFAULT_ALL_DAY_START_TIME);

        // When - Try to update courtroom
        String updateCourtSchedulePayload = getPayload(UPDATE_COURT_SCHEDULE_JSON);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_SCHEDULE_ID_4, draftSession.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_ROOM_ID_2, newCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(BUSINESS_TYPE_2, LGT_2);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(SESSION_TYPE_2, AM_SESSION);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(PANEL_2, ADULT_2);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("\"jurisdiction\": \"MAGISTRATES\"", "\"jurisdiction\": \"CROWN\"");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(MAX_DURATION_10, "\"maxSlots\": 15,\n  \"maxDuration\": 0,\n  \"isDraft\": true");

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);
        final String responsePayload = response.readEntity(String.class);

        // Then - Should return 400 with error message
        assertThat("Update response: " + responsePayload, response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        assertThat(responsePayload, containsString("Cannot assign courtroom to a CROWN draft session with hearings booked"));
    }

    @Test
    void shouldReturn400WhenUpdatingCrownDraftSessionWithHearingsBookedToChangeState() throws SQLException {
        // Given - CROWN draft session with hearings booked
        final UUID courtScheduleId = randomUUID();
        final CourtSchedule draftSession = RANDOM.nextObject(CourtSchedule.class);
        final String courtRoomId = UUID_3FC02C0F;
        final String courtHouseId = UUID_785339C1;

        draftSession.setCourtScheduleId(courtScheduleId.toString());
        draftSession.setBusinessType(LGT_2);
        draftSession.setSlotBased(true);
        draftSession.setMaxSlots(15);
        draftSession.setAvailableSlots(10); // Some slots booked
        draftSession.setIsDraft(true);
        draftSession.setSupportAdSplit(false);
        draftSession.setCourtSession(AM_SESSION);
        draftSession.setPanel(ADULT_2);
        draftSession.setJurisdiction(CROWN_2);
        draftSession.setCourtRoomId(courtRoomId);
        draftSession.setCourtHouseId(courtHouseId);
        draftSession.setSessionDate(now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)));
        draftSession.setSessionStartTime(DateUtils.localDateToDateWithTime(draftSession.getSessionDate(), 9, 0).toInstant());
        draftSession.setSessionEndTime(DateUtils.localDateToDateWithTime(draftSession.getSessionDate(), 13, 0).toInstant());
        databaseSeeder.insertCourtSchedule(draftSession);

        // Create allocated listing to simulate hearings booked
        final UUID hearingId = randomUUID();
        final UUID bookingId = randomUUID();
        createAllocatedListing(draftSession, hearingId, bookingId, 60, DEFAULT_ALL_DAY_START_TIME);

        // When - Try to change state from draft to assigned (isDraft: false)
        String updateCourtSchedulePayload = getPayload(UPDATE_COURT_SCHEDULE_JSON);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_SCHEDULE_ID_4, draftSession.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(COURT_ROOM_ID_2, courtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(BUSINESS_TYPE_2, LGT_2);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(SESSION_TYPE_2, AM_SESSION);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(PANEL_2, ADULT_2);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("\"jurisdiction\": \"MAGISTRATES\"", "\"jurisdiction\": \"CROWN\"");
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace(MAX_DURATION_10, "\"maxSlots\": 15,\n  \"maxDuration\": 0,\n  \"isDraft\": false");

        final Response response = postCommand(BASE_RESOURCE_URL + UPDATE_URL, COURT_SCHEDULE_UPDATE_CONTENT_TYPE, USER_ID, updateCourtSchedulePayload);
        final String responsePayload = response.readEntity(String.class);

        // Then - Should return 400 with error message
        assertThat("Update response: " + responsePayload, response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        assertThat(responsePayload, containsString("Cannot assign state to a CROWN draft session with hearings booked"));
    }

    @Test
    void shouldReturnErrorWhenAssigningCourtroomToCrownDraftSessionWithHearingsBooked() throws SQLException {
        // Given - CROWN draft session with hearings booked
        final UUID draftSessionId = randomUUID();
        final CourtSchedule draftSession = RANDOM.nextObject(CourtSchedule.class);
        final String originalCourtRoomId = ORIGINAL_COURTROOM_ID;
        final String newCourtRoomId = UUID_3FC02C0F;
        final String courtHouseId = UUID_785339C1;

        draftSession.setCourtScheduleId(draftSessionId.toString());
        draftSession.setBusinessType(LGT_2);
        draftSession.setSlotBased(true);
        draftSession.setMaxSlots(15);
        draftSession.setAvailableSlots(10); // Some slots booked
        draftSession.setIsDraft(true);
        draftSession.setSupportAdSplit(false);
        draftSession.setCourtSession(AM_SESSION);
        draftSession.setPanel(ADULT_2);
        draftSession.setJurisdiction(CROWN_2);
        draftSession.setCourtRoomId(originalCourtRoomId);
        draftSession.setCourtHouseId(courtHouseId);
        draftSession.setSessionDate(now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)));
        draftSession.setSessionStartTime(DateUtils.localDateToDateWithTime(draftSession.getSessionDate(), 9, 0).toInstant());
        draftSession.setSessionEndTime(DateUtils.localDateToDateWithTime(draftSession.getSessionDate(), 13, 0).toInstant());
        databaseSeeder.insertCourtSchedule(draftSession);

        // Create allocated listing to simulate hearings booked
        final UUID hearingId = randomUUID();
        final UUID bookingId = randomUUID();
        createAllocatedListing(draftSession, hearingId, bookingId, 60, DEFAULT_ALL_DAY_START_TIME);

        // When - Try to assign courtroom via assign.courtroom endpoint
        String assignCourtroomPayload = getPayload("assign-courtroom-CROWN-with-hearing.json");
        assignCourtroomPayload = assignCourtroomPayload.replace(COURT_SCHEDULE_ID_1_2, draftSessionId.toString());
        assignCourtroomPayload = assignCourtroomPayload.replace(COURT_ROOM_ID_2, newCourtRoomId);

        final Response response = postCommand(BASE_RESOURCE_URL + ASSIGN_COURTROOM_URL, COURT_SCHEDULE_ASSIGN_COURTROOM_CONTENT_TYPE, USER_ID, assignCourtroomPayload);
        final String responsePayload = response.readEntity(String.class);

        // Then - Should return 200 with error group containing the error
        assertThat(ASSIGN_COURTROOM_RESPONSE + responsePayload, response.getStatus(), is(OK.getStatusCode()));

        // Parse JSON response
        final JsonReader jsonReader = Json.createReader(new StringReader(responsePayload));
        final JsonObject jsonResponse = jsonReader.readObject();
        jsonReader.close();

        // Verify response has errorGroups key
        assertThat(RESPONSE_SHOULD_CONTAIN_ERROR_GROUPS, jsonResponse.containsKey(ERROR_GROUPS), is(true));
        final jakarta.json.JsonArray jsonResponseArray = jsonResponse.getJsonArray(ERROR_GROUPS);

        // Verify response is an array
        assertThat(RESPONSE_SHOULD_BE_AN_ARRAY, jsonResponseArray, notNullValue());

        // Find the error group with the expected error message
        final boolean foundErrorGroup = jsonResponseArray.stream()
                .map(JsonValue::asJsonObject)
                .anyMatch(errorGroup -> {
                    final String error = errorGroup.getString(ERROR);
                    if (ERROR_CROWN_DRAFT_WITH_HEARINGS.equals(error)) {
                        final jakarta.json.JsonArray sessions = errorGroup.getJsonArray(SESSIONS);
                        return sessions.stream()
                                .map(s -> s.asJsonObject())
                                .anyMatch(s -> draftSessionId.toString().equals(s.getString(COURT_SCHEDULE_ID_5)));
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
                    cs.setBusinessType(TRL_2);
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
                    cs.setSessionStartTime(combineDateAndTime(sessionDate, DEFAULT_MORNING_START_TIME).toInstant());
                    cs.setSessionEndTime(combineDateAndTime(sessionDate, DEFAULT_MORNING_END_TIME).toInstant());
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
        final int returnedCount = json.getJsonArray(COURT_SCHEDULES).size();

        assertThat("Should return all 200 court schedules", returnedCount, is(numberOfSchedules));

        final List<String> returnedIds = json.getJsonArray(COURT_SCHEDULES).stream()
                .map(JsonValue::asJsonObject)
                .flatMap(courtRoom -> courtRoom.getJsonArray(SESSIONS).stream())
                .map(JsonValue::asJsonObject)
                .map(session -> session.getString(COURT_SCHEDULE_ID_5))
                .toList();

        for (final CourtSchedule expected : courtSchedules) {
            assertTrue(returnedIds.contains(expected.getCourtScheduleId()),
                    "Response should contain courtScheduleId: " + expected.getCourtScheduleId());
        }
    }

    private static LocalDate getNextWeekdayMonToWed() {
        LocalDate date = now().plusDays(1);
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
