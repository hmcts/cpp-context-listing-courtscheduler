package uk.gov.moj.cpp.courtscheduler.integration;

import static jakarta.json.Json.createArrayBuilder;
import static jakarta.json.Json.createObjectBuilder;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.OK;
import static java.time.DayOfWeek.MONDAY;
import static java.time.LocalDate.now;
import static java.time.temporal.TemporalAdjusters.next;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.DUPLICATE_SESSIONS;

import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.UUID;

import jakarta.json.JsonObjectBuilder;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * POST /validate (validate.create) checks the session to be added against the sessions already
 * stored and against the other sessions in the payload. A draft and a final session never clash,
 * the same as the is_draft column in the unique indexes of changeset 051, and a session without
 * isDraft counts as final because that is how it is stored. Inside one draft state an AD clashes
 * with AM, PM and AD on the same day; AM and PM do not clash.
 *
 * <p>The stored-session cases use the C45GU00 court centre and its shared courtroom, which no other
 * IT stores sessions on.</p>
 */
class ValidateCreateSessionDraftUniquenessIT extends AbstractIT {

    private static final String VALIDATE_URL = "/validate";
    private static final String VALIDATE_CREATE_CONTENT_TYPE = "application/vnd.courtscheduler.validate.create+json";
    private static final String COURT_CENTRE_ID = "b45de9f1-2c7a-3f4e-9d68-1a2b3c4d5e6f";
    private static final String COURT_ROOM_ID = "77777777-6666-3555-8444-333333333333";
    private static final String OU_CODE = "C45GU00";
    private static final String BUSINESS_TYPE = "TRL";
    private static final String CROWN = "CROWN";
    private static final String ALL_DAY = "AD";
    private static final String MORNING = "AM";
    private static final String AFTERNOON = "PM";
    private static final String MAGISTRATES_COURT_CENTRE_ID = "49db2271-1941-3847-a7fb-dbd92b035e40";
    private static final String MAGISTRATES_COURT_ROOM_ID = "41a41156-0ca9-3352-999e-8d26274bb1aa";

    private final LocalDate weekStart = now().with(next(MONDAY));
    private final LocalDate thursday = weekStart.plusDays(3);

    @ParameterizedTest
    @ValueSource(strings = {ALL_DAY, MORNING, AFTERNOON})
    void shouldAcceptDraftSessionWhenOnlyAFinalAllDaySessionExistsOnThatDay(final String sessionType) throws Exception {
        databaseSeeder.insertCourtSchedule(storedSession(ALL_DAY, false));

        final Response response = validateCreate(sessionType, true);

        final String body = response.readEntity(String.class);
        assertThat("validate-create answered with body " + body, response.getStatus(), is(OK.getStatusCode()));
        assertThat(body, is("{}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {ALL_DAY, MORNING, AFTERNOON})
    void shouldRejectDraftSessionWhenADraftAllDaySessionExistsOnThatDay(final String sessionType) throws Exception {
        databaseSeeder.insertCourtSchedule(storedSession(ALL_DAY, true));

        final Response response = validateCreate(sessionType, true);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        assertThat(response.readEntity(String.class), containsString(DUPLICATE_SESSIONS));
    }

    @Test
    void shouldRejectFinalSessionWhenAFinalAllDaySessionExistsOnThatDay() throws Exception {
        databaseSeeder.insertCourtSchedule(storedSession(ALL_DAY, false));

        final Response response = validateCreate(MORNING, false);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        assertThat(response.readEntity(String.class), containsString(DUPLICATE_SESSIONS));
    }

    @Test
    void shouldAcceptDraftAfternoonSessionWhenADraftMorningSessionExistsOnThatDay() throws Exception {
        databaseSeeder.insertCourtSchedule(storedSession(MORNING, true));

        final Response response = validateCreate(AFTERNOON, true);

        final String body = response.readEntity(String.class);
        assertThat("validate-create answered with body " + body, response.getStatus(), is(OK.getStatusCode()));
        assertThat(body, is("{}"));
    }

    @Test
    void shouldRejectFinalSessionWhenTheSameSessionWithoutIsDraftIsInThePayload() {
        final String payload = createObjectBuilder()
                .add("sessions", createArrayBuilder().add(magistratesMorningSession()))
                .add("sessionToBeAdded", magistratesMorningSession().add("isDraft", false))
                .add("repeatPattern", onceInTheWeek())
                .build()
                .toString();

        final Response response = postCommand(VALIDATE_URL, VALIDATE_CREATE_CONTENT_TYPE, USER_ID, payload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        assertThat(response.readEntity(String.class), containsString(DUPLICATE_SESSIONS));
    }

    private JsonObjectBuilder magistratesMorningSession() {
        return createObjectBuilder()
                .add("courtCentreId", MAGISTRATES_COURT_CENTRE_ID)
                .add("courtRoomId", MAGISTRATES_COURT_ROOM_ID)
                .add("jurisdiction", "MAGISTRATES")
                .add("sessionType", MORNING)
                .add("businessType", "DVLA")
                .add("duration", 100)
                .add("panel", "ADULT")
                .add("repeatDays", createArrayBuilder().add("Thursday"));
    }

    private JsonObjectBuilder onceInTheWeek() {
        return createObjectBuilder()
                .add("startDate", weekStart.toString())
                .add("endDate", weekStart.plusDays(6).toString())
                .add("repeatFor", 1)
                .add("frequency", "ONCE");
    }

    private Response validateCreate(final String sessionType, final boolean isDraft) {
        final String payload = createObjectBuilder()
                .add("sessions", createArrayBuilder())
                .add("sessionToBeAdded", createObjectBuilder()
                        .add("courtCentreId", COURT_CENTRE_ID)
                        .add("courtRoomId", COURT_ROOM_ID)
                        .add("jurisdiction", CROWN)
                        .add("sessionType", sessionType)
                        .add("businessType", BUSINESS_TYPE)
                        .add("duration", 360)
                        .add("allDaySplit", false)
                        .add("panel", "ADULT")
                        .add("sessionStartTime", AFTERNOON.equals(sessionType) ? "14:00" : "10:00")
                        .add("sessionEndTime", MORNING.equals(sessionType) ? "13:00" : "16:30")
                        .add("isOverbookingAllowed", true)
                        .add("repeatDays", createArrayBuilder().add("Thursday"))
                        .add("isDraft", isDraft))
                .add("repeatPattern", onceInTheWeek())
                .build()
                .toString();
        return postCommand(VALIDATE_URL, VALIDATE_CREATE_CONTENT_TYPE, USER_ID, payload);
    }

    private CourtSchedule storedSession(final String courtSession, final boolean isDraft) {
        final CourtSchedule cs = new CourtSchedule();
        cs.setCourtScheduleId(UUID.randomUUID().toString());
        cs.setListingProfileId(UUID.randomUUID().toString());
        cs.setOuCode(OU_CODE);
        cs.setCourtRoomId(COURT_ROOM_ID);
        cs.setCourtRoomNumber(1);
        cs.setCourtHouseId(COURT_CENTRE_ID);
        cs.setCourtHouseName("Second Crown Court");
        cs.setCourtRoomName("Courtroom 03 (Shared)");
        cs.setOperationalUnit(OU_CODE);
        cs.setBusinessType(BUSINESS_TYPE);
        cs.setPanel("ADULT");
        cs.setCourtSession(courtSession);
        cs.setSlotBased(false);
        cs.setSessionDate(thursday);
        cs.setMaxSlots(0);
        cs.setMaxDuration(360);
        cs.setAvailableSlots(0);
        cs.setAvailableDuration(360);
        cs.setSupportAdSplit(false);
        cs.setMaxAdMorningDuration(0);
        cs.setMaxAdAfternoonDuration(0);
        cs.setSessionStartTime(atUtc(LocalTime.of(10, 0)));
        cs.setSessionEndTime(atUtc(LocalTime.of(16, 30)));
        cs.setNationalBreakTime(atUtc(LocalTime.of(13, 0)));
        cs.setIsOverbookingAllowed(true);
        cs.setIsDraft(isDraft);
        cs.setJurisdiction(CROWN);
        return cs;
    }

    private Instant atUtc(final LocalTime time) {
        return thursday.atTime(time).toInstant(ZoneOffset.UTC);
    }
}
